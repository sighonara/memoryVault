# Daily yt-dlp upgrade on the EC2 instance via SSM RunCommand.
#
# Rationale: yt-dlp breaks every few weeks when YouTube changes their player
# internals. Baking it into the AMI / user_data means we only pick up fixes on
# instance replacement, which is too slow. This schedule keeps the EC2 binary
# current without a deploy. Runs the same `pip3 install --upgrade yt-dlp`
# command that user_data.sh uses on first boot.

# EventBridge needs permission to call ssm:SendCommand and pass itself as the
# invoking principal. This is a separate role from the EC2 role.
resource "aws_iam_role" "eventbridge_ssm" {
  name = "${var.project_name}-eventbridge-ssm-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "events.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${var.project_name}-eventbridge-ssm-role"
  }
}

resource "aws_iam_role_policy" "eventbridge_ssm" {
  name = "${var.project_name}-eventbridge-ssm"
  role = aws_iam_role.eventbridge_ssm.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ssm:SendCommand",
        ]
        Resource = [
          "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript",
          "arn:aws:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/*",
        ]
      }
    ]
  })
}

resource "aws_cloudwatch_event_rule" "yt_dlp_upgrade" {
  name                = "${var.project_name}-yt-dlp-upgrade"
  description         = "Daily yt-dlp upgrade on the EC2 app instance"
  schedule_expression = var.yt_dlp_upgrade_schedule
}

resource "aws_cloudwatch_event_target" "yt_dlp_upgrade" {
  rule      = aws_cloudwatch_event_rule.yt_dlp_upgrade.name
  target_id = "yt-dlp-upgrade-ssm"
  arn       = "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript"
  role_arn  = aws_iam_role.eventbridge_ssm.arn

  run_command_targets {
    key    = "tag:Name"
    values = ["${var.project_name}-app"]
  }

  input = jsonencode({
    commands = [
      "pip3 install --upgrade yt-dlp",
    ]
  })
}
