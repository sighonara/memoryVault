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
