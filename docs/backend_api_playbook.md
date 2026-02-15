<p align="right">
  <a href="#english"> <img alt="English" src="https://img.shields.io/badge/English-1f6feb?style=for-the-badge"> </a>
  <a href="#中文"> <img alt="中文" src="https://img.shields.io/badge/中文-2da44e?style=for-the-badge"> </a>
</p>

<details open>
<summary><a id="english"></a><strong>English</strong></summary>

# Backend API Playbook (Entry-Level Friendly)

This is the concrete workflow for building backend APIs in this project.

## 1) What This Process Is Called

- Broad name: `SDLC` (Software Development Lifecycle)
- Team execution name: `Change Delivery Lifecycle`
- Pipeline name: `CI/CD` (build, test, verify, deploy)

For our day-to-day work, you can treat it as:
`Design -> Implement -> Validate -> Ship -> Observe`

## 2) Concrete Coding Workflow (Micro View)

Use this sequence when adding an endpoint.

1. Write API contract first
- Define path, method, request body, response body, status codes.
- Example status policy:
  - `201`: created new record
  - `200`: idempotent duplicate match
  - `400`: invalid input
  - `409`: business conflict
  - `404`: resource not found
  - `202`: async accepted, not completed

2. Keep controller thin
- Controller should do:
  - parse/validate input
  - call service
  - map result to HTTP status
- Do not put complex business decisions in controller.

3. Put business rules in service
- Service handles:
  - duplicate detection
  - status transitions
  - conflict checks
  - idempotency behavior

4. Keep repository focused on queries
- Repository should only provide query methods.
- No business branching logic in repository layer.

5. Standardize errors
- Use common exception types:
  - `ValidationException` -> `400`
  - `BlockException` -> `409`
- Keep error response shape consistent.

6. Add branch-oriented tests
- For each important branch, add at least one test:
  - happy path
  - duplicate path
  - conflict path
  - validation path
  - not-found path
  - async acceptance path (if any)

7. Refactor duplication immediately
- If two controllers/services build the same response or use same logic, extract utility/helper early.

## 3) Whole-Picture Workflow (Macro View)

1. Requirements and domain
- Clarify resource, ownership, status machine, and constraints.

2. API contract
- Create/update OpenAPI first: `docs/api/openapi.yaml`.

3. Implementation
- DTO -> Controller -> Service -> Repository.

4. Verification gate
- Run:
  - `mvn test`
  - `mvn -DskipITs verify`
- Coverage gate must pass (`line >= 90%`, `branch >= 90%`).

5. Runtime sanity
- Boot app and check startup logs:
  - `mvn -DskipTests spring-boot:run`
- Confirm no startup failure.

6. Deployment/infra check
- Confirm route exposure model is still correct (Web vs Lambda).
- Confirm CORS methods match API methods.
- Confirm environment vars and IAM permissions are still enough.

7. PR quality check
- Use `.github/pull_request_template.md`.
- Include API examples and testing evidence.

## 4) Quick Checklist Before Merge

- [ ] OpenAPI updated (`docs/api/openapi.yaml`)
- [ ] Controller remains thin
- [ ] Service owns business rules
- [ ] All status codes are intentional and tested
- [ ] `mvn test` passes
- [ ] `mvn -DskipITs verify` passes
- [ ] Coverage gate passes
- [ ] App startup sanity check done
- [ ] Infra/CORS/routing impact reviewed

## 5) Why `202` for Retry Endpoints

Use `202 Accepted` when request triggers async processing.

- `200` implies request completed now.
- `201` implies a new resource was created now.
- `202` means request accepted and work will continue in background.

</details>

<details>
<summary><a id="中文"></a><strong>中文</strong></summary>

# 后端 API 开发手册（新手友好版）

本文档是本项目后端 API 开发的具体工作流。

## 1) 这个流程叫什么

- 宏观名称：`SDLC`（软件开发生命周期）
- 团队执行名称：`Change Delivery Lifecycle`（变更交付生命周期）
- 流水线名称：`CI/CD`（构建、测试、验证、部署）

日常工作中可以简化理解为：
`设计 -> 实现 -> 验证 -> 发布 -> 观测`

## 2) 具体编码流程（微观视角）

添加一个新接口时，按以下顺序操作。

1. 先写 API 契约
- 定义路径、方法、请求体、响应体、状态码。
- 状态码规范示例：
  - `201`：创建了新记录
  - `200`：幂等匹配（重复请求返回已有结果）
  - `400`：输入校验失败
  - `409`：业务冲突
  - `404`：资源不存在
  - `202`：异步接收，处理未完成

2. Controller 保持精简
- Controller 只做：
  - 解析/校验输入
  - 调用 Service
  - 将结果映射为 HTTP 状态码
- 不要在 Controller 中放复杂业务逻辑。

3. 业务规则放在 Service 层
- Service 负责：
  - 重复检测
  - 状态流转
  - 冲突检查
  - 幂等行为

4. Repository 只做查询
- Repository 只提供查询方法。
- 不在 Repository 层写业务分支逻辑。

5. 错误处理标准化
- 使用统一异常类型：
  - `ValidationException` -> `400`
  - `BlockException` -> `409`
- 保持错误响应格式一致。

6. 按分支写测试
- 对每个重要分支，至少写一个测试：
  - 正常路径
  - 重复路径
  - 冲突路径
  - 校验失败路径
  - 资源不存在路径
  - 异步接收路径（如有）

7. 及时消除重复代码
- 如果两个 Controller/Service 构建了相同的响应或使用相同逻辑，尽早提取为工具类/辅助方法。

## 3) 全局工作流（宏观视角）

1. 需求与领域分析
- 明确资源、所属关系、状态机和约束条件。

2. API 契约
- 先创建/更新 OpenAPI：`docs/api/openapi.yaml`。

3. 实现
- DTO -> Controller -> Service -> Repository。

4. 验证关卡
- 运行：
  - `mvn test`
  - `mvn -DskipITs verify`
- 覆盖率必须通过（`行覆盖率 >= 90%`，`分支覆盖率 >= 90%`）。

5. 运行时检查
- 启动应用并检查日志：
  - `mvn -DskipTests spring-boot:run`
- 确认无启动报错。

6. 部署/基础设施检查
- 确认路由模式仍然正确（Web 还是 Lambda）。
- 确认 CORS 配置的方法与 API 方法一致。
- 确认环境变量和 IAM 权限仍然足够。

7. PR 质量检查
- 使用 `.github/pull_request_template.md`。
- 附带 API 调用示例和测试结果。

## 4) 合并前速查清单

- [ ] OpenAPI 已更新（`docs/api/openapi.yaml`）
- [ ] Controller 保持精简
- [ ] Service 拥有业务规则
- [ ] 所有状态码都有明确意图并已测试
- [ ] `mvn test` 通过
- [ ] `mvn -DskipITs verify` 通过
- [ ] 覆盖率关卡通过
- [ ] 应用启动检查完成
- [ ] 基础设施/CORS/路由影响已评审

## 5) 为什么 Retry 接口用 `202`

当请求触发异步处理时，使用 `202 Accepted`。

- `200` 意味着请求已即时完成。
- `201` 意味着已即时创建了新资源。
- `202` 意味着请求已接收，后续工作将在后台继续执行。

</details>
