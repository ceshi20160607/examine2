# unexamine API 契约草案

> version: `0.1.0-frozen`  
> draft_at: `2026-06-23T15:10:00+08:00`  
> frozen_at: `2026-06-23T16:05:00+08:00`  
> approved: `true`  
> api_frozen: `true`  
> coding_allowed: `true`  

本文由 Phase 2 Contract 四份分片合并而来，并已通过 API review 与 contract-sync。当前 `gates.design_user_approved=true`、`gates.api_frozen=true`、`tasks_planned=true`，Build 可按 `docs/tasks/plan.md` 和 `TASK-*.md` 的输出路径开始；不得脱离任务单随意修改 backend/frontend/sql。

## 0. 合同状态

- 设计输入：`docs/design/prototype-brief.md`，版本 `1.7.24-clean-pre-coding-review-fixes`。
- 当前原型：`docs/design/prototypes/index.html`。
- 用户签字：`docs/design/user-approval.md approved=true`。
- 当前阶段：`phase=contract`，本文件是已冻结 API 契约。
- 冻结证据：`docs/evidence/api-review-2026-06-23.md`、`docs/evidence/contract-sync-2026-06-23.md`。
- 后续条件：Build 必须按 `docs/tasks/plan.md` 和 `TASK-*.md` 执行，每个任务完成后由 `task-accept` 验收，批次边界运行 `clean-build`。

## 1. 阻断问题

当前合并未发现 P0/P1 API 合同阻断问题。

若后续 API review 发现 P0/P1，应追加到本节并停止进入 API freeze。本文末尾“待复核项”仅保留 P2/P3，不阻塞进入 API review。

## 2. P2 裁决收敛

1. `SystemSwitchContext` 与 `TenantSwitchContext` 在切换接口中返回完整上下文，同时内嵌 `EffectivePermissionSnapshot` 摘要；权限详情可通过独立接口查询。
2. `EffectivePermissionSnapshot` 是一等对象，包含 `permissionVersion`、`sourceRoleIds`、`denyPolicyIds`、`field/action/dataScope`、`disabledReason`、`explain`。
3. 动态业务数据首期对象明确为 `record/value/index/child/relation/history/sequence`；实现可按任务分批，但 API 命名和边界一次冻结。
4. 消息目标统一为 `MessageTarget`，区分 `platform/system` scope；平台消息不能直跳系统业务详情，只能跳平台对象、系统切换或授权引导。
5. 后台任务统一状态机：`QUEUED/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELED/ROLLBACKING/ROLLED_BACK`。
6. Agent 确认接口拆分为 `platformAgentConfirm`、`systemAgentWriteConfirm`、`workAgentDraftConfirm`；三者全部审计，不能共用泛确认接口。
7. `SecretRef/SecretRotationJob`、`NoMemberAccessRequest`、`DailyReportAutoSourceRule`、`message_delivery_log` 都是一等对象。

## 3. 通用协议

### 3.1 URL 分层

- 账号认证：`/api/v1/auth/**`、`/api/v1/account/**`
- 平台层：`/api/v1/platform/**`
- 系统层：`/api/v1/systems/{systemId}/**`
- 租户层：`/api/v1/systems/{systemId}/tenants/{tenantId}/**`
- 运行态动态记录：`/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/**`
- OpenAPI：`/openapi/v1/apps/{externalAppCode}/**`

服务端必须校验账号、系统成员、租户、角色、权限快照和数据范围。前端路由参数只表示访问目标，不作为权限事实。

### 3.2 统一返回

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

失败响应必须返回分域 `code`、用户可读 `message`、`requestId`、`traceId`、可选 `errorFields`、`disabledReason`。写入、审批、导入导出、权限拒绝、密钥轮换、Agent 确认、OpenAPI 调用必须返回或记录 `auditLogId`。

### 3.3 分页、筛选、排序

统一分页请求：

