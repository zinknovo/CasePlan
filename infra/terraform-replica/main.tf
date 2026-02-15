terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

resource "random_string" "suffix" {
  length  = 6
  special = false
  upper   = false
}

locals {
  name_prefix               = "${var.project_name}-${var.environment}-${random_string.suffix.result}"
  lambda_security_group_ids = concat([aws_security_group.lambda_sg.id], var.additional_lambda_security_group_ids)
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
    Purpose     = "ephemeral-replica"
  }
}

resource "aws_security_group" "lambda_sg" {
  name        = "${local.name_prefix}-lambda-sg"
  description = "Lambda SG for ephemeral replica"
  vpc_id      = var.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, { Name = "${local.name_prefix}-lambda-sg" })
}

resource "aws_security_group_rule" "rds_ingress_from_lambda" {
  type                     = "ingress"
  description              = "Allow replica lambda SG to connect Postgres"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = var.rds_security_group_id
  source_security_group_id = aws_security_group.lambda_sg.id
}

resource "aws_security_group_rule" "sqs_vpce_ingress_from_lambda" {
  count                    = var.sqs_vpce_security_group_id == "" ? 0 : 1
  type                     = "ingress"
  description              = "Allow replica lambda SG to reach SQS VPC endpoint over HTTPS"
  from_port                = 443
  to_port                  = 443
  protocol                 = "tcp"
  security_group_id        = var.sqs_vpce_security_group_id
  source_security_group_id = aws_security_group.lambda_sg.id
}

resource "aws_s3_bucket" "lambda_artifacts" {
  bucket        = "${local.name_prefix}-lambda-artifacts"
  force_destroy = true
  tags          = local.common_tags
}

resource "aws_s3_bucket_public_access_block" "lambda_artifacts" {
  bucket                  = aws_s3_bucket.lambda_artifacts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_object" "lambda_zip" {
  bucket = aws_s3_bucket.lambda_artifacts.id
  key    = "artifacts/legal-caseplan-lambda.zip"
  source = var.lambda_zip_path
  etag   = filemd5(var.lambda_zip_path)
}

resource "aws_sqs_queue" "dlq" {
  name                       = "${local.name_prefix}-orders-dlq"
  message_retention_seconds  = 1209600
  visibility_timeout_seconds = 30
  tags                       = local.common_tags
}

resource "aws_sqs_queue" "orders" {
  name                       = "${local.name_prefix}-orders"
  visibility_timeout_seconds = 180
  message_retention_seconds  = 345600
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = 3
  })
  tags = local.common_tags
}

resource "aws_iam_role" "lambda_role" {
  name = "${local.name_prefix}-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "basic_logs" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "vpc_access" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_iam_role_policy" "sqs_access" {
  name = "${local.name_prefix}-sqs-policy"
  role = aws_iam_role.lambda_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "SendToOrdersQueue"
        Effect   = "Allow"
        Action   = ["sqs:SendMessage", "sqs:GetQueueAttributes", "sqs:GetQueueUrl"]
        Resource = [aws_sqs_queue.orders.arn]
      },
      {
        Sid    = "ConsumeOrdersQueue"
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:ChangeMessageVisibility",
          "sqs:GetQueueAttributes",
          "sqs:GetQueueUrl"
        ]
        Resource = [aws_sqs_queue.orders.arn, aws_sqs_queue.dlq.arn]
      }
    ]
  })
}

resource "aws_cloudwatch_log_group" "create_order" {
  name              = "/aws/lambda/${local.name_prefix}-create-order"
  retention_in_days = 7
  tags              = local.common_tags
}

resource "aws_cloudwatch_log_group" "get_order_status" {
  name              = "/aws/lambda/${local.name_prefix}-get-order-status"
  retention_in_days = 7
  tags              = local.common_tags
}

resource "aws_cloudwatch_log_group" "worker" {
  name              = "/aws/lambda/${local.name_prefix}-generate-caseplan-worker"
  retention_in_days = 7
  tags              = local.common_tags
}

resource "aws_lambda_function" "create_order" {
  function_name    = "${local.name_prefix}-create-order"
  role             = aws_iam_role.lambda_role.arn
  runtime          = "java11"
  handler          = "com.caseplan.adapter.in.lambda.CreateOrderHandler::handleRequest"
  s3_bucket        = aws_s3_bucket.lambda_artifacts.id
  s3_key           = aws_s3_object.lambda_zip.key
  source_code_hash = filebase64sha256(var.lambda_zip_path)
  memory_size      = 2048
  timeout          = 60

  vpc_config {
    subnet_ids         = var.lambda_subnet_ids
    security_group_ids = local.lambda_security_group_ids
  }

  environment {
    variables = {
      SPRING_DATASOURCE_URL      = var.datasource_url
      SPRING_DATASOURCE_USERNAME = var.datasource_username
      SPRING_DATASOURCE_PASSWORD = var.datasource_password
      QUEUE_PROVIDER             = "sqs"
      QUEUE_URL                  = aws_sqs_queue.orders.id
      LLM_PROVIDER               = var.llm_provider
      OPENAI_API_KEY             = var.openai_api_key
      DEEPSEEK_API_KEY           = var.deepseek_api_key
    }
  }

  tags = local.common_tags

  depends_on = [
    aws_iam_role_policy_attachment.basic_logs,
    aws_iam_role_policy_attachment.vpc_access,
    aws_iam_role_policy.sqs_access,
    aws_cloudwatch_log_group.create_order,
    aws_s3_object.lambda_zip
  ]
}

