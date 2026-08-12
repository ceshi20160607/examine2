# FAST-FLOW-INCLUSIVE-GATEWAY-41 Acceptance

## Verdict

PASS at 2026-07-30T01:14:36+08:00.

Batch 41 closes executable inclusive selection. The same typed condition
matcher now selects every matching route, while the durable branch runtime
executes and joins exactly that selected set.

## Delivered behavior

- Definitions can own an ordinary, exclusive-conditional, parallel or
  inclusive route shape. The four route shapes are mutually exclusive.
- An inclusive gateway owns 2..5 ordered unique branches. Non-default branches
  require typed all-of conditions; an optional default is ordered last.
- Every matching non-default branch starts from one immutable input snapshot.
  The default starts only when no conditional branch matches. No match without
  a default fails closed.
- Selected branches reuse the durable branch execution table, branch-aware
  task query, explicit branch decisions, parent CAS and fail-fast cancellation
  introduced by Batch 40. Unselected branches create no execution row or task.
- One selected branch is valid and can complete the parent without creating a
  fake second branch. Multiple selected branches require the existing
  all-selected approval join.
- Draft check validates all route members. Simulation returns the exact
  selected route/member set without writes.
- Periodic triggers resolve against empty input and are blocked before publish
  when neither an empty-input condition nor a default can be selected.
- Migration `V8_32_0__flow_inclusive_gateway.sql` stores immutable
  draft/version inclusive graphs. Runtime storage is deliberately reused.
- API create/revise/read/check/simulate/publish/start and explicit branch
  decisions expose one shared inclusive contract.
- The functional editor configures branch conditions, routes and modes and
  renders inclusive split, configured routes and one all-selected join.

## Verification

- Core regression: 20/20 tests passed.
- Flow regression: 213/213 tests passed.
- Targeted inclusive domain/migration/API tests: 5/5 passed.
- Targeted frontend graph/canvas/view tests: 3 files and 73 tests passed.
- `npm.cmd test`: 53 files and 224 tests passed.
- `npm.cmd run typecheck` and `npm.cmd run build`: passed.
- `mvn -DskipTests test-compile`: all backend reactor modules passed.
- `FlowApiJourneyIntegrationTest`: 1/1 passed against real MySQL 8.0.44 and
  Redis 7.4 after applying all 54 migrations through schema v8.32.0.
- The real journey proves multi-match and single-match execution, zero-match
  default fallback, selected-only durable rows/tasks, all-selected join,
  fail-fast cancellation and later-version snapshot safety.
- `git diff --check`: passed.

## Demo path

Open system Flow -> New or Revise definition -> enable Inclusive gateway ->
configure branch conditions, members and approval modes -> Save and Simulate
with values matching one or several branches -> Publish -> Start -> process
the selected branch tasks -> inspect selected branch snapshots and joined
parent result.

## Deferred

- nested gateways, loops and arbitrary graph traversal
- configurable merge thresholds or partial-success merge
- external tasks, Webhooks and subflows
- role/department/dynamic approver sources and quorum approval
- runtime instance overlay and animated execution tokens
- responsive, accessibility and exhaustive visual hardening
