# FAST-FLOW-PARALLEL-BRANCHES-40 Acceptance

## Verdict

PASS at 2026-07-30T00:45:08+08:00.

Batch 40 closes the first durable split-and-join execution path. One parent
instance now owns multiple independently active approval branches, projects
branch-specific tasks and reaches one deterministic terminal result.

## Delivered behavior

- A definition can own one parallel gateway with 2..5 ordered named branches.
  Conditional and parallel gateways are mutually exclusive.
- Every branch snapshots its ordered approvers and
  `SEQUENTIAL`/`ANY`/`ALL` approval mode.
- Starting a published definition creates the parent and every branch execution
  in one transaction. Every initial branch task is immediately eligible.
- Branch decisions target an explicit branch code. Branches advance and finish
  independently while the parent remains pending.
- The parent approves only after every branch approves. The first rejected
  branch rejects the parent and atomically cancels all remaining pending
  branches and tasks.
- Parent withdrawal and termination close every active branch. Assignment,
  claim, return and add/reduce-sign commands fail closed for parallel parents.
- Draft checks and simulations expose every branch and its exact initial active
  members. Publication and runtime branch snapshots remain immutable after a
  later revision.
- JDBC task queries combine ordinary-instance and branch-eligibility paths
  without duplicating parent tasks. Branch rows participate in the existing
  transactional parent CAS update.
- Migration `V8_31_0__flow_parallel_branches.sql` stores immutable
  draft/version graphs and durable branch execution rows.
- The functional Vue Flow editor can enable/remove the gateway, edit 2..5
  branch routes and modes, and render trigger -> split -> independent approval
  routes -> join -> terminal result.
- Instance detail and decision dialogs show branch runtime state and send
  explicit branch decisions. Responsive and visual hardening remains deferred.

## Verification

- Core regression: 20/20 tests passed.
- Flow regression: 208/208 tests passed.
- Targeted frontend graph/canvas/view/API regression: 4 files and 89 tests
  passed.
- `npm.cmd test`: 53 files and 221 tests passed.
- `npm.cmd run typecheck` and `npm.cmd run build`: passed.
- `mvn -DskipTests test-compile`: all backend reactor modules passed.
- `FlowApiJourneyIntegrationTest`: 1/1 passed against real MySQL 8.0.44 and
  Redis 7.4 after applying all 53 migrations through schema v8.31.0.
- The real HTTP journey proves two-account simultaneous task visibility,
  independent branch completion, all-approved join, fail-fast rejection,
  cancellation and task removal, durable branch rows, and later-version
  runtime snapshot safety.
- `git diff --check`: passed.

## Demo path

Open system Flow -> New or Revise definition -> enable Parallel gateway -> edit
each branch's name, members and approval mode -> Save and Simulate -> Publish ->
Start -> open each member's pending task -> decide the explicit branch ->
inspect branch states and the joined parent result.

## Deferred

- inclusive condition gateway and configurable merge policies
- nested gateways, loops, external tasks and subflows
- role/department/dynamic approver sources and quorum approval
- parallel-parent transfer, add-sign, reduce-sign, return and claim
- runtime instance overlay and animated execution tokens
- responsive, accessibility and exhaustive visual hardening
