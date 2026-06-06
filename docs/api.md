# unexamine 设计期 API 契约（冻结版）

## API 文档生成说明

本文档由 PM 在 API 契约最终冻结阶段，基于 `docs/prd.md`、`docs/project_understanding.md`、`docs/api.md`、`docs/api_review.md`、四角色 API 复核报告和 `.codex/state.json` 修订并冻结。冻结状态以 `docs/api_review.md` 的最终结论为准：DBA、backend、frontend、test 均 `pass`，32 个 API 审查 issue 均 `closed`，API 审查循环使用 `2/3` 次，允许 API 冻结，允许进入任务拆分阶段。

本文档是 PRD、项目理解和多角色契约审查后的设计期冻结 API 契约，供任务拆分阶段使用。冻结后，任何接口、字段、枚举、错误码、状态或权限语义变更都必须重新打开 API 契约评审；当前仅允许进入 `docs/task_plan.md` 和 `docs/tasks/` 任务拆分阶段，仍不允许直接进入 DB 设计、SQL、代码生成或后端/前端实现。

阶段标记：

| 标记 | 含义 |
| --- | --- |
| MVP | 首期必须实现并纳入验收。 |
| ENH | 后续增强，首期不阻塞主链路。 |
| PLACEHOLDER | 仅保留契约占位或边界，不在首期实现。 |
| INTERNAL | 内部运维或生成器能力，不面向普通业务用户。 |

## 全局约定

### 基础路径与认证

| 类型 | 约定 |
| --- | --- |
| 内部业务 API | `/api/v1/**` |
| 外部 OpenAPI | `/openapi/v1/**` |
| 认证方式 | 内部 API 使用 `Authorization: Bearer <accessToken>`；OpenAPI 使用 AK/SK 签名头。 |
| 请求追踪 | 前端可传 `X-Request-Id`，后端缺省生成；所有响应、错误、日志、导出任务和 OpenAPI 调用日志必须包含 `requestId`。 |
| 系统上下文 | 内部 API 通过路径中的 `systemId`、会话上下文和必要的 `X-Tenant-Id` 校验；前端不得写入创建人、审计字段、平台账号 ID 等系统字段。 |
| 时间格式 | ISO-8601 字符串，后端统一按 `Asia/Shanghai` 解释和展示。 |
| ID 类型 | 字符串 ID，前端不得假设为数字。 |

内部 API 统一响应 `ApiResponse<T>`：

```json
{
  "requestId": "req_20260605_000001",
  "traceId": "trc_20260605_000001",
  "timestamp": "2026-06-06T08:15:30+08:00",
  "success": true,
  "code": "COMMON_OK",
  "message": "success",
  "data": {},
  "meta": {
    "path": "/api/v1/systems/sys_001/runtime/modules/mod_001/records",
    "method": "POST",
    "idempotencyKey": "idem_001",
    "requestHash": "sha256:...",
    "idempotencyReplay": false,
    "resultSnapshotId": "snap_001"
  },
  "errors": []
}
```

错误响应 `ApiErrorResponse` 与成功响应同形，`success=false`，`data=null`。所有错误必须包含可断言字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| requestId | string | 是 | 请求级追踪 ID，前端错误页、后端日志、审计、导出任务和 OpenAPI 日志必须一致。 |
| traceId | string | 是 | 链路追踪 ID，同一次链路可跨多个内部调用。 |
| timestamp | string | 是 | ISO-8601，`Asia/Shanghai`。 |
| code | string | 是 | 模块化错误码，例如 `FIELD_UNIQUE_CONFLICT`。 |
| message | string | 是 | 面向用户或调用方的稳定提示，不含敏感内部栈。 |
| errors | array | 否 | 字段级或对象级错误明细。 |
| meta.path/method | string | 是 | 便于测试断言接口来源。 |
| meta.idempotencyKey/requestHash/resultSnapshotId | string | 写接口按需 | 幂等请求必须返回，用于重复请求断言。 |

`errors[]` 明细结构：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| errorId | string | 是 | 单条错误明细 ID。 |
| targetType | string | 否 | `FIELD`、`RECORD`、`PERMISSION`、`STATE`、`OPENAPI`、`FILE`、`EXPORT`、`FLOW`。 |
| fieldCode | string | 否 | 字段级校验失败时必填。 |
| objectType | string | 否 | 业务对象类型，例如 `MODULE_RECORD`、`FLOW_TASK`。 |
| objectId | string | 否 | 业务对象 ID。 |
| reason | string | 是 | 稳定机器可读原因，例如 `REQUIRED_MISSING`、`TYPE_INVALID`、`UNIQUE_CONFLICT`。 |
| expected | any | 否 | 期望值或允许范围。 |
| actual | any | 否 | 实际值，敏感字段必须脱敏。 |
| retryable | boolean | 是 | 是否建议原请求重试。 |
| userMessage | string | 是 | 前端可直接展示的短提示。 |

分页请求：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| pageNo | integer | 否 | 1 | 页码，从 1 开始。 |
| pageSize | integer | 否 | 20 | 每页数量，最大值由后端限制。 |
| sorter | array | 否 | [] | 排序字段，字段必须在接口允许排序字段内。 |
| filters | object | 否 | {} | 筛选条件，动态模块使用字段编码和操作符。 |

分页响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| records | array | 当前页数据。 |
| total | integer | 总数。 |
| pageNo | integer | 当前页码。 |
| pageSize | integer | 每页数量。 |
| hasNext | boolean | 是否还有下一页。 |

所有分页列表接口响应必须使用 `PageResult<T>` 放入 `data`，`records` 的 item 结构必须在对应模块 VO 中定义。分页请求支持统一筛选 DSL：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | string | 否 | 模块声明允许的关键字搜索字段。 |
| filters[].field | string | 是 | 固定字段名或动态字段 `fieldCode`。 |
| filters[].op | string | 是 | `EQ`、`NE`、`LIKE`、`IN`、`BETWEEN`、`GT`、`GE`、`LT`、`LE`、`IS_NULL`、`NOT_NULL`。 |
| filters[].value | any | 按 op | 筛选值；`BETWEEN` 必须为二元数组。 |
| sorter[].field | string | 是 | 允许排序字段。 |
| sorter[].direction | string | 是 | `ASC`、`DESC`。 |

### 动态字段值契约

动态模块的新增、编辑、详情、列表和 OpenAPI 均使用统一字段值结构，不直接暴露数据库 EAV 实体。

| 字段 | 类型 | 必填 | 前端可写 | 说明 |
| --- | --- | --- | --- | --- |
| fieldId | string | 否 | 否 | 后端返回字段 ID，前端提交优先使用 `fieldCode`。 |
| fieldCode | string | 是 | 是 | 字段编码，同一模块内唯一。 |
| fieldType | string | 否 | 否 | 字段类型枚举。 |
| value | any | 是 | 是 | 原始值；附件/图片为 fileId 数组，子表为行数组，关联为记录 ID 数组。 |
| displayValue | string/object/array | 否 | 否 | 后端补齐的展示值。 |
| valueSnapshot | object | 否 | 否 | 后端保存和返回的历史展示快照。 |
| readonlyReason | string | 否 | 否 | 字段不可写原因。 |

字段类型 MVP 枚举：`TEXT`、`TEXTAREA`、`NUMBER`、`MONEY`、`DATE`、`DATETIME`、`SELECT`、`MULTI_SELECT`、`SWITCH`、`MEMBER`、`DEPT`、`ATTACHMENT`、`IMAGE`、`AUTO_NO`、`RELATION`、`SUB_TABLE`、`ADDRESS`、`TAG`、`JSON`。

### 状态枚举

| 对象 | 状态 |
| --- | --- |
| accountStatus | `NORMAL`、`DISABLED`、`LOCKED` |
| systemStatus | `DRAFT`、`ENABLED`、`DISABLED`、`ARCHIVED` |
| tenantStatus | `ENABLED`、`DISABLED` |
| appStatus | `DRAFT`、`ENABLED`、`DISABLED`、`ARCHIVED` |
| moduleStatus | `DRAFT`、`PUBLISHED`、`DISABLED`、`ARCHIVED` |
| fieldStatus | `DRAFT`、`ENABLED`、`DISABLED`、`DELETED` |
| versionStatus | `DRAFT`、`PUBLISHED`、`DISCARDED` |
| recordStatus | `DRAFT`、`SUBMITTED`、`IN_APPROVAL`、`APPROVED`、`REJECTED`、`WITHDRAWN`、`ARCHIVED`、`DELETED` |
| flowTemplateStatus | `DRAFT`、`PUBLISHED`、`DISABLED` |
| flowInstanceStatus | `IN_APPROVAL`、`APPROVED`、`REJECTED`、`WITHDRAWN`、`TERMINATED` |
| flowTaskStatus | `PENDING`、`DONE`、`CANCELED`、`TRANSFERRED`、`RETURNED` |
| fileStatus | `TEMP`、`REFERENCED`、`DELETED`、`EXPIRED` |
| exportJobStatus | `QUEUED`、`PROCESSING`、`SUCCESS`、`FAILED`、`CANCELED` |
| openApiClientStatus | `DRAFT`、`ENABLED`、`DISABLED`、`EXPIRED` |

### 错误码命名空间

| 命名空间 | 场景 |
| --- | --- |
| COMMON | 通用参数、分页、幂等、资源不存在、并发冲突。 |
| AUTH | 登录、会话、刷新 token、账号状态。 |
| PLAT | 平台账号、平台角色、系统创建、平台配置。 |
| SYS | 系统、租户、成员、部门、系统内角色。 |
| MODULE | 应用、模块、运行记录、发布版本。 |
| FIELD | 字段类型、字段校验、字段权限、动态值。 |
| PERM | 菜单、操作、数据范围、字段可见可写、导出权限。 |
| FLOW | 流程模板、实例、任务、审批动作。 |
| UPLOAD | 文件上传、下载、预览、存储配置、引用关系。 |
| EXPORT | 导出模板、导出任务、结果文件、重试。 |
| OPENAPI | 签名、时间窗口、客户端、scope、限流、IP 白名单。 |
| AUDIT | 操作日志、请求日志、审计查询。 |
| OPS | 健康检查、配置检查、版本和运行配置。 |
| GENERATOR | 代码生成器表映射、生成任务、生成报告。 |

### 第 1-2 次审查闭环补充契约

#### 权限上下文模型

内部 API 后端必须按以下来源构造 `RequestContext`，前端不得在业务 BO 中伪造这些字段。

| 字段 | 来源 | 适用接口 | 规则 |
| --- | --- | --- | --- |
| accountId | Bearer token | 内部 API | 唯一登录主体，账号停用/锁定时拒绝。 |
| systemId | 路径 `{systemId}` 或平台系统接口目标 ID | 系统内接口 | 必须与当前会话可访问系统一致；平台只读审计例外需有平台权限。 |
| tenantId | `X-Tenant-Id`、会话租户、单租户默认租户 | 系统内接口 | 多租户必填或先切换；单租户由后端补齐默认租户。 |
| memberId | `systemId + accountId` 查询系统成员扩展 | 系统内接口 | 成员停用或不存在时拒绝，平台账号不等于系统成员。 |
| clientId | OpenAPI accessKey 解析 | OpenAPI | 外部调用只使用客户端上下文，不使用内部登录态。 |
| requestId | `X-Request-Id` 或后端生成 | 全部接口 | 贯穿响应、审计和日志。 |
| traceId | 网关/后端生成 | 全部接口 | 串联内部调用。 |

后端统一校验顺序：`requestId/traceId` 建立 -> 登录态或 OpenAPI 签名 -> 系统状态 -> 租户状态 -> 账号/成员/客户端状态 -> API/菜单权限 -> 操作权限 -> 字段可见/可写/导出/OpenAPI 字段权限 -> 数据范围 -> 业务状态/流程锁定 -> 幂等锁定 -> 业务事务 -> 审计日志。任一步失败返回对应模块错误码，不允许前端权限过滤替代后端校验。

前端统一权限禁用态模型：

| 模型 | 字段 | 说明 |
| --- | --- | --- |
| `AvailableAction` | `actionCode`、`label`、`visible`、`enabled`、`disabledReason`、`requiredPermission`、`stateReason`、`confirmRequired`、`danger` | 所有按钮、行操作、详情操作、流程动作、导出、凭证轮换统一使用。 |
| `PermissionHint` | `permissionCode`、`granted`、`disabledByRole`、`disabledByState`、`disabledByDataScope`、`message` | 页面或区块级权限提示。 |
| `FieldPermission` | `fieldCode`、`visible`、`writable`、`exportPlain`、`openApiReadable`、`openApiWritable`、`readonlyReason` | 列表、表单、详情、导出、OpenAPI 共用。 |
| `DataScopeRuleDTO` | `scopeType`、`deptIds`、`memberIds`、`customConditions`、`minVisibleRule` | `scopeType` 为 `SELF`、`DEPT`、`DEPT_TREE`、`ALL`、`CUSTOM`。 |
| `EffectivePermissionVO` | `memberId`、`roles`、`menus`、`operations`、`fieldPermissions`、`dataScopes`、`availableActions`、`version` | RBAC-010 返回，前端 typed SDK 权限模型来源。 |

#### 幂等策略

写接口统一支持 `X-Idempotency-Key`；若因历史兼容放在 body，则字段名仍为 `idempotencyKey`，后端归一化到同一上下文。以下接口必须要求幂等键：`PLAT-002`、`APP-002`、`MOD-002`、`FIELD-002`、`MOD-006`、`UI-008`、`RUN-004`、`RUN-006`、`RUN-008`、`FLOW-005`、`FLOW-009`、`FLOW-010`、`FLOW-013`、`FLOW-015`、`FLOW-016`、`FILE-001`、`EXP-004`、`EXP-007`、`EXP-008`、`OPM-002`、`OPM-005`、`OPN-003`、`OPN-004`、`OPN-005`、`OPN-006`。

