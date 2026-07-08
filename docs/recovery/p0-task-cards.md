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

- The mobile page relies on horizontal document scrolling to reach `鏂板缓`, `瀵煎叆`, `鍏ㄩ儴瀵煎嚭`, or `鍒楄缃甡.
- Any primary runtime action is clipped or partially outside the 390px viewport.
- The table, detail panel, or edit panel makes `document.documentElement.scrollWidth > window.innerWidth`.
- The script only checks API readback without interacting with the deployed mobile UI.
- The normal member sees the system backend entry after the layout change.
- R5 final release orchestration is not updated after standalone R18 passes.

Acceptance script:

1. Prepare a disposable published module and normal member with runtime permission only.
2. Open `/login` in a fresh browser profile at 390x720 and submit the normal account through the real form.
3. Navigate to `#/systems/{systemId}/modules` and assert the runtime page has no document-level horizontal overflow.
4. Assert the `鏂板缓` action and runtime filter controls are fully within the viewport.
5. Click the mobile `鏂板缓` action, fill the deployed frontend form, save the record, and assert the value is visible in list/detail.
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
4. Click `閰嶇疆鐢诲竷`, insert or build an approval-to-end canvas, update node properties, save the canvas, and assert API readback has nodes and edges.
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

- `docs/design/prototype-brief.md` section 7.1涓婄嚎淇濋殰涓庢棩甯告晥鐜?- `docs/user_requirement.md` sections 14.18, 14.32, 14.33, 14.34
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
- REQ-9 split evidence: `docs/evidence/recovery/r24-req9-split-2026-06-30.md`
- Remaining FRC-1 closure evidence still expected at `docs/evidence/recovery/r24-frc1-missing-product-surfaces.md` or follow-up split task evidence.

## REC-P0-028 Command Center

User role: platform administrator, system administrator, normal system member

Business outcome: A user can open a command center from the running product, search authorized navigation/actions/data entry points, and execute a permitted shortcut without leaving the current role context.

Implementation status:

- PARTIAL/PASS for the first navigation loop on 2026-06-30.
- Implemented authenticated command search, platform/system header entry, Ctrl/Cmd+K overlay, search filtering, route execution, and admin/normal-member permission瑁佸壀 evidence.
- Remaining command-center breadth: keyboard result selection, recent/favorite command persistence, safe quick-create/draft actions beyond navigation, normal-member deployed browser disabled-state evidence, and mobile browser audit.

Coverage rows:

- `REQ-9.1` Command center
- Related `REQ-4.1`, `REQ-5.9`, `REQ-5.10`, `REQ-6.2`

Prototype/reference:

- `docs/user_requirement.md` section 9.1

Frontend scope:

- Global command trigger and panel.
- Search result groups for menu, system/app, data record, todo, and configuration pages.
- Empty, no-permission, keyboard navigation, and execution result states.

Backend scope:

- Coded search/command service that resolves current account, active system/member context, permissions, and target routes/actions.
- Execution endpoint for safe shortcut actions such as quick open and quick create draft.

Generator scope:

- Generated menu/module/todo/data reads are plumbing only.
- Command ranking, permission filtering, and execution are coded behavior.

Data scope:

- Command audit log and optional saved recent/favorite commands.

Permission rule:

- Results must only include routes/actions/data visible to the current role and active system member.
- Platform context cannot directly execute system business commands without a valid system switch context.

Explicitly not complete if:

- It is only a static search box.
- It returns unauthorized routes.
- It opens pages but cannot execute any safe action.
- It has no deployed browser evidence from real login.

Acceptance script:

1. Log in as platform admin and open command center.
2. Search platform admin entry and navigate to it.
3. Switch into a system and search module/work/todo entries.
4. Verify a normal member sees only runtime-permitted commands.
5. Verify unauthorized admin command is absent or disabled with reason.

Evidence:

- `scripts/recovery-r28-command-center-smoke.ps1`
- `docs/evidence/recovery/r28-command-center-2026-06-30.md`
- `docs/evidence/recovery/r28-command-center-result.json`
- `docs/evidence/recovery/screenshots/r28-command-center/command-center-browser-audit.json`

## REC-P0-029 Right-Side Intelligent Assistant

User role: system administrator, normal system member

Business outcome: A user can open a right-side assistant in a permitted context, request an AI-assisted draft or explanation, review the result, and explicitly confirm or reject any write.

Coverage rows:

- `REQ-9.2` Right-side intelligent assistant
- Related `REQ-5.19`

Prototype/reference:

- `docs/user_requirement.md` sections 9.2 and 5.19

Frontend scope:

- Right-side assistant drawer from system admin and runtime contexts.
- Context summary, prompt input, draft result, confirmation, rejection, masked sensitive fields, and audit trace.

Backend scope:

- Reuse or extend coded AI policy service so outputs are scoped by module, field, action, data range, model authorization, and confirmation type.

Generator scope:

- Generated AI/log tables are plumbing only.
- Policy resolution, masking, confirmation, and audit are coded behavior.

Data scope:

- Agent policy, prompt/result log, confirmation record, and write audit.

Permission rule:

- Assistant can only read/write within current system member permissions.
- Any generated write requires human confirmation.

Explicitly not complete if:

- The assistant is only a text panel or fake success toast.
- It writes without confirmation.
- It exposes masked fields or ignores policy.

Acceptance script:

1. Configure a system Agent policy.
2. Open assistant as admin and generate a field/process/export draft.
3. Confirm a safe draft and read back audit.
4. Open as normal member and verify denied admin-generation actions.

Status: `accepted` as first-loop evidence only; `REQ-9` remains `PARTIAL`.

Accepted evidence:

- `scripts/recovery-r29-right-assistant-smoke.ps1`
- `docs/evidence/recovery/r29-right-assistant-2026-06-30.md`
- `docs/evidence/recovery/r29-right-assistant-result.json`
- `docs/evidence/recovery/screenshots/r29-right-assistant/assistant-drawer-browser-audit.json`
- `docs/evidence/recovery/screenshots/r29-right-assistant/desktop-assistant-drawer.png`

R29 proven scope:

- System shell exposes a right-side `鍔╂墜` entry.
- Drawer renders context summary, prompt input, generate actions, confirmation states, and audit trace surfaces.
- Admin Agent policy publish-check, message, write preview, confirm, reject, work draft preview/confirm, and audit readback pass on the deployed release.
- Normal member can use a scoped assistant session/message and is rejected from admin policy creation with HTTP 403.

Remaining work:

- Normal-member browser proof for disabled admin-generation state.
- Mobile drawer audit.
- Field/process/export-specific draft templates beyond the first generic write/report loop.
- Drawer focus management and keyboard handling.
- Missing model/policy, backend failure, and conflict-state browser evidence.

## REC-P0-030 Schema-Driven Page Runtime

User role: system administrator, normal system member

Business outcome: A configured schema can render the same page/form contract consistently in admin preview and runtime, with validation, permissions, and readback.

Coverage rows:

- `REQ-9.3` Schema-driven page
- Related `REQ-5.5`, `REQ-5.8`, `REQ-6.5`, `REQ-6.8`

Prototype/reference:

- `docs/user_requirement.md` section 9.3

Frontend scope:

- Shared schema renderer for form/list/detail components.
- Admin preview and runtime read mode.
- Validation, hidden/readonly fields, empty/error states, and mobile containment.

Backend scope:

- Coded schema version endpoint that composes module fields, page components, permissions, and published snapshots.

Generator scope:

- Generated field/page reads are plumbing only.
- Schema composition, versioning, permission masking, and validation are coded behavior.

Data scope:

- Published schema snapshot and schema version metadata.

Permission rule:

- Runtime schema must remove hidden fields and enforce readonly/write permissions server-side.

Explicitly not complete if:

- It renders only one hardcoded form.
- It ignores field permissions.
- Web and mobile paths use divergent schema definitions.

Acceptance script:

1. Configure fields and a page schema.
2. Publish the schema.
3. Verify admin preview and runtime form use the same schema version.
4. Verify normal member field visibility/editability and backend rejection for forbidden writes.

Status: `accepted` as first-loop evidence only; `REQ-9` and `REQ-6.8` remain `PARTIAL`.

Accepted evidence:

- `scripts/recovery-r30-schema-runtime-smoke.ps1`
- `docs/evidence/recovery/r30-schema-runtime-2026-06-30.md`
- `docs/evidence/recovery/r30-schema-runtime-result.json`
- `docs/evidence/recovery/screenshots/r30-schema-runtime/schema-runtime-api-audit.json`
- `docs/evidence/recovery/screenshots/r30-schema-runtime/schema-preview-browser-audit.json`
- `docs/evidence/recovery/screenshots/r30-schema-runtime/desktop-schema-preview.png`

R30 proven scope:

- Admin can save/publish a module page schema and read the published schema endpoint.
- Runtime can read the same published schema version from `/runtime/modules/{moduleId}/pages/main/schema`.
- Schema composition includes module fields, page components, publish version, permission snapshot metadata, validation metadata, hidden-field removal, and readonly flags.
- Normal member runtime schema removes `HIDDEN` fields and components bound to hidden fields.
- Backend rejects direct normal-member writes to readable/hidden fields with HTTP 403.
- Deployed browser evidence shows the system backend `Schema 棰勮` action rendering the published schema and no horizontal overflow.

Remaining before `PROVEN`:

- Move or duplicate the page-designer schema entry into the natural module-management workflow instead of only the home/dashboard design panel.
- Drag/drop or visual component ordering, mobile preview, validation-error focus, and richer component types.
- Normal-member browser evidence for readonly runtime form rendering.
- Broader page designer usability audit and user acceptance.

## REC-P0-031 Advanced Table

User role: normal system member, system administrator

Business outcome: Runtime and admin list pages support modern table work: server paging/sort/filter, column visibility/order/fixed columns, saved views, row-click detail, and no duplicate detail buttons.

Coverage rows:

- `REQ-9.4` Advanced table
- Related `REQ-6.4`, `REQ-5.11`, `REQ-14.22`

Prototype/reference:

- `docs/user_requirement.md` sections 9.4 and 6.4

Frontend scope:

- Column settings, saved view selector, sort/filter controls, fixed-column affordance, row-click detail, and mobile containment.

Backend scope:

- Coded list query endpoint for saved view, server sort, filters, pagination, and permission-filtered columns.

Generator scope:

- Generated record/page reads are plumbing only.
- Query planning, saved views, column permissions, and view persistence are coded behavior.

Data scope:

- Saved table view, user column preferences, filter definitions, and audit where relevant.

Permission rule:

- Saved view cannot reveal hidden fields or data outside the current member scope.

Explicitly not complete if:

- Sorting/filtering is only frontend local filtering over one page.
- Row details require a duplicate "detail" button.
- Column settings do not persist.

Acceptance script:

1. Create enough records for multiple pages.
2. Save a table view with hidden/reordered/fixed columns and filters.
3. Reload/re-login and verify persistence.
4. Verify forbidden fields do not appear in saved views.

Status: `accepted` as first-loop evidence only; `REQ-6.4`, `REQ-5.11`, and `REQ-9` remain `PARTIAL`.

Accepted evidence:

- `scripts/recovery-r31-advanced-table-smoke.ps1`
- `docs/evidence/recovery/r31-advanced-table-2026-06-30.md`
- `docs/evidence/recovery/r31-advanced-table-result.json`
- `docs/evidence/recovery/screenshots/r31-advanced-table/advanced-table-browser-audit.json`
- `docs/evidence/recovery/screenshots/r31-advanced-table/desktop-saved-view.png`
- `docs/evidence/recovery/screenshots/r31-advanced-table/mobile-saved-view.png`

R31 proven scope:

- Runtime normal-member saved-view selector is backed by a runtime-safe scenes endpoint, not the admin scene API.
- Server paging, explicit sort, default scene sort, and field filters run through backend record search.
- Scene-driven list schema crops/orders columns, marks the first configured column fixed, and carries quick filter/default sort metadata.
- Hidden fields do not appear in saved-view columns or returned row fields.
- Saved view selection persists across re-login through backend scene state.
- Runtime rows open detail by row click and duplicate detail-like row buttons are removed.
- Desktop and mobile browser evidence shows the deployed table shell, fixed column, row-click detail, and no document-level horizontal overflow.

Remaining before `PROVEN`:

- Admin list pages need advanced-table parity.
- Saved views need clearer per-user ownership/preference scope.
- Batch actions, empty/error states, column width/fixed controls, keyboard/focus handling, accessibility, and larger role-journey usability review are still open.
- Final user acceptance remains separate from engineering evidence.

## REC-P0-032 Flow Simulation

User role: system administrator

Business outcome: Before publishing, an administrator can simulate a flow for a starter, conditions, and sample record data, then see the predicted nodes, approvers, blockers, and publish impact.

Coverage rows:

- `REQ-9.5` Flow simulation
- Related `REQ-5.12`, `REQ-6.9`

Prototype/reference:

- `docs/user_requirement.md` sections 9.5 and 5.12

Frontend scope:

- Simulation panel in flow designer.
- Starter selector, condition/sample payload editor, predicted path, approver list, blocker list, and trace.

Backend scope:

- Coded simulation endpoint that evaluates the draft or published flow without creating runtime instances.

Generator scope:

- Generated flow nodes/edges are plumbing only.
- Simulation execution, condition evaluation, assignee resolution, and blocker analysis are coded behavior.

Data scope:

- Optional simulation log with input snapshot and result trace.

Permission rule:

- Only system administrators with flow manage permission can simulate draft flows.

Explicitly not complete if:

- It only checks that nodes exist.
- It creates a real approval instance.
- It does not resolve approvers or conditions.

Acceptance script:

1. Build a flow with condition branches.
2. Simulate two different payloads.
3. Verify predicted nodes and approvers differ.
4. Verify no runtime approval instance is created.

Status: `accepted` as first-loop evidence only; `REQ-4.5`, `REQ-5.12`, `REQ-6.9`, and `REQ-9` remain `PARTIAL`.

Accepted evidence:

- `scripts/recovery-r32-flow-simulation-smoke.ps1`
- `docs/evidence/recovery/r32-flow-simulation-result.json`
- `docs/evidence/recovery/r32-flow-simulation-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r32-flow-simulation/flow-simulation-api-audit.json`
- `docs/evidence/recovery/screenshots/r32-flow-simulation/flow-simulation-browser-audit.json`
- `docs/evidence/recovery/screenshots/r32-flow-simulation/desktop-flow-simulation.png`
- `docs/evidence/recovery/screenshots/r32-flow-simulation/mobile-flow-simulation.png`

Proven in first loop:

- Draft canvas simulation chooses different paths for high and low amount payloads.
- Predicted approvers differ by branch.
- Missing approver configuration produces blocker code `E_SIM_APPROVER_EMPTY`.
- Simulation records a trace but does not create a runtime approval instance.
- Deployed browser shows the simulation panel/result, high/low branch behavior, and no document-level horizontal overflow on desktop/mobile.

Remaining:

- Full workflow breadth is still open: timers, external API nodes, field update nodes, richer node properties, publish impact breadth, runtime approval closure, and final user acceptance.

## REC-P0-033 Print Template Visual Designer

User role: system administrator, normal system member

Business outcome: A system administrator can create, preview, publish, and version a module-bound print template; a permitted runtime user can preview/export a record using the published template.

Coverage rows:

- `REQ-9.6` Print template visual designer
- Related `REQ-14.7`, `REQ-5.4`, `REQ-5.11`

Prototype/reference:

- `docs/user_requirement.md` sections 9.6 and 14.7

Frontend scope:

- Left module-field picker, center print preview, right page/header/footer/signature/detail-table configuration.
- Preview, publish-check, publish, version readback, and runtime preview/export entry.

Backend scope:

- Coded print template service for draft, publish-check, snapshot publish, runtime merge, preview metadata, and export/PDF placeholder or actual export according to implementation choice.

Generator scope:

- Generated template records are plumbing only.
- Template rendering, field binding, versioning, permission, and preview/export are coded behavior.

Data scope:

- Print template draft, published snapshot, version, module binding, record render audit, and generated file metadata if PDF/export is produced.

Permission rule:

- System administrators manage templates.
- Runtime users can only preview/export templates and records they can read.

Explicitly not complete if:

- It is only a static print mockup.
- It does not bind to module fields.
- Runtime preview ignores permissions or draft/published separation.

Acceptance script:

1. Create module fields and a template draft.
2. Preview with sample/real record data.
3. Publish-check and publish.
4. Log in as normal member and preview/export a permitted record.
5. Verify forbidden record/template access is denied.

Status: `accepted` as first-loop evidence only; `REQ-9`, `REQ-14.7`, `REQ-5.4`, and `REQ-5.11` remain `PARTIAL`.

Accepted evidence:

- `scripts/recovery-r33-print-template-smoke.ps1`
- `docs/evidence/recovery/r33-print-template-result.json`
- `docs/evidence/recovery/r33-print-template-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r33-print-template/print-template-api-audit.json`
- `docs/evidence/recovery/screenshots/r33-print-template/print-template-browser-audit.json`
- `docs/evidence/recovery/screenshots/r33-print-template/runtime-print-desktop.png`
- `docs/evidence/recovery/screenshots/r33-print-template/runtime-print-mobile.png`

Proven in first loop:

- System admin can save a module-bound print template draft with header/footer/signature/detail-table and module field bindings.
- Admin preview renders field values, detail-table rows, version metadata, trace id, and published/draft state.
- Publish-check validates bound fields and writes a published print template version.
- Runtime print preview/export uses only the published snapshot for a readable record and returns an export file id.
- Normal member runtime preview before publish is denied, and normal member admin template write is denied with HTTP 403.
- Deployed browser evidence shows the print designer and runtime print preview/export on asset `/assets/index-ByJqmpyP.js`; desktop and mobile document overflowX are `0`.

Remaining:

- First runtime direct navigation showed an empty/loading state until reload even though the API had data; this needs a follow-up runtime refresh-state fix if reproduced.
- Export is currently HTML/file-id output, not a verified PDF renderer with pagination/header/footer print fidelity.
- Drag/drop layout, richer template components, page break rules, preview of multiple records, printer/PDF settings, and accessibility/focus evidence remain open.
- Broader module lifecycle, data-record, and list/detail usability review and final user acceptance remain separate.

## REC-P0-034 Final Goal Execution Framework V4 Lock

User role: project conductor, implementation worker, test/verifier agent

Business outcome: Future work cannot again drift into page/API/batch completion claims while the real final target remains unfinished. The framework must force every next task to come from requirement gaps or deployed journey gaps, carry an evidence contract, and keep user signoff separate.

Coverage rows:

- Process/governance support for all `PARTIAL` rows in `docs/framework/final-requirement-coverage-ledger.md`

Prototype/reference:

- User correction on 2026-06-30: final target is a human-usable no-code customizable platform, not R-batch completion or a copied prototype shell.
- `.cursor/architecture/final-goal-framework.md`
- `docs/framework/next-execution-ledger.md`
- `docs/framework/final-requirement-coverage-ledger.md`

Frontend scope:

- None directly. This task changes execution framework and validation before future frontend work.

Backend scope:

- None directly. This task changes execution framework and validation before future backend work.

Generator scope:

- None. Generated CRUD cannot satisfy this task.

Data scope:

- Framework/session/evidence files:
  - `.cursor/architecture/final-goal-framework.md`
  - `.cursor/workflows/final-goal-recovery.md`
  - `.cursor/templates/task.md`
  - `.cursor/session/state.json`
  - `docs/framework/next-execution-ledger.md`
  - `docs/evidence/final-goal-framework-audit-result.json`

Permission rule:

- Only explicit user signoff may close `gates.user_script_passed`.
- Agents may add engineering next tasks and evidence but may not convert partial requirement rows to final completion without matching deployed proof.

States:

- Framework healthy, framework unhealthy, requirements partial, requirements closed, nextTasks missing, nextTasks present, signoff open.

Explicitly not complete if:

- `build_plan.nextTasks` is empty while requirement rows are `PARTIAL` or `OPEN`.
- A task can be started without a role, entry point, business outcome, generated-vs-coded boundary, and evidence contract.
- The process can imply final completion while `gates.user_script_passed=false`.
- Framework health is claimed without running `scripts/final-goal-framework-audit.ps1`.
- Future reusable framework extraction becomes the current workstream before this product is complete.

Acceptance script:

1. Update `.cursor` framework/workflow/template files with v4 execution locks.
2. Add executable next work in `docs/framework/next-execution-ledger.md`.
3. Set `.cursor/session/state.json` `build_plan.nextTasks` to the next FRC work.
4. Run `scripts/final-goal-framework-audit.ps1`.
5. Run `scripts/final-requirement-coverage-audit.ps1 -NoFailExit` and confirm final completion remains blocked.

Evidence paths:

- `scripts/final-goal-framework-audit.ps1`
- `docs/evidence/final-goal-framework-audit-result.json`
- `docs/evidence/final-goal-framework-audit.md`

Status: `accepted` as framework-health evidence only; product requirements remain `PARTIAL`.

## REC-P0-035 Visual And Wording Role-Journey Audit

User role: platform administrator, platform member, system administrator, normal system member

Business outcome: The deployed system must be inspected as a role-based product rather than a collection of pages. Core platform, system, runtime, work, todo, message, and admin routes must not show stacked unrelated surfaces, misleading wording, duplicate detail actions, stale demo text, mojibake, permission leakage, or mobile/desktop overflow.

Coverage rows:

- `REQ-6.1` Overall visual style
- `REQ-6.2` Web main layout
- `REQ-6.3` Home page
- `REQ-6.11` Mobile
- Related `REQ-9` advanced capability surfaces

Prototype/reference:

- User correction on 2026-06-27 and 2026-06-30: the target is a human-usable system, not copied prototype pages or batch completion.
- `.cursor/knowledge/failure-lessons.md`
- `docs/framework/next-execution-ledger.md`

Frontend scope:

- Platform workbench/admin shell.
- System dashboard, runtime module list/detail shell, work, todos, messages, and system admin.
- Desktop 1280x720 and mobile 390x720 containment.

Backend scope:

- No new business API for this audit. The script creates disposable real systems, modules, fields, records, roles, members, and permissions through existing APIs so the frontend is inspected against deployed real data.

Generator scope:

- None. Generated CRUD cannot satisfy human-usability evidence.

Data scope:

- Disposable R35 systems/modules/records/members only, cleaned by default.
- Browser screenshots and machine-readable route metrics.

Permission rule:

- Normal members must not see platform admin or system admin shells.
- Admin-only routes must render denied states for unauthorized normal users.

States:

- Loading, empty, denied, desktop/mobile, route reload, admin role, normal-member role, created data, cleanup.

Explicitly not complete if:

- The audit only checks HTTP 200 or build output.
- Any inspected route has document overflow, clipped controls, duplicate detail-like actions, confusing placeholder/demo/generic toast text, mojibake, stale loading text after settle, or normal-member admin shell exposure.
- The result is treated as final user signoff.

Acceptance script:

1. Run `scripts/recovery-r35-visual-wording-role-journey-audit.ps1` against the deployed release.
2. Create disposable admin and normal-member data.
3. Inspect 16 role routes across desktop and mobile viewports.
4. Persist screenshots and JSON metrics.
5. Fail on visual/wording/role leakage blockers or warnings.
6. Keep `gates.user_script_passed=false`.

Status: `accepted` as deployed visual/wording role-journey engineering evidence only; final product requirements remain `PARTIAL`.

Accepted evidence:

- `scripts/recovery-r35-visual-wording-role-journey-audit.ps1`
- `docs/evidence/recovery/r35-visual-wording-role-journey-result.json`
- `docs/evidence/recovery/r35-visual-wording-role-journey-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r35-visual-wording-role-journey/visual-wording-role-journey-audit.json`
- `docs/evidence/recovery/screenshots/r35-visual-wording-role-journey/`

R35 proven scope:

- Deployed asset `/assets/index-Bnn6q9PB.js` was inspected.
- 16 routes across platform workbench/admin, system dashboard/runtime/work/todos/messages/admin, and normal-member denied admin routes were checked on desktop and mobile.
- Result count `32`, failure count `0`, warning count `0`.
- No document overflow, clipped controls, duplicate detail actions, placeholder/demo/generic-toast text, mojibake, stale loading text after settle, or normal-member admin shell exposure was reported.
- The earlier runtime loading-plus-empty stack and platform workbench duplicate system-card action issue were corrected before the passing audit.

Remaining:

- R35 is a breadth audit, not final acceptance. It does not prove page-designer module-entry/readonly browser flow, runtime first-navigation robustness, admin table parity, full print/PDF fidelity, or user signoff.
- `REQ-6.1`, `REQ-6.2`, `REQ-6.3`, `REQ-6.11`, and `REQ-9` stay `PARTIAL` until broader requirement rows and user acceptance close.

## REC-P0-036 Page Designer Module Entry And Runtime Readonly

User role: system administrator, normal system member

Business outcome: A system administrator configures a module page from the natural module-management workflow, publishes the page schema, and a normal system member sees the published runtime form as readonly with hidden fields/components removed. Backend write denial must still hold even if the browser is bypassed.

Coverage rows:

- `REQ-5.8` Pages
- `REQ-6.8` Page designer
- `REQ-6.11` Mobile
- Related `REQ-9` schema-driven page capability

Prototype/reference:

- `docs/user_requirement.md` sections 5.8, 6.8, 9.3
- R30 remaining work: module-management entry placement and normal-member browser readonly evidence.

Frontend scope:

- Move module page designer into the selected module work panel in system backend module management.
- Keep dashboard/home configuration focused on home-page configuration only.
- Runtime create/edit panel renders published schema fields as readonly for readable-but-not-writable roles and does not render hidden fields/components.

Backend scope:

- Existing coded page designer, publish, page schema, runtime page schema, permission masking, and runtime record write permission services.

Generator scope:

- Generated page/module tables remain persistence plumbing only.
- Module workflow placement, schema composition, permission pruning, readonly rendering, and browser evidence are coded product behavior.

Data scope:

- Module page draft/published snapshot, module fields, role field permissions, runtime schema, dynamic record data, browser screenshots, and cleanup of disposable systems.

Permission rule:

- System administrators can manage module pages.
- Normal members can read only published module page schema under current system member permissions.
- Normal members with read-only role cannot create records and cannot see `HIDDEN` fields/components.

States:

- Draft page, published page, schema preview, readonly runtime form, hidden field, forbidden create, desktop, mobile, cleanup.

Explicitly not complete if:

- Page designer is only available from a generic dashboard/home panel or the first module rather than the selected module workflow.
- Normal-member readonly behavior is proven only by API and not by deployed browser.
- Hidden fields or components leak in runtime UI.
- Direct API write succeeds for a readonly normal member.
- Evidence uses source build only instead of the deployed release.

Acceptance script:

1. Run `scripts/recovery-r36-page-designer-module-entry-readonly-smoke.ps1` against the deployed release.
2. Create a disposable system, module, fields, page schema, readonly normal-member role, and runtime record.
3. Publish the module page and assert admin/normal schema versions match.
4. Browser-check module management shows the module page designer under the selected module and dashboard config no longer mixes module page designer.
5. Browser-check normal-member runtime form renders readonly fields on desktop and mobile with no horizontal overflow.
6. Assert hidden field/component non-leakage and API forbidden create `403`.
7. Keep `gates.user_script_passed=false`.

Status: `accepted` as deployed FRC-1B engineering evidence only; broader page designer/product requirements remain `PARTIAL`.

Accepted evidence:

- `scripts/recovery-r36-page-designer-module-entry-readonly-smoke.ps1`
- `docs/evidence/recovery/r36-page-designer-module-entry-readonly-result.json`
- `docs/evidence/recovery/r36-page-designer-module-entry-readonly-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r36-page-designer-module-entry-readonly/page-designer-browser-audit.json`
- `docs/evidence/recovery/screenshots/r36-page-designer-module-entry-readonly/`

R36 proven scope:

- Deployed asset `/assets/index-D7YKdz44.js` was inspected.
- System admin module management contains the selected module's page designer and schema preview.
- Dashboard/home configuration no longer shows the module page designer surface.
- Normal runtime schema version equals the published admin schema version.
- Normal runtime form rendered five readonly fields in desktop/mobile browser evidence.
- `secretNote` and its bound hidden component did not leak to normal-member runtime schema/UI.
- Direct normal-member record create returned HTTP `403`.
- Browser result count `5`, browser overflow count `0`, cleanup deleted created systems.

Remaining:

- R36 does not close richer page-designer component catalog, drag/drop/copy, validation-error focus, or user acceptance.
- `REQ-5.8`, `REQ-6.8`, `REQ-6.11`, and `REQ-9` remain `PARTIAL`.

## REC-P0-037 Runtime First Navigation And List/Detail Usability

User role: normal system member

Business outcome: A normal system member can log in through the deployed form, open the runtime module URL directly, and immediately see the authorized module list, record row, detail panel, and published print preview without manually refreshing the browser.

Coverage rows:

- `REQ-4.4` Application runtime
- `REQ-5.11` Data records
- `REQ-6.4` List pages
- `REQ-6.6` Detail pages
- `REQ-6.11` Mobile

Prototype/reference:

- `docs/user_requirement.md` sections 4.4, 5.11, 6.4, 6.6, and 6.11
- FRC-3A in `docs/framework/next-execution-ledger.md`

Frontend scope:

- Runtime record state is scoped to the active system context.
- Initial runtime loading renders a dedicated loading state instead of stale or empty list/detail content.
- Direct non-hash deployed routes normalize to the runtime hash route and load current system data on first navigation.
- Record-scoped UI state such as print preview is cleared when the active record changes.

Backend scope:

- Existing coded runtime module, schema, record search/detail, permission, page schema, and print preview APIs.

Generator scope:

- Generated module/record tables remain persistence plumbing only.
- Runtime context scoping, first-navigation state handling, list/detail rendering, print preview wiring, and permission-negative evidence are coded product behavior.

Data scope:

- Disposable system, tenant, module, fields, scene, normal-member role, runtime record, print template, browser screenshots, and cleanup of created systems.

Permission rule:

- Normal members with read-only runtime permission can read the published module, list, detail, and print preview.
- Direct normal-member record create is rejected with HTTP `403`.

States:

- Real login, direct deployed URL, loading, list, row selection, detail, print preview, desktop, mobile, forbidden create, cleanup.

Explicitly not complete if:

- The first direct runtime navigation shows empty/loading/stale data until manual reload.
- Browser evidence uses API login only instead of the deployed login form.
- The record exists by API but is not visible in the deployed browser list/detail/print preview.
- Mobile first navigation overflows horizontally.
- A read-only normal member can create records through the API.

Acceptance script:

1. Run `scripts/recovery-r37-runtime-first-navigation-smoke.ps1` against the deployed release.
2. Create a disposable system, module, fields, scene, print template, record, and read-only normal-member account.
3. Log in through the deployed `/login` form.
4. Open direct non-hash `/systems/{systemId}/modules` on desktop and mobile.
5. Assert the route normalizes, list row/detail/print preview render, and the record value is visible without reload.
6. Assert no runtime empty state, loading state, or document horizontal overflow remains.
7. Assert direct normal-member create returns HTTP `403`.
8. Keep `gates.user_script_passed=false`.

Status: `accepted` as deployed FRC-3A engineering evidence only; broader runtime and product requirements remain `PARTIAL`.

Accepted evidence:

- `scripts/recovery-r37-runtime-first-navigation-smoke.ps1`
- `docs/evidence/recovery/r37-runtime-first-navigation-result.json`
- `docs/evidence/recovery/r37-runtime-first-navigation-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r37-runtime-first-navigation/runtime-first-navigation-browser.json`
- `docs/evidence/recovery/screenshots/r37-runtime-first-navigation/`

R37 proven scope:

- Deployed asset `/assets/index-CDOOMXu_.js` was inspected.
- Normal member logged in through the deployed login form.
- Desktop `1280x720` and mobile `390x720` direct `/systems/726/modules` first navigation normalized to `#/systems/726/modules`.
- Browser result count `2`, blocker count `0`, overflow count `0`.
- Runtime table, first row, detail panel, and published print preview rendered on first navigation without manual reload.
- Record `R37 First Navigation Record 0630191726034_a6f571` was visible in list/detail/print preview.
- API readback found `1` matching record.
- Direct normal-member record create returned HTTP `403`.
- Cleanup deleted created systems `726` and `727`.

Remaining:

- R37 does not close full runtime CRUD breadth, admin table parity, batch actions, rich detail tabs, attachments/history/approval/log actions, broader mobile task usability, or user acceptance.
- `REQ-4.4`, `REQ-5.11`, `REQ-6.4`, `REQ-6.6`, and `REQ-6.11` remain `PARTIAL`.

## REC-P0-038 Module Lifecycle And Admin Table Parity

User role: system administrator

Business outcome: A system administrator can manage a module from the deployed system backend as one coherent configuration workflow: select a module from an admin table, configure fields, list scene columns/filters/sorts, actions, import/export rules, page design, print template, run publish-check, publish, roll back, and read the changed state back without leaving module management.

Coverage rows:

- `REQ-4.3` Application configuration center
- `REQ-5.4` Modules
- `REQ-6.4` List pages
- Related `REQ-9` admin advanced-table parity

Prototype/reference:

- `docs/user_requirement.md` sections 4.3, 5.4, 6.4, and 9
- FRC-2A in `docs/framework/next-execution-ledger.md`

Frontend scope:

- Module management uses an admin table/list hybrid with visible filters, published/status columns, current version, and selected-row detail.
- The selected module work panel includes list-scene configuration and import/export configuration, not only fields/page/print.
- Publish-check, publish, and rollback show real result messages with traceId/version and trigger readback reload.
- Desktop and mobile module management stay inside the viewport without document-level horizontal overflow.

Backend scope:

- Existing coded module config APIs for module, field, action, scene, list schema, import/export config, publish-check, publish, and rollback.
- No new generated CRUD can close this task by itself.

Generator scope:

- Generated module tables and base services remain persistence plumbing.
- Scene composition, import/export rule persistence, module lifecycle state changes, publish-check impact refs, rollback state, admin table rendering, and browser/readback evidence are coded product behavior.

Data scope:

- Disposable system, module group, module, fields, list scene, action, import/export configuration, publish versions, browser screenshots, and cleanup of created systems.

Permission rule:

- System administrators can configure and publish modules in their current system member context.
- Normal members must not access system backend module management directly.

States:

- Empty module list, filtered admin list, selected module, draft config, publish-check pass/fail, published module, rolled-back module, import/export enabled state, scene schema readback, desktop, mobile, forbidden normal-member admin route, cleanup.

Explicitly not complete if:

- Scene/list-schema or import/export configuration can only be changed by API and has no deployed browser entry.
- Module publish/rollback returns a response but the module list/detail does not read back the changed status/version.
- The module list is just stacked cards with no table-like columns or filtering state.
- Mobile module management creates document-level horizontal overflow.
- Normal members can open the system backend module management shell.

Acceptance script:

1. Run `scripts/recovery-r38-module-lifecycle-admin-table-smoke.ps1` against the deployed release.
2. Create a disposable system, module group, module, fields, list scene, action, import/export config, page/print prerequisites as needed.
3. Verify API readback for scene schema, import/export config, publish-check, publish, rollback, and module detail/list status/version.
4. Browser-check system admin module management shows the admin table, selected module workspace, scene config, import/export config, publish/check/rollback results, and no document overflow on desktop/mobile.
5. Browser-check normal-member direct system admin route is denied.
6. Keep `gates.user_script_passed=false`.

Status: `accepted` as deployed FRC-2A engineering evidence only; broader module/list/product requirements remain `PARTIAL`.

Accepted evidence:

- `scripts/recovery-r38-module-lifecycle-admin-table-smoke.ps1`
- `docs/evidence/recovery/r38-module-lifecycle-admin-table-result.json`
- `docs/evidence/recovery/r38-module-lifecycle-admin-table-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r38-module-lifecycle-admin-table/`

R38 proven scope:

- Deployed asset `/assets/index-i69FrJ3O.js` was inspected.
- System admin direct route `/systems/{systemId}/admin/module-config` opens module management without relying on a fragile in-page click.
- Module management shows a table/list hybrid, selected module workspace, lifecycle panel, scene/list-schema panel, and import/export panel.
- API readback proved module lifecycle update, action readback, list schema with `3` columns, `6` filters, `1` sorter, import/export mappings count `3`, publish-check pass, published version `MODULE_v1782820734922`, and rollback result `ROLLED_BACK`.
- Desktop and mobile browser evidence reported browser result count `2`, blocker count `0`, overflow count `0`.
- Normal member direct runtime create returned HTTP `403`; normal member backend module-action read returned HTTP `403`.
- Cleanup deleted created systems `735` and `736`.

Remaining:

- R38 does not close full print/PDF fidelity, full runtime data-record breadth, all admin list batch/empty/error/accessibility states, all module permissions/field-type permutations, or user acceptance.
- `REQ-4.3`, `REQ-5.4`, `REQ-6.4`, `REQ-9`, and `REQ-14` remain `PARTIAL`.

## REC-P0-039 Print/PDF Fidelity And Launch Rule Split

User role: system administrator and system normal member

Business outcome: A system administrator can configure a module-bound print template with page size, orientation, margins, header/footer, detail table, and signatures; after publish, a permitted normal member can preview and export a record with the same published template, with print-ready HTML/CSS metadata that can be verified from API and deployed browser evidence.

Coverage rows:

- `REQ-9.6` Print template visual designer
- `REQ-14.7` Print/export launch rule
- `REQ-14.1-14.37` Launch capability rules split for evidence tracking
- Related `REQ-5.4`, `REQ-5.11`, `REQ-6.11`

Prototype/reference:

- `docs/user_requirement.md` sections 5.4, 5.11, 9.6, and 14
- FRC-5A in `docs/framework/next-execution-ledger.md`
- R33 first-loop print template evidence

REQ-14 split for this task:

- 14.1-14.6: versioning, publish lifecycle, business rule check, uniqueness, rollback, and publish impact must be visible as split evidence, not one merged "publish works" claim.
- 14.7: print/export must prove page setup, published-template separation, runtime permission, export format, content type, pagination CSS, page-break CSS, and record value merge.
- 14.8-14.16: KPI, health, permission preview, archive/restore, confirmations, UX states, APIs, error states, and idempotency remain tracked as launch-rule rows; this task may only add evidence it actually exercises.
- 14.17-14.25: rate limit, cache, design system, performance, reports, audit, gray release, quotas, and masking remain `PARTIAL` unless directly proven by script.
- 14.26-14.37: accessibility, data design, and remaining launch controls remain `PARTIAL` unless directly proven by script.

Frontend scope:

- System admin module management print designer exposes page setup controls: paper, orientation, margins, header/footer, signature labels, field binding, detail-table fields, publish-check, publish, and preview.
- Runtime detail print tab shows the same page setup and export boundary metadata.
- Desktop and mobile deployed browser checks must prove print preview/export areas are visible without page-level horizontal overflow.

Backend scope:

- Print template save/readback persists normalized page setup.
- Publish-check checks page setup and returns split warnings for missing detail table or signature area.
- Runtime print export returns `exportFileId`, `exportMeta`, `pageSetup`, `contentType`, `HTML_PRINT` format, CSS readiness, pagination readiness, estimated page count, and print-ready HTML with `@page`, `@media print`, table header repeat, and page-break avoidance rules.
- This is an HTML print boundary unless a real PDF engine is added or the user explicitly approves HTML export as the current delivery boundary.

Generator scope:

- Generated print-template and publish-version tables/base services are persistence plumbing only.
- Page setup normalization, publish-check rule split, export metadata, print-ready HTML rendering, browser display, and permission readback are coded product behavior.

Data scope:

- Disposable system, module group, module, fields, scene, role/member, record, print template draft/published snapshot, runtime preview/export result, publish version, browser screenshots, and cleanup of created systems.

