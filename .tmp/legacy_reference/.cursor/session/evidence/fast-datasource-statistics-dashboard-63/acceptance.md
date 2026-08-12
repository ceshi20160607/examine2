# FAST-DATASOURCE-STATISTICS-DASHBOARD-63 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-01T05:10:25+08:00`
- Delivery mode: functionality first; responsive, accessibility, animation and
  exhaustive visual hardening remain deferred to the final unified pass.

## Delivered

- Added one declarative active-version statistics API for published native module
  data sources plus an internal exact-version service for immutable dashboard
  execution. Public callers cannot submit SQL, physical columns, tenant/member
  identifiers or authorization predicates.
- Added database-native `COUNT`, `SUM`, `AVG`, `MIN` and `MAX` over the complete
  authorized result set, canonical decimal-string results, stable query identity,
  optional bounded grouped buckets and bounded day/week/month trends with explicit
  empty buckets.
- Reused current member permission, field-read projection and data-scope rules
  before aggregation. Exact historical execution reads the published schema and
  data-source snapshots while still applying current authority and fails closed
  when pinned field metadata no longer matches.
- Extended immutable dashboard widgets with pinned statistics request and field
  identity/code/name/type/query-type metadata. Added `STAT_VALUE`, `BAR_CHART`,
  `PIE_CHART` and `LINE_TREND`; legacy `STAT_COUNT` now also executes through the
  exact-version statistics path. One widget failure remains isolated.
- Added the function-first data-source statistics tester, dashboard capability
  controls and runtime metric/bar/pie/line renderers with loading, empty,
  unavailable and per-widget error states. Final responsive work was not mixed
  into this functional batch.

## Verification

- Final focused backend verification passed `69/69` across `17` domain, mapping,
  native SQL, exact-history, service, runtime, migration and JDBC repository test
  classes.
- Final affected backend regression passed Core `25/25` and Module `242/242`,
  totaling `267/267` with no failure, error or skip.
- Backend production and test compilation passed across all `13` child Maven
  modules.
- Real Spring Boot + MySQL 8.0.44 + Redis 7.4 authenticated HTTP journey passed
  `1/1`; all `74` Flyway migrations applied through V8.52. It proved scalar,
  group and trend results, immutable source/dashboard pins, source and dashboard
  re-publication, exact replay, tenant hiding and a real ordinary-member `SELF`
  owner scope across statistics, metric, group and list widgets.
- Official MySQL 8.4.10 upgrade verification passed `1/1`: Flyway validated `74`
  migrations and replayed cleanly. Exact probes confirmed the three dashboard
  tables now have `70` columns, six restrictive foreign keys, `23` checks and
  `17` physical indexes, and both obsolete draft-publication unique indexes are
  absent.
- Frontend full verification passed `73` files / `353` tests; independent
  typecheck and the Vite production build passed over `5706` transformed modules.
  Only the established large-chunk warning remains.
- Staged and unstaged `git diff --check`, strict UTF-8/mojibake validation, both
  framework structure validators and the VS4 machine-contract validator passed.

## Demonstrated journey

An administrator publishes records and a data source, executes real count/value,
group and trend statistics, then publishes a system-home dashboard containing
metric and chart widgets. Re-publishing the source cannot move the old dashboard;
re-publishing the same dashboard draft against the new source produces a new
immutable version. An ordinary member with `SELF` scope sees only the single
record they own through both the dashboard and database aggregates, while another
tenant sees neither the source nor dashboard.

## Integration corrections

- Removed public exact-version statistics routes; only the active-code API is
  exposed and dashboards use the exact service internally.
- Excluded `MONEY` from numeric statistics until currency-aware aggregation exists,
  and excluded `TEXTAREA` from grouping because the contract caps group keys.
- Forced text keys and labels to `utf8mb4_bin` before database grouping so values
  such as `A` and `a` are not silently merged by the record-value table collation.
- Removed two obsolete draft-publication unique indexes that incorrectly rejected
  valid publication of the same draft after its pinned source/schema changed;
  root CAS, version uniqueness and active fingerprint replay retain race safety.
- Made legacy `STAT_COUNT` use the same exact-version authority path as the new
  widgets, avoiding divergent count/list/statistics permission behavior.

## Deferred

- KPI target ownership, calculation snapshots, attainment and warning rules
- report definitions, scheduling, exports and deliveries
- cross-module, external API and direct-database data sources
- shared caches, personal/application/module dashboards and click-through builders
- responsive, accessibility, animation and exhaustive visual-state hardening
