# P0 Recovery Task Cards

Time: 2026-06-27 Asia/Shanghai

## Task Card Contract

Every coding task must use this shape:

```text
Task id:
User role:
Business outcome:
Prototype reference:
Frontend scope:
Backend scope:
Generator scope:
Data scope:
Permission rule:
States:
Explicitly not complete if:
Acceptance script:
Evidence paths:
```

No task may close on build success alone.

## REC-P0-001 Auth, Health, And Release Baseline

User role: platform root administrator

Business outcome: The default administrator can start from the deployed login page, log in with `admin / 123123aa`, receive a Redis-backed token, and see the platform workspace. Unauthenticated and forged account-id requests are rejected.

Prototype reference:

- Login/register/password reset in `docs/design/prototypes/index.html`
- Auth and platform workspace requirements in `docs/design/prototype-brief.md`

Frontend scope:

- Login page
- Register and password reset entries
- Token storage and logout
- Platform workspace landing after login

Backend scope:

- Auth login/register/password reset
- Token creation and validation
- Current account resolution
- Health check for `status/database/schema/redis`
- Platform root bootstrap

Generator scope:

- Account/role/base persistence only. Generated CRUD does not close this task.

Data scope:

- Account, platform role, account-role binding, login audit, Redis access/refresh token.

Permission rule:

- Browser-provided `X-Account-Id` is ignored unless an explicit diagnostic switch is enabled.

States:

- Loading, invalid password, Redis unavailable, unauthorized, expired token, logout.

Explicitly not complete if:

- Login succeeds without Redis token storage.
- Frontend sends `X-Account-Id`.
- Health is UP while database/schema/redis is DOWN.
- Login only works through an API script but not browser UI.

Acceptance script:

1. Start release backend with external config.
2. Open frontend through same-origin route.
3. Login as `admin / 123123aa`.
4. Verify platform workspace appears.
5. Call a protected API without token and expect `AUTH_UNAUTHORIZED`.
6. Call a protected API with forged `X-Account-Id` and no token and expect `AUTH_UNAUTHORIZED`.
7. Restart service and verify health gates all dependencies.

Evidence paths:

- `docs/evidence/recovery/rec-p0-001-auth-release.md`

## REC-P0-002 Four Shells And Navigation Separation

User role: platform member, platform admin, system member, system admin

Business outcome: The product has four separated shells: platform workspace, platform admin, system business, and system admin. Pages must not stack unrelated shell navigation or mix platform/system menus.

Prototype reference:

- Four-shell separation in `docs/design/prototype-brief.md`
- Approved prototype shell layouts in `docs/design/prototypes/index.html`

Frontend scope:

- `frontend/src/features/platform/**`
- `frontend/src/features/platform-admin/**`
- `frontend/src/features/system-shell/**`
- `frontend/src/features/system-admin/**`
- Route guards and shell layout composition

Backend scope:

- Current account
- System switch context
- Effective roles and permissions
- Platform/system entry authorization

Generator scope:

- None beyond base reads.

Data scope:

- Platform role, system member, account-member binding, system role, menu/action permission.

Permission rule:

- Platform root can enter platform admin.
- Platform member cannot directly access system business without a system member context.
- System admin can enter system admin only inside a switched system.
- System member cannot see system admin or platform admin unless granted.

States:

- No system membership, forbidden route, empty authorized systems, switch required.

Explicitly not complete if:

- Platform and system navigation appear in the same shell.
- A page opens by changing title only without rebuilding context.
- A forbidden route renders partial content.

Acceptance script:

1. Login as platform root and capture platform workspace/admin.
2. Create or select a system and switch into it.
3. Capture system business shell and system admin shell.
4. Login/switch as a normal system member and verify admin entries are absent and direct URLs return forbidden.

Evidence paths:

- `docs/evidence/recovery/rec-p0-002-four-shells.md`

## REC-P0-003 System And Tenant Switch Context

User role: platform account with access to multiple systems or tenants

Business outcome: Switching system or tenant produces real `SystemSwitchContext` and `TenantSwitchContext`, then redraws brand, module groups, module navigation, runtime lists, todos, messages, field permissions, and data scope.

Prototype reference:

- System switch and tenant switch sections in `docs/design/prototype-brief.md`
- Approved prototype switch controls in `docs/design/prototypes/index.html`

Frontend scope:

- System switch control
- Tenant switch control
- App state reset after switch
- Runtime navigation refresh
- Todo/message refresh

Backend scope:

- System switch API
- Tenant list/create/update/switch behavior
- Account-member binding
- Effective permission snapshot

Generator scope:

- Tenant/system/member base CRUD only.

Data scope:

- System, tenant, account-member binding, system member, role-member, permission snapshot.

Permission rule:

- Platform identity alone cannot open system business data.
- Single-tenant systems hide tenant switching.

States:

- No member mapping, disabled system, disabled tenant, no data scope, switch failure.

Explicitly not complete if:

- Switch only changes URL/title.
- Old system modules, records, todos, or messages remain after switch.
- Tenant creation does not create a switchable member binding for the creator.

Acceptance script:

1. Login as admin.
2. Create a multi-tenant system and second tenant.
3. Switch into tenant A and record context fields.
4. Switch into tenant B and verify all context-dependent UI/API data changes.
5. Verify direct system business access without member context is rejected.

Evidence paths:

- `docs/evidence/recovery/rec-p0-003-switch-context.md`

## REC-P0-004 System Admin Initializes A Usable Module

User role: system administrator

Business outcome: A system admin can create a module group, create a module, configure fields/list/scene/actions, publish it, and see the module appear in the system business shell.

Prototype reference:

- System admin module configuration in `docs/design/prototypes/index.html`
- Module field/list/scene/action/publish requirements in `docs/design/prototype-brief.md`

Frontend scope:

- System admin module management
- Field configuration
- Scene/list configuration
- Publish check/result
- System business module navigation

Backend scope:

- Module group APIs
- Module APIs
- Field APIs
- Scene/list/action APIs
- Publish/check APIs
- Runtime schema APIs

Generator scope:

- Generated base CRUD for module tables is allowed only as persistence plumbing.

Data scope:

- Module group, module, fields, scenes, actions, publish version, runtime schema.

Permission rule:

- System admin configures modules.
- Normal system member sees only published and authorized modules.

States:

- Draft, publish validation failure, published, rollback, disabled field, empty business shell.

Explicitly not complete if:

- Module appears only in admin but not business shell.
- Publish silently auto-fills demo data.
- Field/list/scene config is static or local-only.

Acceptance script:

1. Login and switch as system admin.
2. Create module group.
3. Create module with at least text, date, select, attachment, and auto-number fields.
4. Configure list columns and scene.
5. Run publish check and publish.
6. Open system business shell and verify module navigation/schema from API.

Evidence paths:

- `docs/evidence/recovery/rec-p0-004-module-publish.md`

## REC-P0-005 Runtime Record CRUD, Detail, Draft, Sequence, And History

User role: normal system member with module permission

Business outcome: A normal user can create, save draft, submit, list, filter, open detail, edit, view history, use automatic numbering, and attach files for a published dynamic module.

Prototype reference:

- Runtime business list/detail/draft/import-export behavior in approved prototype.

Frontend scope:

- Runtime module list
- Dynamic form
- Draft handling
- Detail drawer
- Filters/pagination/sort
- Attachment and import/export entry states

Backend scope:

- Runtime schema
- Record create/update/search/detail/history
- Draft persistence
- Sequence allocation
- Attachment binding

Generator scope:

- Dynamic record/value/attachment base layer only.

Data scope:

- Dynamic record, value, history, draft, sequence, attachment.

Permission rule:

- Field/action/data-scope permissions must be enforced by backend and reflected by frontend.

States:

- Empty list, validation failure, no permission field, disabled action, draft saved/readback, attachment failure.

Explicitly not complete if:

- Production runtime imports prototype vehicle rows or local data.
- Refresh loses draft or created record.
- Sequence is in memory only.

Acceptance script:

1. Use a module created by REC-P0-004.
2. Save a draft and reload it.
3. Submit a record and verify list/detail/readback.
4. Edit record and verify history.
5. Allocate automatic numbers in two batches and verify continuity.
6. Verify a user without field permission cannot read/write restricted fields.

Evidence paths:

- `docs/evidence/recovery/rec-p0-005-runtime-records.md`

## REC-P0-006 Approval, Todo, Message, And Audit Closure

User role: requester and approver

Business outcome: Submitting a record for approval creates a persisted flow instance, approval task, todo, message, and audit trail. Approver can approve/reject/transfer with idempotency and visible result.

Prototype reference:

- Approval sidebar, todo center, message center, and log sections in approved prototype.

Frontend scope:

- Record detail approval sidebar
- Todo center
- Message center
- Approval action UI
- Result/error states

Backend scope:

- Flow publish
- Submit approval action
- Runtime instance/task/history
- Todo/message creation
- Approval action idempotency
- Audit logs

Generator scope:

- Flow/message/todo base CRUD only.

Data scope:

- Flow definition/version/instance/task/action, todo, message, audit log.

Permission rule:

- Only assigned/candidate approver can process the task.
- Platform messages cannot open system business detail directly.

States:

- Pending, approved, rejected, transferred, duplicate submit/action, no approver, failed message/todo creation.

Explicitly not complete if:

- Approval task exists but no visible todo/message exists.
- Message card has duplicate detail buttons instead of row click.
- Approval action only shows toast.

Acceptance script:

1. Publish a simple approval flow with explicit canvas.
2. Submit a runtime record for approval.
3. Verify todo and message appear for the approver.
4. Open todo/message and navigate to the correct business context.
5. Approve and verify record/sidebar/history/log state.
6. Repeat approval and verify idempotent rejection.

Evidence paths:

- `docs/evidence/recovery/rec-p0-006-approval-todo-message.md`

## REC-P0-007 Admin Configuration Breadth Smoke

User role: platform admin and system admin

Business outcome: Admin configuration pages are not static shells. They load and operate real API data for platform systems, tenants, roles, identity providers, model authorizations, health, logs, system org, members, roles, dictionaries, flows, SSO policy, work config, Agent policies, and logs.

Prototype reference:

- Platform admin and system admin pages in approved prototype.

Frontend scope:

- Platform admin
- System admin
- Platform workbench create-system entry

Backend scope:

- Existing admin APIs for all listed resources
- Permission checks
- Empty/error states

Generator scope:

- Base CRUD only; business publish/check/test endpoints must be coded.

Data scope:

- Platform and system admin configuration tables.

Permission rule:

- Platform admin and system admin boundaries must be distinct.

States:

- Empty org/module/flow, API failure, no permission, publish/check failure.

Explicitly not complete if:

- Admin page renders hard-coded cards or placeholder tables as production content.
- A create/update/test button only emits a success toast.

Acceptance script:

1. Login as platform admin and verify all platform admin dependencies load from APIs.
2. Create or update at least one platform system or identity provider.
3. Switch as system admin and verify all system admin dependencies load from APIs.
4. Create/update at least one department, role, dictionary, or flow draft.

Evidence paths:

- `docs/evidence/recovery/rec-p0-007-admin-config.md`

## REC-P0-008 Release Package And Final User Script

User role: deployer and product acceptor

Business outcome: A clean release package can be built, started, health-checked, restarted, stopped, served behind same-origin Nginx, and exercised through the final user acceptance script.

Prototype reference:

- Deployment/governance requirements in `docs/design/prototype-brief.md`.

Frontend scope:

- Production build
- `config.js`
- Same-origin `/api` calls
- No backend host embedded in frontend assets

Backend scope:

- Release jar
- External `application.yml`
- `server.sh start|stop|restart|status|health`
- Health gate

Generator scope:

- None.

Data scope:

- Target `docs/user_setting.md` database and Redis.

Permission rule:

- Default admin bootstrap must not grant system business access without system member context.

States:

- Missing Java, missing Redis, DB/schema mismatch, port occupied, failed health cleanup.

Explicitly not complete if:

- Current workspace has no fresh release package.
- Health does not require database/schema/redis all UP.
- Frontend assets contain backend host/port.
- User final script is not run or signed off.

Acceptance script:

1. Run `scripts/package-release.ps1`.
2. Start release backend from packaged `server.sh`.
3. Verify `/api/v1/health` all UP.
4. Verify same-origin Nginx config or local equivalent proxy.
5. Run final browser/API user script from login through module, record, approval, todo, message, admin, and release stop.
6. Set `gates.user_script_passed=true` only after user signoff.

Evidence paths:

- `docs/evidence/recovery/rec-p0-008-final-release.md`

## REC-P0-009 Frontend Interaction Contract Cleanup

User role: all P0 roles

Business outcome: P0 operations use explicit page sections, forms, drawers, or result panels that match the prototype task, not browser prompts, inert pagination, or broad mixed admin panels.

Prototype reference:

- Dedicated forms, drawers, list states, pagination, filters, and action results in `docs/design/prototypes/index.html`
- Interaction rules in `docs/design/prototype-brief.md`

Frontend scope:

- `frontend/src/features/platform/**`
- `frontend/src/features/platform-admin/**`
- `frontend/src/features/system-shell/**`
- `frontend/src/features/system-admin/**`
- `frontend/src/features/runtime/**`
- shared pagination, filters, and action-result components

Backend scope:

- Existing business APIs used by the forms/actions
- No new backend API is allowed unless the task card identifies a missing business contract

Generator scope:

- None. This is an interaction and integration cleanup task.

Data scope:

- Same data scope as the page being cleaned; each interaction must read/write through the real business API already assigned to its task card.

Permission rule:

- Disabled or hidden actions must reflect backend permission and state, not only frontend assumptions.

States:

- Loading, validation failure, disabled reason, no permission, backend error, async task created, success with trace/audit id, pagination next/previous, empty result.

Explicitly not complete if:

- Any P0 action still uses `window.prompt`, `window.confirm`, `alert`, or a generic success toast in production UI.
- Previous/next/load-more buttons render without changing data.
- Multiple unrelated admin menu entries scroll to the same mixed panel when the prototype expects separate task surfaces.
- A form submits without showing validation, result, trace/audit id, or async task feedback.

Acceptance script:

1. Static scan for `window.prompt`, `window.confirm`, `alert`, and inert pagination under P0 frontend paths.
2. Browser check platform workspace, platform admin, system business, system admin, todo, message, work, and runtime pages.
3. For each visible P0 action, either execute a real API flow or verify it is disabled with a precise reason.
4. Capture screenshots proving that admin pages are no longer broad mixed piles and that pagination/filter/action states visibly change.

Evidence paths:

- `docs/evidence/recovery/rec-p0-009-frontend-interactions.md`

## REC-P0-010 Work Management Runtime Closure

User role: system member and system administrator

Business outcome: A system member can use work management as a daily workbench: view dashboard counts, create a project, create a project task, create a plain task, switch task lists between list and kanban, write or auto-draft a daily report, and read those records back from the deployed frontend and backend.

Prototype reference:

- Work management rules in `.cursor/knowledge/project-operating-rules.md`
- Work management row in `docs/recovery/prototype-to-implementation-matrix.md`
- Approved prototype work management behavior in `docs/design/prototype-brief.md`

Frontend scope:

- `frontend/src/features/system-shell/systemShell.ts`
- Work dashboard, project task tab, plain task tab, daily report tab
- List/kanban toggle, task detail card, daily report list, pagination controls

Backend scope:

- `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/work/WorkManagementController.java`
- `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/work/WorkManagementService.java`
- Work project/task/report/search/dashboard/kanban APIs

Generator scope:

- Generated work project, work task, work daily report, comment, and event base services are persistence plumbing only.

Data scope:

- Work project, work task, work daily report, work task comment, work task event, system/member/tenant context.

Permission rule:

- Work data is scoped by current system, tenant, and current system member. A platform identity without system member context cannot use work runtime APIs.

States:

- Empty dashboard, empty list, created/readback, page previous/next, list view, kanban view, daily report draft, auto draft failure/success, validation failure.