| 维度 | 内部 API | OpenAPI |
| --- | --- | --- |
| scope | `INTERNAL:{accountId}:{systemId}:{tenantId}:{apiId}:{bizAction}:{idempotencyKey}` | `OPENAPI:{clientId}:{systemId}:{tenantId}:{apiId}:{bizAction}:{idempotencyKey}` |
| requestHash | `method + path + pathVars + query + canonicalJson(body去除idempotencyKey) + accountId/memberId` 的 SHA-256 | `method + path + canonicalQuery + bodyHash + accessKey + systemCode/tenantCode/moduleCode` 的 SHA-256 |
| resultSnapshot | 保存 `code`、`success`、核心 `data` 标识、状态、错误摘要、`requestId`、`createdAt` | 同内部 API，额外保存 `clientId`、`accessKey`、`scopeResult`、`signatureResult` |
| TTL | 默认 24 小时；导出任务、流程任务 72 小时 | 默认 72 小时；可由客户端策略缩短但不得小于 24 小时 |
| 同 key 同 hash | 返回第一次结果快照，`meta.idempotencyReplay=true` | 同内部 API |
| 同 key 不同 hash | `COMMON_IDEMPOTENCY_CONFLICT`，HTTP 409，`retryable=false` | `OPENAPI_IDEMPOTENCY_CONFLICT`，HTTP 409，`retryable=false` |
| 处理中重复请求 | `COMMON_IDEMPOTENCY_PROCESSING`，HTTP 423，返回 `Retry-After` | `OPENAPI_IDEMPOTENCY_PROCESSING`，HTTP 423，返回 `Retry-After` |
| 日志落点 | 请求日志、操作审计、幂等记录 | OpenAPI 调用日志、幂等记录、审计串联 requestId |

确定性校验失败可保存失败快照；存储不可用、流程引擎不可用、文件生成失败等可重试失败不得以成功快照回放。幂等表过期清理不得删除业务结果，只删除幂等判定记录和结果快照引用。

#### 动态字段唯一性规则

字段级唯一使用 `FieldDefinition.unique=true` 表达，组合唯一使用 `UniqueConstraintDTO` 表达。唯一性默认只约束非 `DELETED` 业务记录，软删除记录不参与冲突，软删除后允许新记录复用值；恢复已删除记录时必须重新校验唯一性。

| 规则 | 契约 |
| --- | --- |
| 字段级唯一作用域 | `systemId + tenantId + moduleId + fieldId + typedValueHash`。 |
| 组合唯一作用域 | `systemId + tenantId + moduleId + constraintCode + combinedTypedValueHash`。 |
| 空值规则 | `null`、空字符串、空数组默认跳过唯一校验；字段同时 `required=true` 时先返回必填错误。 |
| 支持唯一字段类型 | `TEXT`、`NUMBER`、`MONEY`、`DATE`、`DATETIME`、`SELECT`、`SWITCH`、`MEMBER`、`DEPT`、`AUTO_NO`、单值 `RELATION`。 |
| 不支持唯一字段类型 | `TEXTAREA`、`MULTI_SELECT`、`ATTACHMENT`、`IMAGE`、`SUB_TABLE`、`ADDRESS`、`TAG`、`JSON`。 |
| 草稿/发布影响 | 草稿字段可保存唯一规则，但只有发布后运行态生效；发布检查必须扫描历史非删除记录，发现冲突则阻止发布。 |
| 冲突返回 | `FIELD_UNIQUE_CONFLICT`，HTTP 409，`errors[]` 包含 `fieldCode`、`constraintCode`、`conflictRecordIds`、`conflictDisplayValues`。 |
| 启用唯一冲突 | `FIELD_UNIQUE_EXISTING_CONFLICT`，发布或启用唯一规则时返回冲突摘要和修复入口。 |

#### 应用/版本表域边界

MVP 中业务应用、应用版本、模块、字段、页面、菜单、配置版本、动态记录、导出模板和导出任务统一归属动态模块域，表名前缀为 `un_module_`。旧项目 `un_app_` 只表示 OpenAPI 历史表域，新项目不得继续创建 OpenAPI 用途的 `un_app_*` 表，也不得用 `un_app_` 表达业务应用。后端模块名 `examine-app` 保留给 OpenAPI 业务模块，DB 表域固定为 `un_openapi_`。

| 领域对象 | MVP 表域方向 | 说明 |
| --- | --- | --- |
| 业务应用/应用版本 | `un_module_app`、`un_module_app_version` | 不使用 `un_app_`。 |
| 模块/字段/页面/菜单/配置版本 | `un_module_` | 配置态和发布快照均在动态模块域。 |
| 运行记录/EAV/索引/历史/关系/子表 | `un_module_` | 不暴露物理结构给前端。 |
| OpenAPI 客户端/凭证/scope/IP/限流/幂等/日志 | `un_openapi_` | 旧 `un_app_*` 仅作为迁移参考。 |

#### 初始化与最小测试数据边界

生产 `init.sql` 只应包含平台默认管理员、平台角色、平台菜单、平台配置、字段类型元数据等建库 seed。默认平台管理员登录名固定为 `platform_admin`，必须绑定 `PLAT_SUPER_ADMIN`，密码不得在 API 契约中出现明文，DBA 后续通过安全占位哈希或部署变量注入。

`PLAT-002` 创建系统必须在一个业务事务内初始化：系统、默认租户 `default`、创建人成员扩展、`SYS_SUPER_ADMIN` 角色、系统默认菜单/权限、默认应用 `default_app`、字段类型引用。响应必须返回 `initializedObjects`，包含对象类型、编码、ID、状态，供 test 断言。

测试样例数据不进入生产 seed。端到端测试应优先通过公开 API 构造：登录/使用初始化账号 -> 创建系统 -> 创建应用/模块/字段/流程/权限 -> 创建记录/审批/导出/OpenAPI 客户端。确需测试夹具时，由后续 test 阶段提供独立测试导入包或测试 SQL，不得要求生产 API 暴露“一键造数”接口。

#### 状态流转与错误码断言

| 对象 | 允许流转 | 禁止操作与错误码 |
| --- | --- | --- |
| accountStatus | `NORMAL -> DISABLED/LOCKED`，`DISABLED/LOCKED -> NORMAL` | 停用/锁定账号登录返回 `AUTH_ACCOUNT_DISABLED` 或 `AUTH_ACCOUNT_LOCKED`。 |
| systemStatus | `DRAFT -> ENABLED -> DISABLED -> ENABLED`，任意非归档状态可 `ARCHIVED` | 归档后编辑返回 `SYS_ARCHIVED`；停用系统普通成员进入返回 `SYS_DISABLED`。 |
| tenantStatus | `ENABLED <-> DISABLED` | 停用租户新增业务数据返回 `SYS_TENANT_DISABLED`。 |
| appStatus | `DRAFT -> ENABLED -> DISABLED -> ENABLED`，非归档可归档 | 归档应用编辑返回 `MODULE_APP_STATUS_INVALID`。 |
| moduleStatus | `DRAFT -> PUBLISHED -> DISABLED -> PUBLISHED`，非归档可归档 | 停用模块新增返回 `MODULE_STATUS_INVALID`。 |
| fieldStatus | `DRAFT -> ENABLED -> DISABLED`，可标记 `DELETED` | 有历史数据物理删除返回 `FIELD_DELETE_HAS_DATA`。 |
| recordStatus | `DRAFT -> IN_APPROVAL -> APPROVED/REJECTED/WITHDRAWN`，可 `ARCHIVED/DELETED` | 审批中普通编辑/删除返回 `MODULE_RECORD_STATUS_CONFLICT`。 |
| flowInstanceStatus | `IN_APPROVAL -> APPROVED/REJECTED/WITHDRAWN/TERMINATED` | 终态再次处理返回 `FLOW_INSTANCE_STATUS_CONFLICT`。 |
| flowTaskStatus | `PENDING -> DONE/TRANSFERRED/RETURNED/CANCELED` | 非待办处理返回 `FLOW_TASK_ALREADY_HANDLED`。 |
| fileStatus | `TEMP -> REFERENCED -> DELETED/EXPIRED` | 已引用文件物理删除返回 `UPLOAD_FILE_REFERENCED`。 |
| exportJobStatus | `QUEUED -> PROCESSING -> SUCCESS/FAILED/CANCELED`，`FAILED -> QUEUED` | 成功任务重试/取消返回 `EXPORT_JOB_STATUS_CONFLICT`。 |
| openApiClientStatus | `DRAFT -> ENABLED -> DISABLED -> ENABLED`，可 `EXPIRED` | 停用/过期调用返回 `OPENAPI_CLIENT_DISABLED` 或 `OPENAPI_CLIENT_EXPIRED`。 |

模块级错误码必须按以下结构维护，本文后续各模块列出的错误码均适用：

| 字段 | 说明 |
| --- | --- |
| code | 稳定错误码。 |
| httpStatus | HTTP 状态，例如 400、401、403、404、409、423、429、500。 |
| trigger | 触发条件。 |
| userMessage | 前端展示提示。 |
| retryable | 是否可重试。 |
| auditRequired | 是否必须写审计或失败日志。 |

核心错误码冻结：

| code | httpStatus | trigger | retryable |
| --- | --- | --- | --- |
| `COMMON_PARAM_INVALID` | 400 | 参数类型、格式或必填缺失 | false |
| `COMMON_IDEMPOTENCY_CONFLICT` | 409 | 内部幂等键相同但请求摘要不同 | false |
| `COMMON_IDEMPOTENCY_PROCESSING` | 423 | 相同幂等请求处理中 | true |
| `PERM_DENIED` | 403 | API、菜单、操作或数据范围无权限 | false |
| `PERM_FIELD_WRITE_DENIED` | 403 | 提交无写权限字段 | false |
| `FIELD_REQUIRED_MISSING` | 400 | 必填字段为空 | false |
| `FIELD_VALUE_TYPE_INVALID` | 400 | 动态字段值类型不匹配 | false |
| `FIELD_UNIQUE_CONFLICT` | 409 | 运行保存触发唯一冲突 | false |
| `FIELD_UNIQUE_EXISTING_CONFLICT` | 409 | 发布或启用唯一规则发现历史冲突 | false |
| `MODULE_RECORD_STATUS_CONFLICT` | 409 | 当前记录状态不允许操作 | false |
| `FLOW_TASK_ALREADY_HANDLED` | 409 | 流程任务已被处理 | false |
| `UPLOAD_STORAGE_UNAVAILABLE` | 500 | 文件存储不可用 | true |
| `EXPORT_FILE_GENERATE_FAILED` | 500 | 导出文件生成失败 | true |
| `OPENAPI_ACCESS_KEY_INVALID` | 401 | accessKey 不存在或格式非法 | false |
| `OPENAPI_SIGNATURE_INVALID` | 401 | 签名不匹配 | false |
| `OPENAPI_TIMESTAMP_EXPIRED` | 401 | 时间戳超出窗口 | false |
| `OPENAPI_NONCE_REPLAY` | 409 | nonce 重放 | false |
| `OPENAPI_BODY_HASH_MISMATCH` | 400 | body hash 与实际请求体不一致 | false |
| `OPENAPI_RATE_LIMITED` | 429 | OpenAPI 触发限流 | true |
| `OPENAPI_IDEMPOTENCY_CONFLICT` | 409 | OpenAPI 幂等键冲突 | false |

#### 逐模块 DTO/VO 补充索引

以下模型是 typed SDK 和测试断言的最低粒度。各接口可在不改变字段语义的前提下增加只读扩展字段，但不得删除或改名。

| 模块 | 请求 DTO/BO | 响应 VO | 适用 API |
| --- | --- | --- | --- |
| 平台账号/角色 | `PlatformAccountQuery`、`PlatformAccountSaveBO`、`PlatformAccountUpdateBO`、`PlatformAccountStatusBO`、`PlatformAccountResetPasswordBO`、`PlatformAccountRoleAssignBO`、`PlatformRoleSaveBO`、`PlatformRolePermissionBO` | `PlatformAccountListVO`、`PlatformAccountDetailVO`、`PlatformRoleVO`、`PlatformPermissionCatalogVO`、`PlatformMenuTreeVO` | `PLAT-006` 至 `PLAT-018` |
| 系统/租户/成员 | `SystemEnterBO`、`TenantSwitchBO`、`MemberQuery`、`MemberInviteBO`、`MemberUpdateBO`、`MemberRoleAssignBO` | `SystemContextVO`、`TenantVO`、`MemberListVO`、`MemberDetailVO` | `SYS-*`、`MEM-*` |
| 字典 | `DictTypeQuery`、`DictTypeSaveBO`、`DictTypeUpdateBO`、`DictStatusBO`、`DictItemQuery`、`DictItemSaveBO`、`DictItemUpdateBO`、`DictDeleteBO` | `DictTypeVO`、`DictItemVO`、`DictUsageVO`、`DictCacheRefreshVO` | `DICT-001` 至 `DICT-011` |
| RBAC | `RolePermissionSaveBO`、`PermissionCatalogQuery`、`DataScopeRuleDTO` | `RolePermissionDetailVO`、`PermissionCatalogVO`、`EffectivePermissionVO` | `RBAC-009` 至 `RBAC-013` |
| 动态 schema | `FieldSaveBO`、`FieldUpdateBO`、`UniqueConstraintDTO`、`ListViewSchemaSaveBO`、`FormSchemaSaveBO`、`DetailSchemaSaveBO`、`PublishRequestBO` | `FieldDefinitionVO`、`RuntimeModuleSchemaVO`、`PublishCheckResultVO`、`CheckIssueVO` | `FIELD-*`、`UI-*`、`RUN-002` |
| 运行台记录 | `RecordQueryBO`、`RecordSaveBO`、`RecordUpdateBO`、`RecordSubmitBO`、`RelationRecordQueryBO` | `RecordListItemVO`、`RecordDetailVO`、`RecordMutationResultVO`、`RecordActionVO`、`FieldValidationErrorVO` | `RUN-003` 至 `RUN-010` |
| 流程工作台 | `FlowTaskQueryBO`、`FlowActionBO`、`FlowWithdrawBO`、`FlowClaimBO` | `FlowTaskListItemVO`、`FlowTaskDetailVO`、`FlowHistoryItemVO`、`FlowDiagramVO`、`FlowActionResultVO` | `FLOW-007` 至 `FLOW-018` |
| 文件 | `FileUploadRequest`、`FileQueryBO`、`FileBindDTO`、`FileUnbindDTO` | `FileInfoVO`、`FileListItemVO`、`FileReferenceVO`、`FileAccessVO` | `FILE-001` 至 `FILE-007` |
| 导出 | `ExportTemplateSaveBO`、`ExportJobCreateBO`、`ExportJobQueryBO`、`ExportJobActionBO` | `ExportTemplateVO`、`ExportJobListItemVO`、`ExportJobDetailVO`、`ExportFailureReasonVO` | `EXP-001` 至 `EXP-008` |
| OpenAPI 管理 | `OpenApiClientSaveBO`、`OpenApiScopeSaveBO`、`OpenApiRateLimitPolicyDTO`、`OpenApiIpWhitelistBO` | `OpenApiClientListItemVO`、`OpenApiClientDetailVO`、`OpenApiCredentialOnceVO`、`OpenApiScopeCatalogVO`、`OpenApiAccessLogVO` | `OPM-001` 至 `OPM-009` |
| 审计/运维 | `AuditLogQueryBO`、`OpsHealthQueryBO` | `AuditLogListItemVO`、`AuditLogDetailVO`、`BeforeAfterSnapshotVO`、`HealthCheckResultVO`、`OpsComponentStatusVO` | `AUD-*`、`OPS-*` |

