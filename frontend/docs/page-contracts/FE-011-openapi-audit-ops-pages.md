# FE-011 OpenAPI 审计运维页面契约证据

> 本文件只记录 FE-011 页面级证据，不替代 FE-012 的 `frontend/docs/api-contract-map.md` 汇总。

## 页面基本信息

| 项 | 内容 |
| --- | --- |
| 任务 ID | FE-011 |
| 页面/模块 | OpenAPI 管理、OpenAPI 调用日志、系统审计日志、平台审计日志、运维健康 |
| 路由 | `/systems/:systemId/openapi`、`/systems/:systemId/audit`、`/platform/audit`、`/ops` |
| 入口 | 系统内 OpenAPI、系统内审计、平台审计、运维 |
| 依赖上下文 | OpenAPI: systemId / tenantId / memberId；系统审计: systemId / memberId；平台审计和运维: accessToken / platformPermissions |
| SDK 引用 | `frontend/src/api` |

## API 映射证据

| API ID | 方法 | 路径 | 触发动作 | 必填 path 入参 | 必填 query 入参 | 必填 body 入参 | 必填 header | 响应字段 | 枚举/状态 | 错误码 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OPM-001 | GET | `/api/v1/systems/{systemId}/openapi/clients` | OpenAPI 客户端列表加载 | systemId | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | clientId、name、code、tenantId、accessKey、maskedSecret、secretVisibleOnce、status、scopes、ipWhitelist、rateLimitPolicy、createdAt、lastUsedAt | OpenApiClientStatus | PERM_DENIED | 无系统/租户/member 上下文时阻止请求 |
| OPM-002 | POST | `/api/v1/systems/{systemId}/openapi/clients` | 创建客户端 | systemId | 无 | name、code、scopes、ipWhitelist、status、idempotencyKey | Authorization / X-Tenant-Id / X-Request-Id / X-Idempotency-Key | clientId、accessKey、secretOnce、secretVisibleOnce、status、scopes | OpenApiClientStatus | COMMON_IDEMPOTENCY_CONFLICT、PERM_DENIED、OPENAPI_SCOPE_DENIED | `secretOnce` 只进入一次性展示状态 |
| OPM-003 | PUT | `/api/v1/systems/{systemId}/openapi/clients/{clientId}` | 更新客户端基础信息 | systemId、clientId | 无 | name、tenantId、rateLimitPolicy、expiresAt、version | Authorization / X-Tenant-Id / X-Request-Id | clientId、status、rateLimitPolicy、expiresAt、version | OpenApiClientStatus | PERM_DENIED | 不提交 secret |
| OPM-004 | PATCH | `/api/v1/systems/{systemId}/openapi/clients/{clientId}/status` | 启用、停用、过期客户端 | systemId、clientId | 无 | status、version | Authorization / X-Tenant-Id / X-Request-Id | clientId、status、updatedAt | OpenApiClientStatus | PERM_DENIED | 状态按钮按 OPENAPI_CLIENT_STATUS 禁用 |
| OPM-005 | POST | `/api/v1/systems/{systemId}/openapi/clients/{clientId}/credentials/rotate` | 轮换凭证 | systemId、clientId | 无 | idempotencyKey | Authorization / X-Tenant-Id / X-Request-Id / X-Idempotency-Key | clientId、accessKey、secretOnce、maskedSecret、secretVisibleOnce、rotatedAt | secretVisibleOnce | COMMON_IDEMPOTENCY_CONFLICT、PERM_DENIED | `secretOnce` 展示后 `consumeSecretOnce` 清除 |
| OPM-006 | PUT | `/api/v1/systems/{systemId}/openapi/clients/{clientId}/scopes` | 保存 scope 授权 | systemId、clientId | 无 | scopes、version | Authorization / X-Tenant-Id / X-Request-Id | clientId、scopes、status、version | DataScopeType | OPENAPI_SCOPE_DENIED、PERM_DENIED | scope 目录来自 OPM-009 |
| OPM-007 | PUT | `/api/v1/systems/{systemId}/openapi/clients/{clientId}/ip-whitelist` | 保存 IP 白名单 | systemId、clientId | 无 | ipWhitelist、version | Authorization / X-Tenant-Id / X-Request-Id | clientId、ipWhitelist、status、version | 无 | COMMON_PARAM_INVALID、PERM_DENIED | IP 合法性以后端错误为准 |
| OPM-008 | GET | `/api/v1/systems/{systemId}/openapi/access-logs` | 查询 OpenAPI 调用日志 | systemId | requestId 可选 | 无 | Authorization / X-Tenant-Id / X-Request-Id | logId、requestId、clientId、accessKey、apiId、method、path、statusCode、errorCode、signatureResult、nonceResult、idempotencyResult、rateLimitResult、scopeResult、durationMs、createdAt | HttpMethod | OPENAPI_*、PERM_DENIED | 支持 requestId 检索和复制 |
| OPM-009 | GET | `/api/v1/systems/{systemId}/openapi/scope-catalog` | 加载可授权 scope 目录 | systemId | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | modules、actions、dataScopes、filePermissions、flowPermissions | DataScopeType | PERM_DENIED | 用于 scope 编辑，不伪造目录 |
| AUD-001 | GET | `/api/v1/systems/{systemId}/audit/operation-logs` | 查询系统操作日志 | systemId | requestId、operatorId、bizType、bizId、action、result、startTime、endTime 可选 | 无 | Authorization / X-Tenant-Id / X-Request-Id | logId、requestId、operatorType、operatorName、systemId、tenantId、bizType、bizId、action、result、errorCode、createdAt、summary | result | AUDIT_QUERY_RANGE_TOO_LARGE、PERM_DENIED | 只读 |
| AUD-002 | GET | `/api/v1/systems/{systemId}/audit/request-logs` | 查询系统请求日志 | systemId | 同 AUD-001 | 无 | Authorization / X-Tenant-Id / X-Request-Id | 同 AUD-001 | result | AUDIT_QUERY_RANGE_TOO_LARGE、PERM_DENIED | 只读 |
| AUD-003 | GET | `/api/v1/systems/{systemId}/audit/error-logs` | 查询系统错误日志 | systemId | 同 AUD-001 | 无 | Authorization / X-Tenant-Id / X-Request-Id | 同 AUD-001 | result | AUDIT_QUERY_RANGE_TOO_LARGE、PERM_DENIED | requestId 为主要检索条件 |
| AUD-004 | GET | `/api/v1/systems/{systemId}/audit/record-changes` | 查询业务记录变更 | systemId | 同 AUD-001 | 无 | Authorization / X-Tenant-Id / X-Request-Id | 同 AUD-001 | result | PERM_DATA_SCOPE_DENIED、PERM_DENIED | 数据范围由后端校验 |
| AUD-005 | GET | `/api/v1/systems/{systemId}/audit/openapi-logs` | 查询系统 OpenAPI 审计日志 | systemId | 同 AUD-001 | 无 | Authorization / X-Tenant-Id / X-Request-Id | 同 AUD-001 | result | PERM_DENIED | 与 OPM-008 调用日志用 requestId 串联 |
| AUD-006 | GET | `/api/v1/platform/audit/operation-logs` | 查询平台操作日志 | 无 | 同 AUD-001 | 无 | Authorization / X-Request-Id | 同 AUD-001 | result | AUDIT_QUERY_RANGE_TOO_LARGE、PERM_DENIED | 平台权限来自 authStore |
| AUD-007 | GET | `/api/v1/systems/{systemId}/audit/logs/{logId}` | 查询系统审计详情 | systemId、logId | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | logId、requestId、traceId、source、apiId、httpMethod、path、operatorId、operatorName、beforeSnapshot、afterSnapshot、errorCode、durationMs、createdAt | HttpMethod | AUDIT_LOG_NOT_FOUND、PERM_DENIED | 详情展示前后快照 |
| AUD-008 | GET | `/api/v1/platform/audit/logs/{logId}` | 查询平台审计详情 | logId | 无 | 无 | Authorization / X-Request-Id | 同 AUD-007 | HttpMethod | AUDIT_LOG_NOT_FOUND、PERM_DENIED | 平台详情不需要 systemId |
| OPS-001 | GET | `/api/v1/ops/health` | 查看总体健康 | 无 | 无 | 无 | Authorization / X-Request-Id | status、checks、requestId | UP/DOWN/WARN/UNKNOWN | OPS_HEALTH_CHECK_FAILED、PERM_DENIED | 健康异常展示组件和 requestId |
| OPS-002 | GET | `/api/v1/ops/config-check` | 查看配置检查 | 无 | 无 | 无 | Authorization / X-Request-Id | status、checks、requestId | UP/DOWN/WARN/UNKNOWN | OPS_CONFIG_MISSING、PERM_DENIED | 只读 |
| OPS-003 | GET | `/api/v1/ops/version` | 查看版本信息 | 无 | 无 | 无 | Authorization / X-Request-Id | version、buildTime、requestId、commitId、profile | 无 | PERM_DENIED | 只读 |
| OPS-004 | GET | `/api/v1/ops/migration/status` | 查看迁移状态 | 无 | 无 | 无 | Authorization / X-Request-Id | status、migrationVersion、checks、requestId | UP/DOWN/WARN/UNKNOWN | OPS_MIGRATION_INCONSISTENT、PERM_DENIED | 只读 |
| OPS-006 | GET | `/api/v1/ops/health/components` | 查看组件级健康 | 无 | 无 | 无 | Authorization / X-Request-Id | component、result、message、suggestion、checkedAt、metadata | UP/DOWN/WARN/UNKNOWN | OPS_HEALTH_CHECK_FAILED、PERM_DENIED | 覆盖 DB/Redis/文件/密钥/migration/OpenAPI 策略检查 |

