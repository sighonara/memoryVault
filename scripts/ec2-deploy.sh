#!/usr/bin/env bash
set -euo pipefail

# Deployment script that runs ON the EC2 instance (called via SSM from GitHub Actions).
# Pulls the latest Docker image from ECR and restarts the container.
#
# Prerequisites (set up by Terraform user_data):
# - Docker installed and running
# - EC2 instance role has ECR pull permissions
# - /etc/memoryvault/env contains all app environment variables

REGION=$(curl -s http://169.254.169.254/latest/meta-data/placement/region)
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REPO="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/memoryvault"

echo "=== Deploying MemoryVault ==="
echo "Region: $REGION"
echo "ECR Repo: $ECR_REPO"

# Authenticate Docker to ECR
aws ecr get-login-password --region "$REGION" | \
  docker login --username AWS --password-stdin "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"

# Pull latest image
echo "Pulling latest image..."
docker pull "$ECR_REPO:latest"

# Stop and remove old container (if running)
echo "Stopping old container..."
docker stop memoryvault 2>/dev/null || true
docker rm memoryvault 2>/dev/null || true

# Start new container with env file
echo "Starting new container..."
docker run -d \
  --name memoryvault \
  --restart unless-stopped \
  -p 8085:8085 \
  -v /var/log/memoryvault:/app/logs \
  --env-file /etc/memoryvault/env \
  "$ECR_REPO:latest"

# Wait for health check
echo "Waiting for health check..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:8085/actuator/health > /dev/null 2>&1; then
    echo "=== Deploy complete — app is healthy ==="
    exit 0
  fi
  sleep 2
done

echo "=== WARNING: Health check did not pass within 60s ==="
docker logs --tail 50 memoryvault
exit 1