平台中心补充接口：

| API ID | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| PLAT-013 | GET | `/api/v1/platform/accounts/{accountId}` | 平台账号详情。 |
| PLAT-014 | PUT | `/api/v1/platform/accounts/{accountId}` | 编辑平台账号展示名、手机、邮箱等非系统字段。 |
| PLAT-015 | POST | `/api/v1/platform/accounts/{accountId}/password/reset` | 管理员重置密码，必须写审计。 |
| PLAT-016 | PUT | `/api/v1/platform/accounts/{accountId}/roles` | 分配平台角色。 |
| PLAT-017 | POST | `/api/v1/platform/roles` | 创建平台角色。 |
| PLAT-018 | PUT | `/api/v1/platform/roles/{roleId}` | 编辑平台角色。 |
| PLAT-019 | PATCH | `/api/v1/platform/roles/{roleId}/status` | 启停平台角色。 |
| PLAT-020 | GET | `/api/v1/platform/permission-catalog` | 平台菜单、操作权限目录。 |

系统字典接口：

| API ID | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- |
| DICT-001 | GET | `/api/v1/systems/{systemId}/dict/types` | Bearer | `DICT_VIEW` | 成员有系统字典查看权限 | 无。 |
| DICT-002 | POST | `/api/v1/systems/{systemId}/dict/types` | Bearer | `DICT_CREATE` | 系统启用，编码唯一 | 创建字典类型并刷新缓存版本。 |
| DICT-003 | PUT | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}` | Bearer | `DICT_EDIT` | 类型存在且非内置只读 | 更新类型基础字段并刷新缓存版本。 |
| DICT-004 | PATCH | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}/status` | Bearer | `DICT_STATUS` | 类型存在，状态流转合法 | 启用/停用类型，停用时同步影响运行表单可选项。 |
| DICT-005 | GET | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}/items` | Bearer | `DICT_VIEW` | 类型存在 | 返回树形或平铺字典项。 |
| DICT-006 | POST | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}/items` | Bearer | `DICT_ITEM_CREATE` | 类型启用，父级合法 | 创建字典项并刷新缓存版本。 |
| DICT-007 | PUT | `/api/v1/systems/{systemId}/dict/items/{dictItemId}` | Bearer | `DICT_ITEM_EDIT` | 字典项存在且非内置只读 | 更新字典项并刷新缓存版本。 |
| DICT-008 | PATCH | `/api/v1/systems/{systemId}/dict/items/{dictItemId}/status` | Bearer | `DICT_ITEM_STATUS` | 字典项存在，状态流转合法 | 启用/停用字典项，已使用值保留历史展示快照。 |
| DICT-009 | GET | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}/usages` | Bearer | `DICT_VIEW` | 类型存在 | 返回字段引用、记录使用和启停/删除影响。 |
| DICT-010 | DELETE | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}` | Bearer | `DICT_DELETE` | 类型未被字段引用且非内置只读 | 软删除字典类型和未引用字典项，刷新缓存版本。 |
| DICT-011 | DELETE | `/api/v1/systems/{systemId}/dict/items/{dictItemId}` | Bearer | `DICT_ITEM_DELETE` | 字典项未被记录值引用且无启用子项 | 软删除字典项，刷新缓存版本。 |

字典请求模型：

| 模型 | 字段 | 必填 | 前端可写 | 规则 |
| --- | --- | --- | --- | --- |
| `DictTypeQuery` | `scopeType`、`tenantId`、`keyword`、`status`、`pageNo`、`pageSize`、`sorter` | 否 | 是 | `scopeType` 为 `SYSTEM` 或 `TENANT`；`TENANT` 范围必须有 `tenantId` 或当前租户上下文。 |
| `DictTypeSaveBO` | `scopeType`、`tenantId`、`code`、`name`、`description`、`sortOrder`、`status` | `scopeType/code/name` 必填 | 是 | `code` 仅允许英文、数字、下划线；`status` 默认 `ENABLED`。 |
| `DictTypeUpdateBO` | `name`、`description`、`sortOrder`、`status`、`version` | `version` 必填 | 是 | 不允许修改 `scopeType`、`tenantId`、`code`；内置类型只允许启停可配置项。 |
| `DictStatusBO` | `targetStatus`、`reason`、`version` | `targetStatus/version` 必填 | 是 | `targetStatus` 为 `ENABLED`、`DISABLED`；停用前必须查询 `DICT-009`。 |
| `DictItemQuery` | `parentId`、`keyword`、`status`、`includeChildren`、`treeMode` | 否 | 是 | `treeMode=true` 返回树；`parentId` 为空查询根节点。 |
| `DictItemSaveBO` | `parentId`、`code`、`label`、`value`、`description`、`sortOrder`、`status`、`ext` | `code/label/value` 必填 | 是 | `parentId` 为空表示根项；`ext` 为可选 JSON，不能存敏感信息。 |
| `DictItemUpdateBO` | `parentId`、`label`、`value`、`description`、`sortOrder`、`status`、`ext`、`version` | `version` 必填 | 是 | 移动父级时必须重新计算层级路径，禁止形成环。 |
| `DictDeleteBO` | `force`、`reason`、`version` | `version` 必填 | 是 | MVP 中 `force` 固定为 `false`；存在引用时必须拒绝删除。 |

字典响应模型：

| 模型 | 字段 | 说明 |
| --- | --- | --- |
| `DictTypeVO` | `dictTypeId`、`systemId`、`scopeType`、`tenantId`、`code`、`name`、`description`、`status`、`sortOrder`、`systemBuiltIn`、`itemCount`、`enabledItemCount`、`referenced`、`cacheVersion`、`version`、`createdAt`、`updatedAt` | 字典类型列表、详情和保存响应共用。 |
| `DictItemVO` | `dictItemId`、`dictTypeId`、`parentId`、`code`、`label`、`value`、`description`、`status`、`sortOrder`、`depthLevel`、`depthPath`、`leaf`、`systemBuiltIn`、`referenced`、`cacheVersion`、`version`、`children`、`createdAt`、`updatedAt` | `children` 仅在 `treeMode=true` 时返回；`depthPath` 形如 `/rootId/childId`。 |
| `DictUsageVO` | `dictTypeId`、`dictItemId`、`fieldUsages`、`recordUsageCount`、`enabledChildrenCount`、`canDisable`、`canDelete`、`blockingReasons` | `fieldUsages[]` 包含 `moduleId`、`moduleCode`、`fieldId`、`fieldCode`、`publishedVersionId`、`status`。 |
| `DictCacheRefreshVO` | `dictTypeId`、`cacheVersion`、`refreshMode`、`refreshedAt`、`affectedKeys` | 写接口成功时放入 `data.cacheRefresh` 或 `meta.dictCache`，供测试断言缓存刷新。 |

字典唯一性与状态规则：

1. 字典类型唯一键为 `systemId + scopeType + tenantId + code + deletedFlag`；`scopeType=SYSTEM` 时 `tenantId` 为空，`scopeType=TENANT` 时 `tenantId` 必填。
2. 字典项唯一键为 `dictTypeId + parentId + code + deletedFlag` 和 `dictTypeId + parentId + value + deletedFlag`；同一父级下 `code` 与 `value` 均不可重复。
3. 字典类型和字典项状态为 `ENABLED`、`DISABLED`、`DELETED`；`DELETED` 为软删除终态，不出现在默认列表。
4. 排序按 `sortOrder ASC, code ASC, createdAt ASC`；同级排序不要求唯一。
5. 层级字典最大深度默认 5 级；超过返回 `DICT_DEPTH_EXCEEDED`；父级不存在返回 `DICT_PARENT_NOT_FOUND`，父级停用时禁止新增启用子项并返回 `DICT_PARENT_DISABLED`。
6. 停用字典类型前，若存在启用字典项或已发布字段引用，必须返回 `DICT_TYPE_IN_USE` 和 `DictUsageVO.blockingReasons`；停用字典项不改变历史记录展示，记录值继续使用保存时的 `valueSnapshot`。
7. 删除字典类型要求无字段引用、无记录引用且非内置类型；删除字典项要求无记录引用且无启用子项。未满足时返回 `DICT_TYPE_IN_USE`、`DICT_ITEM_IN_USE` 或 `DICT_HAS_ENABLED_CHILDREN`。
8. 运行表单和 OpenAPI 只允许选择 `ENABLED` 字典项；已停用项仅可用于历史详情展示和筛选回显，不可用于新写入。

缓存与刷新语义：

1. 字典缓存键按 `DICT:{systemId}:{scopeType}:{tenantId}:{dictTypeCode}:v{cacheVersion}` 组织；`tenantId` 为空时写 `system`。
2. `DICT-002` 至 `DICT-011` 成功后必须递增 `cacheVersion`，同步删除旧缓存或发布刷新事件；响应必须返回 `cacheVersion` 和 `DictCacheRefreshVO`。
3. 缓存刷新失败但数据库事务已提交时，接口返回 `DICT_CACHE_REFRESH_FAILED` 且 `retryable=true`；后端必须记录审计和待补偿刷新任务。
4. 前端 typed SDK 不直接拼缓存键，只消费 `cacheVersion`；测试可断言写操作后再次查询的 `cacheVersion` 增加。

字典错误码：

| 错误码 | HTTP | 触发条件 | retryable |
| --- | --- | --- | --- |
| `DICT_TYPE_NOT_FOUND` | 404 | 字典类型不存在、已删除或跨系统访问 | false |
| `DICT_ITEM_NOT_FOUND` | 404 | 字典项不存在、已删除或跨类型访问 | false |
| `DICT_TYPE_CODE_DUPLICATE` | 409 | 同作用域字典类型编码重复 | false |
| `DICT_ITEM_CODE_DUPLICATE` | 409 | 同父级字典项编码重复 | false |
| `DICT_ITEM_VALUE_DUPLICATE` | 409 | 同父级字典项值重复 | false |
| `DICT_PARENT_NOT_FOUND` | 404 | 指定父级不存在 | false |
| `DICT_PARENT_DISABLED` | 409 | 父级停用但新增/启用子项 | false |
| `DICT_DEPTH_EXCEEDED` | 400 | 层级超过最大深度 | false |
| `DICT_TYPE_IN_USE` | 409 | 类型被字段或记录引用，不能停用/删除 | false |
| `DICT_ITEM_IN_USE` | 409 | 字典项被记录值引用，不能删除 | false |
| `DICT_HAS_ENABLED_CHILDREN` | 409 | 字典项存在启用子项，不能删除 | false |
| `DICT_BUILTIN_READONLY` | 403 | 内置字典执行禁止操作 | false |
| `DICT_SCOPE_INVALID` | 400 | `scopeType` 与 `tenantId` 组合非法 | false |
| `DICT_STATUS_CONFLICT` | 409 | 状态流转不合法或版本冲突 | false |
| `DICT_CACHE_REFRESH_FAILED` | 500 | 缓存刷新失败 | true |

字典测试断言：

1. 创建同 `systemId + scopeType + tenantId + code` 的字典类型必须返回 `DICT_TYPE_CODE_DUPLICATE`。
2. 同一父级下重复 `code` 或 `value` 必须分别返回 `DICT_ITEM_CODE_DUPLICATE`、`DICT_ITEM_VALUE_DUPLICATE`。
3. 创建 6 级字典项必须返回 `DICT_DEPTH_EXCEEDED`。
4. 被已发布字段引用的字典类型，`DICT-009` 必须返回 `canDisable=false` 或 `canDelete=false` 及阻塞原因。
5. 写操作成功后，后续查询返回的 `cacheVersion` 必须大于写前版本。

RBAC 授权补充接口：

| API ID | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| RBAC-012 | GET | `/api/v1/systems/{systemId}/rbac/roles/{roleId}/permissions` | 读取角色已有菜单、操作、字段、数据范围、导出、流程、OpenAPI scope 授权。 |
| RBAC-013 | GET | `/api/v1/systems/{systemId}/rbac/permission-catalog` | 返回系统可授权对象目录：菜单树、操作码、模块字段、导出权限、流程动作、OpenAPI scope 和数据范围 schema。 |

关键 VO 字段：

