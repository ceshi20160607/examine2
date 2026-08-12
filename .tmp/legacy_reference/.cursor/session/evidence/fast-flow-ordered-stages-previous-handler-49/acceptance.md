# FAST-FLOW-ORDERED-STAGES-PREVIOUS-HANDLER-49 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-07-31T12:03:00+08:00`
- Delivery mode: functionality first; responsive and exhaustive visual
  hardening remain deferred.

## Delivered

- Added an optional authoritative `approvalStages` definition contract while
  retaining the legacy one-stage request, response, JSON and database shapes.
- Added 2..10 ordered stage editing, validation, immutable publication,
  simulation and runtime snapshots with a compatible stage-zero projection.
- Implemented lazy stage activation on one stable instance identity for
  `SEQUENTIAL`, `ANY`, `ALL` and `QUORUM` decisions.
- Added later-stage `FIXED` and `PREVIOUS_HANDLER` sources.
  `PREVIOUS_HANDLER` resolves only the actual authenticated human actors that
  completed the immediately preceding stage, including the proxy actor for a
  delegated decision.
- Made completion and next-stage activation atomic under an instance row lock.
  Activation failure rolls back the completing decision and an idempotent
  retry cannot duplicate the decision or stage activation.
- Preserved immutable completed-stage participant and actor snapshots,
  deadline advancement, active-stage mutations and legacy-row compatibility.
- Added V8.39 persistence for published stage plans, durable stage execution
  state and the current-stage cursor.
- Added a function-first Vue editor and runtime readback for ordered stages,
  previous-handler selection and active/completed/waiting stage state.

## Verification

- Core and Flow default regressions passed: `23 + 208 = 231` tests.
- Final targeted domain, JDBC, migration, service, worker and controller
  regression passed: `92/92` tests. Additional isolated domain and repository
  suites passed `60/60` and `53/53`.
- Backend reactor test compilation passed across all `12` modules.
- Real MySQL 8.4 schema integration passed: `1/1`; all `61` migrations applied
  through `V8.39.0` and the repeat migration was clean.
- Real MySQL 8.0.44 + Redis authenticated HTTP journey passed: `1/1`; all
  `61` migrations applied.
- Frontend targeted regression passed: `3` files / `115` tests.
- Frontend canonical full suite passed: `54` files / `259` tests.
- Frontend typecheck and Vite production build passed.
- Base structure validation and active instance-framework validation passed.

## Demonstrated journey

An administrator creates, checks, simulates and publishes a three-stage
definition. The manager completes stage zero directly. The fixed member in
stage one delegates and the authenticated proxy completes that stage. Stage
two then activates with only that real proxy as its previous handler, without
pre-populating an actor before activation. The proxy completes the instance,
and the durable stage cursor, snapshots, delegation audit and decision history
remain correct. Invalid first-stage previous-handler and mixed gateway/stage
shapes fail with stable errors and no runtime side effects.

## Deferred

- Exclusive, parallel and inclusive branch-local ordered stage plans.
- Remaining contextual dynamic approver sources on later stages.
- Multi-level escalation and automatic reporting-tree reassignment.
- Approval attachments, signature evidence and reusable comment templates.
- External tasks, Webhooks and subflows.
- Responsive, accessibility and exhaustive visual-state hardening.
