# Ephemeral Terraform Replica (CasePlan)

This directory provisions an **ephemeral replica** of core CasePlan infra:

- 3 Lambda functions (Java 11, same handlers as prod app)
  - `CreateOrderHandler`
  - `GetOrderStatusHandler`
  - `GenerateCasePlanWorkerHandler`
- SQS queue + DLQ (`maxReceiveCount = 3`)
- Event source mapping (SQS -> worker Lambda)
- HTTP API Gateway routes
  - `POST /orders`
  - `GET /orders/{id}`
- IAM role/policies and CloudWatch log groups
- Dedicated Lambda Security Group + ingress rule into existing RDS SG (5432)

## Scope

This is intentionally **ephemeral/simulation infra**:

- Reuses existing VPC/subnets/RDS.
- Supports pure-new-SG setup across AWS accounts:
  - create a new Lambda SG
  - add one ingress rule on the SQS Interface VPCE SG (443 from new Lambda SG)
- You can still optionally attach existing Lambda SGs as a fallback.
- Does not create a separate RDS instance.
- Resource names are randomized per apply to avoid collisions.

## Prereqs

- Terraform >= 1.5
- AWS CLI authenticated to target account/region
- Built Lambda artifact at `target/legal-caseplan-lambda.zip`

Build artifact (from project root):

```bash
mvn -q -DskipTests package
```

## Usage

1. Copy vars file:

```bash
cp terraform.tfvars.example terraform.tfvars
```

2. Fill required values in `terraform.tfvars`.

3. Apply:

```bash
terraform init
terraform apply -auto-approve
```

4. Smoke test (replace API endpoint from output):

```bash
curl -s -X POST "$API/orders" \
  -H 'Content-Type: application/json' \
  -d '{"clientFirstName":"Smoke","clientLastName":"Replica","attorneyName":"Terraform","barNumber":"B123","primaryCauseOfAction":"Contract","remedySought":"Damages"}'

curl -s "$API/orders/<id>"
```

5. Destroy when done:

```bash
terraform destroy -auto-approve
```

## Notes

- Worker Lambda needs outbound internet/NAT and valid LLM key to generate final content.
- If your VPC uses an SQS Interface Endpoint, set `sqs_vpce_security_group_id` so Terraform can allow HTTPS from the new Lambda SG.
- If no key is set, order creation/status still works; generation can fail and eventually land in DLQ.

## New Backend API Surface (Web App)

The Spring Boot app now includes additional REST endpoints:

- Clients
  - `POST /api/clients`
  - `GET /api/clients`
  - `GET /api/clients/{id}`
  - `PUT /api/clients/{id}`
  - `DELETE /api/clients/{id}`
  - `GET /api/clients/{id}/caseplans`
- Attorneys
  - `POST /api/attorneys`
- Caseplans
  - `GET /api/caseplans?status=...`
  - `GET /api/caseplans/{id}/status`
  - `GET /api/caseplans/{id}/download`
  - `POST /api/caseplans/{id}/retry`

### Deployment Impact

This Terraform replica currently exposes only Lambda order endpoints (`/orders`). To expose the new `/api/...` routes in AWS, choose one path:

1. Add dedicated Lambda handlers + API Gateway routes for each new endpoint.
2. Deploy Spring Boot as a long-running service (ECS/EKS/EC2) behind ALB/API Gateway.
3. Package Spring Boot with `aws-serverless-java-container` and map API Gateway proxy to one Lambda.

API Gateway CORS methods should include `GET/POST/PUT/DELETE/OPTIONS` to match backend methods.
