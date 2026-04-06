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
