# Final System Flow Blueprint

Time: 2026-07-02 Asia/Shanghai

Status: active framework contract. This file is the flow source for the next implementation work.

## Purpose

The project must stop being developed as scattered pages or API slices. The running product must follow a clear human flow:

```text
browser entry
  -> auth state
  -> role and system context
  -> correct shell
  -> one primary user job
  -> persisted result
  -> visible readback
  -> permission and failure states
```

Every future coding task must map to one flow in this file before it changes source code.

## Global Entry Rules

| State | Allowed first screen | Forbidden behavior | Required data check |
|---|---|---|---|
| No access token | Login page only, with register and password recovery links | No platform page, no system page, no dashboard, no hidden demo route | local token absent and `/account/me` not called as a trusted session |
| Expired or invalid token | Login page with clear session-expired message | Rendering stale platform/system state | `/account/me` failure clears token and returns to login |
| Valid token, no system membership | Platform workspace with empty/system-access guidance | Direct system business route, fake default system | `/account/me` and system-switch options show no switchable system |
| Valid token, platform admin/root | Platform workspace; platform admin entry visible | Directly editing system business data from platform admin | platform roles include `PLATFORM_ADMIN` or `PLATFORM_ROOT` |
| Valid token, system member | Platform workspace plus authorized system switch, or direct system route after switch context | Entering system without `SystemSwitchContext` | switch context has `systemId`, `tenantId`, `systemMemberId`, roles, data scope, permission snapshot |
| Valid token, no target system member after SSO/direct link | No-member request page | Auto-granting business access | no-member access request is persisted and awaits admin review |

## Auth Flows

### Flow A1: Login

Entry: `/login` or first browser load without token.

User job: enter account/password or SSO method, then enter the correct post-login shell.

Required screens:

- Login form: account, password, optional MFA/captcha/SSO entry.
- Loading state while authenticating.
- Error state for wrong password, disabled account, expired SSO, rate limit, and network failure.

Success routing:

| Result | Next screen |
|---|---|
| Platform root/admin | `/platform` with platform admin entry visible |
| Platform member only | `/platform` with platform admin entry hidden |
| Has switchable systems | `/platform` with system switch cards and optional last-system entry |
| Direct system URL and switchable target | target `/systems/{systemId}/dashboard` after switch context |
| No system member mapping | `/no-member?systemId=...` or platform workspace guidance |

Data contract:

- Persist access/refresh token in the agreed storage.
- Read `/api/v1/account/me`.
- Read system switch options.
- Do not trust frontend-provided account id.

Acceptance evidence:

- Browser starts from no token and renders only login.
- Successful login produces correct shell for each role.
- Invalid login leaves user on login with clear error.

### Flow A2: Register And Create First System

Entry: `/register-with-system` from login page.

User job: create account and first business system, then become that system's super administrator.

Required screens:

- Account fields.
- First system name/code/tenant mode.
- Registration validation.
- Success transition into system admin first-use guide.

Success routing:

```text
register -> login/session -> create platform account -> create system
  -> bind creator as system super admin
  -> create default tenant/member/roles
  -> switch context
  -> /systems/{systemId}/admin first-use setup
```

Non-completion:

- Register only creates an account but no system.
- User lands on an empty platform page with no next action.
- Creator cannot enter system admin after registration.

### Flow A3: Password Recovery

Entry: `/forgot-password` from login page.

User job: verify identity, reset password, return to login.

Required screens:

- Account/email/mobile input.
- Verification code or recovery token step.
- New password form.
- Success state with return-to-login action.

Non-completion:

- Password recovery is a toast, drawer, or hidden action only.
- Reset succeeds but the user cannot log in with the new password.

### Flow A4: Logout And Session Expiry

Entry: profile/logout, expired token, refresh failure.

User job: leave the system cleanly and return to login.

Required behavior:

- Clear access/refresh token and local shell state.
- Next browser render shows login only.
- Direct system/platform URL without valid token redirects to login.

## Shell Model

The product has four shells. They must not share one mixed navigation.