| VO | 字段 |
| --- | --- |
| `FieldDefinitionVO` | `fieldId`、`fieldCode`、`fieldName`、`fieldType`、`required`、`unique`、`uniqueConstraints`、`options`、`dictTypeId`、`relationConfig`、`subTableConfig`、`serialConfig`、`defaultValue`、`status`、`version`、`fieldPermissions`。 |
| `RuntimeModuleSchemaVO` | `moduleId`、`moduleCode`、`publishedVersionId`、`listSchema`、`formSchema`、`detailSchema`、`fieldDefinitions`、`availableActions`、`permissionHints`、`statusRules`。 |
| `RecordListItemVO` | `recordId`、`recordNo`、`moduleId`、`title`、`recordStatus`、`flowStatus`、`values`、`availableActions`、`createdByName`、`updatedAt`、`recordVersion`。 |
| `RecordDetailVO` | `recordId`、`recordStatus`、`recordVersion`、`values`、`fileRefs`、`flowSummary`、`historySummary`、`availableActions`、`fieldPermissions`、`auditFields`。 |
| `RecordMutationResultVO` | `recordId`、`recordStatus`、`recordVersion`、`flowInstanceId`、`changedFields`、`idempotencyReplay`、`availableActions`。 |
| `FlowTaskDetailVO` | `taskId`、`taskVersion`、`instanceId`、`recordId`、`moduleId`、`nodeId`、`nodeName`、`recordSummary`、`formSchema`、`values`、`history`、`diagram`、`availableActions`。 |
| `FileInfoVO` | `fileId`、`fileName`、`extension`、`contentType`、`size`、`status`、`tempExpiresAt`、`previewable`、`previewableReason`、`downloadable`、`downloadableReason`、`references`。 |
| `ExportJobDetailVO` | `jobId`、`moduleId`、`templateId`、`status`、`progress`、`filterSnapshot`、`permissionSnapshot`、`resultFileId`、`failureReason`、`retryable`、`retryCount`、`maxRetryCount`、`pollingIntervalMs`。 |
| `OpenApiScope` | `scopeCode`、`moduleCode`、`actions`、`readableFieldCodes`、`writableFieldCodes`、`dataScope`、`filePermissions`、`flowPermissions`。 |
| `OpenApiAccessLogVO` | `logId`、`requestId`、`clientId`、`accessKey`、`systemId`、`tenantId`、`apiId`、`method`、`path`、`statusCode`、`errorCode`、`signatureResult`、`nonceResult`、`idempotencyResult`、`rateLimitResult`、`scopeResult`、`durationMs`、`createdAt`。 |
| `AuditLogDetailVO` | `logId`、`requestId`、`traceId`、`source`、`apiId`、`httpMethod`、`path`、`operatorType`、`operatorId`、`operatorName`、`bizType`、`bizId`、`beforeStatus`、`afterStatus`、`changedFields`、`beforeSnapshot`、`afterSnapshot`、`errorCode`、`durationMs`、`createdAt`。 |

运行台保存/提交语义冻结：`RUN-004` 仅保存草稿或新建记录，`RUN-006` 仅编辑保存，二者不再通过 `submitAfterSave` 隐式提交；保存并提交由前端先调用保存接口拿到 `recordId/recordVersion`，再调用 `RUN-008`。后端可提供聚合语义但必须等价于上述两步事务编排，并在契约变更评审后新增 API ID。`RUN-008` 成功后记录状态直接进入 `IN_APPROVAL`；未绑定流程但允许直接提交的模块才可进入 `SUBMITTED`，否则返回 `FLOW_BINDING_MISSING`。

#### 流程工作台补充接口

| API ID | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| FLOW-013 | GET | `/api/v1/systems/{systemId}/flow/workbench/cc` | 抄送列表，支持已读/未读筛选。 |
| FLOW-014 | GET | `/api/v1/systems/{systemId}/flow/workbench/started` | 我的申请列表。 |
| FLOW-015 | POST | `/api/v1/systems/{systemId}/flow/tasks/{taskId}/claim` | 领取任务，需 `taskVersion` 和幂等键。 |
| FLOW-016 | POST | `/api/v1/systems/{systemId}/flow/tasks/{taskId}/unclaim` | 取消领取，需 `taskVersion` 和幂等键。 |
| FLOW-017 | GET | `/api/v1/systems/{systemId}/flow/instances` | 实例列表。 |
| FLOW-018 | GET | `/api/v1/systems/{systemId}/flow/instances/{instanceId}/history` | 审批历史列表。 |
| FLOW-019 | GET | `/api/v1/systems/{systemId}/flow/templates/{templateId}` | 流程模板基础信息和发布版本摘要。 |
| FLOW-020 | GET | `/api/v1/systems/{systemId}/flow/templates/{templateId}/graph` | 读取流程模板结构化图。 |
| FLOW-021 | PATCH | `/api/v1/systems/{systemId}/flow/templates/{templateId}/status` | 启停流程模板，已发布且无运行冲突才允许。 |

`FlowActionBO` 字段：`action`、`comment`、`targetNodeId`、`returnStrategy`、`transferToMemberId`、`taskVersion`、`recordVersion`、`idempotencyKey`。`action=RETURN` 时 `targetNodeId` 或 `returnStrategy` 必填；`REJECT`、`WITHDRAW`、`TERMINATE` 必须填写 `comment`。

#### 文件、附件与导出补充规则

上传请求为 `multipart/form-data`，字段为 `file`、`bizType`、`moduleId`、`recordId`、`fieldCode`、`idempotencyKey`。业务保存前无 `recordId` 时允许上传为 `TEMP`，返回 `tempExpiresAt`。动态字段附件值统一为 `FileBindDTO[]`：`fileId`、`fieldCode`、`displayName`、`sortOrder`、`bizType`。记录保存事务内计算新增绑定和解绑；业务事务失败时文件保持 `TEMP` 并按过期清理，不删除已被其它对象引用的文件。导出结果文件下载复用 `FILE-005`，但必须额外校验导出任务创建人、导出权限快照或审计只读权限。

导出创建时 `selectedRecordIds` 优先于 `filters`；两者同时存在时后端保存两者快照但实际导出以 `selectedRecordIds` 为准。后台 runner 领取任务使用内部 service 边界，不暴露普通业务 API；领取必须基于任务状态和版本原子更新。失败原因结构为 `failureReason.code`、`message`、`retryable`、`stackSummary`、`failedAt`。

#### OpenAPI 签名、scope、限流与审计

OpenAPI 签名算法固定为 `HMAC-SHA256`。secret 明文只在创建或轮换响应中通过 `secretOnce` 返回一次，不落库；后续只返回 `maskedSecret` 和 `secretVisibleOnce=false`。`X-OpenApi-Signature` 的值固定为 HMAC 输出的 **小写 hex** 字符串，不使用 base64。

签名输入分为 `canonicalRequest` 和 `stringToSign`：

```text
canonicalRequest =
  HTTP_METHOD + "\n" +
  CANONICAL_PATH + "\n" +
  CANONICAL_QUERY + "\n" +
  BODY_SHA256_HEX + "\n" +
  CANONICAL_HEADERS + "\n" +
  SIGNED_HEADERS

stringToSign =
  "OPENAPI-HMAC-SHA256" + "\n" +
  X-OpenApi-Timestamp + "\n" +
  X-OpenApi-Nonce + "\n" +
  SHA256_HEX(canonicalRequest)

X-OpenApi-Signature = LOWER_HEX(HMAC_SHA256(secret, UTF8(stringToSign)))
```

规范化规则：

| 组成 | 规则 |
| --- | --- |
| `HTTP_METHOD` | 使用实际 HTTP method 的大写形式，例如 `GET`、`POST`、`PUT`。 |
| `CANONICAL_PATH` | 使用 URL path，不含 scheme、host、fragment；必须以 `/` 开头；按 RFC 3986 对 path segment 做 UTF-8 percent-encoding，`/` 不编码，不做尾斜杠自动补齐。 |
| `CANONICAL_QUERY` | 空 query 为零长度字符串；非空时将所有 query pair 以 UTF-8 RFC 3986 percent-encode 后按 `name`、`value` 升序排序，再用 `&` 拼接为 `name=value`；重复参数保留为多组 pair，空值写为 `name=`。 |
| `BODY_SHA256_HEX` | 对 HTTP 请求 body 原始字节计算 SHA-256 小写 hex；无 body 时使用空字节 hash `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`；JSON 请求按实际发送字节计算，不重新格式化为 canonical JSON。 |
| `CANONICAL_HEADERS` | 参与签名的 header 名统一小写、按字典序排序，值去除首尾空白，连续空白压缩为单空格，格式为 `name:value\n`。必须包含 `x-openapi-accesskey`、`x-openapi-body-sha256`、`x-openapi-nonce`、`x-openapi-timestamp`、`x-request-id`；`X-Request-Id` 未传时值为空字符串。 |
| `SIGNED_HEADERS` | 固定为 `x-openapi-accesskey;x-openapi-body-sha256;x-openapi-nonce;x-openapi-timestamp;x-request-id`。 |

`X-OpenApi-Body-Sha256` 必须等于 `BODY_SHA256_HEX`；不一致返回 `OPENAPI_BODY_HASH_MISMATCH`。签名校验顺序为 accessKey 存在 -> 客户端状态 -> IP 白名单 -> timestamp 窗口 -> nonce 唯一性 -> body hash -> signature -> scope -> rate limit -> 幂等。签名失败也必须写 OpenAPI 失败日志。

时间戳使用 Unix milliseconds 的 13 位十进制字符串，按 UTC 计算，允许偏差默认正负 300 秒；超窗返回 `OPENAPI_TIMESTAMP_EXPIRED`。`nonce` 唯一键为 `clientId + accessKey + nonce`，TTL 为 10 分钟且不得小于时间偏差窗口的 2 倍；TTL 内重复返回 `OPENAPI_NONCE_REPLAY`。

限流维度为 `clientId + systemId + tenantId + apiId + scopeCode + sourceIp`，策略包含 `windowSeconds`、`maxRequests`、`burst` 和 `effectiveFrom`；同一请求命中多条策略时取最严格结果。超限返回 HTTP 429、`OPENAPI_RATE_LIMITED`，响应 header 必须包含 `Retry-After`、`X-RateLimit-Limit`、`X-RateLimit-Remaining`、`X-RateLimit-Reset`，响应 body 的 `meta.rateLimit` 必须包含 `limit`、`remaining`、`resetAt`、`retryAfterSeconds`、`dimension`，且 `remaining=0`。

OpenAPI 签名错误码：

| 错误码 | HTTP | 触发条件 | retryable |
| --- | --- | --- | --- |
| `OPENAPI_ACCESS_KEY_INVALID` | 401 | accessKey 不存在或格式非法 | false |
| `OPENAPI_CLIENT_DISABLED` | 403 | 客户端停用、过期或不在授权系统/租户内 | false |
| `OPENAPI_IP_DENIED` | 403 | 来源 IP 不在白名单内 | false |
| `OPENAPI_TIMESTAMP_EXPIRED` | 401 | 时间戳格式非法或超出允许偏差 | false |
| `OPENAPI_NONCE_REPLAY` | 409 | nonce 在 TTL 内重复 | false |
| `OPENAPI_BODY_HASH_MISMATCH` | 400 | `X-OpenApi-Body-Sha256` 与实际 body hash 不一致 | false |
| `OPENAPI_SIGNATURE_INVALID` | 401 | canonical request 或 HMAC 签名不匹配 | false |
| `OPENAPI_SCOPE_DENIED` | 403 | scope 未授权 | false |
| `OPENAPI_RATE_LIMITED` | 429 | 命中限流 | true |

测试样例说明：

```text
method: POST
path: /openapi/v1/records
query: moduleCode=customer&tenantCode=default
body bytes: {"values":{"name":"Acme"}}
bodyHash: 37355baf144c16f301fd7de1030718002c1ce16dd130bc70f0d49c8e0ec0fc2c
headers:
  X-OpenApi-AccessKey: ak_test_001
  X-OpenApi-Timestamp: 1780713600000
  X-OpenApi-Nonce: nonce-001
  X-OpenApi-Body-Sha256: 37355baf144c16f301fd7de1030718002c1ce16dd130bc70f0d49c8e0ec0fc2c
  X-Request-Id: req_test_001
secret: test-secret
canonicalRequest:
POST
/openapi/v1/records
moduleCode=customer&tenantCode=default
37355baf144c16f301fd7de1030718002c1ce16dd130bc70f0d49c8e0ec0fc2c
x-openapi-accesskey:ak_test_001
x-openapi-body-sha256:37355baf144c16f301fd7de1030718002c1ce16dd130bc70f0d49c8e0ec0fc2c
x-openapi-nonce:nonce-001
x-openapi-timestamp:1780713600000
x-request-id:req_test_001

x-openapi-accesskey;x-openapi-body-sha256;x-openapi-nonce;x-openapi-timestamp;x-request-id
canonicalRequestSha256: 1d0d4b4fbabedb2acbf1a47dcfdbd52718fe11ae1660db69418d47b2347ebb48
expectedSignature: 8b2b2dc7598b655e0c595d22a90413c983974379cd4902c4038d26c31fb870ff
```

自动化测试必须分别覆盖：空 query、重复 query、空 body、JSON body 原始字节变化、header 大小写变化、timestamp 超窗、nonce 重放、body hash 不匹配、签名不匹配、scope 越界和限流响应 header/body 字段。

OpenAPI 外部记录接口必须显式提供模块上下文：`moduleCode` 为必填 query/body 字段；如果后续改为路径 `/openapi/v1/modules/{moduleCode}/records`，必须重新评审。scope 必须细化到模块、动作、字段读写、数据范围、流程动作、文件下载，不允许仅保存字符串 scope。

OpenAPI 管理补充接口：

| API ID | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| OPM-009 | GET | `/api/v1/systems/{systemId}/openapi/scope-catalog` | 返回可授权模块、动作、字段读写、流程动作、文件下载和数据范围目录。 |

审计/运维补充接口：

| API ID | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| AUD-007 | GET | `/api/v1/systems/{systemId}/audit/logs/{logId}` | 返回系统内审计日志详情，包含前后快照、状态变化、错误摘要和耗时。 |
| AUD-008 | GET | `/api/v1/platform/audit/logs/{logId}` | 返回平台审计日志详情。 |
| OPS-006 | GET | `/api/v1/ops/health/components` | 返回组件级健康检查结果，包含数据库、Redis、文件存储、密钥、migration 和 OpenAPI 策略检查项。 |

## 接口总览

