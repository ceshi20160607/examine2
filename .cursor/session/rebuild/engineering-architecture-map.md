# Engineering Architecture Map

## Status

Task: `REQ-R0-002 Engineering Architecture Map`

Status: leader draft complete

This file maps product domains to engineering ownership. It is the bridge between requirements and future implementation tasks.

Scope note: this is a requirements-rebuild artifact. It does not authorize backend, frontend, or SQL implementation by itself.

## Source Files Read

- `.cursor/session/rebuild/engineering-architecture-map.md`
- `.cursor/session/rebuild/product-boundary-contract.md`
- `.cursor/architecture/backend-structure.md`
- `.cursor/architecture/ui-system.md`
- `.cursor/session/rebuild/ui-system-interaction-contract.md`

## Mapping Rules

- Every top-level product domain runs inside either platform context or system member context.
- Platform identity cannot directly operate system business data; entering a system must establish system member context.
- `examine-web` is only startup, web config, and dependency assembly. It must not become a business feature module.
- Owning backend modules write their own source tables. Cross-domain views such as workbench, todo, and message may read or materialize read models, but must not silently mutate owner data.
- Frontend shell/route keys below are architecture anchors for future tasks, not an implementation commitment to a specific router library.
- All future implementation tasks must name backend module, frontend shell/route, table group, permission boundary, state/readback, side effects, and evidence.

## Backend Module Map

| Product Domain | Backend Module | Table Group | Manage Responsibility |
|---|---|---|---|
| 平台账号 / 系统 / 租户 / 平台后台 | `examine-plat` | `un_plat_*` | platform accounts, systems, tenants, platform roles, platform/system admin basics, dashboard config ownership where it is platform/system metadata |
| 业务模块 / 字段 / 页面 / 运行态 | `examine-module` | `un_module_*` | module groups, modules, fields, actions, pages, print templates, runtime records, module permissions, module import/export orchestration |
| Flow / Flow 管理 | `examine-flow` | `un_flow_*` | flow definitions, canvas, nodes, publish checks, runtime instances, approval task source records |
| 应用 / 对外授权阀门 | `examine-app` | `un_openapi_*` | application credentials, scopes, gateway authorization, callbacks, call logs, exposure links for Flow/system services |
| 文件 / 附件 / 导入导出文件 | `examine-upload` | `un_upload_*` | upload, file references, preview/download permissions, attachment readback |
| 通用上下文 / 审计 / 返回 / 异常 | `examine-core` | `un_sys_*`, `un_audit_*` | API result, platform/system context, audit, exception, shared base objects, local event/outbox if adopted |
| 工作 | recommended new `examine-work` | proposed `un_work_*` | work statistics, project tasks, normal tasks, daily reports, work config; must not be hidden under Flow or generic module CRUD |
| 待办 | recommended new `examine-todo` | proposed `un_todo_*` | unified actionable todo/reminder center; source modules create/update items through a contract; Flow keeps its own approval source records |
| 消息 | recommended new `examine-message` | proposed `un_message_*` | current-user message stream, delivery/read state, source references; not duplicated card buttons in every source domain |
| AI | recommended new `examine-ai` | proposed `un_ai_*` | AI sessions, model/policy config, permission/desensitization orchestration, AI audit metadata; writes delegate to owner modules |
| 启动与装配 | `examine-web` | none | application startup, web config, dependency assembly only |
| 代码生成 | `examine-generator` | none | generate base code only, not runtime web feature |

## Frontend Shell Map

| Shell | Purpose | Must Contain |
|---|---|---|
| Platform workbench | platform context runtime | 工作台, Flow, 应用, 工作, AI, 待办, 消息, 后台入口, 个人信息 |
| Platform admin | platform management | platform basic info, organization/accounts, roles, dashboard config, global config, logs |
| System runtime | system business usage | system 工作台, business module group tabs, module lists, details, Flow, 应用, 工作, AI, 待办, 消息, 个人信息 |
| System admin | system management | system info, organization, roles, modules, flow, applications, dashboards, dictionaries, work config, app config, AI config, data sources, logs |

## Product Domain Engineering Map

