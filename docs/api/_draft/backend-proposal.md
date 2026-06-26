# Backend API Proposal（Contract Draft）

> 角色：Phase 2 Contract / Backend worker
> 输出：`docs/api/_draft/backend-proposal.md`
> 状态：设计已签字，当前 `phase=contract`，`gates.design_user_approved=true`，但 `gates.api_frozen=false`、`tasks_planned=false`。
> 结论：本文件仅作为 API 契约冻结前的后端接口提案，不进入 Java 实现，不修改 backend/frontend/sql。

## 1. 当前闸门判断

- `docs/design/user-approval.md` 已签字：`approved: true`，签字时间 `2026-06-23T15:10:00+08:00`。
- `.cursor/session/state.json` 当前为 `phase=contract`、`mode=api-contract-drafting`。
- `api_frozen=false`、`tasks_planned=false`，因此本阶段只产出契约草案，禁止创建实现代码、SQL 实体或正式任务单。
- 本提案以 `docs/design/prototype-brief.md`、`docs/design/reviews/prototype-latest-2026-06-18.md` 顶部“当前唯一开发口径”、`docs/design/pre-coding-readiness.md` 为输入。

## 2. 通用 API 契约

### 2.1 URL 分层

- 平台态接口：`/api/v1/platform/**`
- 账号与登录接口：`/api/v1/auth/**`、`/api/v1/account/**`
- 系统态接口：`/api/v1/systems/{systemId}/**`
- 租户态接口：`/api/v1/systems/{systemId}/tenants/{tenantId}/**`
- 运行态动态记录接口：`/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/**`
- OpenAPI 外部接入：`/openapi/v1/apps/{externalAppCode}/**`

系统态接口必须由服务端校验当前登录账号、`systemId`、`tenantId`、`systemMemberId`、权限快照和数据范围。前端传入的 `systemId/tenantId/moduleId` 只作为路由目标，不作为权限事实。

### 2.2 通用返回

```json
{
  "code": "SUCCESS",
  "message": "成功",
  "requestId": "req_20260623151000001",
  "traceId": "trc_20260623151000001",
  "auditLogId": "aud_20260623151000001",
  "data": {}
}
```

- `requestId`：单次请求标识，前端展示错误追踪号时使用。
- `traceId`：跨服务、后台任务、消息、日志串联标识。
- `auditLogId`：产生审计记录的写操作、导出、确认、审批、密钥轮换、Agent 写入等必须返回。
- 读接口如果不产生审计，可返回 `auditLogId=null`，但仍需有 `requestId/traceId`。

### 2.3 分页、筛选、排序

统一分页请求对象：

```json
{
  "pageNo": 1,
  "pageSize": 20,
  "keyword": "车辆",
  "filters": [
    {"field": "status", "operator": "IN", "value": ["ACTIVE", "DISABLED"]},
    {"field": "createdAt", "operator": "BETWEEN", "value": ["2026-06-01", "2026-06-23"]}
  ],
  "sorts": [
    {"field": "updatedAt", "direction": "DESC"}
  ]
}
```

统一分页响应对象：

```json
{
  "records": [],
  "pageNo": 1,
  "pageSize": 20,
  "total": 0,
  "hasNext": false
}
```

- `filters.operator` 首期建议限定为 `EQ/NE/LIKE/IN/BETWEEN/GT/GTE/LT/LTE/IS_NULL/IS_NOT_NULL`。
- 动态字段筛选必须先经过字段发布版本、字段类型注册表和字段权限校验。
- 排序字段必须来自已发布列表字段、索引字段或内置审计字段，不允许任意 SQL 字段名透传。

### 2.4 幂等与后台任务

- 写操作建议支持 `Idempotency-Key` 请求头；批量导入、导出、发布检查、密钥轮换、AI 写入确认、流程模拟必须要求幂等键。
- 统一后台任务对象 `AsyncTaskVO`：

```json
{
  "taskId": "task_001",
  "bizType": "IMPORT_RECORD",
  "idempotencyKey": "idem_001",
  "status": "QUEUED",
  "progress": 0,
  "retryable": true,
  "cancelable": true,
  "resultFile": null,
  "errorFile": null,
  "failureReason": null,
  "partialSuccessCount": 0,
  "partialFailureCount": 0,
  "rollbackSupported": false,
  "traceId": "trc_001",
  "auditLogId": "aud_001",
  "createdBy": "account_001",
  "createdAt": "2026-06-23T15:10:00+08:00"
}
```

任务状态建议：`QUEUED/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELED/ROLLBACKING/ROLLED_BACK`。

### 2.5 错误码原则

- 错误码按域分段，不用一个通用参数错误承接所有场景。
- 权限类错误必须返回缺失权限、当前上下文、是否可申请、禁用原因和 `requestId`。
- 密钥、SSO、OpenAPI、Agent、导入导出、流程发布等关键能力必须有专属错误码。

建议错误码域：