| 模块 | 阶段 | 路径前缀 | 核心场景 |
| --- | --- | --- | --- |
| 认证/会话 | MVP | `/api/v1/auth` | 登录、刷新 token、退出、当前用户。 |
| 平台中心 | MVP | `/api/v1/platform` | 平台账号、平台角色、我的系统、创建系统、平台配置。 |
| 租户/自定义系统 | MVP | `/api/v1/systems/{systemId}` | 系统基础信息、租户上下文、系统启停、进入系统。 |
| 系统成员扩展 | MVP | `/api/v1/systems/{systemId}/members` | 平台用户在系统内的成员扩展、邀请、启停。 |
| 系统内组织角色权限 | MVP | `/api/v1/systems/{systemId}/rbac` | 部门、角色、菜单、操作、字段、数据范围授权。 |
| 应用配置 | MVP | `/api/v1/systems/{systemId}/apps` | 应用创建、编辑、启停、归档、版本入口。 |
| 模块建模 | MVP | `/api/v1/systems/{systemId}/apps/{appId}/modules` | 模块、字段、字典、自动编号、发布检查。 |
| 字段/视图/页面配置 | MVP | `/api/v1/systems/{systemId}/modules/{moduleId}/ui` | 列表、表单、详情、菜单、按钮、字段权限配置。 |
| 运行填报 | MVP | `/api/v1/systems/{systemId}/runtime` | 运行菜单、schema、记录查询、新增、编辑、删除、提交审批。 |
| 流程审批 | MVP | `/api/v1/systems/{systemId}/flow` | 流程模板、发布、绑定、待办、审批动作、历史。 |
| 上传文件 | MVP | `/api/v1/systems/{systemId}/files` | 上传、预览、下载、引用、删除。 |
| 导入导出 | MVP/PLACEHOLDER | `/api/v1/systems/{systemId}/exports`、`/imports` | 导出模板与任务闭环；导入只保留占位。 |
| OpenAPI 管理 | MVP | `/api/v1/systems/{systemId}/openapi` | 客户端、凭证、scope、白名单、调用日志。 |
| 外部 OpenAPI | MVP | `/openapi/v1` | 外部记录、流程、文件能力。 |
| 审计日志 | MVP | `/api/v1/systems/{systemId}/audit` | 操作日志、请求日志、错误日志、业务变更。 |
| 运维配置 | MVP/ENH | `/api/v1/ops` | 健康检查、配置检查、版本信息、迁移状态。 |
| 代码生成器管理 | PLACEHOLDER/INTERNAL | `/api/v1/ops/generator` | 代码生成边界、任务和报告占位。 |

## 认证/会话

场景：平台用户是唯一登录主体。登录成功后用户进入“我的系统”，进入具体系统时再转换为系统成员上下文。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AUTH-001 | MVP | POST | `/api/v1/auth/register` | 无 | 无 | 平台允许注册 | 创建平台账号，状态 `NORMAL` 或待激活。 |
| AUTH-002 | MVP | POST | `/api/v1/auth/login` | 无 | 无 | 账号存在且未停用/锁定 | 写登录日志，返回 token。 |
| AUTH-003 | MVP | POST | `/api/v1/auth/refresh` | refreshToken | 无 | refreshToken 有效 | 续签 accessToken。 |
| AUTH-004 | MVP | POST | `/api/v1/auth/logout` | Bearer | 无 | 会话有效 | 当前会话失效。 |
| AUTH-005 | MVP | GET | `/api/v1/auth/me` | Bearer | 无 | 会话有效 | 无状态变化。 |
| AUTH-006 | ENH | POST | `/api/v1/auth/password/reset` | 无/验证码 | 无 | 找回密码策略启用 | 修改密码并使旧会话失效。 |

关键入参：

| 接口 | 字段 | 类型 | 必填 | 校验 |
| --- | --- | --- | --- | --- |
| AUTH-001 | loginName、password、displayName、mobile、email | string | loginName/password 必填 | loginName 全局唯一，密码满足平台策略。 |
| AUTH-002 | loginName、password、captchaToken | string | loginName/password 必填 | 连续失败触发限制。 |
| AUTH-003 | refreshToken | string | 是 | 未过期、未吊销。 |

关键出参：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| accountId | string | 平台账号 ID。 |
| loginName | string | 登录名。 |
| displayName | string | 展示名。 |
| accessToken | string | 内部 API 访问 token。 |
| refreshToken | string | 刷新 token。 |
| expiresIn | integer | accessToken 有效秒数。 |
| platformRoles | array | 平台角色编码。 |

错误码：`AUTH_INVALID_CREDENTIAL`、`AUTH_ACCOUNT_DISABLED`、`AUTH_ACCOUNT_LOCKED`、`AUTH_TOKEN_EXPIRED`、`AUTH_REFRESH_INVALID`、`PLAT_ACCOUNT_DUPLICATED`、`COMMON_PARAM_INVALID`。

## 平台中心

场景：平台超级管理员管理平台账号、平台角色、平台菜单、全局配置和系统；平台用户查看和创建自己的系统。平台超级管理员默认不绕过系统内权限直接修改业务数据。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PLAT-001 | MVP | GET | `/api/v1/platform/my-systems` | Bearer | 登录用户 | 用户已登录 | 无。 |
| PLAT-002 | MVP | POST | `/api/v1/platform/systems` | Bearer | `PLAT_SYSTEM_CREATE` | 账号正常 | 创建系统、默认租户、创建人成员扩展、系统超级管理员角色、默认菜单和默认应用，事务内完成。 |
| PLAT-003 | MVP | GET | `/api/v1/platform/systems` | Bearer | `PLAT_SYSTEM_VIEW` | 平台管理权限 | 无。 |
| PLAT-004 | MVP | GET | `/api/v1/platform/systems/{systemId}` | Bearer | `PLAT_SYSTEM_VIEW` 或系统成员 | 系统存在 | 无。 |
| PLAT-005 | MVP | PATCH | `/api/v1/platform/systems/{systemId}/status` | Bearer | `PLAT_SYSTEM_STATUS` | 系统未归档或满足状态规则 | 变更系统状态。 |
| PLAT-006 | MVP | GET | `/api/v1/platform/accounts` | Bearer | `PLAT_ACCOUNT_VIEW` | 平台管理权限 | 无。 |
| PLAT-007 | MVP | POST | `/api/v1/platform/accounts` | Bearer | `PLAT_ACCOUNT_CREATE` | loginName 未重复 | 创建平台账号。 |
| PLAT-008 | MVP | PATCH | `/api/v1/platform/accounts/{accountId}/status` | Bearer | `PLAT_ACCOUNT_STATUS` | 账号存在 | 正常/停用/锁定状态变更。 |
| PLAT-009 | MVP | GET | `/api/v1/platform/roles` | Bearer | `PLAT_ROLE_VIEW` | 平台管理权限 | 无。 |
| PLAT-010 | MVP | PUT | `/api/v1/platform/roles/{roleId}/menus` | Bearer | `PLAT_ROLE_AUTH` | 角色和菜单存在 | 更新平台角色菜单权限。 |
| PLAT-011 | MVP | GET | `/api/v1/platform/configs` | Bearer | `PLAT_CONFIG_VIEW` | 平台管理权限 | 无。 |
| PLAT-012 | MVP | PUT | `/api/v1/platform/configs/{configKey}` | Bearer | `PLAT_CONFIG_EDIT` | 配置 key 已定义 | 更新平台配置，写审计。 |

关键入参：

| 接口 | 字段 | 类型 | 必填 | 前端可写 | 说明 |
| --- | --- | --- | --- | --- | --- |
| PLAT-002 | name、code、tenantMode、description | string | name/code/tenantMode 是 | 是 | 创建系统；`tenantMode` 为 `SINGLE` 或 `MULTI`。 |
| PLAT-005 | status、reason | string | status 是 | 是 | 状态只能按矩阵流转。 |
| PLAT-007 | loginName、displayName、mobile、email、initialPassword | string | loginName 是 | 是 | 密码策略由后端校验。 |
| PLAT-010 | menuIds、operationCodes | array | 是 | 是 | 平台菜单和平台操作权限。 |
| PLAT-012 | value、remark | object/string | value 是 | 是 | 敏感值由后端脱敏返回。 |

关键出参：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| systemId、systemCode、systemName | string | 系统标识。 |
| tenantMode | string | `SINGLE`、`MULTI`。 |
| ownerAccountId | string | 创建人平台账号。 |
| ownerMemberId | string | 创建人在系统内的成员扩展。 |
| status | string | 系统或账号状态。 |
| platformPermissions | array | 平台权限编码。 |

错误码：`PLAT_SYSTEM_CODE_DUPLICATED`、`PLAT_SYSTEM_INIT_FAILED`、`PLAT_ACCOUNT_NOT_FOUND`、`PLAT_ACCOUNT_STATUS_INVALID`、`PLAT_CONFIG_SENSITIVE_VALUE_FORBIDDEN`、`PERM_DENIED`。

数据落点和事务边界：PLAT-002 影响平台系统、默认租户、成员扩展、系统角色、系统菜单和默认应用等领域对象，必须单事务提交；初始化任一步失败整体回滚，不允许出现系统已创建但创建人没有系统超级管理员权限。

## 租户/自定义系统

场景：系统创建人进入系统后成为系统超级管理员；单租户系统仍有默认租户上下文，多租户系统需要显式租户切换。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SYS-001 | MVP | POST | `/api/v1/systems/{systemId}/enter` | Bearer | 系统成员 | 系统启用或管理员进入停用系统设置 | 建立系统成员上下文。 |
| SYS-002 | MVP | GET | `/api/v1/systems/{systemId}/profile` | Bearer | `SYS_PROFILE_VIEW` | 系统存在且有成员上下文 | 无。 |
| SYS-003 | MVP | PUT | `/api/v1/systems/{systemId}/profile` | Bearer | `SYS_PROFILE_EDIT` | 系统未归档 | 更新系统基础信息。 |
| SYS-004 | MVP | GET | `/api/v1/systems/{systemId}/tenants` | Bearer | `SYS_TENANT_VIEW` | 系统为多租户或管理员 | 无。 |
| SYS-005 | MVP | POST | `/api/v1/systems/{systemId}/tenants` | Bearer | `SYS_TENANT_CREATE` | 多租户模式 | 创建租户。 |
| SYS-006 | MVP | PATCH | `/api/v1/systems/{systemId}/tenants/{tenantId}/status` | Bearer | `SYS_TENANT_STATUS` | 租户存在 | 启用/停用租户。 |
| SYS-007 | MVP | POST | `/api/v1/systems/{systemId}/tenant-context/switch` | Bearer | 系统成员 | 成员属于目标租户或有管理范围 | 切换当前租户上下文。 |

关键入参：`name`、`code`、`domain`、`tenantMode`、`defaultTenantId`、`tenantId`、`status`、`reason`。`systemId`、`tenantId` 必须由路径或后端上下文确定，前端不得在业务对象中伪造。

关键出参：`systemId`、`tenantId`、`tenantCode`、`tenantName`、`tenantMode`、`memberId`、`memberRoles`、`systemPermissions`、`runtimeHomePage`。

错误码：`SYS_NOT_FOUND`、`SYS_DISABLED`、`SYS_ARCHIVED`、`SYS_TENANT_CODE_DUPLICATED`、`SYS_TENANT_DISABLED`、`SYS_CONTEXT_REQUIRED`、`PERM_DENIED`。

## 系统成员扩展

场景：系统内成员不是独立账号，而是平台用户在系统内的扩展。成员承载部门、岗位、角色、状态、租户和数据范围。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MEM-001 | MVP | GET | `/api/v1/systems/{systemId}/members` | Bearer | `SYS_MEMBER_VIEW` | 系统成员上下文 | 无。 |
| MEM-002 | MVP | POST | `/api/v1/systems/{systemId}/members/invitations` | Bearer | `SYS_MEMBER_INVITE` | 平台账号存在或允许邀请 | 创建或绑定系统成员扩展。 |
| MEM-003 | MVP | GET | `/api/v1/systems/{systemId}/members/{memberId}` | Bearer | `SYS_MEMBER_VIEW` | 成员存在 | 无。 |
| MEM-004 | MVP | PUT | `/api/v1/systems/{systemId}/members/{memberId}` | Bearer | `SYS_MEMBER_EDIT` | 成员未删除 | 更新成员部门、岗位、租户归属。 |
| MEM-005 | MVP | PATCH | `/api/v1/systems/{systemId}/members/{memberId}/status` | Bearer | `SYS_MEMBER_STATUS` | 成员存在 | 启用/停用成员。 |
| MEM-006 | MVP | PUT | `/api/v1/systems/{systemId}/members/{memberId}/roles` | Bearer | `SYS_ROLE_ASSIGN` | 角色启用 | 更新成员角色。 |
| MEM-007 | MVP | GET | `/api/v1/systems/{systemId}/members/current` | Bearer | 系统成员 | 已进入系统 | 无。 |

关键入参：`accountId`、`loginName`、`tenantIds`、`deptIds`、`postName`、`roleIds`、`status`。同一 `systemId + accountId` 只能有一个成员扩展。

关键出参：`memberId`、`accountId`、`loginName`、`displayName`、`tenantIds`、`deptPath`、`roles`、`dataScopeSummary`、`status`。

错误码：`SYS_MEMBER_ACCOUNT_REQUIRED`、`SYS_MEMBER_DUPLICATED`、`SYS_MEMBER_DISABLED`、`SYS_MEMBER_NOT_FOUND`、`SYS_ROLE_DISABLED`、`PERM_DENIED`。

## 系统内组织角色权限

场景：系统内组织、角色、菜单、操作、字段和数据范围权限由系统内部维护。前端权限只用于体验过滤，后端必须重新校验。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| RBAC-001 | MVP | GET | `/api/v1/systems/{systemId}/rbac/departments/tree` | Bearer | `SYS_DEPT_VIEW` | 系统上下文 | 无。 |
| RBAC-002 | MVP | POST | `/api/v1/systems/{systemId}/rbac/departments` | Bearer | `SYS_DEPT_CREATE` | 父部门存在或根部门 | 创建部门。 |
| RBAC-003 | MVP | PUT | `/api/v1/systems/{systemId}/rbac/departments/{deptId}` | Bearer | `SYS_DEPT_EDIT` | 部门存在 | 更新部门。 |
| RBAC-004 | MVP | DELETE | `/api/v1/systems/{systemId}/rbac/departments/{deptId}` | Bearer | `SYS_DEPT_DELETE` | 无成员或子部门限制通过 | 软删除部门。 |
| RBAC-005 | MVP | GET | `/api/v1/systems/{systemId}/rbac/roles` | Bearer | `SYS_ROLE_VIEW` | 系统上下文 | 无。 |
| RBAC-006 | MVP | POST | `/api/v1/systems/{systemId}/rbac/roles` | Bearer | `SYS_ROLE_CREATE` | code 未重复 | 创建角色。 |
| RBAC-007 | MVP | PUT | `/api/v1/systems/{systemId}/rbac/roles/{roleId}` | Bearer | `SYS_ROLE_EDIT` | 角色存在 | 更新角色。 |
| RBAC-008 | MVP | PATCH | `/api/v1/systems/{systemId}/rbac/roles/{roleId}/status` | Bearer | `SYS_ROLE_STATUS` | 角色存在 | 启用/停用角色。 |
| RBAC-009 | MVP | PUT | `/api/v1/systems/{systemId}/rbac/roles/{roleId}/permissions` | Bearer | `SYS_ROLE_PERMISSION_EDIT` | 权限对象存在 | 更新菜单、操作、字段、数据范围、导出和流程权限。 |
| RBAC-010 | MVP | GET | `/api/v1/systems/{systemId}/rbac/effective-permissions` | Bearer | 系统成员 | 成员已启用 | 返回当前成员有效权限。 |
| RBAC-011 | MVP | GET | `/api/v1/systems/{systemId}/rbac/runtime-menus` | Bearer | 系统成员 | 已发布菜单 | 返回运行菜单树。 |

