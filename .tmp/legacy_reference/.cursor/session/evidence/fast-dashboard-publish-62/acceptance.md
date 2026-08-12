# FAST-DASHBOARD-PUBLISH-62 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-01T03:52:00+08:00`
- Delivery mode: functionality first; responsive, accessibility, drag-and-drop
  and exhaustive visual hardening remain deferred to the final unified pass.

## Delivered

- Added one tenant-scoped `SYSTEM_HOME` dashboard root, immutable published
  versions and immutable ordered version-widget rows in V8.51. Draft saves use
  optimistic CAS; checks are stable and read-only; unchanged publishes replay;
  one tenant cannot see another tenant's dashboard or pinned sources.
- Added 1..20 `STAT_COUNT` and `DATA_LIST` widgets with unique codes, immutable
  titles/source pins, server-enforced row limits and explicit non-overlapping
  12-column by 100-row desktop grid rectangles.
- Added management list/create/detail/draft/check/publish/version APIs and runtime
  by-code plus literal system-home APIs. Runtime reads only the active dashboard
  version and the exact historical data-source versions captured at publication.
- Extended the Batch61 data-source service/repository/runtime with exact version
  lookup. A later source publication therefore cannot move an already published
  dashboard to new filters, output fields or sort rules.
- Widget execution delegates to the native data-source runtime. Count and list
  preserve module permission, data scope, record state and field projection;
  a stable per-widget failure does not erase successful siblings.
- Added the function-first system-admin dashboard editor, numeric layout controls,
  source/version status, check/publish CAS, immutable version history and real
  runtime preview. Added the ordinary-member system dashboard page with count,
  list, loading, unavailable, empty and partial-error states.

## Verification

- Focused dashboard/data-source verification passed `19/19` across domain,
  service, migration, JDBC repository and both runtime services.
- Final affected backend regression passed Core `25/25` and Module `208/208`,
  totaling `233/233` with no failure, error or skip.
- Backend test compilation passed across all `13` child Maven modules.
- Real Spring Boot + MySQL 8.0.44 + Redis 7.4 authenticated HTTP journey passed
  `1/1`; all `73` Flyway migrations applied through V8.51. The journey proved
  dashboard draft CAS, check, exact replay, count/list rows, literal and generic
  runtime routes, historical source-version pinning, draft isolation and tenant
  hiding while retaining the full existing Flow journey.
- Official MySQL 8.4.10 upgrade verification passed `1/1`: Flyway validated `73`
  migrations, applied `31` migrations from V8.20 through V8.51 and applied `0`
  on replay. Exact schema probes confirmed `3` dashboard tables, `50` columns,
  `6` restrictive foreign keys, a five-column pinned-source FK, `20` checks and
  `18` actual indexes including MySQL's two required implicit FK indexes.
- Frontend full verification passed `70` files / `337` tests; independent
  typecheck and the Vite production build passed.
- Staged and unstaged `git diff --check`, strict UTF-8/mojibake checks, both
  framework structure validators and the VS4 contract validator passed.

## Demonstrated journey

An administrator publishes a real module and records, then publishes a filtered
data source and a `SYSTEM_HOME` dashboard containing one count and one list. The
runtime returns the exact scoped total and expected record. Re-publishing the
source with a filter that returns zero changes the source runtime but the
dashboard still reads its pinned historical version and returns the original
total. A later dashboard draft remains invisible, and another tenant sees neither
management roots nor runtime results.

## Integration corrections

- Removed a duplicate JDBC dashboard source catalog and retained the native
  adapter over the accepted data-source repository/catalog. This prevents a
  double Spring bean and avoids treating unavailable output fields as readable.
- Added `sourceDraftVersion` end to end because V8.51 persists it and version
  history must identify the draft generation that produced an immutable version.
- Wired `DashboardService` through an explicit UTC-clock configuration bean;
  direct component construction had no application `Clock` dependency.
- Corrected frontend save/publish CAS to use `draftVersion`, not root `version`;
  publication advances root version without changing draft generation.
- The MySQL 8.4 probe initially expected only 16 declared indexes. Inspection
  proved MySQL correctly adds two implicit child indexes for foreign keys, so the
  accepted physical schema expectation is 18.

## Deferred

- aggregate/group/trend statistics and chart widgets
- KPI targets/calculation records and report/schedule/export foundations
- cross-module, external API and direct-database data sources
- shared caches, personal/application/module dashboards, click-through builders
- responsive, accessibility, drag-and-drop and exhaustive visual hardening