```json
{
  "pageNo": 1,
  "pageSize": 20,
  "keyword": "车辆",
  "filters": [
    {"field": "status", "operator": "IN", "value": ["ACTIVE"]},
    {"field": "createdAt", "operator": "BETWEEN", "value": ["2026-06-01", "2026-06-23"]}
  ],
  "sorts": [
    {"field": "updatedAt", "direction": "DESC"}
  ]
}
```

统一分页响应：`records/pageNo/pageSize/total/hasNext`。首期筛选操作符限定为 `EQ/NE/LIKE/IN/BETWEEN/GT/GTE/LT/LTE/IS_NULL/IS_NOT_NULL`。动态字段筛选和排序必须经过字段发布版本、字段类型注册表、字段权限和索引支持校验。

### 3.4 幂等、审计与任务

- 写操作支持 `Idempotency-Key`；导入、导出、发布检查、流程模拟、密钥轮换、AI 确认必须要求幂等键。
- 统一后台任务对象为 `AsyncTask`，字段包含 `taskId/bizType/idempotencyKey/status/progress/retryable/cancelable/resultFile/errorFile/failureReason/partialSuccessCount/partialFailureCount/rollbackSupported/traceId/auditLogId/createdBy/createdAt`。
- 任务状态机固定为 `QUEUED/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELED/ROLLBACKING/ROLLED_BACK`。

### 3.5 错误码域

错误码按域拆分：`AUTH_`、`PLATFORM_`、`TENANT_`、`MEMBER_`、`PERMISSION_`、`MODULE_`、`FIELD_`、`DICT_`、`RECORD_`、`FLOW_`、`TODO_`、`MESSAGE_`、`WORK_`、`SSO_`、`SECRET_`、`OPENAPI_`、`AGENT_`、`LOG_`、`TASK_`、`OPS_`。

## 4. 认证账户

### Endpoint 组

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

### 关键对象

- `LoginRequest`：`loginName/password/mfaCode/loginTarget/systemCode/tenantCode`
- `LoginResponse`：`accessToken/refreshToken/account/profile/defaultLanding/ssoBindingSummary`
- `RegisterWithSystemRequest`：`accountName/mobile/email/password/systemName/systemCode/tenantMode/templateCode`
- `RegisterWithSystemResponse`：`accountId/systemId/systemMemberId/systemSuperAdminRoleId/initGuideSteps`
- `LoginAudit`：`identityProvider/externalUserId/authMethod/mfaResult/ip/device/requestId/traceId/result/failureReason`

### 权限、审计、幂等、分页

登录、登出、刷新失败、密码修改、注册建系统都记录登录或安全审计。注册建系统必须支持幂等，防止重复创建系统。登录日志分页支持 `loginResult/authMethod/identityProvider/ip/timeRange/keyword`。

## 5. 平台 / 系统 / 租户 / 成员

### 5.1 平台系统生命周期

Endpoint 组：

- `GET /api/v1/platform/systems`
- `POST /api/v1/platform/systems`
- `GET /api/v1/platform/systems/{systemId}`
- `PATCH /api/v1/platform/systems/{systemId}`
- `POST /api/v1/platform/systems/{systemId}/enable`
- `POST /api/v1/platform/systems/{systemId}/disable`
- `DELETE /api/v1/platform/systems/{systemId}`
- `POST /api/v1/platform/systems/{systemId}/restore`
- `GET /api/v1/platform/health`

关键对象：`SystemCreateRequest`、`SystemVO`、`SystemLifecycleRequest`、`PlatformHealthVO`。

系统列表支持 `status/tenantMode/owner/keyword/recentAccessRange` 筛选。创建、删除、恢复、体检、备份恢复返回同步结果或 `AsyncTask`。生命周期操作必须记录原因、影响范围、`traceId/auditLogId`。

### 5.2 系统切换上下文

Endpoint 组：

- `GET /api/v1/platform/system-switch/options`
- `POST /api/v1/platform/system-switch`
- `GET /api/v1/context/current-system`

`SystemSwitchContext`：

