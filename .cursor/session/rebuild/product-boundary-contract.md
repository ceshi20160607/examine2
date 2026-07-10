# Product Boundary Contract

## Status

Task: `REQ-R0-001 Product Boundary Contract`

Status: leader draft complete

This file is the single source for the current product boundary during requirements rebuild. Other files should reference this file instead of copying long boundary text.

Coding remains closed. This document is for product boundary and engineering split only.

## Source Files

- `docs/user_requirement.md`
- `docs/temp_flow.md`
- `.cursor/session/rebuild/leader-ui-function-review.md`
- `.cursor/session/rebuild/ui-system-interaction-contract.md`
- `.cursor/knowledge/project-operating-rules.md`
- Latest user directive on 2026-07-09

## Product Position

`unexamine` is a configurable business system platform for internal enterprise operation scenarios.

It is not:

- a marketing site;
- a low-code visual demo only;
- an API/debug console;
- a single fixed CRM/OA product;
- a platform admin tool that can directly override system business rules.

The product must support two layers:

- Platform layer: manages platform identity, systems, global policy, platform Flow, platform Application authorization, platform work, platform AI, platform todo/message, platform admin, and profile.
- System layer: the runtime and admin space of one configured business system. It manages that system's modules, fields, pages, roles, members, Flow, Application authorization, work, AI, todo/message, admin, and business data.

## Context Boundary

Every top-level product module runs under exactly one context.

### Platform Context

Platform context is entered by platform identity after login.

It can show and operate:

- platform workbench statistics;
- platform Flow and platform Flow runtime results;
- platform Application authorization for platform-level service exposure;
- platform work, platform todo, platform messages, platform AI;
- platform admin capabilities such as system lifecycle, platform organization, platform roles, global config, platform logs, health, version, and deployment;
- profile and account security.

It cannot directly operate:

- fields, pages, records, approvals, attachments, comments, exports, dashboards, or permissions inside a specific business system;
- system todo/message targets that require a `systemMemberId`;
- business details from a system message without first switching into that system.

When a platform user enters a system, the product must create a `SystemSwitchContext`.

Required context data:

- `systemId`;
- `tenantId` when the system is tenant-scoped;
- `systemMemberId`;
- current roles;
- data scope;
- permission snapshot;
- effective system shell/navigation.

If no system member mapping exists, the result is a no-member access request flow, not silent authorization and not direct business entry.

### System Context

System context is entered only after a valid `SystemSwitchContext` exists.

It can show and operate:

- current system workbench statistics and configured dashboard;
- system module groups and business module runtime;
- system Flow and Flow runtime readback;
- system Application authorization for external access to current system capabilities;
- system work, system todo, system messages, system AI;
- system admin capabilities such as system info, tenants, members, roles, modules, fields, pages, dictionaries, dashboards, Flow, applications, data sources, logs, SSO, and AI policy;
- profile and account security.

It cannot operate:

- platform global authorization, platform identity sources, global model policy, platform logs, platform health, or other platform-wide config;
- another system's modules or business records;
- another tenant's data unless the current `TenantSwitchContext` grants that data scope.

When a multi-tenant system switches tenant, the product must create a `TenantSwitchContext`.

Required context data:

- `tenantId`;
- tenant roles;
- tenant data scope;
- whether tenant switching is allowed;
- disabled reason when not switchable.

### Context Invariants

- Platform identity and system member identity are different operational identities.
- Platform admin is not a superuser for system business data by default.
- System admin is not a platform admin.
- Platform messages and system messages are separate streams.
- Platform todo and system todo are separate action workbenches.
- Runtime business navigation and admin configuration navigation must remain separate.
- A page name is not enough for engineering work. Every task must state context, role, data object, permission boundary, state/readback, side effects, and evidence.

## Domain Boundary Matrix