Permission rule:

- System administrators can save, preview, publish-check, and publish print templates in their current system member context.
- Normal members can preview/export only published templates for records they can read.
- Normal members must not write admin print-template configuration.

States:

- Draft template, draft runtime denial, publish-check pass with warnings, published template, runtime preview, runtime export, HTML print boundary, desktop, mobile, forbidden normal-member admin write, cleanup.

Explicitly not complete if:

- Export returns only a fake file id with no format/content type/CSS/page setup metadata.
- The browser preview ignores the saved page setup.
- Runtime preview uses a draft template.
- Normal members can create or update print templates.
- The task claims PDF completion without a real PDF engine or explicit user-approved HTML-only boundary.
- REQ-14.x remains a single undifferentiated row with no split evidence notes.

Acceptance script:

1. Run `scripts/recovery-r39-print-pdf-launch-rule-smoke.ps1` against the deployed release.
2. Create a disposable system, module, fields, runtime role/member, record, and print template with page setup.
3. Verify draft runtime preview is denied before publish.
4. Verify publish-check returns pass, impact refs, and launch-rule warnings where expected.
5. Publish the template, then normal-member runtime preview/export must read the published version and record values.
6. Assert export metadata includes `HTML_PRINT`, `text/html`, `printCssReady=true`, `paginationReady=true`, `@page`, `@media print`, and page-break CSS in the HTML.
7. Browser-check admin print designer and runtime print preview/export on desktop/mobile with document overflow `0`.
8. Verify normal-member admin template write is denied.
9. Cleanup created systems and keep `gates.user_script_passed=false`.

Status: `accepted` as deployed FRC-5A engineering evidence only; broader launch and PDF requirements remain `PARTIAL`.

Accepted evidence:

- `scripts/recovery-r39-print-pdf-launch-rule-smoke.ps1`
- `docs/evidence/recovery/r39-print-pdf-launch-rule-result.json`
- `docs/evidence/recovery/r39-print-pdf-launch-rule-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r39-print-pdf-launch-rule/`

R39 proven scope:

- Deployed asset `/assets/index-CXChJ5rg.js` was inspected after release packaging and restart.
- API readback proved warning-template launch-rule split with `warningCount=2`, draft runtime preview denied with HTTP `403`, publish-check pass with impact types `REFRESH_TEMPLATE`, `PRINT_READY_HTML`, and `CHECK_RUNTIME_READ`, published version `PRINT_TEMPLATE_v1782822414358`, runtime preview on the same published version, and normal-member admin write denied with HTTP `403`.
- Runtime export returned `HTML_PRINT`, `text/html; charset=utf-8`, `printCssReady=true`, `paginationReady=true`, `estimatedPageCount=1`, `@page`, `@media print`, page-break CSS, and merged record values.
- Browser evidence covered admin print designer and runtime print export on desktop and mobile: `browserResultCount=4`, `browserOverflowCount=0`, `browserBlockerCount=0`.
- Cleanup deleted created systems `745` and `746`.

Remaining:

- R39 proves a browser-print-ready HTML export boundary, not binary PDF generation.
- REQ-14.1-14.37 remain `PARTIAL` until non-print launch rules such as rate limit, cache, performance, reports, gray release, quotas, masking, accessibility, and full data-design evidence are split and proven or user-excluded.
- `REQ-5.4`, `REQ-5.11`, `REQ-6.11`, `REQ-9`, and `REQ-14` remain `PARTIAL`; user signoff remains open.

## REC-P0-040 Runtime Record Breadth And Detail-Action Closure

User role: system normal member, with system administrator as setup/control role

Business outcome: A permitted normal member can use a published runtime module as a daily work surface: open the list, create or edit a record, save a draft, upload and bind a real attachment, return to detail, see attachments/history/logs/print/action state, run permitted row/detail actions, reload or re-login, and still read back the persisted result. A system administrator can configure the module, but normal members cannot enter system backend configuration.

Coverage rows:

- `REQ-4.4` Application runtime
- `REQ-5.11` Data records
- `REQ-6.6` Detail pages
- Related `REQ-5.13`, `REQ-5.14`, `REQ-6.4`, `REQ-6.5`, `REQ-6.11`

Prototype/reference:

- `docs/user_requirement.md` sections 4.4, 5.11, 5.13, 5.14, 6.4, 6.5, 6.6, and 6.11
- FRC-4A in `docs/framework/next-execution-ledger.md`
- R3 attachment/history/approval API evidence, R17/R18 runtime browser evidence, R31 advanced table evidence, and R37 first-navigation evidence

Frontend scope:

- Runtime record edit/create panel uses real upload instead of a raw attachment-id text box.
- Existing attachments are preserved during edit unless the user removes/replaces them.
- Detail tabs expose business-specific base fields, attachments, print templates/results, and operation/history records.
- Save draft, save record, submit approval, delete, row actions, print preview/export, import/export, and list refresh show visible result state and then read back from the API.
- Desktop and mobile browser checks must prove the list/detail/edit/attachment/history surfaces stay within the viewport.

Backend scope:

- Runtime detail returns explicit `attachments`, `print`, and `operationLogs/history` tab payloads rather than hiding them in the base payload.
- Runtime create/update binds uploaded attachment file ids without deleting existing attachments when no attachment change is submitted.
- Runtime history endpoint and detail payload expose action, operator, trace/audit ids, operated time, and field diffs.
- Permissions continue to enforce record read/create/edit/delete/action and data scope.

Generator scope:

- Dynamic record/value/history/draft/attachment generated tables and base services are persistence plumbing only.
- Detail tab composition, attachment upload/bind/readback, history/log rendering, action result state, permission negative evidence, and browser workflow are coded product behavior.

Data scope:

- Disposable system, tenant, module group, module, fields, list scene, runtime role/member, uploaded file, draft, records, record values, attachment binding, history rows, optional approval/action state, browser screenshots, and cleanup of created systems.

Permission rule:

- Normal members can access only permitted runtime modules and records in the active system member/tenant/data-scope context.
- Normal members must not enter system backend configuration or mutate module configuration.
- A member without create/edit/delete/action permission must receive a backend denial or disabled reason, not a fake success state.

States:

- First load, empty list, filtered list, create, edit, draft saved/readback, validation/failure, uploaded attachment, saved detail, operation history, print tab, delete/action result, reload readback, mobile containment, forbidden admin/config access, cleanup.

Explicitly not complete if:

- Attachments are represented only by manually typed ids or fake file ids.
- Editing a record silently clears existing attachments.
- The detail history/log tab does not read real backend history or trace/audit data.
- Save/delete/action returns a response but the list/detail does not refresh and read back persisted data.
- Detail tabs exist visually but show generic placeholders instead of record-specific content.
- Permission negative cases are not checked from the backend and deployed browser route.
- The task claims final runtime completion while broader requirement rows or user signoff remain open.

Acceptance script:

1. Run `scripts/recovery-r40-runtime-record-detail-action-smoke.ps1` against the deployed release.
2. Create a disposable system/module/fields/scene/role/member and publish the module.
3. Upload a real file through `/api/v1/uploads/files?sourceType=RUNTIME_RECORD`.
4. Save a draft with field values and attachment id, then read back the draft.
5. Create a record with the uploaded attachment, update one field without losing the attachment, and read back list/detail/history.
6. Verify detail tabs expose base fields, attachment metadata/download URL, print templates or print empty state, and operation/history trace/audit ids.
7. Execute at least one permitted row/detail action or verify disabled reason, then refresh and read back action/history state.
8. Browser-check normal-member runtime list/detail/edit/attachment/history on desktop and mobile with document overflow `0`.
9. Verify normal-member direct system admin route or config write is denied.
10. Cleanup created systems and keep `gates.user_script_passed=false`.

Status: `accepted` as deployed FRC-4A engineering evidence only; broader runtime/product requirements remain `PARTIAL`.

Accepted evidence:

- `scripts/recovery-r40-runtime-record-detail-action-smoke.ps1`
- `docs/evidence/recovery/r40-runtime-record-detail-action-result.json`
- `docs/evidence/recovery/r40-runtime-record-detail-action-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r40-runtime-record-detail-action/runtime-record-detail-action-browser.json`

Proven:

- Normal member uploaded a real runtime attachment file and bound its returned file id to a draft and record.
- Draft readback preserved field values and attachment ids.
- Record update without `attachmentIds` preserved the existing attachment instead of clearing it.
- Runtime detail returned tabs `base`, `attachments`, `print`, and `operationLogs`.
- Detail readback included attachment metadata, `2` latest history rows, and history endpoint total `2`.
- Delete action returned `DELETED`; archive action was accepted and added `record.archive` to history.
- Normal-member backend admin action API returned HTTP `403`.
- Deployed desktop and mobile browser evidence on asset `/assets/index-DQnwg1ks.js` proved edit uploader, attachment detail, history list, record value, attachment name, browser blocker count `0`, and overflow count `0`.
- Cleanup deleted systems `755` and `756`.

Still partial:

- R40 is a runtime data-record breadth slice, not final product completion.
- Approval detail breadth, import/export rollback UX, file version/preview permission breadth, batch actions, empty/error/accessibility states, broader mobile task usability, and user signoff remain open.
- `REQ-4.4`, `REQ-5.11`, `REQ-5.13`, `REQ-5.14`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, and `REQ-6.11` remain `PARTIAL`.

## REC-P0-041 Page/Home And Component Usability Closure

User role: new administrator, system administrator, normal system member

Business outcome: A user can enter the product through platform/system home pages and page-designed runtime surfaces without seeing stacked configuration blocks, generic placeholders, or component behavior that looks present but cannot be used. The page/home layer should feel like an actual work surface, not a copied prototype shell.

Coverage rows:

- `REQ-5.8` Pages
- `REQ-6.1` Overall visual style
- `REQ-6.3` Home page
- `REQ-6.8` Page designer
- Related `REQ-9`

Prototype/reference:

- `docs/user_requirement.md` sections 5.8, 6.1, 6.3, 6.8, and 9
- FRC-1 missing product surfaces in `docs/evidence/final-requirement-gap-report.md`
- FRC-1C in `docs/framework/next-execution-ledger.md`

Frontend scope:

- Audit and harden platform workbench, system dashboard/home, module page designer, and runtime page-designed form/list surfaces.
- Remove or wire any component-level fake success, generic placeholder, stale demo text, or visually stacked configuration area found in the FRC-1C route set.
- Prove validation, empty, disabled, loading, read-only, hidden-field, and mobile states for page-designed runtime components.

Backend scope:

- Reuse existing page/home/schema APIs where possible.
- Add only the missing readback or state contract needed to prove component behavior; do not add decorative APIs.

Generator scope:

- Generated page/config persistence is plumbing only.
- Page composition, schema enforcement, hidden/readonly behavior, visible state, validation result, and deployed-browser usability are coded product behavior.

Data scope:

- Disposable system, homepage config, module, fields, page design snapshot, runtime record, normal member, browser screenshots, and cleanup.

Permission rule:

- System administrators can configure page/home surfaces.
- Normal members can only read and use permitted runtime surfaces; hidden fields/components must not leak and read-only fields must not submit writes.

States:

- Fresh home/dashboard load, configured homepage readback, page designer edit/readback, runtime schema readback, validation/disabled/readonly/hidden states, empty components, desktop/mobile containment, no-permission route or action denial, cleanup.

Explicitly not complete if:

- A page exists but its components are static placeholders.
- Home/dashboard still stacks unrelated configuration and runtime content.
- Runtime page components render but ignore validation, read-only, hidden-field, or permission rules.
- Browser evidence only checks that a route opens, without proving visible component state and API readback.
- The task claims final completion while requirement rows or user signoff remain open.

Acceptance script:

1. Run a new deployed FRC-1C script against `http://127.0.0.1:18131`.
2. Create a disposable system/module/page design and publish it.
3. Verify admin homepage/page designer readback and runtime normal-member schema readback.
4. Verify hidden/readonly/validation states from API and deployed browser.
5. Browser-check platform workbench, system dashboard, page designer, and runtime page form/list on desktop and mobile for overflow, clutter, placeholders, and stale/fake wording.
6. Verify normal-member write denial where fields/actions are read-only or forbidden.
7. Cleanup created systems and keep `gates.user_script_passed=false`.

Status: `accepted` as deployed FRC-1C engineering evidence only; `REQ-5.8`, `REQ-6.1`, `REQ-6.3`, `REQ-6.8`, `REQ-9`, and user signoff remain `PARTIAL`.

Accepted evidence:

- `scripts/recovery-r41-page-home-component-usability-smoke.ps1` PASS against `http://127.0.0.1:18131`.
- `docs/evidence/recovery/r41-page-home-component-usability-result.json`
- `docs/evidence/recovery/r41-page-home-component-usability-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r41-page-home-component-usability/page-designer-browser-audit.json`
- Release verification passed on deployed assets `/assets/index-CbTvbGMT.js` and `/assets/index-CQNpsfh0.css`.

Proven:

- Disposable systems `759` and `760` were created and cleaned up.
- Admin system dashboard rendered real home overview and operations panels with stable browser markers.
- Admin runtime schema form displayed required-field validation for `title`, `status`, and `publicName`; after filling required fields, validation errors cleared.
- Module page designer rendered inside selected module management with a published page schema preview.
- Normal-member runtime schema read the published page version, marked 5 fields readonly, hid `secretNote`, removed the hidden component, and rejected direct create with HTTP `403`.
- Desktop/mobile browser evidence reported `browserOverflowCount=0`.
- Readonly disabled reasons are Chinese user-facing copy, not English technical fallback.

## REC-P0-042 Page Designer Component Breadth And Accessibility Closure

Status: `accepted` as deployed FRC-1D engineering evidence only; broader FRC-1 usability and user signoff remain `PARTIAL`.

User role: system administrator, normal system member

Business outcome: A system administrator can configure richer page components without the page designer feeling like a thin schema preview, and a normal system member can use the published page-designed runtime surface with keyboard/focus/accessibility-friendly states on desktop and mobile.

Coverage rows:

- `REQ-5.8` Pages
- `REQ-6.1` Overall visual style
- `REQ-6.3` Home page
- `REQ-6.8` Page designer
- Related `REQ-9`

Prototype/reference:

- `docs/user_requirement.md` sections 5.8, 6.1, 6.3, 6.8, and 9
- FRC-1 remaining gaps in `docs/evidence/final-requirement-gap-report.md`
- FRC-1D in `docs/framework/next-execution-ledger.md`

Frontend scope:

- Add or harden page-designer component behavior beyond the first toolbar/list/detail/form loop: component ordering/copy, component visibility states, and mobile preview or equivalent responsive proof.
- Add keyboard/focus/accessibility checks for page designer and runtime page-designed form/list surfaces.
- Keep admin home config, module page designer, and runtime surfaces separated and visibly task-oriented.

Backend scope:

- Reuse existing page/home/schema APIs where possible.
- Add only missing readback/state metadata required for component breadth or accessibility evidence.

Generator scope:

- Generated page/config persistence remains plumbing only.
- Component composition, role pruning, validation state, keyboard/focus behavior, and browser accessibility evidence are coded product behavior.

Data scope:

- Disposable system, module, fields, page design snapshot, component configuration, runtime records, normal member, browser screenshots, and cleanup.

Permission rule:

- System administrators can configure and publish page components.
- Normal members only see components and fields allowed by their role; hidden/readonly states must remain enforced by API and browser evidence.

States:

- Page designer component readback, copy/reorder or equivalent component manipulation, mobile preview/responsive proof, keyboard focus, validation/readonly/hidden states, permission negatives, desktop/mobile containment, cleanup.

Explicitly not complete if:

- Component behavior is only represented by static text or a saved comma-separated list.
- Browser evidence does not prove keyboard/focus/accessibility or mobile responsive behavior.
- Hidden/readonly/validation state regresses while adding component breadth.
- The task claims final completion while requirement rows or user signoff remain open.

Acceptance script:

1. Run a new deployed FRC-1D script against `http://127.0.0.1:18131`.
2. Create a disposable system/module/page design with multiple component types and published schema.
3. Verify admin component manipulation/readback and normal-member runtime pruning/readback.
4. Browser-check page designer and runtime page surface for focus, keyboard reachability, responsive/mobile containment, and no stale/generic wording.
5. Verify permission negatives and cleanup created systems.
6. Keep `gates.user_script_passed=false`.

Accepted evidence:

- `scripts/recovery-r42-page-designer-component-accessibility-smoke.ps1` PASS against `http://127.0.0.1:18131`.
- `docs/evidence/recovery/r42-page-designer-component-accessibility-result.json`
- `docs/evidence/recovery/r42-page-designer-component-accessibility-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r42-page-designer-component-accessibility/page-designer-browser-audit.json`
- Release verification passed on deployed assets `/assets/index-B6v_geL0.js` and `/assets/index-B5pUhLMV.css`.

Proven:

- System admin configured/published module page `main`; admin and normal schema versions matched `PAGE_v1782829055809`.
- Page designer component workbench rendered 5 component rows, 22 action buttons, 20 focusable controls, and a mobile preview.
- Browser interaction copied `toolbar_copy`, moved component order, hid a component, saved the layout, and API readback persisted the copied/hidden component state.
- Runtime schema validation still caught required `title`, `status`, and `publicName`, then cleared after successful save.
- Normal-member schema marked 5 fields readonly, hid `secretNote`, removed the hidden component, browser create was disabled on desktop/mobile, and direct create returned HTTP `403`.
- Browser result count `8`, overflow count `0`.
- Cleanup deleted systems `767` and `768`.

Still partial:

- R42 is not final product completion.
- Role-specific home/dashboard usefulness, platform-vs-system first impression, broader whole-path usability, requirement coverage closure, and user signoff remain open.

## REC-P0-043 Role-Specific Home And Whole-Path Usability Closure

Status: `accepted`; engineering evidence only, not final product completion.

User role: platform administrator, platform member, system administrator, normal system member

Business outcome: The first useful screens for each role make sense as a product: users can log in, land in the right platform/system context, understand the next action, switch into a system, configure or use a module, and move through home/dashboard/runtime surfaces without mixed piles, duplicate entrances, stale wording, or confusing disabled states.

Coverage rows:

- `REQ-5.8` Pages
- `REQ-6.1` Overall visual style
- `REQ-6.2` Web main layout
- `REQ-6.3` Home page
- `REQ-6.8` Page designer
- Related `REQ-9`

Prototype/reference:

- `docs/user_requirement.md` sections 5.8, 6.1, 6.2, 6.3, 6.8, and 9
- FRC-1 remaining gaps in `docs/evidence/final-requirement-gap-report.md`
- FRC-1E in `docs/framework/next-execution-ledger.md`

Frontend scope:

- Platform workbench first screen, platform admin entry, system dashboard, system admin module page, normal-member runtime home/module path, and related message/todo/work entry fit.
- Remove or disable any mixed placeholder, repeated entry, generic success wording, or visually stacked surface found in those role paths.
- Keep desktop and mobile screenshots auditable with stable markers.

Backend scope:

- Reuse existing auth/system-switch/home/module/runtime APIs.
- Add only missing readback/state metadata needed to prove the role path; no decorative APIs.

Generator scope:

- Generated persistence remains plumbing only.
- Role landing, context switching, dashboard usefulness, visible disabled reasons, and whole-path browser evidence are coded product behavior.

Data scope:

- Disposable systems, module/page/home configuration, runtime record, normal member, platform/system role context, browser screenshots, and cleanup.

Permission rule:

- Platform-only roles do not leak system business data.
- Normal system members do not see admin surfaces.
- Disabled entries must match backend permission/action state.

States:

- Login, platform landing, system switch, system dashboard, system admin configuration entry, normal runtime entry, no-permission route, empty/loading state, desktop/mobile containment, cleanup.

Explicitly not complete if:

- A role lands on a page that is technically functional but still looks like unrelated panels stacked together.
- Platform and system contexts are visually or behaviorally mixed.
- Normal-member route evidence does not start from a realistic login/switch path.
- Browser evidence checks only route existence without proving visible task fit and permission state.
- The task claims final completion while requirement rows or user signoff remain open.

Acceptance script:

1. Run a new deployed FRC-1E script against `http://127.0.0.1:18131`.
2. Start from real login/session context for admin and normal member.
3. Browser-check platform workbench, platform admin denied/allowed state, system dashboard, system admin module page, and normal runtime module path on desktop/mobile.
4. Verify system switch/context, no mixed admin/runtime surfaces, clear disabled reasons, no stale/generic wording, and no horizontal overflow.
5. Verify API permission negatives and cleanup created systems.
6. Keep `gates.user_script_passed=false`.

Accepted evidence:

- `scripts/recovery-r43-role-home-whole-path-usability-smoke.ps1` PASS against `http://127.0.0.1:18131`.
- `docs/evidence/recovery/r43-role-home-whole-path-usability-result.json`
- `docs/evidence/recovery/r43-role-home-whole-path-usability-2026-06-30.md`
- `docs/evidence/recovery/screenshots/r43-role-home-whole-path-usability/role-home-whole-path-usability-audit.json`
- Release verification passed on deployed assets `/assets/index-CNWWdmkH.js` and `/assets/index-B5pUhLMV.css`.

Proven:

- Browser audit covered 16 admin/normal routes across desktop/mobile, with `resultCount=32`, `failureCount=0`, and `warningCount=0`.
- Platform workbench, platform admin allowed/denied, system dashboard, runtime module page, work, todos, messages, and system admin allowed/denied paths all rendered expected role markers.
- Normal platform workbench no longer exposes platform-admin wording or the create-system entry; normal system routes do not expose admin surfaces.
- System dashboard, work, todos, messages, runtime modules, and admin pages did not mix platform/admin/runtime shell markers.
- Normal-member direct record create and admin module action read both returned HTTP `403`.
- Cleanup deleted systems `771` and `772`.

Still partial:

- R43 is not final product completion.
- Full no-code configuration depth, broader runtime/workflow/integration/operations requirements, requirement coverage closure, and user signoff remain open.

## REC-P0-044 No-Code Configuration Depth And Permission Preview Closure

Status: `accepted`; engineering evidence only, not final product completion.

User role: system administrator, normal system member

Business outcome: A system administrator can configure the core no-code building blocks deeply enough for a real business module: field types, dictionaries, module groups/menus, permissions, and effective permission preview. A normal member then sees only the resulting permitted module/data shape.

Coverage rows:

- `REQ-4.3` Application configuration center
- `REQ-5.3.1` Module groups as runtime navigation
- `REQ-5.5` Fields
- `REQ-5.6` Dictionaries
- `REQ-5.9` Menus
- `REQ-5.10` Permissions
- `REQ-6.7` Field designer
- `REQ-6.10` Permission page

Prototype/reference:

- FRC-2 No-Code Configuration Depth in `docs/evidence/final-requirement-gap-report.md`
- `docs/user_requirement.md` sections 4.3, 5.3.1, 5.5, 5.6, 5.9, 5.10, 6.7, and 6.10

Frontend scope:

- System admin module configuration, field designer, dictionary configuration, role permission page, effective permission preview, and runtime navigation readback.
- Remove generic matrix placeholders where a field/dictionary/permission preview needs a dedicated control.

Backend scope:

- Reuse existing module/field/dictionary/role/permission APIs where complete.
- Add readback or preview metadata only when needed to prove the configured state becomes runtime behavior.

Generator scope:

- Generated entities remain persistence plumbing.
- Field-type behavior, dictionary semantics, effective permission preview, runtime navigation, and UI permission-state rendering are coded product behavior.

Data scope:

- Disposable system, module groups, module, field types, dictionary items, roles, permission matrix, normal member, runtime readback, browser screenshots, and cleanup.

Permission rule:

- Normal members see only published module groups/modules and readable fields.
- Permission preview must explain effective menu/module/action/field/data scope before relying on runtime behavior.

States:

- Admin configuration, save/readback, publish/check, effective preview, normal runtime readback, denied admin access, desktop/mobile containment, cleanup.

Explicitly not complete if:

- The task proves only field CRUD without dictionary/permission/runtime effect.
- A permission page saves data but does not preview the effective result.
- Runtime navigation still shows modules/groups/fields that the role cannot use.
- Browser evidence does not start from real login/switch.

Acceptance script:

1. Run a deployed FRC-2B script against `http://127.0.0.1:18131`.
2. Configure module groups, multiple field types, dictionary-bound fields, and role permission rules.
3. Read back admin configuration and effective permission preview.
4. Log in as normal member, switch into the system, and verify runtime navigation/fields/actions match the preview.
5. Verify forbidden admin/runtime mutation negatives and cleanup.
6. Keep `gates.user_script_passed=false`.

Evidence:

- `scripts/recovery-r44-no-code-permission-preview-smoke.ps1`
- `docs/evidence/recovery/r44-no-code-permission-preview-result.json`
- `docs/evidence/recovery/r44-no-code-permission-preview-2026-06-30.md`
- Browser audit: `docs/evidence/recovery/screenshots/r44-no-code-permission-preview/no-code-permission-preview-browser-audit.json`

Result:

- PASS on deployed `http://127.0.0.1:18131` after packaging and release verification.
- Configured module groups, a visible module and hidden module, a status dictionary with 2 items, 3 fields including a dictionary-bound `SELECT` field, role permissions, and effective permission preview.
- Preview denies `record.create`, returns missing permission `record.create`, and explains hidden `secretNote=HIDDEN`.
- Normal member runtime list schema hides `secretNote`, keeps readable dictionary field, disables create action, rejects direct create with HTTP `403`, rejects admin module action read with HTTP `403`, and browser desktop/mobile audit has 4 results with overflow `0` and blockers `0`.
- R44 found and fixed a real integration bug: system switch context exposed role codes while module group `visibleRoleIds` used role IDs, causing normal runtime navigation to incorrectly show no modules.
- Cleanup deleted systems `783` and `784`.

Still partial:

- R44 does not prove every field type, dictionary versioning/reference impact, menu ordering/editing, all field-specific configuration, or full permission matrix usability.
- `gates.user_script_passed` remains `false`.

## REC-P0-045 Field Type Breadth, Dictionary Impact, And Menu Configuration Closure

Status: `accepted`; engineering evidence only, not final product completion.

User role: system administrator, normal system member

Business outcome: A system administrator can configure a real no-code module with a broader field type set, persisted field-specific metadata, dictionary version/reference impact, disabled dictionary options, and runtime menu group visibility/order. A normal member then sees the published navigation and runtime schema created by that configuration.

Coverage rows:

- `REQ-4.3` Application configuration center
- `REQ-5.3.1` Module groups as runtime navigation
- `REQ-5.5` Fields
- `REQ-5.6` Dictionaries
- `REQ-5.9` Menus
- `REQ-6.7` Field designer

Prototype/reference:

- FRC-2C in `docs/framework/next-execution-ledger.md`
- FRC-2 No-Code Configuration Depth in `docs/evidence/final-requirement-gap-report.md`
- `docs/user_requirement.md` sections 4.3, 5.3.1, 5.5, 5.6, 5.9, and 6.7

Requirement confirmation contract:

| Contract item | Required content |
|---|---|
| Requirement source | `docs/user_requirement.md` REQ-4.3, REQ-5.3.1, REQ-5.5, REQ-5.6, REQ-5.9, REQ-6.7 plus FRC-2C row in `docs/framework/next-execution-ledger.md` |
| Target role | System administrator configures; normal system member consumes the published runtime result |
| Entry point | Admin enters `/systems/{systemId}/admin/module-config`, then normal member logs in and switches into the same system |
| User job | Admin builds a configurable business module; normal member sees the resulting permitted menu/list/schema without admin clutter |
| Data contract | Module groups, module definition, field definitions, dictionary type/items, publish versions, role/member context, runtime list schema |
| Permission contract | Admin can configure; normal member cannot access admin APIs; normal member sees only visible groups/modules/fields/actions |
| State contract | Save/readback, publish-check, published/runtime state, disabled dictionary item, no-permission, validation failure, desktop/mobile containment, cleanup |
| Copy contract | Admin labels/tips must describe configuration impact, dictionary impact, disabled item meaning, and runtime menu visibility without generic success copy |
| Acceptance assertions | API creates/reads configuration; deployed browser shows admin controls; normal runtime navigation/schema matches configuration; forbidden admin access returns HTTP 403; cleanup succeeds |
| Screenshot evidence boundary | Screenshots prove layout, visible markers, overflow, and copy visibility only. They do not prove field persistence, dictionary impact, permission correctness, or requirement completion without API/readback assertions |

Frontend scope:

- System admin module lifecycle/menu panel, field designer, dictionary management, and normal runtime navigation/list schema readback.
- The field designer must expose more field types and show persisted default value, validation/config metadata, dictionary binding, and permission mode.
- Dictionary management must show reference impact and disabled option behavior, not just item count.
- Menu configuration must prove group order/visibility and module enable/publish state in admin and runtime.

Backend scope:

- Persist and read back field default values and validation/config metadata through real module field tables.
- Return dictionary reference impact, disabled item count, and published version from dictionary APIs.
- Publish dictionary types through a real endpoint and version row.
- Keep module-group sorting and role visibility backed by existing module group persistence.

Generator scope:

- Generated entities remain persistence plumbing.
- Field-type semantics, dictionary impact/version metadata, menu visibility/readback, and runtime schema effects are coded product behavior.

Data scope:

- Disposable system, module groups, module, many field types, dictionary type/items, disabled item, dictionary publish version, runtime list schema, normal member, browser screenshots, and cleanup.

Permission rule:

- Normal members must not see groups outside their visible role IDs.
- Normal runtime schema must not expose disabled/admin-only configuration controls.
- Admin configuration endpoints must reject normal-member access.

States:

- Admin save/readback, dictionary publish/readback, disabled item, runtime navigation, runtime list schema, permission negatives, desktop/mobile containment, cleanup.

Explicitly not complete if:

- Field breadth is only front-end buttons without backend readback.
- Dictionary items can be added but no version/reference impact or disabled behavior is visible.
- Menu group visibility/order is saved in admin but not reflected in runtime navigation.
- Evidence checks only API creation and does not verify deployed browser surfaces.
- The task claims final completion while requirement rows or user signoff remain open.

Acceptance script:

1. Run a deployed FRC-2C script against `http://127.0.0.1:18131`.
2. Configure module groups with order and visible role IDs.
3. Configure and read back long text, number, date/datetime, select, multi-select, user/department, attachment/image, relation, and child-table boundary field metadata.
4. Configure dictionary items including a disabled item, publish the dictionary, and verify reference impact.
5. Publish the module, log in as a normal member, and verify runtime navigation/list schema matches the configuration.
6. Verify forbidden admin access, deployed desktop/mobile containment, evidence files, and cleanup.
7. Keep `gates.user_script_passed=false`.

Evidence paths:

- Script: `scripts/recovery-r45-field-dict-menu-smoke.ps1`
- Result: `docs/evidence/recovery/r45-field-dict-menu-result.json`
- Summary: `docs/evidence/recovery/r45-field-dict-menu-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r45-field-dict-menu/field-dict-menu-browser-audit.json`

Result:

- PASS on deployed `http://127.0.0.1:18131`.
- Release verification passed on deployed asset `/assets/index-CejDtUdM.js`.
- Configured 12 field types: `TEXT`, `LONG_TEXT`, `NUMBER`, `DATE`, `DATETIME`, `SELECT`, `MULTI_SELECT`, `USER`, `DEPARTMENT`, `ATTACHMENT`, `IMAGE`, `RELATION`, and `CHILD_TABLE`.
- Read back default value `12.50`, validation min `0`, and number precision `2`.
- Published dictionary `375` as `DICT_TYPE_v1782871517684`, with field reference count `2`, disabled item count `1`, and active runtime options `ACTIVE` and `PENDING` excluding `DISABLED_OLD`.
- Published visible/hidden module groups, verified normal runtime schema column count `15`, normal-member admin access HTTP `403`, browser result count `6`, overflow `0`, blockers `0`, and cleanup `794/795`.

Still partial:

- R45 is not final product completion.
- FRC-1 product-surface rows, richer page definitions, component catalog, live home/runtime usability, broader workflow/integration/operations depth, requirement coverage, and user signoff remain open.

## REC-P0-046 Page Definition, Component Catalog, And Home Runtime Surface Closure

Status: `accepted`; engineering evidence only, not final product completion.

User role: system administrator, normal system member

Business outcome: A system administrator can configure page definitions, component layout, and home/runtime surfaces so the product feels like one usable system instead of stacked prototype panels. A normal member sees the configured runtime/home pages with clean role-specific information, not admin clutter or generic placeholders.

Coverage rows:

- `REQ-5.8` Pages
- `REQ-6.1` Overall visual style
- `REQ-6.3` Home page
- `REQ-6.8` Page designer
- `REQ-9` Advanced/new capabilities

Prototype/reference:

- FRC-1F in `docs/framework/next-execution-ledger.md`
- FRC-1 Missing Product Surfaces in `docs/evidence/final-requirement-gap-report.md`
- `docs/user_requirement.md` sections 5.8, 6.1, 6.3, 6.8, and 9

Requirement confirmation contract:

| Contract item | Required content |
|---|---|
| Requirement source | `docs/user_requirement.md` REQ-5.8, REQ-6.1, REQ-6.3, REQ-6.8, REQ-9 plus FRC-1F row in `docs/framework/next-execution-ledger.md` |
| Target role | System administrator configures page/home/component surfaces; normal system member consumes the published runtime result |
| Entry point | Admin enters `/systems/{systemId}/admin/module-config` and dashboard/home configuration, then normal member logs in and switches into the same system |
| User job | Admin designs a readable page/home/runtime surface; normal member can use the configured page without seeing stacked admin panels or fake placeholders |
| Data contract | Page schema, component definitions, layout order/visibility, home configuration, module/runtime schema, role/member context, publish versions |
| Permission contract | Admin can configure; normal member cannot mutate page definitions or admin home config; normal member sees only permitted runtime/home content |
| State contract | Save/readback, publish/readback, empty component state, validation failure, disabled/no-permission state, desktop/mobile containment, cleanup |
| Copy contract | Visible labels, tips, empty states, disabled reasons, and success/failure messages must describe the user job and must not use generic completion wording |
| Acceptance assertions | API creates/reads page/home/component configuration; deployed browser shows admin controls and normal runtime/home surfaces; no mixed admin/runtime markers; forbidden admin access returns HTTP 403; cleanup succeeds |
| Screenshot evidence boundary | Screenshots prove hierarchy, density, clipping, overflow, copy visibility, and role-specific surface only. They do not prove persistence, permission correctness, workflow closure, or requirement completion without API/readback assertions |

Frontend scope:

- System admin page designer/component workbench, home/dashboard configuration, and normal runtime/home rendering.
- Add or tighten component catalog controls only where they close a real role journey: layout, visibility, ordering, copy, empty state, and runtime readback.
- Remove or disable generic placeholders, fake success toasts, and stacked unrelated panels found in the FRC-1F path.

Backend scope:

- Reuse existing page schema and home configuration APIs where complete.
- Add readback or publish metadata only when needed to prove configured page/home state becomes runtime behavior.
- Keep permission negatives enforced from backend, not only frontend hiding.

Generator scope:

- Generated entities remain persistence plumbing.
- Page/home/component usability, runtime rendering, permission-state display, and copy/state behavior are coded product behavior.

Data scope:

- Disposable system, module, page schema, component definitions, home configuration, runtime list/detail/schema, normal member, browser screenshots, and cleanup.

Permission rule:

- Normal members must not access page/home admin APIs.
- Normal runtime/home surfaces must not expose admin-only markers, configuration controls, hidden components, or fake placeholder actions.

States:

- Admin save/readback, publish/readback, normal runtime/home readback, empty/validation/no-permission states, desktop/mobile containment, cleanup.

Explicitly not complete if:

- The task only adds more component buttons without runtime readback.
- Browser evidence checks route existence but not page hierarchy, copy, mixed surfaces, and permission state.
- Page/home configuration saves but normal-member runtime still shows generic placeholders, admin clutter, or stacked unrelated panels.
- Screenshots are used to claim persistence or permission correctness without API/readback assertions.
- The task claims final completion while requirement rows or user signoff remain open.

Acceptance script:

1. Run a deployed FRC-1F script against `http://127.0.0.1:18131`.
2. Configure page components, ordering/visibility, and home/dashboard content from admin surfaces.
3. Read back the configuration and publish or expose it to runtime.
4. Log in as a normal member and verify runtime/home surfaces match the configured role-visible page definition without admin clutter.
5. Verify forbidden admin access, no fake/generic placeholder states, deployed desktop/mobile containment, evidence files, and cleanup.
6. Keep `gates.user_script_passed=false`.

Evidence paths:

- Script: `scripts/recovery-r46-page-surface-smoke.ps1`
- Result: `docs/evidence/recovery/r46-page-surface-result.json`
- Summary: `docs/evidence/recovery/r46-page-surface-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r46-page-surface/page-surface-browser-audit.json`

Result:

- PASS on deployed `http://127.0.0.1:18131`.
- Configured home title/widgets, proved admin save/readback, runtime readback, normal-member readback, and home publish-check.
- Configured a module page definition with five components: `toolbar`, `list`, `detail`, `chart`, and a hidden-field-bound `secret` component.
- Published page version `PAGE_v1782872581003`; normal runtime page read the same published version.
- Admin schema had 5 components and 7 fields; normal runtime components were `toolbar`, `list`, `detail`, and `chart`.
- Hidden field leaked=false and hidden component leaked=false in normal runtime schema.
- Normal-member home-config write and page-definition write both returned HTTP `403`.
- Browser audit covered 10 admin/normal desktop/mobile routes with overflow `0`, blockers `0`, no admin/runtime marker mixing in the audited surfaces, and cleanup `798/799`.

Still partial:

- R46 is not final product completion.
- Runtime daily-use depth, file/import/export states, workflow/integration/operations breadth, full requirement coverage, user acceptance, and `gates.user_script_passed` remain open.

## REC-P0-047 Runtime Daily-Use Record, File, Import/Export, And State Closure

Status: `accepted`; engineering evidence only, not final product completion.

User role: normal system member, system administrator

Business outcome: A normal system member can use a published runtime module as a daily work surface: list, filter/sort/page, create/edit/detail, upload/download file evidence, see import/export entries with real state, and get clear empty/error/no-permission behavior. A system administrator can configure enough of the module surface to support that runtime journey without exposing admin clutter.

Coverage rows:

- `REQ-4.4` Application runtime
- `REQ-5.11` Data records
- `REQ-5.13` Files
- `REQ-5.14` Import and export
- `REQ-6.4` List pages
- `REQ-6.5` Form pages
- `REQ-6.6` Detail pages
- `REQ-6.11` Mobile

Prototype/reference:

- FRC-3B in `docs/framework/next-execution-ledger.md`
- FRC-3 Runtime User Depth in `docs/evidence/final-requirement-gap-report.md`
- `docs/user_requirement.md` sections 4.4, 5.11, 5.13, 5.14, 6.4, 6.5, 6.6, and 6.11

Requirement confirmation contract:

| Contract item | Required content |
|---|---|
| Requirement source | `docs/user_requirement.md` REQ-4.4, REQ-5.11, REQ-5.13, REQ-5.14, REQ-6.4, REQ-6.5, REQ-6.6, REQ-6.11 plus FRC-3B row in `docs/framework/next-execution-ledger.md` |
| Target role | Normal system member uses runtime records; system administrator configures supporting module/list/file/import-export behavior |
| Entry point | Normal member logs in, switches into the system, opens `/systems/{systemId}/modules`, and works inside the authorized module |
| User job | Create/read/edit records, inspect detail, use files and import/export state, and understand empty/error/no-permission outcomes without admin clutter |
| Data contract | Module, fields, list scene, records, values, attachments, import/export task/result state, history/readback, role/member context |
| Permission contract | Admin configures; normal member can use only granted record/file/import-export actions; denied actions are hidden/disabled and backend rejects direct calls |
| State contract | Empty list, populated list, filter/sort/page, validation error, save success/readback, detail tabs, file upload/download metadata, import/export task state, forbidden state, desktop/mobile containment, cleanup |
| Copy contract | Runtime labels, empty states, validation errors, disabled reasons, task results, and import/export messages must describe the action result, not generic success |
| Acceptance assertions | API and deployed browser prove record/file/import-export state transitions; results survive reload/re-login; forbidden actions return HTTP 403; no placeholder/fake success text; cleanup succeeds |
| Screenshot evidence boundary | Screenshots prove layout, overflow, visible state, copy, and role-specific surface only. They do not prove persistence, file integrity, import/export result, or permission correctness without API/readback assertions |

