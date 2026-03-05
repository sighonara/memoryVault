---
name: add-lambda
description: Scaffold a new AWS Lambda function for MemoryVault scheduled sync jobs (RSS fetch, YouTube sync, link health check, etc.). Creates the function code, Terraform resource, and local test harness.
---

# Add Lambda Function

You are adding a new AWS Lambda function to MemoryVault. Lambda functions handle async scheduled work — they are triggered by EventBridge on a schedule, do their job, and write results back to PostgreSQL or S3.

## Where Lambda Functions Live

```
lambdas/
└── <function-name>/
    ├── src/
    │   └── main.py       # handler entry point
    ├── tests/
    │   └── test_main.py  # pytest tests
    ├── requirements.txt
    └── README.md         # trigger schedule, inputs, outputs
```

Terraform resources live in `terraform/lambdas/<function-name>.tf`.

## Steps

### 1. Write the failing test first

Create `lambdas/<function-name>/tests/test_main.py`:

```python
import pytest
from unittest.mock import patch, MagicMock
from src.main import handler

def test_handler_returns_success_on_valid_event():
    event = {}
    context = MagicMock()
    result = handler(event, context)
    assert result["statusCode"] == 200

def test_handler_returns_failure_on_error():
    with patch("src.main.<dependency>", side_effect=Exception("boom")):
        result = handler({}, MagicMock())
    assert result["statusCode"] == 500
    assert "error" in result["body"]
```

Run: `cd lambdas/<function-name> && python -m pytest tests/ -v`
Expected: FAIL — `src.main` does not exist.

### 2. Create the handler

Create `lambdas/<function-name>/src/main.py`:

```python
import json
import logging

logger = logging.getLogger()
logger.setLevel(logging.INFO)

def handler(event, context):
    """
    <Describe what this Lambda does, what triggers it, what it writes.>
    EventBridge schedule: <cron or rate expression>
    """
    try:
        logger.info("Starting <function-name>", extra={"event": event})
        # --- implementation here ---
        return {"statusCode": 200, "body": json.dumps({"status": "ok"})}
    except Exception as e:
        logger.error("Failed: %s", str(e), exc_info=True)
        return {"statusCode": 500, "body": json.dumps({"error": str(e)})}
```

Run: `python -m pytest tests/ -v` — Expected: PASS.

### 3. Add requirements.txt

```
psycopg2-binary==2.9.9
boto3==1.34.0
```

### 4. Add Terraform resource

Create `terraform/lambdas/<function-name>.tf`:

```hcl
resource "aws_lambda_function" "<function_name>" {
  function_name    = "${var.environment}-memoryvault-<function-name>"
  role             = aws_iam_role.lambda_exec.arn
  handler          = "src.main.handler"
  runtime          = "python3.11"
  timeout          = 300

  filename         = data.archive_file.<function_name>_zip.output_path
  source_code_hash = data.archive_file.<function_name>_zip.output_base64sha256

  environment {
    variables = {
      DB_HOST     = var.db_host
      DB_NAME     = var.db_name
      DB_USER     = var.db_user
      DB_PASSWORD = var.db_password
    }
  }
}

data "archive_file" "<function_name>_zip" {
  type        = "zip"
  source_dir  = "${path.root}/../lambdas/<function-name>"
  output_path = "${path.root}/zips/<function-name>.zip"
}

resource "aws_cloudwatch_event_rule" "<function_name>_schedule" {
  name                = "${var.environment}-<function-name>-schedule"
  schedule_expression = "rate(1 hour)"
}

resource "aws_cloudwatch_event_target" "<function_name>_target" {
  rule      = aws_cloudwatch_event_rule.<function_name>_schedule.name
  target_id = "<function_name>"
  arn       = aws_lambda_function.<function_name>.arn
}

resource "aws_lambda_permission" "allow_eventbridge_<function_name>" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.<function_name>.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.<function_name>_schedule.arn
}
```

### 5. Commit

```bash
git add lambdas/<function-name>/ terraform/lambdas/<function-name>.tf
git commit -m "feat: add <function-name> Lambda function with Terraform and tests"
```
