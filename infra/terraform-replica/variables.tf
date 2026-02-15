variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-2"
}

variable "project_name" {
  description = "Project name prefix"
  type        = string
  default     = "caseplan"
}

variable "environment" {
  description = "Environment label"
  type        = string
  default     = "replica"
}

variable "vpc_id" {
  description = "VPC ID where Lambda runs"
  type        = string
}

variable "lambda_subnet_ids" {
  description = "Private subnet IDs for Lambda ENIs"
  type        = list(string)
}

variable "additional_lambda_security_group_ids" {
  description = "Optional existing SGs to attach to replica Lambdas (for VPC endpoint/NAT compatibility)"
  type        = list(string)
  default     = []
}

variable "rds_security_group_id" {
  description = "RDS security group ID to allow inbound 5432 from replica Lambda SG"
  type        = string
}

variable "sqs_vpce_security_group_id" {
  description = "Optional Interface VPC Endpoint SG ID for SQS; if set, allow 443 ingress from replica Lambda SG"
  type        = string
  default     = ""
}

variable "datasource_url" {
  description = "JDBC URL for Postgres"
  type        = string
}

variable "datasource_username" {
  description = "DB username"
  type        = string
}

variable "datasource_password" {
  description = "DB password"
  type        = string
  sensitive   = true
}

variable "lambda_zip_path" {
  description = "Path to Lambda deployment zip"
  type        = string
  default     = "../../target/legal-caseplan-lambda.zip"
}

variable "llm_provider" {
  description = "LLM provider for worker"
  type        = string
  default     = "openai"
}

variable "openai_api_key" {
  description = "Optional OpenAI-compatible API key"
  type        = string
  sensitive   = true
  default     = ""
}

variable "deepseek_api_key" {
  description = "Optional DeepSeek API key"
  type        = string
  sensitive   = true
  default     = ""
}

variable "cors_allow_origins" {
  description = "CORS allowed origins for replica API"
  type        = list(string)
  default     = ["*"]
}
