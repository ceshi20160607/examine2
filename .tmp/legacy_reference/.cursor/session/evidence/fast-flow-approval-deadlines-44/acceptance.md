# FAST-FLOW-APPROVAL-DEADLINES-44 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-07-30T19:12:23+08:00`
- Delivery mode: functionality first; responsive and exhaustive visual hardening remain deferred.

## Delivered

- Added typed approval deadline policies to ordinary, exclusive, parallel and inclusive routes, with positive timeout, optional reminder offset and `NONE`, `AUTO_APPROVE` or `AUTO_REJECT` actions.
- Draft check, simulation, publication and instance start now carry the route or branch policy through one contract and compute immutable absolute reminder/due timestamps.
- Persisted definition policies and route/branch runtime snapshots through JDBC/MySQL with Flyway migration `V8_35_0__flow_approval_deadlines.sql`.
- Added a bounded, transaction-isolated database worker that locks due instances, emits durable deduplicated reminders and executes timeout actions through the existing approval domain/persistence boundary.
- Added explicit reminder, overdue, automatic-approval and automatic-rejection history events; expanded the real MySQL history event contract so these transactions cannot be truncated or rejected by the database.
- `NONE` records an overdue state without closing the task, while automatic actions complete or reject routes/branches and preserve existing concurrent-mode invariants.
- Added API request/view mapping and function-first canvas/editor controls for policy editing, validation, save/reopen, simulation and runtime status/history.
- Added worker failure observability so a failed due item remains retryable while its cause is logged and other due items continue.

## Verification

- Core/Flow/Event full regression: `20 + 231 + 25 = 276` tests passed.
- Targeted deadline backend and frontend suite: `71` tests passed.
- Frontend full suite: `53` files / `230` tests passed.
- Frontend typecheck and production build passed.
- Backend 12-module reactor test compilation passed.
- Real MySQL 8.0.44 + Redis 7.4 authenticated HTTP journey passed with `57` migrations through `V8.35.0`.
- The real journey covered policy save/read/check/simulate/publish/start, revision immutability, reminder delivery and deduplication, automatic approval, automatic rejection, overdue-only processing and later human approval.
- Real MySQL schema migration integration passed with `57` migrations and final segment `15`.
- Instance framework validation and scoped `git diff --check` passed.

## Demonstrated journey

An administrator configures reminder and timeout behavior on a route, checks and simulates it, publishes and starts an immutable runtime snapshot, then revises the definition without changing the running instance. A durable worker emits one reminder, automatically approves or rejects according to the snapshotted action, or records overdue without closing the task so an eligible human can still approve.

## Deferred

- Business calendars, working hours and escalation chains.
- Per-participant deadlines inside one concurrent route.
- Custom reminder templates and external delivery channels.
- Advanced approver sources, delegation and proxy approval.
- External tasks, Webhooks and subflows.
- Responsive, accessibility and exhaustive visual-state hardening.
