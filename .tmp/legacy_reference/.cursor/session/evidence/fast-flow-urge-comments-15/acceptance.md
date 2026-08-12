# FAST-FLOW-URGE-COMMENTS-15 Acceptance

Accepted at: 2026-07-27T19:06:18+08:00

Verdict: PASS

## Delivered functional closures

- `MemberMessageFacade` provides a narrow Core-owned transaction-joining command contract. The Event adapter
  reuses the existing active-recipient validation, inbox aggregate and repository, returning the real persisted
  unread message id without introducing a Flow-to-Event module dependency.
- The original requester can urge a `PENDING` instance after any sequential handoff. The durable urge captures
  the requester, current approver, optional normalized message and creation time without changing instance
  status, current approver, completion time or decision history.
- Urge persistence uses one guarded `INSERT ... SELECT` over the scoped Flow instance with exact PENDING,
  requester and current-approver predicates. A concurrent terminate/withdraw therefore cannot leave a
  post-terminal urge or inbox message.
- Urge and message dispatch share the same transaction. Same-key/same-body replay produces neither a second urge
  nor a second message; changed bodies conflict and a new key after terminal state returns the Flow state conflict.
- Urge success delivers one `FLOW_INSTANCE_URGED` unread inbox message to the current approver with Flow-instance
  target, business key and optional requester message.
- Permitted members can append comments to pending or terminal instances. Comments are trimmed, immutable,
  1..2000 Unicode code points, idempotent and independent of Flow decision history.
- Urge and comment lists verify the scoped instance, enforce the existing page bounds and return stable
  chronological order.
- `V8_9_0` creates restrictive tenant-scoped urge/comment tables, backfills both permissions and root grants and
  advances authorization epochs. `PermissionCatalog` covers systems created after the migration chain.
- The Flow view exposes requester-only urging, paged urge/comment timelines and permitted comment creation.
  Failed drafts are preserved and successful mutations refresh the relevant timeline. Responsive and visual
  hardening remains deferred to P10-H1.

## Verification

- Core + Event: 8 test files / 26 tests passed after the final transaction-proxy regression check.
- Core + Flow: 25 test files / 69 tests passed; Flow alone is 22 files / 64 tests.
- Platform permission catalog: 6 test files / 13 tests passed.
- Frontend: 37 test files / 122 tests passed; TypeScript/Vue typecheck and production build passed.
- Real authenticated HTTP on MySQL 8.0.44:
  - `FlowApiJourneyIntegrationTest` passed 1/1.
  - All 31 Flyway migrations applied from an empty schema through V8.9.
  - The journey proved requester-only urge, 501-code-point rejection, normalized message, same-key replay,
    changed-body conflict, one durable urge, unchanged decision history, exactly one unread current-approver
    inbox message, terminal urge conflict, terminal comments, comment replay/conflict and chronological paging.
- `P4A1SchemaIntegrationTest` passed 1/1 on MySQL 8.4.10. A populated V4.10 database applied 15 later migrations
  through V8.9, validated both new tables and both permission backfills, and remained migration-idempotent.
- Batch15 scoped whitespace/diff checks and both framework structure validators passed.

## Integration findings closed

- The first real application-context run applied V8.9 successfully, then showed that the final Event adapter
  could not receive Spring's class-based transaction proxy. The adapter is now proxyable and a focused
  regression assertion prevents the final modifier from returning. The same real journey then passed.
- Review found a read-then-insert race between urging and terminal Flow CAS mutations. The JDBC write now performs
  a database-guarded insert against the current PENDING/requester/approver tuple, with zero rows mapped to the
  existing Flow state conflict.

## Non-blocking observation

The production build retains the existing warning for chunks above 500 kB. Code splitting and the full
viewport/accessibility/visual matrix remain grouped with P10-H1.