```json
{
  "accountId": "acc_001",
  "accountMemberBindingId": "amb_001",
  "systemId": "sys_vehicle",
  "systemCode": "vehicle",
  "systemName": "车辆资产管理系统",
  "tenantId": "tenant_default",
  "systemMemberId": "sm_001",
  "effectiveRoleIds": ["role_operator"],
  "dataScope": {"type": "DEPARTMENT", "expression": "dept in currentMember.departments"},
  "permissionSnapshotSummary": {
    "snapshotId": "eps_001",
    "permissionVersion": "perm_20260623_001",
    "disabledReason": null
  },
  "messageTodoScope": {"scope": "system", "systemId": "sys_vehicle", "tenantId": "tenant_default"},
  "expiresAt": "2026-06-23T17:10:00+08:00"
}
```

约束：没有 `systemMemberId` 时不得返回业务上下文，只能返回 `NoMemberAccessRequest` 入口。平台内置超管不能绕过系统成员映射访问业务数据。切换成功后必须返回足够信息供前端重绘业务壳：模块分组、左侧模块、待办消息计数、字段权限摘要。

### 5.3 租户与成员

Endpoint 组：

- `GET /api/v1/systems/{systemId}/tenants`
- `POST /api/v1/systems/{systemId}/tenants`
- `PATCH /api/v1/systems/{systemId}/tenants/{tenantId}`
- `POST /api/v1/systems/{systemId}/tenant-switch`
- `GET /api/v1/systems/{systemId}/org/departments`
- `POST /api/v1/systems/{systemId}/org/departments`
- `GET /api/v1/systems/{systemId}/members`
- `POST /api/v1/systems/{systemId}/members`
- `PATCH /api/v1/systems/{systemId}/members/{systemMemberId}`
- `POST /api/v1/systems/{systemId}/members/{systemMemberId}/bind-account`
- `GET /api/v1/systems/{systemId}/member-bindings`

`TenantSwitchContext`：`systemId/tenantId/tenantName/tenantRoleIds/tenantDataScope/isTenantSwitchable/disabledReason/permissionSnapshotSummary`。单租户系统隐藏租户切换入口；多租户切换后刷新菜单、数据范围、待办、消息和字段权限。

成员列表支持 `departmentId/status/roleId/keyword/bindingStatus`。成员导入、组织同步、成员绑定预检走后台任务。成员增删改、账号绑定、租户切换、组织同步都记录审计。

## 6. 角色权限

### Endpoint 组

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

### 关键对象

- `RoleSaveRequest`：`roleName/roleCode/roleType/status/description`
- `RolePermissionSaveRequest`：`menuPermissions/modulePermissions/actionPermissions/fieldPermissions/dataScopeRules/denyPolicies`
- `PermissionPreviewRequest`：`systemMemberId/tenantId/roleIds/moduleId/recordId/actionCode`
- `PermissionDecisionVO`：`allowed/disabledReason/missingPermissions/dataScopeExpression/fieldMaskRules/explain`
- `EffectivePermissionSnapshot`：

```json
{
  "snapshotId": "eps_001",
  "permissionVersion": "perm_20260623_001",
  "systemMemberId": "sm_001",
  "tenantId": "tenant_default",
  "sourceRoleIds": ["role_operator", "role_auditor"],
  "denyPolicyIds": ["deny_export_secret"],
  "field": {"vehicle.licenseNo": "READABLE", "vehicle.secretNote": "MASKED"},
  "action": {"record.create": true, "record.export": false},
  "dataScope": {"type": "DEPARTMENT", "expression": "dept in currentMember.departments"},
  "disabledReason": null,
  "explain": [
    {"type": "ROLE_ALLOW", "roleId": "role_operator", "target": "record.create"},
    {"type": "DENY_POLICY", "policyId": "deny_export_secret", "target": "record.export"}
  ]
}
```

### 权限、审计、幂等、分页

多角色默认允许并集，显式拒绝优先。字段权限、动作权限、数据范围和审批节点临时字段权限必须由服务端解释。权限变更生成新 `permissionVersion` 并清缓存。角色列表支持 `roleType/status/keyword`。权限发布、大规模权限重算可返回 `AsyncTask`。权限变更记录修改前后、影响成员、版本和 `auditLogId`。