Frontend scope:

- Runtime list/detail/form surfaces, file controls, import/export entry/result state, empty/error/no-permission UI, desktop/mobile containment.
- Add or tighten browser markers only where needed for deterministic role-journey evidence.
- Remove or disable fake placeholders and generic toasts on the runtime path.

Backend scope:

- Reuse existing runtime record, upload, import/export, schema, and permission APIs where complete.
- Add readback metadata or permission-state details only when needed to prove runtime behavior.
- Keep direct forbidden calls rejected from backend, not only hidden in frontend.

Generator scope:

- Generated dynamic record/value/upload/import-export tables remain persistence plumbing.
- Runtime task behavior, file binding/readback, import/export state transitions, permission-state rendering, and user-facing state copy are coded product behavior.

Data scope:

- Disposable system, module, fields, list scene, records, attachments, import/export task metadata, normal member, browser screenshots, and cleanup.

Permission rule:

- Normal members must not mutate restricted fields/actions or use admin configuration APIs.
- File/import/export actions must respect current member permissions.
- Direct API attempts for denied runtime/admin actions must return HTTP `403`.

States:

- Empty, loading, validation failure, saved/readback, detail readback, upload/download metadata, import/export task accepted/result, no-permission, desktop/mobile containment, cleanup.

Explicitly not complete if:

- The task proves only API record CRUD without deployed browser runtime state.
- Files use fake IDs or are not read back from the record detail.
- Import/export is only a button or toast without a task/result state.
- Empty/error/no-permission states are invisible or generic.
- Screenshots are used to claim persistence or permission correctness without API/readback assertions.
- The task claims final completion while requirement rows or user signoff remain open.

Acceptance script:

1. Run a deployed FRC-3B script against `http://127.0.0.1:18131`.
2. Configure and publish a runtime module with list scene, writable fields, readonly/hidden field, and file-capable behavior.
3. Log in as normal member, create/edit/read a record, upload and bind a real file, and verify detail/readback after reload or re-login.
4. Trigger or verify import/export task/result state from the runtime surface with real API readback.
5. Verify empty/error/no-permission states, denied direct APIs, desktop/mobile containment, evidence files, and cleanup.
6. Keep `gates.user_script_passed=false`.

Evidence paths:

- Script: `scripts/recovery-r47-runtime-daily-use-smoke.ps1`
- Result: `docs/evidence/recovery/r47-runtime-daily-use-result.json`
- Summary: `docs/evidence/recovery/r47-runtime-daily-use-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r47-runtime-daily-use/runtime-daily-use-browser-audit.json`

Result:

- PASS on deployed `http://127.0.0.1:18131`.
- Configured and published runtime module `297` in system `803`, with normal runtime role `1149` and readonly role `1150`.
- Proved empty search total `0`, record create/readback record id `387`, edit/detail readback, attachment file `file_82287bbc_1782873359219`, detail attachment count `4`, and detail history count `2`.
- Import precheck returned `2` valid rows; import confirm task `task_import_confirm_3751cbac` completed with status `SUCCESS` and inserted count `2`.
- Export task `task_runtime_record_export_1b774a17` completed with status `SUCCESS` and result file `file_export_result_1b774a17`.
- Runtime search total became `3`, hidden field leaked=false, readonly direct create returned HTTP `403`, normal-member admin access returned HTTP `403`.
- Browser audit covered 10 normal/admin desktop/mobile runtime routes with overflow `0`, blockers `0`, and cleanup `803/804/805`.

Still partial:

- R47 is not final product completion.
- Workflow/approval/todo/message/flow surface closure, integration/operations breadth, broader mobile/accessibility depth, full requirement coverage, user acceptance, and `gates.user_script_passed` remain open.

## REC-P0-048 Workflow, Approval, Todo/Message, And Flow Surface Closure

Status: `accepted`; engineering evidence only, not final product completion.

User role: requester, approver, system administrator, normal system member

Business outcome: A requester can submit a real runtime record into an approval flow, an approver can receive and process the task through todo/message surfaces, and a system administrator can configure or simulate enough of the flow surface to prove workflow is not just backend plumbing.

Coverage rows:

- `REQ-4.5` Workflow engine
- `REQ-5.12` Workflows
- `REQ-5.16` Notifications/messages
- `REQ-5.19` Todos/workbench entry
- `REQ-6.9` Flow designer
- `REQ-9` Advanced/new capabilities

Prototype/reference:

- FRC-4B in `docs/framework/next-execution-ledger.md`
- FRC-4 Workflow/Automation Depth in `docs/evidence/final-requirement-gap-report.md`
- `docs/user_requirement.md` sections 4.5, 5.12, 5.16, 5.19, 6.9, and 9

Requirement confirmation contract:

| Contract item | Required content |
|---|---|
| Requirement source | `docs/user_requirement.md` REQ-4.5, REQ-5.12, REQ-5.16, REQ-5.19, REQ-6.9, REQ-9 plus FRC-4B row in `docs/framework/next-execution-ledger.md` |
| Target role | System administrator configures/simulates workflow; requester submits; approver handles todo/message; normal member only sees permitted workflow state |
| Entry point | Admin enters system workflow/flow designer; requester opens a runtime record; approver opens todo/message center and target detail |
| User job | Configure or verify a flow, submit a record, receive todo/message, process approval, and see terminal state/history without stale or duplicate surfaces |
| Data contract | Flow definition/version, runtime record, flow instance/task/history, todo, message, audit/log ids, role/member context, permission state |
| Permission contract | Requester cannot approve own assigned approver task; only assigned/candidate approver can act; normal member cannot mutate flow/admin surfaces; backend rejects direct forbidden calls |
| State contract | Draft/published flow, simulation or publish-check, pending approval, pending todo/message, approved/rejected terminal state, duplicate action/idempotency, no-permission, desktop/mobile containment, cleanup |
| Copy contract | Flow labels, approval status, todo/message wording, disabled reasons, action results, duplicate/idempotent results, and error messages must explain the user state, not generic success |
| Acceptance assertions | API and deployed browser prove flow configuration/readback, approval submit, todo/message creation/readback, approver action, terminal detail/history, forbidden actions, and cleanup |
| Screenshot evidence boundary | Screenshots prove layout, overflow, visible workflow/todo/message state, and copy only. They do not prove workflow correctness, permission enforcement, idempotency, or persistence without API/readback assertions |

Frontend scope:

- Flow designer/simulation or publish surface, runtime approval entry/sidebar, todo center, message center, terminal status/detail/history, disabled/no-permission states, desktop/mobile containment.
- Remove duplicate or stale workflow/todo/message action surfaces found on the deployed path.
- Add deterministic markers only where needed for deployed browser evidence.

Backend scope:

- Reuse existing flow definition/runtime, approval, todo, message, and audit APIs where complete.
- Add readback or state metadata only when needed to prove workflow behavior and permission boundaries.
- Keep approval/todo/message permission negatives enforced in backend.

Generator scope:

- Generated flow/todo/message tables remain persistence plumbing.
- Approval routing, state transitions, visible todo/message behavior, idempotency, and user-facing workflow state are coded product behavior.

Data scope:

- Disposable system, module, record, flow definition/version, flow instance/task/history, todo/message records, requester/approver/normal member, browser screenshots, and cleanup.

Permission rule:

- Requester cannot process approver-only tasks unless explicitly assigned by the flow.
- Approver can process only assigned/candidate tasks.
- Normal members must not mutate workflow/admin configuration or unrelated todo/message state.
- Direct forbidden APIs must return HTTP `403`.

States:

- Draft, publish-check/simulation, submitted, pending approval, pending todo/message, approved/rejected, duplicate action, no-permission, desktop/mobile containment, cleanup.

Explicitly not complete if:

- Approval passes only through API without deployed todo/message browser evidence.
- Todo/message appears but cannot navigate to or reflect the correct business target.
- Flow designer/simulation surface is static or disconnected from published/runtime behavior.
- Duplicate approval or forbidden actions are not asserted.
- Screenshots are used to claim workflow correctness without API/readback assertions.
- The task claims final completion while requirement rows or user signoff remain open.

Acceptance script:

1. Run a deployed FRC-4B script against `http://127.0.0.1:18131`.
2. Configure or publish a simple approval flow and verify simulation/publish metadata.
3. Submit a runtime record as requester and verify flow instance/task/history plus todo/message creation.
4. Log in as approver, open todo/message surfaces, process approval, and verify terminal record/sidebar/history state.
5. Verify requester/normal-member forbidden actions, duplicate action idempotency, desktop/mobile containment, evidence files, and cleanup.
6. Keep `gates.user_script_passed=false`.

Evidence paths:

- Script: `scripts/recovery-r48-workflow-message-flow-smoke.ps1`
- Result: `docs/evidence/recovery/r48-workflow-message-flow-result.json`
- Summary: `docs/evidence/recovery/r48-workflow-message-flow-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r48-workflow-message-flow/workflow-message-flow-browser-audit.json`

Result:

- PASS on deployed `http://127.0.0.1:18131`.
- Published flow `128` at version `flow_v1782874494680`; publish-check passed with impact refs `2`.
- Simulation passed with `runtimeInstanceCreated=false` and step count `2`.
- Requester pending todo total was `0`; approver pending todo total was `1`; approver message total was `1`.
- Requester direct approve returned HTTP `403`; normal-member flow/admin read returned HTTP `403`.
- Approver todo action returned `HANDLED`; duplicate action with the same idempotency key returned `HANDLED` with the same trace id.
- Record detail and approval sidebar both reached `APPROVED`; pending todos moved to `0`, handled todos became `1`.
- Browser audit covered 8 before-approval and 6 after-approval desktop/mobile results for flow management, todo, message, and runtime surfaces with overflow `0`, blockers `0`, and cleanup `812/813/814`.

Still partial:

- R48 is not final product completion.
- Reject/transfer/timer/escalation variants, OpenAPI/assistant/integration breadth, operations breadth, full requirement coverage, user acceptance, and `gates.user_script_passed=false` remain open.

## REC-P0-049 OpenAPI, Assistant, And Integration Boundary Closure

Status: `accepted`; engineering evidence only, not final product completion.

User role: system administrator, external integrator, normal system member

Business outcome: A system administrator can configure safe external integration and assistant policies, an external integrator can call a scoped API and receive auditable results, and a normal member can use the assistant within permission boundaries without hidden admin or unsafe write behavior.

Coverage rows:

- `REQ-5.15` OpenAPI
- `REQ-5.19` Intelligent capabilities
- `REQ-9` Advanced/new capabilities
- `REQ-14.1-14.37` Launch capability rules

Prototype/reference:

- FRC-4C in `docs/framework/next-execution-ledger.md`
- FRC-4 Workflow/Automation Depth and FRC-5 Operations/Robustness/Delivery in `docs/evidence/final-requirement-gap-report.md`
- `docs/user_requirement.md` sections 5.15, 5.19, 9, and 14

Requirement confirmation contract:

| Contract item | Required content |
|---|---|
| Requirement source | `docs/user_requirement.md` REQ-5.15, REQ-5.19, REQ-9, REQ-14.1-14.37 plus FRC-4C row in `docs/framework/next-execution-ledger.md` |
| Target role | System administrator configures OpenAPI and assistant policy; external integrator calls scoped APIs; normal member uses scoped assistant behavior |
| Entry point | Admin enters system admin OpenAPI/Agent surfaces; integrator calls external API; normal member opens assistant from system shell |
| User job | Configure safe external access, prove scoped API read/write/logging, use assistant preview/confirm/deny behavior, and verify no unsafe cross-role or cross-system leakage |
| Data contract | OpenAPI app, secret/secret ref, scopes, call logs, runtime records, assistant session/message, write preview, confirmation result, audit/log ids, role/member context |
| Permission contract | Secrets are not leaked; OpenAPI scopes restrict calls; assistant uses current system/member permissions; normal member cannot mutate admin/integration config; backend rejects direct forbidden calls |
| State contract | App create/readback, secret rotation or masked display, successful scoped call, denied scoped call, call log/audit readback, assistant preview, confirm, reject, failure/no-permission state, desktop/mobile containment, cleanup |
| Copy contract | Integration labels, secret warnings, scope errors, assistant previews, confirmation results, denied reasons, and audit lines must describe actual state and avoid generic success wording |
| Acceptance assertions | API and deployed browser prove OpenAPI config/readback, scoped call behavior, call log/audit, assistant permission boundary, forbidden actions, and cleanup |
| Screenshot evidence boundary | Screenshots prove layout, overflow, visible integration/assistant state, copy, and role-specific surfaces only. They do not prove scope enforcement, secret safety, assistant permission, or audit correctness without API/readback assertions |

Frontend scope:

- System admin OpenAPI app surface, Agent/assistant policy surface, right-side assistant drawer, visible scope/secret/call-log states, desktop/mobile containment.
- Remove or disable any static integration/assistant placeholders found in the deployed path.
- Add deterministic markers only where needed for deployed browser evidence.

Backend scope:

- Reuse existing OpenAPI app/secret/call-log/runtime APIs and assistant session/message/write-confirm APIs where complete.
- Add readback or state metadata only when needed to prove scope enforcement, masking, auditability, and permission boundaries.
- Keep OpenAPI and assistant permission negatives enforced in backend.

Generator scope:

- Generated OpenAPI/assistant/log tables remain persistence plumbing.
- Scope enforcement, secret safety, call logging, assistant write preview/confirmation, permission-state display, and user-facing integration state are coded product behavior.

Data scope:

- Disposable system, module, record, OpenAPI app/secret, call log/audit rows, assistant session/message, normal member, browser screenshots, and cleanup.

Permission rule:

- OpenAPI calls must be scoped to configured permissions and current system/tenant context.
- Secrets must never be returned in full after creation/rotation.
- Assistant write behavior must require preview and explicit confirmation where it mutates state.
- Direct forbidden APIs must return HTTP `403` or the documented scoped-denial error.

States:

- Created, masked/rotated secret, scoped call success, scoped call denial, call-log readback, assistant preview, confirmed write, rejected write, no-permission, desktop/mobile containment, cleanup.

Explicitly not complete if:

- OpenAPI only creates an app but no scoped call/log readback is proven.
- Secret values are leaked after initial creation/rotation.
- Assistant only displays a chat shell without scoped session/readback or write confirmation evidence.
- Browser evidence checks route existence but not visible scope, secret, log, assistant, and permission states.
- Screenshots are used to claim integration/assistant correctness without API/readback assertions.
- The task claims final completion while requirement rows or user signoff remain open.

Acceptance script:

1. Run a deployed FRC-4C script against `http://127.0.0.1:18131`.
2. Configure an OpenAPI app and prove secret masking or rotation behavior.
3. Execute one allowed scoped API call and one denied scoped API call, then read back call logs/audit.
4. Open the assistant as a normal member, prove scoped session/readback and write preview/confirm/reject behavior.
5. Verify forbidden admin/integration mutation, desktop/mobile containment, evidence files, and cleanup.
6. Keep `gates.user_script_passed=false`.

Evidence paths:

- Script: `scripts/recovery-r49-openapi-assistant-integration-smoke.ps1`
- Result: `docs/evidence/recovery/r49-openapi-assistant-integration-result.json`
- Summary: `docs/evidence/recovery/r49-openapi-assistant-integration-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r49-openapi-assistant-integration/openapi-assistant-integration-browser-audit.json`

Result:

- PASS on deployed `http://127.0.0.1:18131`.
- Proved OpenAPI app `52`, record `394`, SecretRef `sec_openapi_r49_app_0701133505709_821aad_e662e71b`, rotation job `srj_openapi_52_bee0d801`, success call logs `5`, failed call logs `1`, read-only scope denial HTTP `403`, wrong SecretRef denial HTTP `401`.
- Proved platform Agent system-write denial `REJECTED_BY_SCOPE`, system Agent policy publish-check, assistant proposed `SYSTEM_AGENT_WRITE_CONFIRM` and `WORK_AGENT_DRAFT_CONFIRM`, write preview/confirm/reject terminal states, and work draft preview/confirm terminal states.
- Proved normal-member scoped assistant session, normal OpenAPI admin mutation HTTP `403`, normal Agent policy mutation HTTP `403`, agent audit logs `12`, confirmation audit logs `6`.
- Browser audit covered admin OpenAPI, admin Agent, and normal assistant drawer on desktop/mobile with result count `6`, overflow `0`, blockers `0`.
- Cleanup deleted systems `817` and `818`.

Still partial:

- R49 is not final product completion.
- Callback/webhook/API documentation UX, rate-limit behavior under load, broader assistant flows, operations/log/release breadth, requirement coverage, user acceptance, and `gates.user_script_passed=false` remain open.
- Next executable work is Framework V6 final usable-system remediation and deployed role-journey audit preparation.

## REC-P0-050 Operations, Logs, Release, And Launch-Rule Closure

Status: `accepted`; engineering evidence only, not final product completion.

User role: operator, platform administrator, system administrator, normal system member

Business outcome: An operator or administrator can verify the deployed system health, inspect logs/audits, understand release/runtime status, and execute or review risky operational settings with clear confirmation, permission, trace, and readback behavior.

Coverage rows:

- `REQ-4.6` System management and operations center
- `REQ-5.17` Logs and audit
- `REQ-5.18` System management settings
- `REQ-7` Technical architecture
- `REQ-8` Robustness
- `REQ-10` Full delivery standard
- `REQ-14.1-14.37` Launch capability rules
- `REQ-A` Confirmed product clarifications

Prototype/reference:

- FRC-5B in `docs/framework/next-execution-ledger.md`
- FRC-5 Operations, Robustness, Delivery in `docs/evidence/final-requirement-gap-report.md`
- `docs/user_requirement.md` sections 4.6, 5.17, 5.18, 7, 8, 10, 14, and Appendix A

Requirement confirmation contract:

| Contract item | Required content |
|---|---|
| Requirement source | `docs/user_requirement.md` REQ-4.6, REQ-5.17, REQ-5.18, REQ-7, REQ-8, REQ-10, REQ-14.1-14.37, REQ-A plus FRC-5B row in `docs/framework/next-execution-ledger.md` |
| Target role | Operator/platform administrator verifies release and operations; system administrator reviews system settings/logs; normal member must not access admin/ops mutation surfaces |
| Entry point | Release package health/status script, platform/system admin operations/log pages, system settings pages, and direct forbidden normal-member URLs/APIs |
| User job | Confirm the running deployment is healthy, inspect relevant logs/audits, verify operational settings/readback, perform or preview risky operations with confirmation/audit, and understand failure/no-permission states |
| Data contract | Health response, release metadata, backend/frontend asset identity, login/session result, platform/system log records, operation/audit trace ids, settings readback, async task/risk confirmation records where applicable |
| Permission contract | Operators/admins can read permitted logs/settings; normal members cannot mutate or view admin/ops surfaces; backend rejects direct forbidden calls; secrets and sensitive config are masked |
| State contract | Healthy/unhealthy, loading, empty, filtered log list, log detail, setting saved/readback, risky-operation confirmation, async task status, no-permission, release restart/status, desktop/mobile containment, cleanup |
| Copy contract | Health, log filters, trace/audit ids, disabled reasons, risk confirmation, success/failure, and release status copy must explain the actual state, not generic success/toast wording |
| Acceptance assertions | API/release/browser evidence proves health/release, log list/detail/readback, settings/risky operation state, permission positives/negatives, masking, desktop/mobile containment, and cleanup |
| Screenshot evidence boundary | Screenshots prove layout, overflow, visible log/settings/operation state, copy, and role-specific surfaces only. They do not prove release health, audit completeness, permission enforcement, or robustness without API/readback assertions and release assertions |

Frontend scope:

- Platform/system log management and operations/settings surfaces relevant to launch, health, release, audit, risky confirmation, and no-permission state.
- Remove or disable static operational placeholders and fake success toasts found in deployed paths.
- Add deterministic markers only where needed for deployed browser evidence.

Backend scope:

- Reuse existing health, release verification, log/audit, system settings, task, and risk-confirmation APIs where complete.
- Add readback/state metadata only when needed to prove operations are real and auditable.
- Keep admin/ops permission negatives enforced in backend.

Generator scope:

- Generated log/settings/task tables remain persistence plumbing.
- Release health, audit/query/detail behavior, risk confirmation, permission-state display, and user-facing operational state are coded product behavior.

Data scope:

- Deployed release package, health response, log/audit rows, operational settings, risky-operation confirmation/task records, admin/normal accounts, browser screenshots, and cleanup.

Permission rule:

- Normal members must not access platform/system operations mutation or log-management admin surfaces.
- Sensitive settings and credentials must be masked.
- Direct forbidden APIs must return HTTP `403` or documented permission-denial errors.

States:

- Healthy, unhealthy/failure capture, empty log list, filtered list, log detail, setting saved/readback, confirmation required, task queued/success/failure, no-permission, desktop/mobile containment, cleanup.

Explicitly not complete if:

- Release verification only checks a process exists or one HTTP 200 without health fields.
- Logs are only created but cannot be searched, filtered, or opened with trace/audit detail.
- Operations pages are static summaries, placeholders, or success toasts without persisted readback.
- Normal-member forbidden operations are not asserted from backend and browser.
- Screenshots are used to claim health, robustness, logs, or permission correctness without API/readback/release assertions.
- The task claims final completion while requirement rows or user signoff remain open.

Acceptance script:

1. Run a deployed FRC-5B script against `http://127.0.0.1:18131` and the current release package.
2. Verify release health/status, backend/frontend asset identity, Redis/database/schema UP, and admin login.
3. Create or locate log/audit-producing actions, then verify platform/system log list, filters, detail, trace/audit ids, and masking/readback.
4. Verify operational setting readback or risky-operation confirmation/task state, including failure/no-permission states where applicable.
5. Verify normal-member forbidden admin/ops APIs and deployed desktop/mobile containment.
6. Keep `gates.user_script_passed=false`.

Evidence paths:

- Script: `scripts/recovery-r50-operations-release-log-smoke.ps1`
- Result: `docs/evidence/recovery/r50-operations-release-log-result.json`
- Summary: `docs/evidence/recovery/r50-operations-release-log-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r50-operations-release-log/operations-release-log-browser-audit.json`

Result:

- `status=PASS`
- Release verification PASS at `http://127.0.0.1:18131`.
- Server script commands present: `start`, `stop`, `restart`, `status`, `health`.
- Platform/system operation requests wrote persisted audit rows searchable by traceId with detail readback.
- Normal-member platform/system ops writes returned HTTP `403` and wrote `FAILURE` audit rows.
- Browser audit result count `6`, overflow `0`, blockers `0`.
- Cleanup removed disposable systems `819` and `820`.

Still partial:

- This is slice evidence only. Final requirement coverage, broad deployed role-journey coherence, and `gates.user_script_passed=false` remain open.

Next executable:

- Framework V6 final usable-system remediation and deployed role-journey audit preparation.

## REC-P0-051 Final Role-Journey Gap Audit

Status: `accepted`; framework/diagnostic evidence only, not final product completion.

User role: all roles; this task audits readiness and does not implement user-facing behavior.

Business outcome: the team can see the current final-goal truth from requirement rows and role journeys before the next coding slice starts.

Requirement source: `docs/recovery/final-usable-system-acceptance.md`, `docs/framework/final-requirement-coverage-ledger.md`, `docs/evidence/final-requirement-gap-report.md`, and user feedback on 2026-07-01 that the deployed product remains confusing/unusable.

Prototype reference: `docs/design/prototype-brief.md` and `docs/design/prototypes/index.html`; screenshots are visual evidence only.

Frontend scope: no frontend implementation in this task.

Backend scope: no backend implementation in this task.

Generator scope: none.

Data scope: audit reads session state, requirement coverage ledger, final acceptance ledger, release verification, static usability result, and prior R45-R50 evidence.

Permission rule: keep `gates.user_script_passed=false`; do not convert engineering evidence into user signoff.

States: framework audit PASS, requirement coverage FAIL_EXPECTED, final acceptance reopened, static usability blockers, release verification, prior evidence readability, and recommended next task.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-A`, all current `PARTIAL` rows, and reopened final usable-system acceptance |
| Target role | all product roles as audit subjects |
| Entry point | repository state and deployed release `http://127.0.0.1:18131` |
| User job | produce a truthful next-work decision before more coding |
| Data contract | JSON/Markdown evidence files must be regenerated and readable |
| Permission contract | user signoff remains false; engineering evidence remains separate |
| State contract | audit PASS can coexist with `productStatus=FAIL_EXPECTED` |
| Copy contract | report must say "FAIL_EXPECTED" for product readiness while requirements remain partial |
| Acceptance assertions | framework audit PASS; coverage audit executes and reports notClosed rows; final acceptance reopened; release verify passes; next recommended batch exists |
| Screenshot evidence boundary | Screenshots prove visual rendering only; they do not prove requirement closure. API/readback assertions and coverage rows drive next work. |

Evidence paths:

- Script: `scripts/recovery-r51-final-role-journey-gap-audit.ps1`
- Result: `docs/evidence/recovery/r51-final-role-journey-gap-audit-result.json`
- Summary: `docs/evidence/recovery/r51-final-role-journey-gap-audit-2026-07-01.md`

Result:

- `status=PASS`
- `productStatus=FAIL_EXPECTED`
- Requirement rows not closed: `45`
- Final journey partial rows: `8`
- User signoff: `false`
- First recommended batch: `FRC-1 Missing Product Surfaces`
- Recommended next task: `REC-P0-052 fresh deployed role-journey closure from FRC-1 Missing Product Surfaces (REQ-5.8, REQ-6.1, REQ-6.3, REQ-6.8, REQ-9)`

Still partial:

- This task did not implement product behavior.
- Final product completion, requirement coverage closure, and user signoff remain open.

## REC-P0-052 FRC-1 Missing Product Surfaces Fresh Deployed Closure

Status: `accepted` as deployed R52 FRC-1 engineering evidence only; final product acceptance, full requirement coverage, and user signoff remain open.

User role: normal system member, system administrator, platform administrator, platform member

Business outcome: users can enter the deployed product and see coherent, task-oriented product surfaces for home, runtime pages, page designer, command/assistant entry, and high-level visual structure without stacked/mixed panels or misleading tips.

Requirement source: `REQ-5.8`, `REQ-6.1`, `REQ-6.3`, `REQ-6.8`, `REQ-9` from `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: deployed login, platform workspace, system dashboard/home, runtime list/detail/form surfaces, page designer/admin surface, command/assistant entry, desktop/mobile containment, text/tips/error state scan.

Backend scope: only code backend behavior needed to support real frontend readback for home/page/runtime/assistant surface state; do not add fake responses or static placeholders.

Generator scope: generated CRUD may be used only for plumbing. It cannot close home/page designer/runtime/assistant usability.

Data scope: home configuration, page definitions, runtime module schema/records, messages/todos/work indicators, assistant session metadata, and audit/trace evidence where actions are exercised.

Permission rule: platform/system admin-only entries must be hidden or denied for normal members; normal members must keep authorized runtime surfaces; backend denial and UI state must agree.

States: loading, empty, disabled, validation, backend failure, no-permission, async result, mobile overflow, stale data, and no fake/generic placeholder states.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-5.8`, `REQ-6.1`, `REQ-6.3`, `REQ-6.8`, `REQ-9` |
| Target role | normal system member, system administrator, platform administrator, platform member |
| Entry point | real login or existing authenticated deployed session, then platform/system switch and product routes |
| User job | reach a clean role-appropriate home/runtime/configuration/assistant surface and complete the first visible action without confusion |
| Data contract | home/page/runtime records and assistant state must come from backend APIs and read back after reload/re-login where applicable |
| Permission contract | allowed role can see/act; forbidden role receives backend 403 and UI hidden/disabled/no-permission state |
| State contract | loading, empty, disabled, validation, backend error, async result, mobile and desktop containment must be asserted |
| Copy contract | labels, tips, disabled reasons, empty/error messages must be specific, non-contradictory, and not generic success/placeholder wording |
| Acceptance assertions | deployed browser route audit plus API/readback assertions for configured home/page/runtime data, permission positives/negatives, and static usability blockers |
| Screenshot evidence boundary | Screenshots prove visual hierarchy, overflow, clipping, and visible copy only; they do not prove data, permission, persistence, or requirement closure. API/readback assertions are required. |

Evidence paths:

- Script: `scripts/recovery-r52-frc1-deployed-surface-closure.ps1`
- Result: `docs/evidence/recovery/r52-frc1-deployed-surface-closure-result.json`
- Summary: `docs/evidence/recovery/r52-frc1-deployed-surface-closure-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r52-frc1-deployed-surface/frc1-deployed-surface-browser-audit.json`

Result:

- `status=PASS`
- `productStatus=PARTIAL_ENGINEERING_EVIDENCE_ONLY`
- Release health/database/schema/Redis all `UP`.
- Static usability audit: blockers `0`, warnings `0`.
- Fresh R43 role/home whole-path browser audit: results `32`, failures `0`, warnings `0`.
- Fresh R46 page/home/runtime surface browser audit: browserResults `10`, overflow `0`, blockers `0`.
- Print preview request now uses `previewValues`; frontend no longer emits sample/demo preview payloads.
- Cleanup: `825:DELETE`.

Explicitly not complete if:

- The page looks cleaner but data is local/static or not read back from backend.
- Screenshots pass but requirement rows or role journeys remain unverified.
- A normal member sees admin-only copy/actions or can open admin routes.
- A button only shows generic success, placeholder, or stale local state.
- Desktop passes but mobile stacks unrelated panels or clips controls.
- The task does not update coverage ledger, gap report, next ledger, and session state after evidence.

## REC-P0-053 FRC-2 No-Code Configuration Coherence Fresh Closure

Status: `accepted`; engineering evidence only, not final product completion.

User role: system administrator, normal system member, platform administrator

Business outcome: a system administrator can configure no-code structure from coherent admin entries, publish it, and a normal member sees the matching runtime menu, fields, permissions, and page behavior without leaked admin controls or confusing configuration fragments.

Requirement source: `REQ-4.3`, `REQ-5.3`, `REQ-5.3.1`, `REQ-5.4`, `REQ-5.5`, `REQ-5.6`, `REQ-5.7`, `REQ-5.9`, `REQ-5.10`, `REQ-6.7`, `REQ-6.10` from `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: system admin module configuration, field designer, dictionary binding, menu/module group visibility, role permission workbench, permission preview, organization/member surface where required for role assignment, and normal-member runtime menu/schema state.

Backend scope: only code backend behavior needed for persisted configuration readback, publish/check results, role permission decisions, menu visibility, field/dictionary effects, and runtime schema agreement.

Generator scope: generated CRUD may provide table/entity plumbing only. The no-code behavior closes only when coded services enforce publish, visibility, field permission, dictionary impact, and runtime readback.

Data scope: modules, module groups, fields, dictionaries, menu visibility, roles, role permissions, members, effective permission preview, runtime list schema, and audit/trace evidence.

Permission rule: system admin can configure and publish; normal system member can read only permitted runtime surfaces; normal member receives backend `403` and hidden/disabled UI for admin-only writes; platform administrator cannot bypass system member context for runtime data.

States: loading, empty, disabled, validation, backend failure, no-permission, publish-check warning, dictionary disabled item, hidden/masked field, menu hidden state, and mobile/desktop containment.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.3`, `REQ-5.3`, `REQ-5.3.1`, `REQ-5.4`, `REQ-5.5`, `REQ-5.6`, `REQ-5.7`, `REQ-5.9`, `REQ-5.10`, `REQ-6.7`, `REQ-6.10` |
| Target role | system administrator, normal system member, platform administrator |
| Entry point | deployed login, platform system switch, system backend configuration pages, and normal runtime module navigation |
| User job | configure fields/dictionaries/menu/permissions, publish them, and verify the runtime member sees exactly the allowed business surface |
| Data contract | configuration mutations must be read back by admin APIs and reflected in runtime schema/menu APIs after publish |
| Permission contract | admin write/read positives, normal-member runtime positives, normal-member admin/write negatives, and platform/system boundary negatives |
| State contract | validation, disabled, no-permission, publish-check, hidden/masked field, disabled dictionary item, empty and error states must be asserted |
| Copy contract | labels, tips, disabled reasons, empty/error messages must be specific to configuration or runtime use; no generic success/placeholder wording |
| Acceptance assertions | deployed browser route audit plus API/readback assertions for configured module/field/dictionary/menu/permission state and permission positives/negatives |
| Screenshot evidence boundary | Screenshots prove visual hierarchy, overflow, clipping, and visible copy only; they do not prove data, permission, persistence, or requirement closure. API/readback assertions are required. |

Evidence paths:

- Script: `scripts/recovery-r53-frc2-no-code-configuration-coherence.ps1`
- Result: `docs/evidence/recovery/r53-frc2-no-code-configuration-coherence-result.json`
- Summary: `docs/evidence/recovery/r53-frc2-no-code-configuration-coherence-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r53-frc2-no-code-configuration/no-code-configuration-browser-audit.json`

Explicitly not complete if:

- Configuration saves but runtime menu/schema/permissions do not reflect it after publish.
- Generated CRUD exists but coded publish, permission, dictionary impact, or runtime behavior is missing.
- Normal members see admin-only configuration copy/actions or can mutate configuration.
- Permission preview disagrees with runtime API/UI behavior.
- Screenshots pass but API/readback or permission negatives are absent.

Result:

- PASS on deployed `http://127.0.0.1:18131`.
- Fresh R38 module lifecycle/admin table evidence passed: columns `3`, filters `6`, sorters `1`, import/export mappings `3`, publish-check passed, rollback `ROLLED_BACK`, forbidden create/admin `403/403`, browser results `2`, overflow `0`, blockers `0`.
- Fresh R44 permission preview/runtime evidence passed: runtime role `1214`, preview denied `record.create`, hidden field rule `HIDDEN`, normal schema columns `5`, secret field leaked `false`, create disabled `true`, forbidden create/admin `403/403`, browser results `4`, overflow `0`, blockers `0`.
- Fresh R45 field/dictionary/menu evidence passed: field count `12`, required field types present, dictionary published `DICT_TYPE_v1782891592075`, disabled dictionary item count `1`, active runtime options `ACTIVE/PENDING`, normal schema columns `15`, forbidden admin `403`, browser results `6`, overflow `0`, blockers `0`.
- Static usability audit passed with blockers `0` and warnings `0`.

Still partial:

- R53 is no-code configuration coherence evidence only. Runtime user depth, messages/work management breadth, final requirement coverage, user acceptance, and `gates.user_script_passed=false` remain open.

## REC-P0-054 FRC-3 Runtime User Depth Fresh Closure

Status: `accepted`; engineering evidence only, not final product completion.

User role: normal system member, requester/approver where messages are involved, system administrator

Business outcome: a normal member can use the running system for daily work across runtime records, files, import/export, messages, work-management surfaces, list/form/detail states, and mobile containment without seeing admin clutter or fake success states.

Requirement source: `REQ-4.4`, `REQ-5.11`, `REQ-5.13`, `REQ-5.14`, `REQ-5.16`, `REQ-5.20`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, `REQ-6.11` from `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, FRC-3 Runtime User Depth in `docs/evidence/final-requirement-gap-report.md`, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: normal-member runtime module list/detail/form, attachment controls, import/export panels and task result states, message center, work management daily surfaces, empty/error/no-permission/disabled states, and mobile/desktop containment.

Backend scope: only code backend behavior needed for runtime record/file/import-export/message/work readback, permission decisions, task/result state, audit/trace evidence, and reload/re-login persistence.

Generator scope: generated dynamic record/value tables, upload tables, task tables, and list plumbing are infrastructure only. The slice closes only when coded runtime behavior and frontend states let the role complete the daily job.

Data scope: modules, records, field values, attachments, import/export tasks and result files, messages, work items or daily-report evidence where applicable, member role context, operation history, and cleanup.

Permission rule: normal members can use only permitted runtime actions; denied record/file/import/export/admin/work/message mutations are hidden or disabled in UI and rejected by backend with deterministic status; system admins configure supporting surfaces but cannot be used as proof of normal-member usability.

States: loading, empty, populated, validation, save/readback, detail tabs, file upload/download failure, import precheck/confirm/result, export task/result, message read/archive/filter, work item/report empty/result, no-permission, backend failure, reload/re-login, and mobile/desktop containment.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.4`, `REQ-5.11`, `REQ-5.13`, `REQ-5.14`, `REQ-5.16`, `REQ-5.20`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, `REQ-6.11` |
| Target role | normal system member, requester/approver for message/todo coupling, system administrator only for supporting configuration |
| Entry point | deployed login, platform system switch, normal runtime modules, message entry, work-management entry, and mobile-width deployed routes |
| User job | create/read/update records, inspect details, use files/import/export, read or act on messages, use daily work surfaces, and recover from empty/error/no-permission states |
| Data contract | mutations must be read back by runtime APIs and visible after reload/re-login; task/message/work evidence must include persisted ids or trace/readback |
| Permission contract | normal-member positives and negatives, admin-only route denials, denied direct API calls, and field/action/data-scope pruning |
| State contract | empty, loading, validation, disabled, no-permission, error, async task, readback, reload/re-login, and mobile containment states must be asserted |
| Copy contract | labels, tips, disabled reasons, task results, import/export messages, and empty/error messages must be action-specific; no generic success/placeholder wording |
| Acceptance assertions | deployed browser route audit plus API/readback assertions for runtime record/file/import/export/message/work states and permission positives/negatives |
| Screenshot evidence boundary | Screenshots prove visual hierarchy, overflow, clipping, visible state, and visible copy only; they do not prove data, permission, persistence, task completion, or requirement closure. API/readback assertions are required. |

Evidence paths:

- Script: `scripts/recovery-r54-frc3-runtime-user-depth.ps1`
- Result: `docs/evidence/recovery/r54-frc3-runtime-user-depth-result.json`
- Summary: `docs/evidence/recovery/r54-frc3-runtime-user-depth-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r54-frc3-runtime-user-depth/runtime-user-depth-browser-audit.json`

Explicitly not complete if:

- Runtime CRUD works only by API and not from deployed normal-member pages.
- Files, import/export, messages, or work management are only buttons/toasts without persisted task/result/readback state.
- Admin evidence is used to claim normal-member daily usability.
- Empty/error/no-permission states are generic, invisible, or misleading.
- Mobile route evidence passes only by hiding core actions rather than making the workflow usable.
- Screenshots pass but API/readback or permission negatives are absent.

Result:

- PASS on deployed `http://127.0.0.1:18131`.
- Fresh R47 runtime evidence passed: record `422`, uploaded file `file_fdb4f2ac_1782892960903`, attachment count `4`, history count `2`, import `SUCCESS/2`, export `SUCCESS`, result file `file_export_result_c46bf0ea`, runtime total `3`, hidden-field leak `false`, browser results `10`, overflow `0`, blockers `0`.
- Fresh R48 message/todo/approval evidence passed: todo `76`, message `78`, action `HANDLED`, duplicate action idempotent, terminal runtime/detail state `APPROVED`, pending after approve `0`, handled after approve `1`, browser before/after results `8/6`, overflow `0`, blockers `0`.
- Fresh R43 work/todo/message/mobile surface evidence passed: `16` routes, `32` desktop/mobile results, failures `0`, warnings `0`, work routes `4`, todo routes `4`, message routes `4`.
- Static usability audit passed with blockers `0` and warnings `0`.

