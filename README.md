<p align="right">
  <a href="./README.zh-CN.md"> <img alt="中文" src="https://img.shields.io/badge/中文-2da44e?style=for-the-badge"> </a>
</p>

# CasePlan

CasePlan is a legal case intake and case-plan generation system that supports two runtime modes:

- Local/traditional: Spring Boot Web + PostgreSQL + Redis
- AWS/serverless: API Gateway + Lambda + RDS PostgreSQL + SQS (+ DLQ)

The current project is **Lambda-first**, while preserving local development and debugging workflows.

## Features

- Case creation with validation, duplicate checks, and warning flows
- Asynchronous case-plan generation through queue decoupling
- Order status query (`pending / processing / completed / failed`)
- DLQ handling after max retries (`maxReceiveCount=3`)
- Web UI (`index.html`) and API integration support

## Functional Overview

### Goal and Scope

- Primary operator is a paralegal who submits case intake data and triggers plan generation
- The system generates a Legal Service Plan for attorney review, download, printing, or filing
- Main value is reducing manual drafting time and handling compliance-driven case volume

### Inputs and Validation

- Required fields include client name, referring attorney and bar number, case number, primary cause of action, remedy sought, and case documents
- Additional causes and prior legal actions are supported as optional inputs
- Server-side validation enforces required fields, format checks, and uniqueness constraints

### Generated Output

- One plan per case
- Default plan sections: Problem List, Goals, Attorney Interventions, Monitoring Plan
- Output is downloadable for offline review and external submission workflows

### Duplicate Detection and Warning Flow

- Definite duplicates are blocked as `ERROR`
- Potential duplicates are surfaced as confirmable `WARNING`
- Referring attorney bar-number conflicts are treated as hard validation errors

### Iteration Plan

- Current scope is MVP: intake, validation, dedup, generation, download, and status tracking
- Future enhancements: online attorney review/editing, role-based permissions, audit logs, version history, bulk import, draft save

## Tech Stack

- Java 11
- Spring Boot 2.7
- Spring Data JPA
- PostgreSQL
- Redis (local queue mode)
- AWS Lambda, API Gateway, SQS, RDS PostgreSQL
- Maven, JUnit4, Mockito, JaCoCo

## Project Layout

```text
src/main/java/com/caseplan
  adapter/
    in/
      web/        # Spring MVC controllers
      lambda/     # Lambda handlers
      queue/      # Local consumer
      intake/     # Multi-source intake adapters
    out/
      persistence/# JPA repositories
      queue/      # Redis/SQS adapters
      llm/        # OpenAI/Claude adapters
  application/
    service/      # Use-case services
    port/         # Inbound/outbound ports
  domain/
    model/        # Domain models

src/main/resources/static/index.html  # Web UI
infra/terraform-replica/              # Ephemeral infra replication (Terraform)
scripts/smoke_apigw_lambda.sh         # API Gateway -> Lambda smoke test
```

## Runtime Modes

### Local Mode (Spring Web)

- API base: `/api/caseplans`
- Queue provider: Redis (`QUEUE_PROVIDER=redis`)
- Background processor: `CasePlanConsumer`

### AWS Mode (Lambda)

- `POST /orders` -> `CreateOrderHandler`
- `GET /orders/{id}` -> `GetOrderStatusHandler`
- SQS event source -> `GenerateCasePlanWorkerHandler`
- Queue provider: SQS (`QUEUE_PROVIDER=sqs`)

## Quick Start (Local)

### Option A: Start dependencies with Docker Compose, run app with Maven

```bash
docker compose up -d db redis
mvn spring-boot:run
```

Access:

- App: `http://localhost:8080`
- UI: `http://localhost:8080/index.html`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

### Option B: Full compose stack (including test container)

```bash
docker compose up --build
```

## Configuration

Main configuration file: `src/main/resources/application.yaml`.

### Database

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

Local default URL: `jdbc:postgresql://localhost:5433/dev_db`

### Queue

- `QUEUE_PROVIDER`: `redis` or `sqs`
- `QUEUE_URL`: required when provider is `sqs`
- `AWS_REGION`: default `us-east-2`

### LLM