权限保存入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| menuIds | array | 否 | 菜单可见权限。 |
| operationCodes | array | 否 | 新增、编辑、删除、提交、导出、审批等操作权限。 |
| fieldPermissions | array | 否 | `fieldCode`、`visible`、`writable`、`exportPlain`、`openApiReadable`。 |
| dataScope | object | 否 | `SELF`、`DEPT`、`DEPT_TREE`、`ALL` 或结构化条件。 |
| explicitDeny | array | 否 | 显式禁用项，优先级高于授权并集。 |

错误码：`PERM_DENIED`、`PERM_FIELD_WRITE_DENIED`、`PERM_DATA_SCOPE_DENIED`、`SYS_DEPT_HAS_MEMBER`、`SYS_ROLE_CODE_DUPLICATED`、`SYS_ROLE_PROTECTED`。

## 应用配置

场景：应用配置管理员在系统内创建业务应用，应用归属系统和租户上下文。业务应用和动态模块归入动态模块域，OpenAPI 不与业务应用混用。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| APP-001 | MVP | GET | `/api/v1/systems/{systemId}/apps` | Bearer | `APP_VIEW` | 系统上下文 | 无。 |
| APP-002 | MVP | POST | `/api/v1/systems/{systemId}/apps` | Bearer | `APP_CREATE` | 系统启用，租户启用 | 创建应用草稿。 |
| APP-003 | MVP | GET | `/api/v1/systems/{systemId}/apps/{appId}` | Bearer | `APP_VIEW` | 应用存在 | 无。 |
| APP-004 | MVP | PUT | `/api/v1/systems/{systemId}/apps/{appId}` | Bearer | `APP_EDIT` | 应用未归档 | 更新应用。 |
| APP-005 | MVP | PATCH | `/api/v1/systems/{systemId}/apps/{appId}/status` | Bearer | `APP_STATUS` | 状态流转合法 | 启用/停用/归档。 |
| APP-006 | ENH | POST | `/api/v1/systems/{systemId}/apps/{appId}/copy` | Bearer | `APP_COPY` | 应用存在 | 复制应用配置草稿。 |
| APP-007 | ENH | GET | `/api/v1/systems/{systemId}/apps/templates` | Bearer | `APP_TEMPLATE_VIEW` | 模板能力启用 | 无。 |

关键入参：`name`、`code`、`icon`、`description`、`tenantId`、`sortOrder`、`status`。`code` 在 `systemId + tenantId` 下唯一。

关键出参：`appId`、`systemId`、`tenantId`、`name`、`code`、`status`、`moduleCount`、`publishedVersion`、`updatedAt`。

错误码：`MODULE_APP_CODE_DUPLICATED`、`MODULE_APP_NOT_FOUND`、`MODULE_APP_STATUS_INVALID`、`SYS_TENANT_DISABLED`、`PERM_DENIED`。

## 模块建模

场景：应用配置管理员创建模块、字段、字典、自动编号和关联关系，发布后运行台只读取发布版本。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOD-001 | MVP | GET | `/api/v1/systems/{systemId}/apps/{appId}/modules` | Bearer | `MODULE_VIEW` | 应用存在 | 无。 |
| MOD-002 | MVP | POST | `/api/v1/systems/{systemId}/apps/{appId}/modules` | Bearer | `MODULE_CREATE` | 应用未归档 | 创建模块草稿。 |
| MOD-003 | MVP | GET | `/api/v1/systems/{systemId}/modules/{moduleId}` | Bearer | `MODULE_VIEW` | 模块存在 | 无。 |
| MOD-004 | MVP | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}` | Bearer | `MODULE_EDIT` | 模块未归档 | 更新模块基础信息。 |
| MOD-005 | MVP | PATCH | `/api/v1/systems/{systemId}/modules/{moduleId}/status` | Bearer | `MODULE_STATUS` | 状态流转合法 | 停用/归档模块。 |
| FIELD-001 | MVP | GET | `/api/v1/systems/{systemId}/modules/{moduleId}/fields` | Bearer | `FIELD_VIEW` | 模块存在 | 无。 |
| FIELD-002 | MVP | POST | `/api/v1/systems/{systemId}/modules/{moduleId}/fields` | Bearer | `FIELD_CREATE` | 字段编码未重复 | 创建字段草稿。 |
| FIELD-003 | MVP | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/fields/{fieldId}` | Bearer | `FIELD_EDIT` | 字段未删除 | 更新字段。 |
| FIELD-004 | MVP | PATCH | `/api/v1/systems/{systemId}/modules/{moduleId}/fields/{fieldId}/status` | Bearer | `FIELD_STATUS` | 状态合法 | 启用/停用/删除标记。 |
| FIELD-005 | MVP | GET | `/api/v1/systems/{systemId}/field-types` | Bearer | `FIELD_TYPE_VIEW` | 系统上下文 | 返回可用字段类型。 |
| MOD-006 | MVP | POST | `/api/v1/systems/{systemId}/modules/{moduleId}/publish-check` | Bearer | `MODULE_PUBLISH` | 模块存在 | 返回发布检查结果，不生成版本。 |
| MOD-007 | MVP | POST | `/api/v1/systems/{systemId}/modules/{moduleId}/publish` | Bearer | `MODULE_PUBLISH` | 检查通过 | 生成发布版本，运行态可见。 |

模块关键字段：`moduleId`、`appId`、`name`、`code`、`description`、`status`、`versionStatus`、`flowBindingId`。

字段关键入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 字段中文名。 |
| code | string | 是 | 英文、数字、下划线，同模块唯一。 |
| fieldType | string | 是 | 字段类型枚举。 |
| required | boolean | 否 | 是否必填。 |
| unique | boolean | 否 | 是否唯一，DB/API 后续细化软删除复用规则。 |
| defaultValue | any | 否 | 默认值。 |
| options | array | 否 | 单选/多选选项。 |
| relationConfig | object | 否 | 关联模块、展示字段、数据范围。 |
| subTableConfig | object | 否 | 子表列定义。 |
| serialConfig | object | 否 | 自动编号规则。 |

错误码：`MODULE_CODE_DUPLICATED`、`MODULE_NOT_FOUND`、`MODULE_PUBLISH_CHECK_FAILED`、`FIELD_CODE_DUPLICATED`、`FIELD_TYPE_UNSUPPORTED`、`FIELD_DELETE_HAS_DATA`、`FIELD_RELATION_INVALID`、`FIELD_SERIAL_RULE_INVALID`。

数据落点：配置对象落动态模块配置域；发布生成配置版本和运行快照。API 不生成 DB 表，但 DBA 后续应按 `un_module_` 前缀设计业务应用、应用版本、模块、字段、页面、权限、记录、导出等对象。`un_app_` 不进入 MVP 建表范围，也不得用于 OpenAPI；OpenAPI 表域固定为 `un_openapi_`。

## 字段/视图/页面配置

场景：配置管理员维护列表、表单、详情、菜单、按钮和字段权限，发布后运行台按版本渲染。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UI-001 | MVP | GET | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/list-views` | Bearer | `PAGE_VIEW` | 模块存在 | 无。 |
| UI-002 | MVP | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/list-views/default` | Bearer | `PAGE_EDIT` | 字段存在 | 保存列表视图草稿。 |
| UI-003 | MVP | GET | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/forms/default` | Bearer | `PAGE_VIEW` | 模块存在 | 无。 |
| UI-004 | MVP | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/forms/default` | Bearer | `PAGE_EDIT` | 字段存在 | 保存表单草稿。 |
| UI-005 | MVP | GET | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/details/default` | Bearer | `PAGE_VIEW` | 模块存在 | 无。 |
| UI-006 | MVP | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/details/default` | Bearer | `PAGE_EDIT` | 字段存在 | 保存详情草稿。 |
| UI-007 | MVP | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/menu` | Bearer | `MENU_EDIT` | 模块存在 | 保存运行菜单配置。 |
| UI-008 | MVP | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/actions` | Bearer | `ACTION_EDIT` | 模块存在 | 保存按钮动作配置。 |
| UI-009 | ENH | POST | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/import` | Bearer | `PAGE_IMPORT` | 配置导入能力启用 | 导入配置草稿。 |

关键入参：`columns`、`filters`、`sorters`、`formSections`、`detailBlocks`、`menuParentId`、`actionCodes`、`fieldVisibility`、`fieldWritable`。所有字段引用必须使用当前模块字段 ID 或字段编码。

关键出参：`schemaVersion`、`draftVersion`、`publishedVersion`、`listSchema`、`formSchema`、`detailSchema`、`permissionHints`。

错误码：`MODULE_PAGE_FIELD_MISSING`、`MODULE_MENU_CODE_DUPLICATED`、`PERM_FIELD_WRITE_DENIED`、`MODULE_CONFIG_VERSION_CONFLICT`。

## 运行填报

场景：业务用户在运行台只看到有权限菜单和动作，按发布版本填写业务记录。新增、编辑、删除、提交审批均需校验菜单、操作、字段、数据范围和记录状态。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| RUN-001 | MVP | GET | `/api/v1/systems/{systemId}/runtime/menus` | Bearer | 系统成员 | 系统/租户启用 | 无。 |
| RUN-002 | MVP | GET | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/schema` | Bearer | 菜单可见 | 模块已发布 | 无。 |
| RUN-003 | MVP | POST | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/query` | Bearer | `RECORD_VIEW` | 模块已发布 | 无。 |
| RUN-004 | MVP | POST | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records` | Bearer | `RECORD_CREATE` | 字段校验通过 | 创建记录并保存为 `DRAFT`。 |
| RUN-005 | MVP | GET | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}` | Bearer | `RECORD_VIEW` + 数据范围 | 记录存在 | 无。 |
| RUN-006 | MVP | PUT | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}` | Bearer | `RECORD_EDIT` + 字段写权限 | 记录未删除且未被流程锁定 | 更新记录、字段值、历史和附件引用。 |
| RUN-007 | MVP | DELETE | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}` | Bearer | `RECORD_DELETE` | 状态允许删除 | 软删除记录。 |
| RUN-008 | MVP | POST | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/submit` | Bearer | `RECORD_SUBMIT` | 记录校验通过，流程绑定合法 | 提交记录并按绑定流程创建审批实例。 |
| RUN-009 | MVP | GET | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/history` | Bearer | `RECORD_HISTORY_VIEW` | 记录存在且有数据范围 | 无。 |
| RUN-010 | MVP | GET | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/relations` | Bearer | `RECORD_VIEW` | 关联字段存在 | 无。 |

新增/编辑入参：

| 字段 | 类型 | 必填 | 前端可写 | 说明 |
| --- | --- | --- | --- | --- |
| values | array | 是 | 是 | 动态字段值数组。 |
| recordVersion | integer | 更新时是 | 是 | 乐观锁版本；新增时不传，编辑和提交前必须使用详情或保存响应中的最新版本。 |
| idempotencyKey | string | 是 | 是 | 防重复提交；也可通过 `X-Idempotency-Key` 传入。 |
| remark | string | 否 | 是 | 保存备注；提交审批原因由 RUN-008/FLOW 动作入参传入。 |

详情响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| recordId | string | 记录 ID。 |
| status | string | 记录状态。 |
| values | array | 动态字段值，已按字段权限过滤。 |
| actions | array | 当前用户可执行动作。 |
| flowSummary | object | 流程实例摘要。 |
| fileRefs | array | 附件引用摘要。 |
| createdBy、updatedBy | object | 展示用成员快照。 |

错误码：`MODULE_RECORD_NOT_FOUND`、`MODULE_RECORD_STATUS_CONFLICT`、`FIELD_REQUIRED_MISSING`、`FIELD_VALUE_TYPE_INVALID`、`FIELD_UNIQUE_CONFLICT`、`PERM_FIELD_WRITE_DENIED`、`PERM_DATA_SCOPE_DENIED`、`FLOW_BINDING_MISSING`、`COMMON_IDEMPOTENCY_CONFLICT`。

事务边界：RUN-004/RUN-006 必须在同一事务内处理主记录、字段值、索引值、关联/子表、历史、附件引用校验、自动编号和审计。RUN-008 在本地记录校验和文件引用完成后创建流程实例；流程创建失败则记录提交整体失败并返回 requestId。

## 流程审批

场景：系统管理员配置流程模板和发布版本，运行台提交记录触发流程；审批人在流程工作台处理待办。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FLOW-001 | MVP | GET | `/api/v1/systems/{systemId}/flow/templates` | Bearer | `FLOW_TEMPLATE_VIEW` | 系统上下文 | 无。 |
| FLOW-002 | MVP | POST | `/api/v1/systems/{systemId}/flow/templates` | Bearer | `FLOW_TEMPLATE_CREATE` | code 未重复 | 创建流程模板草稿。 |
| FLOW-003 | MVP | PUT | `/api/v1/systems/{systemId}/flow/templates/{templateId}/graph` | Bearer | `FLOW_TEMPLATE_EDIT` | 模板草稿 | 保存结构化节点、连线、条件、审批人。 |
| FLOW-004 | MVP | POST | `/api/v1/systems/{systemId}/flow/templates/{templateId}/publish-check` | Bearer | `FLOW_TEMPLATE_PUBLISH` | 模板存在 | 返回发布检查结果。 |
| FLOW-005 | MVP | POST | `/api/v1/systems/{systemId}/flow/templates/{templateId}/publish` | Bearer | `FLOW_TEMPLATE_PUBLISH` | 检查通过 | 生成流程发布版本。 |
| FLOW-006 | MVP | PUT | `/api/v1/systems/{systemId}/flow/bindings/modules/{moduleId}` | Bearer | `FLOW_BINDING_EDIT` | 模块和流程版本存在 | 绑定模块提交动作到流程版本。 |
| FLOW-007 | MVP | GET | `/api/v1/systems/{systemId}/flow/tasks/todo` | Bearer | 审批人 | 有待办范围 | 无。 |
| FLOW-008 | MVP | GET | `/api/v1/systems/{systemId}/flow/tasks/{taskId}` | Bearer | 任务候选人/处理人 | 任务存在 | 无。 |
| FLOW-009 | MVP | POST | `/api/v1/systems/{systemId}/flow/tasks/{taskId}/actions` | Bearer | `FLOW_TASK_HANDLE` | 任务待处理 | 同意/拒绝/转交/退回/终止，推进实例和业务状态。 |
| FLOW-010 | MVP | POST | `/api/v1/systems/{systemId}/flow/instances/{instanceId}/withdraw` | Bearer | 发起人或授权人 | 实例审批中且允许撤回 | 撤回实例和业务状态。 |
| FLOW-011 | MVP | GET | `/api/v1/systems/{systemId}/flow/instances/{instanceId}` | Bearer | `FLOW_INSTANCE_VIEW` + 数据范围 | 实例存在 | 无。 |
| FLOW-012 | MVP | GET | `/api/v1/systems/{systemId}/flow/instances/{instanceId}/diagram` | Bearer | `FLOW_INSTANCE_VIEW` | 实例存在 | 无。 |

