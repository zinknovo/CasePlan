# AGENTS.md

This file defines persistent, project-level operating rules for AI coding agents in this repository.
本文件用于定义本仓库 AI 编码代理的长期执行规则。

## 0) Scope / 作用范围

- Applies to this repository only.
- 仅对本仓库生效。
- Do not assume these rules apply to other repositories unless copied there.
- 不要假设这些规则自动适用于其他仓库，除非你把它复制过去。

## 1) Non-Negotiable Deployment Rules / 不可协商的发布规则

### 1.1 Lambda code update must use S3 handoff

- For Java Lambda artifacts in this project, do **NOT** use direct `--zip-file` upload by default.
- 本项目 Java Lambda 包默认**禁止**先尝试 `--zip-file` 直传。
- Always use: upload package to S3, then `aws lambda update-function-code --s3-bucket --s3-key`.
- 一律采用：先上传 S3，再通过 `--s3-bucket/--s3-key` 更新函数。
- Reason: package size frequently exceeds direct upload limits.
- 原因：包体经常超出直传限制。

### 1.2 Frontend change is not complete without live deploy verification

- Any frontend behavior change must include:
- 前端行为变更必须包含：
1. local test pass,
2. deployment confirmation,
3. live URL verification.

- Minimum live verification includes:
- 最低线上验证包括：
1. page contains expected label/text,
2. API responses match expected contract,
3. no stale cache issue after publish.

### 1.3 CI/CD must not rely only on unit/E2E mocks

- Mock-based tests are necessary but insufficient.
- 基于 mock 的测试是必要的，但不充分。
- Always add/maintain post-deploy smoke checks against real staging/prod endpoints.
- 必须维护面向真实环境 endpoint 的发布后 smoke check。

## 2) API Contract Guardrails / 接口契约护栏

When frontend fields or table rendering depend on backend JSON:
当前端字段或表格依赖后端 JSON 时：

- Verify **actual API Gateway route -> integration -> Lambda** mapping before declaring done.
- 在宣告完成前，检查真实的 API Gateway 路由到 Lambda 的映射。
- Verify live payload keys explicitly.
- 明确验证线上返回键名。
- Do not assume local code changes are already running in cloud.
- 不要假设本地代码改动已经在云端生效。

Current known business convention:
当前已确认的业务约定：

- Internal technical id (`caseplan id`) exists for backend operations.
- 后端保留技术主键（`caseplan id`）。
- Business-facing identifier is `serviceNumber` (`SRV-YYYYMMDD-XXXX`).
- 业务展示编号为 `serviceNumber`（`SRV-YYYYMMDD-XXXX`）。
- Frontend result row currently displays only `#serviceNumber` text, not "Service" or "Docket" label.
- 前端结果行当前仅显示 `#serviceNumber`，不显示 "Service" 或 "Docket" 文案。

## 3) Data Hygiene Rules / 数据卫生规则

- Do not leave E2E/probe seed data in production-like environments.
- 不要在类生产环境保留 E2E/探针测试数据。
- If user requests to preserve manual entries, explicitly preserve only those and remove the rest.
- 如果用户要求保留手工录入数据，必须仅保留指定记录并清理其余记录。
- After cleanup, verify resulting list and share concrete IDs/names.
- 清理后要复核列表，并给出明确 ID/姓名结果。

## 4) Required Delivery Checklist / 交付前必检清单

Before saying "done", execute and report:
在说“完成”前，必须执行并报告：

1. Code-level tests run status (what passed/failed).
2. Deployment status (what was deployed, where).
3. Live endpoint verification (sample response keys).
4. Frontend live URL check (expected UI text present).
5. Data state check if relevant (counts, preserved records).

## 5) Documentation Update Rule / 文档同步规则

- If workflow/process behavior changes, update docs in same change set.
- 若流程行为发生变化，必须在同一变更中同步更新文档。
- At minimum update README + README.zh-CN when CI/CD, deploy, or env assumptions change.
- 至少同步更新 README 与 README.zh-CN（CI/CD、部署、环境假设变更时）。

## 6) Tooling Compatibility Note / 兼容性说明

- `AGENTS.md` is a convention supported by some coding agents (including this Codex workflow here).
- `AGENTS.md` 是部分编码代理支持的约定（包括当前这套 Codex 工作流）。
- It is **NOT** a universal standard for all CLI agents.
- 它**不是**所有 CLI agent 的通用标准。
- Other tools may use different files (for example: their own config/instruction files) or ignore this file entirely.
- 其他工具可能使用不同指令文件，或完全忽略本文件。

## 7) If in Doubt / 不确定时

- Prefer explicit verification over assumption.
- 以显式验证替代主观假设。
- Surface uncertainty early and run a concrete check.
- 尽早暴露不确定点并执行具体检查。