Explicitly not complete if:

- Work records are only created by API but not visible in the work page.
- Project tasks and plain tasks are mixed into one generic task surface.
- List and kanban render together.
- Previous/next buttons appear clickable without changing page data.
- Daily report is only a text block or toast and cannot be read back.

Acceptance script:

1. Login as `admin / 123123aa` and create or switch into a test system.
2. Create a work project, project task, plain task, and daily report.
3. Read dashboard counts and search endpoints back.
4. Verify task list and kanban data contain the created records.
5. Verify deployed frontend shows the created records in the matching tabs.
6. Clean up or use clearly marked recovery data.

Evidence paths:

- `docs/evidence/recovery/rec-p0-010-work-management.md`

## REC-P0-011 Todo And Message Center Runtime Closure

User role: system requester, assigned approver, and normal system member

Business outcome: A system member can use the todo and message centers as daily workbenches. Approval submission creates an assigned todo and message; the requester does not see the approver todo; the approver can filter/search todos, open the business target, process the todo, see pending move to handled, read messages, mark all as read, archive messages, and verify archived messages leave the default active stream.

Prototype reference:

- Todo center and message center rules in `.cursor/knowledge/project-operating-rules.md`
- Todo/message rows in `docs/recovery/prototype-to-implementation-matrix.md`
- Approved prototype behavior in `docs/design/prototype-brief.md`

Frontend scope:

- `frontend/src/features/system-shell/systemShell.ts`
- System todo workbench type tree, keyword filter, paging, row action, and target jump
- System message center keyword/read/archive filters, paging, mark-all-read, archive action, row click read + target jump

Backend scope:

- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/todo/TodoController.java`
- `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/todo/TodoService.java`
- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/message/MessageController.java`
- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/message/MessageService.java`
- Approval-created todo/message persistence from runtime workflow

Generator scope:

- Generated todo/message base services are persistence plumbing only. Completion requires coded service filtering, actions, permission checks, and frontend state changes.

Data scope:

- `un_message_todo`, `un_message_message`, flow instance/task/action tables, runtime record tables, system/member/account binding.

Permission rule:

- Todo and message rows are scoped to the current bearer-token account and current system. Only the assigned approver can process the approval todo. Platform messages must not directly open system business details without system member context.

States:

- Empty list, pending, handled, unread, read, active, archived, keyword filter, pagination previous/next, no permission, action result with trace/audit id.

Explicitly not complete if:

- The todo or message exists only in API readback but cannot be found in the deployed frontend.
- The message page shows static filter chips without applying real query state.
- Archive or mark-read only shows a toast and does not change subsequent readback.
- Requester and approver see the same pending approval todo.
- Row click cannot navigate to the correct business target.

Acceptance script:

1. Create a target system, runtime module, approval flow, requester account, and approver account.
2. Submit a record for approval as requester.
3. Assert requester pending todo total is `0`, approver pending todo total is `1`, and approver message total is `1`.
4. Search/filter the approver todo center and verify the row target points to the created record.
5. Mark the message read, mark all matching messages read, archive the message, and verify active vs archived filters.
6. Process the approval todo or approval task as the approver and verify pending todo total becomes `0`, handled total becomes `1`.
7. Verify deployed frontend shows the todo and message centers with real data, filters, and state changes.
8. Clean created systems by default.

Evidence paths:

- `docs/evidence/recovery/rec-p0-011-todo-message-center.md`

## REC-P0-012 OpenAPI Upload Import Export Closure

User role: system administrator, external application integrator, and normal system member

Business outcome: A system administrator can create an external OpenAPI application without exposing plaintext secrets, rotate its secret through a secret reference, and see call-log evidence. A system member can upload a real file, bind it to business data, start import/export async tasks, and read task/file results back from the deployed frontend and backend.

Prototype reference:

- OpenAPI/upload/import-export row in `docs/recovery/prototype-to-implementation-matrix.md`
- OpenAPI and import/export rules in `.cursor/knowledge/project-operating-rules.md`
- Generated-vs-coded boundary in `docs/recovery/generated-vs-coded-api.md`

Frontend scope:

- `frontend/src/features/system-admin/systemAdmin.ts` OpenAPI panel
- Runtime module upload/import/export controls where records and attachments are operated
- API client bindings in `frontend/src/api/liveData.ts`

Backend scope:

- OpenAPI external app management, secret rotation, call-log APIs
- Upload file APIs and attachment binding APIs
- Import/export async task lifecycle APIs
- Existing runtime record/module APIs used by import/export target data

Generator scope:

- Generated app/upload/task/file base services are persistence plumbing only. Completion requires coded secret-reference behavior, permission checks, async task state, call-log creation, import/export readback, and frontend state.

Data scope:

- External app, OpenAPI secret reference/rotation job, OpenAPI call log, upload file, dynamic attachment, async task, task file/result, dynamic module/record/value.

Permission rule:

- System OpenAPI administration requires system administrator authority.
- Normal system members may upload/import/export only inside a switched system/member context and only for authorized modules/actions.
- OpenAPI secrets must never be returned to the frontend or logs as plaintext after creation/rotation.

States:

- External app enabled/disabled, secret active/rotating/expired, call allowed/denied, upload success/failure, import queued/running/succeeded/failed, export queued/running/succeeded/failed, task result downloadable, no permission.

Explicitly not complete if:

- The OpenAPI page displays a plaintext `appSecret` or only a fake key string.
- Secret rotation only updates a visible field and does not create a rotation/readback object.
- Upload succeeds but cannot be bound/read from a runtime record.
- Import/export only shows a toast and no persisted async task/file/result is read back.
- Browser evidence cannot show OpenAPI, upload, import, and export state from real backend data.

Acceptance script:

1. Login as `admin / 123123aa`, create and switch into a recovery system.
2. Create an OpenAPI app with scope/rate-limit/callback metadata and verify readback returns only `secretRef`, `secretVersion`, status, and masked key material.
3. Rotate the app secret and verify a rotation job/secret version is returned and read back without plaintext.
4. Simulate or invoke an OpenAPI call-log write/read and verify call result, scope, trace id, and app id.
5. Create/publish a dynamic module and upload a real file.
6. Bind the uploaded file to a runtime record and verify attachment readback.
7. Start an import task with a real uploaded file and verify task status/result readback.
8. Start an export task for the module and verify task status plus output file/result readback.
9. Verify deployed frontend shows the OpenAPI app/rotation/call log and runtime import/export/upload results with real backend data.
10. Clean created systems and files by default.

Evidence paths:

- `docs/evidence/recovery/r10-openapi-upload-import-export-2026-06-29.md`

## REC-P0-013 AI Agent Scope Confirmation Audit Closure

User role: platform administrator, system administrator, system member, and work user

Business outcome: Platform Agent and system/work Agent are separated by scope. Platform Agent can manage only platform-safe actions and is blocked from system business data. System Agent can create sessions, prepare write/work drafts, and persist audit logs, but business writes and work drafts require explicit human confirmation. System admin can manage Agent policy; ordinary system members cannot manage Agent policy.

Frontend scope:

- `frontend/src/features/system-admin/systemAdmin.ts` Agent policy panel must be backed by real policy APIs and publish-check result state.
- Runtime/work Agent interactions must expose confirmation state and audit trace where the UI surface exists.

Backend/API scope:

- Platform model authorization CRUD must persist `un_agent_model_authorization` and SecretRef metadata without plaintext credentials.
- Agent policy APIs must persist `un_agent_policy` with module/field/action/data/outbound/desensitize scope.
- Platform Agent session/message/confirmation APIs must persist `un_agent_session`, `un_agent_confirmation`, and `un_agent_audit_log` and reject system-business target scope.
- System Agent session/message/write-confirmation APIs must persist session, confirmation, audit, permission snapshot, field diffs, permission clips, and confirmation/rejection state.
- Work Agent draft confirm API must require human confirmation and persist source snapshots/audit.

Generator scope:

- Generated Agent base services are persistence plumbing only. Completion requires coded scope behavior, confirmations, audit snapshots, permission checks, and frontend/API integration.

Data scope:

- Model authorization, Agent policy, Agent session, Agent confirmation, Agent audit log, SecretRef, system member, role/member permissions.

Permission rule:

- Platform Agent cannot read or write system business payloads.
- System Agent write/work draft operations require human confirmation.
- System admin can manage Agent policy.
- Ordinary system members can use allowed runtime scope but cannot manage Agent policy.

Explicitly not complete if:

- Agent policy is only visible as an admin card or success toast.
- Platform Agent can access/write system business payloads.
- System write/work drafts are applied without human confirmation evidence.
- Model credential references expose plaintext secret material.
- Audit logs are missing session/confirmation/policy/permission/outbound/desensitize snapshots.
- Ordinary system members can manage Agent policies.

Acceptance script:

1. Login as admin, create a target system, and switch into it.
2. Create platform model authorization and verify readback returns only SecretRef metadata.
3. Create platform Agent session/message and verify platform audit plus pending confirmation.
4. Try platform Agent confirmation against system scope and verify it is rejected by scope.
5. Create system Agent policy with module/field/action/data/outbound/desensitize scope and verify publish-check passes.
6. Create system Agent session/message and verify write/work confirmations are proposed.
7. Create, confirm, and reject system write confirmations and verify status/audit readback.
8. Confirm work Agent draft and verify manual-confirm-required/source snapshot/readback.
9. Bind a normal system member account and assert it cannot manage Agent policies.
10. Add R11 to R5 final release orchestration after standalone R11 passes.

Evidence paths:

- `docs/evidence/recovery/r11-ai-agent-scope-confirmation-2026-06-29.md`

## REC-P0-014 SSO And No-Member Lifecycle Closure

User role: platform administrator, system administrator, and authenticated account without target system membership

Business outcome: A logged-in account that has no member mapping for a target system cannot enter that system directly. The account can submit a no-member access request. A system administrator can review it; incomplete approval keeps the request in reviewing state and still blocks business access. Approval with account, system member, role, and data scope creates the account-member binding, system role-member binding, and SSO binding, after which the requester can switch into the target system with the assigned role.

Prototype reference:

- SSO/no-member rules in `.cursor/knowledge/project-operating-rules.md`
- SSO/no-member row in `docs/recovery/prototype-to-implementation-matrix.md`
- SSO/no-member API contract in `docs/api/api.md`

Frontend scope:

- `frontend/src/features/no-member/noMemberAccess.ts`
- Platform admin identity provider management
- System admin SSO policy and precheck panel
- System switch failure/approval success states

Backend scope:

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/sso/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/nomember/**`
- System switch context and system access guards