## 7. 模块配置

### Endpoint 组

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

### 关键对象

- `ModuleGroupVO`：`groupId/name/sort/visibleRoleIds/publishStatus/publishedVersion`
- `ModuleVO`：`moduleId/groupId/moduleCode/name/status/publishStatus/currentVersion`
- `FieldDefinitionVO`：`fieldId/fieldCode/name/fieldType/storageType/filterOperators/sortable/required/maskRule/importExportRule`
- `DynamicListSchema`：`moduleId/moduleCode/sceneId/columns/filters/sorters/page/rowClickTarget/batchActions/toolbarActions/importExportConfig/emptyState/permissionSnapshotId`
- `PublishCheckResultVO`：`passed/failureItems/warningItems/impactRefs/traceId`

### 权限、审计、幂等、分页

模块组只做运行态导航、排序、可见角色和发布关系，不作为业务数据父级。模块按 `groupId/status/publishStatus/keyword` 筛选；字段按 `fieldType/status/keyword` 筛选；字典项支持普通、树形、级联、状态、标签、字段选项类型。发布检查、发布、回滚支持幂等并记录配置版本、影响范围、`traceId/auditLogId`。

## 8. 动态业务运行态

### Endpoint 组

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

### 首期对象边界

- `record`：业务记录主对象，归属 `systemId/tenantId/moduleId`。
- `value`：字段值，支持主表字段和子表字段。
- `index`：列表筛选、排序、检索索引值。
- `child`：子表行。
- `relation`：关联数据关系。
- `history`：字段前后值、来源、权限快照、traceId。
- `sequence`：自动编号序号，不允许“查最大值 + 1”。

### 关键对象

- `RecordSearchRequest`：统一分页 + `sceneCode/fieldFilters/keyword`
- `BusinessRecordRow`：`recordId/title/summary/fields/actions/disabledReasons/permissionSnapshotVersion`
- `BusinessDetailView`：`summary/baseFields/childRows/relations/attachments/printRecords/operationLogs/approvalSidebar/fieldMaskResults`
- `RecordSaveRequest`：`fieldValues/childRows/attachments/draftId`
- `ImportPrecheckRequest`：`fileId/templateCode/fieldMapping/duplicateStrategy`
- `ExportRequest`：`scope/selectedRecordIds/fields/fileFormat/desensitizeMode`

### 权限、审计、幂等、分页

列表必须服务端分页。动态字段筛选和排序必须经过字段类型、发布版本、字段权限和索引支持校验。保存、更新、业务动作支持幂等；导入、导出、大批量删除、批量转移、批量打印走 `AsyncTask`。所有记录写入记录字段前后值、来源 Web/OpenAPI/Agent、权限快照和脱敏结果。

## 9. 流程待办消息

### 9.1 流程与审批

Endpoint 组：

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

关键对象：`FlowDefinitionVO`、`FlowNodeConfigVO`、`WorkflowInstanceSnapshot`、`ApprovalTaskSnapshot`、`ApprovalActionRequest`。审批、条件、字段更新、外部 API、超时提醒等节点必须有专属属性。审批处理必须幂等；拒绝、撤回、终止必须有原因。

### 9.2 待办

Endpoint 组：

- `POST /api/v1/platform/todos/search`
- `POST /api/v1/systems/{systemId}/todos/search`
- `POST /api/v1/systems/{systemId}/todos/{todoId}/actions/{actionCode}`

`TodoRowView`：`todoId/scope/type/title/sourceName/moduleCode/objectTitle/assigneeId/dueAt/status/priority/target/primaryAction/actionPermissions/traceId`。

平台待办和系统待办都按左侧类型树 + 右侧列表组织。系统待办只在当前系统成员上下文内跳业务对象。

### 9.3 消息、模板与投递

Endpoint 组：