Still partial:

- R54 is runtime user depth evidence only. Broader workflow variants, OpenAPI/AI depth, operations breadth, final requirement coverage, user acceptance, and `gates.user_script_passed=false` remain open.

## REC-P0-055 FRC-4 Workflow Integration AI Depth Fresh Closure

Status: `accepted` as deployed engineering evidence only; final product acceptance remains open.

User role: system administrator, requester, approver, normal system member, external integrator

Business outcome: workflow, approval, todo/message, OpenAPI, and assistant/AI integrations behave as connected product capabilities with persisted state, permission boundaries, audit/readback, and clear user-facing states.

Requirement source: `REQ-4.5`, `REQ-5.12`, `REQ-5.15`, `REQ-5.19` from `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, FRC-4 Workflow Integration AI Depth in `docs/evidence/final-requirement-gap-report.md`, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: workflow workbench/canvas, approval/todo/message surfaces, OpenAPI app surface, assistant/AI confirmation surfaces, failure/denied states, and desktop/mobile containment.

Backend scope: only code backend behavior needed for workflow publish/runtime/todo/message closure, OpenAPI secret/scope/call-log behavior, assistant policy/confirmation/readback, permission negatives, and audit evidence.

Generator scope: generated CRUD, tables, and transport endpoints are plumbing only. Workflow runtime, OpenAPI authorization/logging, and AI write confirmation are coded business behavior.

Data scope: flows, nodes, runtime instances, todos, messages, OpenAPI apps/secrets/logs, assistant policies, confirmation records, business records, audit traces, and cleanup.

Permission rule: requesters cannot approve their own denied actions, approvers can handle assigned work, external apps can only use authorized scopes, normal members cannot mutate admin integration config, and platform/system AI boundaries remain separated.

States: publish-check, simulation, pending/handled todo, unread/read message, approved/rejected/duplicate action, OpenAPI success/failure logs, secret denial, assistant preview/confirm/reject, disabled/no-permission, loading/error, and mobile/desktop containment.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.5`, `REQ-5.12`, `REQ-5.15`, `REQ-5.19` |
| Target role | system administrator, requester, approver, normal system member, external integrator |
| Entry point | deployed login, system backend workflow/OpenAPI/AI configuration, normal runtime approval/todo/message, external API call, assistant entry |
| User job | configure/publish workflow and integrations, submit/approve work, receive todo/message, call OpenAPI safely, and confirm/reject AI-assisted writes |
| Data contract | flow, todo, message, OpenAPI log, assistant confirmation, business record, and audit rows must be persisted and read back |
| Permission contract | requester/approver separation, external scope positives/negatives, normal-member admin denials, platform/system AI boundary negatives |
| State contract | publish-check, simulation, pending/terminal approval, message delivery/readback, OpenAPI success/failure, assistant preview/confirm/reject, duplicate/idempotent action, and error states |
| Copy contract | labels, tips, disabled reasons, integration errors, confirmation text, and task/message results must be specific; no generic success/placeholder wording |
| Acceptance assertions | deployed browser route audit plus API/readback assertions for workflow/message/OpenAPI/assistant states and permission positives/negatives |
| Screenshot evidence boundary | Screenshots prove visual hierarchy, overflow, clipping, visible state, and visible copy only; they do not prove workflow closure, integration permission, audit persistence, AI boundary, or requirement closure. API/readback assertions are required. |

Evidence paths:

- Planned script: `scripts/recovery-r55-frc4-workflow-integration-ai-depth.ps1`
- Planned result: `docs/evidence/recovery/r55-frc4-workflow-integration-ai-depth-result.json`
- Planned summary: `docs/evidence/recovery/r55-frc4-workflow-integration-ai-depth-2026-07-01.md`
- Planned browser audit: `docs/evidence/recovery/screenshots/r55-frc4-workflow-integration-ai-depth/workflow-integration-ai-browser-audit.json`

Explicitly not complete if:

- Workflow evidence stops at publish/check and does not close record/todo/message terminal states.
- OpenAPI works only for happy-path calls without wrong-secret/read-only/scope-denial logs.
- Assistant/AI only shows a button or toast without preview/confirm/reject/audit readback.
- Platform AI can write system business data or system AI bypasses role/field/action/data-scope permission.
- Screenshots pass but API/readback or permission negatives are absent.

Acceptance evidence:

- `scripts/recovery-r55-frc4-workflow-integration-ai-depth.ps1` PASS on `http://127.0.0.1:18131`.
- Release health database/schema/Redis: `UP`.
- Static usability audit: blockers `0`, warnings `0`.
- Fresh workflow/message evidence: R48 PASS, flow `131`, todo `77`, message `79`, action `HANDLED`, terminal `APPROVED`, duplicate trace same `true`, browser before/after `8/6`, overflow `0`, blockers `0`.
- Fresh OpenAPI/assistant evidence: R49 PASS, OpenAPI app `54`, record `435`, SecretRef `sec_openapi_r49_app_0701161859351_3df407_fac2bc9a`, rotation job `srj_openapi_54_e43ab661`, success logs `5`, failed logs `1`, read-only denied `403`, wrong secret denied `401`, platform Agent denied `REJECTED_BY_SCOPE`, confirmations `SYSTEM_AGENT_WRITE_CONFIRM/WORK_AGENT_DRAFT_CONFIRM`, browser results `6`, overflow `0`, blockers `0`.
- Result: `docs/evidence/recovery/r55-frc4-workflow-integration-ai-depth-result.json`.
- Summary: `docs/evidence/recovery/r55-frc4-workflow-integration-ai-depth-2026-07-01.md`.
- Browser aggregation: `docs/evidence/recovery/screenshots/r55-frc4-workflow-integration-ai-depth/workflow-integration-ai-browser-audit.json`.

Still partial:

- R55 is workflow/integration/AI depth evidence only. Broader workflow variants, richer OpenAPI/assistant failure and UX states, operations breadth, final requirement coverage, user acceptance, and `gates.user_script_passed=false` remain open.

## REC-P0-056 FRC-5 Operations Robustness Delivery Fresh Closure

Status: `accepted` as deployed FRC-5 engineering evidence only; final usable-system acceptance remains `PARTIAL`.

User role: platform administrator, system administrator, operator, normal system member

Business outcome: operations, logs, release delivery, robustness, launch rules, and appendix clarifications behave as usable product capabilities with persisted audit/readback, safe permissions, restart/deployment evidence, and clear user-facing states.

Requirement source: `REQ-4.6`, `REQ-5.17`, `REQ-5.18`, `REQ-7`, `REQ-8`, `REQ-10`, `REQ-14.1-14.37`, and `REQ-A` from `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, FRC-5 Operations/Robustness/Delivery in `docs/evidence/final-requirement-gap-report.md`, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: platform/system operations center, log search/detail surfaces, risky-action confirmations, health/release status, launch-rule visible states, backup/rollback/cache/quota/gray-release entries where implemented, and desktop/mobile containment.

Backend scope: only code backend behavior needed for health/release checks, persisted login/business/API/AI/import/export/approval audit readback, operations settings/actions, forbidden-operation audit failures, delivery package verification, and launch-rule evidence.

Generator scope: generated tables/endpoints are plumbing only. Operations behavior, audit semantics, release packaging verification, launch rules, and user-facing risk confirmations are coded business behavior.

Data scope: audit logs, operation traces, health records, release package metadata, server script evidence, operation settings, risky action tasks, forbidden failure rows, launch-rule rows, and cleanup.

Permission rule: operators/admins can use authorized operations surfaces, normal members cannot mutate platform/system operations, forbidden operations must persist failure audit rows, and delivery scripts must not imply user signoff.

States: health up/down, release verify pass/fail, operation queued/success/failure, forbidden/denied, audit detail found/not found, log filters, restart/stop/start outcomes, disabled/no-permission, loading/error, and mobile/desktop containment.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.6`, `REQ-5.17`, `REQ-5.18`, `REQ-7`, `REQ-8`, `REQ-10`, `REQ-14.1-14.37`, `REQ-A` |
| Target role | platform administrator, system administrator, operator, normal system member |
| Entry point | deployed login, platform/system operations and log surfaces, release package scripts, health endpoints |
| User job | verify operations health, inspect audit logs, run safe operation checks, prove release delivery, and see clear operation states |
| Data contract | audit logs, operation settings, release metadata, health state, operation task/readback, forbidden failure logs, and launch-rule evidence must persist and be readable |
| Permission contract | admin/operator positives, normal-member operation denials, forbidden failures persisted in logs, and delivery evidence separate from user signoff |
| State contract | health/release success, operation action states, forbidden/error states, audit search/detail states, restart/package evidence, and disabled/no-permission states |
| Copy contract | labels, tips, disabled reasons, operation errors, log detail text, and release messages must be specific; no generic success/placeholder wording |
| Acceptance assertions | deployed browser route audit plus API/readback assertions for health/logs/operations/release states and permission positives/negatives |
| Screenshot evidence boundary | Screenshots prove visual hierarchy, overflow, clipping, visible state, and visible copy only; they do not prove operations safety, release integrity, audit persistence, robustness, launch-rule coverage, or requirement closure. API/readback assertions are required. |

Evidence paths:

- Script: `scripts/recovery-r56-frc5-operations-robustness-delivery.ps1`
- Result: `docs/evidence/recovery/r56-frc5-operations-robustness-delivery-result.json`
- Summary: `docs/evidence/recovery/r56-frc5-operations-robustness-delivery-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r56-frc5-operations-robustness-delivery/operations-robustness-delivery-browser-audit.json`

Explicitly not complete if:

- Operations evidence stops at health `UP` without persisted log/detail/readback.
- Release evidence verifies only backend health and not deployed frontend assets or script behavior.
- Normal members can mutate operations settings or forbidden operation attempts do not write failure audit rows.
- Launch-rule or robustness evidence is only a checklist without deployed API/browser/readback proof.
- Screenshots pass but API/readback or permission negatives are absent.

Result:

- R56 PASS on deployed release `http://127.0.0.1:18131`: release health `database/schema/redis UP`, static usability blockers `0` warnings `0`, fresh R50 release verification `PASS`, server commands `start/stop/restart/status/health`, persisted operations task/readback for backup/restore/archive/rollback, deployment/cache readback, platform audit rows `9`, system audit rows `1`, normal-member operation denials `403/403` with `FAILURE` audit rows, browser results `6`, overflow `0`, blockers `0`, cleanup `875/876`.

Still partial:

- R56 is operations/robustness/delivery engineering evidence only. Human information-architecture acceptance, broader role journey coherence, remaining requirement rows, and `gates.user_script_passed=false` remain open.

## REC-P0-057 FRC-6 Human Acceptance Pass Fresh Closure

Status: `accepted` as deployed FRC-6 engineering evidence only; final usable-system acceptance remains `PARTIAL`.

User role: platform normal member, platform administrator, system administrator, system normal member

Business outcome: a real person can enter the deployed product, understand which shell they are in, follow the primary platform/system/work/runtime/admin journeys without mixed navigation or crowded page stacks, and finish representative jobs without relying on hidden prototype knowledge.

Requirement source: `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, and `REQ-6.2` from `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, FRC-6 Human Acceptance Pass in `docs/evidence/final-requirement-gap-report.md`, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: login, platform workbench, platform admin, system switch, system dashboard, runtime module, work management, todo/message, system admin, mobile containment, and role-specific denied states.

Backend scope: only code backend behavior needed for role context, system/member switching, route permissions, persisted journey readback, and auditability if the human-journey script exposes gaps.

Generator scope: generated CRUD/routes are plumbing only. Human-usable navigation, role shell separation, page density, action uniqueness, permission states, and task-flow closure are coded product behavior.

Data scope: accounts, systems, tenants, account-member bindings, role permissions, runtime records, todos, messages, work objects, logs, and cleanup data created by the acceptance script.

Permission rule: each role must see only its appropriate shell and actions; denied paths must be explicit and non-confusing; platform users cannot directly operate system business data without system member context.

States: first load, shell switch, system switch, list/detail, empty/loading/error, denied/disabled, mobile/desktop containment, task completion, message/todo readback, and post-action state.

V7 Human-Usable Gate:

- primary_role_journey: platform normal member, platform administrator, system administrator, and system normal member can each follow their primary deployed journey from login to a visible result.
- real_entry_sequence: login -> platform workspace or system switch -> correct shell -> target business/admin/work/runtime/todo/message route.
- one_primary_task_surface: each route must expose one active task surface; alternate views use tabs/segmented controls instead of stacked competing sections.
- role_shell_separation: platform workspace, platform admin, system runtime, and system admin must not leak the wrong role's actions or copy.
- page_stacking_risk: list/detail, board/calendar, import/export, log/detail, config panels, and assistant drawers must not all appear as simultaneous primary content.
- ambiguous_copy_or_tip_risk: labels, tips, disabled reasons, empty states, and success/failure messages must be specific and role-appropriate.
- data_readback_after_action: journey-created systems, records, work objects, todos, messages, permissions, and logs must be read back from backend state.
- permission_positive_and_negative: every role journey includes allowed and denied assertions from the same role context used by the frontend.
- reload_relogin_restart_requirement: persistence-sensitive results must survive reload or relogin; release-only checks must prove restart where applicable.
- user_signoff_boundary: script evidence can support acceptance but cannot set `gates.user_script_passed=true`.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, `REQ-6.2` |
| Target role | platform normal member, platform administrator, system administrator, system normal member |
| Entry point | deployed login and primary platform/system/work/runtime/admin routes |
| User job | enter the right shell, understand available work, complete representative platform/system/runtime/work/todo/message/admin tasks, and avoid mixed or crowded flows |
| Data contract | journey-created systems, members, records, tasks, messages, role context, and audit/readback state must persist and be readable |
| Permission contract | role positives and negatives must match the visible shell; denied routes must not leak admin/business capabilities |
| State contract | each primary route must show one coherent active view with loading/empty/error/denied states and no page stacking |
| Copy contract | page labels, disabled reasons, empty states, action names, and shell labels must be specific and role-appropriate |
| Acceptance assertions | deployed browser journey audit plus API/readback assertions for route, role, context, permission, data, and post-action state |
| Screenshot evidence boundary | Screenshots prove visual hierarchy, overflow, clipping, visible state, and visible copy only; they do not prove user signoff or requirement closure. API/readback assertions and explicit user signoff remain separate. |

Evidence paths:

- Script: `scripts/recovery-r57-frc6-human-acceptance-pass.ps1`
- Result: `docs/evidence/recovery/r57-frc6-human-acceptance-pass-result.json`
- Summary: `docs/evidence/recovery/r57-frc6-human-acceptance-pass-2026-07-01.md`
- Browser audit: `docs/evidence/recovery/screenshots/r57-frc6-human-acceptance-pass/human-acceptance-browser-audit.json`

Explicitly not complete if:

- The script only checks routes or screenshots without role/data/readback assertions.
- Platform and system shells mix navigation, copy, or actions for the wrong role.
- A page stacks multiple primary views together instead of one clear active task surface.
- Normal members see admin-only actions or admin denied states leak confusing backend details.
- Browser evidence passes but the user has not verified or signed off; `gates.user_script_passed` must remain false.

Result:

- R57 PASS on deployed release `http://127.0.0.1:18131`.
- Release health `database/schema/redis UP`; static usability audit blockers `0`, warnings `0`.
- Fresh role journey evidence passed: routes `16`, viewports `2`, browser results `32`, failures `0`, warnings `0`.
- Role shell and permissions passed: forbidden create/admin HTTP `403/403`, journey-created system `883`, module `338`, admin and normal runtime browser evidence present.
- V7 gate assertions covered role journey, real entry sequence, one-primary-surface thresholds, role shell separation, copy/tip checks, data readback, permission positives/negatives, and deployed release verification.

Still partial:

- R57 is human-usable journey engineering evidence only. It did not reduce all high-density surfaces, close all requirement rows, or set `gates.user_script_passed=true`.

## REC-P0-058 High-Density Surface Convergence

Status: `accepted` as deployed density/usability engineering evidence only; final usable-system acceptance remains `PARTIAL`.

User role: system administrator, system normal member, platform administrator

Business outcome: high-density runtime and work surfaces converge repeated buttons/panels into task-oriented controls while preserving the same deployed role journeys, data readback, and permission negatives.

Requirement source: `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, `REQ-4.4`, `REQ-6.2`, and `REQ-6.4` from `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, R57 FRC-6 browser evidence, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: runtime record list/header/row actions and work management dashboard/task controls.

Backend scope: no new backend behavior unless density convergence exposes a missing API binding; existing R57 role/data/permission checks must still pass.

Generator scope: generated CRUD/routes are plumbing only. Surface convergence, action grouping, role clarity, and page-density thresholds are coded product behavior.

Data scope: runtime records, work dashboard/task data, role context, permission negatives, and deployed browser audit metrics.

Permission rule: menu consolidation must not expose hidden actions to normal members or bypass existing backend denials.

States: desktop/mobile containment, menu actions, one-primary-surface density, no overflow/clipping/control text overflow, static copy quality, denied route/API states, and post-convergence role journey readback.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, `REQ-4.4`, `REQ-6.2`, `REQ-6.4` |
| Target role | system administrator, system normal member, platform administrator |
| Entry point | deployed runtime module, work management, and R57 primary role routes |
| User job | use runtime/work pages without action piles, keep the primary task obvious, and retain representative admin/normal journeys |
| Data contract | R57-created system/module/runtime/work evidence and browser audit metrics must be readable from evidence files |
| Permission contract | normal-member admin/create negatives remain HTTP `403`; UI action grouping must not create permission leaks |
| State contract | runtime and work pages stay under density thresholds, no overflow/clipping/control text overflow, and static audit remains blocker/warning free |
| Copy contract | no placeholder/generic/demo/mojibake findings after convergence |
| Acceptance assertions | deployed release verification, static usability audit, fresh R57 rerun, and R58 density thresholds all pass |
| Screenshot evidence boundary | Screenshots and browser metrics prove visible density/containment/copy only; they do not prove user signoff or full requirement closure. |

Evidence paths:

- Script: `scripts/recovery-r58-high-density-surface-convergence.ps1`
- Result: `docs/evidence/recovery/r58-high-density-surface-convergence-result.json`
- Summary: `docs/evidence/recovery/r58-high-density-surface-convergence-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r58-high-density-surface-convergence/high-density-surface-convergence-browser-audit.json`

Explicitly not complete if:

- Runtime rows still expose long button piles instead of grouped row actions.
- Work dashboard/task controls still stack competing primary surfaces.
- R57 role journey regresses after density convergence.
- Static usability audit reintroduces placeholder, generic, demo, mojibake, or fake-completion findings.
- Browser evidence passes but the user has not verified or signed off; `gates.user_script_passed` must remain false.

Result:

- R58 PASS on deployed release `http://127.0.0.1:18131`.
- Deployed asset: `http://127.0.0.1:18131/assets/index-BnZhqLS7.js`.
- Static usability audit blockers `0`, warnings `0`.
- Runtime module max visible button count `27`; work max panel count `7`; work max button count `14`.
- Fresh R57 role journey still passed after convergence, with forbidden create/admin HTTP `403/403`.

Still partial:

- R58 improves human-use density evidence but does not close all admin configuration depth, all list/runtime variants, all requirement rows, or user signoff.

## REC-P0-059 FRC-2/FRC-6 Admin Configuration Human First-Use Closure

Status: `accepted` as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

User role: system administrator and system normal member

Business outcome: a system administrator can configure the core no-code structure from first-use through module, field, dictionary, menu, role permission, and permission preview without broad stacked admin panels; a normal member then sees the resulting runtime navigation/schema exactly as configured.

Requirement source: `REQ-4.3`, `REQ-5.3`, `REQ-5.3.1`, `REQ-5.4`, `REQ-5.5`, `REQ-5.6`, `REQ-5.7`, `REQ-5.9`, `REQ-5.10`, `REQ-6.2`, `REQ-6.7`, and `REQ-6.10` from `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, FRC-2 no-code configuration rows in `docs/evidence/final-requirement-gap-report.md`, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: system admin module/configuration entries, field designer, dictionary page, module group/menu configuration, role permission workbench, permission preview, normal runtime navigation/schema reflection, and desktop/mobile containment.

Backend scope: only code backend behavior needed for configuration save/readback/publish, permission preview consistency, menu/runtime schema reflection, role/member identity agreement, and permission negatives.

Generator scope: generated CRUD, tables, and base endpoints are plumbing only. First-use configuration flow, publish/readback semantics, permission preview, runtime reflection, copy/tip clarity, and density convergence are coded product behavior.

Data scope: modules, module groups, fields, dictionaries, roles, members, permissions, page/runtime schema, publish versions, audit/readback records, and cleanup data.

Permission rule: system administrators can configure; normal members can only see/use authorized runtime modules/fields/actions; forbidden admin/config/runtime writes return backend denials and clear frontend states.

States: first-use empty state, draft/published, disabled/unpublished module/menu, hidden/readonly field, permission preview allow/deny, loading/empty/error, mobile/desktop containment, post-action readback, and normal-member denied state.

V7 Human-Usable Gate:

- primary_role_journey: system administrator configures no-code structure and normal member uses the resulting runtime shape.
- real_entry_sequence: login -> system switch -> system admin configuration -> publish/readback -> normal runtime navigation/schema.
- one_primary_task_surface: each admin configuration entry exposes one active configuration task; dense secondary actions use tabs, segmented controls, or menus.
- role_shell_separation: system admin configuration copy/actions do not leak into normal runtime; normal runtime does not expose admin-only actions.
- page_stacking_risk: module, field, dictionary, menu, and permission workbenches must not render as one broad mixed pile.
- ambiguous_copy_or_tip_risk: labels, disabled reasons, validation messages, permission explanations, and publish results are specific.
- data_readback_after_action: every configured module/field/dictionary/menu/permission has API readback and runtime reflection evidence.
- permission_positive_and_negative: administrator positives and normal-member admin/write denials are asserted from the same role context used by the frontend.
- reload_relogin_restart_requirement: configuration-sensitive runtime results survive deployed release verification and fresh browser execution.
- user_signoff_boundary: script evidence can support acceptance but cannot set `gates.user_script_passed=true`.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.3`, `REQ-5.3`, `REQ-5.3.1`, `REQ-5.4`, `REQ-5.5`, `REQ-5.6`, `REQ-5.7`, `REQ-5.9`, `REQ-5.10`, `REQ-6.2`, `REQ-6.7`, `REQ-6.10` |
| Target role | system administrator and system normal member |
| Entry point | deployed login, system switch, system admin configuration routes, and normal runtime route |
| User job | configure a usable no-code app structure, publish/read it back, preview permissions, and verify normal runtime reflection without stacked admin pages |
| Data contract | module, group/menu, field, dictionary, role/member, permission, preview, runtime schema, publish version, and audit/readback rows must persist and be readable |
| Permission contract | admin positives, normal-member admin/config denials, hidden/readonly/runtime action permission reflection, and preview/runtime/backend agreement |
| State contract | first-use, draft/published, disabled/unpublished, hidden/readonly, validation, no-permission, empty/error, and mobile/desktop density states |
| Copy contract | page labels, tips, disabled reasons, publish/check results, permission explanations, and empty/error copy must be specific and role-appropriate |
| Acceptance assertions | deployed browser/API/readback assertions for admin configuration, normal runtime reflection, density thresholds, permission positives/negatives, and cleanup |
| Screenshot evidence boundary | Screenshots prove visual hierarchy, overflow, clipping, visible state, and visible copy only; screenshots do not prove functional completion. API/readback assertions plus permission/state assertions prove behavior. |

Evidence paths:

- Script: `scripts/recovery-r59-frc2-frc6-admin-configuration-human-first-use.ps1`
- Result: `docs/evidence/recovery/r59-frc2-frc6-admin-configuration-human-first-use-result.json`
- Summary: `docs/evidence/recovery/r59-frc2-frc6-admin-configuration-human-first-use-2026-07-02.md`
- Browser first-use result: `docs/evidence/recovery/r59-admin-first-use-browser-result.json`
- Browser audit: `docs/evidence/recovery/screenshots/r59-admin-first-use/admin-first-use-browser-audit.json`
- Deep no-code chain: `docs/evidence/recovery/r53-frc2-no-code-configuration-coherence-result.json`

Accepted evidence:

- R59 PASS on deployed `http://127.0.0.1:18131` with frontend asset `/assets/index-Dc298sMo.js`.
- Module configuration now exposes one active task surface at a time across lifecycle, fields, list/action/import-export, page, and print.
- Desktop/mobile browser first-use audit covered all five task tabs with `browserResultCount=10`, `browserOverflowCount=0`, and `browserBlockerCount=0`.
- Fresh R53 no-code chain still passes: R38 module lifecycle/admin table, R44 permission preview/runtime agreement, and R45 field/dictionary/menu configuration.
- Static usability audit reports blockers `0`, warnings `0`; release verification reports database/schema/Redis `UP`.
- `accepted=true` in the result means engineering evidence only. `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.

Explicitly not complete if:

- The task only reruns R53/R57 without reducing admin configuration density or first-use ambiguity.
- Module, field, dictionary, menu, permission, and runtime schema readback do not agree.
- Normal members see admin-only configuration actions or hidden fields/modules.
- Admin configuration pages still render as a broad pile of unrelated panels.
- Screenshots pass but API/readback or permission negatives are absent.
- Browser evidence passes but the user has not verified or signed off; `gates.user_script_passed` must remain false.

## REC-P0-067 Final Role Journey And Requirement Acceptance Candidate Refresh

Status: `accepted` as final-candidate engineering evidence only; final usable-system acceptance remains `PARTIAL`.

User role: unauthenticated user, platform member, platform administrator, system administrator, normal system member, requester, approver, external integrator, assistant user, operator, and deployer

Business outcome: the deployed product is rechecked as a whole system from entry to usable work: auth, shells, no-code configuration, runtime business, workflow/todo/message, OpenAPI, AI assistant, work/logs/operations, release health, and all requirement rows are either backed by fresh evidence or left honestly open for user acceptance.

Flow source: `docs/framework/final-system-flow-blueprint.md`, all flows `A1-A4`, `P1-P2`, `S1-S2`, `C1-C4`, `B1-B5`, `E1-E2`, `AI1-AI3`, and `O1-O4`.

Requirement source: every row in `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`: `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, `REQ-4.3`, `REQ-4.4`, `REQ-4.5`, `REQ-4.6`, `REQ-5.1`, `REQ-5.2`, `REQ-5.3`, `REQ-5.3.1`, `REQ-5.4`, `REQ-5.5`, `REQ-5.6`, `REQ-5.7`, `REQ-5.8`, `REQ-5.9`, `REQ-5.10`, `REQ-5.11`, `REQ-5.12`, `REQ-5.13`, `REQ-5.14`, `REQ-5.15`, `REQ-5.16`, `REQ-5.17`, `REQ-5.18`, `REQ-5.19`, `REQ-5.20`, `REQ-6.1`, `REQ-6.2`, `REQ-6.3`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, `REQ-6.7`, `REQ-6.8`, `REQ-6.9`, `REQ-6.10`, `REQ-6.11`, `REQ-7`, `REQ-8`, `REQ-9`, `REQ-10`, `REQ-14.1-14.37`, and `REQ-A`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, `docs/user_requirement.md`, all fresh R63-R66 deployed evidence, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: deployed role-shell paths, all primary navigation groups, no mixed shells, no page stacking, no ambiguous tips, no stale static/demo/prototype copy, desktop/mobile containment, and clear final blocker list.

Backend scope: only readback/audit behavior needed to prove the final candidate matrix: health, auth, permissions, runtime data, workflow, OpenAPI, AI, work, logs, operations, and release evidence.

Generator scope: generated CRUD remains plumbing. The final candidate can only use generated surfaces when human role journeys prove they are usable with permissions, state, copy, and readback.

Data scope: R63-R66 fresh evidence plus any new final journey data, release/static/framework/coverage audit outputs, route screenshots, requirement-row matrix, blocker list, and cleanup data.

Permission rule: every role journey must assert at least one allowed path and one relevant denied path where applicable. No role may see admin-only surfaces or unsafe actions outside its scope.

States: no token, login/register/recovery, platform shell, system shell, system admin, runtime list/detail/form, workflow pending/terminal, todo/message pending/handled/read, OpenAPI success/failure, AI preview/confirm/reject, operations/logs/release, mobile, reload, denied, error, empty, and cleanup.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | all 45 coverage rows from `docs/framework/final-requirement-coverage-ledger.md` |
| Target role | unauthenticated user, platform member/admin, system admin/member, requester, approver, external integrator, assistant user, operator, deployer |
| Entry point | deployed first load, auth routes, platform routes, system routes, OpenAPI endpoint, assistant drawer, release scripts |
| User job | finish one coherent useful task per primary role and see persisted/readable results or explicit denied/failure state |
| Data contract | final matrix links each role journey to `systemId/moduleId/recordId/flowId/todoId/messageId/openApiAppId/sessionId/taskId/traceId/auditLogId` where applicable |
| Permission contract | role positives and negatives are asserted across admin, runtime, workflow, integration, AI, logs, and operations |
| State contract | success, failure, empty, disabled, pending, terminal, reload, mobile, denied, and cleanup states are asserted, not inferred |
| Copy contract | visible labels/tips/errors/success/disabled states must be role-specific and must not contain generic placeholder, stale demo, or misleading completion copy |
| Acceptance assertions | deployed browser, API/readback, release/static/framework/coverage audits, and requirement matrix prove the candidate state; user signoff remains explicit and false until the user verifies |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, overflow, clipping, and visible state only; screenshots do not prove final acceptance, persistence, permissions, or requirement closure; API/readback assertions and explicit user signoff prove behavior and acceptance |

Planned evidence:

- Script: `scripts/recovery-r67-final-role-journey-requirement-acceptance-candidate.ps1`
- Result: `docs/evidence/recovery/r67-final-role-journey-requirement-acceptance-candidate-result.json`
- Summary: `docs/evidence/recovery/r67-final-role-journey-requirement-acceptance-candidate-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r67-final-role-journey-requirement-acceptance-candidate/final-role-journey-browser-audit.json`

Accepted evidence:

- R67 PASS on `http://127.0.0.1:18131`.
- Release verification, static usability audit, and final-goal framework audit passed.
- Requirement coverage audit remained honestly open with `missing=0` and `notClosed=45`.
- Fresh R57 role-shell browser refresh passed with `resultCount=32`, `failureCount=0`, `warningCount=0`, `forbiddenCreate=403`, and `forbiddenAdmin=403`.
- R59/R63/R64/R65/R66 browser evidence all reported overflow `0` and blockers `0`.
- Requirement candidate matrix contains all `45` rows, `42` rows have fresh evidence, and `0` rows were promoted to `PROVEN`.

Still partial:

- R67 is a candidate refresh, not final acceptance. `REQ-5.8`, `REQ-6.1`, and `REQ-6.8` have no fresh R67 evidence and drive R68.
- `gates.user_script_passed` remains false.

## REC-P0-068 Page Visual Designer Fresh Evidence Closure

Status: `accepted` as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

User role: system administrator and normal system member

Business outcome: a system administrator can configure a real page through the page designer, publish it, and a normal member can use the resulting runtime page without stacked/mixed panels, unclear copy, hidden component leakage, or visual overflow on desktop/mobile.

Flow source: `docs/framework/final-system-flow-blueprint.md`, flows `C1`, `C2`, `C3`, `B1`, and `B2`.

Requirement source: `REQ-5.8`, `REQ-6.1`, and `REQ-6.8` from `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, current page-designer/runtime source, and R67 candidate matrix.

Frontend scope: page designer workbench, component catalog, component property controls, publish/readback UI, runtime page rendering, hidden component/field pruning, desktop/mobile visual hierarchy, no overflow, no generic placeholder/demo copy, and no stacked competing views.

Backend scope: existing module page definition APIs, page publish/readback, runtime page/schema readback, permission and hidden component/field enforcement. Add only narrowly needed readback fields if current APIs cannot prove the page-designer-to-runtime contract.

Generator scope: generated CRUD is not completion evidence. R68 may use existing generated plumbing only when coded page-definition, permission, and runtime rendering behavior are proven end to end.

Data scope: systemId, moduleId, pageDefinitionId/version, component definitions, component order/visibility/properties, published version, runtime page schema, normal-member runtime render result, permission negatives, browser audit, cleanup.

Permission rule: system admin can configure and publish page definitions. Normal member can only render authorized runtime components and cannot mutate page definitions or see hidden/admin-only components.

States: empty page definition, edit controls, save draft, publish check, published runtime page, normal-member runtime render, hidden component, disabled/denied admin access, mobile layout, reload, error/empty copy, and cleanup.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-5.8`, `REQ-6.1`, `REQ-6.8` |
| Target role | system administrator and normal system member |
| Entry point | `/systems/{systemId}/admin/modules`, page designer entry, runtime `/systems/{systemId}/modules` |
| User job | configure a page, publish it, and use the resulting runtime page as a normal member |
| Data contract | page definition, component list, component properties, publish version, runtime schema/render metadata, and cleanup readback |
| Permission contract | admin can configure/publish; normal member cannot write admin page config and cannot see hidden/admin-only components |
| State contract | draft, saved, published, runtime visible, hidden, denied, empty/error, desktop/mobile, reload, and cleanup |
| Copy contract | page designer labels, tips, empty states, denied reasons, and runtime component labels must be specific and not generic/demo/prototype text |
| Acceptance assertions | deployed browser and API/readback evidence prove page-designer-to-runtime behavior; visual audit proves hierarchy/overflow/copy only; user signoff remains false |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, overflow, clipping, and visible state only; screenshots do not prove final acceptance, persistence, permissions, or requirement closure; API/readback assertions and explicit user signoff prove behavior and acceptance |

Acceptance script:

- `scripts/recovery-r68-page-visual-designer-fresh-evidence.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r68-page-visual-designer-fresh-evidence-result.json`
- Summary: `docs/evidence/recovery/r68-page-visual-designer-fresh-evidence-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r68-page-visual-designer-fresh-evidence/page-visual-designer-browser-audit.json`

Explicitly not complete if:

- The page designer saves UI-only state without backend readback.
- Runtime rendering uses fallback/demo components instead of the published page definition.
- Normal member can mutate page configuration or see hidden/admin-only components.
- Desktop or mobile pages show horizontal overflow, clipped controls, stacked competing views, or misleading generic copy.
- The task only proves screenshots without API/readback and permission assertions.

Accepted evidence:

- R68 PASS on `http://127.0.0.1:18131`.
- Release verification, static usability audit, and final-goal framework audit passed.
- Fresh R42 page-designer evidence passed: `componentReadbackCount=5`, browser results `8`, browser overflow `0`, hidden field leakage `false`, hidden component leakage `false`, and forbidden create `403`.
- Fresh R46 page/runtime evidence passed: page published version `PAGE_v1782970607916`, admin schema component count `5`, browser results `10`, browser overflow `0`, browser blockers `0`, hidden field/component leakage `false`, forbidden home write `403`, and forbidden page write `403`.
- R68 result: `docs/evidence/recovery/r68-page-visual-designer-fresh-evidence-result.json`.
- R68 browser aggregate: `docs/evidence/recovery/screenshots/r68-page-visual-designer-fresh-evidence/page-visual-designer-browser-audit.json`.

Still partial:

- R68 is engineering evidence only. It does not set `gates.user_script_passed=true`.
- R68 closes the last fresh-evidence gap found by R67, but requirement rows still need a row-by-row promotion decision and any residual implementation tasks.

## REC-P0-069 Requirement Evidence Promotion And Residual Gap Decision

Status: `planned`; active next executable task after R68.

User role: platform administrator, system administrator, normal system member, requester, approver, external integrator, operator, and deployer

Business outcome: the project stops cycling through vague "not done" claims and produces a machine-checkable requirement decision table: each of the 45 requirement rows is either promoted by fresh deployed evidence, kept partial with a concrete residual gap, or explicitly held for user signoff. The result must name the next coding batch instead of asking the user to inspect again.

Flow source: `docs/framework/final-system-flow-blueprint.md`, all primary flows `A1-A4`, `P1-P2`, `S1-S2`, `C1-C4`, `B1-B5`, `E1-E2`, `AI1-AI3`, and `O1-O4`.

Requirement source: `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, `REQ-4.3`, `REQ-4.4`, `REQ-4.5`, `REQ-4.6`, `REQ-5.1`, `REQ-5.2`, `REQ-5.3`, `REQ-5.3.1`, `REQ-5.4`, `REQ-5.5`, `REQ-5.6`, `REQ-5.7`, `REQ-5.8`, `REQ-5.9`, `REQ-5.10`, `REQ-5.11`, `REQ-5.12`, `REQ-5.13`, `REQ-5.14`, `REQ-5.15`, `REQ-5.16`, `REQ-5.17`, `REQ-5.18`, `REQ-5.19`, `REQ-5.20`, `REQ-6.1`, `REQ-6.2`, `REQ-6.3`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, `REQ-6.7`, `REQ-6.8`, `REQ-6.9`, `REQ-6.10`, `REQ-6.11`, `REQ-7`, `REQ-8`, `REQ-9`, `REQ-10`, `REQ-14.1-14.37`, and `REQ-A`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, `docs/framework/final-system-flow-blueprint.md`, R67 candidate matrix, R68 page/visual/page-designer evidence, and the current deployed release.

Frontend scope: no new UI work unless the promotion audit finds a reproducible blocker. The audit must evaluate current deployed role journeys, visible copy, page stacking, role shell separation, browser overflow/blockers, and whether evidence covers the requirement row.

Backend scope: no new backend work unless the promotion audit finds a reproducible blocker. The audit must evaluate API/readback evidence, permission positives/negatives, state transitions, logs, release health, and cleanup links.

Generator scope: generated CRUD/API plumbing is evidence input only. R69 may not promote a row solely because generated endpoints exist; promotion requires coded behavior, deployed browser/API/readback evidence, permission boundaries, and clear user-facing state.

Data scope: 45 requirement rows, R59/R63/R64/R65/R66/R67/R68 evidence files, coverage ledger, gap report, final usable-system acceptance, role-journey browser audits, release/static/framework/coverage audit outputs, promoted/partial decision matrix, and next residual batch.

Permission rule: promotion must include both positive authorized role behavior and negative forbidden-role/API behavior when the requirement has permissions. Missing permission negatives keep a row partial.

States: promoted, partial, needs new coding batch, blocked by explicit user signoff, evidence missing, evidence stale, browser-only evidence insufficient, user signoff false, and cleanup.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | All 45 rows: `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, `REQ-4.3`, `REQ-4.4`, `REQ-4.5`, `REQ-4.6`, `REQ-5.1`, `REQ-5.2`, `REQ-5.3`, `REQ-5.3.1`, `REQ-5.4`, `REQ-5.5`, `REQ-5.6`, `REQ-5.7`, `REQ-5.8`, `REQ-5.9`, `REQ-5.10`, `REQ-5.11`, `REQ-5.12`, `REQ-5.13`, `REQ-5.14`, `REQ-5.15`, `REQ-5.16`, `REQ-5.17`, `REQ-5.18`, `REQ-5.19`, `REQ-5.20`, `REQ-6.1`, `REQ-6.2`, `REQ-6.3`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, `REQ-6.7`, `REQ-6.8`, `REQ-6.9`, `REQ-6.10`, `REQ-6.11`, `REQ-7`, `REQ-8`, `REQ-9`, `REQ-10`, `REQ-14.1-14.37`, `REQ-A` |
| Target role | all primary platform/system/runtime/integration/operator roles covered by the flow blueprint |
| Entry point | deployed release `http://127.0.0.1:18131`, release/static/framework/coverage audits, R59/R63/R64/R65/R66/R67/R68 evidence, and final coverage ledger |
| User job | decide which requirement rows current evidence actually proves and create the next concrete residual implementation batch |
| Data contract | requirement row id, source, area, current evidence, fresh evidence ids, browser/API/readback assertions, permission positives/negatives, promotion decision, residual gap, and next task id |
| Permission contract | each permission-sensitive row requires positive role evidence and forbidden-role/API denial evidence before promotion |
| State contract | promoted/partial decision, stale/missing evidence, browser-only insufficiency, user-signoff boundary, next residual task, and unchanged `gates.user_script_passed=false` |
| Copy contract | promotion report must use concrete row ids, evidence paths, and residual gaps; it must not use vague completion language or misleading final tips |
| Acceptance assertions | deployed release/static/framework/coverage audits pass; 45-row matrix exists; every row has a decision and evidence reference; no row is promoted from screenshots only; user signoff remains false; next residual task is written if any row remains partial |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, overflow, clipping, and visible state only; screenshots do not prove requirement promotion, persistence, permissions, or final acceptance without API/readback assertions and explicit user signoff |

