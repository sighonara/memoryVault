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

# --- PostgreSQL client (for operational debugging against RDS) ---
dnf install -y postgresql16

# --- yt-dlp + ffmpeg ---
dnf install -y python3-pip
# ffmpeg-free is not in AL2023 default repos; install from amazon-linux-extras or skip
# yt-dlp works without ffmpeg (just can't merge formats)
pip3 install yt-dlp

# --- SSM Agent (pre-installed on standard AL2023 AMI) ---
# Non-fatal: SSM is operational tooling, not required for the app to run.
systemctl enable amazon-ssm-agent || echo "WARN: SSM agent enable failed"
systemctl start amazon-ssm-agent || echo "WARN: SSM agent start failed"

# --- Environment file for app container (reused by deploy script) ---
mkdir -p /etc/memoryvault
cat > /etc/memoryvault/env <<'ENVFILE'
SPRING_PROFILES_ACTIVE=aws,prod
DATABASE_URL=jdbc:postgresql://${db_endpoint}/memoryvault
DATABASE_USERNAME=${db_username}
DATABASE_PASSWORD=${db_password}
JWT_SECRET=${jwt_secret}
CORS_ALLOWED_ORIGINS=https://${domain_name}
MEMORYVAULT_STORAGE_S3__BUCKET=${s3_bucket}
MEMORYVAULT_STORAGE_S3__REGION=${region}
MEMORYVAULT_LOGGING_CLOUDWATCH__LOG__GROUP=${cloudwatch_log_group}
MEMORYVAULT_LOGGING_CLOUDWATCH__REGION=${region}
INTERNAL_API_KEY=${internal_api_key}
MEMORYVAULT_COGNITO_REGION=${region}
MEMORYVAULT_COGNITO_USER__POOL__ID=${cognito_user_pool_id}
ENVFILE
chmod 600 /etc/memoryvault/env

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
  --env-file /etc/memoryvault/env \
  "$ECR_REPO:latest"

echo "=== MemoryVault EC2 setup complete ==="
