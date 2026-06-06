# 后端 API 文档

本文档只描述当前 `backend/` 已实现并对外暴露的接口。基础 CRUD 位于 `base` 包，不直接暴露 Controller；外部入口统一位于 `manage.controller`。

## 接口清单

| 模块 | 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|---|
| 认证 | POST | `/api/v1/auth/register` | 注册平台账号 | 匿名 |
| 认证 | POST | `/api/v1/auth/login` | 登录并获取 Bearer Token | 匿名 |
| 认证 | POST | `/api/v1/auth/refresh` | 刷新当前上下文 Token | Bearer |
| 认证 | GET | `/api/v1/auth/me` | 当前用户信息 | Bearer |
| 认证 | POST | `/api/v1/auth/logout` | 退出登录 | Bearer |
| 平台中心 | GET | `/api/v1/platform/tenants` | 租户分页 | Bearer + `tenant:view` 平台权限 |
| 平台中心 | POST | `/api/v1/platform/tenants` | 创建租户 | Bearer + `tenant:create` 平台权限 |
| 平台中心 | GET | `/api/v1/platform/systems` | 系统分页 | Bearer + `system:view` 平台权限 |
| 平台中心 | POST | `/api/v1/platform/systems` | 创建系统 | Bearer + `system:create` 平台权限 |
| 平台中心 | PATCH | `/api/v1/platform/systems/{systemId}/status` | 更新系统状态 | Bearer + `system:status` 平台权限 |
| 平台中心 | POST | `/api/v1/platform/context/enter-system` | 进入系统上下文并重新签发 Token | Bearer |
| 应用配置 | GET/POST | `/api/v1/system/config/apps` | 应用列表/创建 | Bearer + 系统内权限 |
| 应用配置 | POST | `/api/v1/system/config/apps/{appId}/publish` | 发布应用版本 | Bearer + `app:publish` |
| 应用配置 | GET/POST | `/api/v1/system/config/modules` | 模块列表/创建 | Bearer + 系统内权限 |
| 应用配置 | GET/POST | `/api/v1/system/config/fields` | 字段列表/创建 | Bearer + 系统内权限 |
| 应用配置 | POST | `/api/v1/system/config/field-options` | 创建字段选项 | Bearer + `field:save` |
| 应用配置 | GET/POST | `/api/v1/system/config/pages` | 页面列表/创建 | Bearer + 系统内权限 |
| 应用配置 | POST | `/api/v1/system/config/pages/{pageId}/publish` | 发布页面 | Bearer + `page:publish` |
| 应用配置 | GET/POST | `/api/v1/system/config/menus` | 菜单列表/创建 | Bearer + 系统内权限 |
| 应用配置 | GET/POST | `/api/v1/system/config/dictionaries` | 字典列表/创建 | Bearer + 系统内权限 |
| 应用配置 | POST | `/api/v1/system/config/dictionary-items` | 创建字典项 | Bearer + `dict:save` |
| 运行记录 | GET | `/api/v1/system/records` | 记录分页 | Bearer + `record:view` |
| 运行记录 | POST | `/api/v1/system/records` | 创建记录 | Bearer + `record:create` |
| 运行记录 | PUT | `/api/v1/system/records/{recordId}` | 更新记录 | Bearer + `record:update` |
| 运行记录 | GET | `/api/v1/system/records/{recordId}` | 记录详情 | Bearer + `record:view` |
| 运行记录 | DELETE | `/api/v1/system/records/{recordId}` | 软删除记录 | Bearer + `record:delete` |
| 运行记录 | POST | `/api/v1/system/records/comments` | 添加评论 | Bearer + `record:comment` |
| 流程 | GET/POST | `/api/v1/system/workflow/templates` | 流程模板列表/创建 | Bearer + 系统内权限 |
| 流程 | POST | `/api/v1/system/workflow/versions` | 创建流程版本 | Bearer + `workflow:save` |
| 流程 | POST | `/api/v1/system/workflow/versions/{versionId}/publish` | 发布流程版本 | Bearer + `workflow:publish` |
| 流程 | POST | `/api/v1/system/workflow/instances/start` | 发起流程 | Bearer + `workflow:start` |
| 流程 | GET | `/api/v1/system/workflow/tasks` | 任务分页 | Bearer |
| 流程 | POST | `/api/v1/system/workflow/tasks/{taskId}/handle` | 处理任务 | Bearer + `workflow:handle` |
| 文件与任务 | GET/POST | `/api/v1/system/files` | 文件元数据列表/创建 | Bearer + 文件权限 |
| 文件与任务 | POST | `/api/v1/system/files/relations` | 关联文件 | Bearer + `file:link` |
| 文件与任务 | DELETE | `/api/v1/system/files/{fileId}` | 删除文件元数据 | Bearer + `file:delete` |
| 文件与任务 | GET/POST | `/api/v1/system/files/tasks` | 导入导出任务列表/创建 | Bearer + 导入导出权限 |
| OpenAPI 管理 | GET/POST | `/api/v1/system/openapi/clients` | 外部应用列表/创建 | Bearer + OpenAPI 权限 |
| OpenAPI 管理 | POST | `/api/v1/system/openapi/credentials` | 创建凭证并返回一次性密钥 | Bearer + `openapi:credential` |
| OpenAPI 管理 | POST | `/api/v1/system/openapi/scopes` | 保存授权范围 | Bearer + `openapi:scope` |
| OpenAPI 管理 | POST | `/api/v1/system/openapi/ip-whitelist` | 保存 IP 白名单，支持单条或批量 | Bearer + `openapi:ip` |
| OpenAPI 调用 | GET | `/api/v1/open/records/{moduleId}/{recordId}` | 外部系统查询记录 | HMAC |
| OpenAPI 调用 | POST | `/api/v1/open/records` | 外部系统创建记录 | HMAC |
| 运维中心 | GET | `/api/v1/ops/health` | 健康检查 | 匿名 |
| 运维中心 | GET | `/api/v1/ops/audit-logs` | 审计日志分页 | Bearer |
| 运维中心 | GET/POST | `/api/v1/ops/configs` | 全局配置列表/创建 | Bearer |