## 字段映射

| UI 字段 | API 字段 | 来源 | 是否前端可写 | 提交转换规则 | 回显/空值规则 |
| --- | --- | --- | --- | --- | --- |
| 当前系统 | systemId | 路由 / systemContextStore | 否 | `systemContext.toPathParams()` 补齐 | 缺失时阻止 OpenAPI 和系统审计请求 |
| 当前租户 | tenantId / X-Tenant-Id | systemContextStore | OpenAPI 创建时可写 tenantId；header 不可写 | header 使用 `systemContext.toTenantHeader()`；BO 中 tenantId 仅用于客户端归属 | 多租户缺失时阻止 OpenAPI 请求 |
| 客户端名称 | name | OpenAPI 表单 | 是 | OPM-002/OPM-003 body.name | 空值以后端 COMMON_PARAM_INVALID 为准 |
| 客户端编码 | code | OpenAPI 表单 | 是，创建后不编辑 | OPM-002 body.code | 回显 list/detail code |
| 凭证 secret | secretOnce | OPM-002 / OPM-005 响应 | 否 | 不提交，不落 localStorage/sessionStorage；仅构造 `SecretOnceDisplayState` | 展示一次后清除 secretOnce，后续只显示 maskedSecret |
| scope 授权 | scopes | OPM-002 / OPM-006 body | 是 | 使用 `OpenApiScope[]` 原字段提交 | 空态展示“暂无 scope 授权” |
| IP 白名单 | ipWhitelist | OPM-002 / OPM-007 body | 是 | 使用 string[] 原字段提交 | 空数组表示未配置，由后端规则校验 |
| 限流策略 | rateLimitPolicy | OPM-002 / OPM-003 body | 是 | windowSeconds、maxRequests、burst、effectiveFrom 原字段提交 | 未配置时显示“未配置限流策略” |
| requestId 检索 | requestId | OPM-008 / AUD-* query | 是 | query.requestId 透传 | 列表和详情均可复制 requestId |
| 审计详情快照 | beforeSnapshot / afterSnapshot | AUD-007 / AUD-008 响应 | 否 | 不提交 | 无快照时展示空态 |
| 健康组件 | checks / components | OPS-* 响应 | 否 | 不提交 | 无检查项时展示空态 |