Acceptance script:

- `scripts/recovery-r69-requirement-evidence-promotion-and-gap-decision.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r69-requirement-evidence-promotion-and-gap-decision-result.json`
- Summary: `docs/evidence/recovery/r69-requirement-evidence-promotion-and-gap-decision-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r69-requirement-evidence-promotion-and-gap-decision/requirement-evidence-browser-audit.json`

Explicitly not complete if:

- Any requirement row lacks an explicit promote/partial decision.
- A row is promoted from screenshots, route existence, generated endpoints, or old summaries without fresh deployed evidence.
- User signoff is inferred or `gates.user_script_passed` is set true.
- The audit does not name the next coding batch for rows that remain partial.
- Evidence paths are missing or cannot be read.

Accepted evidence:

- R69 PASS on `http://127.0.0.1:18131`.
- Release/static/framework audits passed; requirement coverage remained honestly open with `missing=0` and `notClosed=45`.
- R67 and R68 evidence was readable and accepted.
- Promotion matrix has `45` rows, rows with fresh evidence after R68 `45`, rows with no fresh evidence `0`, promoted rows `0`, still partial rows `45`.
- R69 emitted next residual coding batch `REC-P0-070 No-Code Configuration Residual Depth Closure` with `11` rows.
- Result: `docs/evidence/recovery/r69-requirement-evidence-promotion-and-gap-decision-result.json`.
- Browser aggregate: `docs/evidence/recovery/screenshots/r69-requirement-evidence-promotion-and-gap-decision/requirement-evidence-browser-audit.json`.

Still partial:

- R69 is engineering evidence only. It does not set `gates.user_script_passed=true`.
- Requirement rows remain `PARTIAL` until R70 and later residual implementation batches close concrete gaps or the user explicitly excludes/signs off.

## REC-P0-070 No-Code Configuration Residual Depth Closure

Status: `accepted` as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

User role: system administrator, normal system member, platform administrator, and member/role manager

Business outcome: a system administrator can configure a complete no-code application structure, including app/module lifecycle, nested and disabled/unpublished module groups, field/dictionary variants, org/member binding, permission matrix preview, and runtime menu/schema reflection, without stacked panels, ambiguous tips, or generated-only behavior.

Flow source: `docs/framework/final-system-flow-blueprint.md`, flows `C1`, `C2`, `C3`, `B1`, and `B2`.

Requirement source: `REQ-4.3`, `REQ-5.3`, `REQ-5.3.1`, `REQ-5.4`, `REQ-5.5`, `REQ-5.6`, `REQ-5.7`, `REQ-5.9`, `REQ-5.10`, `REQ-6.7`, and `REQ-6.10` from `docs/framework/final-requirement-coverage-ledger.md`, `docs/evidence/final-requirement-gap-report.md`, and R69 residual decision.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, `docs/framework/final-system-flow-blueprint.md`, R59 admin first-use evidence, R53 no-code configuration coherence evidence, and R69 residual decision.

Frontend scope: system admin module/app configuration, module group/menu editor, field designer, dictionary designer, organization/member/role binding surface, permission matrix and preview, runtime module navigation, runtime schema/list reflection, denied/empty/error states, desktop/mobile containment, and copy clarity.

Backend scope: app/module lifecycle APIs, group/menu publish/readback, field/dictionary metadata readback, role/member permission preview, effective runtime permission/schema APIs, forbidden write/read denials, audit/log traces where available, and cleanup.

Generator scope: generated module CRUD is plumbing only. R70 closes only when coded admin configuration, permission preview, runtime navigation/schema reflection, and permission negatives are proven end to end.

Data scope: systemId, app/module ids, group ids, nested group relationships, disabled/unpublished group/module states, field ids/types/validation/defaults, dictionary ids/items/disabled options, member/role ids, permission matrix, runtime schema, runtime menu readback, browser audits, and cleanup ids.

Permission rule: system admin can configure app/module/menu/field/dictionary/role permission; normal member can only see authorized published runtime menu/schema/actions; disabled/unpublished/hidden modules and fields must not leak; direct forbidden APIs must return denial.

States: create/edit/publish/rollback app or module, nested group visible/hidden/disabled/unpublished, dictionary active/disabled item, field validation/default/type config, member role binding, permission allowed/denied/preview, runtime visible/hidden, reload, desktop/mobile, and cleanup.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.3`, `REQ-5.3`, `REQ-5.3.1`, `REQ-5.4`, `REQ-5.5`, `REQ-5.6`, `REQ-5.7`, `REQ-5.9`, `REQ-5.10`, `REQ-6.7`, `REQ-6.10` |
| Target role | system administrator, normal system member, platform administrator, member/role manager |
| Entry point | `/systems/{systemId}/admin/module-config`, org/member/role pages, dictionary page, runtime `/systems/{systemId}/modules` |
| User job | configure a no-code app structure, publish it, bind roles/members, preview permissions, and verify normal-member runtime reflection |
| Data contract | app/module/group/menu/field/dictionary/member/role/permission/runtime-schema ids and versions must connect admin configuration to runtime readback and cleanup |
| Permission contract | admin positives, normal-member authorized runtime positives, hidden/disabled/unpublished leakage negatives, direct forbidden API denials, and permission preview agreement |
| State contract | draft, published, rollback, disabled, unpublished, hidden, denied, visible runtime, reload, desktop/mobile, and cleanup states must be asserted |
| Copy contract | admin configuration labels, permission reasons, disabled/unpublished explanations, empty states, and runtime menu/schema labels must be concrete and not generic/demo text |
| Acceptance assertions | deployed browser/API/readback evidence proves configuration depth, runtime reflection, permission positives/negatives, visual containment, copy clarity, and cleanup; user signoff remains false |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, overflow, clipping, and visible state only; screenshots do not prove persistence, permissions, runtime schema reflection, or final acceptance without API/readback assertions |

Acceptance script:

- `scripts/recovery-r70-no-code-configuration-residual-depth.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r70-no-code-configuration-residual-depth-result.json`
- Summary: `docs/evidence/recovery/r70-no-code-configuration-residual-depth-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r70-no-code-configuration-residual-depth/no-code-configuration-browser-audit.json`

Accepted evidence:

- R70 PASS on deployed `http://127.0.0.1:18131` after rebuilding and restarting release with frontend assets `/assets/index-D3FrPh_v.js` and `/assets/index-DzYIVez0.css`.
- Backend runtime navigation metadata now carries group `visibleRoleIds` and `runtimeVisible` from module/group publish and module status. Runtime record APIs reject direct access to hidden-role, draft, disabled, and draft-group modules.
- R70 configured system `1001`, runtime role `1458`, member/binding `1358`, visible module `405`, hidden module `406`, draft module `407`, disabled module `408`, and draft-group module `409`; cleanup deleted systems `1001` and `1002`.
- Normal-member direct-access negatives returned hidden `403`, draft `403`, disabled `403`, draft-group `403`, and forbidden create `403`; visible published schema readback had `4` columns.
- Fresh R44 permission preview still passed with `previewAllowed=false`, hidden-field leak `false`, create disabled, browser blockers `0`.
- Fresh R45 field/dictionary/menu evidence still passed with field types `ATTACHMENT`, `CHILD_TABLE`, `DATE`, `DATETIME`, `DEPARTMENT`, `IMAGE`, `LONG_TEXT`, `MULTI_SELECT`, `NUMBER`, `RELATION`, `SELECT`, `TEXT`, `USER`; disabled dictionary item remained excluded from runtime options.
- Browser aggregate had result count `10`, overflow `0`, blockers `0`.
- R70 is engineering evidence only. It does not set `gates.user_script_passed=true`.

Still partial:

- Current `ModuleGroup` has no `parentId`; nested module groups are recorded as a model gap instead of falsely claimed.
- Frontend still needs stronger stable selectors and a visible member-role binding workbench so browser evidence can prove org/member/role binding depth without relying only on API readback.
- Permission preview still needs broader conflict/member-role explanation beyond the current action/field runtime agreement evidence.

Explicitly not complete if:

- Runtime menus or schemas do not read from the configured/published admin data.
- Nested, disabled, unpublished, hidden, or role-restricted modules leak to normal members.
- Permission preview disagrees with runtime behavior.
- Field/dictionary/member/role changes lack API readback or cleanup.
- Desktop/mobile pages show stacked competing panels, overflow, clipped controls, or vague tips.
- The task claims final completion or sets `gates.user_script_passed=true`.

Explicitly not complete if:

- The task claims final completion or sets `gates.user_script_passed=true`.
- Requirement rows are promoted without fresh evidence and explicit user acceptance boundary.
- It only aggregates old summaries without deployed release/static/framework/coverage audits.
- Any primary role still lands in mixed shells, stacked pages, stale demo text, ambiguous tips, or hidden-admin leakage.
- Screenshots are treated as final proof without API/readback, permissions, and requirement matrix.

## REC-P0-071 No-Code Frontend Binding And Hierarchy Residual Closure

Status: `accepted` as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

User role: system administrator, member/role manager, and normal system member

Business outcome: the no-code configuration surface becomes directly browser-verifiable for module hierarchy/status, member-role binding, field/dictionary metadata, and runtime reflection, so a human and an automated script can see which configured objects are active, hidden, disabled, unpublished, or bound to a member without guessing from API internals.

Flow source: `docs/framework/final-system-flow-blueprint.md`, flows `C1`, `C2`, `C3`, `B1`, and `B2`.

Requirement source: `REQ-4.3`, `REQ-5.3`, `REQ-5.3.1`, `REQ-5.4`, `REQ-5.5`, `REQ-5.6`, `REQ-5.7`, `REQ-5.9`, `REQ-5.10`, `REQ-6.7`, and `REQ-6.10`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, R70 residual result, and subagent frontend/backend findings recorded in this session.

Frontend scope: module group/module admin rows, runtime module buttons, field cards, dictionary rows, organization/member/role binding surface, system header group navigation, runtime schema form markers, desktop/mobile layout containment, and clear state/copy for disabled/unpublished/hidden objects.

Backend scope: only model/API work needed to support visible hierarchy or to explicitly record unsupported module-group nesting. Do not invent a fake parent relationship in frontend state if the backend schema cannot persist it.

Generator scope: generated CRUD remains plumbing. R71 closes only when coded frontend and backend contracts make member-role binding and hierarchy/status visibility browser-verifiable.

Data scope: module group ids/status/publish state, module ids/status/publish state/runtimeVisible, field codes/permission modes/dict ids, member ids, bound role ids, normal-member runtime module ids, browser selectors, and cleanup ids.

Permission rule: admin can inspect and bind roles/members; normal member sees only authorized published runtime modules and fields. Browser DOM must not contain hidden module/field identifiers for normal member runtime views.

States: flat vs unsupported nested module group, draft/published/disabled/hidden module, field permission modes, dictionary active/disabled item, member role bound/unbound, permission preview allowed/denied, runtime visible/hidden, reload, desktop/mobile, and cleanup.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.3`, `REQ-5.3`, `REQ-5.3.1`, `REQ-5.4`, `REQ-5.5`, `REQ-5.6`, `REQ-5.7`, `REQ-5.9`, `REQ-5.10`, `REQ-6.7`, `REQ-6.10` |
| Target role | system administrator, member/role manager, normal system member |
| Entry point | `/systems/{systemId}/admin/module-config`, org/role/member admin pages, dictionary page, runtime `/systems/{systemId}/modules` |
| User job | visually inspect configured objects, bind member roles, preview permissions, and verify runtime only shows authorized published objects |
| Data contract | browser data attributes and API readback must point to the same group/module/field/dict/member/role ids |
| Permission contract | admin positives, normal-member runtime positives, hidden/unpublished/disabled DOM and API negatives, and permission preview agreement |
| State contract | draft/published/disabled/hidden/bound/unbound/denied/reload/mobile states must be asserted |
| Copy contract | visible copy must explain unsupported nested module groups honestly and use concrete disabled/unpublished/hidden reasons |
| Acceptance assertions | deployed browser and API evidence prove member-role binding markers, object status markers, hidden DOM non-leakage, direct API denials, and cleanup |
| Screenshot evidence boundary | Screenshots prove visual hierarchy, overflow, clipping, and visible state only; screenshots do not prove member-role binding, hierarchy persistence, runtime permission, hidden DOM non-leakage, or functional completion without API/readback assertions, DOM selector assertions, and permission assertions |

Acceptance script:

- `scripts/recovery-r71-no-code-frontend-binding-and-hierarchy-residual.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r71-no-code-frontend-binding-and-hierarchy-residual-result.json`
- Summary: `docs/evidence/recovery/r71-no-code-frontend-binding-and-hierarchy-residual-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r71-no-code-frontend-binding-and-hierarchy-residual/no-code-frontend-binding-browser-audit.json`

Accepted evidence:

- R71 PASS on deployed `http://127.0.0.1:18131` after typecheck/build/package/restart/verify-release with frontend assets `/assets/index-DAsRRrA1.js` and `/assets/index-DzYIVez0.css`.
- Frontend runtime/admin surfaces now expose stable browser markers for group/module/field/member/role ids, publish/status/runtimeVisible state, visible role ids, active module/group context, and the explicit unsupported hierarchy boundary.
- Browser evidence proved admin module/group/field markers, member role binding markers, permission workbench role/module options, normal-member runtime visible module markers, hidden/disabled module absence, hidden field absence, system header group marker, desktop/mobile overflow `0`, and blockers `0`.
- API/readback evidence still proved visible schema columns `title,status,ownerDept,publicName`, hidden direct `403`, disabled direct `403`, forbidden create `403`, and cleanup deleted systems `1007` and `1008`.
- R71 is engineering evidence only. It does not set `gates.user_script_passed=true`.

Still partial:

- Module group hierarchy is still honestly marked unsupported because the persisted backend model has no `parentId`; this cannot be claimed as nested group support until implemented or explicitly excluded.
- Requirement rows remain `PARTIAL` until later runtime, workflow, integration, operations, and user-signoff gates close the remaining final-system gap.

Explicitly not complete if:

- Browser evidence cannot identify group/module/field/member/role ids from stable selectors.
- Normal-member runtime DOM contains hidden module or hidden field identifiers.
- Member-role binding is only API readback with no visible workbench state.
- Nested module group support is faked in frontend without persisted backend schema.
- The task claims final completion or sets `gates.user_script_passed=true`.

## REC-P0-072 Runtime Daily-Use File Import Export Error-State Residual Closure

Status: `accepted` as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

User role: normal system member, requester/approver, system administrator, and file/import-export operator

Business outcome: a normal member can use a published runtime module for daily work, including create/edit/detail/history, files, import/export, readonly/forbidden states, approval detail context, batch operations, and clear error/empty/loading states without stacked panels or ambiguous tips.

Flow source: `docs/framework/final-system-flow-blueprint.md`, flows `B1`, `B2`, `B3`, `B4`, `B5`, `C1`, `C2`, and `C3`.

Requirement source: `REQ-4.4`, `REQ-5.11`, `REQ-5.13`, `REQ-5.14`, `REQ-5.16`, `REQ-5.20`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, and `REQ-6.11`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, R47 runtime daily-use evidence, R54 runtime depth aggregation, R63 configured runtime first-use evidence, R64 workflow/todo/message evidence, and R69 residual decision.

Frontend scope: runtime module list/detail/edit/create, attachment preview/download, import/export dialogs and task results, approval detail/sidebar, batch actions, empty/error/readonly/forbidden states, mobile containment, and stable selectors for fields/files/tasks/status.

Backend scope: runtime record create/update/detail/history, attachment upload/bind/preview/download permission checks, import precheck/confirm rollback and error-file cases, export task/result-file cases, approval detail readback, batch action permissions, audit/log traces where available, and cleanup.

Generator scope: generated runtime CRUD is plumbing only. R72 closes only when coded runtime services, file/import-export behavior, workflow-state readback, permission negatives, frontend states, and deployed browser/API evidence agree.

Data scope: systemId, tenantId, moduleId, publishedVersion, record ids, field codes/ids, attachment file ids/versions/download urls, import task ids/error files/rollback results, export task ids/result files, approval/todo ids, batch action result ids, browser audit output, and cleanup ids.

Permission rule: authorized normal members can use runtime data and allowed files/import/export actions; readonly or forbidden roles cannot mutate records/files/import/export; hidden fields and unauthorized files/tasks must not leak in API or DOM; direct forbidden APIs return denial.

States: empty search, loading, validation errors, readonly create/edit, forbidden create/edit/file/download/import/export, attachment preview success/failure, missing/deleted file, import precheck success/failure, import confirm rollback/error-file, export success/failure, batch partial success/failure, approval pending/terminal, reload, desktop/mobile, and cleanup.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.4`, `REQ-5.11`, `REQ-5.13`, `REQ-5.14`, `REQ-5.16`, `REQ-5.20`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, `REQ-6.11` |
| Target role | normal system member, requester/approver, system administrator, file/import-export operator |
| Entry point | login/system switch into `/systems/{systemId}/modules`, runtime module detail, todo/message approval entry, import/export toolbar |
| User job | complete daily runtime work with records, files, import/export, approval context, batch actions, and readable failure states |
| Data contract | runtime record/file/import/export/approval ids and versions must be persisted, read back, and tied to browser-visible markers |
| Permission contract | normal authorized positives, readonly/forbidden negatives, hidden-field and unauthorized-file DOM/API non-leakage, and direct API denials |
| State contract | empty/loading/validation/readonly/forbidden/file failure/import rollback/export failure/batch partial/approval terminal/mobile states must be asserted |
| Copy contract | visible copy must explain record, file, import/export, approval, readonly, forbidden, and failure states concretely without generic success tips |
| Acceptance assertions | deployed browser/API/readback evidence proves runtime daily use, file preview/download, import/export success and failure, permission positives/negatives, clear states, mobile containment, and cleanup |
| Screenshot evidence boundary | Screenshots prove visual hierarchy, overflow, clipping, and visible state only; screenshots do not prove persistence, file permissions, import/export rollback, approval closure, hidden DOM non-leakage, or final acceptance without API/readback assertions, DOM selector assertions, and permission assertions |

Acceptance script:

- `scripts/recovery-r72-runtime-file-import-export-error-state-residual.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r72-runtime-file-import-export-error-state-residual-result.json`
- Summary: `docs/evidence/recovery/r72-runtime-file-import-export-error-state-residual-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r72-runtime-file-import-export-error-state-residual/runtime-file-import-export-error-browser-audit.json`

Accepted evidence:

- R72 PASS on deployed `http://127.0.0.1:18131` after typecheck/build/package/restart/verify-release with frontend assets `/assets/index-BCZ4MtU6.js` and `/assets/index-DzYIVez0.css`.
- R72 fixed a real backend safety gap: failed import precheck results now cannot be confirmed into runtime records. The script proved failed confirm returns `400` and produces an error file id.
- Runtime file evidence proved uploaded attachment readback status `READY`, preview HTTP `200`, download HTTP `200`, missing file HTTP `400`, denied file access `allowed=false`, detail attachments `4`, and detail history `2`.
- Runtime import/export evidence proved valid import precheck/confirm success with `2` inserted rows and rollback support, invalid precheck `FAILED` with error file `file_precheck_error_7cc793fe`, export-all result file `file_export_result_ffb5c2a9`, selected export result file `file_export_result_98ae5a1e`, and batch archive async task `QUEUED`.
- Permission and leakage evidence proved hidden field leakage `false`, readonly create `403`, normal-member admin access `403`, browser result count `10`, overflow `0`, blockers `0`, and cleanup deleted systems `1015`, `1016`, and `1017`.
- R72 is engineering evidence only. It does not set `gates.user_script_passed=true`.

Still partial:

- R72 does not close broader workflow/todo/message daily operations, reassignment, rejection, terminal conflict, message read/archive/filter pagination, or final user signoff.
- Requirement rows remain `PARTIAL` until later workflow, integration, operations, and user-signoff gates close the remaining final-system gap.

Explicitly not complete if:

- Runtime daily work only proves create/list but not detail/history/file/import/export/error states.
- Import/export success is proven without failure, rollback, result-file, or error-file assertions.
- File preview/download ignores permission, deleted/missing file, or version states.
- Approval detail/sidebar state is not tied back to the runtime record/todo/message.
- Normal-member DOM contains hidden fields, unauthorized files, or forbidden action identifiers.
- Desktop/mobile pages show stacked competing panels, overflow, clipped controls, or vague tips.
- The task claims final completion or sets `gates.user_script_passed=true`.

## REC-P0-073 Workflow Todo Message Integration Error-State Residual Closure

Status: `accepted` as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

User role: normal system requester, assigned approver, system administrator, message/todo reader, and forbidden/unassigned member

Business outcome: a configured workflow becomes daily usable through the same runtime record, todo, message, and approval surfaces. A requester can submit and see terminal feedback, an assigned approver can handle the todo, messages reflect the real state, duplicates and forbidden actions are blocked, and the user can filter/read/archive work without stacked panels or vague tips.

Flow source: `docs/framework/final-system-flow-blueprint.md`, flows `C4`, `B4`, `B5`, `B1`, and `B2`.

Requirement source: `REQ-4.5`, `REQ-5.12`, `REQ-5.16`, `REQ-5.19`, `REQ-6.9`, and `REQ-9`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, R48 workflow/message evidence, R55 workflow integration depth, R64 workflow/todo/message first-use evidence, R69 residual decision, and the current deployed runtime evidence from R72.

Frontend scope: runtime submit/approval context, todo workbench, message drawer/feed, approval sidebar/detail, filter/read/archive controls, duplicate/terminal disabled actions, requester/approver/forbidden role states, desktop/mobile containment, and stable selectors for todo/message/approval ids and status.

Backend scope: workflow publish/runtime start, approval assignment, requester/approver permission checks, todo creation/handling/readback, message creation/read/archive/filter/pagination, duplicate action idempotence, terminal conflict prevention, rejection/transfer/reassignment where supported, audit/log traces where available, and cleanup.

Generator scope: generated workflow/todo/message CRUD is plumbing only. R73 closes only when coded runtime services, assignment rules, message/todo behavior, permission negatives, frontend states, and deployed browser/API evidence agree.

Data scope: systemId, tenantId, moduleId, recordId, flowId, workflowInstanceId, approvalTraceId, todoId, messageId, requesterMemberId, approverMemberId, role ids, task/message status, read/archive state, filter/page results, browser audit output, and cleanup ids.

Permission rule: requester can submit and observe own workflow state but cannot approve their own assigned approver todo; assigned approver can handle the todo; unassigned/readonly/normal forbidden members cannot approve, reassign, archive unrelated messages, or mutate workflow configuration; direct forbidden APIs return denial and forbidden DOM/actions do not imply success.

States: draft/published flow, submit success/failure, pending approval, approved, rejected, transferred/reassigned where supported, duplicate action, terminal conflict, todo pending/handled/empty, message unread/read/archived/filter/page, requester forbidden approval, unassigned approver forbidden, reload, desktop/mobile, and cleanup.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.5`, `REQ-5.12`, `REQ-5.16`, `REQ-5.19`, `REQ-6.9`, `REQ-9` |
| Target role | normal requester, assigned approver, system administrator, message/todo reader, forbidden/unassigned member |
| Entry point | runtime record submit, `/systems/{systemId}/todos`, message drawer/feed, approval sidebar/detail, workflow admin publish/check |
| User job | submit a workflow-backed record, process the assigned approval todo, read/archive related messages, and see terminal state without duplicate or forbidden actions |
| Data contract | record/workflow/todo/message ids and statuses must be persisted, read back, and tied to browser-visible markers |
| Permission contract | requester positives, assigned-approver positives, requester self-approval denial, unassigned/readonly/normal forbidden negatives, and direct API denials |
| State contract | pending/approved/rejected/duplicate/terminal/todo handled/message read archived/filter/page/mobile states must be asserted |
| Copy contract | visible copy must explain pending, handled, rejected, duplicate, forbidden, empty, read, archived, and filtered states concretely without generic success tips |
| Acceptance assertions | deployed browser/API/readback evidence proves workflow start, assigned todo/message delivery, approval terminal readback, duplicate/forbidden prevention, message read/archive/filter/page behavior, mobile containment, and cleanup |
| Screenshot evidence boundary | Screenshots prove visual hierarchy, overflow, clipping, and visible state only; screenshots do not prove assignment correctness, todo/message persistence, terminal workflow state, permission denial, duplicate prevention, or final acceptance without API/readback assertions, DOM selector assertions, and permission assertions |

Acceptance script:

- `scripts/recovery-r73-workflow-todo-message-error-state-residual.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r73-workflow-todo-message-error-state-residual-result.json`
- Summary: `docs/evidence/recovery/r73-workflow-todo-message-error-state-residual-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r73-workflow-todo-message-error-state-residual/workflow-todo-message-error-browser-audit.json`

Accepted evidence:

- R73 PASS on deployed `http://127.0.0.1:18131`.
- Flow/runtime handoff: `systemId=1024`, `moduleId=422`, `recordId=592`, `flowId=139`, `pendingTaskId=83`, `pendingTodoId=83`, `pendingMessageId=85`.
- Flow checks: publish-check passed with impact refs `2`; simulation passed with `runtimeInstanceCreated=false` and step count `2`.
- Message states: unread before `1`, mark-all-read affected `1`, unread after `0`, read after `1`, archive affected `1`, active after archive `0`, archived after archive `1`.
- Permission and terminal conflicts: requester approve `403`, requester flow-admin `403`, duplicate todo action `TASK_STATE_CONFLICT`, terminal reject `400`, terminal transfer `400`.
- Approval readback: detail `APPROVED`, approval sidebar `APPROVED`, pending todos after approval `0`, handled todos `1`.
- Reject path: `rejectSystemId=1027`, `rejectRecordId=593`, `rejectTaskId=84`, terminal detail/sidebar `REJECTED`, pending after reject `0`, handled after reject `1`.
- Browser evidence: result count `8`, overflow `0`, blockers `0`.
- Cleanup succeeded for systems `1024`, `1025`, `1026`, `1027`, `1028`, and `1029`.
- `accepted=true` in the result means engineering evidence only. `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.

Explicitly not complete if:

- Workflow evidence stops at publish or API `200` without requester/approver/todo/message readback.
- The submitter can approve their own assigned approver todo, or an unassigned member can approve.
- Todo/message UI uses static sample rows, ambiguous cards, duplicated detail buttons, or stacked competing panels.
- Approval terminal state is not tied back to the runtime record, todo, and message.
- Message read/archive/filter/page actions have no persisted readback or visible state change.
- Duplicate approvals or terminal conflicts look successful without a clear disabled/denied state.
- Desktop/mobile pages show overflow, clipped controls, hidden forbidden action leakage, or vague tips.
- The task claims final completion or sets `gates.user_script_passed=true`.

## REC-P0-074 OpenAPI AI External-Service Error-State Residual Closure

Status: `accepted` as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

User role: system administrator, external integrator, normal system member, assistant user, operator/auditor, and forbidden external caller

Business outcome: scoped OpenAPI, external service calls, AI assistant confirmation, and logs behave as one safe platform capability. An administrator can expose only approved data, an external caller can use only the current valid secret and scope, an assistant user can preview/confirm/reject AI actions without unsafe automatic writes, and an operator can trace every success/failure through logs without leaked secrets or misleading UI states.

Flow source: `docs/framework/final-system-flow-blueprint.md`, flows `E1`, `E2`, `AI1`, `AI2`, `AI3`, and `O2`.

Requirement source: `REQ-5.15`, `REQ-5.19`, `REQ-9`, and `REQ-14.1-14.37`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, R49 OpenAPI/assistant integration evidence, R55 workflow-integration-AI aggregation, R65 external-service first-use evidence, R69 residual decision, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: system admin OpenAPI app/secret/policy surfaces, assistant drawer/session/confirmation UI, platform/system AI boundary states, integration/log readback surfaces, normal-member denied states, desktop/mobile containment, and stable selectors for app ids, secret refs, assistant session/confirmation ids, trace ids, status, and disabled reasons.

Backend scope: scoped OpenAPI app creation, secret reference and rotation, old-secret invalidation, new-secret success, allowed create/search/detail, read-only denial, wrong-secret denial, wrong-scope/system denial, assistant preview/confirm/reject/repeat-confirm handling, audit/log readback, permission negatives, and cleanup.

Generator scope: generated integration CRUD, log CRUD, and assistant list surfaces are plumbing only. R74 closes only when coded credential scope, AI confirmation state, audit logs, permission negatives, frontend states, and deployed browser/API evidence agree.

Data scope: systemId, tenantId, moduleId, recordId, openApiAppId, secretRefId, rotatedSecretRefId, success/failure traceIds, callLogIds, assistantSessionId, confirmationIds, assistant message ids, policy ids, role/member ids, browser audit output, and cleanup ids.

Permission rule: external callers can only use active scoped credentials for granted modules/actions; old secrets, wrong secrets, read-only apps, wrong-scope callers, and normal members mutating OpenAPI/AI policy are denied; AI writes require explicit human confirmation and repeated/terminal confirmations cannot look successful.

States: app draft/active/disabled where supported, secret reference, rotation, old-secret denied, new-secret success, allowed create/search/detail, read-only denied, wrong-secret denied, wrong-scope denied, assistant preview waiting, confirm success, reject success, duplicate confirm conflict or terminal no-op, platform/system scope denial, log success/failure/detail, reload, desktop/mobile, and cleanup.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-5.15`, `REQ-5.19`, `REQ-9`, `REQ-14.1-14.37` |
| Target role | system administrator, external integrator, normal system member, assistant user, operator/auditor, forbidden external caller |
| Entry point | system admin OpenAPI/AI policy pages, `/openapi` endpoint, assistant drawer/session, platform/system log pages |
| User job | expose a safe scoped external API, call it with valid credentials, use AI only through confirmation boundaries, and audit success/failure traces |
| Data contract | OpenAPI app/secret/record/log ids and assistant session/confirmation/log ids must be persisted, read back, and tied to browser-visible markers |
| Permission contract | admin positives, external scoped positives, old-secret/wrong-secret/read-only/wrong-scope negatives, normal-member policy denial, AI confirmation boundary, and direct API denials |
| State contract | credential rotation, success/failure calls, assistant preview/confirm/reject/duplicate terminal, logs, denied, reload/mobile states must be asserted |
| Copy contract | visible copy must explain secret refs, rotation, denied external calls, read-only scope, AI preview, confirmation, rejection, duplicate terminal, logs, and safety boundaries without generic success tips |
| Acceptance assertions | deployed browser/API/readback evidence proves scoped OpenAPI calls, rotation and denial states, assistant confirmation state machine, log traceability, permission negatives, mobile containment, and cleanup |
| Screenshot evidence boundary | Screenshots prove visual hierarchy, overflow, clipping, and visible state only; screenshots do not prove credential scope, AI safety, log integrity, permission denial, or final acceptance without API/readback assertions, DOM selector assertions, and permission assertions |

Acceptance script:

- `scripts/recovery-r74-openapi-ai-external-service-error-state-residual.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r74-openapi-ai-external-service-error-state-residual-result.json`
- Summary: `docs/evidence/recovery/r74-openapi-ai-external-service-error-state-residual-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r74-openapi-ai-external-service-error-state-residual/openapi-ai-external-service-error-browser-audit.json`

Accepted evidence:

- R74 PASS on deployed `http://127.0.0.1:18131`.
- OpenAPI handoff: `systemId=1032`, `moduleId=425`, `openApiAppId=60`, `openApiRecordId=595`.
- Secret rotation: old SecretRef `sec_openapi_r74_app_0702161011730_b380bc_f8e70a96`, active SecretRef `sec_openapi_r74_app_0702161011730_b380bc_rot_faf7cbfc`, rotation job `srj_openapi_60_faf7cbfc`, old-secret denied `401`.
- External-call states: success logs `5`, failed logs `2`, read-only denied `403`, wrong-secret denied `401`.
- AI/assistant states: platform denied `REJECTED_BY_SCOPE`, policy publish-check passed, write preview `WAITING_HUMAN_CONFIRM`, write confirmed `CONFIRMED`, duplicate confirm denied `400`, write rejected `REJECTED`, draft preview `WAITING_HUMAN_CONFIRM`, draft confirmed `CONFIRMED`.
- Permission negatives: normal OpenAPI app create `403`, normal agent policy create `403`.
- Audit/browser evidence: agent audit logs `12`, confirmation audit logs `6`, browser result count `6`, overflow `0`, blockers `0`.
- Cleanup succeeded for systems `1032` and `1033`.
- `accepted=true` in the result means engineering evidence only. `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.

Explicitly not complete if:

- OpenAPI evidence stops at admin app creation or API `200` without scoped external credential behavior and log readback.
- Secret rotation leaves the old secret usable, exposes raw secrets in UI/logs, or cannot prove the new secret path.
- Read-only, wrong-secret, wrong-scope, or normal-member denials look like successful UI/API actions.
- Assistant writes happen without explicit human confirmation, or repeat/terminal confirmation looks successful without a clear conflict/terminal state.
- Logs are static/sample rows or cannot be tied to call/assistant trace ids from real operations.
- Desktop/mobile pages show overflow, clipped controls, leaked secret text, hidden forbidden action leakage, or vague tips.
- The task claims final completion or sets `gates.user_script_passed=true`.

## REC-P0-075 Operations Logs Release Maintenance Error-State Residual Closure

Status: `planned`; active next executable task after R74.

User role: platform operator, platform administrator, system administrator, normal system member, deployer, and forbidden operator

Business outcome: operations, logs, release verification, maintenance tasks, and denied states become usable and traceable as one operator workflow. An operator can verify deployed health/assets, inspect real platform/system logs, run safe maintenance dry-runs, understand task/result states, and see forbidden actions fail with clear audit evidence.

Flow source: `docs/framework/final-system-flow-blueprint.md`, flows `O1`, `O2`, `O3`, and `O4`.

Requirement source: `REQ-4.6`, `REQ-5.17`, `REQ-5.18`, `REQ-7`, `REQ-8`, `REQ-10`, `REQ-14.1-14.37`, and `REQ-A`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, R50 operations/log closure, R56 operations robustness, R66 operations first-use evidence, R69 residual decision, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: platform admin operations page, platform/system log pages, health/release evidence display, maintenance controls/dry-run states, task/detail states, normal-member denied states, desktop/mobile containment, and stable selectors for health, trace id, audit log id, task id, release asset, and failure reason.

Backend scope: release health/readiness, audit/log query/detail, operations task creation/dry-run, backup/restore/archive/rollback/cache/rate-limit states, deployed asset verification metadata, permission positives/negatives, trace/audit readback, and cleanup.

Generator scope: generated log/task CRUD is plumbing only. R75 closes only when coded operations services, release scripts, logs, permission negatives, frontend states, and deployed browser/API evidence agree.

Data scope: release asset paths, backend jar hash, frontend asset paths, health status, redis/database/schema status, traceIds, auditLogIds, taskIds, operation result files or dry-run payloads, platform/system denial rows, browser audit output, and cleanup ids.

Permission rule: platform operators/admins can read operations and logs; system admins can read their scoped logs; normal members cannot mutate platform operations or inspect unrelated platform logs; direct forbidden APIs return denial and visible states do not imply success.

States: health up/down, deployed asset match/mismatch, log success/failure/detail, operation dry-run/submitted/succeeded/failed, backup/restore/rollback/cache/rate-limit state, permission denied, reload, desktop/mobile, and cleanup.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.6`, `REQ-5.17`, `REQ-5.18`, `REQ-7`, `REQ-8`, `REQ-10`, `REQ-14.1-14.37`, `REQ-A` |
| Target role | platform operator/admin, system administrator, normal system member, deployer, forbidden operator |
| Entry point | release scripts, health endpoint, platform admin operations/log pages, system admin log page |
| User job | verify the deployed system, inspect real logs, run/dry-run maintenance, and understand success/failure/denied outcomes |
| Data contract | trace/task/release ids must connect API operation, persisted logs, browser-visible markers, and release verification |
| Permission contract | operator/admin positives, system scoped positives, normal-member platform ops/log denial, direct forbidden API denial, and visible denied state |
| State contract | health, release asset, log row/detail, operation task, failure/denied, reload/mobile states must be asserted |
| Copy contract | visible copy must explain health, task id, trace id, denied reason, dry-run versus executed state, release mismatch, and risk boundary without generic success tips |
| Acceptance assertions | deployed browser/API/readback evidence proves health, logs, operations tasks/dry-runs, release scripts/assets, permission negatives, mobile containment, and cleanup |
| Screenshot evidence boundary | Screenshots prove visual hierarchy, overflow, clipping, and visible state only; screenshots do not prove health, logs, release assets, task state, permissions, or final acceptance without API/readback assertions, DOM selector assertions, and permission assertions |

Acceptance script:

- `scripts/recovery-r75-operations-logs-release-error-state-residual.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r75-operations-logs-release-error-state-residual-result.json`
- Summary: `docs/evidence/recovery/r75-operations-logs-release-error-state-residual-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r75-operations-logs-release-error-state-residual/operations-logs-release-error-browser-audit.json`

Explicitly not complete if:

- Release health is inferred from build output but not checked against the deployed release.
- Logs are static/sample rows or cannot be tied to trace/audit ids from real operations.
- Operations buttons produce generic success without task/readback evidence or risk boundary.
- Normal-member or wrong-role operations/log access looks successful.
- Desktop/mobile pages show overflow, clipped controls, stale demo data, hidden forbidden action leakage, or vague tips.
- The task claims final completion or sets `gates.user_script_passed=true`.

Accepted evidence:

- R75 PASS on deployed `http://127.0.0.1:18131`.
- Result: `docs/evidence/recovery/r75-operations-logs-release-error-state-residual-result.json`
- Browser audit: `docs/evidence/recovery/screenshots/r75-operations-logs-release-error-state-residual/operations-logs-release-error-browser-audit.json`
- Fresh R66 child evidence passed inside R75.
- Release verification passed with deployed frontend assets matching the release package.
- Operations task ids, trace ids, dry-run, rollbackSupported, deployment/cache states, platform/system log panels, and forbidden-operation failure logs were verified through API/readback and deployed browser DOM markers.
- Browser result count `6`, overflow `0`, blockers `0`.
- User signoff remains false.

## REC-P0-077 Final Requirement Candidate Refresh After Residual Closure

Status: `planned`; active next executable task after R75.

User role: final product reviewer, platform administrator, system administrator, normal member, external integrator, operator, and forbidden role

Business outcome: after R70-R76 residual closures, the project has an honest updated final-candidate matrix. The next session can see exactly which requirement rows and role journeys remain partial, which evidence is fresh, and what the next executable gap is, without mistaking engineering evidence for final user acceptance.

Flow source: `docs/framework/final-system-flow-blueprint.md`, all primary flows `A1-A4`, `P1-P2`, `S1-S2`, `C1-C4`, `B1-B5`, `E1-E2`, `AI1-AI3`, and `O1-O4`.

