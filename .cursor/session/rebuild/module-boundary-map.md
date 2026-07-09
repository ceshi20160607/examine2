# Module Boundary Map

## Context-Aware Top Level

| Module | Platform Context | System Context |
|---|---|---|
| 工作台 | Platform statistics and platform dashboard | Current system statistics and configured dashboard |
| Flow | Platform-level workflow/service orchestration | System flow management and approval/runtime workflow |
| 应用 | Authorization gateway for platform/system service exposure | System OpenAPI/application authorization gateway |
| 工作 | Platform work statistics and tasks | Work dashboard, project tasks, normal tasks, daily reports |
| AI | AI entry for platform assistance | AI entry for system analysis and assisted expansion |
| 待办 | Platform configured reminders/actions | System configured reminders/actions, approvals, today replies |
| 消息 | Platform-related messages | System-related mentions, approvals, copied info, imports/exports |
| 后台 | Platform admin by permission | System admin by permission |
| 个人信息 | profile, switch system, logout | profile, switch system, logout |

## Admin Scope

后台 must include:

- system basic information;
- organization;
- role management;
- module management;
- flow management;
- application management;
- dashboard management;
- dictionary management;
- work config;
- application config;
- AI config;
- data source config;
- log management.

## Business Modules

业务模块 is not the same as Application. Business modules are grouped tabs configured inside a system. Each module owns:

- fields and substructures;
- actions and permissions;
- list/detail pages;
- print templates;
- import/export;
- workflow binding;
- runtime list/detail state.

## UI Layout Rules

- Main list surfaces use left-side tabs/category navigation.
- Details use right-side tabs/work areas.
- Row click opens detail while preserving list filters, paging, and selection.
- Buttons inside rows are only for actions different from opening detail.

## Flow Rules

Flow management owns the flowchart and workflow behavior:

- internal business module binding;
- external third-party binding;
- approvers and approval nodes;
- data sources;
- node actions;
- publish checks;
- runtime readback;
- todo/message/log side effects.

External exposure requires an Application authorization boundary.
