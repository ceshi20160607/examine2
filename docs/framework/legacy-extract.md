# Legacy Extract

Time: 2026-07-10 Asia/Shanghai

## Decision

The useful parts of `.oldbk/` have been extracted into this document.

The legacy archive is no longer an execution dependency and should not remain in the workspace. New implementation should start from the current requirement, current database design, generated base code, and explicit business services.

## What Is Kept

### 1. Product Flow Groups

Use these flow groups as a checklist when rebuilding current flow documents:

| Group | Meaning |
|---|---|
| A1-A4 | Login, register/create first system, password recovery, logout/session expiry |
| P1-P2 | Platform dashboard and create-system flow |
| PA1 | Platform operations/admin flow |
| S1-S2 | System switch and tenant switch context |
| B1-B5 | System dashboard, runtime module navigation, work management, todo, message |
| C1-C5 | First-use guide, no-code module configuration, role permission, workflow configuration, integration/AI/ops configuration |
| E1-E2 | OpenAPI caller and import/export |
| AI1-AI2 | Platform Agent and System Agent |
| O1-O4 | Release/health, maintenance, logs/audit, release script/recovery boundary |

The important rule is that every development task must map to a role journey and one or more flow groups before coding.

### 2. Layer Boundaries

Keep these boundaries:

- Platform layer manages platform accounts, systems, authorization, platform Flow, platform applications, global operations, logs, health, and deployment.
- System layer manages one configured system: members, departments, roles, permissions, module groups, modules, fields, pages, menus, dictionaries, workflow, dashboard, OpenAPI, logs, and AI policy.
- Runtime layer is normal daily business use: dashboard, module group navigation, lists, forms, detail, files, import/export, print, workflow, todo, messages, and work.
- External integration layer is scoped OpenAPI access through application credentials, SecretRef, logs, idempotency, and rate limits.

Do not mix platform application with system external application. Do not use application as the parent container for modules. Module group is the runtime navigation group.

### 3. Database Domains

Use these old SQL domains as planning references, not as copy-paste schema:

- platform identity: account, system, tenant, department, member, account-member binding, SSO binding, no-member request, login log
- permission/RBAC: role, role member, permission version, role permission, field permission, data scope, deny policy, permission preview, effective snapshot
- module configuration: module group, module definition, field definition, dictionary type/item, list scene, action config, import/export config, print template, work config, publish version
- dynamic runtime: record, value, index, child row, relation, history, draft, attachment, sequence, file storage/version/access/recycle/policy
- workflow approval: flow definition, node, edge, snapshot, instance, approval action log, simulation log, approval task
- message/todo/log: todo, notification template, target, message, delivery log, export task, business audit log
- work management: project, task, collaborator, comment, event, relation, kanban config, daily report, daily report source, calendar item, auto source rule
- secret/OpenAPI/AI: SecretRef, secret rotation job, identity provider, system SSO policy, OpenAPI app/call log, model authorization, agent policy/session/audit/confirmation
- async/ops: async task, idempotency key, health check, feature flag, quota, rate limit policy, backup/restore, archive/restore, deployment, API cache policy

### 4. Generation Strategy

Keep the generation strategy, not the old generator code:

- database design comes first
- generate base persistence code after schema is stable
- generated base code includes entity, mapper, mapper XML, service, and service implementation where appropriate
- generated code belongs to the base/infrastructure layer
- coded business behavior belongs to manage/application/service/controller layers
- generated CRUD is never accepted as product completion
- do not generate product behavior, page flow, permissions, workflow, messages, logs, import/export, OpenAPI, or operations semantics from table shape alone

### 5. Backend Module Reference

Old backend module names can guide domain separation:

- `examine-core`
- `examine-plat`
- `examine-module`
- `examine-flow`
- `examine-upload`
- `examine-app`
- `examine-web`
- `examine-generator`

This is a naming/reference list only. The new architecture contract may rename or merge modules if the current requirement and database design justify it.

### 6. Frontend Contract Areas

Old page contracts are summarized into these current frontend areas:

- auth and system entry
- platform center
- system member/RBAC/dictionary
- application/module/field configuration
- dynamic schema renderer
- runtime workbench
- flow workbench
- file/export
- OpenAPI/audit/operations
- shell navigation and visual hierarchy
- product usability cleanup

Do not copy old pages. Use this list only to ensure the new frontend architecture does not miss a surface.

### 7. Acceptance Lessons

Keep these engineering lessons:

- User signoff is separate from engineering evidence.
- Screenshots prove layout and visible copy only; they do not prove function.
- A row marked `PARTIAL` is not complete.
- Generated CRUD is not a completed product journey.
- Role journeys must include real entry, persisted readback, permission positives/negatives, states, errors, and operational trace.
- Platform/system/runtime shells must not leak actions or text from another role.
- Old PASS reports are history, not current acceptance.

## What Is Discarded

Discard these from future work:

- old frontend source pages
- old backend business implementations
- old recovery scripts as execution plan
- old screenshots and browser profiles
- old generated base code
- old evidence JSON as current proof
- old task queue names as current delivery plan

## Current Rebuild Principle

Rebuild from:

```text
current requirement
  -> current flow contract
  -> current database design
  -> generated base code
  -> coded business behavior
  -> browser/API/readback/permission/state verification
```

Do not rebuild from old code.