| Domain | User Result | Engineering Meaning | Not This |
|---|---|---|---|
| 工作台 | The user lands in the correct operational overview for the current context, sees configured statistics, key status, recent work, risks, and jump targets. Platform workbench explains platform/system authorization status; system workbench explains current system business status. | A context-scoped dashboard shell. Data is assembled from published dashboard/stat config, permission-filtered statistics, task/todo/message summaries, and stable jump targets. Must read by platform context or `SystemSwitchContext`; system dashboard must include `systemId` and tenant scope when relevant. | Not a fixed welcome page; not a decorative card wall; not a business module list replacement; not a marketing home page. |
| Flow | The user can configure, publish, run, monitor, and read back workflows or automations in the current context. Platform Flow serves platform operations and platform service exposure; system Flow serves system business modules, approval, automation, and external integration. | A workflow orchestration domain with list, canvas, node-specific properties, draft/publish/check states, runtime instances, logs, traceId, retry/compensation, and permissioned bindings to modules, data sources, external APIs, or Application scopes. | Not only approval JSON; not a generic debug workflow page; not a hidden business-data bypass; not a generic drawer without node-specific fields. |
| 应用 | The user creates and manages an authorization entry that allows internal service communication or external systems to call authorized platform/system capabilities. A Flow or service must link to an Application before external exposure. | An Application is an authorization valve/gateway object. It owns app identity, status, scope, secret reference/version, callback/IP/rate-limit/signature policy, call logs, audit, and published access boundaries. Platform Application and system Application are separate context-scoped objects. | Not a business module parent; not the runtime system switch; not a module group; not the user's daily business entry; not a replacement for OpenAPI docs. |
| 工作 | The user manages daily execution: work overview, project tasks, normal tasks, daily reports, assignments, comments, attachments, acceptance, and personal/team progress. | A work-management domain with task objects, project task objects, daily report objects, status fields from dictionary/config, list/kanban views, permissions, reminders, message/log side effects, and AI-assisted draft extraction with human confirmation. | Not isolated task mini pages; not a generic todo list; not direct mutation of business records without permission and readback. |
| AI | The user receives AI assistance for parsing, searching, analysis, task/report drafts, and assisted expansion inside the allowed platform/system scope. | A permissioned assistant surface plus admin-managed policy. Requires model authorization, `AgentPolicyScope`, prompt/tool policy, field/action/data scope, desensitization, external-send limits, audit logs, traceId, confirmation before writes, and failure states. Platform AI cannot read system business data without system switch. | Not a permission bypass; not a hidden automation writer; not a model playground; not a direct writer without user confirmation and readback. |
| 待办 | The user sees actionable items that need processing and can complete, transfer, delay, batch process, or jump to the target object when permitted. | A context-scoped action workbench generated by Flow, work, reminders, imports/exports, AI confirmations, and configured scenes. Requires type, source, target, due time, state transitions, permission checks, processing result, message/log side effects, and readback. | Not generic notifications only; not a business module sidebar; not duplicate buttons when row click is the main action; not cross-context business access. |
| 消息 | The user receives a current-user message stream and can read, filter, archive, mark all read, and jump to permitted targets. | A template-driven message domain with platform/system separation, delivery log, channels, variables, dedupe key, read receipt, quiet hours, retry, jump target, target permission handling, and state readback. | Not todo processing; not duplicate "view/open/enter" buttons inside every card; not a platform message that directly opens a system business detail. |
| 后台 | Authorized admins configure and operate the current context's management capabilities. Platform admin manages platform/global objects; system admin manages current system configuration and published runtime. | A separate admin shell. Platform admin covers system lifecycle, organization, roles, global policy, health, logs, deployment. System admin covers system info, tenants, members, roles, module groups, modules, fields, pages, Flow, applications, dashboards, dictionaries, data sources, SSO, AI policy, imports/exports, and logs. Config objects need draft/publish/check/readback states. | Not runtime business navigation for normal members; not mixed into list/detail business pages; not a toast-only config center. |
| 个人信息 | The user manages personal profile, account security, message preferences, login devices, logout, and allowed admin entry visibility. System entry uses top-level system switch, not profile shortcuts. | An account/profile domain tied to current platform account. It may expose context-specific admin entry only by permission, but must not create system authorization or bypass `SystemSwitchContext`. | Not an authorization-system list; not a hidden system switch; not a platform/system permission editor. |
| 业务模块 | The user works with configured business records through module group navigation, dense lists, forms, right-side details, actions, import/export, attachments, comments, print, approval, and logs. | A system-context runtime domain. Module groups define top navigation grouping; modules own fields, pages, actions, list scenes, detail tabs, print templates, workflow bindings, record permissions, field permissions, imports/exports, audit, and published versions. Records are scoped by `systemId`, `tenantId`, and `moduleId`. | Not Application; not OpenAPI app; not platform data; not a generic CRUD table without configured fields, permissions, readback, and side effects. |
| flow管理 | Admin users design, validate, publish, stop, version, and inspect Flow definitions and runtime behavior. | The management side of Flow. Includes flowchart/canvas, related modules or external systems, approvers, node-specific properties, data sources, actions, condition labels, publish checks, simulation, versioning, runtime readback, logs, traceId, retry and compensation. Platform flow management and system flow management are separate. | Not a generic details drawer; not only runtime approval handling; not reusable approval-node config for all node types. |

## Application Naming Normalization

