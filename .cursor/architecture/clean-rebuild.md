# Clean Rebuild Architecture

## Purpose

This project is now in clean rebuild mode. The previous implementation is archived under `.oldbk/restart-20260708-220451/` and may be read only as a reference.

Update 2026-07-09: clean rebuild is currently paused at requirements and engineering reconstruction. The active instruction is not to continue coding from the old P0 queue. Read `.cursor/architecture/requirements-rebuild.md` before using this file.

The active product must be rebuilt from these retained sources:

- `docs/user_requirement.md`
- `docs/design/prototypes/**`
- `docs/temp_flow.md`
- `docs/temp_flow_persion.html`
- `.cursor/knowledge/project-operating-rules.md`
- `.cursor/knowledge/failure-lessons.md`

## Hard Boundary

- Do not continue the old `RECOVERY-R*` implementation line.
- Do not promote old scripts, old releases, or old evidence as clean rebuild acceptance.
- Do not recreate page/API piles. Build from role journeys and product boundaries.
- Coding starts only after the requirements rebuild package, engineering architecture map, legacy inventory, committee review gate, task breakdown, and coding gate are accepted.
- The user has explicitly authorized continuing without further clarification unless a decision is impossible to infer from retained sources.

## Product Shells

The shell is context-aware:

- Platform context: platform statistics, platform Flow, platform Application authorization, platform work, platform AI entry, platform todo, platform messages, platform admin entry, profile.
- System context: current system statistics, configured business module groups, system Flow, system Application/OpenAPI authorization, system work, system AI entry, system todo, system messages, system admin entry, profile.

System switching must create a real context before system business data is shown.

## Navigation Contract

- `工作台`: context statistics and configured dashboard.
- `Flow`: workflow configuration and service orchestration; can bind internal business modules or external systems.
- `应用`: authorization valve for internal app communication and external service exposure; a Flow or system service must be linked through an Application before being exposed.
- `工作`: work dashboard, project tasks, normal tasks, daily reports.
- `AI`: user-facing AI assistance; model, policy, and authorization are configured in backend/admin.
- `待办`: configured action/reminder workbench, including approvals, reminders, today replies, and fixed backend-driven action categories.
- `消息`: messages related to the current user, including mentions, approvals, copied information, import/export, and major operation feedback.
- `后台`: entry to admin for basic info, org, roles, modules, flow, applications, dashboards, dictionaries, work config, application config, AI config, data sources, logs.
- `个人信息`: profile, switch system, logout.

## Business Module Contract

- Business modules are grouped by configured module groups.
- A group tab exposes modules under that group.
- A module owns fields, actions, pages, print templates, permissions, imports/exports, and runtime list/detail behavior.
- List-style pages use left-side tabs or left-side category navigation.
- Detail-style pages use right-side tabs/work areas and preserve list context.

## Flow Management Contract

Flow management must support:

- flow list, create/edit, versioning, publish checks;
- flowchart canvas, nodes, edges, conditions, approvers, data sources, actions;
- binding internal business modules or external services;
- approval node handling, todo/message generation, audit logs, and readback;
- exposure through Application authorization before external callers can use it.

## Acceptance Contract

A rebuild task is accepted only when it proves:

- frontend behavior;
- backend behavior;
- persisted data/readback;
- permission positive and negative paths;
- state transitions, todo/message/log side effects where relevant;
- responsive/browser evidence for user-facing surfaces;
- deployable or runnable verification appropriate to the current phase.

Engineering evidence never sets `gates.user_script_passed=true`; only user verification/signoff can do that.