- `POST /api/v1/platform/messages/search`
- `POST /api/v1/systems/{systemId}/messages/search`
- `POST /api/v1/systems/{systemId}/messages/mark-read`
- `POST /api/v1/systems/{systemId}/messages/archive`
- `GET /api/v1/systems/{systemId}/notification-templates`
- `POST /api/v1/systems/{systemId}/notification-templates`
- `GET /api/v1/systems/{systemId}/message-delivery-logs`

`MessageTarget`：

```json
{
  "scope": "system",
  "targetType": "business_record",
  "targetId": "rec_001",
  "targetSystemId": "sys_vehicle",
  "targetTenantId": "tenant_default",
  "requiresSystemSwitch": false,
  "fallbackAction": null
}
```

平台消息 `scope=platform` 时，`targetType` 只能为 `system_switch/platform_auth/platform_task/platform_log/agent_result/audit_log` 等平台目标，不得直跳 `business_record`。系统消息必须在当前 `SystemSwitchContext` 和 `systemMemberId` 下跳业务对象。

`NotificationTemplate` 包含 `templateCode/scope/templateType/variables/channels/targetRule/dedupeKey/readReceiptRequired/quietPolicy/retryPolicy`。`message_delivery_log` 记录 `messageId/channel/status/failureReason/retryCount/readReceipt/doNotDisturb/archiveStatus/traceId`。

消息支持 `systemId/tenantId/templateCode/type/readStatus/archiveStatus/timeRange/keyword` 筛选；全部已读、归档、加载更多必须有状态结果。模板变更、投递失败、消息补发记录审计。

## 10. 工作管理

### Endpoint 组

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

### 关键对象

- `WorkDashboardVO`：项目概览、今日预警、月历、我的任务、日报提醒。
- `WorkTaskVO`：`taskId/taskType/title/projectId/assignee/collaborators/status/tags/progress/dueAt/relatedObject`
- `KanbanQueryRequest`：`taskType/columnFieldId/swimlaneFieldId/groupFieldId/pageCursor`
- `DailyReportVO`：`reportId/date/status/content/sourceSummary/submitter`
- `DailyReportAutoSourceRule`：`sourceTypes/taskScope/todoScope/messageScope/logScope/approvalScope/permissionPolicy/manualConfirmRequired`

### 权限、审计、幂等、分页

工作管理固定四标签：仪表盘、项目任务、普通任务、日报。项目任务和普通任务列表分页；看板使用 cursor 加载更多。看板列、泳道、分组来自已发布字段和字典。日报只展示我的日报；自动草稿只读取当前成员有权限的任务、待办、消息、业务日志和审批记录，提交前必须人工确认。任务状态变更、日报提交、自动草稿来源和确认都记录权限快照。

## 11. SSO / Secret / OpenAPI

### Endpoint 组

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

### 一等对象

- `IdentityProvider`：`providerId/name/protocol/issuer/clientId/secretRef/certRef/domainWhitelist/jitPolicy/mfaPolicy/status`
- `SecretRef`：`secretRefId/refType/version/expiresAt/rotationStatus/lastUsedAt/displayName`
- `IdentityProviderSecretRef`、`OpenApiSecretRef`、`ModelCredentialRef` 均继承 SecretRef 语义。
- `SecretRotationJob`：新版本创建、双写验证、切换生效、观察期、旧版本停用、失败回滚、最近调用检查、`traceId/auditLogId`。
- `SystemSsoPolicy`：`enabledProviderIds/tenantDomains/orgMapping/employeeBinding/jitMemberPolicy/noMemberFeedback`
- `NoMemberAccessRequest`：`requestId/status/identityProvider/externalUserId/targetSystemId/tenantId/requestRole/approverId/approveResult/roleIds/dataScope/rejectReason/traceId`
- `OpenApiApp`：`externalAppId/appName/status/openApiSecretRef/scopes/callbackUrl/rateLimit/lastUsedAt`

### 状态与约束

`NoMemberAccessRequest` 生命周期：`SUBMITTED/REVIEWING/APPROVED/REJECTED/CANCELED`。审批通过并绑定员工、分配角色和数据范围前，不得进入业务页。