Old requirement text used "应用" in several ways. Future requirements, design, tasks, route names, and API contracts must normalize them as follows.

| Old Usage | Normalized Term | Rule |
|---|---|---|
| "应用" as a business container that owns modules | 模块组 + 业务模块 | Business modeling is `模块组 -> 模块 -> 字段/页面/动作/权限/数据`. Do not create modules under Application. |
| "应用首页" or "应用运行台" as daily user entry | 系统工作台 + 模块组导航 + 业务模块运行态 | The daily runtime entry is the current system shell, not an Application. |
| "应用配置中心" as system configuration | 系统后台 | System config belongs to system admin. Use "系统后台" or specific admin domain names. |
| "应用管理" for external access | 对外应用 / Application authorization | Keep "应用" only when it means authorization valve/gateway. UI copy should say "对外应用" or "应用授权" when ambiguity is likely. |
| "授权系统" shown inside profile | 系统切换 | Entering a system must use top-level system switch and create `SystemSwitchContext`; profile must not duplicate it. |
| Platform "应用/授权系统" | 平台应用 / 平台授权 | Platform Application manages platform-level authorization and may expose platform Flow through scopes. It does not switch into system business. |

Canonical definition:

> Application means a context-scoped authorization valve/gateway for internal service calls or external access. It is not the parent of modules and not the business runtime entry.

Required engineering naming:

- Use `Application` only for authorization/gateway objects.
- Use `ModuleGroup` for runtime top navigation grouping.
- Use `BusinessModule` or `Module` for configured business objects.
- Use `SystemSwitchContext` for entering a system.
- Use `SystemAdmin` for system configuration.
- Use `PlatformAdmin` for platform configuration.

## Shell And Navigation Boundary

The product has four shells:

| Shell | Entry | Main Users | Contains | Must Not Contain |
|---|---|---|---|---|
| Platform workbench | Login into platform context | platform admins, platform members, system owners before switch | 工作台, Flow, 应用, 工作, AI, 待办, 消息, system switch, create system, profile | system business module sidebars, system record details, system approval handling without switch |
| Platform admin | platform admin entry by permission | platform admin/root | system lifecycle, platform org, platform roles, global config, platform Flow/Application config, health, logs, deployment | system fields/pages/business records |
| System runtime | valid `SystemSwitchContext` | system admins and members | 工作台, module groups, business modules, Flow runtime where exposed, 工作, AI, 待办, 消息, profile | platform admin config, other systems' data |
| System admin | system admin entry by permission | system owner/admin | system info, tenants, members, roles, module groups, modules, fields, pages, dictionaries, Flow, applications, dashboards, data sources, SSO, AI policy, logs | platform global config, normal member runtime clutter |

## Layout Contract

Primary source: `.cursor/session/rebuild/ui-system-interaction-contract.md`.

Summary:

- The product uses a dense enterprise operations visual system: dark top navigation, compact hierarchy, and table-first surfaces.
- List-dominant surfaces use dense table-first layouts with left-side tabs or left-side category navigation where relevant.
- Details use a right-side work area or drawer while preserving list context.
- Row click opens detail only for real data/business lists.
- Row buttons only perform actions distinct from opening detail.
- Admin pages use left tree/category plus right table/form when managing structured configuration.
- Runtime business navigation must not be mixed into admin settings.
- Every future UI task must define loading, empty, no-permission, validation error, backend error, disabled reason, draft, publish-check failed, published, stopped/disabled, async running, and success readback states.

## Engineering Split Rules

Future engineering tasks must map each page/action to this contract before coding.

Required fields:

- context: platform or system;
- shell: platform workbench, platform admin, system runtime, or system admin;
- role and permission boundary;
- data object and owner domain;
- state model and readback point;
- side effects: todo, message, log, audit, task, file, or none with reason;
- list/detail layout pattern;
- positive and negative permission examples;
- persistence and publish/version requirement when config is involved;
- evidence required for acceptance.

No coding task may start from a page name alone.

## Open Conflicts To Resolve

- Historical documents and prototypes may still use "应用" for runtime entry or module parent. This contract overrides that usage.
- Existing prototype is static and cannot prove real behavior. It can provide IA evidence only.
- Historical rebuild evidence cannot prove current workspace completion.
- Any generic drawer/default detail without domain-specific fields, permissions, states, failure feedback, and traceId is a design gap and cannot enter coding.

## Coding Impact

No backend, frontend, or SQL implementation may begin during requirements rebuild.

When coding later opens, every task must first map to one or more domains in this contract and state context, role, data object, permission boundary, state/readback, side effects, and evidence.
