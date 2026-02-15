<p align="right">
  <a href="#english"> <img alt="English" src="https://img.shields.io/badge/English-1f6feb?style=for-the-badge"> </a>
  <a href="#中文"> <img alt="中文" src="https://img.shields.io/badge/中文-2da44e?style=for-the-badge"> </a>
</p>

<details open>
<summary><a id="english"></a><strong>English</strong></summary>

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

- Monitoring plan: `MONITORING.md`
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

- Architecture: `architecture.md`
- Monitoring: `MONITORING.md`
- Product doc: `PRD.md`
- OpenAPI: `docs/api/openapi.yaml`
- Backend playbook: `docs/backend_api_playbook.md`
- Terraform CD notes: `docs/cd_terraform_oidc.md`

</details>

<details>
<summary><a id="中文"></a><strong>中文</strong></summary>

# CasePlan（中文说明）

CasePlan 是一个法律案件录入与 Case Plan 生成系统，支持两种运行形态：

- 本地传统模式：Spring Boot Web + PostgreSQL + Redis
- AWS 无服务器模式：API Gateway + Lambda + RDS PostgreSQL + SQS（含 DLQ）

当前项目以 **Lambda 路径为主**，同时保留本地开发调试能力。

## 功能

- 案件创建（含校验、重复检查、告警分支）
- 队列异步生成 Case Plan
- 状态查询（`pending / processing / completed / failed`）
- 重试超限消息进入 DLQ（`maxReceiveCount=3`）
- 提供 Web 页面与 API 接入

## 技术栈

- Java 11
- Spring Boot 2.7
- Spring Data JPA
- PostgreSQL
- Redis（本地队列模式）
- AWS Lambda / API Gateway / SQS / RDS
- Maven / JUnit4 / Mockito / JaCoCo

## 项目结构

```text
src/main/java/com/caseplan
  adapter/
    in/
      web/        # Spring MVC 控制器
      lambda/     # Lambda 处理器
      queue/      # 本地消费者
      intake/     # 多数据源接入适配器
    out/
      persistence/# JPA 持久层
      queue/      # Redis/SQS 队列适配器
      llm/        # OpenAI/Claude LLM 适配器
  application/
    service/      # 用例服务
    port/         # 入站/出站端口
  domain/
    model/        # 领域模型

src/main/resources/static/index.html  # Web 页面
infra/terraform-replica/              # 临时基础设施复刻（Terraform）
scripts/smoke_apigw_lambda.sh         # API Gateway -> Lambda 冒烟测试
```

## 运行模式

### 本地模式（Spring Web）

- API 基础路径：`/api/caseplans`
- 队列提供者：Redis（`QUEUE_PROVIDER=redis`）
- 后台处理器：`CasePlanConsumer`

### AWS 模式（Lambda）

- `POST /orders` -> `CreateOrderHandler`
- `GET /orders/{id}` -> `GetOrderStatusHandler`
- SQS 事件源 -> `GenerateCasePlanWorkerHandler`
- 队列提供者：SQS（`QUEUE_PROVIDER=sqs`）

## 本地启动

### 方式 A：Docker Compose 启动依赖，Maven 运行应用

```bash
docker compose up -d db redis
mvn spring-boot:run
```

访问地址：

- 应用：`http://localhost:8080`
- 页面：`http://localhost:8080/index.html`
- Prometheus：`http://localhost:9090`
- Grafana：`http://localhost:3000`

### 方式 B：完整 Compose 栈（含测试容器）

```bash
docker compose up --build
```

## 关键配置

主配置文件：`src/main/resources/application.yaml`

### 数据库

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

本地默认 URL：`jdbc:postgresql://localhost:5433/dev_db`

### 队列

- `QUEUE_PROVIDER`：`redis` 或 `sqs`
- `QUEUE_URL`：provider 为 `sqs` 时必填
- `AWS_REGION`：默认 `us-east-2`

### LLM

- `LLM_PROVIDER`：`openai` 或 `claude`
- `LLM_OPENAI_API_KEY` / `DEEPSEEK_API_KEY`
- `LLM_OPENAI_BASE_URL`（默认兼容 DeepSeek）
- `ANTHROPIC_API_KEY`