| Shell | Role | Entry | Primary purpose | Forbidden content |
|---|---|---|---|---|
| Platform workspace | platform member/admin/root | `/platform` | select/create systems, platform flow/apps/work/todos/messages/profile | system admin configuration panels |
| Platform admin | platform admin/root | `/platform/admin` | platform information, systems, platform roles, SSO/model/ops/logs | business module fields/data editing |
| System business shell | system member/admin/super admin | `/systems/{systemId}/dashboard`, runtime/work/todos/messages/module routes | daily use of configured systems | platform admin actions and system config panels |
| System admin shell | system admin/super admin | `/systems/{systemId}/admin` and admin subsections | configure the current system | platform global config and normal business runtime clutter |

## Platform Workspace Flows

### Flow P1: Platform Dashboard

User job: understand available platform work and enter the right system or platform tool.

Required navigation:

- Dashboard.
- Flow.
- Applications.
- Work.
- Todo.
- Messages.
- Profile.
- System switch.
- Create system only for authorized platform users.
- Platform admin entry only for platform admin/root.

Data contract:

- `availableSystems` from switch options.
- Platform todos/messages from platform-scoped APIs.
- Platform messages must not directly open system business details without system switch.

Non-completion:

- Platform sidebar duplicates todo/message if they are already top-level actions.
- Platform member sees platform admin or create-system action without permission.
- System cards do not explain disabled/no-member state.

### Flow P2: Create System From Platform

User job: platform-authorized user creates a system and enters its first-use configuration.

Sequence:

```text
/platform -> create system -> system name/code/tenant mode
  -> backend creates system/tenant/member/super-admin mapping
  -> switch context
  -> /systems/{systemId}/admin first-use guide
```

Required readback:

- New system appears in platform switch options.
- Current switch context points to the new system.
- Creator has system super-admin role.

## Platform Admin Flows

### Flow PA1: Platform Operations

User job: manage platform-level systems, roles, identity, AI model authorization, health, deployment, cache, quotas, and logs.

Primary sections:

- Platform information.
- Organization/account overview.
- System lifecycle.
- Platform roles.
- Dashboard management.
- Unified identity and AI model authorization.
- Operations governance.
- Platform logs.

Rules:

- Platform admin can enable/disable systems and inspect health.
- Platform admin cannot configure a system's business fields or directly change business records here.
- Dangerous operations require reason, task/readback, traceId, and audit log.

## System Switch And Context Flows

### Flow S1: Enter System

User job: enter one authorized system and receive the correct member, tenant, role, and permission context.

Sequence:

```text
/platform system card or direct /systems/{id}/...
  -> POST /platform/system-switch
  -> SystemSwitchContext
  -> redraw system name, tenant, module groups, modules, work, todos, messages, permissions
  -> target system route
```

Required context fields:

- `systemId`
- `tenantId`
- `accountMemberBindingId`
- `systemMemberId`
- `effectiveRoleIds`
- `dataScope`
- `permissionSnapshotSummary`
- `messageTodoScope`

Non-completion:

- URL changes but old system modules/data remain.
- Platform account id alone opens system business data.
- Role ids/codes mismatch between admin config, preview, runtime nav, and backend permission checks.

### Flow S2: Tenant Switch

User job: switch tenant only when the current system is multi-tenant and the user has access.

Rules:

- Single-tenant systems hide tenant switching.
- Multi-tenant systems show tenant switch with disabled reasons when blocked.
- Switching tenant redraws modules, records, todos, messages, data scope, and permissions.

## System Business Flows

### Flow B1: System Dashboard

User job: see the current system's business overview and next useful actions.

Required content:

- Current system name and context.
- Configured dashboard widgets.
- Work/todo/message summary.
- Runtime module entry if modules exist.
- Setup guidance only for system admins when no published modules exist.

Forbidden:

- Generic welcome page with no data source.
- Admin-only configuration controls for normal members.

### Flow B2: Runtime Module Navigation

User job: use business modules configured by the system administrator.

Required layout:

```text
top module groups
  -> left modules in active group
  -> right list/form/detail for active module
```

Required runtime behavior:

- Only authorized module groups/modules visible.
- List supports search, filters, sort, paging, saved view, column settings, row click detail, batch actions by permission.
- Create/edit form renders fields by published schema.
- Detail opens on the side and keeps list context.
- Attachments, history, approval, related data, print, import/export are reachable without permanent page stacking.

