# AGENTS.md (CasePlan)

This file defines repository-level operating rules for AI coding agents.
本文件定义仓库级 AI 编码代理执行规则。

## 1. Scope & Priority / 作用范围与优先级

- Apply to all work in this repository.
- 适用于本仓库内所有改动与操作。
- Priority:
- 优先级：
1. System/developer instructions from runtime.
2. User request in current conversation.
3. This `AGENTS.md`.

## 2. Core Principle / 核心原则

- Do not rely on memory from chat.
- 不依赖“对话记忆”。
- Convert repeated expectations into scripts, CI checks, and docs.
- 将重复要求固化为脚本、CI 检查、文档。

## 3. Deployment Guardrails / 部署防呆规则

- Java Lambda package updates must use S3 handoff (`--s3-bucket` + `--s3-key`).
- Java Lambda 更新必须走 S3 中转，不先走本地 zip 直传。
- After deployment, verify live API contract before claiming success.
- 部署后先做线上契约校验，再宣告成功。

Required live checks:
必做线上校验：
1. `GET /orders` returns expected fields for current UI contract.
2. API Gateway routes target the expected Lambda functions.
3. Lambda `LastModified` is newer than pre-deploy baseline.

## 4. Frontend-Backend Contract Rule / 前后端契约规则

- Any DTO/response field change must update all three together:
- 任何字段变更必须同时更新三处：
1. Backend handler/service DTO mapping.
2. Frontend payload rendering/consumption.
3. Tests (unit/e2e) and smoke checks.

- If contract changed but deployed API is old, explicitly state deployment mismatch.
- 若代码已改但线上接口仍旧，必须明确指出“部署不一致”。

## 5. Frontend Publish Rule / 前端发布规则

- For `src/main/resources/static/index.html` changes:
- 当 `src/main/resources/static/index.html` 变更时：
1. Publish to frontend S3 bucket.
2. Trigger CloudFront invalidation when permission exists.
3. If invalidation permission is missing, do not hide it; report clearly.

## 6. Data Hygiene Rule / 数据清理规则

- E2E/test records must not remain in production-facing list unless user asks.
- 未经用户明确保留，E2E/测试数据不能留在生产展示列表。
- For one-off cleanup tooling, remove temporary code after execution.
- 一次性清理工具执行完后，删除临时代码与临时函数。

## 7. Verification Before Reply / 回复前验证

- Before final reply on delivery tasks, include:
- 对“已完成/已发布”类回复，必须先核验：
1. `git` state (clean or clearly explained).
2. Test results actually run.
3. Live endpoint result (if deployment/integration involved).

## 8. Documentation Update Rule / 文档更新规则

- If workflow/behavior changed, update docs in same PR/commit.
- 工作流或行为变更时，同次提交更新文档。
- Minimum docs to consider:
- 至少检查这些文档：
1. `README.md`
2. `README.zh-CN.md`
3. Relevant docs under `docs/`

## 9. Communication Rule / 沟通规则

- State uncertainties as facts with evidence.
- 对不确定项给出证据和边界，不做猜测式承诺。
- Distinguish clearly:
- 明确区分：
1. Code changed locally.
2. Pushed to remote.
3. Actually live in AWS.

