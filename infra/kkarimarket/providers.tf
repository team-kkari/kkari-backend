provider "aws" {
  region  = var.region
  profile = var.aws_profile

  # Hard guard: Terraform refuses to run against any other account.
  # variables.tf pins this id to the personal demo account (see CLAUDE.md).
  allowed_account_ids = [var.allowed_account_id]

  default_tags {
    tags = local.base_tags
  }
}