| 域 | 前缀 | 示例 |
|---|---|---|
| 账号认证 | `AUTH_` | `AUTH_INVALID_CREDENTIAL` |
| 平台系统 | `PLATFORM_` | `PLATFORM_SYSTEM_DISABLED` |
| 租户成员 | `TENANT_` / `MEMBER_` | `MEMBER_MAPPING_NOT_FOUND` |
| 权限 | `PERMISSION_` | `PERMISSION_DENIED_BY_FIELD` |
| 模块配置 | `MODULE_` / `FIELD_` / `DICT_` | `FIELD_NOT_PUBLISHED` |
| 运行态记录 | `RECORD_` | `RECORD_NOT_FOUND` |
| 流程待办消息 | `FLOW_` / `TODO_` / `MESSAGE_` | `FLOW_NODE_INVALID` |
| 工作管理 | `WORK_` | `WORK_KANBAN_FIELD_UNPUBLISHED` |
| SSO/OpenAPI/Secret | `SSO_` / `SECRET_` / `OPENAPI_` | `SECRET_ROTATION_FAILED` |
| Agent | `AGENT_` | `AGENT_WRITE_CONFIRM_REQUIRED` |
| 日志任务运维 | `LOG_` / `TASK_` / `OPS_` | `TASK_NOT_RETRYABLE` |

### 2.6 审计

所有写入、导入、导出、审批、发布、密钥轮换、Agent 确认、OpenAPI 调用、权限变更、上下文切换失败都必须生成审计或安全日志，并在响应中返回 `traceId/requestId/auditLogId`。

日志最小字段：操作者、账号类型、系统/租户/成员上下文、模块/对象、动作、结果、IP、设备、字段差异、权限快照、脱敏结果、失败原因。

## 3. 服务端生成的上下文对象

### 3.1 SystemSwitchContext

系统切换必须由服务端生成，前端不得拼接权限上下文。

建议接口：

- `GET /api/v1/platform/system-switch/options`
- `POST /api/v1/platform/system-switch`
- `GET /api/v1/context/current-system`

`SystemSwitchRequest`：

```json
{
  "targetSystemId": "sys_vehicle",
  "targetTenantId": "tenant_default",
  "accountMemberBindingId": "amb_001"
}
```

`SystemSwitchContextVO`：

```json
{
  "accountId": "acc_001",
  "accountMemberBindingId": "amb_001",
  "systemId": "sys_vehicle",
  "tenantId": "tenant_default",
  "systemMemberId": "sm_001",
  "effectiveRoleIds": ["role_admin"],
  "dataScope": {"type": "ALL", "expression": "system=sys_vehicle"},
  "permissionSnapshot": {},
  "messageTodoScope": {"systemId": "sys_vehicle", "tenantId": "tenant_default"},
  "expiresAt": "2026-06-23T17:10:00+08:00"
}
```

约束：

- 平台账号没有 `systemMemberId` 时不能返回系统业务上下文，只能返回 `NoMemberAccessRequest` 入口。
- `platform_admin_root` 不等于系统超级管理员，不能绕过系统成员映射访问业务数据。
- 系统切换成功后，服务端返回模块分组、左侧模块、待办消息计数、字段权限摘要，支持前端重绘业务壳。

### 3.2 TenantSwitchContext

建议接口：

- `GET /api/v1/systems/{systemId}/tenant-switch/options`
- `POST /api/v1/systems/{systemId}/tenant-switch`

`TenantSwitchContextVO`：

```json
{
  "systemId": "sys_vehicle",
  "tenantId": "tenant_default",
  "tenantRoleIds": ["tenant_admin"],
  "tenantDataScope": {"type": "TENANT", "expression": "tenant=tenant_default"},
  "isTenantSwitchable": true,
  "disabledReason": null,
  "permissionSnapshotVersion": "perm_20260623_001"
}
```

单租户系统隐藏租户切换入口；多租户切换后必须刷新菜单、数据范围、待办、消息和字段权限。

### 3.3 EffectivePermissionSnapshot

建议接口：

- `GET /api/v1/systems/{systemId}/permissions/effective`
- `POST /api/v1/systems/{systemId}/permissions/effective/preview`

`EffectivePermissionSnapshotVO`：

```json
{
  "snapshotId": "eps_001",
  "permissionVersion": "perm_20260623_001",
  "systemMemberId": "sm_001",
  "tenantId": "tenant_default",
  "sourceRoleIds": ["role_operator", "role_auditor"],
  "denyPolicyIds": [],
  "modulePermissions": [],
  "fieldPermissions": [],
  "actionPermissions": [],
  "dataScopeExpression": "dept in currentMember.departments",
  "explain": []
}
```

原则：

- 多角色默认允许并集，显式拒绝优先。
- 字段权限、审批节点临时字段权限、数据范围冲突规则必须在 API 冻结前裁决。
- 缓存 key 必须包含 `systemId/tenantId/systemMemberId/permissionVersion`；角色、字段、流程节点权限发布后必须失效。

## 4. API 分层与模块边界

## 4.1 auth/account

边界：平台账号、登录注册、密码、MFA、当前账号信息、账号安全。只负责统一账号，不直接表达系统业务成员权限。