| Product Domain | Backend Ownership | Frontend Shell / Route Anchor | Table Group | Permission Boundary | State / Readback | Side Effects | Evidence Required |
|---|---|---|---|---|---|---|---|
| 工作台 | Aggregated read surface. Config ownership is `examine-plat`; metric contributors come from owner modules. | `/platform/workbench`, `/systems/:systemId/workbench`; global shell plus dense dashboard/list blocks. | Config under `un_plat_*`; aggregate reads from owner table groups. | Platform account sees platform metrics only. System member sees current-system metrics only. Widget visibility re-checks backend permission. | loading, empty, no permission, backend error, contributor partial failure, config readback after admin save. | Read-only workbench creates no todo/message. Dashboard config save writes audit log. | Context switch proof, widget permission proof, config save/readback, visual screenshot/browser check, no runtime/admin mixing. |
| Flow / Flow 管理 | `examine-flow`; external exposure uses `examine-app`; business-module association references `examine-module`. | `/platform/flow`, `/systems/:systemId/flow`, admin variants; dense list + right detail/canvas. | `un_flow_*`; references to `un_openapi_*` and `un_module_*` by id only. | Flow designer/admin permissions by context; runtime approver permissions by task; external calls pass Application scope and Flow publish state. | draft, publish check failed, published, stopped/disabled, runtime running, waiting, approved/rejected, failed. | Approval tasks create/update Todo. Mentions/review/audit requests and approve/reject results create Messages. All publish/runtime/external-call/failure paths write audit/call logs. | Publish check proof, canvas readback, instance/task readback, permission denial proof, todo/message/log proof, right-detail preservation. |
| 应用 | `examine-app` as the authorization valve for internal communication and external exposure of Flow/system services. | `/platform/applications`, `/systems/:systemId/applications`, admin variants; dense list + right detail. | `un_openapi_*`; source service links reference `un_flow_*` or `un_module_*`. | Platform/system app admins manage credentials/scopes for their context. Callers access only explicitly granted scopes. Secrets are masked on readback. | draft/configured, enabled, disabled with reason, credential rotated, callback pending/success/failed, call success/failure. | Credential changes and exposure changes write audit logs. Callback/call failures may create owner messages. Application creates no business todos unless a linked service does. | Scope enforcement proof, masked secret readback, enable/disable proof, callback/call log proof, permission denial proof. |
| 工作 | Recommended independent `examine-work`; includes work statistics, project tasks, normal tasks, daily reports, and work config. | `/platform/work`, `/systems/:systemId/work`, admin config variants; dense list + task/report right detail. | Proposed `un_work_*`; attachments use `un_upload_*`; audit uses `un_audit_*`. | Work viewer/editor/admin by context; project/task/report ownership and participant rules; system work requires system member context. | task draft/open/in progress/blocked/done/cancelled/overdue; report draft/submitted/reviewed/rejected; readback includes list counts, task detail, report detail, stats, and assignee/reviewer state. | Assignment, due reminder, review request, mention, and rejection create Todo/Message records through contracts. Create/update/delete/status changes write audit logs. | Task/report CRUD readback, assignment permission proof, due/review todo proof, message proof, audit proof, desktop and narrow viewport checks. |
| AI | Recommended independent `examine-ai`; calls owner modules through permission-checked services instead of writing owner tables directly. | `/platform/ai`, `/systems/:systemId/ai`, admin config variants; dense conversation/history list plus right detail or work area. | Proposed `un_ai_*`; audit/desensitized metadata may use `un_audit_*`; generated files use `un_upload_*`. | AI use/admin permissions by context. Model, policy, tool scope, desensitization, and retention are enforced before request execution. AI cannot bypass module/field/action permissions. | provider disabled, config draft/published, session running/succeeded/failed, async running, validation error, backend/provider error. | AI-assisted writes create owner-module audit logs and may create Todo/Message only when the owner action normally would. AI request/response metadata writes AI audit. | Permission and desensitization proof, disabled/provider failure proof, session/result readback, owner-module write readback when applicable, audit proof. |
| 待办 | Recommended independent `examine-todo` as a unified actionable read/work center. Source business facts remain in owner modules; Flow approval source records remain `un_flow_*`. | `/platform/todo`, `/systems/:systemId/todo`; dense list with reminder/approval/reply/configured-scene filters; right detail/action area. | Proposed `un_todo_*`; source refs point to `un_flow_*`, `un_work_*`, `un_module_*`, or other owner groups. | Current-user recipient can view/action own todos. Source-module permission is rechecked before opening or completing. Admin views are aggregate/config only unless explicitly allowed. | pending, due soon, overdue, done, dismissed, expired, source invalid, action failed; readback includes counts, item detail, source summary, and post-action state. | Source modules create/update/close todos through contract. Todo action writes audit log and may emit message when action result affects others. | Source-to-todo creation proof, current-user isolation proof, source permission recheck proof, action readback, log/message proof. |
| 消息 | Recommended independent `examine-message` as current-user stream and delivery/read-state owner. Source business events remain in owner modules. | `/platform/messages`, `/systems/:systemId/messages`; dense list with mentions, review/audit requests, copied info, import/export, and major-operation categories. | Proposed `un_message_*`; source refs point to owner groups. | Current-user recipient can view/mark own messages. System context filters to current system. Admins use audit/log surfaces, not arbitrary user inbox access unless policy explicitly allows it. | unread, read, archived, deleted/hidden, delivery failed, source deleted; readback includes unread count, category count, message detail, source link availability. | Owner modules create messages through event/contract. Mark read/archive writes message read-state and optional audit log. Message itself should not create todos unless configured as actionable todo. | Recipient isolation proof, unread count readback, mark-read/archive readback, source link permission proof, no duplicate open buttons where row click navigates. |
| 后台 | Fan-out by object: `examine-plat`, `examine-module`, `examine-flow`, `examine-app`, proposed `examine-work`, proposed `examine-ai`, `examine-core`. | `/platform/admin`, `/systems/:systemId/admin`; admin tree/list with right table/form. | Multiple owner groups: `un_plat_*`, `un_module_*`, `un_flow_*`, `un_openapi_*`, proposed `un_work_*`, proposed `un_ai_*`, `un_audit_*`. | Platform admin cannot directly edit system business records unless entering system member/admin context. System admin permissions are scoped to current system. Runtime navigation must not expose admin actions to normal members. | config draft/published/disabled where applicable, validation error, backend error, success readback. Logs are append-only/read-only by default. | Every config mutation writes audit log. Config changes may create messages to affected admins/users when operationally relevant. Todos only for explicit approval/review workflows. | Admin permission proof, config save/readback, log proof, no runtime/admin mixing, dense admin layout screenshot/browser check. |
| 个人信息 | `examine-plat` owns identity/profile, memberships, system switch entry, logout/session metadata. | Top-nav utility `/profile`; system switch from platform/system utility group. | `un_plat_*`; audit/session metadata in `un_audit_*` if adopted. | Self-service profile only. System switch lists only memberships. Logout/session changes affect current principal. | profile loading/empty/error, update validation, success readback, system switch success/failure, logout success. | Profile/security changes write audit log and may create security message. System switch writes session/context log. | Self-only permission proof, membership-filtered switch proof, profile update readback, logout/context evidence. |
| 业务模块 | `examine-module`; Flow may orchestrate records but does not own module schema/runtime data. | `/systems/:systemId/modules/:groupKey/:moduleKey`; system admin `/systems/:systemId/admin/modules`; dense list + right detail/runtime form. | `un_module_*`; files in `un_upload_*`; workflow links in `un_flow_*`. | System member context required. Module/page/field/action permissions are enforced backend-side. Platform identity cannot directly operate system records. | module draft/publish check failed/published/disabled; record create/update/delete/import/export async running/succeeded/failed; runtime detail readback includes field/action/page permissions. | Import/export completion creates messages. Flow-bound actions create todos/messages through Flow/Todo/Message contracts. Record changes write audit logs. | Module publish proof, field/action permission proof, record CRUD readback, import/export message proof, audit proof, right-detail preservation. |

