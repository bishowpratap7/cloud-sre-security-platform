output "cluster_endpoint" {
  description = "Endpoint for the EKS cluster API server"
  value       = aws_eks_cluster.sre.endpoint
}

output "cluster_name" {
  description = "EKS cluster name"
  value       = aws_eks_cluster.sre.name
}

output "cluster_ca_certificate" {
  description = "Base64 CA certificate for the EKS cluster"
  value       = aws_eks_cluster.sre.certificate_authority[0].data
}

output "kubeconfig_command" {
  description = "Command to configure kubectl for this cluster"
  value       = "aws eks update-kubeconfig --region ${var.region} --name ${aws_eks_cluster.sre.name}"
}

output "node_group_role" {
  description = "IAM role ARN used by the managed node group"
  value       = aws_iam_role.node.arn
}
