# FAST-MODULE-DATASOURCE-PUBLISH-61 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-01T03:16:28+08:00`
- Delivery mode: functionality first; responsive, accessibility and exhaustive
  visual hardening remain deferred to the final unified pass.

## Delivered

- Added tenant-scoped single-module data-source roots and immutable publication
  versions in V8.50. Draft saves use optimistic CAS; checks are stable and
  read-only; unchanged publishes replay the existing version; later draft edits
  cannot affect the active runtime snapshot.
- Added bounded ordered outputs, typed fixed filters, optional default sort and
  time fields, published-schema revalidation and canonical runtime-readable JSON
  values. Catalog capabilities now come from the immutable active module
  snapshot, so `FILTER` and `SORT` index modes cannot diverge between check and
  runtime execution.
- Added tenant-scoped management APIs for catalog, list, create, detail, draft,
  check, publish and version history, guarded by system administration plus
  `module.config.manage`. Existing and newly provisioned ROOT roles receive the
  action, and dynamic `module.*` synchronization cannot disable or overwrite the
  static management permission.
- Added published runtime metadata and paged rows. Query execution delegates to
  the native module record runtime, preserving module permission, data scope,
  record state, field projection and query semantics; unreadable output fields
  are omitted instead of leaked.
- Added the function-first system-admin Data Sources editor and navigation with
  visual module/field/filter/sort/time controls, CAS state, check/publish,
  version history and a preview backed by the real published runtime endpoint.

## Verification

- Focused data-source, permission and controller verification passed `32/32`.
- Final affected backend regression passed Core `25/25`, Platform `36/36` and
  Module `191/191`, totaling `252/252` with no failure, error or skip.
- Backend test compilation passed across all `13` child Maven modules.
- Real Spring Boot + MySQL 8.0.44 + Redis 7.4 authenticated HTTP journey passed
  `1/1`; all `72` Flyway migrations applied through V8.50. The journey proved
  draft CAS, check, exact publish replay, native filtered rows, unpublished draft
  isolation and cross-tenant hiding while retaining the existing Flow journey.
- Official MySQL 8.4.10 upgrade verification passed `1/1`: Flyway validated `72`
  migrations, applied `30` migrations from V8.20 through V8.50 and applied `0`
  on replay. An independent empty-container probe confirmed `2` data-source
  tables, `30` columns, `5` foreign keys, `12` checks and `14` actual indexes,
  plus the management permission, ROOT grant and permission-version bumps.
- Frontend full verification passed `65` files / `326` tests; independent
  typecheck and the Vite production build passed.
- Staged and unstaged `git diff --check` passed. Strict UTF-8/mojibake validation
  scanned `1835` active text files with zero issues. Both framework structure
  validators and the VS4 `43`-endpoint / `49`-field contract validator passed.

## Demonstrated journey

An administrator publishes a real module, creates three active records, creates
one data source selecting and sorting its `route` field, fixes an `EMPTY` filter,
checks and publishes it, then repeats the exact publish without creating a
second version. Runtime metadata and rows use the immutable publication and
native module authority. A later unmatched draft remains invisible to runtime,
and switching to another tenant hides both management and runtime data.

## Integration corrections

- Removed `final` from JDBC repository/catalog beans so Spring persistence and
  transaction proxies can construct the real application context.
- Protected static permissions from dynamic namespace reconciliation; otherwise
  publishing `module.*` permissions disabled `module.config.manage` because the
  codes intentionally share a prefix.
- Made catalog filter/sort capabilities read `is_filterable` and `index_mode`
  from the immutable active configuration snapshot, eliminating a save-time vs
  runtime capability mismatch.
- Updated the cumulative MySQL 8.4 schema journey from V8.49 to V8.50 counts and
  asserted the new tables, permission, ROOT grant and authorization epochs.

## Deferred

- Multi-module joins, external API and direct-database data sources.
- Aggregate/group/trend queries, configurable dashboard/widget persistence,
  KPI targets, reports, schedules, caches and Agent tools.
- Responsive, accessibility and exhaustive visual-state hardening.
