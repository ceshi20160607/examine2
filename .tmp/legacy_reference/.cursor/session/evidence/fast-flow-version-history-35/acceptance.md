# FAST-FLOW-VERSION-HISTORY-RESTORE-35 Acceptance

## Verdict

PASS at 2026-07-29T21:14:25+08:00.

Batch 35 closes the first usable Flow version-governance slice. Managers can
page through complete immutable publication snapshots and restore any selected
snapshot into a new draft without changing the active runtime version.

## Delivered behavior

- Published snapshots are listed newest first with stable pagination and retain
  exact name, ordered approvers, trigger binding, terminal record mapping,
  source revision and publication time.
- Restore copies the complete selected snapshot into the current draft,
  increments its revision and never updates or deletes published history.
- Restore is draft-only. Manual and automatic execution continue using the
  latest publication until the restored draft is explicitly published.
- Publishing a restored draft creates the next monotonic version. Existing
  instances stay pinned to their original version.
- Periodic snapshots retain their start and interval while rebinding requester
  identity to the member performing the restore.
- History reads and restore writes require definition-management permission and
  preserve system/tenant isolation.
- The Flow manager now exposes version history, complete snapshot details,
  paging and restore-to-editor behavior.

## Verification

- Core regression: 20/20 tests passed.
- Flow regression: 186/186 tests passed.
- Targeted service, repository and API regression: 52/52 tests passed.
- `FlowApiJourneyIntegrationTest`: 1/1 passed in 130.9 s against real MySQL
  8.0 and Redis after applying all 50 migrations. It proves newest-first
  paging, permission denial, draft-only restore, v1/v2 instance pinning and
  restored publication as v3.
- `npm.cmd test`: 51 files and 199 tests passed.
- `npm.cmd run build`: TypeScript validation and Vite production build passed.
- `mvn -DskipTests test-compile`: all 12 backend reactor modules passed.
- No schema migration was required; the real journey reached schema v8.28.0.

## Demo path

Open system Flow -> open Version history -> inspect divergent immutable
snapshots -> restore an older snapshot -> edit the returned draft -> observe
that starts still use the active version -> publish -> observe the next version
and preserved historical instances.

## Deferred

- side-by-side visual diff, partial merge and selective-property restoration
- graph canvas, simulation and expanded publish checks
- responsive, accessibility and exhaustive visual hardening
