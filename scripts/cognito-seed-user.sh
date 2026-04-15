#!/usr/bin/env bash
set -euo pipefail

# Create the seed admin user in Cognito.
# Run ONCE after terraform apply creates the User Pool.
# Prerequisites: AWS CLI configured, terraform outputs available.

REGION="${AWS_REGION:-us-east-1}"
USER_POOL_ID="${1:?Usage: $0 <user-pool-id> [email]}"
EMAIL="${2:-system@memoryvault.local}"
PASSWORD=$(openssl rand -base64 24)

echo "=== Creating Cognito seed user ==="
echo "Pool: $USER_POOL_ID"
echo "Email: $EMAIL"

# Create user with temporary password
aws cognito-idp admin-create-user \
  --user-pool-id "$USER_POOL_ID" \
  --username "$EMAIL" \
  --user-attributes Name=email,Value="$EMAIL" Name=email_verified,Value=true Name=custom:role,Value=OWNER \
  --region "$REGION"

# Set permanent password (skip FORCE_CHANGE_PASSWORD state)
aws cognito-idp admin-set-user-password \
  --user-pool-id "$USER_POOL_ID" \
  --username "$EMAIL" \
  --password "$PASSWORD" \
  --permanent \
  --region "$REGION"

echo ""
echo "=== Seed user created ==="
echo "Email: $EMAIL"
echo "Password: $PASSWORD"
echo ""
echo "SAVE THIS PASSWORD — it will not be shown again."
echo "You can change it later via: aws cognito-idp admin-set-user-password"