## 请求方法与路径

| 模块 | 请求方法 | 路径 | Controller 方法 | 说明 |
|---|---|---|---|---|
| 认证 | POST | `/api/v1/auth/register` | `AuthController.register` | 注册平台账号 |
| 认证 | POST | `/api/v1/auth/login` | `AuthController.login` | 登录并获取 Bearer Token |
| 认证 | POST | `/api/v1/auth/refresh` | `AuthController.refresh` | 刷新当前上下文 Token |
| 认证 | GET | `/api/v1/auth/me` | `AuthController.me` | 当前用户信息 |
| 认证 | POST | `/api/v1/auth/logout` | `AuthController.logout` | 退出登录 |
| 平台中心 | GET | `/api/v1/platform/tenants` | `PlatformController.tenants` | 租户分页 |
| 平台中心 | POST | `/api/v1/platform/tenants` | `PlatformController.createTenant` | 创建租户 |
| 平台中心 | GET | `/api/v1/platform/systems` | `PlatformController.systems` | 系统分页 |
| 平台中心 | POST | `/api/v1/platform/systems` | `PlatformController.createSystem` | 创建系统 |
| 平台中心 | PATCH | `/api/v1/platform/systems/{systemId}/status` | `PlatformController.updateSystemStatus` | 更新系统状态 |
| 平台中心 | POST | `/api/v1/platform/context/enter-system` | `PlatformController.enterSystem` | 进入系统上下文并重新签发 Token |
| 应用配置 | GET | `/api/v1/system/config/apps` | `ConfigController.apps` | 应用分页 |
| 应用配置 | POST | `/api/v1/system/config/apps` | `ConfigController.saveApp` | 创建应用 |
| 应用配置 | POST | `/api/v1/system/config/apps/{appId}/publish` | `ConfigController.publishApp` | 发布应用版本 |
| 应用配置 | GET | `/api/v1/system/config/modules` | `ConfigController.modules` | 模块分页 |
| 应用配置 | POST | `/api/v1/system/config/modules` | `ConfigController.saveModule` | 创建模块 |
| 应用配置 | GET | `/api/v1/system/config/fields` | `ConfigController.fields` | 字段分页 |
| 应用配置 | POST | `/api/v1/system/config/fields` | `ConfigController.saveField` | 创建字段 |
| 应用配置 | POST | `/api/v1/system/config/field-options` | `ConfigController.saveFieldOption` | 创建字段选项 |
| 应用配置 | GET | `/api/v1/system/config/pages` | `ConfigController.pages` | 页面分页 |
| 应用配置 | POST | `/api/v1/system/config/pages` | `ConfigController.savePage` | 创建页面 |
| 应用配置 | POST | `/api/v1/system/config/pages/{pageId}/publish` | `ConfigController.publishPage` | 发布页面 |
| 应用配置 | GET | `/api/v1/system/config/menus` | `ConfigController.menus` | 菜单分页 |
| 应用配置 | POST | `/api/v1/system/config/menus` | `ConfigController.saveMenu` | 创建菜单 |
| 应用配置 | GET | `/api/v1/system/config/dictionaries` | `ConfigController.dictionaries` | 字典分页 |
| 应用配置 | POST | `/api/v1/system/config/dictionaries` | `ConfigController.saveDictionary` | 创建字典 |
| 应用配置 | POST | `/api/v1/system/config/dictionary-items` | `ConfigController.saveDictionaryItem` | 创建字典项 |
| 运行记录 | GET | `/api/v1/system/records` | `RuntimeRecordController.records` | 记录分页 |
| 运行记录 | POST | `/api/v1/system/records` | `RuntimeRecordController.create` | 创建记录 |
| 运行记录 | PUT | `/api/v1/system/records/{recordId}` | `RuntimeRecordController.update` | 更新记录 |
| 运行记录 | GET | `/api/v1/system/records/{recordId}` | `RuntimeRecordController.detail` | 记录详情 |
| 运行记录 | DELETE | `/api/v1/system/records/{recordId}` | `RuntimeRecordController.delete` | 软删除记录 |
| 运行记录 | POST | `/api/v1/system/records/comments` | `RuntimeRecordController.comment` | 添加评论 |
| 流程 | GET | `/api/v1/system/workflow/templates` | `WorkflowController.templates` | 流程模板分页 |
| 流程 | POST | `/api/v1/system/workflow/templates` | `WorkflowController.saveTemplate` | 创建流程模板 |
| 流程 | POST | `/api/v1/system/workflow/versions` | `WorkflowController.saveVersion` | 创建流程版本 |
| 流程 | POST | `/api/v1/system/workflow/versions/{versionId}/publish` | `WorkflowController.publishVersion` | 发布流程版本 |
| 流程 | POST | `/api/v1/system/workflow/instances/start` | `WorkflowController.start` | 发起流程 |
| 流程 | GET | `/api/v1/system/workflow/tasks` | `WorkflowController.tasks` | 任务分页 |
| 流程 | POST | `/api/v1/system/workflow/tasks/{taskId}/handle` | `WorkflowController.handle` | 处理任务 |
| 文件与任务 | GET | `/api/v1/system/files` | `FileController.files` | 文件元数据分页 |
| 文件与任务 | POST | `/api/v1/system/files` | `FileController.createFile` | 创建文件元数据 |
| 文件与任务 | POST | `/api/v1/system/files/relations` | `FileController.link` | 关联文件 |
| 文件与任务 | DELETE | `/api/v1/system/files/{fileId}` | `FileController.delete` | 删除文件元数据 |
| 文件与任务 | GET | `/api/v1/system/files/tasks` | `FileController.tasks` | 导入导出任务分页 |
| 文件与任务 | POST | `/api/v1/system/files/tasks` | `FileController.createTask` | 创建导入导出任务 |
| OpenAPI 管理 | GET | `/api/v1/system/openapi/clients` | `OpenApiManageController.clients` | 外部应用分页 |
| OpenAPI 管理 | POST | `/api/v1/system/openapi/clients` | `OpenApiManageController.saveClient` | 创建外部应用 |
| OpenAPI 管理 | POST | `/api/v1/system/openapi/credentials` | `OpenApiManageController.createCredential` | 创建凭证并返回一次性密钥 |
| OpenAPI 管理 | POST | `/api/v1/system/openapi/scopes` | `OpenApiManageController.saveScope` | 保存授权范围 |
| OpenAPI 管理 | POST | `/api/v1/system/openapi/ip-whitelist` | `OpenApiManageController.saveIp` | 保存 IP 白名单 |
| OpenAPI 调用 | GET | `/api/v1/open/records/{moduleId}/{recordId}` | `OpenApiRecordController.detail` | 外部系统查询记录 |
| OpenAPI 调用 | POST | `/api/v1/open/records` | `OpenApiRecordController.create` | 外部系统创建记录 |
| 运维中心 | GET | `/api/v1/ops/health` | `OpsController.health` | 健康检查 |
| 运维中心 | GET | `/api/v1/ops/audit-logs` | `OpsController.auditLogs` | 审计日志分页 |
| 运维中心 | GET | `/api/v1/ops/configs` | `OpsController.configs` | 全局配置分页 |
| 运维中心 | POST | `/api/v1/ops/configs` | `OpsController.saveConfig` | 创建全局配置 |

