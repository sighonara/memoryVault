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
