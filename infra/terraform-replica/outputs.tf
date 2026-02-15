output "api_endpoint" {
  description = "Replica API endpoint base URL"
  value       = aws_apigatewayv2_api.orders_api.api_endpoint
}

output "post_orders_url" {
  description = "Create order endpoint"
  value       = "${aws_apigatewayv2_api.orders_api.api_endpoint}/orders"
}

output "get_order_url_template" {
  description = "Get order endpoint template"
  value       = "${aws_apigatewayv2_api.orders_api.api_endpoint}/orders/{id}"
}

output "queue_url" {
  description = "Replica SQS queue URL"
  value       = aws_sqs_queue.orders.id
}

output "dlq_url" {
  description = "Replica DLQ URL"
  value       = aws_sqs_queue.dlq.id
}

output "create_order_lambda" {
  value = aws_lambda_function.create_order.function_name
}

output "get_order_status_lambda" {
  value = aws_lambda_function.get_order_status.function_name
}

output "worker_lambda" {
  value = aws_lambda_function.generate_caseplan_worker.function_name
}
