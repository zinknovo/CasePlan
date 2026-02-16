<p align="right">
  <a href="./README.md"> <img alt="English" src="https://img.shields.io/badge/English-1f6feb?style=for-the-badge"> </a>
</p>

# CasePlan

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

## 项目功能介绍

### 目标与场景

- 目标用户是律师助理（Paralegal），用于录入案件并触发方案生成
- 系统自动生成法律服务方案（Legal Service Plan），供律师审核、下载、打印或归档
- 重点解决人工撰写耗时长、积压多、合规审查材料准备慢的问题

### 输入与校验

- 必填核心字段包括：委托人姓名、转介律师与执业证号、案件编号、主要案由、法律救济诉求、案件材料
- 支持附加案由、既往法律行动等扩展信息
- 服务端执行格式/唯一性/必填校验，避免脏数据进入后续流程

### 生成输出

- 一案一方案（一个案件对应一个计划）
- 方案默认包含四个板块：案情摘要、服务目标、律师建议策略、跟进计划
- 结果可下载，用于线下审核与对外提交

### 重复检测与告警分支

- 明确重复（如关键组合字段一致）直接阻断提交（`ERROR`）
- 疑似重复（信息部分冲突）给出可确认告警（`WARNING`）
- 转介律师执业证号冲突按强校验处理，避免主数据污染

### 迭代范围

- 当前版本聚焦 MVP：录入、校验、去重、生成、下载、状态追踪
- 增强项（后续）：在线审核编辑、权限模型、审计日志、版本管理、批量导入、草稿保存

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

### 当前线上地址

- 前端：`https://dyopmtsq4vhrb.cloudfront.net`
- API 基础地址：`https://mc94chabh2.execute-api.us-east-2.amazonaws.com`

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

- 监控方案：`monitoring.md`
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
- 流程：构建 Lambda 包 + 前端 E2E → 校验配置 → 渲染 tfvars → Terraform init/plan
- `apply` 任务仅在 push main 或手动触发且 `apply=true` 时执行
- Terraform state 存储在 S3 + DynamoDB 锁
- `apply` 后会自动发布前端 `src/main/resources/static/index.html` 到 S3，并触发 CloudFront 失效
- 前端发布目标支持仓库变量（可选）：
  - `FRONTEND_S3_BUCKET`（默认：`caseplan-frontend-727766004034-use2`）
  - `FRONTEND_DISTRIBUTION_ID`（默认：`E23XI74DK4D2MF`）

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

- 架构：`architect.md`
- 监控：`monitoring.md`
- OpenAPI：`docs/api/openapi.yaml`
- 后端开发手册：`docs/backend-api-playbook.md`
- Terraform CD 说明：`docs/cd-terraform-oidc.md`
