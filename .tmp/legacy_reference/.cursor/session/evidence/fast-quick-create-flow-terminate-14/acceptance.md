# FAST-QUICK-CREATE-FLOW-TERMINATE-14 Acceptance

Accepted at: 2026-07-27T18:44:26+08:00

Verdict: PASS

## Delivered functional closures

- The runtime quick-create endpoint composes authenticated navigation with each module's current record schema.
  It returns only navigation-visible modules whose schema is `READY` and whose real action set contains `CREATE`,
  while preserving server navigation order.
- Modules that disappear, become unpublished or lose authorization between navigation and schema lookup are
  omitted without disclosure. The endpoint is read-only and adds no CSRF, idempotency, success audit, outbox or
  migration behavior.
- The workbench exposes the ordered quick-create panel and reuses the existing module switch, dirty-form
  confirmation and real `?module={moduleCode}&mode=create` form. Failed or empty refreshes do not replace the
  current workbench.
- A member with `flow.instance.terminate` can terminate any `PENDING` instance, including after sequential
  approval handoff. The operation trims and requires a 1..500-character reason, persists `TERMINATED` history
  with the current member as actor, sets completion time and removes the pending task projection.
- Termination uses the existing optimistic Flow state version and transaction-scoped idempotency store.
  Same-key/same-body requests replay the original result; changed bodies conflict; all later approve, reject,
  withdraw and terminate attempts against the terminal instance conflict.
- `V8_8_0` expands Flow constraints and backfills existing systems and root roles. The canonical
  `PermissionCatalog` covers systems created after the migration chain.
- The Flow view shows termination only for `PENDING` instances when the session has the dedicated permission,
  preserves the reason on failure and refreshes instance, history and task projections after success.
  Responsive and visual hardening remains deferred to P10-H1.

## Verification

- Quick-create backend: `examine-core` 3 test files / 5 tests and `examine-module` 48 test files / 118 tests;
  total 51 files / 123 tests passed with zero failures/errors/skips.
- Flow termination backend: `examine-core` 3 test files / 5 tests and `examine-flow` 16 test files / 48 tests;
  total 19 files / 53 tests passed with zero failures/errors/skips.
- Platform permission catalog: 6 test files / 12 tests passed, including both post-migration withdrawal and
  termination permissions.
- Root combined verification passed with current reactor dependencies:
  - `PermissionCatalogTest` passed 2/2.
  - `SystemFieldJourneyIntegrationTest` passed 1/1 on real authenticated HTTP and MySQL 8.0.44 for the exact
    quick-create list and read-only audit/outbox boundary.
  - `FlowApiJourneyIntegrationTest` passed 1/1 on real authenticated HTTP and MySQL 8.0.44 for a non-requester
    operator terminating after sequential handoff, reason validation, replay, changed-body and terminal
    conflicts, history actor/reason and pending/completed task projections.
- Both HTTP journeys applied all 30 Flyway migrations from an empty schema through V8.8.
- `P4A1SchemaIntegrationTest` passed 1/1 on MySQL 8.4.10: a populated V4.10 database applied 14 later migrations
  through V8.8, retained exact migration idempotence, backfilled the Flow permission and validated all three
  WITHDRAWN/TERMINATED constraints.
- Frontend: 37 test files / 114 tests passed; TypeScript/Vue typecheck and production build passed.
- Batch14 scoped whitespace/diff checks passed.

## Non-blocking observations

- The production build retains the existing warning for chunks above 500 kB. Code splitting and the full
  viewport/accessibility/visual matrix remain grouped with P10-H1.
- An initial standalone MySQL shell probe stopped in readiness handling because Windows PowerShell promoted
  expected pre-ready stderr to a terminating error. It never executed a migration. The root Testcontainers runs
  subsequently validated V8.8 on both MySQL 8.0.44 and MySQL 8.4.10, so no product or migration risk remains.
