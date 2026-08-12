# FAST-FLOW-SUBFLOW-COMPLETION-53 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-07-31T19:42:00+08:00`
- Delivery mode: functionality first; responsive, accessibility and exhaustive
  visual hardening remain deferred.

## Delivered

- Added immutable ordered `SUBFLOW` completion steps with an exact published
  child `definitionId/version`; definition snapshots and execution snapshots
  cannot drift after later child edits or publication.
- Added durable `RUNNING` completion executions and append-only subflow-run
  facts linking one parent execution attempt to one child instance.
- Added deterministic child launch, parent wait, child terminal observation,
  restart-safe reconciliation and idempotent application of the child result.
- Child `APPROVED/COMPLETED` advances the parent; rejection, withdrawal or
  termination fails the active step. Administrator retry preserves the old run
  and creates a new attempt and child.
- Parent termination cancels its running completion execution and terminates a
  pending child in parent/execution/child lock order. Late reconciliation is a
  durable no-op and cannot resurrect the parent.
- Added exact-version preflight with unavailable-target, direct/indirect cycle
  and maximum-depth-eight protection. Runtime start context carries immutable
  root/depth facts.
- Child approvals inherit requester and immutable record context facts but use
  no parent record binding, so they cannot project the parent's record state.
- Added sanitized definition/detail/history API readback and frontend editing,
  simulation summaries, child-instance navigation and failed-step retry.
- Added V8.43 `RUNNING` constraints, six-field start context, restrictive
  subflow-run foreign keys, unique attempt/launch/child identities and due/
  reconciliation indexes.

## Reconciliation design correction

The child aggregate is the authoritative durable terminal fact. The worker
observes it through `un_flow_subflow_run`, records `terminal_at`, and commits
`result_applied_at` when the parent result is applied. This pair is the durable
at-least-once reconciliation ledger. It removes a redundant second outbox row
without creating a loss window: a crash before observation or before result
application leaves the run discoverable and replayable after restart.

## Verification

- Affected backend regressions passed: Core `25/25` and Flow `345/345`, totaling
  `370/370` with no failure, error or skip.
- Batch53 domain/repository/migration targeted verification passed `12/12`;
  subflow runtime, worker, preflight, API and transport contracts are included
  in the green Flow full suite.
- Backend reactor test compilation passed across all `12` modules.
- Real MySQL 8.4 schema integration passed `1/1`: all `65` migrations applied
  through V8.43 and repeat migration was clean.
- Real MySQL 8.0.44 + Redis authenticated HTTP journey passed `1/1` with all
  `65` migrations applied.
- Frontend targeted verification passed `2` files / `117` tests; the canonical
  full suite passed `54` files / `281` tests. Typecheck and Vite production
  build passed.
- Base structure, active instance, VS4 contract, strict encoding and cadence
  self-tests passed. `git diff --check` passed.

## Demonstrated journey

An administrator publishes a child approval and a parent whose completion plan
contains that exact child version. Final human approval leaves the parent
`PENDING/EXTERNAL_EXECUTION`; the worker creates exactly one child and the
parent execution becomes `RUNNING`. Child approval reconciles the parent to
`APPROVED/COMPLETED`. A second parent proves child rejection, administrator
retry, a different second child and successful completion while both attempts
remain immutable. Replay adds no transition, and a direct cycle is blocked at
check and publish.

## Integration corrections

- Marked the production subflow-worker constructor for Spring injection while
  retaining the test-clock constructor.
- Replaced the legacy four-field start-context database check with a strict
  six-field ordinary/root/depth contract.
- Accepted both MySQL `INTEGER` and `UNSIGNED INTEGER` JSON types for positive
  Snowflake identifiers while keeping exact integer and positivity checks.

## Deferred

- Parallel completion joins, compensation and rollback workflows.
- Dynamic or cross-tenant child selection, loops and arbitrary graph jumps.
- Responsive, accessibility and exhaustive visual-state hardening.