Non-completion:

- Runtime shows admin configuration controls.
- Import/export panels are always mounted below the list.
- Row action buttons repeat the row-click detail action.
- Hidden fields leak in list, form, detail, export, or OpenAPI.

### Flow B3: Work Management

User job: manage daily work inside the current system.

Required tabs:

- Dashboard.
- Project tasks.
- Plain tasks.
- Daily reports.

Rules:

- List and kanban are mutually exclusive views.
- Project tasks and plain tasks are not merged into one ambiguous list.
- Daily report auto draft requires user confirmation before submission.
- Work data is scoped by system, tenant, and member.

### Flow B4: Todo Center

User job: process work assigned to the current system member.

Required layout:

- Left todo type/status tree or filter.
- Right list.
- Row click opens target object or task detail.
- Pending and handled states are separated.

Rules:

- Requester does not see approver-only pending todo.
- Approver can process assigned approval.
- Duplicate approval action is idempotent and visibly handled.

### Flow B5: Message Center

User job: read system messages and jump to allowed targets.

Required behavior:

- Filters for read/archive/type/template/system/tenant where applicable.
- Mark read, mark all read, archive.
- Archived messages leave the active stream and appear in archived filter.
- Platform messages and system messages stay separate.

## System Admin Flows

### Flow C1: First-Use Configuration Guide

User job: after creating or entering a new system, configure enough structure for normal members to use it.

Required steps:

1. System information and access address.
2. Organization and members.
3. Roles and permissions.
4. Module groups and modules.
5. Fields and dictionaries.
6. Page/list/detail/action configuration.
7. Workflow and messages.
8. Work configuration.
9. OpenAPI/external applications.
10. Publish check and runtime preview.

Rules:

- This guide is not a marketing stepper. Each step links to a real admin surface and shows readback status.
- A step is complete only when the relevant backend state exists and is readable.
- The guide must show what blocks publishing.

### Flow C2: No-Code Module Configuration

User job: configure a business module from zero to usable runtime.

Sequence:

```text
module group -> module -> fields -> list scene -> form/detail/page -> actions
  -> permissions -> publish check -> publish
  -> normal runtime schema/navigation readback
```

Required pages:

- Module group/menu configuration.
- Module lifecycle.
- Field designer.
- Dictionary binding.
- Page designer.
- Action/import/export/print configuration.
- Permission preview.

Non-completion:

- Module, field, dictionary, menu, and permission panels are rendered as one broad pile.
- Admin readback passes but normal runtime nav/schema does not reflect the configuration.
- Permission preview disagrees with frontend runtime or backend denials.

### Flow C3: Role Permission

User job: define what a role can see and do.

Required permissions:

- Menu/module visibility.
- Action permission.
- Field visible/edit/export permission.
- Data scope.
- Admin entry permission.
- Workflow node field permission where applicable.

Required proof:

- Preview explains why allowed/denied.
- Normal member runtime matches preview.
- Backend denies forbidden direct API calls.

### Flow C4: Workflow Configuration

User job: configure and publish workflow behavior for business records.

Required:

- Flow canvas with real nodes/edges/condition labels.
- Node-specific properties.
- Publish check.
- Simulation.
- Runtime submit creates instance/todo/message.
- Approval updates record, history, todo, message, and audit.

### Flow C5: Integration, AI, And Operations Configuration

User job: configure external and automation capabilities safely.

Required:

- External application credentials as SecretRef, scope, status, rotation, call logs.
- OpenAPI docs or callable endpoint list.
- AI Agent policy by module/field/action/data scope, confirmation, audit.
- Data source check/publish.
- Import/export task rules and result files.
- Logs and health traces.

## External Integration Flows

### Flow E1: OpenAPI Caller

User job: external system calls authorized platform/system capabilities.

Required:

- App credential is SecretRef/versioned.
- Scope controls module/action/field/data access.
- Wrong secret, read-only scope, missing idempotency, and rate limit produce clear errors and logs.
- Success/failure call logs are searchable.

### Flow E2: Import/Export

User job: move data in and out safely.

Import sequence:

```text
download template -> upload file -> field mapping -> precheck
  -> error file if needed -> confirm import -> task progress -> result readback
```

