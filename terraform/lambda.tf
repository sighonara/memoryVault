# Security group for the sync-scheduler Lambdas.
# No ingress (nothing calls Lambda directly — EventBridge invokes via the Lambda API,
# which does not use this SG). All egress so the function can reach the EC2 private IP
# on :8085. Egress to 0.0.0.0/0 is safe because the Lambdas live in a VPC-attached
# ENI that has no route to the internet (no NAT gateway); the only reachable target
# is in-VPC traffic.
resource "aws_security_group" "lambda_sync" {
  name_prefix = "${var.project_name}-lambda-sync-"
  description = "Sync scheduler Lambdas - egress-only to EC2 in-VPC"
  vpc_id      = aws_vpc.main.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "All outbound (VPC-internal only - no NAT)"
  }

  tags = {
    Name = "${var.project_name}-lambda-sync-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# IAM role shared by both sync-scheduler Lambdas.
resource "aws_iam_role" "lambda_sync" {
  name = "${var.project_name}-lambda-sync-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${var.project_name}-lambda-sync-role"
  }
}

# Basic CloudWatch Logs permissions.
resource "aws_iam_role_policy_attachment" "lambda_sync_basic" {
  role       = aws_iam_role.lambda_sync.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Required for VPC-attached Lambdas (ENI create/describe/delete).
resource "aws_iam_role_policy_attachment" "lambda_sync_vpc" {
  role       = aws_iam_role.lambda_sync.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

# --- feed-sync Lambda ---

data "archive_file" "feed_sync_zip" {
  type        = "zip"
  source_dir  = "${path.module}/../lambdas/feed-sync"
  output_path = "${path.module}/.build/feed-sync.zip"
}

resource "aws_cloudwatch_log_group" "feed_sync" {
  name              = "/aws/lambda/${var.project_name}-feed-sync"
  retention_in_days = 30

  tags = {
    Name = "${var.project_name}-feed-sync-logs"
  }
}

resource "aws_lambda_function" "feed_sync" {
  function_name    = "${var.project_name}-feed-sync"
  role             = aws_iam_role.lambda_sync.arn
  runtime          = "python3.11"
  handler          = "handler.handler"
  filename         = data.archive_file.feed_sync_zip.output_path
  source_code_hash = data.archive_file.feed_sync_zip.output_base64sha256
  timeout          = 60
  memory_size      = 128

  vpc_config {
    subnet_ids         = [aws_subnet.public_a.id]
    security_group_ids = [aws_security_group.lambda_sync.id]
  }

  environment {
    variables = {
      APP_BASE_URL     = "http://${aws_instance.app.private_ip}:8085"
      INTERNAL_API_KEY = random_password.internal_api_key.result
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_sync_basic,
    aws_iam_role_policy_attachment.lambda_sync_vpc,
    aws_cloudwatch_log_group.feed_sync,
  ]

  tags = {
    Name = "${var.project_name}-feed-sync"
  }
}

resource "aws_cloudwatch_event_rule" "feed_sync" {
  name                = "${var.project_name}-feed-sync"
  description         = "Scheduled feed refresh - invokes feed-sync Lambda"
  schedule_expression = var.feed_sync_schedule
}

resource "aws_cloudwatch_event_target" "feed_sync" {
  rule      = aws_cloudwatch_event_rule.feed_sync.name
  target_id = "feed-sync-lambda"
  arn       = aws_lambda_function.feed_sync.arn
}

resource "aws_lambda_permission" "feed_sync_events" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.feed_sync.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.feed_sync.arn
}

# --- youtube-sync Lambda ---

data "archive_file" "youtube_sync_zip" {
  type        = "zip"
  source_dir  = "${path.module}/../lambdas/youtube-sync"
  output_path = "${path.module}/.build/youtube-sync.zip"
}

resource "aws_cloudwatch_log_group" "youtube_sync" {
  name              = "/aws/lambda/${var.project_name}-youtube-sync"
  retention_in_days = 30

  tags = {
    Name = "${var.project_name}-youtube-sync-logs"
  }
}

resource "aws_lambda_function" "youtube_sync" {
  function_name    = "${var.project_name}-youtube-sync"
  role             = aws_iam_role.lambda_sync.arn
  runtime          = "python3.11"
  handler          = "handler.handler"
  filename         = data.archive_file.youtube_sync_zip.output_path
  source_code_hash = data.archive_file.youtube_sync_zip.output_base64sha256
  timeout          = 60
  memory_size      = 128

  vpc_config {
    subnet_ids         = [aws_subnet.public_a.id]
    security_group_ids = [aws_security_group.lambda_sync.id]
  }

  environment {
    variables = {
      APP_BASE_URL     = "http://${aws_instance.app.private_ip}:8085"
      INTERNAL_API_KEY = random_password.internal_api_key.result
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_sync_basic,
    aws_iam_role_policy_attachment.lambda_sync_vpc,
    aws_cloudwatch_log_group.youtube_sync,
  ]

  tags = {
    Name = "${var.project_name}-youtube-sync"
  }
}

resource "aws_cloudwatch_event_rule" "youtube_sync" {
  name                = "${var.project_name}-youtube-sync"
  description         = "Scheduled YouTube playlist refresh - invokes youtube-sync Lambda"
  schedule_expression = var.youtube_sync_schedule
}

resource "aws_cloudwatch_event_target" "youtube_sync" {
  rule      = aws_cloudwatch_event_rule.youtube_sync.name
  target_id = "youtube-sync-lambda"
  arn       = aws_lambda_function.youtube_sync.arn
}

resource "aws_lambda_permission" "youtube_sync_events" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.youtube_sync.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.youtube_sync.arn
}