Secret 不在页面、日志、响应中回显明文，只返回引用、版本、到期、轮换状态和最近使用。组织同步预检、成员绑定确认、Secret 轮换、OpenAPI 密钥轮换走后台任务。OpenAPI 写请求必须支持幂等键，调用日志支持 `externalAppId/scope/result/timeRange/traceId`。

## 12. AI Agent

### Endpoint 组

- `GET /api/v1/platform/agent/model-authorizations`
- `POST /api/v1/platform/agent/model-authorizations`
- `PATCH /api/v1/platform/agent/model-authorizations/{authorizationId}`
- `POST /api/v1/platform/agent/sessions`
- `POST /api/v1/platform/agent/sessions/{sessionId}/messages`
- `POST /api/v1/platform/agent/platform-agent-confirm`
- `GET /api/v1/systems/{systemId}/agent/policies`
- `POST /api/v1/systems/{systemId}/agent/policies`
- `PATCH /api/v1/systems/{systemId}/agent/policies/{policyId}`
- `POST /api/v1/systems/{systemId}/agent/policies/{policyId}/publish-check`
- `POST /api/v1/systems/{systemId}/agent/sessions`
- `POST /api/v1/systems/{systemId}/agent/sessions/{sessionId}/messages`
- `POST /api/v1/systems/{systemId}/agent/system-agent-write-confirmations`
- `POST /api/v1/systems/{systemId}/agent/system-agent-write-confirmations/{confirmationId}/confirm`
- `POST /api/v1/systems/{systemId}/agent/system-agent-write-confirmations/{confirmationId}/reject`
- `POST /api/v1/systems/{systemId}/work/agent/work-agent-draft-confirm`
- `GET /api/v1/systems/{systemId}/agent/audit-logs`

### 关键对象

- `ModelAuthorization`：`authorizationId/modelProvider/modelName/modelCredentialRef/quota/dataOutboundPolicy/status/version`
- `AgentPolicyScope`：`moduleScope/fieldScope/actionScope/dataScopeExpression/outboundLimit/desensitizePolicy/policyVersion`
- `AgentSession`：`sessionId/scope/systemId/tenantId/systemMemberId/modelVersion/promptVersion/permissionSnapshotVersion`
- `platformAgentConfirm`：只允许生成平台任务、平台消息、平台日志。
- `systemAgentWriteConfirm`：业务字段差异、权限裁剪、审批要求、失败补偿、业务日志。
- `workAgentDraftConfirm`：任务草稿、日报草稿、来源快照、人工确认。

### 权限、审计、幂等、分页

平台 Agent 只处理平台授权、日志、任务、模型额度、系统健康和系统切换引导，不得打开或写入系统业务数据。系统 Agent 必须在 `SystemSwitchContext/TenantSwitchContext/EffectivePermissionSnapshot` 下运行。全部确认接口都必须人工确认、幂等、记录原始对话、工具调用、提示词版本、模型授权版本、策略版本、权限快照、脱敏结果、确认人、确认时间和写入结果。

## 13. 日志 / 后台任务 / 运维

### Endpoint 组

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

### 关键对象

- `AuditLog`：`logId/logType/scope/systemId/tenantId/operator/action/objectType/objectId/result/requestId/traceId/auditLogId/ip/device/fieldDiff/desensitizeResult/permissionSnapshot/failureReason`
- `AsyncTask`：统一后台任务对象，状态机见通用协议。
- `OpsHealthCheck`：服务连通性、配置检查、容量、最近备份、风险项、修复入口。
- `FeatureFlag`：`flagId/scope/status/rules/rollbackVersion/auditLogId`
- `Quota`：`scope/quotaType/limit/used/warnThreshold`
- `BackupRestore`：数据库、文件、配置、密钥备份边界、恢复演练结果。
- `ApiCachePolicy`：权限、字典、页面、字段、打印模板缓存 key 和失效规则。

### 权限、审计、幂等、分页

