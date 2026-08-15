# State locking + bootstrap: run once before terraform apply.
#
#   aws dynamodb create-table \
#     --table-name sre-platform-tf-lock \
#     --attribute-definitions AttributeName=LockID,AttributeType=S \
#     --key-schema AttributeName=LockID,KeyType=HASH \
#     --billing-mode PAY_PER_REQUEST
#
#   aws s3api create-bucket --bucket sre-platform-terraform-state --region us-east-1
#   aws s3api put-bucket-versioning \
#     --bucket sre-platform-terraform-state \
#     --versioning-configuration Status=Enabled
#   aws s3api put-bucket-encryption \
#     --bucket sre-platform-terraform-state \
#     --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