Export sequence:

```text
choose scope/fields/format -> permission filter -> async task
  -> result file -> download -> audit log
```

## AI Flows

### Flow AI1: Platform Agent

Allowed scope:

- platform authorization
- platform logs
- platform tasks/messages
- model quota
- system health
- system switch guidance

Forbidden:

- direct system business record read/write without system member context.

### Flow AI2: System Agent

Allowed scope:

- current system/member/role/data-scope context only
- module data query/statistics
- task extraction
- daily report draft
- confirmed write operations

Required:

- policy version
- model authorization version
- prompt version
- permission snapshot
- preview/confirm/reject
- audit trace

## Operations Flows

### Flow O1: Release And Health

User job: deployer/operator can package, start, restart, stop, and verify the release.

Required:

- release package exists
- frontend assets match deployed assets
- health requires database/schema/Redis UP
- server scripts support start/stop/restart/status/health
- logs and trace are available

### Flow O2: Maintenance

User job: operator can understand backup, restore drill, archive restore, rollback, cache, quota, and rate-limit state.

Required:

- dangerous action reason
- background task id
- traceId/auditLogId
- rollback boundary
- success/failure readback

### Flow O3: Logs And Audit

User job: operator, platform administrator, and scoped system administrator can trace real operations without reading server consoles.

Required:

- login, system switch, permission denial, business operation, workflow, OpenAPI, import/export, AI, and maintenance logs are searchable.
- log list and detail expose traceId/auditLogId, operator, scope, object, action, result, reason, and created time.
- platform logs and system logs stay separated by scope.
- normal members cannot inspect platform logs or unrelated system logs.
- empty, filtered-empty, failed-query, and denied states have clear visible reasons.

### Flow O4: Release Script And Recovery Boundary

User job: deployer/operator can package, start, stop, restart, verify, and diagnose the deployable release.

Required:

- package output includes backend jar, external config, server script, frontend assets, logs directory, and upload/data directories.
- deployed frontend assets match the release `index.html`.
- start/restart waits for database, schema, and Redis health to be UP.
- stop releases the configured port.
- rollback, backup, restore drill, archive restore, cache clear, quota, and rate-limit operations require reason, task id, traceId, result, and rollback boundary.

Non-completion:

- build PASS is treated as deployable PASS.
- server script only checks process existence.
- frontend asset mismatch is ignored.
- dangerous operations show generic success without task/readback/audit evidence.

## Development Order From This Blueprint

The next development work must follow this order unless a later audit proves a higher blocker:

| Order | Flow | Why |
|---|---|---|
| 1 | A1-A4 auth and entry guard | If entry is wrong, every later page can appear stacked or exposed. |
| 2 | Shell model plus P1/S1 | The product must route users to the right layer before feature work. |
| 3 | C1 first-use guide | New systems must have a real setup path instead of an empty backend. |
| 4 | C2/C3 no-code configuration | The core promise is configurable systems. |
| 5 | B1/B2 normal runtime | Admin configuration must become usable business pages. |
| 6 | C4/B4/B5 workflow, todo, message | Automation must close as one role journey. |
| 7 | B3 work management | Daily work must be coherent in the system shell. |
| 8 | E1/E2 external integration and import/export | External service and data movement must be safe and traceable. |
| 9 | AI1/AI2 | AI must respect platform/system boundaries and confirmation. |
| 10 | O1/O2 operations | The system must be deployable and maintainable. |

## Task Rule

A task is valid only if it names:

- the flow ids from this file
- the target role
- the exact entry URL or navigation action
- the user job
- required frontend surface
- required backend/data/readback
- permission positive and negative cases
- states and copy that must appear
- generated plumbing versus coded behavior
- deterministic evidence script

If a proposed task cannot fill these fields, it is not ready for coding.

## Current Immediate Task

The immediate next task is not another page patch. It is:

`REC-P0-060 Flow Blueprint And Rebuild Contract Lock`

Expected output:

- this flow blueprint remains complete enough to drive task decomposition
- session state points to flow-first execution
- framework audit checks the blueprint exists and the active next task references it
- the next coding task is generated from the development order above

`gates.user_script_passed` remains `false`.