## 枚举、状态与错误码

| 类型 | 契约来源 | 页面使用位置 | 展示/禁用规则 |
| --- | --- | --- | --- |
| 枚举 | `frontend/src/api/enums.ts`、`frontend/src/api/types.ts` | OpenApiClientStatus、DataScopeType、HttpMethod | 未知枚举按错误态展示原值 |
| 状态 | `frontend/src/api/types.ts`、页面模型 `OpsCheckResult` | 客户端 DRAFT/ENABLED/DISABLED/EXPIRED；健康 UP/DOWN/WARN/UNKNOWN | DISABLED/EXPIRED 客户端仍可查看；DOWN/WARN 标记健康异常 |
| 错误码 | `frontend/src/api/errorCodes.ts` | OpenAPI、审计、运维错误态 | 必须展示 `message` 与 `requestId`；OPENAPI_RATE_LIMITED 可提示稍后重试 |

## 权限禁用态

| 控件/动作 | 权限来源 | 禁用字段 | 禁用展示 | 后端兜底错误码 |
| --- | --- | --- | --- | --- |
| 查看 OpenAPI 客户端 | `permissionStore.decide({ anyOperations: ["OPENAPI_CLIENT_VIEW"] })` | enabled=false | PERM_DENIED | PERM_DENIED |
| 新建客户端 | `OPENAPI_CLIENT_CREATE` | enabled=false | PERM_DENIED；不展示提交按钮或禁用 | PERM_DENIED |
| 编辑客户端基础信息 | `OPENAPI_CLIENT_EDIT` | enabled=false | PERM_DENIED | PERM_DENIED |
| 启停客户端 | `OPENAPI_CLIENT_STATUS` | enabled=false | PERM_DENIED | PERM_DENIED |
| 轮换凭证 | `OPENAPI_CREDENTIAL_ROTATE` | enabled=false | PERM_DENIED | PERM_DENIED |
| 编辑 scope | `OPENAPI_SCOPE_EDIT` | enabled=false | PERM_DENIED | OPENAPI_SCOPE_DENIED / PERM_DENIED |
| 编辑 IP 白名单 | `OPENAPI_IP_EDIT` | enabled=false | PERM_DENIED | PERM_DENIED |
| 查看 OpenAPI 调用日志 | `OPENAPI_LOG_VIEW` | enabled=false | PERM_DENIED | PERM_DENIED |
| 查看系统审计日志 | `AUDIT_OPERATION_VIEW` / `AUDIT_REQUEST_VIEW` / `AUDIT_ERROR_VIEW` / `AUDIT_RECORD_VIEW` | enabled=false | PERM_DENIED | PERM_DENIED |
| 查看平台审计日志 | `authStore.hasPlatformPermission("PLAT_AUDIT_VIEW")` | enabled=false | PERM_DENIED | PERM_DENIED |
| 查看运维健康/配置/版本/迁移 | `authStore.hasPlatformPermission("OPS_*_VIEW")` | enabled=false | PERM_DENIED | PERM_DENIED |
| 编辑运行配置 | `OPS_CONFIG_EDIT` | enabled=false | ENH 接口不在本页实现；始终不作为主流程入口 | PERM_DENIED |

