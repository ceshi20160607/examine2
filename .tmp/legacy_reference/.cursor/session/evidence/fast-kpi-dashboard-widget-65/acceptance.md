# FAST-KPI-DASHBOARD-WIDGET-65 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-03T23:45:07+08:00`
- Delivery mode: small functionality-first slice; scheduled KPI recalculation and
  reminders, responsive layout, accessibility, animation and exhaustive visual
  hardening remain separate later work.

## Delivered

- Added `KPI_VALUE` as a first-class published `SYSTEM_HOME` dashboard widget.
  Its draft accepts only a KPI root and layout/title identity; data-source,
  statistics and row-limit settings are rejected.
- Dashboard check resolves the same-tenant active KPI through a narrow catalog.
  Publication pins KPI root/version/number/code/name/subject/period metadata,
  includes all pins in fingerprint identity and preserves exact replay and old
  historical versions after later KPI publications.
- Extended JDBC dashboard snapshots and V8.54 persistence so existing data-source
  widgets and KPI widgets have strictly exclusive pin shapes. The database adds a
  restrictive immutable-KPI-version foreign key, source-kind check and KPI index
  without backfilling or changing legacy widget identities.
- Added server-clock month/quarter/year alignment and current-authority runtime
  resolution. A KPI widget returns every current member-applicable target for the
  exact pinned version, including multiple role/department matches; an empty set
  is a successful result and failures remain isolated per widget.
- Added dashboard editor KPI capability loading, published-version pin display and
  runtime zero/one/many target rendering. Existing KPI cards and explanations are
  reused, and canonical decimal strings are never recomputed with JavaScript.

## Verification

- Final focused dashboard/KPI backend verification passed `40/40` across domain,
  mapping, service, runtime, JDBC catalog/repository and migration contract tests.
- Final affected backend regression passed Core `28/28`, Platform `40/40` and
  Module `284/284`, totaling `352/352` with no failure, error or skip.
- Backend production and test compilation passed across all `13` child Maven
  modules.
- Real Spring Boot + MySQL 8.0.44 + Redis 7.4 authenticated HTTP journey passed
  `1/1`. It published a KPI and KPI dashboard, returned the real target and
  calculation explanation, re-published the KPI without moving the old dashboard
  version, and proved cross-tenant hiding.
- Official MySQL 8.4.10 upgrade verification passed `1/1`: all `76` Flyway
  migrations applied through V8.54. Dashboard storage now has `77` columns, seven
  restrictive foreign keys, `23` checks and `18` physical indexes across its three
  tables.
- Frontend full verification passed `79` files / `376` tests; independent
  typecheck and Vite production build passed over `5729` transformed modules.
  Only the established large-chunk warning remains.
- Staged and unstaged `git diff --check`, strict UTF-8/mojibake validation, the
  framework structure validator and both VS4 machine-contract validators passed.

## Demonstrated journey

An administrator publishes records, a native data source and a monthly member
KPI, calculates its target, then adds that KPI to the system-home dashboard. The
runtime dashboard returns the exact target, backend-owned status and explanation.
Publishing a revised KPI creates a new immutable version while the old dashboard
continues to return its old pinned target. Another tenant can discover neither
the dashboard nor KPI widget.

## Integration corrections

- Replaced test-only Mockito usage with a narrow KPI target reader and handwritten
  fake because the module intentionally excludes Mockito.
- Preserved two- and three-argument runtime service constructors used by existing
  statistics tests while the Spring path injects the new KPI runtime component.
- Returned nullable source pins safely for KPI widgets instead of unboxing them.
- Kept the exact legacy data-source widget fingerprint stable while adding a
  distinct KPI fingerprint branch.
- Strengthened SQL checks with explicit null-state rules so three-valued SQL logic
  cannot admit a half-populated source or KPI pin set.

## Deferred

- scheduled KPI recalculation, unmet-target reminders and escalation
- KPI ranking/progress/drill-through widget variants
- report definitions, schedules, exports and deliveries
- responsive, accessibility, animation and exhaustive visual-state hardening