关键 endpoint 组：

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/token/refresh`
- `POST /api/v1/auth/register-with-system`
- `POST /api/v1/auth/password-reset/request`
- `POST /api/v1/auth/password-reset/confirm`
- `GET /api/v1/account/me`
- `PATCH /api/v1/account/me/profile`
- `PATCH /api/v1/account/me/password`
- `GET /api/v1/account/me/login-logs`

请求/响应对象：

- `LoginRequest`：`loginName/password/mfaCode/loginTarget/systemCode/tenantCode`
- `LoginResponse`：`accessToken/refreshToken/account/profile/defaultLanding/ssoBindingSummary/requestId/traceId`
- `RegisterWithSystemRequest`：`accountName/mobile/email/password/systemName/systemCode/tenantMode/templateCode`
- `RegisterWithSystemResponse`：`accountId/systemId/systemMemberId/systemSuperAdminRoleId/initGuideSteps`
- `AccountProfileVO`：账号基本资料、安全状态、可进入平台/系统列表摘要。

分页/筛选/排序：

- 登录日志使用统一分页，支持 `loginResult/authMethod/identityProvider/ip/timeRange/keyword` 筛选，按 `loginAt` 排序。

错误码原则：

- 登录失败、账号停用、密码过期、MFA 失败、注册系统编码重复、登录目标不可用必须拆分错误码。
- 登录成功但无系统成员映射时，不返回业务 token 直达能力，应返回 `NoMemberAccessRequest` 创建入口或禁用原因。

幂等/后台任务：

- 注册并创建系统建议支持 `Idempotency-Key`，避免重复创建系统。
- 找回密码请求需要限流，不进入后台任务。

审计：

- 登录、登出、刷新失败、密码修改、注册系统创建均记录 `requestId/traceId/auditLogId`。

## 4.2 platform/system

边界：平台工作台、平台后台、系统生命周期、平台组织/角色、平台配置、平台消息/待办/任务。平台层不直接读写系统业务数据。

关键 endpoint 组：

- `GET /api/v1/platform/dashboard`
- `GET /api/v1/platform/systems`
- `POST /api/v1/platform/systems`
- `GET /api/v1/platform/systems/{systemId}`
- `PATCH /api/v1/platform/systems/{systemId}`
- `POST /api/v1/platform/systems/{systemId}/enable`
- `POST /api/v1/platform/systems/{systemId}/disable`
- `DELETE /api/v1/platform/systems/{systemId}`
- `POST /api/v1/platform/systems/{systemId}/restore`
- `GET /api/v1/platform/config`
- `PATCH /api/v1/platform/config`
- `GET /api/v1/platform/health`

请求/响应对象：

- `SystemCreateRequest`：`systemName/systemCode/tenantMode/templateCode/domain/icon/initOptions`
- `SystemVO`：`systemId/systemCode/systemName/status/tenantMode/ownerAccountId/recentAccessAt/disabledReason/lifecycleAudit`
- `SystemLifecycleRequest`：`reason/effectiveAt/confirmText`
- `PlatformHealthVO`：数据库、Redis、文件、短信、SSO、OpenAPI、AI Agent、容量、最近备份。

分页/筛选/排序：

- 系统列表支持 `status/tenantMode/owner/keyword/recentAccessRange`，排序 `createdAt/updatedAt/recentAccessAt`。

错误码原则：

- 系统编码重复、系统停用、删除有依赖、恢复不可用、容量配额不足、平台权限不足必须拆分。

幂等/后台任务：

- 创建系统、删除系统、恢复系统、体检、备份恢复、部署回滚建议走幂等。
- 删除、恢复、体检、备份恢复可返回 `AsyncTaskVO`。

审计：

- 生命周期动作必须记录原因、操作者、影响范围、`traceId/auditLogId`。

## 4.3 tenant/member

边界：系统内租户、组织架构、成员、员工绑定、账号成员映射。不得由前端伪造成员上下文。

关键 endpoint 组：

- `GET /api/v1/systems/{systemId}/tenants`
- `POST /api/v1/systems/{systemId}/tenants`
- `PATCH /api/v1/systems/{systemId}/tenants/{tenantId}`
- `POST /api/v1/systems/{systemId}/tenants/{tenantId}/enable`
- `POST /api/v1/systems/{systemId}/tenants/{tenantId}/disable`
- `GET /api/v1/systems/{systemId}/org/departments`
- `POST /api/v1/systems/{systemId}/org/departments`
- `GET /api/v1/systems/{systemId}/members`
- `POST /api/v1/systems/{systemId}/members`
- `PATCH /api/v1/systems/{systemId}/members/{systemMemberId}`
- `POST /api/v1/systems/{systemId}/members/{systemMemberId}/bind-account`
- `GET /api/v1/systems/{systemId}/member-bindings`

请求/响应对象：

- `TenantVO`：`tenantId/name/status/domain/quota/dataScopeStrategy/disabledReason`
- `DepartmentTreeVO`：树节点、父子层级、成员数量、启停状态。
- `SystemMemberVO`：`systemMemberId/accountId/employeeNo/name/deptIds/status/roleIds/dataScope/accountMemberBindingId`
- `AccountMemberBindingVO`：`accountMemberBindingId/accountId/systemId/tenantId/systemMemberId/bindingStatus/source`

分页/筛选/排序：

- 成员列表支持 `departmentId/status/roleId/keyword/bindingStatus`。
- 租户列表支持 `status/domain/keyword`。

错误码原则：

- 成员不存在、账号已绑定、租户停用、部门有子节点、成员状态禁用、跨租户绑定冲突必须拆分。

幂等/后台任务：

- 批量导入成员、组织同步、成员绑定预检走后台任务。

审计：

- 成员增删改、角色分配、账号绑定、租户切换、组织同步均记录审计。

## 4.4 role/permission

边界：平台角色、系统角色、菜单/模块/字段/动作/数据范围权限、有效权限快照。权限解释由后端给出。

关键 endpoint 组：

- `GET /api/v1/platform/roles`
- `POST /api/v1/platform/roles`
- `PATCH /api/v1/platform/roles/{roleId}`
- `GET /api/v1/systems/{systemId}/roles`
- `POST /api/v1/systems/{systemId}/roles`
- `PATCH /api/v1/systems/{systemId}/roles/{roleId}`
- `POST /api/v1/systems/{systemId}/roles/{roleId}/assign-members`
- `GET /api/v1/systems/{systemId}/roles/{roleId}/permissions`
- `PUT /api/v1/systems/{systemId}/roles/{roleId}/permissions`
- `GET /api/v1/systems/{systemId}/permissions/effective`
- `POST /api/v1/systems/{systemId}/permissions/effective/preview`

请求/响应对象：

- `RoleSaveRequest`：`roleName/roleCode/roleType/status/description`
- `RolePermissionSaveRequest`：`menuPermissions/modulePermissions/actionPermissions/fieldPermissions/dataScopeRules/denyPolicies`
- `PermissionPreviewRequest`：`systemMemberId/tenantId/roleIds/moduleId/recordId/actionCode`
- `PermissionDecisionVO`：`allowed/disabledReason/missingPermissions/dataScopeExpression/fieldMaskRules/explain`

分页/筛选/排序：

- 角色列表支持 `roleType/status/keyword`。
- 权限预览按模块、动作、字段分组返回，不分页。

错误码原则：

- 角色编码重复、内置角色不可删除、显式拒绝命中、字段无查看权限、动作无权限、数据范围为空要拆分。

幂等/后台任务：

- 权限发布、权限缓存失效可同步返回版本；大规模成员权限重算可返回后台任务。

审计：

- 权限变更必须记录修改前后、影响角色/成员、权限版本、`auditLogId`。

## 4.5 module-config

边界：模块组、模块、字段、字典、页面配置、列表场景、页面动作、导入导出、打印模板、发布版本。模块组只做导航和发布关系，不作为业务数据父级。

关键 endpoint 组：

- `GET /api/v1/systems/{systemId}/module-groups`
- `POST /api/v1/systems/{systemId}/module-groups`
- `PATCH /api/v1/systems/{systemId}/module-groups/{groupId}`
- `POST /api/v1/systems/{systemId}/module-groups/{groupId}/publish`
- `GET /api/v1/systems/{systemId}/modules`
- `POST /api/v1/systems/{systemId}/modules`
- `GET /api/v1/systems/{systemId}/modules/{moduleId}`
- `PATCH /api/v1/systems/{systemId}/modules/{moduleId}`
- `GET /api/v1/systems/{systemId}/modules/{moduleId}/fields`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/fields`
- `PATCH /api/v1/systems/{systemId}/modules/{moduleId}/fields/{fieldId}`
- `GET /api/v1/systems/{systemId}/dict-types`
- `POST /api/v1/systems/{systemId}/dict-types`
- `GET /api/v1/systems/{systemId}/dict-types/{dictTypeId}/items`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/publish-check`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/publish`
- `POST /api/v1/systems/{systemId}/modules/{moduleId}/rollback`

