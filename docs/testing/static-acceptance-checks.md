# Static Acceptance Checks

## Global

- Every output path must match its `TASK-*.md`.
- Every implementation batch must run `git diff --check`.
- API names must match `docs/api/api.md`; frozen contract changes require a contract issue.
- No Secret plaintext appears in docs, fixtures, logs, exports, screenshots, or UI state.

## UI And Interaction

- Login, register-with-system, password reset, SSO login, and MFA entry routes exist.
- Platform shell, platform backend, system shell, and system backend stay separate.
- System switch returns and stores `SystemSwitchContext`; tenant switch returns and stores `TenantSwitchContext`.
- Business data list rows open detail on row click.
- Checkbox, button, link, input, and select clicks do not trigger row detail.
- Static tables, permission matrices, status tables, and configuration explanation tables do not use row-click affordance.
- Duplicate detail/open buttons are removed when whole-row click is the primary action.
- Disabled buttons show `disabledReason`.
- List pages include pagination, filters, sorting where applicable, selection limits, empty state, and async task results.

## Backend And Data

- Mutable APIs record trace ID and audit ID.
- Long-running operations return `AsyncTask`.
- Permission and field masks are server-side decisions.
- Tenant and system context are explicit.
- Publish, rollback, import/export, Secret rotation, Agent confirmation, and ops actions are idempotent.

## Feature Coverage

- SSO has platform identity provider config and system SSO policy/org-member mapping.
- Work management has dashboard, project task, plain task, and daily report routes.
- Workflow nodes include approval, condition, field update, external API, timer, and timeout properties.
- Log management uses one entry with login, business, risk, import/export, Agent, OpenAPI, and ops filters.
- AI Agent has separate platform confirm, system write confirm, and work draft confirm flows.

