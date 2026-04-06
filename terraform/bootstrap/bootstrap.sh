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