## 通用请求与响应

后台接口请求头：

```http
Authorization: Bearer <accessToken>
```

通用响应：

```json
{
  "code": 0,
  "message": "成功",
  "data": {}
}
```

分页出参：

```json
{
  "pageNo": 1,
  "pageSize": 20,
  "total": 100,
  "records": []
}
```

## 入参

### 平台中心

`GET /api/v1/platform/tenants`：`pageNo`、`pageSize`、`keyword`。

`GET /api/v1/platform/systems`：`pageNo`、`pageSize`、`tenantId`、`keyword`。

租户和系统分页属于平台全量管理数据，必须具备平台权限。普通账号如需进入系统，应通过已知 `systemId` 调用 `/api/v1/platform/context/enter-system`；后端仅允许系统 owner 或 `system_member` 启用成员进入，不通过平台列表暴露全量数据。

### 运行记录

`GET /api/v1/system/records` 支持：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| pageNo/pageSize | number | 否 | 分页 |
| systemId/tenantId | number | 否 | 不传时从 Bearer Token 当前系统上下文补齐 |
| appId | number | 否 | 按应用过滤 |
| moduleId | number | 是 | 模块 ID |
| recordNo | string | 否 | 按记录编号模糊过滤 |
| status | string | 否 | 记录状态过滤 |
| recordStatus | string | 否 | 兼容前端旧字段；`status` 为空时生效 |

