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
    description = "HTTPS (Caddy with LetsEncrypt)"
  }

  # SSH (restricted to your IP)
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
    description = "SSH access from admin IP"
  }

  # Spring Boot app port (8085) — reachable only from the sync-scheduler Lambda SG.
  # Caddy terminates TLS on :443 and reverse-proxies to :8085 locally, so public
  # traffic never hits this port directly.
  ingress {
    from_port       = 8085
    to_port         = 8085
    protocol        = "tcp"
    security_groups = [aws_security_group.lambda_sync.id]
    description     = "Internal sync from Lambda schedulers"
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
