output "ec2_public_ip" {
  description = "Elastic IP of the EC2 instance"
  value       = aws_eip.ec2.public_ip
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint (host:port)"
  value       = aws_db_instance.main.endpoint
}

output "s3_bucket_name" {
  description = "S3 content storage bucket name"
  value       = aws_s3_bucket.content.id
}

output "ecr_repository_url" {
  description = "ECR repository URL for Docker images"
  value       = aws_ecr_repository.app.repository_url
}

output "cloudwatch_log_group" {
  description = "CloudWatch log group name"
  value       = aws_cloudwatch_log_group.app.name
}

output "domain_name" {
  description = "Application domain name"
  value       = var.domain_name
}

output "ec2_instance_id" {
  description = "EC2 instance ID (used for SSM commands)"
  value       = aws_instance.app.id
}

output "nameservers" {
  description = "Route 53 nameservers (set these at your domain registrar if using external DNS)"
  value       = data.aws_route53_zone.main.name_servers
}

output "cognito_user_pool_id" {
  description = "Cognito User Pool ID"
  value       = aws_cognito_user_pool.main.id
}

output "cognito_client_id" {
  description = "Cognito App Client ID (public, no secret)"
  value       = aws_cognito_user_pool_client.spa.id
}
