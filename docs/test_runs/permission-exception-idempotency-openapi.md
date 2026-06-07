# TEST-004 权限异常幂等 OpenAPI 测试记录

- 执行时间: 2026-06-08 00:20 至 00:32
- 执行角色: test
- 环境: 本机启动 `backend/examine-web/target/unexamine.jar`，端口 `9999`
- 数据库: `192.168.0.211:3306/examine1`
- 结论: 本期 smoke 风险场景通过；返工后新增 OpenAPI 安全负向单元测试覆盖 accessKey、timestamp、body hash、signature、scope 和 rate limit 专属错误码。

## 权限与异常

| 场景 | 操作 | 预期 | 结果 |
| --- | --- | --- | --- |
| 未登录访问内部 API | `GET /api/v1/platform/my-systems` 不带 `Authorization` | 401，带 requestId | 通过，返回 `COMMON_UNAUTHORIZED` |
| OpenAPI 缺少 AK | `POST /openapi/v1/records/query` 不带 `X-OpenApi-AccessKey` | 401，`OPENAPI_ACCESS_KEY_INVALID`，带 requestId | 返工后通过；单元测试断言错误码和日志均为 `OPENAPI_ACCESS_KEY_INVALID` |
| 登录凭证错误 | 使用错误密码登录 | 401 | 通过，返回 `AUTH_INVALID_CREDENTIAL` |

## OpenAPI 安全负向矩阵

| 场景 | 覆盖方式 | 预期错误码 | 结果 |
| --- | --- | --- | --- |
| 缺少 `X-OpenApi-AccessKey` | `OpenApiSecurityServiceImplTest.shouldUseOpenApiAccessKeyErrorWhenAccessKeyHeaderMissing` | `OPENAPI_ACCESS_KEY_INVALID` | 通过 |
| 未知 accessKey | `OpenApiSecurityServiceImplTest.shouldUseOpenApiAccessKeyErrorWhenAccessKeyUnknown` | `OPENAPI_ACCESS_KEY_INVALID` | 通过 |
| timestamp 过期或格式非法 | `OpenApiSecurityServiceImplTest.shouldUseTimestampErrorWhenTimestampExpired` | `OPENAPI_TIMESTAMP_EXPIRED` | 通过 |
| body hash 不匹配 | `OpenApiSecurityServiceImplTest.shouldUseBodyHashErrorWhenBodyHashMismatch` | `OPENAPI_BODY_HASH_MISMATCH` | 通过 |
| signature 不匹配 | `OpenApiSecurityServiceImplTest.shouldUseSignatureErrorWhenSignatureMismatch` | `OPENAPI_SIGNATURE_INVALID` | 通过 |
| scope 未授权 | `OpenApiSecurityServiceImplTest.shouldUseScopeDeniedWhenScopeNotGranted` | `OPENAPI_SCOPE_DENIED` | 通过 |
| rate limit 超限 | `OpenApiSecurityServiceImplTest.shouldUseRateLimitedWhenPolicyWindowExceeded` | `OPENAPI_RATE_LIMITED` | 通过 |

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
| OpenAPI 缺少 AK 返回通用错误码 | `OpenApiSecurityServiceImpl` 缺失/未知 accessKey 均返回 `OPENAPI_ACCESS_KEY_INVALID`，并补充日志断言 |

## 剩余说明

当前已覆盖 OpenAPI accessKey、timestamp、body hash、signature、scope 和 rate limit 的专属错误码断言。nonce replay、IP 白名单、OpenAPI 幂等冲突、自动编号并发、流程任务并发处理和导出任务并发领取仍建议作为上线前专项压测或增强自动化继续补齐。
