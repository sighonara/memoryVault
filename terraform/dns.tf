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