## Work / Todo / Message / AI Architecture Recommendation

Recommended settlement for rebuild planning:

1. Add independent product-domain modules for `examine-work`, `examine-todo`, `examine-message`, and `examine-ai`.
2. Reserve table prefixes `un_work_*`, `un_todo_*`, `un_message_*`, and `un_ai_*` before SQL freeze.
3. Keep `examine-flow` ownership of Flow definitions, runtime instances, and approval source records. The Todo module owns the unified current-user todo/reminder read/work center, not Flow internals.
4. Keep business source truth in owner modules. Todo and Message store normalized user-facing items with `source_type`, `source_id`, context, recipient, state, and action/read metadata.
5. AI is a user-facing orchestration module. It may call tools/services, but any durable write must be performed by the owning module after normal permission, state, validation, and audit checks.
6. Use `examine-core` only for shared context, audit, exception, result, and optional local event/outbox infrastructure. Do not move product APIs into core.

Fallback if new Maven modules are not approved: do not bury these domains in `examine-web`. Either postpone implementation until modules/prefixes are approved, or create an explicitly temporary package with the same ownership names and a migration task. The recommended path is still independent modules because the product boundary treats Work, Todo, Message, and AI as top-level domains in both platform and system contexts.

## Cross-Cutting Contracts

- Context: platform context and system member context are separate.
- Permission: backend must re-check every frontend permission.
- State: draft, publish check, published, disabled, failed states must be explicit.
- State/readback: every mutation must have an explicit success readback path from the owning module.
- Side effects: todo/message/log effects must be named for any workflow, import/export, AI write, application call, config mutation, or major action.
- Eventing: if a local event/outbox is adopted, `examine-core` owns infrastructure tables and contracts; product modules still own business semantics.
- Evidence: future tasks need frontend, backend, persistence/readback, permissions, states, side effects, and browser evidence.

## Open Architecture Decisions

1. Approve or reject new Maven modules and table prefixes for `examine-work`, `examine-todo`, `examine-message`, and `examine-ai`.
2. Decide whether Todo/Message side effects use a shared local event/outbox table, direct transactional writes, or a hybrid. Recommendation: local event/outbox for cross-module reliability, direct writes only inside the same owner module.
3. Define the normalized source reference contract for Todo and Message (`source_type`, `source_id`, context, route/action hint, permission recheck behavior).
4. Define Application scope model for both platform-level and system-level services inside `examine-app`, including whether manage services are separated by context.
5. Define AI model/provider policy, desensitization boundary, retention period, and tool-call permission model before AI implementation.
6. Define dashboard/widget configuration ownership: platform/system metadata in `examine-plat`, metric source ownership in each contributor module.

## Coding Impact

No future task may say only "implement Flow page" or "implement Application CRUD". It must name backend module, frontend shell/route, table group, permission model, state/readback, todo/message/log side effects, and evidence.

Before any backend/frontend/sql coding for Work, Todo, Message, or AI, the open decisions above must be resolved or explicitly scoped into that task.
