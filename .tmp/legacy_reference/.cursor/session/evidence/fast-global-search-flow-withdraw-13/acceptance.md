# FAST-GLOBAL-SEARCH-FLOW-WITHDRAW-13 Acceptance

Accepted at: 2026-07-27T18:23:30+08:00

Verdict: PASS

## Delivered functional closures

- Runtime global search enumerates the authenticated navigation, intersects published field-read metadata with
  the current runtime schema, and searches only ACTIVE records through each module's existing permission, data
  scope and token-query path. It excludes hidden, disabled, unreadable, masked, IDENTITY and SECRET fields.
- Global pagination preserves navigation/module order and each module's stable record order. It skips complete
  module ranges, reads only the local pages needed for the requested global page, and silently removes modules
  that become unavailable while the search is executing.
- Search results expose only module identity, record identity/label/status and conservatively attributable
  readable field codes. Successful searches create no audit or outbox facts. Invalid requests retain the
  platform-wide failed-request audit required by `GlobalExceptionHandler` and create no outbox fact.
- A Flow requester can withdraw any still-PENDING instance, including after sequential approval handoff. The
  transaction uses the existing optimistic state version, persists terminal `WITHDRAWN` history, removes the
  pending task projection and exposes the result in completed/all task projections.
- Withdrawal requires the dedicated permission, CSRF, a trimmed 1..500-character reason and an idempotency key.
  Same-key/same-body replay returns the original response without duplicate history; changed bodies and terminal
  repeats return their frozen conflicts.
- Existing systems receive the permission through `V8_7_0`; systems created after the migration chain receive it
  from `PermissionCatalog`, so root bootstrap and role editing use one complete catalog.
- The workbench exposes paged global search with cross-module detail navigation. Flow views expose requester-only
  withdrawal, preserve the reason on failure and refresh instance, history and task projections after success.
  Responsive, breakpoint, accessibility and visual hardening remains deferred to P10-H1.

## Verification

- Global-search affected backend: `examine-core` 3 test files / 5 tests and `examine-module` 46 test files /
  115 tests; total 49 files / 120 tests passed with zero failures/errors/skips.
- Flow affected backend: `examine-core` 3 test files / 5 tests and `examine-flow` 10 test files / 37 tests;
  total 13 files / 42 tests passed with zero failures/errors/skips.
- Platform catalog: 6 test files / 11 tests passed after adding the post-migration new-system permission case.
- Real authenticated MySQL 8.0.44 HTTP:
  - `SystemFieldJourneyIntegrationTest` passed 1/1 for indexed cross-module search, exact total/pagination,
    matched-field attribution, invalid input and audit/outbox boundaries.
  - `FlowApiJourneyIntegrationTest` passed 1/1 for two real members, sequential handoff, requester-only
    withdrawal, reason validation, replay, changed-body conflict, terminal conflict, history and task views.
- `P4A1SchemaIntegrationTest` passed 1/1 on MySQL 8.4.10: a populated V4.10 database applied 13 later migrations
  through V8.7 and validated the three WITHDRAWN constraints plus permission backfill.
- An independent empty MySQL 8.4 database applied all 29 migrations and passed a DDL probe that withdrew an
  instance after two sequential approvals with state version 3 and the exact terminal history actor/reason.
- Frontend: 36 test files / 106 tests passed; TypeScript/Vue typecheck and production build passed.
- Batch13 scoped whitespace/diff checks passed.

## Integration findings closed

- The first HTTP run showed that migration backfill alone does not populate permissions for systems created
  later. `flow.instance.withdraw` was added to the canonical system permission catalog with a focused regression
  test; the real Flow journey then passed.
- The first search journey configured a field as searchable while leaving its index mode `NONE`; the runtime
  correctly omitted it. The fixture now publishes a real FILTER index and proves indexed search end to end.
- The invalid-query check initially assumed that no audit applied to validation errors. The existing global
  exception policy intentionally records failed HTTP requests, so verification now distinguishes successful
  read-only search from failed-request audit without weakening the platform policy.

## Non-blocking observation

The production build retains the existing warning for chunks above 500 kB. Functional output is valid; code
splitting and the full viewport/accessibility/visual matrix remain grouped with P10-H1.