请求/响应对象：

- `ModuleGroupVO`：`groupId/name/sort/visibleRoleIds/publishStatus/publishedVersion`
- `ModuleVO`：`moduleId/groupId/moduleCode/name/status/publishStatus/currentVersion`
- `FieldDefinitionVO`：`fieldId/fieldCode/name/fieldType/storageType/filterOperators/sortable/required/maskRule/importExportRule`
- `ListSceneVO`：默认表头、筛选、排序、整行点击、批量动作。
- `PublishCheckResultVO`：`passed/failureItems/warningItems/impactRefs/traceId`

分页/筛选/排序：

- 模块按 `groupId/status/publishStatus/keyword` 筛选。
- 字段按 `fieldType/status/keyword` 筛选，排序按 `sort/createdAt`。
- 字典项支持树形、级联、状态、标签类型筛选。

错误码原则：

- 编码重复、字段类型不支持、字段被发布版本引用、字典项停用被引用、发布检查失败、回滚版本不存在必须拆分。

幂等/后台任务：

- 发布检查可同步或后台任务；发布、回滚、导入配置建议支持幂等。

审计：

- 配置草稿、发布、回滚记录配置版本、影响范围、`traceId/auditLogId`。

## 4.6 runtime-record

边界：系统业务运行态动态记录、列表、详情、新建编辑、草稿、附件、导入导出、打印、业务动作。业务数据归属 `systemId/tenantId/moduleId`，不归属 `appId`。

关键 endpoint 组：

- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/search`
- `GET /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records`
- `PATCH /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}`
- `DELETE /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/actions/{actionCode}`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/drafts`
- `GET /api/v1/systems/{systemId}/runtime/modules/{moduleId}/drafts/{draftId}`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/imports/precheck`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/imports/confirm`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/exports`
- `POST /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/attachments`
- `GET /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/history`

请求/响应对象：

- `RecordSearchRequest`：统一分页 + `sceneCode/fieldFilters/keyword`
- `RecordListVO`：`recordId/title/summary/fields/actions/disabledReasons/permissionSnapshotVersion`
- `RecordDetailVO`：基础字段、子表、关联记录、附件、打印记录、操作记录、审批摘要、字段脱敏结果。
- `RecordSaveRequest`：`fieldValues/childRows/attachments/draftId`
- `ImportPrecheckRequest`：`fileId/templateCode/fieldMapping/duplicateStrategy`
- `ExportRequest`：`scope/selectedRecordIds/fields/fileFormat/desensitizeMode`

分页/筛选/排序：

- 列表必须服务端分页。
- 动态字段筛选、排序先校验字段类型注册表、发布版本、字段权限和索引支持。

错误码原则：

- 记录不存在、字段无权限、字段校验失败、数据范围无权、状态禁止操作、导入预检失败、导出字段无权限必须拆分。

幂等/后台任务：

- 保存、更新、业务动作支持幂等。
- 导入、导出、大批量删除、批量转移、打印批量导出走后台任务。

审计：

- 记录新增/修改/删除必须保存字段前后值、来源 Web/OpenAPI/Agent、权限快照和脱敏结果。

## 4.7 workflow/todo/message

边界：流程配置、流程实例、审批任务、待办、消息模板、通知渠道、消息投递日志。待办是独立工作台，消息是顶部消息流抽屉。

关键 endpoint 组：

- `GET /api/v1/systems/{systemId}/flows`
- `POST /api/v1/systems/{systemId}/flows`
- `PATCH /api/v1/systems/{systemId}/flows/{flowId}`
- `POST /api/v1/systems/{systemId}/flows/{flowId}/simulate`
- `POST /api/v1/systems/{systemId}/flows/{flowId}/publish-check`
- `POST /api/v1/systems/{systemId}/flows/{flowId}/publish`
- `GET /api/v1/systems/{systemId}/flow-instances/{instanceId}`
- `POST /api/v1/systems/{systemId}/approval-tasks/{taskId}/approve`
- `POST /api/v1/systems/{systemId}/approval-tasks/{taskId}/reject`
- `POST /api/v1/systems/{systemId}/approval-tasks/{taskId}/transfer`
- `POST /api/v1/systems/{systemId}/todos/search`
- `POST /api/v1/platform/todos/search`
- `POST /api/v1/systems/{systemId}/messages/search`
- `POST /api/v1/platform/messages/search`
- `POST /api/v1/systems/{systemId}/messages/mark-read`
- `POST /api/v1/systems/{systemId}/messages/archive`
- `GET /api/v1/systems/{systemId}/notification-templates`
- `POST /api/v1/systems/{systemId}/notification-templates`
- `GET /api/v1/systems/{systemId}/message-delivery-logs`

请求/响应对象：

- `FlowDefinitionVO`：节点、连线、条件标签、节点属性、触发事件、绑定模块、版本。
- `FlowNodeConfigVO`：审批、条件、字段更新、外部 API、超时提醒等专属属性。
- `ApprovalActionRequest`：`taskId/action/comment/fieldValues/nextAssignee/idempotencyKey`
- `TodoVO`：`todoId/type/status/title/sourceObject/primaryAction/disabledReason`
- `MessageVO`：`messageId/level/templateCode/title/content/status/target/layer/readAt/archivedAt`
- `NotificationTemplateVO`：`templateCode/layer/type/variables/channels/target/dedupeKey/readReceipt/doNotDisturb/retryPolicy`
- `MessageDeliveryLogVO`：投递渠道、结果、失败原因、重试次数、`traceId`。

分页/筛选/排序：

- 待办支持 `type/status/moduleId/timeRange/keyword`。
- 消息支持 `systemId/tenantId/templateCode/type/readStatus/archiveStatus/timeRange/keyword`。

错误码原则：

- 流程发布检查失败、节点配置缺失、审批任务已处理、审批人无权、消息模板跳转目标非法、平台消息直跳系统业务详情必须拆分。

幂等/后台任务：

- 审批处理必须幂等。
- 流程模拟、发布检查、模板批量发布、消息补发可走后台任务。

审计：

- 审批处理、流程发布、消息模板变更、消息投递失败必须有 `traceId/auditLogId`。

## 4.8 work-management

边界：工作仪表盘、项目任务、普通任务、日报、看板规则、日报自动草稿。工作配置在系统后台，运行态只展示和办理当前成员有权限的数据。

关键 endpoint 组：

- `GET /api/v1/systems/{systemId}/work/dashboard`
- `POST /api/v1/systems/{systemId}/work/projects/search`
- `POST /api/v1/systems/{systemId}/work/project-tasks/search`
- `POST /api/v1/systems/{systemId}/work/project-tasks`
- `PATCH /api/v1/systems/{systemId}/work/project-tasks/{taskId}`
- `POST /api/v1/systems/{systemId}/work/plain-tasks/search`
- `POST /api/v1/systems/{systemId}/work/plain-tasks`
- `PATCH /api/v1/systems/{systemId}/work/plain-tasks/{taskId}`
- `POST /api/v1/systems/{systemId}/work/kanban/query`
- `POST /api/v1/systems/{systemId}/work/daily-reports/search`
- `POST /api/v1/systems/{systemId}/work/daily-reports`
- `POST /api/v1/systems/{systemId}/work/daily-reports/auto-draft`
- `GET /api/v1/systems/{systemId}/work/config`
- `PATCH /api/v1/systems/{systemId}/work/config`
- `POST /api/v1/systems/{systemId}/work/config/publish-check`

请求/响应对象：

- `WorkDashboardVO`：项目概览、今日预警、月历、我的任务、日报提醒。
- `WorkTaskVO`：`taskId/taskType/title/projectId/assignee/collaborators/status/tags/progress/dueAt/relatedObject`
- `KanbanQueryRequest`：`taskType/columnFieldId/swimlaneFieldId/groupFieldId/pageCursor`
- `DailyReportVO`：`reportId/date/status/content/sourceSummary/submitter`
- `DailyReportAutoSourceRuleVO`：`sourceTypes/taskScope/todoScope/messageScope/logScope/approvalScope/permissionPolicy`

分页/筛选/排序：

- 项目任务、普通任务列表分页；看板不分页，使用 `cursor` 向下加载更多。
- 日报只展示我的日报，支持 `dateRange/status/projectId/keyword`。

错误码原则：

- 看板字段未发布、字典项停用、任务无权、日报重复提交、自动草稿来源无权限必须拆分。

幂等/后台任务：

- 日报自动草稿可同步生成草稿，但提交必须人工确认。
- 大范围自动草稿统计、看板重算可走后台任务。

审计：

- 任务状态变更、日报提交、自动草稿来源和人工确认必须记录权限快照。

## 4.9 sso/secret/openapi

边界：平台身份源、系统继承、组织映射、员工绑定、无成员申请、SecretRef、SecretRotationJob、对外应用、OpenAPI scope/限流/调用日志。

关键 endpoint 组：

- `GET /api/v1/platform/identity-providers`
- `POST /api/v1/platform/identity-providers`
- `PATCH /api/v1/platform/identity-providers/{providerId}`
- `POST /api/v1/platform/identity-providers/{providerId}/test`
- `POST /api/v1/platform/identity-providers/{providerId}/publish`
- `GET /api/v1/systems/{systemId}/sso/policies`
- `PATCH /api/v1/systems/{systemId}/sso/policies`
- `POST /api/v1/systems/{systemId}/sso/org-sync/precheck`
- `POST /api/v1/systems/{systemId}/sso/member-bindings/confirm`
- `POST /api/v1/systems/{systemId}/no-member-access-requests`
- `GET /api/v1/systems/{systemId}/no-member-access-requests`
- `POST /api/v1/systems/{systemId}/no-member-access-requests/{requestId}/approve`
- `POST /api/v1/systems/{systemId}/no-member-access-requests/{requestId}/reject`
- `POST /api/v1/secrets/{secretRefId}/rotation-jobs`
- `GET /api/v1/secrets/rotation-jobs/{jobId}`
- `GET /api/v1/systems/{systemId}/openapi/apps`
- `POST /api/v1/systems/{systemId}/openapi/apps`
- `PATCH /api/v1/systems/{systemId}/openapi/apps/{externalAppId}`
- `POST /api/v1/systems/{systemId}/openapi/apps/{externalAppId}/rotate-secret`
- `GET /api/v1/systems/{systemId}/openapi/call-logs`

请求/响应对象：

- `IdentityProviderVO`：`providerId/name/protocol/issuer/clientId/secretRef/certRef/domainWhitelist/jitPolicy/mfaPolicy/status`
- `IdentityProviderSecretRefVO`：`secretRefId/version/expiresAt/rotationStatus/lastUsedAt`
- `SystemSsoPolicyVO`：`enabledProviderIds/tenantDomains/orgMapping/employeeBinding/jitMemberPolicy/noMemberFeedback`
- `NoMemberAccessRequestVO`：`requestId/status/identityProvider/externalUserId/targetSystemId/tenantId/requestRole/approverId/approveResult/roleIds/dataScope/rejectReason/traceId`
- `SecretRotationJobVO`：新版本、双写验证、切换生效、观察期、旧版本停用、失败回滚、最近调用检查。
- `OpenApiAppVO`：`externalAppId/appName/status/openApiSecretRef/scopes/callbackUrl/rateLimit/lastUsedAt`
- `OpenApiSecretRefVO`：`secretRefId/version/rotationStatus/expiresAt/lastUsedAt`

NoMemberAccessRequest 生命周期：

- `SUBMITTED`：认证成功但无 `systemMemberId`，用户提交申请。
- `REVIEWING`：系统管理员处理中。
- `APPROVED`：已绑定员工、分配角色和数据范围，可重新系统切换。
- `REJECTED`：拒绝并记录原因。
- `CANCELED`：用户或系统取消。

约束：

- 审核通过前不得进入业务页，不得自动授予系统角色或数据范围。
- Secret 不在页面、日志、响应中回显明文，只返回 SecretRef、版本、到期和轮换状态。

分页/筛选/排序：

- 无成员申请支持 `status/identityProvider/tenantId/timeRange/keyword`。
- OpenAPI 调用日志支持 `externalAppId/scope/result/timeRange/traceId`。

错误码原则：

- 身份源测试失败、组织映射冲突、无成员申请重复、Secret 轮换失败、OpenAPI scope 越权、限流命中必须拆分。

幂等/后台任务：

- 组织同步预检、成员绑定确认、Secret 轮换、OpenAPI 密钥轮换走后台任务。
- OpenAPI 写请求必须支持幂等键。

审计：

- SSO 登录、发布、成员绑定、无成员申请审批、密钥轮换、OpenAPI 调用均记录审计。

## 4.10 agent

边界：平台模型授权、系统 Agent 策略、运行态对话、写入确认、审计。平台 Agent 与系统 Agent 的确认接口必须分离。

关键 endpoint 组：

- `GET /api/v1/platform/agent/model-authorizations`
- `POST /api/v1/platform/agent/model-authorizations`
- `PATCH /api/v1/platform/agent/model-authorizations/{authorizationId}`
- `POST /api/v1/platform/agent/sessions`
- `POST /api/v1/platform/agent/sessions/{sessionId}/messages`
- `POST /api/v1/platform/agent/confirm-platform-task`
- `GET /api/v1/systems/{systemId}/agent/policies`
- `POST /api/v1/systems/{systemId}/agent/policies`
- `PATCH /api/v1/systems/{systemId}/agent/policies/{policyId}`
- `POST /api/v1/systems/{systemId}/agent/policies/{policyId}/publish-check`
- `POST /api/v1/systems/{systemId}/agent/sessions`
- `POST /api/v1/systems/{systemId}/agent/sessions/{sessionId}/messages`
- `POST /api/v1/systems/{systemId}/agent/write-confirmations`
- `POST /api/v1/systems/{systemId}/agent/write-confirmations/{confirmationId}/confirm`
- `POST /api/v1/systems/{systemId}/agent/write-confirmations/{confirmationId}/reject`
- `GET /api/v1/systems/{systemId}/agent/audit-logs`

请求/响应对象：

- `ModelAuthorizationVO`：`authorizationId/modelProvider/modelName/modelCredentialRef/quota/dataOutboundPolicy/status/version`
- `AgentPolicyScopeVO`：`moduleScope/fieldScope/actionScope/dataScopeExpression/outboundLimit/desensitizePolicy/policyVersion`
- `AgentSessionVO`：`sessionId/layer/systemId/tenantId/systemMemberId/modelVersion/promptVersion/permissionSnapshotVersion`
- `AgentMessageRequest`：`content/attachments/contextObject/idempotencyKey`
- `PlatformAgentConfirmRequest`：只允许生成平台任务、平台消息、平台日志，不包含系统业务写入 payload。
- `SystemAgentWriteConfirmationVO`：`confirmationId/sourceSessionId/moduleId/recordId/fieldDiff/permissionCuts/approvalRequired/compensationPlan/auditLogId`

强约束：

- Agent 写入必须人工确认，并受权限、脱敏、数据范围、外发限制和审计控制。
- 平台 Agent 只处理平台授权、平台日志、平台任务、模型额度、系统健康和系统切换引导；不得直接打开或写入系统业务数据。
- 系统 Agent 必须在 `SystemSwitchContext/TenantSwitchContext/EffectivePermissionSnapshot` 下运行。
- 平台 Agent 确认接口与系统 Agent 写入确认接口分离，不能共用一个泛确认接口。

分页/筛选/排序：

- Agent 审计日志支持 `sessionId/modelProvider/moduleId/result/timeRange/traceId`。

错误码原则：

- 模型未授权、策略未发布、字段无权、需要人工确认、确认人无权、脱敏策略禁止外发、平台 Agent 越界写系统业务必须拆分。

幂等/后台任务：

- 对话消息可幂等。
- 写入确认、发布检查、批量生成日报草稿、复杂统计可返回后台任务。

审计：

- 记录原始对话、工具调用、提示词版本、模型授权版本、策略版本、权限快照、脱敏结果、确认人、确认时间和写入结果。

## 4.11 log/task/ops

边界：平台/系统登录日志、业务日志、后台任务、上线保障、体检、功能开关、容量配额、限流、备份恢复、归档恢复、多环境部署回滚、API 缓存策略。

关键 endpoint 组：

- `POST /api/v1/platform/logs/search`
- `GET /api/v1/platform/logs/{logId}`
- `POST /api/v1/systems/{systemId}/logs/search`
- `GET /api/v1/systems/{systemId}/logs/{logId}`
- `POST /api/v1/tasks/search`
- `GET /api/v1/tasks/{taskId}`
- `POST /api/v1/tasks/{taskId}/cancel`
- `POST /api/v1/tasks/{taskId}/retry`
- `POST /api/v1/platform/ops/health-check`
- `POST /api/v1/systems/{systemId}/ops/health-check`
- `GET /api/v1/platform/ops/feature-flags`
- `PATCH /api/v1/platform/ops/feature-flags/{flagId}`
- `GET /api/v1/platform/ops/quotas`
- `PATCH /api/v1/platform/ops/quotas/{quotaId}`
- `POST /api/v1/platform/ops/backups`
- `POST /api/v1/platform/ops/backups/{backupId}/restore-drill`
- `POST /api/v1/platform/ops/archive-restore-requests`
- `GET /api/v1/platform/ops/deployments`
- `POST /api/v1/platform/ops/deployments/{deploymentId}/rollback`
- `GET /api/v1/platform/ops/api-cache-policy`
- `PATCH /api/v1/platform/ops/api-cache-policy`

请求/响应对象：

- `AuditLogVO`：`logId/logType/layer/systemId/tenantId/operator/action/objectType/objectId/result/requestId/traceId/auditLogId/ip/device/fieldDiff/desensitizeResult/permissionSnapshot/failureReason`
- `TaskSearchRequest`：统一分页 + `bizType/status/createdBy/timeRange/traceId`
- `OpsHealthCheckVO`：服务连通性、配置检查、容量、最近备份、风险项、修复入口。
- `FeatureFlagVO`：`flagId/scope/status/rules/rollbackVersion/auditLogId`
- `QuotaVO`：`scope/quotaType/limit/used/warnThreshold`
- `BackupRestoreVO`：数据库、文件、配置、密钥备份边界、恢复演练结果。
- `ApiCachePolicyVO`：权限、字典、页面、字段、打印模板等缓存 key 和失效规则。

分页/筛选/排序：

- 日志按左侧日志类型树过滤：登录日志、业务日志、风险事件、导入导出/后台任务、AI Agent。
- 后台任务支持 `bizType/status/retryable/cancelable/createdBy/timeRange/traceId`。

错误码原则：

- 日志不可见、任务不可重试、任务不可取消、恢复演练失败、容量超限、功能开关规则冲突、回滚不可用必须拆分。

幂等/后台任务：

- 体检、备份、恢复演练、归档恢复、部署回滚都走后台任务。
- 任务取消/重试必须校验状态机和幂等。

审计：

- 运维动作、日志导出、任务重试/取消、缓存策略变更均记录审计。

## 5. API 冻结前需 PM 裁决的 P2 点

1. `SystemSwitchContext` 与 `EffectivePermissionSnapshot` 是同接口合并返回，还是系统切换返回上下文、权限快照单独懒加载。
2. `EffectivePermissionSnapshot` 版本号、缓存失效、显式拒绝优先级、审批节点字段权限与角色字段权限冲突规则。
3. 动态业务数据首期存储边界：`record/value/index/child_row/relation/history` 的表和接口是否一次冻结，哪些索引字段进入 MVP。
4. 动态字段筛选排序的首期支持范围：哪些字段类型可筛选、可排序、可聚合。
5. 流程节点快照与运行实例快照字段：条件、字段更新、外部 API、超时提醒的状态回写、重试、补偿和幂等键。
6. 消息跳转目标模型：平台目标、系统目标、业务对象、后台任务、日志、Agent 结果是否统一为 `MessageTarget`。
7. 平台 Agent 确认、系统 Agent 写入确认、普通后台任务确认是否拆三类接口；本提案建议平台 Agent 与系统 Agent 必须拆开。
8. Agent 原始对话、工具调用、提示词版本、模型授权版本、权限快照的保留期限、归档策略和导出权限。
9. `NoMemberAccessRequest` 审批通过后是否自动触发一次系统切换，还是只返回可切换状态由前端重新调用切换接口。
10. SecretRef 明文托管位置、加密边界、轮换状态机、双写验证窗口、最近调用检查和失败回滚策略。
11. 首期 SSO 协议支持范围：OIDC/SAML/OAuth2/LDAP/企业微信/钉钉哪些启用，未启用协议如何展示禁用态和错误反馈。
12. OpenAPI 幂等键、签名、限流维度、scope 粒度和调用日志保留期限。
13. 统一后台任务状态机：取消、重试、回滚、部分成功、结果文件生命周期和错误文件权限。
14. 导入导出失败明细是否全部结构化保存，还是大明细只保存文件并在日志中保留摘要。
15. 工作管理看板规则：多选字段作为列/泳道时的展开规则、停用字典项历史展示和排序。
16. `DailyReportAutoSourceRule` 的来源默认范围和是否允许管理员配置读取业务日志/审批记录。
17. 备份恢复、归档恢复、部署回滚是否首期只做契约和禁用态，还是进入 MVP 可执行范围。
18. 日志导出、任务明细、Agent 审计等大数据是否首期需要归档接口。

## 6. Backend 合并建议

- 先冻结通用协议、上下文、权限、后台任务和审计字段，再冻结各业务域 endpoint。
- API 合并时应把 `SystemSwitchContext/TenantSwitchContext/EffectivePermissionSnapshot` 作为最高优先级对象，避免后续前端临时拼上下文。
- `NoMemberAccessRequest`、`SecretRotationJob`、`OpenApiSecretRef`、`AgentPolicyScope`、`DailyReportAutoSourceRule` 必须进入正式 API 文档的一等对象，不宜藏在泛化 payload 中。
- 平台层和系统层的消息、待办、Agent、日志接口必须分开；涉及系统业务详情的动作必须先取得系统成员上下文。
- 本提案未发现阻断 API 草案合并的 P0/P1；上方 P2 点建议由 PM 在冻结 `docs/api/api.md` 前集中裁决。
