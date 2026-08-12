# FAST-KPI-TARGET-CALCULATION-64 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-03T12:45:00+08:00`
- Delivery mode: functionality first; dashboard KPI widgets, scheduled reminder
  delivery, responsive layout, accessibility, animation and exhaustive visual
  hardening remain deferred to later focused slices.

## Delivered

- Added tenant-scoped KPI roots, mutable CAS-protected drafts, immutable published
  versions and exact publication replay over one pinned native data-source/schema
  version. Definitions preserve aggregation, subject, period, measure/time field,
  direction and warning-threshold metadata without exposing physical SQL details.
- Added member, department and role targets with aligned month/quarter/year
  periods, canonical decimal strings, immutable version pins, optimistic updates
  and idempotent creation. Subject validation is delegated through the platform
  directory facade instead of reading platform tables from the KPI module.
- Added deterministic explicit calculations through the internal exact statistics
  path. Ownership restrictions are applied in SQL before aggregation; role member
  expansion is sorted, deduplicated and snapshotted; an empty role deterministically
  produces zero.
- Persisted immutable calculation explanations including source/schema/field pins,
  subject snapshot, target and actual values, attainment/warning result, query ID,
  matched count, trend buckets, authorization epoch, actor and stable failures.
- Added management APIs and independent administration/runtime pages for KPI
  definition editing, checking, publishing, target maintenance, calculation,
  history, period filtering and current-member visibility. Decimal values remain
  strings in the browser and backend results remain authoritative.
- Added Flyway V8.53 with five KPI tables, restrictive foreign keys, checks,
  idempotency keys and indexes. The application service transaction boundary keeps
  subject validation, authorization, locks and final calculation persistence in
  one consistent operation.

## Verification

- KPI-focused backend verification passed `25/25`; affected native statistics
  restriction/query-identity verification passed another `28/28`, for `53/53`
  focused checks with no failure, error or skip.
- Final affected backend regression passed Core `28/28`, Platform `40/40` and
  Module `266/266`, totaling `334/334` with no failure, error or skip.
- Backend production and test compilation passed across all `13` child Maven
  modules.
- Real Spring Boot + MySQL 8.0.44 + Redis 7.4 authenticated HTTP journey passed
  `1/1`. It published and replayed a KPI, created a real member target, calculated
  and replayed the result, read history/runtime explanation and proved tenant
  hiding.
- Official MySQL 8.4.10 upgrade verification passed `1/1`: all `75` Flyway
  migrations applied through V8.53. The database contained `59` tables and the
  five KPI tables passed exact probes covering `116` columns, `13` foreign keys
  and `27` checks.
- Frontend full verification passed `78` files / `371` tests; independent
  typecheck and the Vite production build passed over `5726` transformed modules.
  Only the established large-chunk warning remains.
- Staged and unstaged `git diff --check`, strict UTF-8/mojibake validation, the
  framework structure validator and both VS4 machine-contract validators passed.

## Demonstrated journey

An administrator publishes records and a native data source, creates and checks a
KPI draft, publishes it twice with exact replay, assigns a monthly member target
and executes a real calculation. Repeating the command returns the same immutable
calculation, history and runtime views retain the exact explanation, the applicable
member can see the target, and another tenant cannot discover it.

## Integration corrections

- Made KPI application operations transactional so platform subject resolution
  and locking reads run inside the required transaction and the calculation
  snapshot is committed atomically.
- Allowed Spring to proxy the JDBC source catalog and KPI service instead of
  failing startup on final classes.
- Stored canonical numeric values as checked ASCII decimal strings, avoiding
  database scale truncation while retaining exact Java decimal arithmetic.
- Kept ownership restrictions internal to the statistics gateway and included
  them in stable query identity without accepting them from public HTTP DTOs.
- Removed duplicate subject type input from target creation; it is derived from
  the immutable KPI version and cannot drift from the published definition.

## Deferred

- KPI dashboard widgets, rankings, progress views and click-through builders
- scheduled KPI recalculation, reminders, escalation and notification delivery
- report definitions, schedules, exports and delivery history
- cross-module, external API and direct-database data sources
- responsive, accessibility, animation and exhaustive visual-state hardening
