# CloudWatch log group for application logs
resource "aws_cloudwatch_log_group" "app" {
  name              = "/memoryvault/app"
  retention_in_days = 30

  tags = {
    Name = "${var.project_name}-logs"
  }
}

# Look up latest Amazon Linux 2023 AMI
data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-kernel-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# Get current AWS account ID
data "aws_caller_identity" "current" {}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.amazon_linux.id
  instance_type          = var.ec2_instance_type
  key_name               = var.ec2_key_pair_name
  subnet_id              = aws_subnet.public_a.id
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
    encrypted   = true
  }

  user_data = templatefile("${path.module}/templates/user_data.sh", {
    region               = var.aws_region
    account_id           = data.aws_caller_identity.current.account_id
    ecr_repo_url         = aws_ecr_repository.app.repository_url
    db_endpoint          = aws_db_instance.main.endpoint
    db_username          = var.db_username
    db_password          = var.db_password
    jwt_secret           = var.jwt_secret
    domain_name          = var.domain_name
    s3_bucket            = aws_s3_bucket.content.id
    cloudwatch_log_group = aws_cloudwatch_log_group.app.name
    internal_api_key     = var.internal_api_key
    cognito_user_pool_id = aws_cognito_user_pool.main.id
    cognito_client_id    = aws_cognito_user_pool_client.spa.id
  })

  tags = {
    Name = "${var.project_name}-app"
  }

  depends_on = [
    aws_db_instance.main,
    aws_ecr_repository.app
  ]
}