Requirement source: `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, `REQ-4.3`, `REQ-4.4`, `REQ-4.5`, `REQ-4.6`, `REQ-5.1`, `REQ-5.2`, `REQ-5.3`, `REQ-5.3.1`, `REQ-5.4`, `REQ-5.5`, `REQ-5.6`, `REQ-5.7`, `REQ-5.8`, `REQ-5.9`, `REQ-5.10`, `REQ-5.11`, `REQ-5.12`, `REQ-5.13`, `REQ-5.14`, `REQ-5.15`, `REQ-5.16`, `REQ-5.17`, `REQ-5.18`, `REQ-5.19`, `REQ-5.20`, `REQ-6.1`, `REQ-6.2`, `REQ-6.3`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, `REQ-6.7`, `REQ-6.8`, `REQ-6.9`, `REQ-6.10`, `REQ-6.11`, `REQ-7`, `REQ-8`, `REQ-9`, `REQ-10`, `REQ-14.1-14.37`, and `REQ-A`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, final flow blueprint, R67/R69 candidate evidence, and R70-R76 residual evidence.

Frontend scope: no new product UI coding unless the refresh script discovers a concrete broken journey. R77 reads deployed browser evidence, static usability output, final coverage ledger, candidate matrix, and residual evidence.

Backend scope: no new backend behavior unless the refresh discovers a concrete broken journey. R77 reads API/readback evidence, trace/task/log ids, and release verification evidence from the latest batches.

Generator scope: generated CRUD evidence remains plumbing only. R77 may not promote a row to final proven status from generated CRUD, screenshots, API 200, or build success alone.

Data scope: final requirement coverage rows, gap report rows, R70-R76 result JSON, browser audit JSON, release asset paths, trace/task/audit ids, cleanup records, and user signoff state.

Permission rule: promotion decisions must preserve role boundaries. Admin-only evidence cannot close normal-member rows; platform evidence cannot close system-scoped rows; forbidden-role evidence must remain visible.

States: fresh evidence present/missing, promoted/not promoted, still partial, blocked by user signoff, next executable gap, and no-final-completion claim.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | All final requirement rows listed above |
| Target role | final reviewer plus every product role covered by the flow blueprint |
| Entry point | release URL, framework audits, coverage ledger, gap report, R70-R76 evidence |
| User job | know honestly whether the system can be accepted, and what exact gap remains if it cannot |
| Data contract | every candidate decision must cite fresh evidence or keep the row partial |
| Permission contract | role-specific evidence cannot be promoted across roles |
| State contract | missing, partial, promoted, and next-gap states must be explicit |
| Copy contract | summaries must avoid final-completion language while user signoff is false |
| Acceptance assertions | script passes only when framework/static/coverage/gap/evidence ingestion all agree and `gates.user_script_passed=false` remains visible |
| Screenshot evidence boundary | Screenshots prove only visual rendering and layout; R77 cannot promote functional closure from screenshots alone |

Acceptance script:

- `scripts/recovery-r77-final-requirement-candidate-refresh-after-residual.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r77-final-requirement-candidate-refresh-after-residual-result.json`
- Summary: `docs/evidence/recovery/r77-final-requirement-candidate-refresh-after-residual-2026-07-02.md`

Explicitly not complete if:

- Any requirement row is promoted without fresh deployed evidence and role-specific API/readback support.
- User signoff is changed or implied.
- The script only says prior batches passed without naming the next concrete gap.
- The next execution ledger and session state still point to an already accepted task.

Accepted evidence:

- R77 PASS on deployed `http://127.0.0.1:18131`.
- Result: `docs/evidence/recovery/r77-final-requirement-candidate-refresh-after-residual-result.json`
- Framework audit PASS, static usability audit PASS with blockers `0`, warnings `0`.
- Requirement coverage remained honest: ledger rows `45`, missing `0`, notClosed `45`, promotedToProven `0`.
- R70-R76 residual evidence files were present and PASS, userSignoff remained false.
- Next concrete gap selected from gap report: FRC-1 product surfaces/human acceptance rows `REQ-5.8`, `REQ-6.1`, `REQ-6.3`, `REQ-6.8`, and `REQ-9`.

## REC-P0-080 Live User Trial Workspace Seed

Status: `accepted` as live trial engineering evidence only; user signoff remains open.

User role: product reviewer, platform admin, system admin, normal member, readonly member, requester, approver, and operator

Business outcome: a human reviewer can open the deployed release with real credentials, enter seeded systems, inspect configured runtime data, and try workflow/todo/message paths without starting from empty evidence reports.

Flow source: `docs/framework/final-system-flow-blueprint.md`, especially flows `B1-B5`, `C1-C4`, `O1`, and `J1-J11`, with auth/platform/system entry context from `A1-A4`, `P1-P2`, and `S1-S2`.

Requirement source: all still-partial rows, explicitly including `REQ-4.4`, `REQ-4.5`, `REQ-5.11`, `REQ-5.12`, `REQ-5.13`, `REQ-5.14`, `REQ-5.15`, `REQ-5.16`, `REQ-5.19`, `REQ-5.20`, `REQ-6.2`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, `REQ-6.9`, `REQ-6.11`, and `REQ-9`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, R63 configured runtime evidence, R64 workflow/todo/message evidence, and R79 continuation guide.

Frontend scope: no product UI coding unless the trial seeding exposes a blocker. R80 must verify the deployed frontend through release checks and child browser evidence from R63/R64.

Backend scope: no new business code unless the trial seeding exposes a blocker. R80 uses existing release APIs and persistence paths.

Generator scope: none. Generated CRUD remains plumbing and cannot close the final target.

Data scope: persistent trial systems, tenants, members, roles, module records, workflow instance state, todos, messages, and credentials. Cleanup is intentionally skipped for the trial workspace.

Permission rule: admin, normal, readonly, requester, and approver boundaries must remain explicit. Normal/readonly/requester/approver evidence cannot mutate `gates.user_script_passed`.

States: release verified, runtime workspace seeded, readonly denied, workflow terminal state present, todo/message evidence present, cleanup skipped intentionally, coverage still partial, and user signoff false.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | all still-partial rows, with runtime/workflow/todo/message rows named explicitly |
| Target role | product reviewer, platform admin, system admin, normal member, readonly member, requester, approver, operator |
| Entry point | deployed release URL, admin entry, runtime system/module entry, todo entry, message entry, R80 trial pack |
| User job | log in with provided accounts, inspect a configured runtime workspace, try normal/readonly boundaries, and review workflow/todo/message closure |
| Data contract | R63 and R64 child results must expose retained system ids, module ids, record ids, and credentials |
| Permission contract | readonly/user/admin/requester/approver boundaries remain proven by child scripts and signoff remains false |
| State contract | release health, framework/static/coverage audits, retained data, terminal workflow state, and cleanup-skipped state agree in JSON evidence |
| Copy contract | summary must name credentials, routes, and the engineering-only boundary clearly |
| Acceptance assertions | R80 passes only when release/framework/static checks pass, R63/R64 seed data is retained, trial credentials/routes are emitted, and `gates.user_script_passed=false` |
| Screenshot evidence boundary | child screenshots prove visible surfaces only; final closure still requires user verification plus API/readback/permission assertions |

Acceptance script:

- `scripts/recovery-r80-live-user-trial-workspace.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r80-live-user-trial-workspace-result.json`
- Summary: `docs/evidence/recovery/r80-live-user-trial-workspace-2026-07-06.md`

Accepted evidence 2026-07-06:

- R80 PASS on deployed `http://127.0.0.1:18131`.
- Release verification passed before seeding.
- Framework audit passed with errors `0` and warnings `0`.
- Static usability stayed PASS with blockers `0` and warnings `0`.
- Coverage stayed honest with notClosed `45` and `gates.user_script_passed=false`.
- R63 retained runtime trial workspace: system `1118`, module `453`, existing record `625`, normal member `r63_member_0706162707_4a00b6`, readonly member `r63_readonly_0706162707_4a00b6`, password `Aa123456!`.
- R64 retained workflow/todo/message workspace: system `1121`, module `454`, flow `144`, terminal record `626`, requester `r3_requester_0706162803307_ca6efa`, approver `r3_approver_0706162803307_ca6efa`, password `Aa123456!`.
- Trial pack is written to `docs/evidence/recovery/r80-live-user-trial-workspace-result.json`.
- R80 is not final user acceptance and does not change user signoff.

Explicitly not complete if:

- R80 only points to old evidence without creating retained trial data.
- R80 cleans up the trial workspace.
- R80 omits credentials, routes, system ids, module ids, or role boundaries.
- R80 claims user signoff or changes `gates.user_script_passed`.
- Release, framework, static usability, or coverage audits are skipped.

## REC-P0-081 Trial Login And Role Use Audit

Status: `accepted` as deployed login/role engineering evidence only; user signoff remains open.

User role: product reviewer, platform admin, normal member, readonly member, requester, and approver

Business outcome: a human reviewer can start from the deployed login page, enter the retained R80 trial accounts, reach the expected platform/runtime/workflow/todo/message surfaces, see real retained data, and see role boundaries without reading implementation reports first.

Flow source: `docs/framework/final-system-flow-blueprint.md`, especially `A1-A4`, `P1-P2`, `S1-S2`, `B1-B5`, `C1-C4`, and `J1-J11`.

Requirement source: all still-partial rows, especially `REQ-2.1`, `REQ-4.4`, `REQ-4.5`, `REQ-5.11`, `REQ-5.12`, `REQ-5.16`, `REQ-5.19`, `REQ-5.20`, `REQ-6.2`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, `REQ-6.9`, `REQ-6.11`, and `REQ-9`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, R80 trial pack, R63 configured runtime evidence, and R64 workflow/todo/message evidence.

Frontend scope: deployed login page, platform workbench, runtime module page, readonly runtime state, workflow terminal runtime page, todo workbench, message center, desktop/mobile containment, and role-shell separation. No product UI coding is planned unless the audit finds a real blocker.

Backend scope: existing login, runtime detail/search, readonly create denial, workflow terminal detail, todo search, and message search APIs only. No backend coding is planned unless the audit finds a real blocker.

Generator scope: none. Generated CRUD remains plumbing and cannot close the final target.

Data scope: R80 retained trial pack: runtime system `1118`, runtime module `453`, runtime record `625`, workflow system `1121`, workflow module `454`, workflow record `626`, plus R80 credentials.

Permission rule: normal member can read/use authorized runtime data; readonly member must not create records; requester/approver remain separate; platform admin stays in platform workspace; engineering evidence cannot set `gates.user_script_passed=true`.

States: login form submitted, platform workbench visible, runtime list/detail visible, readonly disabled/denied state, workflow terminal approved state, todo/message surfaces visible, desktop/mobile containment, coverage partial, and user signoff false.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | all still-partial rows, with runtime/workflow/todo/message/auth rows named explicitly |
| Target role | platform admin, normal member, readonly member, requester, approver, product reviewer |
| Entry point | deployed login page, R80 admin/runtime/workflow routes, todo route, message route |
| User job | use the provided account/password, land in the right role surface, inspect retained business data, and see denied states where expected |
| Data contract | R80 trial pack ids connect browser evidence, API readback, permission negative checks, and summary output |
| Permission contract | readonly create denial, user/admin shell separation, requester/approver workflow separation, and signoff false |
| State contract | login success, direct role route, runtime record visible, workflow terminal approved, todo/message loaded, mobile containment, coverage partial |
| Copy contract | summary must name role entries, evidence paths, and engineering-only boundary clearly |
| Acceptance assertions | R81 passes only when release/framework/static checks pass, every role uses the deployed login form, browser surfaces load, API readback/permission checks pass, and `gates.user_script_passed=false` |
| Screenshot evidence boundary | Screenshots prove visible route, hierarchy, overflow, shell separation, and obvious blocker text only; API/readback and permission assertions prove behavior |

Acceptance script:

- `scripts/recovery-r81-trial-login-role-use-audit.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r81-trial-login-role-use-audit-result.json`
- Summary: `docs/evidence/recovery/r81-trial-login-role-use-audit-2026-07-06.md`
- Browser audit: `docs/evidence/recovery/screenshots/r81-trial-login-role-use-audit/trial-login-role-use-browser-audit.json`

Explicitly not complete if:

- The audit bypasses the login form by only injecting localStorage.
- The audit omits readonly, requester, or approver checks.
- The audit claims user signoff or changes `gates.user_script_passed`.
- The audit uses old screenshots without a fresh deployed browser run.
- The browser surfaces pass without API/readback or permission-negative assertions.


Accepted evidence:

- 2026-07-06 R81 PASS: `docs/evidence/recovery/r81-trial-login-role-use-audit-result.json` reports `status=PASS`, `accepted=true`, `productStatus=R81_TRIAL_LOGIN_ROLE_USE_ENGINEERING_EVIDENCE_ONLY`, and `userSignoff=false`.
- Browser audit `docs/evidence/recovery/screenshots/r81-trial-login-role-use-audit/trial-login-role-use-browser-audit.json` reports `status=PASS`, result count `8`, overflow `0`, blockers `0`.
- Role/use evidence covers deployed login entry, platform admin route, normal runtime retained record `625`, readonly create denial `403`, workflow terminal record `626`, approver handled todo/message evidence, framework/static PASS, coverage notClosed `45`, and `gates.user_script_passed=false`.

## REC-P0-082 Visible Copy Encoding And Trial Usability Cleanup

Status: `accepted` as deployed visible-copy engineering evidence only; user signoff remains open.

User role: product reviewer, platform admin, normal member, readonly member, requester, and approver

Business outcome: the R81 trial login and role-use paths are not only technically reachable, but readable by a human reviewer. Visible auth, runtime, system shell, todo, message, disabled-state, loading, empty, and success/failure copy must not contain mojibake, stale placeholder text, or misleading generic wording.

Flow source: `docs/framework/final-system-flow-blueprint.md`, especially `A1-A4`, `P1-P2`, `S1-S2`, `B1-B5`, `C1-C4`, and `J8-J11`.

Requirement source: all still-partial rows with visible usability impact, especially `REQ-2.1`, `REQ-6.1`, `REQ-6.2`, `REQ-6.3`, `REQ-6.8`, and `REQ-9`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, R81 screenshots, and visible source strings in `frontend/src/features/auth`, `frontend/src/features/system-shell`, and `frontend/src/features/runtime`.

Frontend scope: visible copy in the deployed login, platform/system shell, runtime, todo, and message surfaces used by R81. Fix only user-visible strings and selectors needed for deterministic copy evidence; avoid layout or feature refactors.

Backend scope: none unless a backend error/disabled reason is the source of visible unreadable copy.

Generator scope: none. Generated CRUD cannot define or close visible human usability.

Data scope: R80/R81 trial pack and browser screenshots, plus source strings rendered on those routes.

Permission rule: copy cleanup cannot weaken role boundaries. Readonly/create denial, requester/approver separation, and admin/user shell separation must remain true.

States: login, loading, route landing, runtime list/detail, readonly disabled reason, workflow terminal state, todo empty/handled state, message state, mobile containment, coverage partial, and user signoff false.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | visible usability rows `REQ-2.1`, `REQ-6.1`, `REQ-6.2`, `REQ-6.3`, `REQ-6.8`, `REQ-9` plus all still-partial rows touched by copy |
| Target role | platform admin, normal member, readonly member, requester, approver, product reviewer |
| Entry point | deployed login page and R80/R81 trial routes |
| User job | read where they are, what they can do, why an action is disabled/denied, and what state the workflow/todo/message is in |
| Data contract | same R80/R81 trial ids connect screenshots, text extraction, source scan, and browser evidence |
| Permission contract | copy changes must preserve readonly 403, user/admin shell separation, and requester/approver workflow separation |
| State contract | source scan, deployed browser text scan, screenshots, static usability, framework audit, and coverage boundary agree |
| Copy contract | no mojibake-like fragments, no stale demo/prototype copy, and no generic success/failure wording on R81 routes |
| Acceptance assertions | R82 passes only when source/browser copy scans pass, R81 role audit still passes, static/framework checks pass, and signoff remains false |
| Screenshot evidence boundary | Screenshots prove visible readability and containment only; they do not prove functional completion, permission behavior, persistence, or requirement closure without API/readback assertions and R81 permission assertions |

Acceptance script:

- `scripts/recovery-r82-visible-copy-encoding-trial-usability.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r82-visible-copy-encoding-trial-usability-result.json`
- Summary: `docs/evidence/recovery/r82-visible-copy-encoding-trial-usability-2026-07-07.md`
- Browser audit: `docs/evidence/recovery/screenshots/r82-visible-copy-encoding-trial-usability/visible-copy-browser-audit.json`

Explicitly not complete if:

- Only source strings are changed without deployed browser text evidence.
- Copy cleanup breaks login, runtime, readonly, workflow, todo, or message role paths.
- The script ignores mojibake-like fragments visible in R81 routes.
- The task claims user signoff or changes `gates.user_script_passed`.
## REC-P0-083 User Trial Feedback Intake And Next Change Selection

Status: `accepted` as continuation/control evidence only; user signoff remains open.

User role: product reviewer, final user verifier, platform admin, system admin, normal member, requester, approver, external integrator, and operator

Business outcome: after R80-R82 made a live trial workspace reachable and readable, the next change must be selected from actual user verification or an unfinished requirement row. R83 prevents drift by recording the trial entry, collecting feedback into requirement/role-journey terms, and creating exactly one follow-up task card before any new product coding.

Flow source: `docs/framework/final-system-flow-blueprint.md`, all flows `A1-A4`, `P1-P2`, `S1-S2`, `C1-C4`, `B1-B5`, `E1-E2`, `AI1-AI3`, `O1-O4`, and `J1-J11`.

Requirement source: all still-partial rows in `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`, with the active intake row set `REQ-2.1`, `REQ-6.1`, `REQ-6.2`, `REQ-6.3`, `REQ-6.8`, and `REQ-9`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, R80 trial pack, R81 role-use audit, R82 visible-copy audit, and `docs/recovery/continuation-implementation-guide.md`.

Frontend scope: none by default. Any frontend change discovered during user trial must first get its own follow-up task card naming the affected route, role, visible state, selectors, and deployed-browser evidence.

Backend scope: none by default. Any backend change discovered during user trial must first get its own follow-up task card naming the API/readback, permission positive/negative, data state, and audit evidence.

Generator scope: none. Generated CRUD remains plumbing and cannot close a user-reported usability or business-flow gap.

Data scope: current R80/R81/R82 evidence, release URL, trial credentials/routes, requirement coverage ledger, gap report, and any user feedback written into recovery docs.

Permission rule: R83 cannot weaken role boundaries or set `gates.user_script_passed=true`. User signoff can only come from explicit user verification/signature.

States: release reachable, R81 PASS on current deployment, R82 PASS on current deployment, coverage notClosed `45`, user signoff false, and next product change not yet selected until feedback or a specific unfinished row is chosen.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | all still-partial rows in coverage ledger and gap report, active intake rows `REQ-2.1`, `REQ-6.1`, `REQ-6.2`, `REQ-6.3`, `REQ-6.8`, `REQ-9` |
| Target role | final reviewer plus the role affected by the next reported gap |
| Entry point | deployed trial URL, R80 trial credentials/routes, and feedback intake docs |
| User job | verify the current system, report a concrete blocker or approve/sign off |
| Data contract | feedback must reference route, role, observed state, expected state, and related requirement row |
| Permission contract | follow-up work must preserve readonly/admin/requester/approver/platform/system boundaries |
| State contract | R81/R82 stay PASS on current deployment unless a follow-up task intentionally changes the affected surface |
| Selection contract | before coding, create one follow-up REC-P0 task from the feedback or unfinished requirement row |
| Acceptance assertions | R83 passes only as intake/control evidence; it must not claim final product completion |
| Signoff boundary | `gates.user_script_passed` remains false unless the user explicitly verifies or signs |
| Screenshot evidence boundary | Screenshots prove visible trial readability and containment only; they do not prove functional completion without API/readback assertions and permission/readback evidence |

Acceptance script:

- `scripts/recovery-r83-user-trial-feedback-intake.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r83-user-trial-feedback-intake-result.json`
- Summary: `docs/evidence/recovery/r83-user-trial-feedback-intake-2026-07-07.md`

Explicitly not complete if:

- It starts coding before feedback or a specific unfinished requirement row is selected.
- It treats R80/R81/R82 engineering evidence as user signoff.
- It creates a broad cleanup batch instead of one role-journey follow-up task.
## REC-P0-079 Final User Verification Readiness And Continuation Contract

Status: `accepted` as final handoff engineering evidence only; user signoff remains open.

User role: product owner, final product reviewer, platform administrator, system administrator, normal member, external integrator, and operator

Business outcome: the project stops drifting after R78. The existing architecture, current target, developed evidence, user verification path, and future change process are explicit on disk so later work continues through role journeys, requirement rows, task cards, implementation, deterministic evidence, and user verification instead of scattered page/API fixes.

Flow source: `docs/framework/final-system-flow-blueprint.md`, all flows `A1-A4`, `P1-P2`, `S1-S2`, `C1-C4`, `B1-B5`, `E1-E2`, `AI1-AI3`, and `O1-O4`.

Requirement source: all still-partial requirement rows in `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, current R78 deployed evidence, and `docs/recovery/continuation-implementation-guide.md`.

Frontend scope: no product UI coding in this batch unless a verification blocker is found. R79 names the user verification path and future frontend change rules.

Backend scope: no product backend coding in this batch unless a verification blocker is found. R79 names the future backend/readback/permission evidence rules.

Generator scope: none. Generated CRUD remains plumbing and cannot close final verification.

Data scope: current session state, R78 evidence, framework audit, static usability audit, coverage audit, continuation guide, and user verification script.

Permission rule: only explicit user verification may set `gates.user_script_passed=true`; R79 must keep user signoff false.

States: R78 accepted engineering evidence, R79 active handoff, framework healthy, static usability healthy, coverage still partial, user signoff false, and future change path defined.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | all still-partial rows in the coverage ledger and gap report |
| Target role | product owner, final reviewer, platform admin, system admin, normal member, external integrator, operator |
| Entry point | `.cursor/session/state.json`, final acceptance, next execution ledger, task cards, fix batches, continuation guide, R79 script |
| User job | understand what has been built, how to try it, why signoff is still separate, and how future changes will be implemented |
| Data contract | same R78 result, framework audit, static audit, coverage audit, and R79 result connect current state to the handoff |
| Permission contract | engineering evidence cannot mutate user signoff; user verification is the only close event |
| State contract | active task, accepted evidence, coverage partial state, and signoff state must agree across state/docs/scripts |
| Copy contract | handoff copy must clearly distinguish developed evidence, remaining user verification, and future change workflow |
| Acceptance assertions | R79 passes only when required docs exist, R78 is accepted engineering evidence, framework/static audits pass, coverage remains honest, and signoff remains false |
| Screenshot evidence boundary | screenshots are not required for R79; future screenshot evidence remains visual-only and cannot prove functional closure without API/readback assertions |

Acceptance script:

- `scripts/recovery-r79-final-user-verification-readiness.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r79-final-user-verification-readiness-result.json`
- Summary: `docs/evidence/recovery/r79-final-user-verification-readiness-2026-07-06.md`
- Continuation guide: `docs/recovery/continuation-implementation-guide.md`

Accepted evidence 2026-07-06:

- R79 PASS.
- Framework audit, static usability audit, and requirement coverage audit executed from disk.
- Static usability stayed PASS with blockers `0` and warnings `0`.
- Coverage boundary remained honest with missing `0`, notClosed `45`, and `gates.user_script_passed=false`.
- R78 prerequisite evidence remained accepted as engineering evidence only.
- Continuation guide exists and defines the future change rule: role journey -> requirement row -> task card -> implementation -> script evidence -> user verification.
- R79 is not final user acceptance and does not change user signoff.

Explicitly not complete if:

- R79 claims final user acceptance or changes `gates.user_script_passed`.
- The handoff does not explain how future changes use role journey -> requirement row -> task card -> implementation -> script evidence.
- Framework, static usability, or coverage audits are skipped.
- R78 is not accepted engineering evidence.
- Coverage rows are promoted without explicit user verification or exclusion.

## REC-P0-078 FRC-1 Product Surface Human Acceptance Residual Closure

Status: `accepted` as product-surface engineering evidence only; final usable-system acceptance and user signoff remain open.

User role: new administrator, system administrator, normal member, final product reviewer, and forbidden/readonly role

Business outcome: product surfaces stop feeling like a prototype or a pile of panels. A real user can understand the first screen, page/home/page-designer surfaces, runtime surface handoff, and advanced capability boundaries without stale demo text, confusing tips, mixed admin/runtime panels, or unprovable screenshot-only claims.

Flow source: `docs/framework/final-system-flow-blueprint.md`, flows `A1-A4`, `P1-P2`, `S1-S2`, `C1-C3`, `B1-B2`, `AI1-AI3`, and `O1-O4`.

Requirement source: `REQ-5.8`, `REQ-6.1`, `REQ-6.3`, `REQ-6.8`, and `REQ-9`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, R42/R46/R52/R68 page evidence, R77 gap refresh, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: platform home, system admin page designer, home/dashboard configuration, runtime page rendering, command/assistant advanced entry points where they affect product-surface clarity, mobile/desktop containment, visible copy, disabled reasons, and stable selectors for page/home/component/runtime state.

Backend scope: page/home definitions, publish-check/readback, runtime schema/page readback, permission negatives, trace/audit ids, and any required data state for page/home/runtime proof.

Generator scope: generated CRUD/page shells are plumbing only. R78 closes only when coded frontend surfaces, backend readback, permission state, copy, and deployed browser evidence agree.

Data scope: page definition ids, component ids, home config ids, publish versions, traceIds, auditLogIds, runtime schema/page markers, hidden-field/component pruning, browser overflow/blocker output, and cleanup ids.

Permission rule: system admin can configure/publish page and home surfaces; normal member can only see authorized runtime surfaces; forbidden/readonly users cannot create or mutate page/home/runtime state.

States: empty, configured, draft, publish-check pass/fail, published, hidden/readonly, forbidden, reload, desktop/mobile, and advanced-feature boundary copy.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-5.8`, `REQ-6.1`, `REQ-6.3`, `REQ-6.8`, `REQ-9` |
| Target role | new administrator, system administrator, normal member, final product reviewer, forbidden/readonly role |
| Entry point | login, platform/home, system admin page/home designer, normal runtime page |
| User job | understand and use product surfaces without panel stacking, stale demo text, ambiguous tips, or fake completion |
| Data contract | page/home/component/runtime evidence must share ids, versions, trace ids, and readback |
| Permission contract | admin positive, normal-member authorized view, forbidden/readonly mutation denial |
| State contract | empty/configured/draft/publish/published/hidden/forbidden/reload/mobile states must be asserted |
| Copy contract | visible copy must explain page/home/designer/runtime purpose and advanced-feature boundary without generic success |
| Acceptance assertions | deployed browser/API/readback evidence proves surface clarity, no overflow, no mixed shell, no hidden leakage, permission negatives, and cleanup |
| Screenshot evidence boundary | Screenshots prove only visual hierarchy and visible copy; functional closure requires API/readback and permission assertions |

Acceptance script:

- `scripts/recovery-r78-frc1-product-surface-human-acceptance-residual.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r78-frc1-product-surface-human-acceptance-residual-result.json`
- Summary: `docs/evidence/recovery/r78-frc1-product-surface-human-acceptance-residual-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r78-frc1-product-surface-human-acceptance-residual/product-surface-human-acceptance-browser-audit.json`

Accepted evidence 2026-07-06:

- R78 PASS on deployed `http://127.0.0.1:18131`.
- Release verification, static usability, and framework audits passed.
- Fresh R68 page/page-designer evidence passed with page designer browser results `8`, runtime browser results `10`, overflow `0`, blockers `0`, hidden field/component leakage `false`, and forbidden page/home writes `403`.
- Fresh R75 operations/logs/release evidence passed with browser results `6`, overflow `0`, blockers `0`, platform/system audit readback `9/1`, and forbidden operation denials `403/403`.
- Fresh R77 coverage boundary remained honest with notClosed `45`, promotedToProven `0`, and userSignoff `false`.
- R78 is accepted only as engineering evidence. It does not set `gates.user_script_passed=true` and does not close final product acceptance.

Explicitly not complete if:

- The path relies on screenshots without backend/readback/permission proof.
- Page/home/designer/runtime surfaces still show stacked unrelated panels or stale demo/prototype copy.
- Normal-member or readonly roles can mutate configuration.
- Hidden fields/components leak in runtime DOM.
- Mobile/desktop overflow or clipped controls remain.
- The task claims final user acceptance or changes `gates.user_script_passed`.

## REC-P0-076 Auth Shell Entry Role Flow Residual

Status: `accepted` as engineering evidence only; final usable-system acceptance and user signoff remain open. R75 remains the next executable residual batch.

User role: unauthenticated visitor, platform root/admin/member, system administrator, system normal member, and no-member/direct-link visitor

Business outcome: the product entry and shell routing stop creating the confusing first impression the user reported. A browser without a token sees only authentication. Platform roles land on the platform workbench, not an implicit system page. Registration enters system admin setup. Password recovery can return to login. Platform admin and system admin render as standalone admin shells instead of being wrapped by workbench/runtime navigation.

Flow source: `docs/framework/final-system-flow-blueprint.md`, flows `A1`, `A2`, `A3`, `A4`, `P1`, `S1`, and `C1`.

Requirement source: `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, `REQ-6.2`, and `REQ-9`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, R61 auth entry guard evidence, R62 platform/system shell landing evidence, and the 2026-07-02 user correction requiring the full login/register/recovery/role-routing flow before further scattered coding.

Frontend scope: `frontend/src/app/app.ts`, `frontend/src/app/routes.ts`, `frontend/src/app/state.ts`, `frontend/src/features/auth/authPages.ts`, `frontend/src/features/platform/platformShell.ts`, and the system-admin route branch in `frontend/src/features/system-shell/systemShell.ts`.

Backend scope: `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/auth/AuthService.java` default landing route.

Generator scope: generated CRUD and existing shell routes are plumbing only. R76 closes only when coded entry guards, role landing, delayed system switch, standalone admin shells, visible copy, and deterministic checks agree.

Data scope: access token state, `/account/me`, system-switch options, `SystemSwitchContext`, defaultLanding route, platform/system route markers, auth form markers, and shell standalone markers.

Permission rule: unauthenticated users cannot render platform/system shells; platform admin/root can enter platform admin; system admin/super admin can enter system admin; platform and system admin shells must not expose mixed workbench/runtime navigation as their primary shell.

States: no-token login, invalid-token session expiry, platform default landing, registration-to-system-admin, password recovery return-to-login, delayed system switch, platform admin standalone, system admin standalone, stale `/platform/dashboard` normalization, source no-mojibake check, typecheck, and build.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, `REQ-6.2`, `REQ-9` |
| Target role | unauthenticated visitor, platform roles, system roles, no-member/direct-link visitor |
| Entry point | `/`, `/login`, `/register-with-system`, `/forgot-password`, `/platform`, `/platform/admin`, `/systems/{id}/dashboard`, `/systems/{id}/admin` |
| User job | enter the correct first shell and avoid mixed platform/system/admin navigation |
| Data contract | token state, account profile, switch options, defaultLanding, and switch context must be consistent |
| Permission contract | no-token guard, platform admin guard, system admin guard, no auto system switch before explicit entry |
| State contract | login, register, recovery, session expiry, stale platform dashboard, standalone admin shells, build states |
| Copy contract | visible auth/platform entry copy must be readable Chinese and not mojibake |
| Acceptance assertions | source contract, no-mojibake source scan, typecheck, and production build |
| Screenshot evidence boundary | Screenshots can prove visible shell separation later, but R76 evidence is engineering source/build evidence and does not prove final acceptance without deployed browser role journey |

Acceptance script:

- `scripts/recovery-r76-auth-shell-entry-role-flow-residual.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r76-auth-shell-entry-role-flow-residual-result.json`
- Summary: `docs/evidence/recovery/r76-auth-shell-entry-role-flow-residual-2026-07-02.md`

Accepted evidence:

- R76 PASS on 2026-07-02.
- Backend platform default landing no longer returns `/platform/dashboard`; it returns `/platform`.
- Frontend platform roles land on `/platform`.
- `initializeShellState()` no longer auto-switches the first available system; system context is created by explicit system entry or direct system route.
- Registration still lands in `/systems/{systemId}/admin`.
- Password recovery has an explicit return-to-login action.
- `/platform/admin` renders `platformAdminStandalone`; `/systems/{id}/admin` renders `systemAdminStandalone`.
- Auth/platform entry source scan reports no obvious mojibake.
- `npm typecheck` and `npm build` passed; frontend asset in this build was `/assets/index-QDWgcnIm.js`.
- `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.

Explicitly not complete if:

- This task is used to claim final product acceptance.
- R75 operations/logs/release residual is skipped.
- Browser role journeys are not later verified on the deployed release.
- Admin shell internals still contain unrelated mojibake or generic copy outside the entry/shell scope.

## REC-P0-066 O1/O2/O3 Operations Logs Release Maintenance First-Use Closure

Status: `accepted` as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

User role: platform operator, platform administrator, system administrator, normal system member, and deployer

Business outcome: an operator can verify release health, inspect platform/system logs, run or dry-run maintenance controls, confirm release package/scripts/assets, and see clear success/failure/denied states without relying on hidden console knowledge or stale static rows.

Flow source: `docs/framework/final-system-flow-blueprint.md`, especially `O1 Release And Health`, `O2 Logs And Audit`, `O3 Operations Maintenance`, `O4 Backup Restore Rollback`, and related operator paths.

Requirement source: `REQ-4.6`, `REQ-5.17`, `REQ-5.18`, `REQ-7`, `REQ-8`, `REQ-10`, `REQ-14.1-14.37`, and `REQ-A` from `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, operations/log/release surfaces, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: platform admin operations page, system log page, platform log page, health/release evidence display, maintenance controls/dry-runs, normal-member denied states, desktop/mobile containment, and clear copy for task ids, trace ids, rollback/cache/rate-limit/backup states.

Backend scope: only code behavior needed for health/readiness, audit/log query/detail, operations task creation or dry-run, release verification metadata, permission positives/negatives, trace/audit readback, and cleanup.

Generator scope: generated log CRUD is plumbing only. The task closes only when operator-facing logs and operations controls are proven through real events, role permissions, release assets, and deployed browser/API evidence.

Data scope: health response, platform/system audit rows, operations tasks, release asset paths/hashes, backend/frontend process state, Redis/DB/schema state, normal-member denial rows, browser screenshots, and cleanup data.

Permission rule: platform/operator roles can access operations and logs; normal system members cannot mutate platform operations or inspect unrelated platform logs; direct forbidden APIs must return backend denial and visible frontend state must not imply success.

States: health up/down/readiness, release asset match/mismatch, log success/failure/detail, operations dry-run/submitted/succeeded/failed, backup/restore/rollback/cache/rate-limit state, permission denied, desktop/mobile containment, and cleanup.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.6`, `REQ-5.17`, `REQ-5.18`, `REQ-7`, `REQ-8`, `REQ-10`, `REQ-14.1-14.37`, `REQ-A` |
| Target role | platform operator, platform administrator, system administrator, normal system member, deployer |
| Entry point | deployed login, platform admin operations/log pages, system admin log page, release scripts, health endpoint |
| User job | verify the system is running, inspect real logs, run/dry-run maintenance controls, and confirm release assets/scripts are usable |
| Data contract | same `traceId/auditLogId/taskId/releaseAsset/backendPid/frontendPid/healthStatus` connects operation, logs, browser evidence, and release verification |
| Permission contract | operator/admin positives, normal-member platform ops/log denial, direct forbidden API denial, and visible denied state |
| State contract | health, log rows, operation task states, release verification, failures/denials, reload/mobile, and cleanup must be asserted |
| Copy contract | operational copy must explain task id, trace id, denied reason, risk boundary, dry-run versus executed state, and release mismatch without generic success tips |
| Acceptance assertions | deployed browser and API prove health, logs, operations tasks/dry-runs, release scripts/assets, permission negatives, and cleanup |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, overflow, clipping, and visible state only; screenshots do not prove health, logs, release assets, or permissions without API/readback assertions |

Planned evidence:

- Script: `scripts/recovery-r66-operations-logs-release-maintenance-first-use.ps1`
- Result: `docs/evidence/recovery/r66-operations-logs-release-maintenance-first-use-result.json`
- Summary: `docs/evidence/recovery/r66-operations-logs-release-maintenance-first-use-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r66-operations-logs-release-maintenance-first-use/operations-logs-release-browser-audit.json`

Accepted evidence:

- R66 PASS on deployed `http://127.0.0.1:18131`.
- Release verification passed; packaged server commands include `start`, `stop`, `restart`, `status`, and `health`.
- Static usability audit passed with blockers `0` and warnings `0`.
- Operations tasks/readback: backup, restore, archive, and rollback task `TASK-20260702121503`; deployment count `1`; cache policies `4`, updated `1`.
- Logs/readback: platform audit readback `9`, system audit readback `1`, platform trace `trc_r50_platform_health_0702121434929_8760fc`, system trace `trc_r50_system_health_0702121434929_8760fc`.
- Permission negatives: platform denied `403/FAILURE`, system denied `403/FAILURE`.
- Browser evidence: result count `6`, overflow `0`, blockers `0`.
- Cleanup succeeded for systems `949` and `950`.
- `accepted=true` in the result means engineering evidence only. `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.

Explicitly not complete if:

- The task only reruns old R50/R56 evidence without a fresh deployed operator first-use journey.
- Logs are static/sample rows or cannot be tied to trace/audit ids from real operations.
- Operations buttons produce generic success without task/readback evidence or risk boundary.
- Release verification is inferred from build output but not checked against deployed assets.
- Browser screenshots pass but health/logs/ops/release/permission assertions are absent.
- User signoff is implied from script evidence; `gates.user_script_passed` must remain false.

## REC-P0-065 E1/AI2 OpenAPI Assistant External-Service First-Use Closure

Status: `accepted` as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

User role: system administrator, external integrator, normal system member, and operator/auditor

Business outcome: a system administrator configures scoped external access and assistant policy, an external integrator calls the allowed API with a safe secret, a normal member uses the system assistant through preview/confirm/reject boundaries, and an operator can read the resulting logs without unsafe writes, leaked secrets, or generic/ambiguous states.

Flow source: `docs/framework/final-system-flow-blueprint.md`, especially `E1 OpenAPI Caller`, `E2 External Callback/Webhook`, `AI1 Assistant Entry`, `AI2 System Agent`, `AI3 Human Confirmation`, and operational readback from `O2 Logs`.

Requirement source: `REQ-5.15`, `REQ-5.19`, `REQ-9`, and `REQ-14.1-14.37` from `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, OpenAPI/app-secret configuration, assistant drawer/policy, audit/log pages, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: system admin OpenAPI/app-secret/policy surfaces, assistant entry and confirmation UI, integration/log readback surfaces, normal-member denied states, desktop/mobile containment, and clear copy for secret refs, confirmations, failures, and unsafe write boundaries.

Backend scope: only code behavior needed for scoped OpenAPI app/secret creation, secret rotation/readback by reference, allowed external create/search/detail calls, wrong-secret/read-only denials, assistant preview/confirm/reject/draft behavior, audit/log readback, permission negatives, and cleanup.

Generator scope: generated integration CRUD and log tables are plumbing only. The task closes only when external API and AI assistant behavior is proven through scoped credentials, role context, logs, confirmations, and deployed browser evidence.

Data scope: one system, module, scoped OpenAPI app, SecretRef, rotation job, external API-created record, assistant session/message, confirmation records, audit/log rows, normal-member and admin role contexts, browser screenshots, and cleanup data.

