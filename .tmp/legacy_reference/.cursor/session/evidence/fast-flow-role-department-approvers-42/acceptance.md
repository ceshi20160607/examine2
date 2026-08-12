# FAST-FLOW-ROLE-DEPARTMENT-APPROVERS-42 Acceptance

## Verdict

PASS at 2026-07-30T02:13:47+08:00.

Batch 42 closes tenant-scoped role and department approver assignment. Every
executable approval route can now retain the fixed-member contract or resolve
one role/department into a deterministic member snapshot at check, simulation
and instance start.

## Delivered behavior

- Ordinary, exclusive, parallel and inclusive routes own one immutable
  `FIXED`, `ROLE` or `DEPARTMENT` source.
- Fixed sources retain ordered 1..10-member compatibility. Dynamic sources
  reference one positive role/department ID and do not send placeholder member
  IDs.
- The Core facade and Plat bridge resolve only active members from the exact
  current system and tenant. Results are unique and sorted by member ID.
- Draft check blocks missing, inactive, cross-scope, empty or oversized
  sources. Simulation previews the current resolved route without writes.
- Publish rechecks the source under the mutation transaction. Manual and
  periodic starts resolve current membership and persist the resolved IDs.
- Approval modes reuse the resolved snapshot. Later role, department or
  membership changes affect only later simulations and instances; pending and
  completed instances remain immutable.
- Draft and version JDBC storage persists the complete source aggregate for
  every graph shape. Existing rows and fixed-member clients remain compatible.
- Migration `V8_33_0__flow_approver_sources.sql` adds nullable selector
  snapshots to definitions and versions without rewriting runtime snapshots.
- API create/revise/read/check/simulate/publish/start exposes source kind,
  source ID and resolved preview.
- The functional graph editor supports fixed, role and department selection
  per active route and shows the server-resolved member preview. Responsive and
  visual hardening remain deferred.

## Verification

- Core regression: 20/20 tests passed.
- Plat regression: 20/20 tests passed, including three directory bridge
  contract tests.
- Flow regression: 215/215 tests passed.
- Source-focused directory, preflight and migration contracts: 10/10 tests
  passed.
- `FlowApiJourneyIntegrationTest`: 1/1 passed against real MySQL 8.0.44 and
  Redis 7.4 after applying all 55 migrations through schema v8.33.0.
- The real HTTP journey proves role save/read/check/simulate/publish/start,
  current-member mutation, old-instance snapshot safety and exact department
  fan-out.
- `P4A1SchemaIntegrationTest`: passed against clean MySQL 8.4.10, validating
  55 migrations, the 13-migration final segment and repeat migration
  idempotency.
- Frontend regression: 53 files and 225/225 tests passed.
- Frontend typecheck and production build: passed. The existing large-chunk
  advisory remains non-blocking.
- All backend reactor modules passed `mvn -DskipTests test-compile`.
- Framework instance structure validation and `git diff --check`: passed.

## Demo path

Open system Flow -> New or Revise definition -> select a route -> choose Role
or Department and enter its ID -> Save -> Check or Simulate to inspect current
members -> Publish -> Start -> change directory membership -> verify the old
instance retains its tasks while a new simulation/start uses the new members.

## Deferred

- department leader, direct manager, initiator manager and previous handler
- member-field-driven sources and multiple sources per route
- quorum percentage, delegation, agent approval and candidate claiming
- external tasks, Webhooks and subflows
- responsive, accessibility and exhaustive visual hardening