Generator scope:

- Generated SSO/no-member/base services are persistence plumbing only. Completion requires coded lifecycle behavior, permission checks, binding creation, and readback.

Data scope:

- Identity provider, system SSO policy, SSO binding, no-member access request, account-member binding, role-member binding, system member, system role.

Permission rule:

- Creating a no-member request requires a logged-in account but must not require an existing target system member mapping.
- Approve/reject requires system administrator authority for the target system.
- Before approval with member, roles, and data scope, system switch must remain forbidden.

States:

- Submitted, reviewing, approved, rejected, no member mapping, missing member/role/data scope, approved and switchable.

Explicitly not complete if:

- The no-member request creation endpoint requires an existing target system member binding.
- Approval only changes request status but does not create account-member, role-member, and SSO binding records.
- A user can switch into the target system before full approval.
- The deployed frontend or list readback cannot show the request status and binding evidence after approval.

Acceptance script:

1. Create a target system and a platform identity provider.
2. Save system SSO policy and run org/member precheck.
3. Create a separate logged-in account with no target system membership.
4. Assert target system switch is rejected before approval.
5. Submit a no-member access request as the requester and read it back.
6. Approve with incomplete assignment and assert status is `REVIEWING`, business access is false, and switch is still rejected.
7. Create a target system member and role, approve with account/member/role/data scope, and assert account-member binding plus SSO binding ids are returned.
8. Assert the requester can now switch into the target system with the assigned role.
9. Verify deployed frontend shows the no-member page/request state or system admin SSO state with real backend data.
10. Clean created systems by default.

Evidence paths:

- `docs/evidence/recovery/r9-sso-no-member-2026-06-27.md`

## REC-P0-015 Module Builder Usability And Initialization Closure

User role: system administrator

Business outcome: A system administrator can create a new system and immediately configure a usable business module without layout collisions or hidden actions. The module builder must be a real configuration workspace: module list/search/filter, clear create/edit actions, fixed field-type selection, field editor with left field list, center preview/form, right property panel, visible publish-check and publish actions, and a default organization/department baseline for a fresh system.

Prototype reference:

- Module configuration row in `docs/recovery/prototype-to-implementation-matrix.md`
- System admin initialization and module configuration rules in `.cursor/knowledge/project-operating-rules.md`
- Latest user acceptance feedback on 2026-06-29: module settings buttons are obscured, publish cannot be clicked, filter beside reset is missing, field type selection/configuration shape is wrong, and new systems lack default department setup.

Frontend scope:

- `frontend/src/features/system-admin/systemAdmin.ts`
- Module management page layout, toolbar, filter/reset buttons, publish actions, and field configuration workspace.
- Shared CSS in `frontend/src/styles.css` if layout primitives are causing overlap.

Backend scope:

- Module/group/field/scene/action/publish APIs already used by REC-P0-004.
- System creation/bootstrap APIs if default department initialization is missing.

Generator scope:

- Generated base CRUD remains persistence plumbing only. This task closes only when the deployed browser UI is usable end to end.

Data scope:

- System, tenant, department, module group, module, fields, scenes, actions, publish version.

Permission rule:

- Only system administrators can configure modules and publish. Normal system members can only see published authorized modules in the business shell.

States:

- Empty module list, filtered module list, field add/edit/delete, fixed field type choices, validation failure, publish-check failed, publish succeeded, disabled publish with precise reason, fresh-system default department visible.

Explicitly not complete if:

- Any module configuration button is clipped, hidden behind another panel, or unreachable at desktop or mobile widths.
- Publish/check buttons are present but not clickable when the module is valid.
- Reset exists without a working filter/apply control beside it.
- Field type is free text or scattered across unrelated forms instead of a fixed selection in a dedicated field builder.
- A fresh system has no default organization/department baseline or initialization path for departments.
- Browser evidence only proves APIs, not the deployed UI.

Acceptance script:

1. Login as `admin / 123123aa`, create a fresh system, and switch into system admin.
2. Verify the fresh system has a default department or a visible initialization action that creates/readbacks one.
3. Open module management and verify toolbar actions are visible and not overlapped at desktop and narrow widths.
4. Use filter and reset controls and verify module list state changes or disabled state has a precise reason.
5. Create a module and add fields using fixed field type choices for text, date, select, attachment, and auto-number.
6. Verify the field builder exposes left field list, center form/preview, and right property panel without overlap.
7. Run publish check, publish, then open system business shell and verify the module is visible.
8. Capture deployed-browser evidence and include the task in final release verification before user signoff.

Evidence paths:

- `docs/evidence/recovery/r12-module-builder-usability-2026-06-29.md`

## REC-P0-016 Cross-Shell Responsive Usability Closure

User role: platform administrator, system administrator, and system member

Business outcome: The same deployed product must stay usable at desktop and narrow browser widths. Core shells and daily work pages must not stack whole sidebars above content, clip cards horizontally, hide primary actions, or require the user to guess which page section is active.

Prototype reference:

- Four-shell separation in `docs/recovery/prototype-to-implementation-matrix.md`
- Work management and system admin layout rules in `.cursor/knowledge/project-operating-rules.md`
- Latest user acceptance feedback on 2026-06-29: pages still look piled together and cannot be used as a system even after module-builder fixes.

Frontend scope:

- `frontend/src/features/system-admin/systemAdmin.ts`
- `frontend/src/features/system-shell/systemShell.ts`
- Shared responsive layout rules in `frontend/src/styles.css`
- Browser verification for platform workspace, system admin, system business dashboard, modules, work, todo, and messages.

Backend scope:

- No new backend behavior is expected for this card unless browser verification finds a state/API failure. Existing APIs must continue to pass through R5 orchestration.

Generator scope:

- None. Generated CRUD cannot close visual usability or shell behavior.

Data scope:

- Existing systems, modules, work records, todos, messages, and admin configuration data used by prior recovery scripts.

Permission rule:

- Responsive layout must not expose admin navigation or actions to roles that do not have permission. Normal member permission negatives from R2 remain part of the regression boundary.

States:

- Desktop, narrow/mobile viewport, selected admin section, no system, no module, work dashboard, work list, todo/message empty and populated states, loading/error/no-permission.

Explicitly not complete if:

- A narrow viewport shows an entire admin sidebar first and pushes the selected configuration page below the first screen.
- Dashboard or work cards overflow horizontally or clip the right side of content.
- Toolbar actions wrap over content, hide publish/action buttons, or create horizontal document scroll.
- Browser evidence only checks one route or only checks screenshots without machine-readable overflow/layout assertions.
- R5 final release orchestration is not updated after the responsive smoke passes.

Acceptance script:

1. Start or verify the deployed release at `http://127.0.0.1:18131`.
2. Login as `admin / 123123aa` and create or select a switchable system.
3. Capture desktop and narrow screenshots for platform workspace, system admin module management, system business dashboard, module runtime, work management, todo, and message pages.
4. Assert every route has the selected primary content visible, no document-level horizontal overflow, and no clipped action toolbar.
5. Specifically assert narrow system admin keeps navigation compact and the selected panel visible in the first viewport.
6. Specifically assert narrow work dashboard/cards use a single-column or otherwise non-clipped layout.
7. Re-run frontend typecheck/build, release package, local release start, verify-release, and the final R5 orchestration including this card.

Evidence paths:

- `docs/evidence/recovery/r13-cross-shell-responsive-usability-2026-06-29.md`

## REC-P0-017 Real Login Session State Browser Evidence Closure

User role: platform root administrator and system administrator

Business outcome: The deployed product must prove a real user can start from the login page, submit credentials through the UI, initialize frontend shell state from `/api/v1/account/me`, see the authenticated account label, and keep that authenticated state while opening system admin and work pages. Browser evidence must not rely on writing tokens after the app has already mounted.

Prototype reference:

- Login/register/password reset row in `docs/recovery/prototype-to-implementation-matrix.md`
- Four-shell separation and system admin/work rows in `docs/recovery/prototype-to-implementation-matrix.md`
- 2026-06-29 recovery lesson: script/browser evidence must prove the human path, not only an API token or a post-mounted localStorage mutation.

Frontend scope:

- Login page form submission and navigation.
- Shell state initialization after login.
- Platform workspace authenticated account display.
- System admin module-management route opened from a real logged-in browser session.
- System work route opened from the same real logged-in browser session.

Backend scope:

- Existing auth/account/system-switch APIs.
- Existing platform system creation and cleanup APIs used to prepare a disposable test system.

Generator scope:

- None. Generated CRUD cannot close login/session browser evidence.

Data scope:

- Redis access token, account profile, platform system, tenant/member switch context, system admin/work read APIs.

Permission rule:

- The browser session must use the default `admin / 123123aa` platform root account and receive a real system-super-admin context only after creating/switching into the test system.

States:

- Login form, authenticated platform workspace, localStorage token/account state, account label, system admin route, work route, mobile/narrow layout state, cleanup.

Explicitly not complete if:

- The script calls login API and then injects tokens after the SPA has already initialized.
- The browser screenshot still shows an unauthenticated account label after login.
- System admin or work routes only load after manually seeding shell state.
- Evidence omits machine-readable assertions for token/account state, account label, route hash, and horizontal overflow.
- R5 final release orchestration is not updated after the standalone R14 smoke passes.

Acceptance script:

1. Start or verify the deployed release at `http://127.0.0.1:18131`.
2. Use API login only to create a disposable test system and cleanup it later.
3. Open `/login` in a fresh browser profile.
4. Fill `admin / 123123aa` into the real login form and click the real submit button.
5. Assert localStorage token/account state exists, the shell shows `admin`, and the unauthenticated label is absent.
6. Navigate to system admin module management and work management using the same browser session.
7. Assert selected content is visible, no document-level horizontal overflow exists, and screenshots are captured.
8. Clean the created system and include this script in R5 final release orchestration.

Evidence paths:

- `docs/evidence/recovery/r14-real-login-session-state-2026-06-29.md`

## REC-P0-018 Password Reset Account Closure

User role: unauthenticated platform account owner

Business outcome: A user can request a password reset ticket from the deployed password-reset page, confirm the reset with the verification code, and then log in with the new password while the old password is rejected.

Prototype reference:

- Login/register/password reset row in `docs/recovery/prototype-to-implementation-matrix.md`
- Password reset route in `docs/design/prototypes/index.html`
- Auth/password reset contract in `docs/api/api.md`

Frontend scope:

- `frontend/src/features/auth/authPages.ts`
- Password reset request and confirm panels.
- Reset ticket and verification code readback for the current standalone/local delivery mode.
- Login page after password reset.

Backend scope:

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/auth/AuthService.java`
- Password reset request must resolve a real account, issue a short-lived reset ticket, store it server-side, and return traceable reset state.
- Password reset confirm must validate the ticket and verification code, update the account password hash, delete the ticket, and return a trace/audit result.

Generator scope:

- Account generated base service remains persistence plumbing only. Redis ticket storage and password update are coded behavior.

Data scope:

- `un_plat_account.password_hash`
- Redis password reset ticket key with account id and verification code.

Permission rule:

- Password reset request/confirm remain unauthenticated public auth endpoints, but confirm must only affect the account bound to the valid reset ticket.

States:

- Unknown account, expired/invalid ticket, wrong verification code, missing new password, old password rejected, new password accepted, ticket one-time use.

Explicitly not complete if:

- Confirm only returns a success/accepted result without changing the password hash.
- Reset works only through API and not through the deployed browser page.
- A reset ticket can be reused after a successful reset.
- The old password still logs in after reset.
- R5 final release orchestration is not updated after the standalone R15 smoke passes.

Acceptance script:

1. Register a disposable account with a disposable system through the auth API.
2. Open the deployed password reset page in a browser, request a reset for the account, and capture the returned ticket/verification code state.
3. Confirm a new password through the browser page.
4. Verify login with the old password fails and login with the new password succeeds.
5. Verify reusing the same reset ticket fails.
6. Clean created system data by default and include R15 in R5 final release orchestration.

Evidence paths:

- `docs/evidence/recovery/r15-password-reset-account-closure-2026-06-29.md`

## REC-P0-019 Register First-Use Browser Initialization Closure

User role: unauthenticated new system creator

Business outcome: A new user can start from the deployed register page, create an account and system through the real browser form, land in the new system dashboard with first-use initialization guidance, enter system backend from that guidance, and receive real system-super-admin context with a default department baseline.

Prototype reference:

- Login/register/password reset row in `docs/recovery/prototype-to-implementation-matrix.md`
- System admin initialization row in `docs/recovery/prototype-to-implementation-matrix.md`
- First-use initialization requirement in `.cursor/knowledge/project-operating-rules.md`

Frontend scope:

- `frontend/src/features/auth/authPages.ts`
- `frontend/src/app/state.ts`
- `frontend/src/features/system-shell/systemShell.ts`
- Register form, token persistence, shell initialization, dashboard onboarding prompt, and system backend entry.

Backend scope:

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/auth/AuthService.java`
- Register-with-system bootstrap for account, system, tenant, default department, owner member, role, binding, and token issuance.
- System switch and org/member readback APIs.

Generator scope:

- Generated account/system/member CRUD is persistence plumbing only. Completion requires coded register bootstrap plus browser evidence.

Data scope:

- Platform account, system, tenant, department, member, role, role-member, account-member binding, Redis token.

Permission rule:

- The registered account must become `SYSTEM_SUPER_ADMIN` only inside the newly created system. It must not receive platform-admin authority by default.

States:

