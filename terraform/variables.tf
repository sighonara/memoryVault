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

variable "encryption_key" {
  description = "AES-256-GCM encryption key for backup provider credentials"
  type        = string
  sensitive   = true
}

variable "feed_sync_schedule" {
  description = "EventBridge schedule expression for feed-sync Lambda"
  type        = string
  default     = "rate(30 minutes)"
}

variable "youtube_sync_schedule" {
  description = "EventBridge schedule expression for youtube-sync Lambda"
  type        = string
  default     = "rate(6 hours)"
}

variable "yt_dlp_upgrade_schedule" {
  description = "EventBridge schedule expression for daily yt-dlp upgrade"
  type        = string
  default     = "rate(1 day)"
}
