# Phase 9A — AWS Foundation + Terraform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy MemoryVault to AWS with Terraform-managed infrastructure (VPC, EC2, RDS, S3, Route 53, ECR) and a Dockerized Spring Boot application behind Caddy for HTTPS.

**Architecture:** Single-region, single-VPC deployment with two public subnets across two AZs. EC2 runs the app as a Docker container with Caddy as the HTTPS reverse proxy (Let's Encrypt). RDS PostgreSQL for the database. S3 for content storage. Route 53 + Elastic IP for DNS.

**Tech Stack:** Terraform, AWS (VPC, EC2, RDS, S3, Route 53, ECR, IAM, SSM), Docker, Caddy, Spring Boot

**Design Spec:** `docs/plans/2026-04-03-phase-9-infrastructure-design.md`

---

### Task 1: Profile Naming Migration

Rename the `dev` profile to `local` and replace all `@Profile("!aws")` with `@Profile("local")` for readability and future extensibility.

**Files:**
- Rename: `src/main/resources/application-dev.properties` → `src/main/resources/application-local.properties`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/kotlin/org/sightech/memoryvault/storage/LocalStorageService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/logging/LocalLogService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/auth/service/AuthService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/auth/controller/AuthController.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/youtube/service/LocalVideoDownloader.kt`

- [ ] **Step 1: Rename application-dev.properties to application-local.properties**

```bash
git mv src/main/resources/application-dev.properties src/main/resources/application-local.properties
```

- [ ] **Step 2: Update the file header comment**

In `src/main/resources/application-local.properties`, change line 1:

Old: `# Dev profile — active by default (no SPRING_PROFILES_ACTIVE set)`
New: `# Local profile — active by default (spring.profiles.default=local)`

- [ ] **Step 3: Update spring.profiles.default in application.properties**

In `src/main/resources/application.properties`, change line 2:

Old: `spring.profiles.default=dev`
New: `spring.profiles.default=local`

- [ ] **Step 4: Replace @Profile("!aws") with @Profile("local") in all 5 files**

In each of the following files, find `@Profile("!aws")` and replace with `@Profile("local")`:

`src/main/kotlin/org/sightech/memoryvault/storage/LocalStorageService.kt`:
```kotlin
// Old
@Profile("!aws")
// New
@Profile("local")
```

`src/main/kotlin/org/sightech/memoryvault/logging/LocalLogService.kt`:
```kotlin
// Old
@Profile("!aws")
// New
@Profile("local")
```

`src/main/kotlin/org/sightech/memoryvault/auth/service/AuthService.kt`:
```kotlin
// Old
@Profile("!aws")
// New
@Profile("local")
```

`src/main/kotlin/org/sightech/memoryvault/auth/controller/AuthController.kt`:
```kotlin
// Old
@Profile("!aws")
// New
@Profile("local")
```

`src/main/kotlin/org/sightech/memoryvault/youtube/service/LocalVideoDownloader.kt`:
```kotlin
// Old
@Profile("!aws")
// New
@Profile("local")
```

- [ ] **Step 5: Verify the app starts with the local profile**

```bash
./gradlew bootRun
```

Expected: App starts successfully, logs show `No active profile set, falling back to 1 default profile: "local"`. Stop the app with Ctrl+C.

- [ ] **Step 6: Run all tests**

```bash
./gradlew test
```

Expected: All tests pass. The test profile (`application-test.properties`) doesn't reference `dev` or `local`, so no changes needed.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "refactor: rename dev profile to local, replace @Profile(!aws) with @Profile(local)"
```

---

### Task 2: Dockerfile

Create a multi-stage Dockerfile for building and running the Spring Boot application.

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`

- [ ] **Step 1: Create .dockerignore**

Create `.dockerignore` at the project root:

```
.git
.gradle
.idea
build
client/node_modules
client/dist
client/.angular
terraform
lambdas
docs
*.md
```

- [ ] **Step 2: Create the Dockerfile**

Create `Dockerfile` at the project root:

```dockerfile
# Stage 1: Build the application
FROM gradle:8.13-jdk22 AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradle.properties* ./
COPY src/ src/
RUN gradle bootJar --no-daemon

# Stage 2: Run the application
FROM eclipse-temurin:22-jre-alpine
WORKDIR /app

# Create a non-root user
RUN addgroup -S memoryvault && adduser -S memoryvault -G memoryvault

COPY --from=builder /app/build/libs/*.jar app.jar

# Switch to non-root user
USER memoryvault

EXPOSE 8085

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8085/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 3: Verify Docker build succeeds**

```bash
docker build -t memoryvault:local .
```

Expected: Build completes successfully, outputs `Successfully tagged memoryvault:local`.

- [ ] **Step 4: Verify Docker container runs**

```bash
docker run --rm -p 8085:8085 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/memoryvault \
  -e SPRING_DATASOURCE_USERNAME=memoryvault \
  -e SPRING_DATASOURCE_PASSWORD=memoryvault \
  -e MEMORYVAULT_JWT_SECRET=dev-secret-key-change-in-production-must-be-at-least-256-bits-long!! \
  -e MEMORYVAULT_CORS_ALLOWED__ORIGINS=http://localhost:4200 \
  memoryvault:local
```

Expected: App starts successfully inside the container. Test with `curl http://localhost:8085/actuator/health`. Stop with Ctrl+C.

Note: `host.docker.internal` resolves to the host machine's localhost on Docker Desktop (macOS). This lets the container reach the PostgreSQL running on the host via Docker Compose.

- [ ] **Step 5: Commit**

```bash
git add Dockerfile .dockerignore && git commit -m "feat: add Dockerfile for containerized deployment"
```

---

### Task 3: Terraform Bootstrap

Create the bootstrap script that provisions the S3 bucket for Terraform state and the DynamoDB table for state locking. These resources must exist before Terraform can initialize.

**Files:**
- Create: `terraform/bootstrap/bootstrap.sh`
- Create: `terraform/bootstrap/README.md`

- [ ] **Step 1: Create the bootstrap directory**

```bash
mkdir -p terraform/bootstrap
```

- [ ] **Step 2: Create the bootstrap script**

Create `terraform/bootstrap/bootstrap.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Bootstrap script for Terraform state backend.
# Run this ONCE before the first `terraform init`.
# Prerequisites: AWS CLI configured with admin credentials.

REGION="${AWS_REGION:-us-east-1}"
STATE_BUCKET="memoryvault-terraform-state"
LOCK_TABLE="memoryvault-terraform-locks"

echo "=== Bootstrapping Terraform backend ==="
echo "Region: $REGION"
echo "State bucket: $STATE_BUCKET"
echo "Lock table: $LOCK_TABLE"
echo ""

# Create S3 bucket for state
echo "Creating S3 bucket..."
if [ "$REGION" = "us-east-1" ]; then
  aws s3api create-bucket \
    --bucket "$STATE_BUCKET" \
    --region "$REGION"
else
  aws s3api create-bucket \
    --bucket "$STATE_BUCKET" \
    --region "$REGION" \
    --create-bucket-configuration LocationConstraint="$REGION"
fi

# Enable versioning on state bucket
echo "Enabling versioning..."
aws s3api put-bucket-versioning \
  --bucket "$STATE_BUCKET" \
  --versioning-configuration Status=Enabled

# Enable server-side encryption
echo "Enabling encryption..."
aws s3api put-bucket-encryption \
  --bucket "$STATE_BUCKET" \
  --server-side-encryption-configuration '{
    "Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]
  }'

# Block public access
echo "Blocking public access..."
aws s3api put-public-access-block \
  --bucket "$STATE_BUCKET" \
  --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

# Create DynamoDB table for state locking
echo "Creating DynamoDB lock table..."
aws dynamodb create-table \
  --table-name "$LOCK_TABLE" \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region "$REGION" \
  2>/dev/null || echo "  (table may already exist — that's OK)"

echo ""
echo "=== Bootstrap complete ==="
echo "You can now run: cd terraform && terraform init"
```

- [ ] **Step 3: Make bootstrap script executable**

```bash
chmod +x terraform/bootstrap/bootstrap.sh
```

- [ ] **Step 4: Create the bootstrap README**

Create `terraform/bootstrap/README.md`:

```markdown
# Terraform Bootstrap

Run this script **once** before the first `terraform init`. It creates the S3 bucket and DynamoDB table that Terraform uses to store and lock its state.

## Prerequisites

1. AWS CLI installed and configured (`aws configure`)
2. IAM user with `AdministratorAccess` policy

## Usage

```bash
# Use default region (us-east-1)
./bootstrap.sh

# Or specify a region
AWS_REGION=us-west-2 ./bootstrap.sh
```

## What it creates

- S3 bucket `memoryvault-terraform-state` (versioned, encrypted, no public access)
- DynamoDB table `memoryvault-terraform-locks` (for state locking)

## After bootstrapping

```bash
cd ../
terraform init
```
```

- [ ] **Step 5: Commit**

```bash
git add terraform/bootstrap/ && git commit -m "feat: add Terraform bootstrap script for state backend"
```

---

### Task 4: Terraform Variables and Provider

Set up the Terraform provider configuration, variables, and S3 backend.

**Files:**
- Create: `terraform/variables.tf`
- Create: `terraform/main.tf`

- [ ] **Step 1: Create variables.tf**

Create `terraform/variables.tf`:

```hcl
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
```

- [ ] **Step 2: Create main.tf**

Create `terraform/main.tf`:

```hcl
terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "memoryvault-terraform-state"
    key            = "terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "memoryvault-terraform-locks"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      ManagedBy   = "terraform"
    }
  }
}
```

- [ ] **Step 3: Validate syntax**

```bash
cd terraform && terraform fmt -check && cd ..
```

Expected: No output (files are formatted correctly). If formatting issues, run `terraform fmt` to fix.

- [ ] **Step 4: Commit**

```bash
git add terraform/variables.tf terraform/main.tf && git commit -m "feat: add Terraform provider config and variables"
```

---

### Task 5: VPC and Networking

Create the VPC with two public subnets across two AZs, internet gateway, and route tables.

**Files:**
- Create: `terraform/vpc.tf`

- [ ] **Step 1: Create vpc.tf**

Create `terraform/vpc.tf`:

```hcl
# Fetch available AZs in the region
data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${var.project_name}-vpc"
  }
}

resource "aws_subnet" "public_a" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.project_name}-public-a"
  }
}

resource "aws_subnet" "public_b" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = data.aws_availability_zones.available.names[1]
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.project_name}-public-b"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-igw"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "${var.project_name}-public-rt"
  }
}

resource "aws_route_table_association" "public_a" {
  subnet_id      = aws_subnet.public_a.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "public_b" {
  subnet_id      = aws_subnet.public_b.id
  route_table_id = aws_route_table.public.id
}
```

- [ ] **Step 2: Validate**

```bash
cd terraform && terraform fmt -check && cd ..
```

- [ ] **Step 3: Commit**

```bash
git add terraform/vpc.tf && git commit -m "feat: add Terraform VPC with two public subnets"
```

---

### Task 6: Security Groups

Create security groups for EC2 and RDS.

**Files:**
- Create: `terraform/security.tf`

- [ ] **Step 1: Create security.tf**

Create `terraform/security.tf`:

```hcl
resource "aws_security_group" "ec2" {
  name_prefix = "${var.project_name}-ec2-"
  description = "Security group for MemoryVault EC2 instance"
  vpc_id      = aws_vpc.main.id

  # HTTP (Caddy redirect to HTTPS)
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTP (Caddy redirects to HTTPS)"
  }

  # HTTPS (Caddy TLS termination)
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTPS (Caddy with Let's Encrypt)"
  }

  # SSH (restricted to your IP)
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
    description = "SSH access from admin IP"
  }

  # All outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "All outbound traffic"
  }

  tags = {
    Name = "${var.project_name}-ec2-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "rds" {
  name_prefix = "${var.project_name}-rds-"
  description = "Security group for MemoryVault RDS instance"
  vpc_id      = aws_vpc.main.id

  # PostgreSQL from EC2 only
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2.id]
    description     = "PostgreSQL from EC2 only"
  }

  tags = {
    Name = "${var.project_name}-rds-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}
```

- [ ] **Step 2: Validate**

```bash
cd terraform && terraform fmt -check && cd ..
```

- [ ] **Step 3: Commit**

```bash
git add terraform/security.tf && git commit -m "feat: add Terraform security groups for EC2 and RDS"
```

---

### Task 7: IAM Roles and Instance Profile

Create the IAM role for EC2 with permissions for S3, CloudWatch Logs, ECR, and SSM.

**Files:**
- Create: `terraform/iam.tf`

- [ ] **Step 1: Create iam.tf**

Create `terraform/iam.tf`:

```hcl
# EC2 instance role
resource "aws_iam_role" "ec2" {
  name = "${var.project_name}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${var.project_name}-ec2-role"
  }
}

# S3 access for content storage bucket
resource "aws_iam_role_policy" "ec2_s3" {
  name = "${var.project_name}-ec2-s3"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
          "s3:ListBucket",
          "s3:HeadObject"
        ]
        Resource = [
          aws_s3_bucket.content.arn,
          "${aws_s3_bucket.content.arn}/*"
        ]
      }
    ]
  })
}

# CloudWatch Logs for log shipping
resource "aws_iam_role_policy" "ec2_cloudwatch" {
  name = "${var.project_name}-ec2-cloudwatch"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams",
          "logs:StartQuery",
          "logs:GetQueryResults"
        ]
        Resource = "arn:aws:logs:${var.aws_region}:*:*"
      }
    ]
  })
}

# ECR pull for Docker images
resource "aws_iam_role_policy" "ec2_ecr" {
  name = "${var.project_name}-ec2-ecr"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken",
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage"
        ]
        Resource = "*"
      }
    ]
  })
}

# SSM for remote command execution (deploys)
resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# Instance profile (connects the role to EC2)
resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project_name}-ec2-profile"
  role = aws_iam_role.ec2.name
}
```

- [ ] **Step 2: Validate**

```bash
cd terraform && terraform fmt -check && cd ..
```

- [ ] **Step 3: Commit**

```bash
git add terraform/iam.tf && git commit -m "feat: add Terraform IAM role and instance profile for EC2"
```

---

### Task 8: S3 Bucket

Create the S3 bucket for content storage with versioning and lifecycle rules.

**Files:**
- Create: `terraform/s3.tf`

- [ ] **Step 1: Create s3.tf**

Create `terraform/s3.tf`:

```hcl
resource "aws_s3_bucket" "content" {
  bucket = "${var.project_name}-content"

  tags = {
    Name = "${var.project_name}-content"
  }
}

resource "aws_s3_bucket_versioning" "content" {
  bucket = aws_s3_bucket.content.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "content" {
  bucket = aws_s3_bucket.content.id

  rule {
    id     = "transition-to-ia"
    status = "Enabled"

    transition {
      days          = 90
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 365
      storage_class = "DEEP_ARCHIVE"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "content" {
  bucket = aws_s3_bucket.content.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "content" {
  bucket = aws_s3_bucket.content.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
```

- [ ] **Step 2: Validate**

```bash
cd terraform && terraform fmt -check && cd ..
```

- [ ] **Step 3: Commit**

```bash
git add terraform/s3.tf && git commit -m "feat: add Terraform S3 bucket with lifecycle rules"
```

---

### Task 9: ECR Repository

Create the ECR repository for Docker images.

**Files:**
- Create: `terraform/ecr.tf`

- [ ] **Step 1: Create ecr.tf**

Create `terraform/ecr.tf`:

```hcl
resource "aws_ecr_repository" "app" {
  name                 = var.project_name
  image_tag_mutability = "MUTABLE"
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "${var.project_name}-ecr"
  }
}

# Keep last 10 images, expire untagged after 7 days
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 7 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 7
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "Keep last 10 tagged images"
        selection = {
          tagStatus   = "tagged"
          tagPrefixList = ["latest"]
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
```

- [ ] **Step 2: Validate**

```bash
cd terraform && terraform fmt -check && cd ..
```

- [ ] **Step 3: Commit**

```bash
git add terraform/ecr.tf && git commit -m "feat: add Terraform ECR repository"
```

---

### Task 10: RDS PostgreSQL

Create the RDS PostgreSQL instance.

**Files:**
- Create: `terraform/rds.tf`

- [ ] **Step 1: Create rds.tf**

Create `terraform/rds.tf`:

```hcl
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet"
  subnet_ids = [aws_subnet.public_a.id, aws_subnet.public_b.id]

  tags = {
    Name = "${var.project_name}-db-subnet"
  }
}

resource "aws_db_instance" "main" {
  identifier     = var.project_name
  engine         = "postgres"
  engine_version = "16"

  instance_class    = var.rds_instance_class
  allocated_storage = var.rds_allocated_storage
  storage_type      = "gp3"

  db_name  = "memoryvault"
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:00-sun:05:00"

  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-final-snapshot"

  storage_encrypted = true

  tags = {
    Name = "${var.project_name}-rds"
  }
}
```

- [ ] **Step 2: Validate**

```bash
cd terraform && terraform fmt -check && cd ..
```

- [ ] **Step 3: Commit**

```bash
git add terraform/rds.tf && git commit -m "feat: add Terraform RDS PostgreSQL instance"
```

---

### Task 11: Route 53 DNS and Elastic IP

Create the Route 53 hosted zone, Elastic IP, and DNS records.

**Files:**
- Create: `terraform/dns.tf`

- [ ] **Step 1: Create dns.tf**

Create `terraform/dns.tf`:

```hcl
# Look up the hosted zone created by domain registration
# (Route 53 automatically creates a hosted zone when you register a domain)
data "aws_route53_zone" "main" {
  name = var.domain_name
}

# Static IP for EC2 — DNS records point here, survives instance restarts
resource "aws_eip" "ec2" {
  instance = aws_instance.app.id
  domain   = "vpc"

  tags = {
    Name = "${var.project_name}-eip"
  }
}

# A record pointing domain to EC2's Elastic IP
resource "aws_route53_record" "app" {
  zone_id = data.aws_route53_zone.main.zone_id
  name    = var.domain_name
  type    = "A"
  ttl     = 300
  records = [aws_eip.ec2.public_ip]
}
```

- [ ] **Step 2: Validate**

```bash
cd terraform && terraform fmt -check && cd ..
```

- [ ] **Step 3: Commit**

```bash
git add terraform/dns.tf && git commit -m "feat: add Terraform Route 53 DNS and Elastic IP"
```

---

### Task 12: EC2 Instance with user_data

Create the EC2 instance with a user_data script that installs Docker, Caddy, SSM agent, yt-dlp, and starts the application.

**Files:**
- Create: `terraform/ec2.tf`
- Create: `terraform/templates/user_data.sh`

- [ ] **Step 1: Create the user_data template**

```bash
mkdir -p terraform/templates
```

Create `terraform/templates/user_data.sh`:

```bash
#!/bin/bash
set -euo pipefail

# Log user_data output for debugging
exec > >(tee /var/log/user_data.log) 2>&1

echo "=== MemoryVault EC2 setup ==="

# Update system packages
dnf update -y

# --- Docker ---
dnf install -y docker
systemctl enable docker
systemctl start docker
usermod -aG docker ec2-user

# --- Caddy ---
dnf install -y dnf-plugins-core
dnf copr enable -y @caddy/caddy epel-9-$(uname -m)
dnf install -y caddy

# Write Caddyfile
cat > /etc/caddy/Caddyfile <<'CADDYFILE'
${domain_name} {
    reverse_proxy localhost:8085
}
CADDYFILE

systemctl enable caddy
systemctl start caddy

# --- CloudWatch Agent ---
dnf install -y amazon-cloudwatch-agent

cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json <<'CW_CONFIG'
{
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/var/log/memoryvault/*.log",
            "log_group_name": "${cloudwatch_log_group}",
            "log_stream_name": "{instance_id}",
            "timestamp_format": "%Y-%m-%dT%H:%M:%S"
          }
        ]
      }
    }
  }
}
CW_CONFIG

/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config -m ec2 \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json -s

# --- yt-dlp + ffmpeg ---
dnf install -y python3-pip ffmpeg-free
pip3 install yt-dlp

# --- SSM Agent (usually pre-installed on Amazon Linux 2023) ---
systemctl enable amazon-ssm-agent
systemctl start amazon-ssm-agent

# --- Pull and run the application ---
REGION="${region}"
ACCOUNT_ID="${account_id}"
ECR_REPO="${ecr_repo_url}"

# Authenticate Docker to ECR
aws ecr get-login-password --region "$REGION" | \
  docker login --username AWS --password-stdin "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"

# Create log directory for CloudWatch agent
mkdir -p /var/log/memoryvault

# Run the application container
docker run -d \
  --name memoryvault \
  --restart unless-stopped \
  -p 8085:8085 \
  -v /var/log/memoryvault:/app/logs \
  -e SPRING_PROFILES_ACTIVE=aws,prod \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://${db_endpoint}/memoryvault" \
  -e SPRING_DATASOURCE_USERNAME="${db_username}" \
  -e SPRING_DATASOURCE_PASSWORD="${db_password}" \
  -e MEMORYVAULT_JWT_SECRET="${jwt_secret}" \
  -e MEMORYVAULT_CORS_ALLOWED__ORIGINS="https://${domain_name}" \
  -e MEMORYVAULT_STORAGE_S3__BUCKET="${s3_bucket}" \
  -e MEMORYVAULT_STORAGE_S3__REGION="${region}" \
  -e MEMORYVAULT_LOGGING_CLOUDWATCH__LOG__GROUP="${cloudwatch_log_group}" \
  -e MEMORYVAULT_LOGGING_CLOUDWATCH__REGION="${region}" \
  -e MEMORYVAULT_WEBSOCKET_ALLOWED__ORIGINS="https://${domain_name}" \
  -e INTERNAL_API_KEY="${internal_api_key}" \
  "$ECR_REPO:latest"

echo "=== MemoryVault EC2 setup complete ==="
```

- [ ] **Step 2: Create ec2.tf**

Create `terraform/ec2.tf`:

```hcl
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
    values = ["al2023-ami-*-x86_64"]
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
  })

  tags = {
    Name = "${var.project_name}-app"
  }

  depends_on = [
    aws_db_instance.main,
    aws_ecr_repository.app
  ]
}
```

- [ ] **Step 3: Validate**

```bash
cd terraform && terraform fmt -check && cd ..
```

- [ ] **Step 4: Commit**

```bash
git add terraform/ec2.tf terraform/templates/ && git commit -m "feat: add Terraform EC2 instance with user_data (Docker, Caddy, yt-dlp)"
```

---

### Task 13: Terraform Outputs

Create the outputs file that exposes key resource values.

**Files:**
- Create: `terraform/outputs.tf`

- [ ] **Step 1: Create outputs.tf**

Create `terraform/outputs.tf`:

```hcl
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
```

- [ ] **Step 2: Validate**

```bash
cd terraform && terraform fmt -check && cd ..
```

- [ ] **Step 3: Commit**

```bash
git add terraform/outputs.tf && git commit -m "feat: add Terraform outputs"
```

---

### Task 14: Terraform Variable Defaults File

Create a `terraform.tfvars.example` file so the user knows which variables to set.

**Files:**
- Create: `terraform/terraform.tfvars.example`
- Modify: `.gitignore`

- [ ] **Step 1: Create terraform.tfvars.example**

Create `terraform/terraform.tfvars.example`:

```hcl
# Copy this file to terraform.tfvars and fill in the values.
# terraform.tfvars is gitignored — it contains secrets.

aws_region         = "us-east-1"
domain_name        = "memoryvault.example.com"
ec2_instance_type  = "t3.small"
rds_instance_class = "db.t3.micro"
rds_allocated_storage = 20
db_username        = "memoryvault"
db_password        = "CHANGE_ME_strong_password_here"
ssh_allowed_cidr   = "YOUR_IP/32"
ec2_key_pair_name  = "memoryvault-key"
jwt_secret         = "CHANGE_ME_at_least_256_bits_long_for_hs256_signing"
internal_api_key   = "CHANGE_ME_random_uuid_or_long_string"
```

- [ ] **Step 2: Add terraform.tfvars and .terraform to .gitignore**

Append to `.gitignore` at the project root:

```
# Terraform
terraform/.terraform/
terraform/.terraform.lock.hcl
terraform/terraform.tfvars
terraform/*.tfstate
terraform/*.tfstate.backup
```

- [ ] **Step 3: Commit**

```bash
git add terraform/terraform.tfvars.example .gitignore && git commit -m "feat: add Terraform tfvars example and update .gitignore"
```

---

### Task 15: Update Master Roadmap

Mark Phase 9A as in-progress in the master roadmap.

**Files:**
- Modify: `docs/plans/2026-03-05-tooling-first-design.md`

- [ ] **Step 1: Update the Phase 9 entry**

In `docs/plans/2026-03-05-tooling-first-design.md`, find the Phase 9 entry in the Development Sequence section and mark it as in-progress. If there isn't a Development Sequence section, add Phase 9 to the Notes section.

- [ ] **Step 2: Commit**

```bash
git add docs/plans/2026-03-05-tooling-first-design.md && git commit -m "docs: mark Phase 9 as in-progress in master roadmap"
```

---

## Summary Table

| Task | Description | Key Files |
|------|-------------|-----------|
| 1 | Profile naming migration (dev → local) | `application.properties`, `application-local.properties`, 5 Kotlin files |
| 2 | Dockerfile | `Dockerfile`, `.dockerignore` |
| 3 | Terraform bootstrap | `terraform/bootstrap/bootstrap.sh`, `terraform/bootstrap/README.md` |
| 4 | Terraform provider and variables | `terraform/main.tf`, `terraform/variables.tf` |
| 5 | VPC and networking | `terraform/vpc.tf` |
| 6 | Security groups | `terraform/security.tf` |
| 7 | IAM roles and instance profile | `terraform/iam.tf` |
| 8 | S3 bucket | `terraform/s3.tf` |
| 9 | ECR repository | `terraform/ecr.tf` |
| 10 | RDS PostgreSQL | `terraform/rds.tf` |
| 11 | Route 53 DNS and Elastic IP | `terraform/dns.tf` |
| 12 | EC2 instance with user_data | `terraform/ec2.tf`, `terraform/templates/user_data.sh` |
| 13 | Terraform outputs | `terraform/outputs.tf` |
| 14 | Terraform tfvars example and .gitignore | `terraform/terraform.tfvars.example`, `.gitignore` |
| 15 | Update master roadmap | `docs/plans/2026-03-05-tooling-first-design.md` |