日志按左侧日志类型树过滤：登录日志、业务日志、风险事件、导入导出/后台任务、AI Agent、OpenAPI。后台任务支持 `bizType/status/retryable/cancelable/createdBy/timeRange/traceId`。体检、备份、恢复演练、归档恢复、部署回滚走后台任务。日志导出、任务重试/取消、缓存策略变更、运维动作均记录审计。

## 14. 数据模型索引

| 对象 | 归属 | 关键字段 | 说明 |
|---|---|---|---|
| PlatformAccount | platform | accountId、loginName、mobile、email、status | 统一平台账号 |
| System | platform | systemId、systemCode、status、tenantMode、ownerAccountId | 系统生命周期 |
| AccountMemberBinding | system | accountMemberBindingId、accountId、systemId、tenantId、systemMemberId | 平台账号与系统成员绑定 |
| SystemSwitchContext | session/context | systemId、tenantId、systemMemberId、permissionSnapshotSummary | 系统切换完整上下文 |
| TenantSwitchContext | session/context | tenantId、tenantRoleIds、tenantDataScope、isTenantSwitchable | 租户切换上下文 |
| EffectivePermissionSnapshot | permission | permissionVersion、sourceRoleIds、denyPolicyIds、field/action/dataScope、explain | 有效权限快照 |
| ModuleGroup | system config | groupId、sort、visibleRoleIds、publishedVersion | 运行态导航分组 |
| Module | system config | moduleId、moduleCode、groupId、currentVersion | 业务模块 |
| FieldDefinition | system config | fieldCode、fieldType、storageType、filterOperators | 动态字段 |
| Dictionary | system config | dictType、items、color、icon、semantic | 字典类型与项 |
| DynamicRecord | runtime | systemId、tenantId、moduleId、recordId | 动态业务记录主对象 |
| DynamicValue | runtime | recordId、fieldId、rowId、value | 字段值 |
| DynamicIndex | runtime | recordId、fieldId、indexValue | 筛选排序索引 |
| DynamicChild | runtime | recordId、childModuleId、rowId | 子表行 |
| DynamicRelation | runtime | sourceRecordId、targetRecordId、relationType | 关联关系 |
| DynamicHistory | runtime audit | recordId、fieldDiff、permissionSnapshotId、traceId | 记录历史 |
| DynamicSequence | runtime | systemId、tenantId、moduleId、sequenceType、currentNo | 自动编号 |
| WorkflowDefinition / Snapshot | workflow | flowId、version、nodes、edges | 流程配置与快照 |
| Todo | workflow | todoId、scope、type、status、target | 待办 |
| MessageTarget | message | scope、targetType、targetId、requiresSystemSwitch | 消息跳转目标 |
| message_delivery_log | message | messageId、channel、status、retryCount、traceId | 消息投递日志 |
| WorkTask | work | taskId、taskType、status、assignee、relatedObject | 项目/普通任务 |
| DailyReportAutoSourceRule | work | sourceTypes、permissionPolicy、manualConfirmRequired | 日报自动草稿来源规则 |
| SecretRef | secret | secretRefId、version、rotationStatus、expiresAt | 密钥引用 |
| SecretRotationJob | secret | jobId、status、newVersion、rollbackPlan | 密钥轮换任务 |
| NoMemberAccessRequest | sso | requestId、status、identityProvider、targetSystemId、roleIds、dataScope | 无成员映射申请 |
| OpenApiApp | openapi | externalAppId、openApiSecretRef、scopes、rateLimit | 对外应用 |
| AgentPolicyScope | agent | moduleScope、fieldScope、actionScope、dataScopeExpression | 系统 Agent 策略范围 |
| AgentConfirmation | agent | confirmationId、confirmType、status、auditLogId | Agent 确认 |
| AuditLog | log | logType、scope、operator、objectId、traceId | 登录/业务/风险日志 |
| AsyncTask | task | taskId、bizType、status、progress、resultFile、errorFile | 统一后台任务 |

## 15. 测试契约

### 15.1 统一断言

冻结前所有域都必须可测试：

