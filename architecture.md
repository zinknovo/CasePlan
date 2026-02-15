# Case Plan System Architecture

## Overview

Current codebase follows hexagonal-style package boundaries:

- `domain`: core entities (`CasePlan`, `CaseInfo`, `Client`, `Attorney`)
- `application`: use-case services + ports
- `adapter/in`: inbound adapters (web, lambda, queue consumer, intake transforms)
- `adapter/out`: outbound adapters (persistence, queue, llm)

```text
Clients
  -> Web UI / HTTP API (/api/caseplans)
  -> API Gateway (/orders)
  -> SQS (async)

                    +------------------------------------+
                    | adapter/in                         |
                    | - web/controller/*                |
                    | - lambda/CreateOrderHandler       |
                    | - lambda/GetOrderStatusHandler    |
                    | - queue/CasePlanConsumer          |
                    +----------------+-------------------+
                                     |
                                     v
                    +------------------------------------+
                    | application/service                |
                    | - CasePlanService                 |
                    | - CasePlanGenerationService       |
                    +----------------+-------------------+
                                     |
                +--------------------+---------------------+
                v                                          v
      +--------------------------+               +--------------------------+
      | application/port/out     |               | adapter/out/persistence  |
      | - QueuePort              |<------------->| Spring Data JPA repos    |
      | - LLMService             |               +-------------+------------+
      +------------+-------------+                             |
                   |                                           v
                   v                                     PostgreSQL / RDS
      +--------------------------+
      | adapter/out              |
      | - queue: Redis/SQS       |
      | - llm: OpenAI/Claude     |
      +--------------------------+

API Gateway POST /orders
  -> create_order_java
  -> QueuePort.enqueue(...)
  -> SQS main queue (dev_sqs_caseplan)
        |
        | event source mapping
        v
  -> generate_caseplan_worker_java (GenerateCasePlanWorkerHandler)
        |
        | processWithRetry (up to 3 receives via SQS redrive policy)
        v
     success -> DB status completed
     failure -> SQS DLQ (dev_sqs_caseplan_dlq)

API Gateway GET /orders/{id}
  -> get_order_status_java
  -> DB read status/content
```

## Runtime Flows

### 1) Create order (API Gateway -> Lambda)

- Route: `POST /orders`
- Handler: `adapter/in/lambda/CreateOrderHandler`
- Path:
  - validate input
  - call `CasePlanController.create(...)`
  - `CasePlanService` persists case data (`pending`)
  - `QueuePort.enqueue(planId)` -> Redis (local) or SQS (AWS)

### 2) Worker generation (SQS -> Lambda)

- Trigger: SQS event source mapping
- Handler: `adapter/in/lambda/GenerateCasePlanWorkerHandler`
- Shared logic: `application/service/CasePlanGenerationService#processWithRetry`
- Path:
  - load `CasePlan`
  - status `pending/processing` -> `processing`
  - call LLM
  - write `completed` + generated content, or `failed` + error

### 3) Query order status (API Gateway -> Lambda)

- Route: `GET /orders/{id}`
- Handler: `adapter/in/lambda/GetOrderStatusHandler`
- Path:
  - call `CasePlanService.getStatus(id)`
  - return `pending|processing|completed|failed`

### 4) Local/background consumer (Redis mode)

- Component: `adapter/in/queue/CasePlanConsumer`
- Starts only when `caseplan.consumer.enabled=true`
- Performs stale recovery and pending reconciliation for Redis queue mode.

## Key Ports and Adapters

| Port | Implementation | Purpose |
|---|---|---|
| `application.port.out.QueuePort` | `RedisQueueAdapter`, `SqsQueueAdapter` | enqueue async plan generation |
| `application.port.out.LLMService` | `OpenAIService`, `ClaudeService` | generate case plan text |

Persistence uses Spring Data repositories in `adapter/out/persistence/*` against PostgreSQL.

## Deployment Modes

### Local

- API: Spring Boot web controllers
- Queue: Redis
- DB: local/docker PostgreSQL
- Background worker: `CasePlanConsumer`

### AWS

- API: API Gateway -> Lambda handlers
- Queue: SQS (+ DLQ)
- DB: RDS PostgreSQL
- Worker: SQS -> `generate_caseplan_worker_java`
- Metrics/logs: CloudWatch (EMF in lambda handlers)

## Current Lambda Set

- `create_order_java`: create order + enqueue
- `get_order_status_java`: query status/content
- `generate_caseplan_worker_java`: consume queue and generate

## SQS -> Lambda Trigger (AWS)

- Main queue: `dev_sqs_caseplan`
- DLQ: `dev_sqs_caseplan_dlq`
- Event source mapping:
  - source: `dev_sqs_caseplan`
  - target: `generate_caseplan_worker_java`
  - state: enabled
  - batch size: `1`
  - function response type: `ReportBatchItemFailures`
- Retry and dead-letter behavior:
  - SQS `RedrivePolicy.maxReceiveCount = 3`
  - when receive count exceeds 3, message moves to DLQ
  - queue `VisibilityTimeout` should be safely larger than Lambda timeout

## Important Config

From `application.yaml` / Lambda env:

- `queue.provider=redis|sqs`
- `queue.sqs.queue-url`
- `spring.datasource.*`
- `llm.provider=openai|claude`
- provider-specific API key/base-url/model vars

## Validation and Testing

- Unit tests: `mvn test`
- Integration tests (`*IT`): `mvn failsafe:integration-test failsafe:verify`
- Full verify with coverage gate: `mvn verify`
- Post-deploy smoke test script: `scripts/smoke_apigw_lambda.sh`