- `LLM_PROVIDER`: `openai` or `claude`
- `LLM_OPENAI_API_KEY` / `DEEPSEEK_API_KEY`
- `LLM_OPENAI_BASE_URL` (DeepSeek-compatible by default)
- `ANTHROPIC_API_KEY`

## API Summary

### Local Web API

- `POST /api/caseplans`
- `GET /api/caseplans`
- `GET /api/caseplans/{id}`
- `GET /api/caseplans/{id}/status`
- `POST /api/caseplans/{id}/retry`
- `GET /api/caseplans/{id}/download`
- `POST /api/clients`, `PUT /api/clients/{id}`, `DELETE /api/clients/{id}`
- `POST /api/attorneys`
- `POST /api/intake?source=jsonA|jsonB|xml`

### Lambda/API Gateway API

- `POST /orders`
- `GET /orders/{id}`

Example:

```bash
curl -X POST "$API/orders" \
  -H 'content-type: application/json' \
  -d '{"clientFirstName":"A","clientLastName":"B","attorneyName":"C","barNumber":"BAR-1","primaryCauseOfAction":"Contract","remedySought":"Damages"}'

curl "$API/orders/123"
```

## Testing and Quality

```bash
# Unit tests (excludes *IT)
mvn test

# Unit + integration + coverage gates
mvn verify
```

JaCoCo thresholds in `pom.xml`:

- Line coverage >= 90%
- Branch coverage >= 90%

## Monitoring and Smoke Test

- Monitoring plan: `monitoring.md`
- Lambda metrics: CloudWatch EMF (namespace `CasePlan/Lambda`) — zero-dependency, stdout-based
- Grafana dashboards:
  - `caseplan-overview.json` — Prometheus (local Spring Boot)
  - `lambda-overview.json` — CloudWatch (Lambda + SQS + EMF business metrics)
- CloudWatch datasource: `monitoring/grafana/provisioning/datasources/cloudwatch.yml`
- Smoke script: `scripts/smoke_apigw_lambda.sh`

```bash
API_BASE_URL="https://<api-id>.execute-api.<region>.amazonaws.com" \
  bash scripts/smoke_apigw_lambda.sh
```

## Terraform (Ephemeral AWS Replica)

Folder: `infra/terraform-replica/`

Used to provision a temporary replica of API/Lambda/SQS/IAM/SG for validation, then destroy it.

```bash
cd infra/terraform-replica
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform apply
terraform destroy
```

Details: `infra/terraform-replica/README.md`

## CI/CD

### CI — `.github/workflows/ci.yml`

- Uses PostgreSQL + Redis service containers
- Runs: `mvn -B -Dspring.profiles.active=it verify`
- Triggered on code/build file changes (`src/**`, `pom.xml`, `Dockerfile`, etc.)

### CD — `.github/workflows/cd-terraform.yml`

- Triggered on push to `main` (infra/src changes), PR, or manual `workflow_dispatch`
- Uses **GitHub OIDC** to assume an AWS IAM Role (no static credentials)
- Pipeline: Build Lambda artifact → Validate config → Render tfvars → Terraform init/plan
- `apply` job runs only on push to main or manual dispatch with `apply=true`
- Terraform state stored in S3 + DynamoDB lock

### Branch Protection (Ruleset)

- Direct push to `main` requires admin role (bypass)
- Others must go through PR with 1 approval + CI `test` check passing

## Lambda Packaging

Terraform expects `target/legal-caseplan-lambda.zip`.

If missing, build it:

```bash
mvn -DskipTests package
cd target/lambda && zip -r ../legal-caseplan-lambda.zip .
```

## Security Notes

- Do not commit DB passwords or LLM keys in code/tfvars.
- For production, prefer AWS Secrets Manager/SSM for secret management.
- If UI is on CloudFront and API is on API Gateway, configure CORS explicitly.
- If SQS uses an Interface VPC Endpoint, allow Lambda SG -> VPCE SG:443.

## Docs

- Architecture: `architect.md`
- Monitoring: `monitoring.md`
- OpenAPI: `docs/api/openapi.yaml`
- Backend playbook: `docs/backend-api-playbook.md`
- Terraform CD notes: `docs/cd-terraform-oidc.md`
