# FAST-FLOW-APPROVAL-MODES-39 Acceptance

## Verdict

PASS at 2026-07-29T23:46:58+08:00.

Batch 39 closes executable route participation semantics. Draft save, preflight,
simulation, immutable publication, runtime snapshots, eligible task queries and
transactional decisions now execute one shared `SEQUENTIAL`, `ANY` or `ALL`
approval-mode contract.

## Delivered behavior

- Every ordinary definition route and every conditional/default gateway branch
  owns an approval mode. Existing rows and legacy API callers default to
  `SEQUENTIAL`.
- `ANY` activates every route member. The first approval completes the
  instance; an individual rejection removes only that member's pending task,
  and the instance rejects only after every member rejects.
- `ALL` activates every route member. Every approval is required and the first
  rejection terminates the instance.
- Instances snapshot the selected mode, ordered route and approved/rejected
  member decisions. Later draft or publication changes cannot alter an
  in-flight instance.
- JDBC task queries distinguish sequential ownership from concurrent route
  membership, remove decided members from pending work and preserve completed
  history for the correct participants.
- Concurrent decisions use the existing transactional compare-and-save path.
  Duplicate and late decisions fail without reopening a terminal instance.
- Transfer, add-sign, reduce-sign, return and claim remain explicitly
  sequential-only.
- Simulation exposes the selected mode and exact initial active-member set.
- The Vue Flow editor saves the ordinary/default and branch-level modes.
  `ANY`/`ALL` routes render one grouped approval node and expose every group
  member in the inspector instead of presenting a false sequential graph.
- Migration `V8_30_0__flow_approval_modes.sql` persists draft/version modes,
  runtime mode and approved/rejected decision arrays.

## Verification

- Core regression: 20/20 tests passed.
- Flow regression: 203/203 tests passed.
- Targeted SQL/mapping regression: 10/10 tests passed.
- Targeted frontend graph/canvas/view/API regression: 4 files and 85 tests
  passed.
- `npm.cmd test`: 53 files and 217 tests passed.
- `npm.cmd run build`: TypeScript validation and Vite production build passed.
- `mvn -DskipTests test-compile`: all 12 backend reactor modules passed.
- `FlowApiJourneyIntegrationTest`: 1/1 passed against real MySQL 8.0.44 and
  Redis 7.4 after applying all 52 migrations through schema v8.30.0.
- The real journey proves concurrent two-member task visibility, ANY partial
  rejection and terminal approval, ALL partial/terminal approval and terminal
  rejection, JSON decision persistence, sequential completed-task
  compatibility and rejection of concurrent transfer.

## Demo path

Open system Flow -> New or Revise definition -> select an approval node or
gateway branch -> choose Sequential, Any one or All members -> assign members
-> Save and Simulate -> Publish -> Start -> open each member's pending tasks ->
decide -> inspect active/approved/rejected members in instance detail.

## Deferred

- parallel/inclusive split-and-merge gateways
- external tasks and subflows
- role/department/dynamic approver sources and quorum approval
- runtime instance overlay and animated execution tokens
- responsive, accessibility and exhaustive visual hardening