resource "aws_lambda_function" "get_order_status" {
  function_name    = "${local.name_prefix}-get-order-status"
  role             = aws_iam_role.lambda_role.arn
  runtime          = "java11"
  handler          = "com.caseplan.adapter.in.lambda.GetOrderStatusHandler::handleRequest"
  s3_bucket        = aws_s3_bucket.lambda_artifacts.id
  s3_key           = aws_s3_object.lambda_zip.key
  source_code_hash = filebase64sha256(var.lambda_zip_path)
  memory_size      = 2048
  timeout          = 60

  vpc_config {
    subnet_ids         = var.lambda_subnet_ids
    security_group_ids = local.lambda_security_group_ids
  }

  environment {
    variables = {
      SPRING_DATASOURCE_URL      = var.datasource_url
      SPRING_DATASOURCE_USERNAME = var.datasource_username
      SPRING_DATASOURCE_PASSWORD = var.datasource_password
    }
  }

  tags = local.common_tags

  depends_on = [
    aws_iam_role_policy_attachment.basic_logs,
    aws_iam_role_policy_attachment.vpc_access,
    aws_cloudwatch_log_group.get_order_status,
    aws_s3_object.lambda_zip
  ]
}

resource "aws_lambda_function" "generate_caseplan_worker" {
  function_name    = "${local.name_prefix}-generate-caseplan-worker"
  role             = aws_iam_role.lambda_role.arn
  runtime          = "java11"
  handler          = "com.caseplan.adapter.in.lambda.GenerateCasePlanWorkerHandler::handleRequest"
  s3_bucket        = aws_s3_bucket.lambda_artifacts.id
  s3_key           = aws_s3_object.lambda_zip.key
  source_code_hash = filebase64sha256(var.lambda_zip_path)
  memory_size      = 3008
  timeout          = 180

  vpc_config {
    subnet_ids         = var.lambda_subnet_ids
    security_group_ids = local.lambda_security_group_ids
  }

  environment {
    variables = {
      SPRING_DATASOURCE_URL      = var.datasource_url
      SPRING_DATASOURCE_USERNAME = var.datasource_username
      SPRING_DATASOURCE_PASSWORD = var.datasource_password
      LLM_PROVIDER               = var.llm_provider
      OPENAI_API_KEY             = var.openai_api_key
      DEEPSEEK_API_KEY           = var.deepseek_api_key
    }
  }

  tags = local.common_tags

  depends_on = [
    aws_iam_role_policy_attachment.basic_logs,
    aws_iam_role_policy_attachment.vpc_access,
    aws_iam_role_policy.sqs_access,
    aws_cloudwatch_log_group.worker,
    aws_s3_object.lambda_zip
  ]
}

resource "aws_lambda_event_source_mapping" "worker_sqs" {
  event_source_arn                   = aws_sqs_queue.orders.arn
  function_name                      = aws_lambda_function.generate_caseplan_worker.arn
  batch_size                         = 1
  function_response_types            = ["ReportBatchItemFailures"]
  maximum_batching_window_in_seconds = 0
  enabled                            = true
}

resource "aws_apigatewayv2_api" "orders_api" {
  name          = "${local.name_prefix}-api"
  protocol_type = "HTTP"

  cors_configuration {
    allow_origins = var.cors_allow_origins
    allow_methods = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
    allow_headers = ["content-type", "authorization"]
    max_age       = 3600
  }

  tags = local.common_tags
}

resource "aws_apigatewayv2_integration" "create_order" {
  api_id                 = aws_apigatewayv2_api.orders_api.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.create_order.invoke_arn
  integration_method     = "POST"
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_integration" "get_order_status" {
  api_id                 = aws_apigatewayv2_api.orders_api.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.get_order_status.invoke_arn
  integration_method     = "POST"
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "post_orders" {
  api_id    = aws_apigatewayv2_api.orders_api.id
  route_key = "POST /orders"
  target    = "integrations/${aws_apigatewayv2_integration.create_order.id}"
}

resource "aws_apigatewayv2_route" "get_order_by_id" {
  api_id    = aws_apigatewayv2_api.orders_api.id
  route_key = "GET /orders/{id}"
  target    = "integrations/${aws_apigatewayv2_integration.get_order_status.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.orders_api.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    detailed_metrics_enabled = false
    throttling_burst_limit   = 200
    throttling_rate_limit    = 100
  }

  tags = local.common_tags
}

resource "aws_lambda_permission" "api_invoke_create" {
  statement_id  = "AllowExecutionFromApiGatewayCreate"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.create_order.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.orders_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_invoke_get" {
  statement_id  = "AllowExecutionFromApiGatewayGet"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.get_order_status.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.orders_api.execution_arn}/*/*"
}
