# FAST-FLOW-QUORUM-APPROVAL-43 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-07-30T03:03:56+08:00`
- Delivery mode: functionality first; responsive and exhaustive visual hardening remain deferred.

## Delivered

- Added `QUORUM` approval mode to ordinary, exclusive, parallel and inclusive approval routes.
- Added immutable `COUNT` and `PERCENTAGE` rules, including positive-count validation, `1..100` percentage validation and deterministic ceiling calculation.
- Draft check and simulation resolve fixed, role and department members before validating the threshold and expose the computed required approval count.
- Runtime instances and branch executions persist both the resolved participant snapshot and required approval count, so later definition or membership changes cannot alter running work.
- Concurrent decisions approve immediately when the threshold is reached and reject immediately when the remaining possible approvals cannot reach it; untouched participant tasks are cancelled.
- Persisted quorum rules and runtime thresholds through JDBC/MySQL with Flyway migration `V8_34_0__flow_quorum_approval.sql`.
- Added API request/view mapping and function-first canvas/editor controls for count and percentage rules, validation, save/reopen, simulation and runtime detail.

## Verification

- Core/Plat/Flow full regression: `20 + 20 + 222 = 262` tests passed.
- Targeted quorum backend suite: `65` tests passed.
- Frontend full suite: `53` files / `227` tests passed.
- Frontend typecheck and production build passed.
- Backend 12-module reactor test compilation passed.
- Real MySQL 8.0.44 + Redis 7.4 multi-account HTTP journey passed, including 2-of-3 early approval, impossible early rejection, pending-task cancellation and immutable runtime thresholds after a definition revision.
- Real MySQL 8.4 schema upgrade and repeat-migration integration passed with `56` migrations and final segment `14`.
- Instance framework validation and scoped `git diff --check` passed.

## Demonstrated journey

Administrator configures a role/department-backed 2-of-3 or percentage quorum route, checks and simulates the computed threshold, publishes and starts it, then distinct tenant members reach an early approval or an impossible early rejection while remaining tasks are cancelled. Revising the definition afterwards leaves existing instances on their original participant and threshold snapshots.

## Deferred

- Weighted votes or per-member weights.
- Quorum mutation after an instance starts.
- Delegation, proxy approval and broader candidate-claim policies.
- Department leader, manager, record-field member and previous-handler sources.
- External tasks, Webhooks and subflows.
- Responsive, accessibility and exhaustive visual-state hardening.