Permission rule: external callers can only use scoped APIs granted to their app; wrong secrets and read-only apps are denied; normal members cannot create OpenAPI apps or unsafe assistant policies; assistant writes require explicit human confirmation and preserve audit traces.

States: app draft/active, secret reference/rotation, allowed external create/search/detail, wrong-secret denied, read-only denied, assistant preview waiting for confirmation, write confirmed, write rejected, work draft confirmed, platform/scope denied, logs present, desktop/mobile containment, and cleanup.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-5.15`, `REQ-5.19`, `REQ-9`, `REQ-14.1-14.37` |
| Target role | system administrator, external integrator, normal system member, operator/auditor |
| Entry point | deployed login, system admin OpenAPI/assistant policy surfaces, OpenAPI endpoint, assistant drawer/session, audit/log surfaces |
| User job | expose a scoped external service, call it safely, use AI with confirmation boundaries, and read logs/traces that prove what happened |
| Data contract | same `systemId/moduleId/openApiAppId/secretRefId/recordId/sessionId/confirmationId/auditLogId/traceId` connects configuration, external call, assistant action, and logs |
| Permission contract | admin positives, external scoped positives, wrong-secret and read-only denials, normal-member OpenAPI/admin denial, assistant unsafe-write confirmation boundary |
| State contract | active/rotated secret, success/failure logs, preview/confirm/reject, denied states, reload/mobile, and cleanup must be asserted |
| Copy contract | secret, external-call, assistant, confirmation, denial, and log copy must be specific and must not imply unsafe automatic writes |
| Acceptance assertions | deployed browser and API prove OpenAPI configuration/readback, scoped external calls, failure logs, assistant confirmation behavior, permission negatives, and cleanup |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, overflow, clipping, and visible state only; screenshots do not prove external API, AI safety, logs, or permissions without API/readback assertions |

Planned evidence:

- Script: `scripts/recovery-r65-openapi-assistant-external-service-first-use.ps1`
- Result: `docs/evidence/recovery/r65-openapi-assistant-external-service-first-use-result.json`
- Summary: `docs/evidence/recovery/r65-openapi-assistant-external-service-first-use-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r65-openapi-assistant-external-service-first-use/openapi-assistant-browser-audit.json`

Accepted evidence:

- R65 PASS on deployed `http://127.0.0.1:18131`.
- Fresh external-service handoff: `systemId=947`, `moduleId=379`, `openApiAppId=56`, `openApiRecordId=475`.
- Secret/log boundary: `openApiSecretRefId=sec_openapi_r49_app_0702120745045_74d61e_d3d943ae`, `openApiRotateJobId=srj_openapi_56_3332295e`, success logs `5`, failed logs `1`.
- Permission negatives: read-only app `403`, wrong secret `401`, normal-member OpenAPI app create `403`, normal-member agent policy create `403`, platform agent system-write denied as `REJECTED_BY_SCOPE`.
- Assistant confirmations: proposed `SYSTEM_AGENT_WRITE_CONFIRM` and `WORK_AGENT_DRAFT_CONFIRM`; write preview `WAITING_HUMAN_CONFIRM`, write confirmed `CONFIRMED`, write rejected `REJECTED`, draft preview `WAITING_HUMAN_CONFIRM`, draft confirmed `CONFIRMED`.
- Audit/browser evidence: agent audit logs `12`, confirmation audit logs `6`, browser result count `6`, overflow `0`, blockers `0`.
- Cleanup succeeded for systems `947` and `948`.
- `accepted=true` in the result means engineering evidence only. `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.

Explicitly not complete if:

- The task only reruns old R49/R55 evidence without a fresh deployed first-use role journey.
- OpenAPI works only through privileged internal headers instead of scoped external credentials.
- Secrets are exposed directly instead of referenced/rotated safely.
- Assistant writes happen without human confirmation or audit trace.
- Browser screenshots pass but external calls, assistant confirmations, logs, permission negatives, and cleanup are not asserted.
- User signoff is implied from script evidence; `gates.user_script_passed` must remain false.

## REC-P0-064 C4/B4/B5 Workflow Todo Message First-Use And Closure

Status: `accepted` as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

User role: system administrator, workflow requester, assigned approver, and normal system member

Business outcome: a system administrator configures and publishes a workflow for the same kind of runtime business record, a requester submits that record into approval, the assigned approver receives todo/message work, handles the approval, and all users see terminal business/todo/message/readback states without duplicate actions or role leakage.

Flow source: `docs/framework/final-system-flow-blueprint.md`, especially `C4 Workflow Configuration`, `B4 Todo Center`, `B5 Message Center`, and the runtime handoff from `B1/B2`.

Requirement source: `REQ-4.5`, `REQ-5.12`, `REQ-5.16`, `REQ-5.19`, `REQ-6.9`, and `REQ-9` from `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, workflow canvas/configuration, runtime record approval, todo center, message center, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: system admin workflow configuration/publish surface, runtime record submit/approval state, system todo center pending/handled state, system message list/detail/read state, requester/approver role separation, desktop/mobile containment, and clear disabled/terminal copy.

Backend scope: only code behavior needed for workflow publish-check, runtime instance creation from a real record, assigned todo/message delivery, approver action, duplicate/idempotent action state, terminal record/sidebar readback, permission positives/negatives, audit/trace readback, and cleanup.

Generator scope: generated flow/todo/message/log CRUD is plumbing only. The task closes only when the configured workflow drives a normal business record through requester -> assigned approver -> terminal state with browser and API evidence.

Data scope: one system, module, role/member/account bindings, workflow definition/canvas/version, runtime record, approval runtime instance, todo, message, audit/trace rows, browser screenshots, and cleanup data.

Permission rule: requester can submit but cannot approve their own approval step; only the assigned approver can handle the todo; normal members cannot mutate workflow/admin configuration; todo/message targets must stay inside the current system member context.

States: workflow draft/publish-check/published, runtime record submitted/pending/approved, requester denied action, approver pending todo, approver unread/read message, handled todo, duplicate action idempotency, terminal sidebar, empty/pending/handled filters, denied/admin forbidden, desktop/mobile containment, and cleanup.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.5`, `REQ-5.12`, `REQ-5.16`, `REQ-5.19`, `REQ-6.9`, `REQ-9` |
| Target role | system administrator, workflow requester, assigned approver, normal system member |
| Entry point | deployed login, system admin workflow config, runtime record route, `/systems/{systemId}/todos`, `/systems/{systemId}/messages` |
| User job | submit a configured business record into approval, handle the assigned todo, and see terminal record/todo/message states |
| Data contract | same `systemId/moduleId/flowId/recordId/requesterMemberId/approverMemberId/todoId/messageId` connects configuration, runtime, todo, message, and browser evidence |
| Permission contract | admin positives, requester submit positive, requester approve negative, assigned approver positive, normal workflow-admin denial, duplicate action idempotency |
| State contract | publish-check, pending todo/message, handled todo, read message, terminal approval, duplicate action, reload/mobile, forbidden states must be asserted |
| Copy contract | todo/message/action/terminal/disabled copy must be role-specific and must not use generic placeholder or misleading success tips |
| Acceptance assertions | deployed browser and API prove workflow publish/readback, runtime submit, todo/message delivery, approver action, terminal readback, permission negatives, and cleanup |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, overflow, clipping, and visible state only; screenshots do not prove workflow completion; API/readback assertions and permission assertions prove behavior |

Planned evidence:

- Script: `scripts/recovery-r64-workflow-todo-message-first-use.ps1`
- Result: `docs/evidence/recovery/r64-workflow-todo-message-first-use-result.json`
- Summary: `docs/evidence/recovery/r64-workflow-todo-message-first-use-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r64-workflow-todo-message-first-use/workflow-todo-message-browser-audit.json`

Accepted evidence:

- R64 PASS on deployed `http://127.0.0.1:18131` with frontend asset `/assets/index-D3FrPh_v.js`.
- Flow/runtime handoff: `systemId=944`, `moduleId=378`, `recordId=474`, `flowId=136`, `pendingTaskId=81`, `pendingTodoId=81`, `pendingMessageId=83`.
- Flow checks: publish-check passed with impact refs `2`; simulation passed with `runtimeInstanceCreated=false` and step count `2`.
- Browser first-use: requester submitted from runtime edit form; approver opened message/todo surfaces and approved through deployed browser; requester read terminal runtime state on desktop/mobile.
- Permission negatives: requester approve `403`, requester flow/admin read `403`.
- Terminal readback: detail `APPROVED`, approval sidebar `APPROVED`, pending todos after approval `0`, handled todos `1`.
- Duplicate terminal action returned `TASK_STATE_CONFLICT` with HTTP `400`, proving already-handled tasks cannot be advanced again with a new key.
- Browser evidence: result count `8`, overflow `0`, blockers `0`.
- Cleanup succeeded for systems `944`, `945`, `946`.
- `accepted=true` in the result means engineering evidence only. `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.

Explicitly not complete if:

- The script only reuses old R48 data instead of configuring workflow and runtime record in the same flow.
- Todo/message is proven only by API but the deployed browser cannot show pending, handled, and terminal user states.
- The requester can approve their own assigned step, or the assigned approver cannot handle it.
- Message/todo links leak platform context or bypass system member context.
- Screenshots are treated as behavior proof without API/readback assertions.
- User signoff is implied from script evidence; `gates.user_script_passed` must remain false.

## REC-P0-063 B1/B2 Configured Runtime First-Use And Daily Business Closure

Status: `accepted` as deployed engineering evidence only; final usable-system acceptance and user signoff remain open.

User role: system administrator and system normal member

Business outcome: a system administrator publishes a no-code module configuration, then a normal member uses that exact published configuration in the system business shell to create, list, open, edit, and read back real business data without seeing admin-only configuration controls or hidden fields.

Flow source: `docs/framework/final-system-flow-blueprint.md`, especially `B1 System Dashboard`, `B2 Runtime Module Navigation`, and the handoff from `C1/C2/C3`.

Requirement source: `REQ-4.4`, `REQ-5.11`, `REQ-5.13`, `REQ-5.14`, `REQ-5.16`, `REQ-5.20`, `REQ-6.2`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, and `REQ-6.11` from `docs/framework/final-requirement-coverage-ledger.md` and `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, runtime business shell and module navigation areas, and current deployed release `http://127.0.0.1:18131`.

Frontend scope: system dashboard runtime entry, top module group navigation, left module navigation, runtime list, create/edit form, detail panel, attachment surface, import/export entry behavior, permission-disabled actions, desktop/mobile containment, and copy/state clarity.

Backend scope: only code behavior needed for published schema/runtime data consistency, normal-member create/edit/list/detail readback, attachment binding/readback if used, import/export task readback if used, and permission positives/negatives.

Generator scope: generated CRUD and generic runtime schema are plumbing only. The task closes only when a normal member can complete the business use flow from the deployed frontend with persisted data, correct permissions, and visible readback.

Data scope: one created system, module group, module, fields, dictionary/options, role, member/account binding, permission snapshot, published version, runtime record, attachment/import/export artifacts when exercised, audit/trace records, and cleanup data.

Permission rule: the admin can configure and publish; the normal member can perform only granted runtime actions and sees only authorized modules/fields/actions. Hidden fields must not leak through list, form, detail, export, or browser text. Forbidden admin/config/direct runtime writes return backend denial and clear frontend state.

States: first runtime dashboard after publish, no-data list, list with data, create validation, successful create, detail readback, edit readback, disabled/hidden/readonly action, direct API forbidden, reload/relogin readback, desktop/mobile containment, and cleanup.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.4`, `REQ-5.11`, `REQ-5.13`, `REQ-5.14`, `REQ-5.16`, `REQ-5.20`, `REQ-6.2`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, `REQ-6.11` |
| Target role | system administrator and system normal member |
| Entry point | deployed login, system switch, system admin publish path, `/systems/{systemId}/dashboard`, and runtime module route |
| User job | normal member uses the just-published configured module as a daily business page |
| Data contract | same `systemId/moduleId/publishedVersion/roleId/memberId` connects admin configuration, runtime schema, runtime record, and browser evidence |
| Permission contract | admin positives, normal-member allowed runtime action, normal-member hidden/readonly state, normal-member admin/config denials, and direct forbidden API denial |
| State contract | empty/list/detail/form/reload/mobile/forbidden states must be asserted, not inferred from screenshots |
| Copy contract | runtime labels, disabled reasons, validation, success/failure, empty/error copy must be role-specific and non-generic |
| Acceptance assertions | deployed browser starts from real login, normal member sees module and schema, creates/reads/edits a record, hidden fields do not leak, permission negatives return 403, and cleanup succeeds |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, overflow, clipping, and visible state only; screenshots do not prove functional completion; API/readback assertions and permission assertions prove behavior |

Planned evidence:

- Script: `scripts/recovery-r63-configured-runtime-first-use.ps1`
- Result: `docs/evidence/recovery/r63-configured-runtime-first-use-result.json`
- Summary: `docs/evidence/recovery/r63-configured-runtime-first-use-2026-07-02.md`
- Browser audit: `docs/evidence/recovery/screenshots/r63-configured-runtime-first-use/configured-runtime-browser-audit.json`

Accepted evidence:

- R63 PASS on deployed `http://127.0.0.1:18131` with frontend asset `/assets/index-DXYcfcQU.js`.
- Handoff: `systemId=929`, `moduleId=373`, `publishedVersion=MODULE_v1782963082835`, `normalRoleId=1353`, `normalMemberId=1249`, `accountMemberBindingId=1249`.
- Deployed browser normal-member flow created `R63 Browser Created 0702113109_79a030`, edited it to `R63 Browser Updated 0702113109_79a030`, and read back record `469` on desktop and mobile.
- API readback: `runtimeSearchTotal=1`, `runtimeSchemaColumnCount=3`, `hiddenFieldLeakedInList=false`, `hiddenFieldLeakedInDetail=false`.
- Permission negatives: readonly create `403`, normal-member admin fields `403`.
- Browser evidence: result count `7`, overflow `0`, blockers `0`.
- Cleanup succeeded for systems `929`, `930`, `931`.
- `accepted=true` in the result means engineering evidence only. `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.

Explicitly not complete if:

- The script only reuses old R47/R54 data instead of using a module configured and published in the same flow.
- The normal member runtime page does not prove the same published version/configuration from the admin step.
- The browser shows admin configuration controls, hidden fields, stale demo/sample text, generic success copy, or stacked import/export/detail panels.
- Create/list/detail/edit pass by API but the deployed browser cannot complete the user flow.
- Permission positives and negatives are not asserted from the same role context.
- User signoff is implied from script evidence; `gates.user_script_passed` must remain false.

## REC-P0-060 Flow Blueprint And Rebuild Contract Lock

Status: `accepted` as framework evidence only; final usable-system acceptance remains `PARTIAL`.

User role: all roles before and after login

Business outcome: the project has one explicit flow blueprint for how a human enters, registers, recovers password, logs in, reaches the correct shell, configures a system, uses runtime data, handles workflow/todos/messages, calls OpenAPI, uses AI, and operates the release.

Requirement source: `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, `REQ-4.3`, `REQ-4.4`, `REQ-4.5`, `REQ-4.6`, `REQ-5.1`, `REQ-5.2`, `REQ-5.3`, `REQ-5.3.1`, `REQ-5.4`, `REQ-5.5`, `REQ-5.6`, `REQ-5.9`, `REQ-5.10`, `REQ-5.11`, `REQ-5.12`, `REQ-5.15`, `REQ-5.16`, `REQ-5.17`, `REQ-5.19`, `REQ-6.2`, `REQ-6.4`, `REQ-6.7`, `REQ-6.10`, `REQ-7`, `REQ-8`, `REQ-9`, `REQ-10`, `REQ-14.1-14.37`, and `REQ-A`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, `docs/user_requirement.md`, and current source routes in `frontend/src/app/app.ts`, `frontend/src/app/state.ts`, `frontend/src/features/platform/platformShell.ts`, and `frontend/src/features/system-shell/systemShell.ts`.

Frontend scope: routing and shell flow contract only. No source UI coding is part of this task unless the framework audit requires a machine-checkable reference.

Backend scope: context and data contracts only: account profile, system switch, tenant switch, no-member request, system admin configuration, runtime schema/data, workflow/todo/message, OpenAPI, AI, and operations readback.

Generator scope: none. Generated CRUD cannot define or close the flow blueprint.

Data scope: account/session, platform role, system member, tenant, permission snapshot, modules, fields, dictionaries, permissions, records, workflows, todos, messages, external app credentials, AI policy, logs, tasks, release health.

Permission rule: every flow must name the allowed and forbidden role states before implementation.

States: no token, invalid token, loading, login error, register success, password reset success, no system, no member mapping, disabled system/tenant, permission denied, empty system, first-use setup, draft/published, runtime empty/list/detail/form, workflow pending/terminal, message read/archive, task success/failure, health up/down.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | broad final-system rows listed above |
| Target role | unauthenticated user, platform member, platform admin/root, system admin/super admin, system normal member, external integrator, operator |
| Entry point | browser first load, `/login`, `/register-with-system`, `/forgot-password`, `/platform`, `/platform/admin`, `/systems/{systemId}/...`, `/no-member`, OpenAPI endpoint, release scripts |
| User job | know where they are, reach the correct shell, complete one coherent product task, and see a persisted result or explicit denied/failure state |
| Data contract | each flow names the backend readback object that proves it is real |
| Permission contract | each flow names both allowed and forbidden role/context states |
| State contract | auth, context, empty, disabled, denied, validation, async, success, failure, and reload/restart states are named before coding |
| Copy contract | labels, tips, disabled reasons, validation, empty, error, success/failure messages must be specific to the role and flow |
| Acceptance assertions | framework audit verifies the blueprint exists and active tasks reference it; later coding tasks derive deterministic browser/API/readback evidence from flow ids |
| Screenshot evidence boundary | screenshots may verify only visible shell, hierarchy, density, overflow, and copy after a flow contract exists |

Evidence paths:

- Flow blueprint: `docs/framework/final-system-flow-blueprint.md`
- Framework file: `.cursor/architecture/final-goal-framework.md`
- Framework audit result: `docs/evidence/final-goal-framework-audit-result.json`

Explicitly not complete if:

- The project continues coding from a route/page/API without naming a flow id.
- The flow blueprint omits auth-state routing, registration, password recovery, role shell separation, system switch context, or normal runtime reflection.
- A generated CRUD/API surface is treated as the flow.
- The next coding task cannot point to a flow id, target role, entry, user job, data/readback, permission, states/copy, and evidence.

Result:

- Added active flow blueprint: `docs/framework/final-system-flow-blueprint.md`.
- Upgraded framework to V8: `.cursor/architecture/final-goal-framework.md`.
- Added framework record: `docs/framework/framework-v8-flow-blueprint-rebuild-contract.md`.
- Updated final acceptance, current product audit, next execution ledger, fix batches, task cards, session state, and framework audit.
- `scripts/final-goal-framework-audit.ps1` PASS with errors `0`, warnings `0`.

Still partial:

- R60 is framework evidence only. It does not implement the next coding slice and does not set `gates.user_script_passed=true`.

## REC-P0-061 Auth Entry Guard And Registration Landing Closure

Status: `accepted` as deployed auth/entry engineering evidence only; final usable-system acceptance remains `PARTIAL`.

Flow source: `docs/framework/final-system-flow-blueprint.md`

Flow ids: `A1`, `A2`, `A3`, `A4`, `P1`, `S1`, `C1`

User role: unauthenticated user, registered system creator, logged-in platform/system user

Business outcome: opening the product without a valid session shows only the auth entry; registration creates a system and lands the creator in the system admin first-use path; authenticated users can enter platform/system shells only after session bootstrap.

Requirement source: `REQ-2.1`, `REQ-5.1`, `REQ-5.2`, `REQ-6.2`, `REQ-8`, `REQ-10` from `docs/framework/final-requirement-coverage-ledger.md` and flow ids `A1-A4/P1/S1/C1`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, and `docs/framework/final-system-flow-blueprint.md`.

Frontend scope: `frontend/src/app/app.ts`, `frontend/src/features/auth/authPages.ts`, and routing behavior for `/login`, `/register-with-system`, `/forgot-password`, `/platform`, `/platform/admin`, `/systems/{systemId}/...`, and `/no-member`.

Backend scope: existing login/register/password-reset/account-me/system-switch APIs only. No new backend API is planned for this slice.

Generator scope: none.

Data scope: access token, refresh token, account profile, system switch context, registration-created system, and system creator super-admin context.

Permission rule: no token means no platform/system/no-member rendering; token bootstrap failure clears token; platform/system shells render only after the session is considered authenticated.

States: no token, invalid token, loading bootstrap, login error, register validation, register success, password reset success, authenticated user hitting auth route, and direct platform/system route without token.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-2.1`, `REQ-5.1`, `REQ-5.2`, `REQ-6.2`, `REQ-8`, `REQ-10`, flow ids `A1-A4/P1/S1/C1` |
| Target role | unauthenticated user, registered creator, authenticated platform/system user |
| Entry point | browser first load, direct hash/direct path to auth/platform/system/no-member routes |
| User job | enter auth when unauthenticated, register/create first system, recover password, and reach the correct shell only after session bootstrap |
| Data contract | token state, account profile, registration result, system id, and switch context must drive routing |
| Permission contract | unauthenticated users cannot render platform/system/no-member shells; authenticated users are routed by role/context |
| State contract | loading, invalid/expired token, missing fields, register success, password reset success, and auth-route-with-token redirect states |
| Copy contract | auth entry labels and denied/session states must be specific; no confusing platform/system page under no-token state |
| Acceptance assertions | typecheck/build plus targeted route guard smoke for no-token platform/system/no-member/auth routes and register landing target |
| Screenshot evidence boundary | screenshots prove only visible auth/shell state; token/context/API assertions prove behavior |

Evidence paths:

- Script: `scripts/recovery-r61-auth-entry-guard.ps1`
- Result: `docs/evidence/recovery/r61-auth-entry-guard-result.json`
- Summary: `docs/evidence/recovery/r61-auth-entry-guard-2026-07-02.md`

Explicitly not complete if:

- `/platform`, `/platform/admin`, `/systems/{systemId}/...`, or `/no-member` can render a non-auth shell with no token.
- Registration lands on a generic dashboard instead of the system admin first-use path.
- Token bootstrap failure leaves stale platform/system UI visible.
- The task only changes route text without deterministic route/state evidence.

Result:

- R61 PASS.
- Source route contract checks passed for public auth routes, no-token redirect/render-login guard, authenticated auth-route redirect, default authenticated landing, and registration landing.
- `npm typecheck` PASS.
- `npm build` PASS; frontend asset `/assets/index-_ZDECw8K.js`.
- Release package PASS and deployed verification PASS on `http://127.0.0.1:18131` with database/schema/Redis `UP` and deployed frontend matching release assets.

Still partial:

- R61 closes the first auth/entry guard slice only. It does not close platform landing IA, system switch redraw breadth, first-use guide content, no-code configuration, runtime, workflow, OpenAPI, AI, operations, or user signoff.

## REC-P0-062 Platform And System Shell Landing Flow Closure

Status: `accepted` as deployed engineering evidence only; final usable-system acceptance remains `PARTIAL`.

Flow source: `docs/framework/final-system-flow-blueprint.md`

Flow ids: `P1`, `P2`, `PA1`, `S1`, `S2`, `B1`

User role: platform root/admin, platform member, system admin, system normal member

Business outcome: after login, each role lands in the correct platform/system shell with the expected primary navigation and no unauthorized shell leakage.

Requirement source: `REQ-2.1`, `REQ-4.1`, `REQ-4.2`, `REQ-5.1`, `REQ-5.2`, `REQ-6.2`, `REQ-6.3`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, and `docs/framework/final-system-flow-blueprint.md`.

Frontend scope: platform workspace header/navigation, platform admin entry, platform AI entry if supported by existing capability, system switch panel, system business shell header/navigation, system admin guard, and profile/todo/message entry positions.

Backend scope: existing account profile, platform todos/messages, system-switch options, system switch context, tenant switch context, and role permissions.

Generator scope: none.

Data scope: platform roles, available systems, current system/tenant/member context, todo/message scope, and current shell route.

Permission rule: platform admin/root can see platform admin; platform member cannot. System admin can see system admin after switch context; normal member cannot. No platform shell can directly edit system business configuration.

States: no available systems, disabled system, no-member mapping, platform admin denied, system admin denied, switch loading/failure, active shell, and role-specific empty states.

Evidence paths:

- Result: `docs/evidence/recovery/r62-platform-system-shell-landing-result.json`
- Summary: `docs/evidence/recovery/r62-platform-system-shell-landing-2026-07-02.md`
- Deployed verification: `scripts/verify-release.ps1 -BaseUrl http://127.0.0.1:18131 -CheckDeployedFrontend` PASS with deployed frontend asset `/assets/index-CLQXbZ2s.js`, backend health `UP`, database/schema/Redis `UP`, and admin login PASS.

Accepted evidence:

- Platform shell header now exposes dashboard, flow, apps, AI, todos, messages, and profile entry.
- Platform AI route `/platform/ai` is live, uses shell-state metrics, calls backend `runPlatformHealthCheck('AI')`, and keeps platform/system authority boundaries explicit.
- Business shells now require initialized account state, preventing stale-token or empty-account rendering such as an "鏈櫥褰? platform shell.
- Route registry includes platform flow/apps/AI/todos/messages/profile and system messages/profile entries.
- Browser verification proved login to platform shell as `admin`, AI health check backend call, and system switch into `#/systems/258/dashboard`.

Explicitly not complete if:

- Platform root/admin does not see the expected platform landing navigation including dashboard/flow/app/AI/todo/message/profile where in scope.
- Platform member sees platform admin or system admin entries.
- System routes render without switch context.
- System admin and system runtime navigation are mixed into one shell.
- The task changes visible navigation without deployed route/role evidence.

Still partial:

- R62 closes the landing-shell slice only. It does not close no-code first-use configuration, admin configuration usability, runtime publish/reflection, OpenAPI/AI workflow depth, operations breadth, full requirement coverage, or user signoff.

## REC-P0-084 System Work Management Daily Use Closure

Status: `accepted` as deployed work-management engineering evidence only; user signoff remains open.

Flow source: `docs/framework/final-system-flow-blueprint.md`

Flow ids: `B3`, `B4`, `B5`, `S2`, `J3`, `J8`, `J9`, `J11`

User role: system normal member using the system business shell

Business outcome: a normal system member can use one coherent daily work surface to review work status, create a project, create a project task, create a plain task, switch list/kanban views mutually exclusively, generate a daily report draft, manually confirm the draft, and read back persisted results.

Requirement source: `REQ-5.20`, `REQ-6.2`, `REQ-6.3`, `REQ-6.11`, and `REQ-2.1` from `docs/framework/final-requirement-coverage-ledger.md` and the gap report.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, `.cursor/knowledge/project-operating-rules.md` section Work Management, and `docs/framework/final-system-flow-blueprint.md` flow `B3`.

Frontend scope: `frontend/src/features/system-shell/systemShell.ts` work route only. Expose stable workbench, tab, dashboard, calendar, warnings, task-list, task-kanban, task-detail, create-panel, action-result, daily-report, auto-draft, and draft-confirm markers; keep the existing four-tab IA.

Backend scope: existing `WorkManagementController`, `WorkManagementService`, persisted work project/task/report tables, and existing work API endpoints. No generated CRUD may replace the coded work service.

Generator scope: none. Generated base services remain plumbing only; the accepted behavior is the coded manage service plus deployed frontend binding.

Data scope: `systemId`, `tenantId`, `systemMemberId`, project id, project task id, plain task id, daily report id, kanban columns, dashboard counts, calendar items, report draft id, trace id, and readback pages.

Permission rule: every work API must resolve the current system member context; anonymous access must be denied; created data must be scoped to the selected system and tenant.

States: loading, dashboard overview, empty warnings/calendar/tasks/reports, list view, kanban view, create panel, action result, selected task detail, auto-draft ready, manual draft confirmation, persisted daily report readback, and unauthenticated denied state.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-5.20`, `REQ-6.2`, `REQ-6.3`, `REQ-6.11`, `REQ-2.1`, flow `B3/B4/B5` |
| Target role | system normal member |
| Entry point | deployed login then `/systems/{systemId}/work`; API entry `/api/v1/systems/{systemId}/work/*` |
| User job | review dashboard/calendar, create project/task/plain-task, switch task view, generate and confirm daily report draft, then read back results |
| Data contract | project/task/report ids and dashboard/list/kanban/report readback must come from backend APIs |
| Permission contract | authenticated system member succeeds; unauthenticated work API call is denied |
| State contract | dashboard, calendar, task empty/non-empty, list, kanban, create result, detail, auto draft, manual confirm, persisted report |
| Copy contract | labels and result messages describe the concrete work object, not generic success |
| Acceptance assertions | typecheck plus R84 script verifies API create/readback, source DOM markers, framework/static/coverage boundaries, and user signoff remains false |
| Screenshot evidence boundary | Screenshots prove visible hierarchy and markers only; they do not prove functional completion. API/readback assertions prove persisted data and permission behavior. |

Acceptance script: `scripts/recovery-r84-work-management-daily-use.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r84-work-management-daily-use-result.json`
- Summary: `docs/evidence/recovery/r84-work-management-daily-use-2026-07-07.md`

Explicitly not complete if:

- Work management is split into isolated task/report pages instead of the four fixed tabs.
- Project tasks and plain tasks share one ambiguous object surface.
- List and kanban render as stacked simultaneous views instead of mutually exclusive views.
- Auto draft creates or submits a daily report without a visible manual confirmation step.
- API creation succeeds but dashboard/list/kanban/report readback cannot find the created objects.
- Anonymous or wrong-context requests can create or read work data.
- User signoff is implied from engineering evidence; `gates.user_script_passed` must remain false.

Accepted evidence for R84:

- R84 PASS on deployed `http://127.0.0.1:18131`.
- Typecheck/build/package-release/local-start/verify-release passed with deployed frontend asset `index-CkQrFuuA.js` and database/schema/Redis `UP`.
- API evidence created/read back a project, project task, plain task, kanban columns, daily draft, confirmed daily report, dashboard before/after, and anonymous API denial.
- Product fixes included stable work-management DOM markers, manual confirmation before saving auto daily-report drafts, local datetime payload formatting for work tasks, and backend same-day daily report upsert to avoid unique-key 500s.
- `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.

## REC-P0-085 System Dashboard Daily Action Hub Closure

Status: `accepted` as deployed dashboard daily-action-hub engineering evidence only; user signoff remains open.

Flow source: `docs/framework/final-system-flow-blueprint.md`

Flow ids: `S2`, `B1`, `B3`, `B4`, `B5`, `J3`, `J8`, `J9`, `J11`

User role: system normal member entering the system business shell after login.

Business outcome: a normal system member can land on `/systems/{systemId}/dashboard` and immediately see today work status, pending todos, unread messages, authorized business modules, and quick-create/navigation actions backed by real APIs instead of guessing where to go next.

Requirement source: `REQ-5.8`, `REQ-6.2`, `REQ-6.3`, `REQ-5.20`, `REQ-5.16`, `REQ-6.11`, and `REQ-2.1` from `docs/framework/final-requirement-coverage-ledger.md` plus `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, `.cursor/knowledge/project-operating-rules.md` runtime daily efficiency and work-management rules, and `docs/framework/final-system-flow-blueprint.md` flow `S2/B1/B3/B4/B5`.

Frontend scope: `frontend/src/features/system-shell/systemShell.ts` dashboard route only. The dashboard must expose stable markers for daily action hub, work preview, todo preview, message preview, authorized module preview, and quick-create/navigation actions. It must keep one primary first-screen task surface and route to existing work/todo/message/module pages.

Backend scope: reuse existing deployed APIs: work dashboard, project/plain task search, system todo search, system message search, and runtime module navigation. No new generated CRUD or duplicate dashboard API is allowed for this slice.

Generator scope: none. Existing generated/base APIs remain plumbing only; the accepted behavior is the coded system shell binding real backend data into the first-screen action hub.

Data scope: `systemId`, `tenantId`, `systemMemberId`, dashboard trace id, work task ids, todo ids, message ids, unread/pending counts, authorized module ids/names, and navigation targets.

Permission rule: the dashboard may only show data returned for the current system member context; anonymous API access must remain denied; platform/system/admin surfaces must not leak into the normal member dashboard action hub.

States: loading, dashboard overview, daily action hub, work empty/non-empty, todo empty/non-empty, message empty/non-empty, authorized module empty/non-empty, quick-create actions, navigation actions, error state, and user signoff boundary.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-5.8`, `REQ-6.2`, `REQ-6.3`, `REQ-5.20`, `REQ-5.16`, `REQ-6.11`, `REQ-2.1`, flow `S2/B1/B3/B4/B5` |
| Target role | system normal member |
| Entry point | deployed login then `/systems/{systemId}/dashboard`; API entries `/work/dashboard`, `/work/*/search`, `/todos/search`, `/messages/search` |
| User job | understand today work, pending todo/message pressure, open authorized business modules, and start a quick work action from the first screen |
| Data contract | dashboard/work/todo/message/module data must come from backend APIs and expose ids/counts in DOM markers or evidence |
| Permission contract | authenticated system member succeeds; unauthenticated dashboard-related APIs are denied; admin-only actions are not shown to normal members |
| State contract | loading, non-empty previews, empty previews, quick action buttons, navigation links, error panel, and trace id |
| Copy contract | first-screen labels describe concrete daily actions, not generic placeholders or fake success |
| Acceptance assertions | typecheck plus R85 script verifies source markers, deployed API readbacks, browser dashboard markers, framework/static/coverage boundaries, and user signoff remains false |
| Screenshot evidence boundary | Screenshots prove visible hierarchy and first-screen markers only as visual evidence only; they do not prove functional completion. API/readback assertions prove data, permission, and route behavior. |

Acceptance script: `scripts/recovery-r85-system-dashboard-daily-action-hub.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r85-system-dashboard-daily-action-hub-result.json`
- Summary: `docs/evidence/recovery/r85-system-dashboard-daily-action-hub-2026-07-07.md`

Explicitly not complete if:

- The dashboard only shows static home text or configured widgets without pending work/todo/message/module data.
- Quick actions are fake buttons, generic toasts, or do not route into existing real work/todo/message/module pages.
- Todo/message/work previews are hardcoded or derived from stale demo fixtures instead of backend APIs.
- Normal members see system-admin/platform-admin action leakage on the dashboard.
- Anonymous requests can read dashboard-related data.
- User signoff is implied from engineering evidence; `gates.user_script_passed` must remain false.
Accepted evidence for R85:

- R85 PASS on deployed `http://127.0.0.1:18131`.
- Typecheck/build/package-release/local-start/verify-release passed with deployed frontend asset `index-C40SlAQy.js` and release health verified.
- API evidence created/read back quick task `53`, read dashboard before/after, home page, project/plain work previews, todo/message pages, authorized modules, and anonymous denial for dashboard/todo/message APIs.
- Product fixes included a system dashboard daily action hub, stable source/deployed DOM markers, quick-create navigation into real work management, todo/message/module previews from existing backend APIs, and responsive action-hub layout.
- `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.
## REC-P0-086 Page Designer Drag Canvas And Runtime Component Contract Closure

Status: `accepted` as deployed page-designer drag-canvas/runtime-component engineering evidence only; user signoff remains open.

Flow source: `docs/framework/final-system-flow-blueprint.md`

Flow ids: `C1`, `C2`, `C3`, `B2`, `J2`, `J3`, `J8`, `J9`, `J11`

User role: system administrator configuring a module page, then system normal member using the published runtime page.

Business outcome: a system administrator can adjust a module page through a real drag/drop-style component canvas with keyboard-reachable controls, visible component layout metadata, and mobile/runtime preview; after publish, a normal member receives the same allowed component order and props through the runtime page schema while hidden-field-bound components remain pruned.

Requirement source: `REQ-5.8`, `REQ-6.1`, `REQ-6.8`, `REQ-6.11`, `REQ-9`, and `REQ-2.1` from `docs/framework/final-requirement-coverage-ledger.md` plus `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, `.cursor/knowledge/project-operating-rules.md` page designer/no-code configuration rules, and `docs/framework/final-system-flow-blueprint.md` flows `C1/C2/C3/B2`.

Frontend scope: `frontend/src/features/system-admin/systemAdmin.ts`, `frontend/src/features/schema/schemaRenderer.ts`, and `frontend/src/styles.css`. Add stable markers for a page drag canvas, draggable component rows, drop targets, keyboard move controls, component width/placement props, mobile preview props, and schema/runtime component preview.

Backend scope: reuse existing `ModulePageDesignController` and `ModulePageDesignService` page save/publish/schema endpoints. Persist layout metadata through existing `PageComponentConfig.props`; do not add a duplicate page-design API unless deterministic evidence shows the existing contract cannot carry the data.

Generator scope: none. Generated/base services remain plumbing only; the accepted behavior is the coded page-designer frontend binding plus persisted page-schema readback.

Data scope: `systemId`, `tenantId`, `moduleId`, `pageCode`, page version, component codes/types/sort/visible flags, `props.width`, `props.placement`, `props.dragCanvas`, bound field codes, permission snapshot version, hidden component pruning, and deployed asset markers.

Permission rule: system admins can save/publish the page; normal members can read only permitted runtime schema; normal-member page/home writes and forbidden runtime writes remain denied; hidden fields and hidden-field-bound components must not leak.

States: loading, existing page design, empty/default component set, drag-start/drop/keyboard reorder, visible/hidden component rows, mobile preview, schema preview, publish check, publish success, runtime schema readback, hidden component pruned, forbidden write denial, desktop/mobile containment, and user signoff boundary.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-5.8`, `REQ-6.1`, `REQ-6.8`, `REQ-6.11`, `REQ-9`, `REQ-2.1`, flows `C1/C2/C3/B2` |
| Target role | system administrator and system normal member |
| Entry point | deployed login then `/systems/{systemId}/admin` module page tab; API entries `/modules/{moduleId}/pages`, `/pages/{pageCode}/publish-check`, `/pages/{pageCode}/publish`, and runtime `/pages/{pageCode}/schema` |
| User job | drag/reorder page components, set layout props, save/publish, then verify runtime schema exposes the allowed component contract |
| Data contract | page component order, visible flags, props, published version, runtime schema components, and permission snapshot must be read from backend APIs |
| Permission contract | admin save/publish succeeds; normal member runtime read is permission-pruned; normal write/admin writes are denied |
| State contract | drag canvas, draggable rows, keyboard controls, prop chips, mobile preview, schema preview, publish result, hidden/pruned runtime state, and forbidden write state |
| Copy contract | labels describe page components, component visibility, layout width/placement, and runtime schema, not fake success or generic placeholders |
| Acceptance assertions | typecheck plus R86 script verifies source/deployed markers, API save/publish/runtime readback, component props persistence, hidden-component pruning, browser drag/keyboard markers, framework/static/coverage boundaries, and user signoff remains false |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, drag canvas markers, mobile preview, and overflow only as visual evidence; they do not prove functional completion. API/readback assertions prove component persistence, permission pruning, and runtime schema behavior. |

Acceptance script: `scripts/recovery-r86-page-designer-drag-canvas-runtime-contract.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r86-page-designer-drag-canvas-runtime-contract-result.json`
- Summary: `docs/evidence/recovery/r86-page-designer-drag-canvas-runtime-contract-2026-07-07.md`

Explicitly not complete if:

- The page designer still only offers up/down buttons with no drag/drop or equivalent canvas affordance.
- Component layout metadata is visible in the UI but is not persisted and read back through the page API.
- Published runtime schema loses component order, visibility, or props.
- Hidden fields or hidden-field-bound components leak to normal members.
- Normal members can write page configuration or bypass runtime write denial.
- Browser evidence only proves a route opens without checking component state, drag/canvas markers, API readback, and permission negatives.
- User signoff is implied from engineering evidence; `gates.user_script_passed` must remain false.
Accepted evidence for R86:

- R86 PASS on deployed `http://127.0.0.1:18131`.
- Typecheck/build/package-release/local-start/verify-release passed with deployed frontend asset `index-YU9KKh-k.js`.
- API evidence saved and published page `main`, preserved `props.width`, `props.placement`, and `props.dragCanvas`, returned matching admin/normal schema version `PAGE_v1783421161375`, pruned hidden field/component for the normal member, and denied normal create with HTTP `403`.
- Browser evidence proved drag canvas, 6 draggable rows, 38 component action buttons, width/placement values, mobile preview markers, schema component strip, zero overflow, source markers, and deployed asset markers.
- Result: `docs/evidence/recovery/r86-page-designer-drag-canvas-runtime-contract-result.json`; summary: `docs/evidence/recovery/r86-page-designer-drag-canvas-runtime-contract-2026-07-07.md`; browser audit: `docs/evidence/recovery/screenshots/r86-page-designer-drag-canvas-runtime-contract/page-designer-browser-audit.json`.
- `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.
## REC-P0-087 System Todo Message Workbench Usability Closure

Status: `accepted` as deployed todo/message workbench usability engineering evidence only; user signoff remains open.

Flow source: `docs/framework/final-system-flow-blueprint.md`

Flow ids: `B3`, `B4`, `B5`, `C4`, `S2`, `J3`, `J8`, `J9`, `J11`

User role: system normal member handling daily pending work, workflow approval todos, and system messages from the system shell.

Business outcome: a normal member can enter the system todo/message workbench from the dashboard or shell, understand which item needs action, inspect item target/status/trace details without leaving context, execute allowed todo actions with visible readback, mark/read/archive messages, and navigate to the related business/workflow target while forbidden or empty states remain clear.

Requirement source: `REQ-5.16`, `REQ-5.20`, `REQ-6.2`, `REQ-6.3`, `REQ-6.11`, and `REQ-2.1` from `docs/framework/final-requirement-coverage-ledger.md` plus `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, `.cursor/knowledge/project-operating-rules.md` normal-member daily-use rules, and `docs/framework/final-system-flow-blueprint.md` flows `B3/B4/B5/C4/S2`.

