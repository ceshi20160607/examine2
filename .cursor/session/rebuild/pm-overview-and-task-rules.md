# PM Overview And Task Rules

## Updated User Directive

The user wants a normally usable system, developed in an engineering way:

1. Understand requirements from retained sources.
2. Communicate and clarify requirement details through durable PM overview artifacts, without stopping for avoidable questions.
3. PM owns the whole project view and confirms boundaries before implementation slices.
4. Split work into small executable tasks.
5. Each task must code, verify, write evidence, and update the next task.
6. Final target is a clear, usable system with reasonable functions and pages that people can use.

## Requirement Understanding

The active product is a configurable enterprise business system with context-aware platform/system shells.

- 工作台: current context statistics and dashboard, configurable in backend/admin.
- Flow: workflow/service configuration, binding internal business modules or external third-party systems.
- 应用: authorization gateway for internal app communication and external service exposure; Flow/system service external exposure must be linked through an Application first.
- 工作: work dashboard, project tasks, normal tasks, daily reports.
- AI: AI-assisted parsing/expansion entry, configured in backend/admin.
- 待办: backend-configured reminders/actions, approvals, today replies, and fixed action categories.
- 消息: user-related messages: mentions, approvals, copied info, import/export and operation feedback.
- 后台: basic info, org, roles, modules, flow, applications, dashboards, dictionaries, work config, application config, AI config, data sources, logs.
- 个人信息: profile, switch system, logout.
- 业务模块: configured system module groups as tabs; group modules own fields, actions, pages, print templates.
- flow管理: flowchart, module/external binding, approvers, data sources, approval nodes, actions.

## UI Rules

- List-dominant surfaces use left-side tabs/categories.
- Detail surfaces use right-side tabs/work areas.
- Row click opens detail while preserving list context.
- Row buttons are only for actions different from opening detail.

## Current Small-Task Breakdown

- `REBUILD-P0-001`: source/archive/architecture baseline. Done.
- `REBUILD-P0-002`: clean runnable scaffold. Done.
- `REBUILD-P0-003`: auth and platform/system context shell. Done.
- `REBUILD-P0-004`: admin configuration foundation. Done.
- `REBUILD-P0-005`: business module configuration and runtime list/detail. Done.
- `REBUILD-P0-006`: Flow and Application gateway. Current.
- `REBUILD-P0-007`: Work, todo, message, AI assistance.
- `REBUILD-P0-008`: release and human trial readiness.

## Acceptance Rules

A task is accepted only when it has:

- backend behavior;
- frontend behavior;
- persisted data/readback where relevant;
- permission/auth negative path where relevant;
- task-specific script evidence;
- `.cursor/session/rebuild/evidence/*` evidence;
- next task updated.

Engineering evidence still does not set `gates.user_script_passed=true`; that remains user verification/signoff only.