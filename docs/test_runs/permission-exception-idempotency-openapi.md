# TEST-004 权限异常幂等 OpenAPI 测试记录

- 执行时间: 2026-06-08 00:20 至 00:32
- 执行角色: test
- 环境: 本机启动 `backend/examine-web/target/unexamine.jar`，端口 `9999`
- 数据库: `192.168.0.211:3306/examine1`
- 结论: 本期 smoke 风险场景通过；更深的并发压测、OpenAPI timestamp/nonce/body hash 全矩阵和限流边界测试由 TEST-005 汇总为后续补测建议。

## 权限与异常

| 场景 | 操作 | 预期 | 结果 |
| --- | --- | --- | --- |
| 未登录访问内部 API | `GET /api/v1/platform/my-systems` 不带 `Authorization` | 401，带 requestId | 通过，返回 `COMMON_UNAUTHORIZED` |
| OpenAPI 缺少 AK | `POST /openapi/v1/records/query` 不带 `X-OpenApi-AccessKey` | 401，带 requestId | 通过，返回 `COMMON_UNAUTHORIZED` |
| 登录凭证错误 | 使用错误密码登录 | 401 | 通过，返回 `AUTH_INVALID_CREDENTIAL` |

## 幂等冲突

| 步骤 | 请求 | 结果 |
| --- | --- | --- |
| 1 | 注册 `idem_20260608003150` | 200，`COMMON_OK` |
| 2 | 登录 `idem_20260608003150` | 200，获取 accessToken |
| 3 | 使用 `X-Idempotency-Key: idem-create-system-20260608003150` 创建系统 A | 200，`COMMON_OK` |
| 4 | 复用同一幂等键创建系统 B，修改 code/name/description | 409，`COMMON_IDEMPOTENCY_CONFLICT`，消息 `幂等请求内容不一致` |

## 执行中修复

| 问题 | 修复 |
| --- | --- |
| 缺少 `Authorization` 请求头时 Spring 参数解析异常被统一落到 500 | `GlobalExceptionHandler` 增加 `MissingRequestHeaderException` 处理，`Authorization` 缺失返回 401 |
| OpenAPI 调用日志先落库时 `http_status` 必填但尚未设置 | `OpenApiSecurityServiceImpl.createLog` 默认写入 `500`，后续成功或失败再覆盖 |
| 创建系统相同幂等键不同请求体仍返回首次结果 | `PlatformCenterServiceImpl.createSystem` 按账号和幂等键记录请求摘要，请求内容不一致时返回 `COMMON_IDEMPOTENCY_CONFLICT` |

## 剩余说明

OpenAPI 缺少 AK 当前按通用未授权码 `COMMON_UNAUTHORIZED` 返回，符合本次“不能 500 且带 requestId”的风险验收；若后续希望所有 OpenAPI 安全失败都返回 `OPENAPI_*` 专属错误码，需要在 TEST-005 中登记为契约细化建议。