任务动作入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| action | string | 是 | `APPROVE`、`REJECT`、`TRANSFER`、`RETURN`、`TERMINATE`。 |
| comment | string | 拒绝/退回/终止必填 | 审批意见。 |
| targetMemberId | string | 转交必填 | 目标审批人。 |
| idempotencyKey | string | 是 | 防重复处理。 |

关键响应：`instanceId`、`taskId`、`instanceStatus`、`taskStatus`、`currentNode`、`nextNodes`、`businessRecordStatus`、`history`。

错误码：`FLOW_TEMPLATE_CHECK_FAILED`、`FLOW_TEMPLATE_NOT_PUBLISHED`、`FLOW_TASK_NOT_FOUND`、`FLOW_TASK_ALREADY_HANDLED`、`FLOW_TASK_ACTOR_INVALID`、`FLOW_ACTION_REASON_REQUIRED`、`FLOW_INSTANCE_STATUS_CONFLICT`、`COMMON_IDEMPOTENCY_CONFLICT`。

事务边界：任务处理必须对流程任务使用幂等和并发防重复；流程实例、任务、审批日志、业务记录状态联动和审计在同一业务事务内完成。外部通知失败不得破坏已完成审批事务，但必须记录可追踪日志。

## 上传文件

场景：文件属于系统/租户上下文，可被动态字段、导出任务、评论或后续模板引用。预览和下载必须跟随业务对象权限和文件引用关系。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FILE-001 | MVP | POST | `/api/v1/systems/{systemId}/files/upload` | Bearer | `FILE_UPLOAD` | 存储配置可用 | 创建临时文件。 |
| FILE-002 | MVP | GET | `/api/v1/systems/{systemId}/files` | Bearer | `FILE_VIEW` | 系统上下文 | 无。 |
| FILE-003 | MVP | GET | `/api/v1/systems/{systemId}/files/{fileId}` | Bearer | 文件引用权限 | 文件存在 | 无。 |
| FILE-004 | MVP | GET | `/api/v1/systems/{systemId}/files/{fileId}/preview` | Bearer | 文件引用权限 | 文件可预览 | 无。 |
| FILE-005 | MVP | GET | `/api/v1/systems/{systemId}/files/{fileId}/download` | Bearer | 文件引用权限 | 文件可下载 | 无。 |
| FILE-006 | MVP | DELETE | `/api/v1/systems/{systemId}/files/{fileId}` | Bearer | `FILE_DELETE` | 文件未被保护引用 | 软删除或标记过期。 |
| FILE-007 | ENH | POST | `/api/v1/systems/{systemId}/files/chunks` | Bearer | `FILE_UPLOAD` | 分片上传启用 | 创建分片。 |

上传入参：`file`、`bizType`、`moduleId`、`recordId`、`fieldCode`。业务保存前上传的文件为 `TEMP`，业务保存成功后由记录保存事务绑定为 `REFERENCED`。

文件响应：`fileId`、`fileName`、`extension`、`contentType`、`size`、`status`、`previewable`、`downloadUrl`、`ownerMemberId`、`refCount`。

错误码：`UPLOAD_SIZE_EXCEEDED`、`UPLOAD_TYPE_FORBIDDEN`、`UPLOAD_STORAGE_UNAVAILABLE`、`UPLOAD_FILE_NOT_FOUND`、`UPLOAD_FILE_DELETED`、`UPLOAD_REF_PERMISSION_DENIED`。

事务边界：文件物理上传成功但业务保存失败时，文件保持临时状态并按过期清理；已引用文件不得直接物理删除。下载和预览必须校验系统、租户、业务对象权限和文件状态。

## 导入导出

场景：MVP 必做导出任务闭环；导入执行作为占位或后续增强，不阻塞首期。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| EXP-001 | MVP | GET | `/api/v1/systems/{systemId}/exports/templates` | Bearer | `EXPORT_TEMPLATE_VIEW` | 系统上下文 | 无。 |
| EXP-002 | MVP | POST | `/api/v1/systems/{systemId}/exports/templates` | Bearer | `EXPORT_TEMPLATE_CREATE` | 字段存在 | 创建导出模板。 |
| EXP-003 | MVP | PUT | `/api/v1/systems/{systemId}/exports/templates/{templateId}` | Bearer | `EXPORT_TEMPLATE_EDIT` | 模板存在 | 更新导出模板。 |
| EXP-004 | MVP | POST | `/api/v1/systems/{systemId}/exports/jobs` | Bearer | `RECORD_EXPORT` | 模块已发布，用户有导出权限 | 创建导出任务 `QUEUED`。 |
| EXP-005 | MVP | GET | `/api/v1/systems/{systemId}/exports/jobs` | Bearer | `EXPORT_JOB_VIEW` | 系统上下文 | 无。 |
| EXP-006 | MVP | GET | `/api/v1/systems/{systemId}/exports/jobs/{jobId}` | Bearer | `EXPORT_JOB_VIEW` | 任务存在 | 无。 |
| EXP-007 | MVP | POST | `/api/v1/systems/{systemId}/exports/jobs/{jobId}/retry` | Bearer | `EXPORT_JOB_RETRY` | 任务失败且可重试 | 重新排队。 |
| EXP-008 | MVP | POST | `/api/v1/systems/{systemId}/exports/jobs/{jobId}/cancel` | Bearer | `EXPORT_JOB_CANCEL` | 任务未成功且可取消 | 标记取消。 |
| IMP-001 | PLACEHOLDER | POST | `/api/v1/systems/{systemId}/imports/preview` | Bearer | `IMPORT_PREVIEW` | 后续导入能力启用 | 占位，不在 MVP 实现。 |
| IMP-002 | PLACEHOLDER | POST | `/api/v1/systems/{systemId}/imports/jobs` | Bearer | `IMPORT_EXECUTE` | 后续导入能力启用 | 占位，不在 MVP 实现。 |

导出任务入参：`moduleId`、`templateId`、`filters`、`sorter`、`selectedRecordIds`、`fileName`、`idempotencyKey`。后端保存筛选、字段权限和脱敏快照。

导出任务响应：`jobId`、`status`、`progress`、`resultFileId`、`failureReason`、`retryable`、`createdBy`、`createdAt`、`finishedAt`。

错误码：`EXPORT_TEMPLATE_FIELD_INVALID`、`EXPORT_PERMISSION_DENIED`、`EXPORT_JOB_NOT_FOUND`、`EXPORT_JOB_STATUS_CONFLICT`、`EXPORT_FILE_GENERATE_FAILED`、`EXPORT_STORAGE_FAILED`、`IMPORT_NOT_IMPLEMENTED`。

## OpenAPI 管理与外部调用

场景：OpenAPI 客户端绑定系统、租户和 scope，只能访问授权范围内的记录、流程和文件。OpenAPI 表域统一为 OpenAPI 专属域，不与业务应用混用。

### 管理接口

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OPM-001 | MVP | GET | `/api/v1/systems/{systemId}/openapi/clients` | Bearer | `OPENAPI_CLIENT_VIEW` | 系统上下文 | 无。 |
| OPM-002 | MVP | POST | `/api/v1/systems/{systemId}/openapi/clients` | Bearer | `OPENAPI_CLIENT_CREATE` | scope 合法 | 创建客户端草稿或启用。 |
| OPM-003 | MVP | PUT | `/api/v1/systems/{systemId}/openapi/clients/{clientId}` | Bearer | `OPENAPI_CLIENT_EDIT` | 客户端存在 | 更新客户端。 |
| OPM-004 | MVP | PATCH | `/api/v1/systems/{systemId}/openapi/clients/{clientId}/status` | Bearer | `OPENAPI_CLIENT_STATUS` | 状态流转合法 | 启用/停用/过期。 |
| OPM-005 | MVP | POST | `/api/v1/systems/{systemId}/openapi/clients/{clientId}/credentials/rotate` | Bearer | `OPENAPI_CREDENTIAL_ROTATE` | 客户端存在 | 生成新凭证，secret 仅返回一次。 |
| OPM-006 | MVP | PUT | `/api/v1/systems/{systemId}/openapi/clients/{clientId}/scopes` | Bearer | `OPENAPI_SCOPE_EDIT` | scope 不越权 | 更新授权范围。 |
| OPM-007 | MVP | PUT | `/api/v1/systems/{systemId}/openapi/clients/{clientId}/ip-whitelist` | Bearer | `OPENAPI_IP_EDIT` | IP 合法 | 更新白名单。 |
| OPM-008 | MVP | GET | `/api/v1/systems/{systemId}/openapi/access-logs` | Bearer | `OPENAPI_LOG_VIEW` | 系统上下文 | 无。 |

客户端入参：`name`、`code`、`tenantId`、`scopes`、`ipWhitelist`、`rateLimitPolicy`、`expiresAt`、`status`。

客户端响应：`clientId`、`accessKey`、`secretOnce`、`status`、`scopes`、`ipWhitelist`、`rateLimitPolicy`、`createdAt`、`lastUsedAt`。`secretOnce` 只在创建或轮换时返回一次。

### 外部 OpenAPI

OpenAPI 请求头：

| Header | 必填 | 说明 |
| --- | --- | --- |
| X-OpenApi-AccessKey | 是 | 客户端 accessKey。 |
| X-OpenApi-Timestamp | 是 | Unix milliseconds 13 位时间戳，必须在时间窗口内。 |
| X-OpenApi-Nonce | 是 | 防重放随机串。 |
| X-OpenApi-Body-Sha256 | 是 | 请求 body 原始字节 SHA-256 小写 hex；无 body 使用空字节 hash。 |
| X-OpenApi-Signature | 是 | 按签名算法生成的小写 hex HMAC。 |
| X-Idempotency-Key | 写接口必填 | 幂等键。 |
| X-Request-Id | 否 | 请求追踪 ID。 |

| API ID | 阶段 | 方法 | 路径 | 鉴权 | Scope | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OPN-001 | MVP | POST | `/openapi/v1/records/query` | AK/SK | `record:read` | 客户端启用，scope 命中 | 写调用日志。 |
| OPN-002 | MVP | GET | `/openapi/v1/records/{recordId}` | AK/SK | `record:read` | 数据范围命中 | 写调用日志。 |
| OPN-003 | MVP | POST | `/openapi/v1/records` | AK/SK | `record:create` | 字段授权、模块已发布 | 创建业务记录。 |
| OPN-004 | MVP | PUT | `/openapi/v1/records/{recordId}` | AK/SK | `record:update` | 字段写权限、状态允许 | 更新业务记录。 |
| OPN-005 | MVP | POST | `/openapi/v1/records/{recordId}/submit` | AK/SK | `record:submit` | 流程绑定合法 | 提交审批。 |
| OPN-006 | MVP | POST | `/openapi/v1/flow/tasks/{taskId}/actions` | AK/SK | `flow:task:handle` | 外部处理人映射合法 | 处理流程任务。 |
| OPN-007 | MVP | GET | `/openapi/v1/files/{fileId}/download` | AK/SK | `file:download` | 文件引用权限命中 | 写调用日志。 |

OpenAPI 入参必须包含 `systemCode` 或客户端绑定系统，必要时包含 `tenantCode`、`moduleCode`、`recordId`、`values`、`filters`。后端必须按客户端绑定范围补齐 `systemId`、`tenantId`、`clientId`，前端或外部系统不得传内部审计字段。

错误码：`OPENAPI_ACCESS_KEY_INVALID`、`OPENAPI_CLIENT_DISABLED`、`OPENAPI_SIGNATURE_INVALID`、`OPENAPI_TIMESTAMP_EXPIRED`、`OPENAPI_NONCE_REPLAY`、`OPENAPI_BODY_HASH_MISMATCH`、`OPENAPI_IP_DENIED`、`OPENAPI_SCOPE_DENIED`、`OPENAPI_RATE_LIMITED`、`OPENAPI_IDEMPOTENCY_CONFLICT`、`OPENAPI_DATA_SCOPE_DENIED`。

数据落点和事务边界：所有外部调用写 OpenAPI 调用日志、签名结果、scope 命中、幂等结果、错误码和 requestId。写接口复用内部业务事务边界，不允许外部接口绕过字段、状态、流程和数据范围校验。

## 审计日志

场景：审计贯穿平台操作、系统配置、运行数据、流程、文件、导出和 OpenAPI。默认只读。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AUD-001 | MVP | GET | `/api/v1/systems/{systemId}/audit/operation-logs` | Bearer | `AUDIT_OPERATION_VIEW` | 审计权限 | 无。 |
| AUD-002 | MVP | GET | `/api/v1/systems/{systemId}/audit/request-logs` | Bearer | `AUDIT_REQUEST_VIEW` | 审计权限 | 无。 |
| AUD-003 | MVP | GET | `/api/v1/systems/{systemId}/audit/error-logs` | Bearer | `AUDIT_ERROR_VIEW` | 审计权限 | 无。 |
| AUD-004 | MVP | GET | `/api/v1/systems/{systemId}/audit/record-changes` | Bearer | `AUDIT_RECORD_VIEW` | 数据范围命中 | 无。 |
| AUD-005 | MVP | GET | `/api/v1/systems/{systemId}/audit/openapi-logs` | Bearer | `OPENAPI_LOG_VIEW` | 审计权限 | 无。 |
| AUD-006 | MVP | GET | `/api/v1/platform/audit/operation-logs` | Bearer | `PLAT_AUDIT_VIEW` | 平台审计权限 | 无。 |