Frontend scope: `frontend/src/features/system-shell/systemShell.ts` and `frontend/src/styles.css`. Add stable markers for a todo/message workbench, selected todo detail, target summary, allowed/disabled actions, action result readback, message detail cards, read/archive controls, empty/filter states, and mobile containment.

Backend scope: reuse existing system todo/message endpoints: `/api/v1/systems/{systemId}/todos/search`, `/todos/{todoId}/actions/{actionCode}`, `/messages/search`, `/messages/mark-read`, `/messages/mark-all-read`, and `/messages/archive`. Do not add duplicate todo/message APIs unless deterministic evidence proves the existing contract cannot support the workbench.

Generator scope: none. Generated/base services remain plumbing only; the accepted behavior is coded frontend binding plus backend API/readback evidence.

Data scope: `systemId`, `tenantId`, `todoId`, `messageId`, type tree, status, priority, dueAt, target, traceId, action permissions, disabled reasons, read status, archive status, affected count, audit trace, and deployed asset markers.

Permission rule: normal members can read only their scoped todos/messages and execute only enabled actions; disabled actions must show reasons; anonymous requests and cross-scope direct reads remain denied; messages and todos must not route to admin surfaces unless the target explicitly requires an authorized switch.

States: loading, pending todos, filtered empty todos, selected todo detail, enabled action, disabled action, action result, pending/read/archived messages, filtered empty messages, mark-one-read, mark-all-read, archive result, target navigation, API denied/failure, desktop/mobile containment, and user signoff boundary.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-5.16`, `REQ-5.20`, `REQ-6.2`, `REQ-6.3`, `REQ-6.11`, `REQ-2.1`, flows `B3/B4/B5/C4/S2` |
| Target role | system normal member |
| Entry point | deployed login then `/systems/{systemId}/dashboard`, `/systems/{systemId}/todos`, and `/systems/{systemId}/messages` |
| User job | open daily todo/message workbench, inspect context, execute allowed todo/message state changes, and navigate to the related target |
| Data contract | todo type tree, page rows, action permissions, message page rows, read/archive state, target metadata, trace/audit ids, and action result readback must come from backend APIs |
| Permission contract | normal member scoped reads/actions succeed; disabled action reasons are visible; anonymous todo/message APIs are denied; admin-only routes are not exposed from the workbench |
| State contract | loading, empty, filtered-empty, selected todo detail, action success/failure, unread/read/archived messages, mark-all result, archive result, target navigation, and mobile containment |
| Copy contract | labels describe pending work, target, priority, due time, disabled reason, trace/audit result, and message state, not fake success or generic placeholders |
| Acceptance assertions | typecheck plus R87 script verifies source/deployed markers, API readback, workbench DOM markers, action/result state, message read/archive state, permission negatives, static/framework boundaries, and user signoff remains false |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, selected detail, message card layout, controls, and overflow only as visual evidence; they do not prove functional completion. API/readback assertions prove scoped data, state transitions, and permission behavior. |

Acceptance script: `scripts/recovery-r87-todo-message-workbench-usability.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r87-todo-message-workbench-usability-result.json`
- Summary: `docs/evidence/recovery/r87-todo-message-workbench-usability-2026-07-07.md`

Explicitly not complete if:

- The todo page remains only a table that immediately navigates away without an inspectable selected item context.
- Todo actions have no visible result, trace/audit readback, or disabled reason.
- The message page cannot distinguish unread/read/archived state, or read/archive is only a fake frontend state.
- Dashboard entries route to dead pages or stale demo data instead of real todo/message APIs.
- Anonymous or unauthorized users can read scoped system todos/messages.
- Browser evidence only proves routes open without checking workbench markers, API state transitions, target context, and permission negatives.
- User signoff is implied from engineering evidence; `gates.user_script_passed` must remain false.
Accepted evidence for R87:

- R87 PASS on deployed `http://127.0.0.1:18131`.
- Typecheck/build/package-release/local-start/verify-release passed with deployed frontend asset `index-qHAj_cJd.js` and CSS `index-i46ACQCh.css`.
- API evidence used retained workflow trial system `1121`, proved scoped todo/message APIs readable, handled todo total `1`, retained archived message total `1`, anonymous todo/message API denial, and message read/archive state readback from the first R87 run.
- Browser evidence proved system todo workbench markers, type tree, selected/empty detail panel, system message center toolbar, empty/archived message state, desktop/mobile containment, overflow `0`, blockers `0`, source markers, and deployed asset markers.
- Result: `docs/evidence/recovery/r87-todo-message-workbench-usability-result.json`; summary: `docs/evidence/recovery/r87-todo-message-workbench-usability-2026-07-07.md`; browser audit: `docs/evidence/recovery/screenshots/r87-todo-message-workbench-usability/todo-message-workbench-browser-audit.json`.
- `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.
## REC-P0-088 Runtime Efficiency Entry Search Recent Draft Closure

Status: `accepted` as deployed runtime efficiency entry/search/recent/draft engineering evidence only; user signoff remains open.

Flow source: `docs/framework/final-system-flow-blueprint.md`

Flow ids: `B1`, `B2`, `B3`, `S2`, `J3`, `J8`, `J9`, `J11`

User role: system normal member using the system dashboard and runtime business modules for daily work.

Business outcome: a normal member can find daily business records through a clear runtime search entry, return to recently opened records/modules, continue a saved runtime draft, and start authorized business creation without guessing routes or losing context.

Requirement source: `REQ-4.4`, `REQ-5.11`, `REQ-6.2`, `REQ-6.3`, `REQ-6.4`, `REQ-6.5`, `REQ-6.11`, and `REQ-2.1` from `docs/framework/final-requirement-coverage-ledger.md` plus `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, `.cursor/knowledge/project-operating-rules.md` runtime daily-efficiency rules, and `docs/framework/final-system-flow-blueprint.md` flows `B1/B2/B3/S2`.

Frontend scope: `frontend/src/features/system-shell/systemShell.ts`, `frontend/src/features/runtime/records/runtimeRecords.ts`, and `frontend/src/styles.css`. Add stable markers for dashboard runtime search/recent/draft/quick-create entries, runtime efficiency strip, saved-draft resume, selected recent record, empty states, field-error focus marker, and mobile containment.

Backend scope: reuse existing runtime module navigation, record search, draft save, and record create/update APIs. Do not add duplicate search/recent/draft APIs unless deterministic evidence proves the existing contracts cannot support the daily-use loop.

Generator scope: none. Generated/base services remain plumbing only; the accepted behavior is coded frontend binding plus backend API/readback evidence.

Data scope: `systemId`, `tenantId`, `moduleId`, `recordId`, record title, module name, keyword, saved draft id, draft field values, permission snapshot, create/edit action permissions, validation/error field code, trace/audit ids, and deployed asset markers.

Permission rule: normal members can only see authorized modules and scoped runtime rows; readonly or forbidden create/edit actions must remain disabled or denied by backend; anonymous runtime search/draft APIs are denied; hidden fields must not leak through recent/draft/search displays.

States: loading, runtime search entry, search result/empty, recent record empty/populated, recent module open, quick create enabled/disabled, draft saved, draft resumed, create validation error with field focus marker, successful record save, backend denied/failure, desktop/mobile containment, and user signoff boundary.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.4`, `REQ-5.11`, `REQ-6.2`, `REQ-6.3`, `REQ-6.4`, `REQ-6.5`, `REQ-6.11`, `REQ-2.1`, flows `B1/B2/B3/S2` |
| Target role | system normal member |
| Entry point | deployed login then `/systems/{systemId}/dashboard` and `/systems/{systemId}/modules` |
| User job | search runtime data, open recent records/modules, save and resume a runtime draft, create a record, and understand field-level validation errors |
| Data contract | runtime module navigation, runtime search rows, draft id/field values, record mutation result, permission snapshot, recent index, and trace/audit ids must be read from real APIs or persisted browser state tied to the current system/module |
| Permission contract | scoped normal-member runtime reads and writes follow backend permissions; readonly/anonymous negatives are denied; dashboard entries only expose authorized modules |
| State contract | dashboard efficiency entries, runtime efficiency strip, populated/empty recent and draft states, quick-create route, draft resume, validation field marker, save result, and mobile containment |
| Copy contract | labels describe search, recent work, draft continuation, quick create, validation field, and backend result without fake success or generic placeholders |
| Acceptance assertions | typecheck plus R88 script verifies source/deployed markers, API readback, draft save/resume, recent index, browser DOM markers, permission negatives, static/framework/coverage boundaries, and user signoff remains false |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, entry placement, mobile containment, and overflow only as visual evidence; they do not prove functional completion. API/readback assertions prove search, draft, mutation, permission, and recent/draft state behavior. |

Acceptance script: `scripts/recovery-r88-runtime-efficiency-entry-search-recent-draft.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r88-runtime-efficiency-entry-search-recent-draft-result.json`
- Summary: `docs/evidence/recovery/r88-runtime-efficiency-entry-search-recent-draft-2026-07-07.md`

Explicitly not complete if:

- Dashboard/runtime entries only navigate to static pages without real runtime search/readback.
- Recent or draft state is global demo data instead of scoped to the current system/module.
- Saved draft id and field values cannot be resumed into the runtime form.
- Validation or backend errors are only shown as generic text without a field/record context marker.
- Readonly, anonymous, or unauthorized users can create records through the new entries.
- Browser evidence only proves routes open without checking source/deployed markers, API readback, draft/recent state, and permission negatives.
- User signoff is implied from engineering evidence; `gates.user_script_passed` must remain false.
Accepted evidence for R88:

- R88 PASS on deployed `http://127.0.0.1:18131`.
- Typecheck/build/package-release/local-start/verify-release passed with deployed frontend asset `index-CtPMdGYM.js` and CSS `index-CHS-pNgA.css`.
- API evidence used retained runtime trial system `1118` and module `453`, saved runtime draft `draft_trc_97d8`, created/search-read runtime record `639`, and proved readonly plus anonymous runtime write/search/draft denials.
- Browser evidence proved dashboard runtime efficiency entries, recent/draft seeded state, runtime efficiency strip, draft restore into `caseTitle`, empty-form field-error marker, desktop/mobile containment, overflow `0`, blockers `0`, source markers, and deployed asset markers.
- Result: `docs/evidence/recovery/r88-runtime-efficiency-entry-search-recent-draft-result.json`; summary: `docs/evidence/recovery/r88-runtime-efficiency-entry-search-recent-draft-2026-07-07.md`; browser audit: `docs/evidence/recovery/screenshots/r88-runtime-efficiency-entry-search-recent-draft/runtime-efficiency-browser-audit.json`.
- `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.
## REC-P0-089 Runtime File Import Export Recovery Detail Closure

Status: `accepted` as deployed runtime file/import/export recovery-detail engineering evidence only; user signoff remains open.

Flow source: `docs/framework/final-system-flow-blueprint.md`

Flow ids: `B1`, `B2`, `E2`, `C5`, `O3`, `J3`, `J6`, `J8`, `J9`, `J11`

User role: system normal member moving business data in and out of an authorized runtime module, plus system administrator/operator reading the task boundary.

Business outcome: a normal member can start import/export work from the runtime module, inspect precheck and async-task details, download or preview result/error file entries, understand selected/all/template export scope, and see whether rollback is supported without guessing from a toast or server log.

Requirement source: `REQ-4.4`, `REQ-5.11`, `REQ-5.13`, `REQ-5.14`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, `REQ-6.11`, and `REQ-2.1` from `docs/framework/final-requirement-coverage-ledger.md` plus `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, `.cursor/knowledge/project-operating-rules.md` runtime data-movement rules, and `docs/framework/final-system-flow-blueprint.md` flow `E2`.

Frontend scope: `frontend/src/features/runtime/import-export/importExportPanel.ts`, `frontend/src/features/runtime/records/runtimeRecords.ts`, and `frontend/src/styles.css`. Add stable markers for import/export recovery detail, precheck issue summary, async task result/error files, selected/all/template export scope, rollback support or unsupported reason, trace/audit readback, permission-denied state, and mobile containment.

Backend scope: reuse existing runtime upload, import precheck/confirm, export, file preview/download, audit, and permission endpoints. Do not add duplicate import/export APIs unless deterministic evidence proves the existing contracts cannot support recovery-detail readback.

Generator scope: none. Generated/base services remain plumbing only; the accepted behavior is coded frontend binding plus backend API/readback evidence.

Data scope: `systemId`, `tenantId`, `moduleId`, `precheckId`, `taskId`, `resultFileId`, `errorFileId`, selected record ids, export scope, template code, duplicate strategy, rollbackSupported, rollback boundary reason, traceId, auditLogId, permission snapshot, and deployed asset markers.

Permission rule: normal members can import/export only through authorized module actions and field permissions; readonly or anonymous users must be denied by backend; hidden fields and forbidden attachments/files must not leak through export, file links, task cards, or recovery details.

States: import empty, uploaded file id, field mapping/precheck, failed precheck with error file, confirm disabled until precheck passes, import success with task/result detail, export all, export selected, template-only export, selected-count empty state, result-file/error-file action links, rollback supported/unsupported boundary, API denied/failure, desktop/mobile containment, and user signoff boundary.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.4`, `REQ-5.11`, `REQ-5.13`, `REQ-5.14`, `REQ-6.4`, `REQ-6.5`, `REQ-6.6`, `REQ-6.11`, `REQ-2.1`, flow `E2` |
| Target role | system normal member plus system administrator/operator readback boundary |
| Entry point | deployed login then `/systems/{systemId}/modules?moduleId={moduleId}` import/export panels |
| User job | upload/import data, inspect precheck and task recovery details, export all/selected/template data, and download or preview task result/error files |
| Data contract | precheck id, task id/status/progress, result/error file ids, selected record ids, export scope, rollbackSupported, trace/audit ids, and permission snapshot must come from real APIs or live runtime state |
| Permission contract | authorized runtime import/export succeeds; readonly/anonymous negatives are denied; hidden fields and forbidden file access do not leak through recovery detail or file links |
| State contract | import precheck, failed-precheck, confirm success, export all/selected/template, selected-empty disabled state, result/error file links, rollback boundary, denied/failure, reload/mobile containment |
| Copy contract | labels explain precheck, mapping, task progress, result file, error file, selected scope, rollback boundary, and denied reasons without fake success or generic placeholders |
| Acceptance assertions | typecheck plus R89 script verifies source/deployed markers, API import/export/file readback, browser DOM markers, selected/all/template export states, rollback boundary, permission negatives, static/framework/coverage boundaries, and user signoff remains false |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, task cards, file-link placement, rollback copy, and overflow only as visual evidence; they do not prove functional completion. API/readback assertions prove import/export tasks, files, permissions, and state transitions. |

Acceptance script: `scripts/recovery-r89-runtime-file-import-export-recovery-detail.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r89-runtime-file-import-export-recovery-detail-result.json`
- Summary: `docs/evidence/recovery/r89-runtime-file-import-export-recovery-detail-2026-07-07.md`

Explicitly not complete if:

- Import/export still only shows a toast without task id, status, trace/audit id, and result/error file context.
- Result/error files are present in API data but not reachable or visible from the deployed UI.
- Selected export can be triggered with no selected rows without a clear disabled reason.
- Rollback support is ambiguous, or unsupported rollback is hidden instead of explained.
- Readonly, anonymous, hidden-field, or forbidden-file paths leak data through the new recovery detail UI.
- Browser evidence only proves routes open without checking source/deployed markers, API readback, task/file states, and permission negatives.
- User signoff is implied from engineering evidence; `gates.user_script_passed` must remain false.
Accepted evidence for R89:

- R89 PASS on deployed `http://127.0.0.1:18131`.
- Typecheck/build/package-release/local-start/verify-release passed with deployed frontend asset `index-CNKwIYLK.js` and CSS `index-LmRPxR3A.css`.
- API evidence used retained runtime trial system `1118` and module `453`, proved import precheck/confirm task `task_import_confirm_1d904b1e`, failed precheck error file `file_precheck_error_765f8107`, all/selected/template export result files, anonymous import/export denials, imported-row search, and hidden-field non-leakage.
- Browser evidence proved export empty selected state, export result/error file actions, import precheck/confirm recovery detail, rollback boundary markers, desktop/mobile containment, overflow `0`, blockers `0`, source markers, and deployed asset markers.
- Local release proxy was hardened so aborted proxied API/file requests do not crash with duplicate response headers.
- Result: `docs/evidence/recovery/r89-runtime-file-import-export-recovery-detail-result.json`; summary: `docs/evidence/recovery/r89-runtime-file-import-export-recovery-detail-2026-07-07.md`; browser audit: `docs/evidence/recovery/screenshots/r89-runtime-file-import-export-recovery-detail/runtime-file-import-export-recovery-browser-audit.json`.
- `userSignoff=false` and `gates.user_script_passed=false` remain unchanged.
## REC-P0-090 System Org Member Role Binding First-Use Closure

Status: `accepted` as deployed organization/member/role binding engineering evidence only; user signoff remains open.

Flow source: `docs/framework/final-system-flow-blueprint.md`

Flow ids: `C1`, `C3`, `S1`, `B1`, `B2`, `J2`, `J3`, `J5`, `J8`, `J9`, `J11`

User role: system administrator configuring a usable system, plus normal member who must enter the system through a real member/account/role binding.

Business outcome: a system administrator can create/select a department, create a member, bind that member to a login account, assign one or more system roles, inspect binding/role/readback state in the deployed admin UI, then verify the normal account can switch into the system and only sees authorized runtime surfaces.

Requirement source: `REQ-4.1`, `REQ-4.3`, `REQ-5.2`, `REQ-5.7`, `REQ-5.10`, `REQ-6.2`, `REQ-6.10`, and `REQ-2.1` from `docs/framework/final-requirement-coverage-ledger.md` plus `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, `.cursor/knowledge/project-operating-rules.md` organization/member/permission rules, and `docs/framework/final-system-flow-blueprint.md` flows `C1`, `C3`, and `S1`.

Frontend scope: `frontend/src/features/system-admin/systemAdmin.ts`, `frontend/src/api/liveData.ts`, and `frontend/src/styles.css`. Add a first-use organization/member operations panel with stable markers for department tree selection, member creation/update, account binding, role assignment, binding state, role state, effective switch context, denied normal-admin state, and mobile containment.

Backend scope: reuse existing organization, member, role, role-member, account binding, system-switch, and permission endpoints. Add only small missing readback fields or validation if evidence proves the existing contracts cannot support the first-use binding loop.

Generator scope: none. Generated/base CRUD remains plumbing only; the accepted behavior is coded frontend binding plus persisted backend readback and permission evidence.

Data scope: `systemId`, `tenantId`, `deptId`, `systemMemberId`, `accountId/loginName`, `accountMemberBindingId`, `roleId`, role-member rows, binding status, system switch context, effective role ids, permission snapshot, denied admin route/API result, traceId/audit markers where available, and deployed asset markers.

Permission rule: only system administrators can manage organization, member, account binding, and role assignment; normal members cannot enter system admin or mutate org/role APIs; bound normal members can switch into the system only through `SystemSwitchContext` and see runtime data according to assigned role permissions.

States: empty department/member list, department selected, member created, member updated, account missing/binding failed, account bound, role assigned, role not selected, binding/readback summary, normal account switch success, normal admin denied state, backend denied failure, desktop/mobile containment, and user signoff boundary.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.1`, `REQ-4.3`, `REQ-5.2`, `REQ-5.7`, `REQ-5.10`, `REQ-6.2`, `REQ-6.10`, `REQ-2.1`, flows `C1`, `C3`, `S1` |
| Target role | system administrator configuring org/member/role binding; bound normal member using the resulting system context |
| Entry point | deployed login then `/systems/{systemId}/admin` organization/role sections, followed by normal login and system switch/runtime route |
| User job | create organization/member data, bind a member to an account, assign role permissions, read back the binding, and prove the bound account can enter the system while denied admin mutation |
| Data contract | department id/tree, member id/status/roleIds, account binding id/status, role id, switch context, effective role ids, denied admin API status, source/deployed asset markers |
| Permission contract | system admin org/member/role operations succeed; normal member system switch succeeds only after binding; normal member system-admin and org/role mutation paths are denied |
| State contract | empty, created, selected, bound, assigned, missing account failure, denied normal-admin, reload/readback, mobile containment |
| Copy contract | labels explain organization setup, member binding, role assignment, switch readiness, denied reasons, and next action without generic success or technical-only IDs |
| Acceptance assertions | typecheck plus R90 script verifies source/deployed markers, API org/member/bind/assign/readback, normal account switch/runtime access, normal admin/API denial, browser DOM markers, static/framework/coverage boundaries, and user signoff remains false |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, tree/member/role binding placement, denied copy, and overflow only as visual evidence; they do not prove functional completion. API/readback assertions prove organization, binding, role, switch, and permission behavior. |

Acceptance script: `scripts/recovery-r90-org-member-role-binding-first-use.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r90-org-member-role-binding-first-use-result.json`
- Summary: `docs/evidence/recovery/r90-org-member-role-binding-first-use-2026-07-07.md`

Accepted evidence:

- `scripts/recovery-r90-org-member-role-binding-first-use.ps1` PASS on deployed `http://127.0.0.1:18131`.
- API readback proved department/member setup, member `1551`, role `1651`, binding `1551`, binding status `BOUND`, and assigned role code `R90_RUNTIME_20260707224102`.
- Bound normal account `r90_normal_20260707224102` switched into system `1134` with effective role id/code readback.
- Normal-member organization, department, and role admin mutations were denied.
- Browser audit proved desktop/mobile organization member binding markers, role workbench marker, denied normal-admin state, overflow `0`, blockers `0`, and user signoff remains false.

Explicitly not complete if:

- The admin UI creates a member but cannot bind the member to a real login account.
- Role assignment is only implied by a selected row and not persisted/read back in `member.roleIds`.
- The normal account cannot switch into the target system after binding.
- Normal members can enter system admin or mutate organization/member/role APIs.
- Browser evidence only proves the route opens without checking source/deployed markers, API readback, switch context, role assignment, and permission negatives.
- User signoff is implied from engineering evidence; `gates.user_script_passed` must remain false.


## REC-P0-091 Role Permission Matrix Impact Preview Audit Closure

Status: `accepted` as deployed permission-matrix impact-preview engineering evidence only; user signoff remains open.

Flow source: `docs/framework/final-system-flow-blueprint.md`

Flow ids: `C1`, `C2`, `C3`, `S1`, `B2`, `J2`, `J3`, `J5`, `J8`, `J9`, `J11`

User role: system administrator configuring role permissions for real bound members, plus normal member whose runtime entry must reflect the configured permission result.

Business outcome: a system administrator can edit one role permission matrix, preview multiple actions before saving or after saving, see field mask/data-scope/conflict explanations and affected member count, read back recent preview audit entries, then verify a normal member receives the denied/allowed runtime behavior without seeing the system-admin permission surface.

Requirement source: `REQ-4.3`, `REQ-5.7`, `REQ-5.10`, `REQ-6.2`, `REQ-6.4`, `REQ-6.10`, and `REQ-2.1` from `docs/framework/final-requirement-coverage-ledger.md` plus `docs/evidence/final-requirement-gap-report.md`.

Prototype reference: `docs/design/prototype-brief.md`, `docs/design/prototypes/index.html`, `.cursor/knowledge/project-operating-rules.md` permission rules, and `docs/framework/final-system-flow-blueprint.md` flows `C2`, `C3`, and `S1`.

Frontend scope: `frontend/src/features/system-admin/systemAdmin.ts`, `frontend/src/api/liveData.ts`, and `frontend/src/styles.css`. Strengthen the existing role permission workbench with stable R91 markers for batch action preview, impact summary, conflict explanation, field mask summary, data-scope summary, affected role/member count, recent preview audit rows, denied normal-admin state, and desktop/mobile containment.

Backend scope: extend existing permission APIs under `/api/v1/systems/{systemId}/permissions` with coded business readback for batch preview and recent preview audit. Reuse persisted role permissions, role-member rows, permission versions, and `un_plat_permission_preview_log`; do not add a parallel permission page or generated-only CRUD surface.

Generator scope: none. Generated/base entities remain plumbing only; the accepted behavior is coded permission aggregation, preview log readback, frontend role workbench binding, and deployed evidence.

Data scope: `systemId`, `tenantId`, `roleId`, `moduleId`, `systemMemberId`, action codes, action decisions, denied/missing permissions, field mask rules, data-scope expression, affected member count, permission version, preview log id, trace id, audit log id, runtime denial/readback status, and deployed asset markers.

Permission rule: only system administrators can read/save/preview role permissions and read preview audit rows; normal members cannot enter system admin or call permission admin APIs. Runtime behavior must still be evaluated from the current `SystemSwitchContext`, not from a frontend-supplied role shortcut.

States: no role/module, loaded permission version, unsaved edits, saved permission version, batch preview all allowed, batch preview denied action, field hidden/masked, data scope self/all, conflict explanation, affected member count, recent preview audit row, normal runtime denied create, normal admin denied, backend denied failure, desktop/mobile containment, and user signoff boundary.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.3`, `REQ-5.7`, `REQ-5.10`, `REQ-6.2`, `REQ-6.4`, `REQ-6.10`, `REQ-2.1`, flows `C2`, `C3`, `S1`, `B2` |
| Target role | system administrator configuring permissions; bound normal member using resulting runtime permission |
| Entry point | deployed login then `/systems/{systemId}/admin` role-management section, followed by normal login/system switch/runtime route |
| User job | configure a role permission matrix, batch-preview several actions, understand impact/conflicts/audit, and prove runtime/admin permission effects |
| Data contract | role id, module id, action decisions, field masks, data scope, affected members, permission version, preview log rows, trace/audit ids, runtime denial/readback |
| Permission contract | admin read/save/preview/audit succeeds; normal member system-admin and permission APIs are denied; runtime create/edit/list follows effective permission |
| State contract | empty, loaded, edited, saved, allowed preview, denied preview, hidden/masked fields, audit rows, normal runtime denied, normal admin denied, mobile containment |
| Copy contract | labels explain permission impact, denied action reason, field mask meaning, data scope, affected members, and recent audit without mojibake or generic success copy |
| Acceptance assertions | typecheck/build plus R91 script verifies source/deployed markers, API save/batch-preview/audit readback, affected member count, normal runtime/admin permission negatives, browser DOM markers, framework/static/coverage boundaries, and user signoff remains false |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, matrix/impact/audit placement, denied copy, and overflow only as visual evidence; they do not prove functional completion. API/readback assertions prove permission decisions, audit rows, and runtime enforcement. |

Acceptance script: `scripts/recovery-r91-permission-impact-preview-audit.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r91-permission-impact-preview-audit-result.json`
- Summary: `docs/evidence/recovery/r91-permission-impact-preview-audit-2026-07-07.md`
- Browser audit: `docs/evidence/recovery/screenshots/r91-permission-impact-preview-audit/permission-impact-preview-browser-audit.json`

Explicitly not complete if:

- Preview only checks one hardcoded action and cannot explain multiple action outcomes.
- Field mask/data-scope/denied-action details are persisted but not visible in the deployed permission workbench.
- Recent preview audit rows are not persisted/read back from `un_plat_permission_preview_log`.
- Normal members can call permission admin APIs or enter the system admin permission surface.
- Runtime behavior ignores the saved permission matrix or relies on frontend role ids instead of effective backend permission.
- Browser evidence only proves the route opens without checking source/deployed markers, API readback, preview logs, runtime permission effects, and permission negatives.
- User signoff is implied from engineering evidence; `gates.user_script_passed` must remain false.
## REC-P0-092 Workflow Designer Advanced Node Publish Impact Closure

Status: `in_progress` as deployed workflow-designer advanced-node and publish-impact engineering evidence only; user signoff remains open.

User role: system administrator configuring workflow; requester/approver normal members experiencing resulting workflow/todo/message behavior.

Business goal: close the next workflow gap after R91 by proving that advanced workflow configuration is understandable and enforceable from the deployed product, including timer/reject-transfer/field-update/external-API node configuration, publish impact, failure states, and runtime permission boundaries.

Requirement rows: `REQ-4.5`, `REQ-5.12`, `REQ-5.16`, `REQ-5.19`, `REQ-6.9`, `REQ-6.11`, `REQ-2.1`.

Journey rows: C4, B4, B5, AI2, J4, J8, J9, J11.

Frontend scope: extend the existing flow management/designer surface. Do not create a parallel workflow page. Add stable R92 markers for advanced node library, selected node properties, publish impact summary, simulation result, requester/approver runtime states, transfer/reject/timer failure states, and mobile containment.

Backend scope: extend existing flow APIs and coded workflow runtime services for deterministic advanced-node configuration/readback and publish-impact preview where required. Reuse existing flow, todo, message, audit, runtime record, OpenAPI/assistant boundaries; generated CRUD alone is not accepted evidence.

Generator scope: none. Generated entities are plumbing only; accepted behavior must be coded flow configuration/runtime behavior with deployed readback.

Data scope: systemId, moduleId, flowId, canvas version, node ids/types, timer/transfer/reject/field-update/external-API config, publish impact refs, simulation trace id, requester/approver member ids, todo/message ids, terminal status, failure reason, audit/trace ids, and deployed asset markers.

Permission rule: only system administrators can configure/publish/simulate flows; normal members cannot mutate flow admin APIs. Runtime requester/approver actions must be enforced by assigned member context, not frontend role shortcuts.

States: empty canvas, selected advanced node, invalid node config, publish-check blocker, publish impact, simulation pass/fail, requester submit, approver approve/reject/transfer, timer/escalation visible state, external API/field update failure, duplicate terminal action, normal admin denied, desktop/mobile containment, and user signoff boundary.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.5`, `REQ-5.12`, `REQ-5.16`, `REQ-5.19`, `REQ-6.9`, `REQ-6.11`, `REQ-2.1`, flows `C4`, `B4`, `B5`, `AI2` |
| Target role | system administrator configuring workflow; requester/approver normal members using the resulting workflow |
| Entry point | deployed login then `/systems/{systemId}/admin` flow management/designer section, followed by requester/approver runtime/todo/message routes |
| User job | configure advanced workflow nodes, understand publish impact and simulation output, then prove runtime todo/message/action outcomes and failure states |
| Data contract | flow canvas, advanced node properties, publish impact refs, simulation trace, requester/approver ids, todo/message ids, terminal status, failure reasons, audit ids |
| Permission contract | admin configure/publish/simulate succeeds; normal member flow-admin APIs are denied; runtime actions are restricted to assigned requester/approver context |
| State contract | empty, edited, invalid, publish-blocked, publish-impact, simulation pass/fail, pending todo/message, approve/reject/transfer terminal state, duplicate/action denial, mobile containment |
| Copy contract | visible labels explain node purpose, publish impact, simulation result, disabled/denied reasons, transfer/reject/timer states, and trace ids without mojibake or generic success copy |
| Acceptance assertions | typecheck/build plus R92 script verifies source/deployed markers, API flow save/publish-impact/simulation/readback, runtime todo/message terminal states, permission negatives, browser DOM markers, framework/static/coverage boundaries, and user signoff remains false |
| Screenshot evidence boundary | Screenshots prove visible hierarchy, advanced node/property/publish-impact placement, denied copy, and overflow only as visual evidence; they do not prove functional completion. API/readback assertions prove workflow configuration, runtime state transitions, permission decisions, and audit rows. |

Acceptance script: `scripts/recovery-r92-workflow-advanced-node-publish-impact.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r92-workflow-advanced-node-publish-impact-result.json`
- Summary: `docs/evidence/recovery/r92-workflow-advanced-node-publish-impact-2026-07-08.md`
- Browser audit: `docs/evidence/recovery/screenshots/r92-workflow-advanced-node-publish-impact/workflow-advanced-node-browser-audit.json`

Explicitly not complete if:

- Advanced nodes are only visible labels and are not saved/read back through the flow API.
- Publish impact does not show affected module/field/todo/message/runtime references.
- Simulation does not expose traceable pass/fail state.
- Runtime todo/message/record terminal states are not proven from requester and approver accounts.
- Normal members can call flow-admin APIs or perform actions outside their assigned runtime context.
- Browser evidence only proves the route opens without API/readback, runtime state, permission negatives, source/deployed markers, and signoff separation.
- User signoff is implied from engineering evidence; `gates.user_script_passed` must remain false.

## REC-P0-093 Platform Flow And Application IA Boundary Realignment

Status: `in_progress` as platform information-architecture and human-usability boundary correction; user signoff remains open.

Flow source: `temp_flow.md`, `temp_flow_persion.html`, `docs/framework/final-system-flow-blueprint.md`, `.cursor/knowledge/project-operating-rules.md`.

Flow ids: P1, P2, P3, S1, E1, E2, J1, J2, J5, J8, J9, J11.

User role: platform administrator, platform member, system administrator, and any user who must distinguish platform modules from system entry.

Business outcome: the platform workbench becomes understandable and usable as a real platform surface. Flow and Application are independent platform modules. Application shows visible application/authorization/configuration objects and actions in the current permission scope. It is not a system-entry page. The only platform-side system entry is the system-switch/workbench surface that creates `SystemSwitchContext` before system business data is opened.

Requirement rows: `REQ-4.1`, `REQ-4.2`, `REQ-5.15`, `REQ-6.1`, `REQ-6.2`, `REQ-6.3`, `REQ-6.11`, `REQ-2.1`.

Frontend scope: `frontend/src/features/platform/platformShell.ts`, `frontend/src/app/routes.ts`, and `frontend/src/styles.css`. Rework platform navigation and platform pages so the first screen hierarchy follows `temp_flow`: main modules are dashboard/workbench, Flow, Application, and Work; AI/todo/message/profile/system-switch are auxiliary entries. Replace `/platform/apps` system-switch reuse with an application list/configuration surface and stable DOM markers proving no system-entry cards are present. Strengthen `/platform/flow` as its own platform Flow surface with list/status/configuration affordances. Keep system switch only in `/platform` and explicit target-switch contexts.

Backend scope: none unless the existing APIs cannot support readback for platform application/authorization objects. For this R93 pass, do not invent a parallel backend model; surface the correct module boundary using current available system/application/OpenAPI/authorization state and link configuration to the owning system/admin surface only after system context is explicit.

Generator scope: none. Generated CRUD is not evidence for IA correctness.

Data scope: platform account role, available system count, current system context, application module rows, OpenAPI/system-app configuration affordances, platform Flow status rows, system switch cards, disabled reasons, and stable source/deployed markers.

Permission rule: platform application visibility/configuration follows platform role and current system membership. System business entry still requires `SystemSwitchContext`. Platform applications must not bypass member mapping, role snapshot, tenant, or data scope.

States: platform workbench with system-entry panel, platform Flow list/health/config state, platform Application list/config state, no current system for system-owned application configuration, no platform-admin permission, empty application list, disabled configuration action with reason, desktop/mobile hierarchy containment, and user signoff boundary.

Requirement Confirmation Contract:

| Contract item | Required content |
|---|---|
| Requirement source | `REQ-4.1`, `REQ-4.2`, `REQ-5.15`, `REQ-6.1`, `REQ-6.2`, `REQ-6.3`, `REQ-6.11`, `REQ-2.1`, flows `P1`, `P2`, `P3`, `S1`, `E1`, `E2` |
| Target role | platform administrator/member and system administrator distinguishing platform modules from system entry |
| Entry point | deployed login then `/platform`, `/platform/flow`, `/platform/apps`, and relevant system admin application configuration route |
| User job | understand where to enter systems, where to manage platform Flow, where to view/configure applications, and why application is not a system-entry shortcut |
| Data contract | available systems, current system, platform roles, application rows/affordances, flow rows/status, disabled reasons, source/deployed DOM markers |
| Permission contract | platform admin sees admin configuration affordances; normal platform member does not see platform-admin mutation; application configuration that belongs to a system requires explicit system context |
| State contract | loaded, empty, no-current-system, disabled/no-permission, configuration available, system-entry available only on workbench, mobile containment |
| Copy contract | labels explain Flow, Application, Workbench/System Entry, and configuration ownership without saying applications are systems or tenants |
| Acceptance assertions | typecheck/build plus R93 script verifies source/deployed markers, `/platform/apps` has no system-switch panel/cards and no direct switch action, `/platform/flow` has independent flow module markers, `/platform` keeps system-entry markers, static hierarchy/copy checks pass, and user signoff remains false |
| Screenshot evidence boundary | Screenshots prove hierarchy, readable copy, app/flow/system-entry separation, and overflow only as visual evidence; they do not prove final product completion. Source/API/browser assertions prove the boundary. |

Acceptance script: `scripts/recovery-r93-platform-flow-app-ia-boundary.ps1`

Evidence paths:

- Result: `docs/evidence/recovery/r93-platform-flow-app-ia-boundary-result.json`
- Summary: `docs/evidence/recovery/r93-platform-flow-app-ia-boundary-2026-07-08.md`
- Browser audit: `docs/evidence/recovery/screenshots/r93-platform-flow-app-ia-boundary/platform-flow-app-ia-browser-audit.json`

Explicitly not complete if:

- `/platform/apps` renders `createSystemSwitchPanel`, `data-platform-system-card`, or primary “enter system” actions.
- Application copy describes systems/tenants as the application list.
- Flow remains only a generic health card and does not read as a platform module.
- System entry appears in multiple platform modules instead of the workbench/system-switch surface.
- Navigation still makes AI/todo/message/profile compete with the main platform modules as equal primary work modules.
- Browser evidence only proves routes open without checking source/deployed markers, hierarchy, copy, and system-entry separation.
- User signoff is implied from engineering evidence; `gates.user_script_passed` must remain false.
