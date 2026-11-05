provider "aws" {
  region  = var.region
  profile = var.aws_profile

  # Hard guard: Terraform refuses to run against any other account.
  # The id comes from the git-ignored terraform.tfvars; variables.tf also rejects forbidden accounts.
  allowed_account_ids = [var.allowed_account_id]

  default_tags {
    tags = local.base_tags
  }
}