筛选入参：`requestId`、`operatorId`、`module`、`bizType`、`bizId`、`action`、`result`、`startTime`、`endTime`。

响应字段：`logId`、`requestId`、`operatorType`、`operatorName`、`systemId`、`tenantId`、`bizType`、`bizId`、`action`、`result`、`errorCode`、`createdAt`、`summary`。

错误码：`AUDIT_LOG_NOT_FOUND`、`AUDIT_QUERY_RANGE_TOO_LARGE`、`PERM_DENIED`。

## 运维配置

场景：运维审计人员和平台超级管理员查看系统健康、配置检查、版本和迁移状态。运维接口默认只读，配置修改需单独授权。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OPS-001 | MVP | GET | `/api/v1/ops/health` | Bearer | `OPS_HEALTH_VIEW` | 平台运维权限 | 无。 |
| OPS-002 | MVP | GET | `/api/v1/ops/config-check` | Bearer | `OPS_CONFIG_VIEW` | 平台运维权限 | 无。 |
| OPS-003 | MVP | GET | `/api/v1/ops/version` | Bearer | `OPS_VERSION_VIEW` | 平台运维权限 | 无。 |
| OPS-004 | MVP | GET | `/api/v1/ops/migration/status` | Bearer | `OPS_MIGRATION_VIEW` | 平台运维权限 | 无。 |
| OPS-005 | ENH | PUT | `/api/v1/ops/runtime-configs/{configKey}` | Bearer | `OPS_CONFIG_EDIT` | 配置允许在线修改 | 更新运行配置并写审计。 |

响应字段：`status`、`checks`、`component`、`result`、`message`、`suggestion`、`version`、`buildTime`、`migrationVersion`、`requestId`。

错误码：`OPS_HEALTH_CHECK_FAILED`、`OPS_CONFIG_MISSING`、`OPS_MIGRATION_INCONSISTENT`、`PERM_DENIED`。

## 代码生成器管理或生成任务接口边界

场景：`examine-generator` 是后端代码生成模块，用于读取数据库表和模块映射，在对应业务模块生成 `base` 层 entity/mapper/service/serviceImpl。MVP 的实现交付可优先使用命令行和生成报告；以下接口仅作为内部运维占位，不面向普通用户，不在首期业务前端实现。

| API ID | 阶段 | 方法 | 路径 | 鉴权 | 权限点 | 前置条件 | 状态影响 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GEN-001 | PLACEHOLDER/INTERNAL | GET | `/api/v1/ops/generator/table-mappings` | Bearer | `GENERATOR_VIEW` | DB 设计和 SQL 已冻结 | 读取表到模块映射，不生成代码。 |
| GEN-002 | PLACEHOLDER/INTERNAL | POST | `/api/v1/ops/generator/tasks` | Bearer | `GENERATOR_RUN` | 数据库可连接、表映射已确认 | 创建生成任务，占位。 |
| GEN-003 | PLACEHOLDER/INTERNAL | GET | `/api/v1/ops/generator/tasks/{taskId}` | Bearer | `GENERATOR_VIEW` | 任务存在 | 无。 |
| GEN-004 | PLACEHOLDER/INTERNAL | GET | `/api/v1/ops/generator/tasks/{taskId}/report` | Bearer | `GENERATOR_VIEW` | 任务完成 | 返回生成报告。 |

边界规则：

1. 生成器接口不能在 API 冻结前触发实际代码生成。
2. 生成结果只能落到各业务模块 `base` 层，不生成对外 Controller。
3. 业务 Controller、BO/VO/DTO、权限和事务编排必须由各业务模块 `manage` 层实现。
4. 生成器不得读取未冻结 DB 设计、不得根据旧项目实体直接反推新库表。

错误码：`GENERATOR_DB_UNAVAILABLE`、`GENERATOR_TABLE_MAPPING_MISSING`、`GENERATOR_MODULE_UNSUPPORTED`、`GENERATOR_TASK_STATUS_CONFLICT`、`GENERATOR_REPORT_NOT_FOUND`。

## 前后端字段映射说明

| 前端页面/能力 | 主要接口 | 字段映射规则 |
| --- | --- | --- |
| 登录页 | AUTH-002、AUTH-003、AUTH-005 | 登录表单只提交 loginName/password；用户信息从 `me` 获取。 |
| 我的系统 | PLAT-001、PLAT-002、SYS-001 | 系统卡片展示 `systemName`、`status`、`tenantMode`、`memberRoleNames`；进入系统后保存系统上下文。 |
| 系统管理/成员 | MEM-001 至 MEM-007、RBAC-001 至 RBAC-011 | 成员列表显示平台账号信息和系统内扩展信息；平台账号字段只读。 |
| 应用配置 | APP-001 至 APP-007 | 应用表单只写 name/code/icon/description/status；系统和租户由上下文补齐。 |
| 模块字段设计 | MOD-001 至 MOD-007、FIELD-001 至 FIELD-005 | 字段设计器使用 `fieldCode`、`fieldType`、`required`、`options`、`relationConfig`；发布前调用检查接口。 |
| 页面配置 | UI-001 至 UI-009 | 列表、表单、详情均以 schema 保存；字段不存在或无权限时后端返回检查错误。 |
| 运行台列表 | RUN-001 至 RUN-003 | 列定义来自 schema；筛选和排序只能提交 schema 允许字段。 |
| 运行台表单/详情 | RUN-004 至 RUN-010 | 表单提交 `values`；详情返回 `values`、`actions`、`flowSummary`、`fileRefs`。 |
| 流程工作台 | FLOW-007 至 FLOW-012 | 待办展示任务、业务摘要、当前节点；操作使用统一 actions 接口。 |
| 文件组件 | FILE-001 至 FILE-006 | 上传后拿 `fileId` 写入动态字段值；详情通过文件引用补齐预览/下载。 |
| 导出任务 | EXP-001 至 EXP-008 | 列表筛选快照传给导出任务，任务列表轮询状态并下载结果文件。 |
| OpenAPI 管理 | OPM-001 至 OPM-008 | 凭证 secret 只展示一次，前端不得持久化明文。 |
| 审计/运维 | AUD-001 至 AUD-006、OPS-001 至 OPS-004 | 所有错误页和日志检索必须支持 requestId。 |

## 数据落点和事务边界汇总

| 场景 | 数据落点方向 | 事务边界 |
| --- | --- | --- |
| 创建系统 | 平台系统、默认租户、成员扩展、系统角色、菜单、默认应用 | 单事务；任一步失败整体回滚。 |
| 角色授权 | 系统角色、菜单、操作、字段、数据范围授权 | 单事务；权限缓存刷新失败必须返回可重试错误或明确降级策略。 |
| 模块发布 | 模块、字段、页面、菜单、流程/导出绑定、发布版本 | 发布检查通过后生成版本；运行态只读发布版本。 |
| 记录保存 | 记录主对象、字段值、索引值、关联/子表、历史、附件引用 | 单事务；字段无权限直接拒绝，不静默忽略。 |
| 提交审批 | 业务记录、流程实例、任务、审批日志、业务状态 | 同步事务；流程创建失败则提交失败。 |
| 文件上传 | 文件元数据、存储对象、引用关系 | 上传和业务保存分离；业务绑定在记录保存事务内完成。 |
| 导出任务 | 任务、筛选快照、权限快照、结果文件、任务日志 | 创建任务事务内保存快照；后台失败更新失败状态。 |
| OpenAPI 写入 | 调用日志、幂等记录、内部业务对象 | 先验签和幂等，再进入内部业务事务。 |
| 审计日志 | 请求日志、操作日志、错误日志、业务变更日志 | 审计写入失败不能静默吞掉，至少写错误日志并携带 requestId。 |

表前缀方向供 DBA 审查：平台与租户使用 `un_plat_`，业务应用/应用版本/动态模块/模型/记录/导出使用 `un_module_`，流程审批使用 `un_flow_`，上传与文件使用 `un_upload_`，OpenAPI 使用 `un_openapi_`，系统日志/审计使用 `un_sys_` 或 `un_audit_`。`un_app_` 仅作为旧项目 OpenAPI 历史表域参考，MVP 不新建该前缀表。本文不生成表结构，不生成 SQL。

## 测试契约

| 模块 | 正常用例 | 异常用例 | 权限用例 | 边界/并发/幂等 |
| --- | --- | --- | --- | --- |
| 认证 | 登录、刷新、退出、当前用户 | 密码错误、账号停用、token 过期 | 未登录访问内部 API | 连续失败锁定、重复退出。 |
| 平台中心 | 创建系统并初始化 | 系统编码重复、初始化失败 | 非平台管理员管理账号 | 创建系统事务回滚。 |
| 系统/成员 | 邀请成员、切换租户 | 租户停用、成员不存在 | 跨系统成员访问 | 同一账号重复加入系统。 |
| RBAC | 角色授权、运行菜单过滤 | 字段权限配置非法 | 字段无写权保存拒绝 | 权限缓存刷新、显式禁用优先。 |
| 应用/模块 | 创建应用、字段、发布 | 字段类型非法、发布检查失败 | 应用配置管理员越权 | 发布版本冲突。 |
| 运行填报 | 新增、编辑、详情、删除、提交 | 必填缺失、唯一冲突、状态锁定 | 数据范围越权 | 自动编号并发、幂等提交。 |
| 流程 | 发布流程、处理待办 | 无结束路径、重复审批、原因缺失 | 非候选人审批 | 任务并发处理、撤回状态冲突。 |
| 文件 | 上传、预览、下载、删除 | 类型禁用、存储不可用 | 无业务对象权限下载 | 临时文件过期、引用文件删除限制。 |
| 导出 | 创建任务、成功下载、失败重试 | 模板字段非法、文件生成失败 | 无导出权限、脱敏权限 | 重复创建、任务领取并发。 |
| OpenAPI | 签名查询、创建记录、提交审批 | 签名错误、时间过期、IP 不匹配 | scope 越界、数据范围越界 | 幂等重放、幂等冲突、限流。 |
| 审计/运维 | requestId 检索、健康检查 | 查询范围过大、健康异常 | 非审计角色访问 | 日志链路完整性。 |
| 生成器 | 查看映射占位 | DB 不可用、映射缺失 | 非运维角色访问 | 任务状态冲突。 |

## 后端实现约束

1. 当前版本仅供第 2 次 API 契约闭环后复核；复核通过并由 `docs/api_review.md` 明确冻结后，backend 必须遵守冻结契约。确需修改接口、字段、错误码、枚举或状态流转时，必须回到 API 契约评审。
2. Controller 入参使用 BO/DTO，出参使用 VO，不直接暴露 `base.entity`。
3. `base` 只承载 MyBatis-Plus 贴表 CRUD，不生成对外 Controller；业务 API 放在各业务模块 `manage.controller` 或明确聚合入口。
4. `examine-web` 只承载启动、Web 装配、全局异常、过滤器/拦截器和必要聚合入口，不堆业务实现。
5. 权限校验必须在后端执行，顺序为登录/OpenAPI 签名、系统/租户上下文、成员/角色状态、菜单/API、操作、字段、数据范围、状态/流程锁定、审计。
6. 所有错误响应必须包含 `requestId` 和模块化错误码；前端 typed SDK 必须以本文档枚举和错误码为来源。
7. OpenAPI 内外部接口必须分离，外部接口不能复用内部 session 权限模型绕过 scope。
8. 代码生成器只能生成各模块 `base` 层，不得依据旧项目实体直接生成新库结构或业务接口。

## 后端契约索引

| API 分组 | 后端模块 | Controller 边界 | Manage 服务职责 | 禁止事项 |
| --- | --- | --- | --- | --- |
| 认证/平台中心 | `examine-plat`、`examine-web` | 平台认证和平台管理入口 | 账号、系统、租户、平台 RBAC、平台配置 | 不返回密码哈希，不让平台管理员直接改系统内业务数据。 |
| 系统成员/RBAC | `examine-module`、`examine-plat` | 系统上下文下的成员和权限入口 | 成员扩展、部门、角色、菜单、字段、数据范围 | 不把系统成员当独立登录账号。 |
| 应用/模块/页面配置 | `examine-module` | 应用配置中心入口 | 应用、模块、字段、页面、菜单、发布版本 | 不照搬生成式 CRUD 接口。 |
| 运行填报 | `examine-module` | 运行台记录入口 | 记录、字段值、索引、历史、附件引用、流程触发 | 不暴露 EAV 物理结构给前端。 |
| 流程审批 | `examine-flow` | 流程配置、待办和实例入口 | 模板、版本、实例、任务、动作日志、状态联动 | 不只保存不可校验 JSON。 |
| 上传文件 | `examine-upload` | 文件上传、预览、下载入口 | 文件元数据、存储配置、引用权限、补偿 | 不绕过业务对象权限下载文件。 |
| 导入导出 | `examine-module`、`examine-upload` | 模板和任务入口 | 导出模板、任务、结果文件、失败重试 | MVP 不实现导入执行闭环。 |
| OpenAPI | `examine-app`、`examine-web` | 客户端管理和外部调用入口 | 凭证、签名、scope、IP 白名单、幂等、限流、日志 | OpenAPI 表域不得与业务应用混用。 |
| 审计/运维 | `examine-core`、`examine-plat`、`examine-web` | 审计查询和健康检查入口 | requestId、日志、健康、版本、配置检查 | 不静默吞掉健康异常。 |
| 代码生成器 | `examine-generator` | 内部生成任务占位 | 表映射、生成任务、生成报告 | 不在 API 冻结前触发生成，不生成业务 Controller。 |

## API 审查关注点

DBA 审查重点：按本文确认表域、状态、快照、动态字段、流程、文件、导出、OpenAPI 和审计落点是否足以进入 DB 设计。

backend 审查重点：确认接口语义、事务边界、权限顺序、错误码、幂等和模块职责是否可实现。

frontend 审查重点：确认响应结构、动态字段值、schema、枚举、错误码和页面到接口映射是否足以生成 typed SDK。

test 审查重点：确认正常、异常、权限、边界、并发和幂等测试点是否覆盖端到端闭环。
