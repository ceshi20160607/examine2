# Current Engineering Framework

Time: 2026-07-10 Asia/Shanghai

## Current State

The current workspace is in a reset/rebuild state.

What exists in the main workspace:

- `docs/user_requirement.md`: broad product requirement source
- `docs/temp_flow.md` and `docs/temp_flow_persion.html`: flow references, but they need encoding review
- `docs/design/prototypes/index.html`: prototype reference
- `docs/user_setting.md`: local environment/account settings
- `docs/framework/legacy-extract.md`: extracted useful legacy information

What does not yet exist in the main workspace:

- active backend source tree
- active frontend source tree
- active database schema under current ownership
- active requirement coverage ledger
- active task cards
- active verification scripts

So the engineering framework is now defined at the documentation/control level, not yet at implementation level.

`.oldbk/` has been extracted and removed to avoid keeping a confusing second project tree.

## Final Product Target

The target is a configurable business system platform that real teams can use.

It must support:

- platform account, login, registration, password recovery, SSO, and account security
- platform workbench, platform Flow, platform application/authorization, platform tasks, platform messages, and platform operations
- system creation, system switching, tenant switching, and system member mapping
- system backend configuration for modules, module groups, fields, pages, menus, roles, permissions, dictionaries, workflow, dashboard, data sources, OpenAPI, logs, and AI Agent
- runtime business use for normal members: dashboard, module group navigation, lists, forms, details, files, import/export, workflow, todo, messages, work, and mobile-friendly usage
- external integration through scoped OpenAPI applications, SecretRef-style secrets, logs, idempotency, and permission limits
- operations and delivery: health, deployment, backups, rollback, audit logs, errors, rate limits, cache, and documentation

The refactor is not a rewrite of the old project page by page. It is a rebuild around this product target.

## Requirement Understanding

The project is a full refactor of `unexamine` into a configurable business system platform.

The product must let a non-developer administrator configure a business system and let normal users complete daily business work in that configured system. It must also let external systems integrate safely through scoped APIs, and let operators deploy, monitor, recover, and audit the platform.

The requirement is not just low-code CRUD. The required product includes configuration, runtime use, workflow, permissions, messages, files, import/export, OpenAPI, AI assistance, operations, security, performance, documentation, and upgrade/recovery rules.

Passing local scripts, creating pages, or reproducing old code is not enough.

## Core Boundaries

### Platform Layer

The platform layer manages the product itself:

- platform accounts and roles
- system creation and lifecycle
- platform authorization
- platform Flow
- platform application/authorization surfaces
- global configuration
- platform messages, todos, tasks, logs, health, and deployment

The platform layer must not directly mutate system business data without an explicit system context and authorized operation.

### Custom System Layer

The custom system layer manages one configured business system:

- system information
- tenant mode
- system members, departments, roles, and permissions
- module groups, modules, fields, pages, menus, dictionaries, workflow, dashboard, data sources, OpenAPI, logs, and AI Agent policy

All system-level behavior must run under a `SystemSwitchContext` containing `systemId`, `tenantId`, `systemMemberId`, roles, data scope, and permission snapshot.

### Runtime Layer

The runtime layer is for daily business work:

- module group navigation
- module lists, forms, detail drawers/pages, files, comments, history, import/export, print, workflow, todo, messages, work, and dashboards

Runtime pages must not expose admin configuration surfaces to normal members.

### External Integration Layer

External application/OpenAPI is a controlled access path into a system:

- app identity
- secret reference and rotation
- scope
- rate limit
- idempotency
- audit log
- failure feedback

It is not the parent container for business modules.

## Product Concepts That Must Stay Fixed

- Module group is the runtime navigation group: top group plus left module list.
- Module is the configurable business object.
- Application in the system backend means external access application, not module container.
- Platform Application is separate from system external application.
- Platform Flow is separate from system workflow configuration.
- Platform messages and system messages are separate.
- Platform todos and system todos are separate.
- System switching and tenant switching must be explicit context objects.
- User acceptance is separate from engineering evidence.

## Development Rule

Every implementation task must follow this order:

```text
requirement source
  -> role journey
  -> product boundary
  -> task scope
  -> generated-vs-coded boundary
  -> implementation
  -> browser/API/readback/permission/state evidence
  -> ledger update
```

Do not start coding from a vague page name, screenshot, or old file path.

## Code Generation Rule

The implementation should be database-design first.

After the database plan is clear, the base persistence layer should be generated with MyBatis Generator or MyBatis-Plus Generator. Generated base code is infrastructure only.

Business behavior must be coded separately in explicit service/application layers. Generated CRUD must not be treated as product completion.

Legacy code is not a migration source. It is faster and cleaner to rebuild from clear requirements, database planning, and generated base code than to patch the old project.

## Engineering Nodes

| Node | Name | Purpose | Current Status |
|---|---|---|---|
| E0 | Framework Reset | Establish active rules and stop relying on old recovery state. | Completed |
| E1 | Source Consolidation | Summarize useful legacy references into current docs and rebuild the requirement ledger. | In progress |
| E2 | Architecture Contract | Define backend modules, database domains, API layers, frontend app shell, and verification strategy. | Pending |
| E3 | Backend Foundation | Build account/system/tenant/context/permission foundations. | Pending |
| E4 | Frontend Foundation | Build login, platform shell, system shell, routing, permission rendering, and design system. | Pending |
| E5 | No-Code Configuration | Build module groups, modules, fields, pages, menus, dictionaries, roles, and publish flow. | Pending |
| E6 | Runtime Business Use | Build lists, forms, detail, files, import/export, print, dashboard, work, and mobile usage. | Pending |
| E7 | Workflow/Todo/Message | Build workflow configuration, runtime approval, todo, messages, and terminal states. | Pending |
| E8 | Integration/AI/Ops | Build OpenAPI, SecretRef, AI Agent policy, logs, health, deployment, backup, and rollback. | Pending |
| E9 | Acceptance Closure | Run role-journey verification, coverage audit, and user signoff. | Pending |

## Immediate Next Node

The next engineering node is E1 Source Consolidation.

Minimum E1 output:

- current requirement coverage ledger: pending
- current final flow blueprint: pending
- legacy reference index: done in `legacy-extract.md`
- current architecture contract draft: pending
- decision list for what is reused, redesigned, or discarded from the legacy extract: done in `legacy-extract.md`

Only after E1 is complete should implementation begin.
