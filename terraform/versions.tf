terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.25"
    }
  }
  backend "s3" {
    bucket         = "sre-platform-terraform-state"
    key            = "eks/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "sre-platform-tf-lock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.region
}

provider "kubernetes" {
  host                   = aws_eks_cluster.sre.endpoint
  cluster_ca_certificate = base64decode(aws_eks_cluster.sre.certificate_authority[0].data)
  token                  = data.aws_eks_cluster_auth.sre.token
}
