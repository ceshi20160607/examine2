# FAST-FLOW-TRANSFER-ADD-SIGN-16 Acceptance

Accepted at: 2026-07-27T19:28:26+08:00

Verdict: PASS

## Delivered functional closures

- Every Flow instance now owns an ordered `approver_ids_json` runtime snapshot. V8.10 backfills existing
  instances from their immutable published definition version, and all instance/task reads use the snapshot so
  transfer and add-sign changes survive reload without rewriting the definition.
- A current approver with `flow.instance.transfer` can idempotently replace the current step with another active
  member in the same system and tenant. The old pending task disappears, the target task appears, the runtime
  sequence is retained after reload and later approval follows the changed sequence.
- A current approver with `flow.instance.add-sign` can idempotently insert an active member immediately BEFORE or
  AFTER the current step. BEFORE immediately assigns the added member and returns to the original current
  approver after approval; AFTER retains the current assignment and hands off to the added member next.
- Assignment requests trim and require a 1..500-code-point reason, require an active non-self target absent from
  the current sequence and preserve the ten-step limit. Current-approver authorization is checked before active
  target resolution to avoid member enumeration.
- Transfer/add-sign update `approver_id`, the sequence snapshot and `state_version` with the existing scoped
  PENDING/version compare-and-set, then append exactly one history fact in the same transaction. Approve, reject,
  withdraw, terminate and competing assignment mutations therefore cannot jointly succeed at one version.
- History now persists and returns `TRANSFERRED`/`ADD_SIGNED` facts with actor, target, position, normalized
  reason and time. Sequential progress counts only `APPROVED` facts, so assignment facts cannot skip a step.
- Same-key/same-body mutation replay returns the original instance without a second history row; changed bodies
  conflict. The existing CSRF and transaction-scoped idempotency infrastructure remains shared.
- V8.10 backfills both permissions and active-root grants, advances authorization epochs and adds both permission
  definitions to `PermissionCatalog` for systems created after the migration chain.
- The Flow frontend exposes permission/current-approver-gated Transfer and Add-sign dialogs through the existing
  member picker. Failures preserve target, reason and position; success refreshes instance, task projections and
  history. Assignment history renders actor, target, position and reason. Responsive/visual hardening remains
  deferred to P10-H1.

## Verification

- Core + Flow: 31 test files / 86 tests passed with zero failures, errors or skips:
  - `examine-core`: 3 files / 5 tests.
  - `examine-flow`: 28 files / 81 tests.
- `PermissionCatalogTest`: 4/4 passed for the post-migration transfer/add-sign permission catalog.
- Frontend final shared-state rerun: 37 test files / 130 tests passed; Vue/TypeScript typecheck and production
  build passed.
- Real authenticated HTTP on MySQL 8.0.44:
  - `FlowApiJourneyIntegrationTest` passed 1/1.
  - All 32 Flyway migrations applied through V8.10.
  - The journey proved transfer reload, exact replay/changed-body conflict, old/new pending task projection,
    target final approval and `STARTED -> TRANSFERRED -> APPROVED` history.
  - It separately proved BEFORE add-sign reload, runtime sequence `[added member, original approver]`, task
    handback after the added member approves, final approval and
    `STARTED -> ADD_SIGNED -> APPROVED -> APPROVED` history.
- `P4A1SchemaIntegrationTest` passed 1/1 on MySQL 8.4.10. A populated V4.10 database applied 15 migrations through
  V8.9, then exactly V8.10 once and zero on rerun. It verified ordered snapshot backfill, all three new columns,
  prior-history null assignment facts, expanded checks and both permission backfills.
- Batch16 scoped trailing-whitespace/diff checks and both framework structure validators passed.

## Integration finding closed

- The first real HTTP run completed transfer and history persistence, then found that the test expected an
  explicit JSON null for transfer position. The application-wide NON_NULL response policy correctly omits that
  field. The test now accepts omitted/null for nullable history properties; no production behavior changed.

## Non-blocking observation

The production build retains the existing warning for chunks above 500 kB. Code splitting and the full
viewport/accessibility/visual matrix remain grouped with P10-H1.