- Register form, created account token state, new system dashboard, no-module onboarding prompt, initialization action, system backend, default department readback, cleanup.

Explicitly not complete if:

- Register works only through API but not the deployed browser form.
- Browser lands on an empty dashboard without first-use initialization guidance.
- The initialization action is not visible or cannot open system backend.
- The registered account cannot switch into the created system as `SYSTEM_SUPER_ADMIN`.
- The fresh system has no `default_department` or the owner member is not bound to it.
- R5 final release orchestration is not updated after standalone R16 passes.

Acceptance script:

1. Open `/register-with-system` in a fresh browser profile.
2. Fill and submit the real register form.
3. Assert token/account state exists and the browser lands on `/systems/{systemId}/dashboard`.
4. Assert the dashboard has a first-use onboarding panel and initialization action.
5. Click the initialization action and assert system backend content renders.
6. Login through API as the registered account and assert system switch returns `SYSTEM_SUPER_ADMIN`.
7. Read departments/members and assert `default_department` plus owner-member binding.
8. Clean the created system by default and include R16 in final release orchestration.

Evidence paths:

- `docs/evidence/recovery/r16-register-first-use-browser-closure-2026-06-29.md`

## REC-P0-020 Normal Member Real-Login Runtime Usability Closure

User role: normal system member with runtime module permission, without system-admin permission

Business outcome: A normal member can start from the deployed login page, log in with a real account, enter an authorized system business page, create a runtime record through the frontend form, read it back in the list/detail, and be denied system backend access both in navigation and direct URL.

Prototype reference:

- System business shell row in `docs/recovery/prototype-to-implementation-matrix.md`
- Runtime list/detail row in `docs/recovery/prototype-to-implementation-matrix.md`
- Four-shell permission rule in `.cursor/knowledge/project-operating-rules.md`

Frontend scope:

- `frontend/src/features/auth/authPages.ts`
- `frontend/src/features/system-shell/systemShell.ts`
- `frontend/src/features/runtime/records/runtimeRecords.ts`
- Login form, system shell header, module navigation, runtime create panel, list/detail readback, and access-denied page.

Backend scope:

- Existing auth/account/system-switch APIs.
- Existing system role/member/bind-account APIs used to prepare a disposable normal member.
- Existing module config/publish and runtime record APIs used by the deployed frontend.

Generator scope:

- Generated base CRUD remains persistence plumbing only. Completion requires coded permissions and browser evidence through business APIs.

Data scope:

- Platform account, target system, tenant, system role, system member, account-member binding, module group, module, fields, scene, publish version, runtime record/value/history.

Permission rule:

- The normal member must receive only a non-admin runtime role in the target system. The system-admin entry must be absent from the header, direct `#/systems/{systemId}/admin` must render an access-denied page, and backend admin APIs must reject the member.

States:

- Real login form, authenticated normal member shell, runtime module list, create form, saved record list/detail, absent admin entry, direct admin denied state, mobile/desktop overflow state, cleanup.

Explicitly not complete if:

- Browser state is seeded with tokens instead of submitting the real login form.
- Runtime record creation is proven only by API and not by the deployed frontend form.
- The normal member sees a system backend navigation entry.
- Direct system-admin URL loads admin content for the normal member.
- Saved record values are not visible after refresh/search.
- R5 final release orchestration is not updated after standalone R17 passes.

Acceptance script:

1. Use admin API only to prepare a disposable target system, published module, runtime role, normal account, member, and binding.
2. Open `/login` in a fresh browser profile.
3. Fill the normal member account credentials into the real login form and click submit.
4. Navigate to `#/systems/{systemId}/modules` and assert the normal member shell is visible without a system-admin entry.
5. Click the runtime page's create action, fill the deployed frontend form, save the record, and assert the value is visible in the list/detail.
6. Navigate directly to `#/systems/{systemId}/admin` and assert access denied rather than admin content.
7. Assert backend admin APIs reject the normal member and runtime search returns the frontend-created record.
8. Capture desktop/mobile screenshots, clean created systems, and include R17 in final release orchestration.

Evidence paths:

- `docs/evidence/recovery/r17-normal-member-real-login-runtime-2026-06-29.md`

## REC-P0-021 Runtime Mobile Action Containment Closure

User role: normal system member using a mobile-width browser

Business outcome: The normal member can use the deployed runtime module page on a 390px-wide viewport without primary actions, filters, table, detail panel, or edit form escaping the visible page. The create action must be fully visible and clickable from the first runtime viewport, and the user must be able to create and read back a record without horizontal document scrolling.

Prototype reference:

- Runtime list/detail row in `docs/recovery/prototype-to-implementation-matrix.md`
- Four-shell responsive rule in `.cursor/knowledge/project-operating-rules.md`
- Failure lesson: mobile shell evidence cannot stop at route existence or API success.

Frontend scope:

- `frontend/src/features/runtime/records/runtimeRecords.ts`
- `frontend/src/styles.css`
- Runtime page header, action group, filter panel, batch bar, table shell, detail/edit side panels, and mobile-width containment styles.

Backend scope:

- Existing runtime APIs are reused only to prepare and read back real records.
- No new backend behavior is expected unless the frontend fix exposes a missing API/state blocker.

Generator scope:

- Generated runtime CRUD remains persistence plumbing. Completion requires deployed mobile browser evidence of usable actions and no document-level horizontal overflow.

Data scope:

- Disposable system, tenant, module group, module, fields, scene, role/member binding, runtime record/value/history.

Permission rule:

- Use a normal member with only runtime permission. The mobile containment fix must not reveal system-admin entry or bypass the R17 permission boundary.

States:

- Real login, runtime module navigation, mobile action bar, quick filters, advanced filters, create form, saved record list/detail, and access denied for direct system-admin URL.

Explicitly not complete if:

- The mobile page relies on horizontal document scrolling to reach `新建`, `导入`, `全部导出`, or `列设置`.
- Any primary runtime action is clipped or partially outside the 390px viewport.
- The table, detail panel, or edit panel makes `document.documentElement.scrollWidth > window.innerWidth`.
- The script only checks API readback without interacting with the deployed mobile UI.
- The normal member sees the system backend entry after the layout change.
- R5 final release orchestration is not updated after standalone R18 passes.

Acceptance script:

1. Prepare a disposable published module and normal member with runtime permission only.
2. Open `/login` in a fresh browser profile at 390x720 and submit the normal account through the real form.
3. Navigate to `#/systems/{systemId}/modules` and assert the runtime page has no document-level horizontal overflow.
4. Assert the `新建` action and runtime filter controls are fully within the viewport.
5. Click the mobile `新建` action, fill the deployed frontend form, save the record, and assert the value is visible in list/detail.
6. Recheck no document-level horizontal overflow after opening detail and edit/create panels.
7. Assert the system-admin header entry is absent and direct system-admin URL renders access denied.
8. Capture mobile/desktop screenshots, clean created systems, and include R18 in final release orchestration.

Evidence paths:

- `docs/evidence/recovery/r18-runtime-mobile-action-containment-2026-06-29.md`

## REC-P0-022 Admin Aggregated Pagination Closure

User role: platform administrator and system administrator

Business outcome: Administrators can reach records beyond the first page in every aggregated backend list. Platform admin and system admin screens must not present disabled pagination for API-backed resources such as systems, roles, members, modules, flows, dictionaries, data sources, OpenAPI apps, Agent policies, and logs.

Prototype reference:

- Platform admin, system admin, module configuration, data source, OpenAPI, AI Agent, and log rows in `docs/recovery/prototype-to-implementation-matrix.md`
- Failure lesson: a page is not accepted when only the first page of data is usable.

Frontend scope:

- `frontend/src/api/liveData.ts`
- `frontend/src/features/platform-admin/platformAdmin.ts`
- `frontend/src/features/system-admin/systemAdmin.ts`

Backend scope:

- Existing paged backend APIs are reused.
- No new backend endpoint is expected unless a paged resource cannot be queried by page number.

Generator scope:

- Not applicable. This is a manual UI integration gap over already generated or existing backend list APIs.

Data scope:

- Disposable platform systems and a disposable system with enough backend rows to force page 2.

Permission rule:

- Platform pagination remains platform-admin only.
- System-admin pagination remains scoped to the selected system and must not expose other systems or normal-member-only access.

States:

- Platform admin first page, next page, previous page.
- System admin first page, next page, previous page for representative paged resources.

Explicitly not complete if:

- Any admin list with `hasNext=true` renders a disabled next-page action because no page-change callback is wired.
- Clicking next page does not re-query the backend with a new `pageNo`.
- The page number changes only in the DOM while records stay from page 1.
- Evidence is API-only without deployed browser interaction.
- The final release orchestration is not updated after standalone R19 passes.

Acceptance script:

1. Prepare enough disposable platform/system data to force at least one platform-admin and one system-admin resource onto page 2.
2. Open the deployed frontend with a real admin login.
3. Navigate to platform admin, verify next page is enabled when `hasNext=true`, click it, and assert the visible page number and record set changed.
4. Navigate to system admin, verify next page is enabled for a representative paged list, click it, and assert the visible page number and record set changed.
5. Assert no admin pagination control shows the unsupported-pagination disabled reason for callback-backed resources.
6. Capture evidence, clean disposable data, and include R19 in final release orchestration.

Evidence paths:

- `docs/evidence/recovery/r19-admin-aggregated-pagination-2026-06-29.md`

## REC-P0-023 System Flow Canvas Designer Closure

User role: system administrator

Business outcome: A system administrator can configure a workflow from the deployed system backend without falling back to API-only scripts. The flow page must expose node library, canvas nodes, directed edges, node-specific property payloads, save, simulation, publish-check, and publish readiness in one coherent workspace.

Prototype reference:

- `docs/design/design-package.md`: Flow must include node library, canvas wiring, node properties, approval method, timeout, simulation, and publish check.
- `docs/design/pre-coding-readiness.md`: `sysFlows + flowNodePropertyDrawer`.
- `.cursor/knowledge/project-operating-rules.md`: flow configuration must have node library, canvas lines, branch labels, property panel, simulation, publish check, and impact analysis.

Frontend scope:

- `frontend/src/api/client.ts`
- `frontend/src/api/liveData.ts`
- `frontend/src/features/system-admin/systemAdmin.ts`
- `frontend/src/styles.css`

Backend scope:

- Reuse existing coded flow definition APIs: node library, canvas read/save, simulate, publish-check, publish, snapshots.
- No generated CRUD response can close this card without browser interaction.

Generator scope:

- Generated `FlowDefinition`, `FlowNode`, `FlowEdge`, and snapshot base services remain persistence plumbing only.
- The coded manage service and deployed frontend must prove the usable workflow.

Data scope:

- Disposable system, flow definition, `un_flow_node`, `un_flow_edge`, flow snapshot/publish-check result, audit/trace response.

Permission rule:

- Only a system administrator in the selected system can configure the system flow.
- Normal-member/runtime approval evidence remains covered by R3/R17 and must not gain system-admin entry.

States:

- Empty draft canvas, explicit template insertion, node add/update/delete, edge add/delete, saved canvas readback, publish-check failed before save when invalid, publish-check passed after valid nodes/edges, simulation result, mobile containment.

Explicitly not complete if:

- The flow page only lists flows and exposes create/check/publish buttons.
- A flow can only be configured by API smoke scripts.
- The frontend silently injects demo/sample nodes when creating a flow.
- Node properties are not editable or are stored only in local state.
- Edges are visual-only and not persisted to `un_flow_edge`.
- Browser evidence does not use real `/login` and deployed frontend.
- R5 final release orchestration is not updated after standalone R20 passes.

Acceptance script:

1. Create a disposable system and a draft flow with an empty canvas.
2. Assert API publish-check fails on the empty canvas.
3. Open the deployed frontend, log in through the real `/login` form, and navigate to system backend flow management.
4. Click `配置画布`, insert or build an approval-to-end canvas, update node properties, save the canvas, and assert API readback has nodes and edges.
5. Run publish-check and simulation from the browser UI and assert trace/result text changes.
6. Assert mobile width does not produce document-level horizontal overflow; the canvas may scroll only inside its own panel.
7. Run API publish and snapshot readback to prove the saved canvas is publishable.
8. Clean disposable systems and include R20 in R5 final release orchestration.

Evidence paths:

- `docs/evidence/recovery/r20-flow-canvas-designer-2026-06-29.md`

## REC-P0-024 Final Usable System Journey Gate

User role: platform administrator, system administrator, normal system member, approver, external integrator, deployer

Business outcome: The product is accepted as a usable system only when the final role journeys pass together: release health, first-system registration, admin business-app build, normal-member daily work, approval/todo/message closure, admin configuration depth, external/import-export integration, and operations/maintenance evidence.

Prototype reference:

- `docs/user_requirement.md`
- `docs/design/prototype-brief.md`
- `docs/recovery/final-usable-system-acceptance.md`

Frontend scope:

- All four shells: platform workspace, platform admin, system business page, system admin.
- Runtime list/detail/form/upload/export/todo/message/approval pages.
- Admin configuration pages for modules, roles, org, flow, dictionary, work config, data source, SSO, Agent, external app, and logs.

Backend scope:

- Existing business APIs used by R2 through R20.
- Final journey audit must treat release/package/start/restart/health as part of the product, not an external afterthought.

Generator scope:

- Generated CRUD is evidence plumbing only.
- The final gate closes only on role journeys with coded business behavior, permissions, state, and deployed browser evidence.

Data scope:

- Platform account/system/tenant/member/role.
- Module group/module/field/scene/action/version.
- Runtime record/value/history/draft/sequence/attachment.
- Flow definition/canvas/snapshot/instance/task.
- Todo/message/log/task/OpenAPI/upload/import/export/Agent/data-source/SSO tables.

Permission rule:

- Platform identity cannot bypass system-member context.
- Normal system members cannot see or enter system admin.
- Approvers can process only assigned/candidate tasks.
- External applications can access only authorized scopes and must not reveal secret plaintext.

States:

- Release dependency down, corrupted evidence, empty system, first-use guidance, draft/published config, validation failure, no permission, disabled action, mobile containment, async task running/success/failure, audit trace.

Explicitly not complete if:

- R5, R20, or any single batch passes but the final J0-J7 journey audit fails.
- Evidence files are missing, corrupted, or API-only for a browser/user journey.
- A feature works only in a disposable isolated script and is not integrated into the same coherent system journey.
- Backup/rollback/operations gaps are silently ignored.
- `gates.user_script_passed` is set true without user verification/signoff.

Acceptance script:

1. Run `scripts/recovery-r21-final-usable-system-audit.ps1`.
2. The script must read the final acceptance ledger and required evidence.
3. It must fail on invalid/corrupted R5 result, missing R20 flow canvas evidence, missing journey evidence, or unproven operations/maintenance journey.
4. It writes `docs/evidence/recovery/r21-final-usable-system-audit-result.json`.
5. Only when J0-J7 all pass and user verification/signoff is recorded may the final product goal be claimed.

Evidence paths:

- `docs/recovery/final-usable-system-acceptance.md`
- `docs/evidence/recovery/r21-final-usable-system-audit-2026-06-29.md`
- `docs/evidence/recovery/r21-final-usable-system-audit-result.json`

## REC-P0-025 Operations And Maintenance Journey Closure

User role: platform administrator and deployer

Business outcome: A platform administrator can use the deployed platform backend to run maintenance governance actions with visible results: platform health check, feature flag update, quota update, rate-limit update, backup task creation, restore drill, archive restore request, deployment rollback dry-run, and API cache policy read/update. A deployer can verify release package scripts support start/stop/restart/status/health and deployed frontend assets match the release.

Prototype reference:

- `docs/design/prototype-brief.md` section 7.1上线保障与日常效率
- `docs/user_requirement.md` sections 14.18, 14.32, 14.33, 14.34
- `docs/recovery/final-usable-system-acceptance.md` J7

Frontend scope:

- Platform admin configuration page.
- Operations governance panel under platform configuration.

Backend scope:

