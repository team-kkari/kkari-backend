# kkariMarket: demo "customer" infrastructure for the Wayco optimization service.
#
# Story: a small online shop preparing for a Black Friday traffic peak. The
# current state intentionally has gaps (single web server in one AZ, single-AZ
# DB) and waste (oversized idle batch server, always-on dev server, unattached
# volume). Redundancy / load balancing / autoscaling are NOT built here; the
# service is expected to propose them.
#
# Naming: <project>-<env>-<resource>   Required tags: project, env, owner

locals {
  base_tags = {
    project = var.project
    owner   = var.owner
  }

  prefix_prod = "${var.project}-prod"
  prefix_dev  = "${var.project}-dev"

  # `env` is the only required tag that differs per resource, so it cannot live in
  # provider default_tags. Always merge one of these into a resource's tags.
  tags_prod = { env = "prod" }
  tags_dev  = { env = "dev" }

  # Every EC2 instance of the demo customer. Key = short role name.
  instances = {
    web = {
      name          = "${local.prefix_prod}-web-1"
      env           = "prod"
      instance_type = var.web_instance_type
      sg_id         = aws_security_group.prod_app.id
      profile       = "web"
    }
    batch = {
      name          = "${local.prefix_prod}-batch-1"
      env           = "prod"
      instance_type = var.batch_instance_type
      sg_id         = aws_security_group.prod_app.id
      profile       = "batch"
    }
    dev = {
      name          = "${local.prefix_dev}-app-1"
      env           = "dev"
      instance_type = var.dev_instance_type
      sg_id         = aws_security_group.dev_app.id
      profile       = "dev"
    }
  }
}

# ---------------------------------------------------------------------------
# Network (default VPC on purpose: no NAT gateway, no public IPs, no inbound)
# ---------------------------------------------------------------------------

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }

  filter {
    name   = "default-for-az"
    values = ["true"]
  }
}

data "aws_subnet" "primary" {
  vpc_id            = data.aws_vpc.default.id
  availability_zone = var.availability_zone
  default_for_az    = true
}

data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }
}

# ---------------------------------------------------------------------------
# Security groups
# ---------------------------------------------------------------------------

resource "aws_security_group" "prod_app" {
  name        = "${local.prefix_prod}-app-sg"
  description = "kkarimarket prod app servers (web and batch). No inbound."
  vpc_id      = data.aws_vpc.default.id

  tags = merge(local.tags_prod, { Name = "${local.prefix_prod}-app-sg" })
}

resource "aws_security_group" "dev_app" {
  name        = "${local.prefix_dev}-app-sg"
  description = "kkarimarket dev app server. No inbound, no outbound."
  vpc_id      = data.aws_vpc.default.id

  tags = merge(local.tags_dev, { Name = "${local.prefix_dev}-app-sg" })
}

resource "aws_security_group" "prod_db" {
  name        = "${local.prefix_prod}-db-sg"
  description = "kkarimarket prod database. PostgreSQL from prod app servers only."
  vpc_id      = data.aws_vpc.default.id

  tags = merge(local.tags_prod, { Name = "${local.prefix_prod}-db-sg" })
}

resource "aws_vpc_security_group_ingress_rule" "prod_db_from_app" {
  security_group_id            = aws_security_group.prod_db.id
  referenced_security_group_id = aws_security_group.prod_app.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "PostgreSQL from prod app servers"

  tags = merge(local.tags_prod, { Name = "${local.prefix_prod}-db-from-app" })
}

resource "aws_vpc_security_group_egress_rule" "prod_app_to_db" {
  security_group_id            = aws_security_group.prod_app.id
  referenced_security_group_id = aws_security_group.prod_db.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "PostgreSQL to prod database"

  tags = merge(local.tags_prod, { Name = "${local.prefix_prod}-app-to-db" })
}

# ---------------------------------------------------------------------------
# Database (single AZ on purpose: the demo story is a database single point of failure)
# ---------------------------------------------------------------------------

resource "random_password" "db" {
  length  = 24
  special = false
}

resource "aws_db_subnet_group" "prod" {
  name        = "${local.prefix_prod}-db-subnets"
  description = "kkarimarket prod database subnets (default VPC)"
  subnet_ids  = data.aws_subnets.default.ids

  tags = merge(local.tags_prod, { Name = "${local.prefix_prod}-db-subnets" })
}

resource "aws_db_instance" "prod" {
  identifier = "${local.prefix_prod}-db"

  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  db_name  = var.project
  username = var.db_username
  password = random_password.db.result

  allocated_storage = var.db_allocated_storage_gb
  storage_type      = "gp3"
  storage_encrypted = true

  availability_zone      = var.availability_zone
  multi_az               = false
  publicly_accessible    = false
  db_subnet_group_name   = aws_db_subnet_group.prod.name
  vpc_security_group_ids = [aws_security_group.prod_db.id]

  backup_retention_period = 1
  # The engine version is pinned, so minor upgrades must not drift the state.
  auto_minor_version_upgrade = false
  copy_tags_to_snapshot      = true
  apply_immediately          = true

  # Demo teardown must be one command: no final snapshot, no deletion protection.
  skip_final_snapshot = true
  deletion_protection = false

  tags = merge(
    local.tags_prod,
    { Name = "${local.prefix_prod}-db" },
    var.db_do_not_change ? { "do-not-change" = "true" } : {}
  )
}

# ---------------------------------------------------------------------------
# EC2 instances (no public IP, no key pair, no instance profile, IMDSv2 only)
# ---------------------------------------------------------------------------

resource "aws_instance" "app" {
  for_each = local.instances

  ami                         = data.aws_ami.al2023.id
  instance_type               = each.value.instance_type
  subnet_id                   = data.aws_subnet.primary.id
  vpc_security_group_ids      = [each.value.sg_id]
  associate_public_ip_address = false
  monitoring                  = false

  # batch_peak only matters for the batch server; keeping it out of the other
  # user_data prevents web/dev from being recreated when the toggle changes.
  # NOTE: any user_data change recreates the instance and resets its CloudWatch
  # history, so avoid editing the template once the demo data has built up.
  user_data = templatefile("${path.module}/templates/load-generator.sh.tftpl", {
    profile    = each.value.profile
    batch_peak = each.value.profile == "batch" ? var.batch_nightly_peak : false
  })
  user_data_replace_on_change = true

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  # Burstable (t2/t3/t3a/t4g) instances only: standard mode prevents surplus-credit charges.
  dynamic "credit_specification" {
    for_each = can(regex("^t[0-9]+[a-z]*\\.", each.value.instance_type)) ? [1] : []

    content {
      cpu_credits = "standard"
    }
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size_gb
    encrypted             = true
    delete_on_termination = true
  }

  tags = {
    Name = each.value.name
    env  = each.value.env
    role = each.key
  }

  volume_tags = merge(local.base_tags, {
    Name = "${each.value.name}-root"
    env  = each.value.env
  })

  lifecycle {
    # A newer AL2023 AMI must not replace running demo servers.
    ignore_changes = [ami]
  }
}

# ---------------------------------------------------------------------------
# Intentionally unattached volume (waste-diagnosis scenario)
# ---------------------------------------------------------------------------

resource "aws_ebs_volume" "orphan" {
  availability_zone = var.availability_zone
  size              = var.orphan_volume_size_gb
  type              = "gp3"
  encrypted         = true

  tags = merge(local.tags_prod, { Name = "${local.prefix_prod}-orphan-vol" })
}
