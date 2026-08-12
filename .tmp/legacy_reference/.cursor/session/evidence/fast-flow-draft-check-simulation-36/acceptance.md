# FAST-FLOW-DRAFT-CHECK-SIMULATION-36 Acceptance

## Verdict

PASS at 2026-07-29T21:44:26+08:00.

Batch 36 closes the first executable Flow preflight slice. Definition managers
can validate the current draft and simulate its exact start decision, ordered
route and record-status effects without creating runtime or publication state.

## Delivered behavior

- Draft check returns the checked revision, READY/BLOCKED verdict, counts and
  stable field-addressable blocker/warning issues.
- The checker revalidates current tenant membership for every ordered approver
  and the periodic requester. Duplicate approvers remain visible as warnings.
- Publish runs the same checker in its transaction and rejects blocked drafts
  with `FLOW_DRAFT_CHECK_BLOCKED`.
- Simulation evaluates manual, record-event and periodic trigger semantics and
  returns the exact ordered steps, trigger decision/reason and terminal record
  status effects.
- Simulation accepts requester, business key and JSON trigger samples while
  rejecting invalid or inactive requesters.
- Check and simulation require definition-management permission and preserve
  system/tenant isolation.
- The Flow manager exposes Check and Simulate actions, blocker locations,
  editable trigger samples, route preview and status-effect preview.
- Simulation is side-effect free: the real database journey proves version and
  instance counts remain unchanged.

## Verification

- Core regression: 20/20 tests passed.
- Flow regression: 191/191 tests passed.
- Targeted service and API regression: 43/43 tests passed.
- `FlowApiJourneyIntegrationTest`: 1/1 passed against real MySQL 8.0 and Redis
  after applying all 50 migrations. It proves READY check, matched/unmatched
  simulation, unchanged version/instance counts, stale-member publication
  blocking, repair and successful publication.
- Frontend targeted regression: 2 files and 69 tests passed.
- `npm.cmd test`: 51 files and 201 tests passed.
- `npm.cmd run build`: TypeScript validation and Vite production build passed.
- `mvn -DskipTests test-compile`: all 12 backend reactor modules passed.
- No schema migration was required; the real journey reached schema v8.28.0.

## Demo path

Open system Flow -> Check draft -> inspect READY or field-addressable blockers
-> Simulate -> provide requester/business key/event values -> inspect trigger
decision, ordered route and record effects -> repair a blocker -> publish.

## Deferred

- graph canvas authoring and richer node/edge types
- dependency impact analysis across applications
- responsive, accessibility and exhaustive visual hardening
