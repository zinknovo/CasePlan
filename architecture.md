# Case Plan System Architecture

## System Overview

```
(Inbound)
                +------------------------------------------+
HTTP/API GW --->| Controller / Lambda Handler              |
                | - parse request                          |
                | - call use case                          |
                +-------------------+----------------------+
                                    |
                                    v
                     +-------------------------------+
                     |        USE CASE (Core)        |
                     |-------------------------------|
                     | CreateCasePlanUseCase         |
                     | GetCasePlanUseCase            |
                     | GenerateCasePlanUseCase       |
                     +----+---------------+----------+
                          |               |
          depends on Port |               | depends on Port
                          v               v
                 +----------------+   +----------------+
                 | QueuePort      |   | LlmPort        |
                 | enqueue(...)   |   | generate(...)  |
                 | poll/ack/...   |   +-------+--------+
                 +-------+--------+           |
                         |                    |
                adapter impls          adapter impls
                         |                    |
         +---------------+----------+   +-----+------------------+
         | RedisQueueAdapter(local) |   | OpenAIServiceAdapter   |
         | SqsQueueAdapter(aws)     |   | ClaudeServiceAdapter   |
         +---------------+----------+   +------------------------+
                         |
                         v
                    [Redis / SQS]

                     +-----------------------+
                     | CasePlanRepositoryPort|
                     +-----------+-----------+
                                 |
                                 v
                        +------------------+
                        | JpaRepository    |
                        +--------+---------+
                                 |
                                 v
                              [PostgreSQL]
```

## Business Flows

### Create Case Plan

```
POST /api/caseplans
  → CreateCasePlanUseCase
    → DB save (status = pending)
    → QueuePort.enqueue(casePlanId)
  → return 201 with pending status
```

### Generate Case Plan (async, triggered by queue)

```
Queue message received
  → GenerateCasePlanUseCase
    → DB update (status = processing)
    → LlmPort.generate(caseInfo)
    → DB update (status = completed / failed)
```

### Get Case Plan

```
GET /api/caseplans/{id}
  → GetCasePlanUseCase
    → DB read
    → return case plan (only show generatedPlan if status = completed)
```

## Port & Adapter Summary

| Port (Interface)       | Adapter (Implementation)         | Swappable          |
|------------------------|----------------------------------|--------------------|
| QueuePort              | RedisQueueAdapter                | Local dev          |
| QueuePort              | SqsQueueAdapter                  | AWS production     |
| LlmPort                | ClaudeServiceAdapter             | Anthropic Claude   |
| LlmPort                | OpenAIServiceAdapter             | OpenAI GPT         |
| CasePlanRepositoryPort | JpaRepository                    | PostgreSQL via JPA |

## Tech Stack

| Layer        | Local Dev              | AWS Production        |
|--------------|------------------------|-----------------------|
| API          | Spring Boot Controller | API Gateway + Lambda  |
| Queue        | Redis                  | SQS                   |
| LLM          | Claude API             | Claude API / Bedrock  |
| Database     | PostgreSQL (Docker)    | RDS PostgreSQL        |
| Monitoring   | Prometheus + Grafana   | CloudWatch            |
