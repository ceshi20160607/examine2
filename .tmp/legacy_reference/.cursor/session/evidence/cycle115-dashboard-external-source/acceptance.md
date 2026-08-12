# Cycle115 dashboard/external-source acceptance

Status: `PASS`

Verdict: `PASS`

The module, Flow/Web integration, frontend production build, schema migration
and packaged cold-start gates are complete. External HTTP/JDBC publications now
enter the same rows/statistics/dashboard/KPI/report runtime as native and joined
sources instead of stopping at publication.

## Delivered capability

- Dashboard scopes use the existing dashboard root/version/widget engine for
  `SYSTEM_HOME`, `APPLICATION_HOME`, `MODULE_HOME`, and owner-isolated
  `PERSONAL_HOME` views.
- Generic `/admin/dashboards` operations are now `SYSTEM_HOME` only.
  Application/module mutations first resolve the exact `(placement,scopeKey)`
  binding; all personal detail/save/check/publish/version operations first
  resolve an owner binding. Personal endpoints use the ordinary runtime member
  session rather than requiring configuration-admin permission. A missing or
  foreign binding is returned as not found.
- Personal views are saved and published through the same immutable dashboard
  service. Grid drag/resize, refresh, safe in-app click-through, style variants,
  ranking, progress, todo list, quick entry, KPI, data-list and chart widget
  contracts are supported.
- HTTP JSON and JDBC table publications are consumed through the existing safe
  readers by runtime rows, statistics, dashboard, KPI and report paths.
- `MULTI_MODULE_JOIN` is a first-class immutable data-source publication, not a
  dashboard/report-specific parallel model. A plan pins 2..8 exact published
  source versions and an explicit 1..10 second timeout and 1..100 row bound.
- Join plans are left-deep and acyclic. Every later input has exactly one edge
  with explicit left/right key, `INNER|LEFT` type, and
  `ONE_TO_ONE|ONE_TO_MANY|MANY_TO_ONE` cardinality.
- Publication checks resolve every child under the same system/tenant, reject
  self/nested joins, require at least two distinct modules, require the exact
  child schema to remain available, negotiate readable field/query types, and
  pin trusted logical id/name/type/query-type capabilities into the published
  snapshot.
- Output collision handling is structural: every projection must use the
  `sourceAlias__field` namespace and duplicate source projections, output codes,
  or logical identities block publication.
- Child rows are read concurrently through the existing exact-version runtime,
  so native module-view and row data-scope authorization remains in force and
  HTTP/JDBC retain their tenant, SecretRef, SSRF and timeout boundaries.
- Join execution detects source truncation, output truncation, timeouts and
  cardinality violations. `ALLOW_PARTIAL_LEFT` can only isolate a failed right
  side of a `LEFT` edge. The result exposes `partial`, bounded source size and
  safe failed aliases; anchor/inner failures fail closed.
- Statistics/KPI never silently aggregate a partial join. A bounded partial
  join raises `STATISTICS_SOURCE_PARTIAL`. Dashboard list widgets expose
  `PARTIAL`; report runtime exposes partial/source/failure metadata; report
  export and AI report reads reject partial pages.
- Joined rows carry an alias-to-source-record drill-through map. Dashboard and
  report runtime row contracts preserve it.
- Dashboard, KPI and report catalogs consume the join publication's pinned
  capabilities, so downstream definitions continue to pin the join source
  version and field identities normally.

## Migration

`sql/migration/V8_88_0__dashboard_scopes_external_consumption.sql`:

- preserves one dashboard engine while adding scope bindings and a generated
  singleton key for `SYSTEM_HOME`;
- adds widget behavior columns and mainstream widget checks;
- adds `ck_module_data_source_join_plan` and
  `ck_module_data_source_version_join_plan` over the existing draft/snapshot
  JSON, enforcing top-level input/edge/projection/timeout/row/failure-mode
  bounds without creating parallel source tables;
- contains no `DROP TABLE`, data deletion, or data rewrite.

## Verification evidence

- Java 21 production and test compilation: PASS.
- focused P8 backend set: 58/58 PASS, zero failures/errors/skips.
- full `examine-module`: 592/592 PASS, zero failures/errors/skips.
- full `examine-flow`: 408/408 PASS; full `examine-work`: 98/98 PASS.
- frontend focused affected set: 41/41 PASS.
- frontend full suite: 127 files, 672/672 tests PASS; typecheck and production build PASS.
- New focused contracts cover exact source pins and capability publication,
  namespace/left-deep validation, one-to-many followed by later join (no false
  cardinality failure), LEFT partial isolation, cardinality fail-closed,
  drill-through, namespaced owner restriction, partial statistics rejection,
  downstream dashboard/KPI/report join catalogs, migration checks, and
  controller scope-boundary routing.
- The whole-plan timeout contract is exercised with a delayed child and returns
  a sanitized failure within the configured one-second bound.
- Flow API real-application journey proves HTTP/JDBC metadata, rows,
  capabilities and statistics consumption through the unified runtime.
- `P4A1SchemaIntegrationTest` proves empty-schema and populated V4.5 upgrade to
  V8.88.0 with 110/110 migrations and the new scoped dashboard constraints.
- packaged cold start proves 110/110 migrations, backend health `UP`, login
  `OK` and frontend HTTP 200.

## Acceptance boundary

The bounded multi-module join engine, downstream capability catalogs, partial
failure behavior and ordinary-member owner restrictions are covered by focused
and full module tests. HTTP/JDBC unified runtime and database migration are also
covered by real application/MySQL journeys. A separate browser styling pass is
not part of this feature-first cycle and remains consolidated UI work.