`POST /api/v1/system/records/comments` 支持：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| recordId | number | 是 | 记录 ID |
| commentText | string | 否 | 评论内容 |
| comment | string | 否 | 兼容前端字段，等同于 `commentText` |
| systemId/tenantId | number | 否 | 可不传；传入时必须与记录归属一致 |

评论保存以记录归属补齐 `systemId/tenantId`，并校验当前 Token 上下文、系统成员和 `record:comment` 权限。

### 流程版本

`POST /api/v1/system/workflow/versions`：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| templateId | number | 是 | 流程模板 ID |
| versionNo | string | 否 | 必须是整数字符串；为空由后端按模板生成下一个版本号 |
| nodeJson | string | 是 | 节点 JSON |
| edgeJson | string | 是 | 连线 JSON |
| conditionJson | string | 否 | 条件 JSON |
| settingJson | string | 否 | 设置 JSON |
| systemId/tenantId | number | 否 | 可不传；传入时必须与模板归属一致 |

后端以 `workflow_template` 归属补齐流程版本的 `systemId/tenantId`，不接受跨系统或跨租户保存。

### OpenAPI

OpenAPI HMAC 请求头：

| Header | 说明 |
|---|---|
| X-Open-Client-Id | 外部应用 `clientId` |
| X-Open-Key-Version | 整数密钥版本 |
| X-Open-Timestamp | Unix 秒级时间戳，允许 5 分钟偏差 |
| X-Open-Nonce | 随机串，参与签名 |
| X-Open-Signature | `Base64URL(HMAC-SHA256(secretOnce, canonical))` |
| Idempotency-Key | 可选，POST 创建记录时用于幂等 |

`canonical = method + "\n" + requestURI + "\n" + timestamp + "\n" + nonce + "\n" + sha256(body)`。

`POST /api/v1/system/openapi/credentials`：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| clientPk | number | 是 | OpenAPI 应用主键 |
| keyVersion | string | 否 | 整数密钥版本；为空由后端生成 |
| expiresAt | datetime | 否 | 过期时间 |

出参 `CredentialVO.secretOnce` 只返回一次。数据库字段 `openapi_credential.secret_digest` 保存加密密钥包和摘要，认证时会校验 `keyVersion`、状态、过期时间、密钥包摘要，并使用解密出的实际密钥验签。旧的纯 digest 凭证不能用于认证，需要重新生成。

`POST /api/v1/system/openapi/ip-whitelist`：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| clientPk | number | 是 | OpenAPI 应用主键 |
| ipValue | string | 否 | 单条 IP 或网段 |
| ipList | string[] | 否 | 批量 IP 或网段 |

`ipValue` 与 `ipList` 至少传一个；批量值会去重后逐条写入 `openapi_ip_whitelist.ip_value`。

## 出参