- 成功与失败响应均有 `requestId/traceId`。
- 写入、审批、密钥、导入导出、Agent、OpenAPI、权限拒绝有 `auditLogId` 或可查询审计记录。
- 权限相关响应有关联 `permissionVersion`。
- 异步动作返回 `taskId`，任务可查询进度、结果文件、错误文件、失败原因和回滚边界。
- 禁用动作返回 `disabledReason`，且后端再次兜底拒绝。
- 失败响应使用分域错误码。

### 15.2 首期 E2E 主剧本

1. `platform_admin_root` 登录平台工作台，验证平台后台入口、登录日志和权限菜单。
2. 创建车辆资产管理系统，返回系统记录、初始化状态、`auditLogId`。
3. 完成系统信息、组织、角色、模块字段、流程字典、邀请成员、发布业务首页。
4. 开通成员 `che`，绑定系统员工、角色、数据范围和 `accountMemberBindingId`。
5. 配置车辆管理模块组、车辆档案字段、列表、权限、审批流程、导入导出。
6. `che` 登录并进入车系统，生成 `SystemSwitchContext`，无后台入口。
7. 单租户隐藏租户切换；多租户返回 `TenantSwitchContext` 并刷新壳。
8. `che` 打开车辆列表，执行搜索、筛选、排序、分页、列设置。
9. `che` 新增车辆、保存草稿、提交并查看详情。
10. 发起审批、处理待办、通过或驳回，重复提交幂等。
11. 执行导入导出，查看消息和任务结果，验证无权限拦截。

### 15.3 关键反向用例

- `che` 直接请求系统后台接口，返回无权限错误和审计。
- `platform_admin_root` 未经系统切换直接访问系统业务数据，拒绝访问。
- SSO 登录成功但无 `systemMemberId`，生成 `NoMemberAccessRequest`。
- 导出无权限字段，字段脱敏或剔除，并记录权限版本。
- 审批任务重复提交，不重复推进流程。
- 平台消息尝试跳系统业务详情，返回系统切换或授权引导。
- 密钥轮换失败，`SecretRotationJob` 进入失败或回滚状态，日志不回显明文。
- 后台任务部分成功，`status=PARTIAL_SUCCESS`，可下载结果文件和错误文件。

## 16. 待复核项

以下仅为 P2/P3，不阻塞进入 API review。

### P2

- `permissionVersion` 生成规则、缓存 key、失效时机和版本不一致反馈。
- 审批节点字段权限与角色字段权限冲突时的优先级细节。
- 动态字段首期可筛选、可排序、可聚合的字段类型清单。
- 流程外部 API 节点的重试、补偿、幂等键和状态回写细节。
- `NoMemberAccessRequest` 审批通过后是否自动触发一次系统切换，或仅返回可切换状态。
- Secret 明文托管位置、加密边界、双写验证窗口、最近调用检查和备份恢复边界。
- 首期 SSO 协议启用范围，以及未启用协议的禁用态和错误码。
- OpenAPI 签名、限流维度、scope 粒度和调用日志保留期限。
- 导入导出大明细全部结构化保存，还是大文件保存、日志保留摘要。
- 工作看板多选字段作为列/泳道时的展开规则和停用字典项历史展示。
- `DailyReportAutoSourceRule` 默认来源范围，以及是否允许读取业务日志和审批记录。
- AI Agent 原始对话、工具调用、外发数据快照、提示词版本、模型授权版本的保留期限与归档策略。

### P3

- 备份恢复、归档恢复、部署回滚首期是否仅提供契约和禁用态，还是进入 MVP 可执行范围。
- 日志导出、任务明细、Agent 审计等大数据归档接口的首期优先级。
- 短信、Webhook、部分外部模型供应商的首期禁用态文案和错误提示细节。

## 17. Review 结论建议

本契约已覆盖四份分片的核心对象、endpoint 组、请求/响应对象、权限、审计、幂等、分页、筛选和测试契约。API review 与 contract-sync 已通过，当前未发现 P0/P1 阻断。Build 阶段只能按 `docs/tasks/plan.md` 和 `TASK-*.md` 的任务边界实施。
