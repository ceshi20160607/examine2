# Frontend API Contract Map

> source: `docs/api/api.md`
> api version: `0.1.0-frozen`
> status: contract-sync artifact
> scope: 前端契约映射，不包含页面实现、请求客户端或路由实现。

## 1. Contract Artifacts

- API source: `docs/api/api.md`
- Type contract: `frontend/src/api/types.ts`
- This map: `frontend/docs/api-contract-map.md`

## 2. Shell And Context

| UI area | API group | Contract objects |
|---|---|---|
| 登录、注册、找回密码 | `/api/v1/auth/**` | `ApiResponse`, `LoginResponse` should extend base response pattern |
| 个人信息、登录日志 | `/api/v1/account/**` | `AuditLog`, `PageRequest`, `PageResult` |
| 系统切换 | `/api/v1/platform/system-switch` | `SystemSwitchContext`, `NoMemberAccessRequest` |
| 租户切换 | `/api/v1/systems/{systemId}/tenant-switch` | `TenantSwitchContext` |
| 权限快照 | `/api/v1/systems/{systemId}/permissions/effective` | `EffectivePermissionSnapshot`, `PermissionDecisionVO` |

## 3. Platform Admin

| UI area | API group | Contract objects |
|---|---|---|
| 平台信息、系统生命周期 | `/api/v1/platform/systems/**` | `AsyncTask`, `AuditLog` |
| 平台组织、成员、角色 | `/api/v1/platform/**` and role APIs | `PageRequest`, `PageResult`, `PermissionDecisionVO` |
| 平台 SSO | `/api/v1/platform/identity-providers/**` | `IdentityProvider`, `SecretRef`, `SecretRotationJob` |
| 平台 Agent | `/api/v1/platform/agent/**` | `AgentSession`, `AgentConfirmation` |
| 平台日志和运维 | `/api/v1/platform/logs/**`, `/api/v1/platform/ops/**` | `AuditLog`, `AsyncTask` |

## 4. System Admin

| UI area | API group | Contract objects |
|---|---|---|
| 组织架构、成员绑定 | `/api/v1/systems/{systemId}/org/**`, `/members/**` | `NoMemberAccessRequest`, `PageRequest` |
| 角色权限 | `/api/v1/systems/{systemId}/roles/**` | `EffectivePermissionSnapshot`, `PermissionDecisionVO` |
| 模块组、模块、字段 | `/api/v1/systems/{systemId}/module-groups/**`, `/modules/**` | `ModuleGroupVO`, `ModuleVO`, `FieldDefinitionVO` |
| 字典、字段选项 | `/api/v1/systems/{systemId}/dict-types/**` | `FieldDefinitionVO` option and dictionary fields |
| 流程配置 | `/api/v1/systems/{systemId}/flows/**` | `AsyncTask`, `ActionContract` |
| 系统 SSO | `/api/v1/systems/{systemId}/sso/**` | `IdentityProvider`, `SecretRef`, `NoMemberAccessRequest` |
| 系统 Agent 策略 | `/api/v1/systems/{systemId}/agent/policies/**` | `AgentPolicyScope` |
| 日志管理 | `/api/v1/systems/{systemId}/logs/**` | `AuditLog`, `PageRequest`, `PageResult` |

## 5. System Runtime

| UI area | API group | Contract objects |
|---|---|---|
| 业务模块列表 | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/search` | `DynamicListSchema`, `BusinessRecordRow`, `PageRequest`, `PageResult` |
| 业务详情抽屉 | `/records/{recordId}` | `BusinessDetailView`, `ApprovalSidebar`, `OperationRecord` |
| 新建、编辑、草稿 | `/records`, `/drafts` | `ActionContract`, `ApiResponse` |
| 导入、导出 | `/imports/**`, `/exports` | `AsyncTask`, `ImportExportConfig` |
| 待办 | `/todos/search`, `/todos/{todoId}/actions/{actionCode}` | `TodoRowView`, `MessageTarget`, `ActionContract` |
| 消息 | `/messages/search`, `/messages/mark-read`, `/messages/archive` | `MessageTarget`, `NotificationTemplate`, `MessageDeliveryLog` |
| 工作管理 | `/work/**` | `WorkDashboardVO`, `WorkTaskVO`, `KanbanQueryRequest`, `DailyReportVO`, `DailyReportAutoSourceRule` |
| 系统 Agent | `/agent/**`, `/work/agent/**` | `AgentSession`, `AgentConfirmation`, `AgentPolicyScope` |

## 6. Sync Rules

- Frontend must not build permission context locally; use `SystemSwitchContext`, `TenantSwitchContext`, and `EffectivePermissionSnapshot`.
- Platform message targets must not open system business detail directly.
- Dynamic runtime rows open detail by row click; action buttons only represent explicit actions returned by API.
- Import, export, publish checks, simulations, secret rotation, and AI confirmations use `AsyncTask` when long running.
- Disabled buttons and blocked row actions must show `disabledReason` from API.
- Secret values must never appear in response models, logs, exports, screenshots, or local state dumps.
