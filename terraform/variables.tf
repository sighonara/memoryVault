variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Project name used for resource naming"
  type        = string
  default     = "memoryvault"
}

variable "domain_name" {
  description = "Domain name for the application (e.g., memoryvault.example.com)"
  type        = string
}

variable "ec2_instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.small"
}

variable "rds_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "rds_allocated_storage" {
  description = "RDS allocated storage in GB"
  type        = number
  default     = 20
}

variable "db_username" {
  description = "Database master username"
  type        = string
  default     = "memoryvault"
}

variable "db_password" {
  description = "Database master password"
  type        = string
  sensitive   = true
}

variable "ssh_allowed_cidr" {
  description = "CIDR block allowed to SSH into EC2 (your IP, e.g., 1.2.3.4/32)"
  type        = string
}

variable "ec2_key_pair_name" {
  description = "Name of the EC2 key pair for SSH access"
  type        = string
}

variable "jwt_secret" {
  description = "JWT signing secret for the application"
  type        = string
  sensitive   = true
}

variable "internal_api_key" {
  description = "Shared API key for internal endpoints (Lambda -> EC2)"
  type        = string
  sensitive   = true
}