## 空态与错误态

| 场景 | 判定条件 | 展示内容 | requestId 展示 | 重试动作 |
| --- | --- | --- | --- | --- |
| OpenAPI 无上下文 | 缺 systemId / tenantId / memberId | 提示先进入系统并选择租户 | 不需要 | 重新进入系统或切换租户 |
| OpenAPI 客户端为空 | OPM-001 records 为空 | 暂无客户端；有 OPENAPI_CLIENT_CREATE 时显示新建入口 | 不需要 | 重新加载 |
| scope 目录为空 | OPM-009 modules 为空 | 暂无可授权 scope | OPM-009 错误时展示 | 重新加载 |
| 调用日志为空 | OPM-008 records 为空 | 暂无调用日志，保留 requestId 搜索框 | 不需要 | 调整筛选后查询 |
| 审计日志为空 | AUD-* records 为空 | 暂无审计日志，保留 requestId 搜索框 | 不需要 | 调整时间范围或 requestId |
| 审计详情不存在 | AUD-007/AUD-008 返回 AUDIT_LOG_NOT_FOUND | 展示错误 message/code/requestId | 必须展示 | 返回列表 |
| 健康异常 | OPS-* status/checks 为 DOWN 或 WARN | 展示 component、message、suggestion | 必须展示响应 requestId | 重新检查 |
| API 错误 | `ApiClientError` | `message` + `code` + `requestId` | 必须展示 | retryable=true 时允许重试 |

## requestId 与审计

- OpenAPI 调用日志模型 `OpenApiAccessLogQuery.requestId` 支持 requestId 检索，列表字段包含 `requestId`，页面状态保留 `lastRequestId`。
- 审计日志模型 `AuditLogQueryBO.requestId` 支持 requestId 检索，`markRequestIdCopied` 记录被复制的 requestId，详情响应展示 `traceId` 与 `requestId`。
- 运维健康、配置、版本和迁移响应均保留 `requestId`，健康异常态必须展示该 requestId。
- 写接口 `OPM-002`、`OPM-005` 按 SDK 要求传入 `X-Idempotency-Key` 或 body `idempotencyKey`。

## 无旁路请求检查

| 检查项 | 结论 |
| --- | --- |
| 页面没有直接调用 `axios` / `fetch` / `XMLHttpRequest` | 通过；FE-011 页面模型未出现这些调用 |
| 页面所有请求均通过 `frontend/src/api` typed client | 通过；全部使用 `ApiClient.call` 和冻结 API ID |
| API ID 均存在于 `API_ENDPOINTS` | 通过；OPM-001 至 OPM-009、AUD-001 至 AUD-008、OPS-001 至 OPS-006 来自 SDK |
| 未伪造冻结 API 未声明的创建/编辑/删除/提交语义 | 通过；仅实现 OPM 管理写接口，审计/运维保持只读，未实现 OPS-005 编辑入口 |
| `systemId` / `tenantId` 等上下文字段由统一请求层或路由上下文补齐 | 通过；`systemContext.toPathParams()` / `toTenantHeader()` 与 `auth.toApiContext()` 统一提供 |
| 无法补齐必填上下文时，页面阻止请求并展示空态或明确提示 | 通过；模型在请求前校验 OpenAPI 和系统审计上下文 |
