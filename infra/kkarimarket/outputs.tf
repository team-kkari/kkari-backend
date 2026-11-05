output "instance_ids" {
  description = "EC2 instance ids by role (web, batch, dev)."
  value       = { for role, inst in aws_instance.app : role => inst.id }
}

output "instance_names" {
  description = "EC2 Name tags by role."
  value       = { for role, inst in aws_instance.app : role => inst.tags["Name"] }
}

output "instance_private_ips" {
  description = "Private IPs by role. No instance has a public IP."
  value       = { for role, inst in aws_instance.app : role => inst.private_ip }
}

output "db_identifier" {
  description = "RDS instance identifier."
  value       = aws_db_instance.prod.identifier
}

output "db_endpoint" {
  description = "RDS endpoint (host:port). The password is only in the git-ignored state."
  value       = aws_db_instance.prod.endpoint
}

output "orphan_volume_id" {
  description = "Id of the intentionally unattached EBS volume."
  value       = aws_ebs_volume.orphan.id
}

output "security_group_ids" {
  description = "Security group ids."
  value = {
    prod_app = aws_security_group.prod_app.id
    dev_app  = aws_security_group.dev_app.id
    prod_db  = aws_security_group.prod_db.id
  }
}
