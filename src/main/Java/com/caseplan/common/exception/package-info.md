# common.exception 包结构说明

## SuccessWithWarnings 是什么？

**SuccessWithWarnings 不是异常**，而是「成功响应但带警告」时的**返回体形状**。

- 当用户带 `confirm=true` 提交，业务里有警告但允许继续时，Controller 不 throw，而是把 WarningException 放进列表，最后**正常返回 201**。
- 此时响应 body 需要同时带「创建结果」和「警告列表」，所以用 `SuccessWithWarnings<CasePlan>` 包装成 `{ "data": CasePlan, "warnings": [...] }`。

## 关系图（ASCII）

```text
                    ┌─────────────────────────────────────────────────────────┐
                    │                  POST /api/caseplans                     │
                    └─────────────────────────────────────────────────────────┘
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                         ▼
            ┌───────────────┐         ┌───────────────┐         ┌───────────────┐
            │ @Valid 失败   │         │ throw 异常     │         │ 正常执行到底   │
            │ (进方法前)    │         │ (业务里)       │         │ 无异常        │
            └───────┬───────┘         └───────┬───────┘         └───────┬───────┘
                    │                         │                         │
                    ▼                         ▼                         ▼
            MethodArgumentNotValidException   BlockException            返回 201
                    │                 ValidationException                 │
                    │                 WarningException                    │
                    │                         │                           │
                    ▼                         ▼                           ▼
            ┌───────────────┐         ┌───────────────┐         ┌───────────────────────┐
            │ GlobalExceptionHandler  │ GlobalExceptionHandler   │ 若有 confirm 产生的    │
            │ handleRequestValidation │ 各 @ExceptionHandler     │ warnings 列表          │
            │ Failure(...)            │ 用 ErrorResponse 或      │ → 用 SuccessWithWarnings│
            └───────┬───────┘         │ 手拼 Map                 │  包装后返回            │
                    │                 └───────┬───────┘         └───────────┬───────────┘
                    ▼                         ▼                           ▼
            400 + type/code/          400 或 409 或 200           201 + { data, warnings }
            message/detail            + type/code/message/detail  或 201 + data

  ─── 类的关系 ───

  BaseAppException (抽象基类)
       ├── BlockException      → 业务阻止，Handler 用 ErrorResponse 转 JSON，返回 409
       ├── ValidationException → 手动验证错误，Handler 用 ErrorResponse 转 JSON，返回 400
       └── WarningException    → 需确认的警告：
                                · 若 throw → Handler 手拼 Map，返回 200
                                · 若不 throw（confirm=true）→ 收集到 List，最后用
                                  SuccessWithWarnings<CasePlan> 包装，返回 201

  ErrorResponse     = 把 BaseAppException 转成 { type, code, message, detail }，给 Handler 用
  SuccessWithWarnings<T> = 成功响应体 { data: T, warnings: [...] }，不是异常
  GlobalExceptionHandler = @RestControllerAdvice，里面对应 4 个 @ExceptionHandler 方法
```

## 包内分类

- **common/exception/**           — 异常类（BaseAppException 及 3 个子类）
- **common/exception/handler/**   — 全局异常处理器（GlobalExceptionHandler）
- **common/exception/response/** — 与异常/警告相关的响应形状（ErrorResponse, SuccessWithWarnings）