- Existing `OpsGovernanceController` and `OpsGovernanceService`.
- Release verification scripts and packaged `server.sh`.

Generator scope:

- Generated `un_ops_*` base tables are not sufficient.
- Completion requires coded governance API results and deployed browser interaction.

Data scope:

- `un_ops_health_check`, `un_ops_feature_flag`, `un_ops_quota`, `un_ops_rate_limit_policy`, `un_ops_backup_restore`, `un_ops_archive_restore_request`, `un_ops_deployment`, `un_ops_api_cache_policy`, async task model, release package files.

Permission rule:

- Platform operations are platform-admin only.
- Dangerous maintenance operations must return dry-run or async-task boundaries unless a real destructive action has an explicit deployer workflow.

States:

- Health warn/pass, feature flag saved, quota saved, rate-limit saved, backup queued, restore drill queued, archive restore queued, deployment rollback queued, cache policy read/update, traceId/auditLogId, release asset match.

Explicitly not complete if:

- Operations are only mentioned in documentation but not visible in the deployed platform backend.
- Backup or rollback buttons only toast success without a task id or traceId.
- The script verifies API only and never opens the deployed browser panel.
- Release verification ignores deployed frontend asset hash or health dependency state.

Acceptance script:

1. Build/package/start a release with the current frontend.
2. Run `scripts/recovery-r22-ops-maintenance-smoke.ps1`.
3. The script must verify release health/assets, packaged script command support, API operations, and deployed browser operation buttons.
4. Add R22 evidence to the final R21 J7 gate.

Evidence paths:

- `docs/evidence/recovery/r22-ops-maintenance-2026-06-29.md`
- `docs/evidence/recovery/r22-ops-maintenance-result.json`

## REC-P0-026 Final Requirement Coverage Gate

User role: product owner, system administrator, normal system member, external integrator, operator

Business outcome: The project cannot claim final completion until every major requirement section in `docs/user_requirement.md` is mapped to a coverage row and each row is either proven by strong deployed evidence or explicitly excluded by the user.

Prototype/reference:

- `docs/user_requirement.md`
- `docs/framework/final-requirement-coverage-ledger.md`
- `docs/framework/final-requirement-closure-plan.md`
- `docs/framework/current-final-system-engineering.md`

Frontend scope:

- All production frontend routes indirectly, because the coverage gate blocks final completion rather than implementing one screen.

Backend scope:

- All coded business APIs indirectly, because the coverage gate checks that evidence covers requirement areas rather than generated/controller existence.

Generator scope:

- Generated CRUD is never enough to mark a coverage row `PROVEN`.

Data scope:

- Coverage ledger and evidence result files:
  - `docs/framework/final-requirement-coverage-ledger.md`
  - `docs/evidence/final-requirement-coverage-audit-result.json`
  - `docs/evidence/final-requirement-gap-report.md`

Permission rule:

- Only explicit user approval can mark a requirement `USER_EXCLUDED`.
- Agents may mark a row `PROVEN` only with deployed browser/API/readback evidence that covers the full requirement area.

States:

- `PROVEN`, `PARTIAL`, `OPEN`, `USER_EXCLUDED`.

Explicitly not complete if:

- Any major `docs/user_requirement.md` section is missing from the ledger.
- Any row is `PARTIAL` or `OPEN`.
- A row is marked `PROVEN` from a narrow script that does not cover the full requirement area.
- A row is marked `USER_EXCLUDED` without explicit user approval.
- R-batch evidence is used to close the full requirement without matching the row scope.

Acceptance script:

1. Run `scripts/final-requirement-coverage-audit.ps1`.
2. Run `scripts/final-requirement-gap-report.ps1`.
3. The audit must fail until all requirement rows are `PROVEN` or `USER_EXCLUDED`.
4. The gap report must list recommended closure batches in order.
5. Final completion claims are blocked while the audit fails.

Evidence paths:

- `docs/framework/final-requirement-coverage-ledger.md`
- `docs/framework/final-requirement-closure-plan.md`
- `docs/evidence/final-requirement-coverage-audit-result.json`
- `docs/evidence/final-requirement-gap-report.md`

## REC-P0-027 FRC-1 Missing Product Surfaces Closure

User role: system administrator, platform administrator, normal system member

Business outcome: The currently `OPEN` requirement rows are turned into implemented and proven product surfaces: pages/page configuration, overall visual style, platform/system home pages, page designer, and advanced capabilities or their explicit implementable sub-task cards.

Coverage rows:

- `REQ-5.8` Pages
- `REQ-6.1` Overall visual style
- `REQ-6.3` Home page
- `REQ-6.8` Page designer
- `REQ-9` Advanced/new capabilities

Prototype/reference:

- `docs/user_requirement.md` sections 5.8, 6.1, 6.3, 6.8, 9
- `docs/framework/final-requirement-coverage-ledger.md`
- `docs/framework/final-requirement-closure-plan.md`
- `docs/design/prototype-brief.md`
- `docs/design/prototypes/index.html`

Frontend scope:

- Platform workspace home page.
- System business home/dashboard page.
- System admin page configuration/designer surface.
- Runtime page rendering behavior that proves page configuration affects the usable system.
- Visual/wording audit surfaces for the primary role journeys.

Backend scope:

- Page configuration APIs if already present; otherwise add coded manage APIs for page definitions, page layouts, publish/readback, and runtime resolution.
- Advanced capability APIs must be split into sub-task cards when a single implementation would be too broad.

Generator scope:

- Generated page/config entities are persistence plumbing only.
- Page designer and advanced capability closure requires coded business behavior, deployed frontend interaction, and readback.

Data scope:

- Page definition/configuration tables, module/page relation, published page snapshot, runtime page resolution.
- Advanced capability tables according to the split sub-task cards: command center, assistant, schema-driven page, advanced table, flow simulation, print designer.

Permission rule:

- System administrators can configure pages for their system.
- Normal members can only see published runtime pages they are authorized to use.
- Platform administrators cannot bypass system-member context to configure system business pages.

States:

- Empty home page, configured home page, draft page, published page, invalid layout, permission denied, mobile layout, preview, rollback/readback, visual hierarchy pass/fail.

Explicitly not complete if:

- `REQ-5.8`, `REQ-6.1`, `REQ-6.3`, `REQ-6.8`, or `REQ-9` remain `OPEN`.
- The page designer exists only as a static card or placeholder.
- Home pages are just dashboards with unrelated stacked panels.
- Visual style is accepted only by absence of overflow, without hierarchy/wording/aesthetic review.
- Advanced capability rows are hidden under a broad "future" label without user exclusion.
- The coverage ledger is not updated after implementation evidence.

Acceptance script:

1. Create or select a disposable system.
2. Log in through the deployed frontend as system administrator.
3. Configure a page/home surface through the UI and publish it.
4. Log in or switch as a normal member and verify the published home/page is visible, clean, permission-filtered, and mobile-contained.
5. Verify API readback for page definition and published snapshot.
6. Run a visual/wording audit for primary home/page routes.
7. Split advanced capabilities into explicit sub-task cards or prove implemented surfaces.
8. Update `docs/framework/final-requirement-coverage-ledger.md` and rerun `scripts/final-requirement-coverage-audit.ps1`.

Evidence paths:

- Home page config loop: `docs/evidence/recovery/r24-home-page-config-2026-06-30.md`
- Home page config result: `docs/evidence/recovery/r24-home-page-config-result.json`
- Browser result: `docs/evidence/recovery/screenshots/r24-home-page-config/home-page-config-browser-audit.json`
- Page designer API/runtime loop: `docs/evidence/recovery/r24-page-designer-2026-06-30.md`
- Page designer result: `docs/evidence/recovery/r24-page-designer-result.json`
- Visual-density browser audit: `docs/evidence/recovery/r24-visual-style-browser-audit-2026-06-30.md`
- Visual-density browser audit result: `docs/evidence/recovery/r24-visual-style-browser-audit-result.json`
- Remaining FRC-1 closure evidence still expected at `docs/evidence/recovery/r24-frc1-missing-product-surfaces.md` or follow-up split task evidence.