## API 概览

### 本地 Web API

- `POST /api/caseplans`
- `GET /api/caseplans`
- `GET /api/caseplans/{id}`
- `GET /api/caseplans/{id}/status`
- `POST /api/caseplans/{id}/retry`
- `GET /api/caseplans/{id}/download`
- `POST /api/clients`、`PUT /api/clients/{id}`、`DELETE /api/clients/{id}`
- `POST /api/attorneys`
- `POST /api/intake?source=jsonA|jsonB|xml`

### Lambda / API Gateway API

- `POST /orders`
- `GET /orders/{id}`

示例：

```bash
curl -X POST "$API/orders" \
  -H 'content-type: application/json' \
  -d '{"clientFirstName":"A","clientLastName":"B","attorneyName":"C","barNumber":"BAR-1","primaryCauseOfAction":"Contract","remedySought":"Damages"}'

curl "$API/orders/123"
```

## 测试

```bash
# 单元测试（不含 *IT）
mvn test

# 单元测试 + 集成测试 + 覆盖率关卡
mvn verify
```

JaCoCo 门槛（`pom.xml`）：

- 行覆盖率 >= 90%
- 分支覆盖率 >= 90%

## 监控与冒烟测试

- 监控方案：`MONITORING.md`
- Lambda 指标：CloudWatch EMF（命名空间 `CasePlan/Lambda`）— 零依赖，stdout 输出
- Grafana 仪表盘：
  - `caseplan-overview.json` — Prometheus（本地 Spring Boot）
  - `lambda-overview.json` — CloudWatch（Lambda + SQS + EMF 业务指标）
- CloudWatch 数据源：`monitoring/grafana/provisioning/datasources/cloudwatch.yml`
- 冒烟测试脚本：`scripts/smoke_apigw_lambda.sh`

```bash
API_BASE_URL="https://<api-id>.execute-api.<region>.amazonaws.com" \
  bash scripts/smoke_apigw_lambda.sh
```

## Terraform 临时复刻

目录：`infra/terraform-replica/`

用于临时创建 API/Lambda/SQS/IAM/SG 副本进行验证，之后销毁。

```bash
cd infra/terraform-replica
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform apply
terraform destroy
```

详情：`infra/terraform-replica/README.md`

## CI/CD

### CI — `.github/workflows/ci.yml`

- 使用 PostgreSQL + Redis 服务容器
- 运行：`mvn -B -Dspring.profiles.active=it verify`
- 代码/构建文件变更时自动触发（`src/**`、`pom.xml`、`Dockerfile` 等）

### CD — `.github/workflows/cd-terraform.yml`

- push main（基础设施/源码变更）、PR 或手动 `workflow_dispatch` 触发
- 使用 **GitHub OIDC** 认证 AWS IAM Role（无静态密钥）
- 流程：构建 Lambda 包 → 校验配置 → 渲染 tfvars → Terraform init/plan
- `apply` 任务仅在 push main 或手动触发且 `apply=true` 时执行
- Terraform state 存储在 S3 + DynamoDB 锁

### 分支保护（Ruleset）

- admin 可直接 push `main`（旁路）
- 其他人需 PR + 1 approval + CI `test` 通过

## Lambda 打包

Terraform 需要 `target/legal-caseplan-lambda.zip`。

如果不存在，手动构建：

```bash
mvn -DskipTests package
cd target/lambda && zip -r ../legal-caseplan-lambda.zip .
```

## 安全建议

- 不要把 DB 密码与 LLM Key 明文提交到代码/tfvars。
- 生产环境建议使用 AWS Secrets Manager / SSM。
- CloudFront 前端 + API Gateway 后端时要正确配置 CORS。
- 若 SQS 使用 Interface VPCE，需放通 Lambda SG 到 VPCE SG 的 443。

## 相关文档

- 架构：`architecture.md`
- 监控：`MONITORING.md`
- 产品文档：`PRD.md`
- OpenAPI：`docs/api/openapi.yaml`
- 后端开发手册：`docs/backend_api_playbook.md`
- Terraform CD 说明：`docs/cd_terraform_oidc.md`

</details>