| VO | 字段 | 说明 |
|---|---|---|
| AuthTokenVO | accessToken、tokenType、resetRequired、user | 登录/刷新结果 |
| UserVO | id、account、realName、mobile、email、status、systemId、tenantId | 当前用户和上下文 |
| SimpleVO | fields | 配置、文件、任务等通用 Map 出参，不直接暴露 `base.entity` |
| RecordVO | id、systemId、tenantId、appId、moduleId、recordNo、recordStatus、processStatus、appVersionId、configSnapshot、values、createdBy、updatedBy、createdAt、updatedAt | 业务记录详情 |
| CredentialVO | id、clientPk、keyVersion、secretOnce、status、expiresAt、createdAt | 凭证创建结果 |
| HealthVO | serviceStatus、databaseStatus、redisStatus、storageStatus、scriptVersionStatus | 运维健康检查 |

## 错误码

| code | 枚举 | 说明 |
|---|---|---|
| 0 | SUCCESS | 成功 |
| 400 | PARAM_ERROR | 参数错误、JSON 不合法或版本号/密钥版本格式错误 |
| 401 | UNAUTHORIZED | 未认证、Token 无效、账号密码错误 |
| 403 | FORBIDDEN | 无权限、系统/租户上下文不一致、角色权限不足 |
| 404 | NOT_FOUND | 账号、系统、应用、模块、记录、流程、文件等不存在 |
| 409 | CONFLICT | 唯一约束冲突、重复编码或重复唯一字段值 |
| 460 | CONFIG_NOT_PUBLISHED | 配置未发布或流程未绑定 |
| 461 | INVALID_STATUS | 当前状态不允许执行操作 |
| 470 | OPENAPI_SIGNATURE_INVALID | OpenAPI 签名、时间戳、凭证、密钥版本、密钥材料或 IP 白名单校验失败 |
| 471 | IDEMPOTENCY_CONFLICT | 同一幂等键请求摘要不一致 |
| 480 | FILE_STORAGE_NOT_CONFIGURED | 文件存储未配置 |
| 500 | SYSTEM_ERROR | 系统异常 |

## 鉴权与权限说明

- 除注册、登录、OpenAPI 调用和 `/api/v1/ops/health` 外，后台接口均要求 Bearer Token。
- `admin` 只可绕过 `requirePlatformAction` 的平台操作权限；系统内接口仍必须校验 Token 中的 `systemId/tenantId`、`system_member` 启用成员身份和 `role_permission` 的 ACTION 权限。
- 平台列表接口是平台管理能力，`listTenants` 要求 `tenant:view`，`listSystems` 要求 `system:view`。
- 进入系统上下文会校验请求 `tenantId` 与系统归属一致，并要求当前账号是系统 owner 或启用成员；进入后后续系统内业务接口仍按系统内 RBAC 校验。
- OpenAPI 不继承后台用户登录态；先用 HMAC、时间戳、密钥版本、凭证状态、过期时间和 IP 白名单认证 client，再校验 `openapi_scope` 覆盖 SYSTEM、TENANT、APP、MODULE 和 ACTION。

## 前后端字段映射说明

| 前端字段 | 后端 BO/VO 字段 | 数据库字段 | 说明 |
|---|---|---|---|
| tenantId | tenantId | tenant_id | 租户隔离字段 |
| systemId | systemId | system_id | 系统隔离字段 |
| appId | appId | app_id | 应用归属，记录列表支持筛选 |
| moduleId | moduleId | module_id | 模块归属 |
| recordNo | recordNo | record_no | 记录编号，列表支持模糊筛选 |
| status | recordStatus | record_status | 记录列表推荐传 `status` |
| recordStatus | recordStatus | record_status | 兼容旧前端字段 |
| comment | commentText | record_comment.comment_content | 评论兼容字段 |
| commentText | commentText | record_comment.comment_content | 评论内容 |
| versionNo | versionNo | workflow_version.version_no | 整数版本号；为空后端生成 |
| keyVersion | keyVersion | openapi_credential.key_version | 整数密钥版本；为空后端生成 |
| secretOnce | secretOnce | 不落明文 | 创建凭证时只返回一次 |
| ipValue | ipValue | openapi_ip_whitelist.ip_value | 单条白名单 |
| ipList[] | ipList[] | openapi_ip_whitelist.ip_value | 批量白名单，逐条写入 |
| scopeType/scopeValue | scopeType/scopeValue | openapi_scope.scope_type/scope_value | OpenAPI 授权范围 |
