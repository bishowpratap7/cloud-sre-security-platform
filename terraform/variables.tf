variable "region" {
  type    = string
  default = "us-east-1"
}

variable "cluster_name" {
  type    = string
  default = "sre-platform"
}

variable "cluster_version" {
  type    = string
  default = "1.30"
}

variable "environment" {
  type    = string
  default = "production"
}

variable "node_group" {
  description = "Managed node group sizing"
  type = object({
    min_size       = number
    max_size       = number
    desired_size   = number
    instance_types = list(string)
  })
  default = {
    min_size       = 2
    max_size       = 8
    desired_size   = 3
    instance_types = ["m5.large"]
  }
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}
