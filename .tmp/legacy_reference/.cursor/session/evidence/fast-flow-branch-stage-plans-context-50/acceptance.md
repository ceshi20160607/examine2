# FAST-FLOW-BRANCH-STAGE-PLANS-CONTEXT-50 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-07-31T12:43:00+08:00`
- Delivery mode: functionality first; responsive and exhaustive visual
  hardening remain deferred.

## Delivered

- Composed the accepted ordered-stage engine into exclusive, parallel and
  inclusive gateway branches without introducing a second workflow runtime.
- Added optional `approvalStages` to every branch with exact stage-zero legacy
  projection and unchanged behavior when the field is absent.
- Added independent durable stage cursors and snapshots for selected parallel
  and inclusive branches while preserving exclusive first-match, selected-only
  execution, all-selected join and fail-fast rejection behavior.
- Made `PREVIOUS_HANDLER` branch-local and kept actual proxy/human actor
  semantics from Batch49.
- Added one immutable, bounded internal start-context snapshot containing the
  requester plus authorized record identity/value data. It is persisted before
  routing and is not exposed to the frontend.
- Enabled later-stage `REQUESTER`, requester manager, requester department
  leader, role, department and record-member-field resolution in addition to
  fixed and previous-handler sources.
- Made each branch stage completion and next-stage activation atomic under the
  locked parent instance, including rollback on contextual resolution failure.
- Added V8.40 persistence for start context, branch stage state and branch
  cursors; branch plans remain inside the existing immutable gateway JSON.
- Added a function-first per-branch stage editor, simulation metadata and
  runtime cursor/snapshot readback for all three gateway types.
- Corrected active-stage JDBC projections so a restart after branch stage
  advancement reloads the new member set, mode and quorum threshold.

## Verification

- Core and Flow full regressions passed: `23 + 289 = 312` tests.
- Final merged targeted domain, JDBC, migration, API and service regression
  passed: `90/90` tests; repository contracts passed `22/22`.
- Backend reactor test compilation passed across all `12` modules.
- Real MySQL 8.4 schema integration passed: `1/1`; all `62` migrations applied
  through `V8.40.0` and the repeat migration was clean.
- Real MySQL 8.0.44 + Redis authenticated HTTP journey passed: `1/1`; all
  `62` migrations applied.
- Frontend targeted regression passed: `4` files / `129` tests.
- Frontend canonical full suite passed: `54` files / `265` tests.
- Frontend typecheck and Vite production build passed.
- Base structure validation, active instance validation and `git diff --check`
  passed.

## Demonstrated journey

An administrator configures and publishes an inclusive gateway whose urgent
branch has three stages and large branch has two. A start selects both branches
from one value snapshot. The branches advance independently: urgent resolves a
branch-local previous handler and then the original requester, while large
resolves that requester after its specialist. One branch finishes while the
parent remains pending; the second final decision joins the parent as approved.
Database assertions prove only selected branches exist, both durable stage
snapshots are present, their cursors differ, the requester start context is
immutable and a reloaded terminal instance projects the correct final stages.

## Deferred

- Approval attachments, signature evidence and reusable comment templates.
- External tasks, Webhooks and subflows.
- Nested gateways, loops, arbitrary graph traversal and cross-branch jumps.
- Multi-level escalation and automatic reporting-tree reassignment.
- Responsive, accessibility and exhaustive visual-state hardening.
