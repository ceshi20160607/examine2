# FAST-MODULE-HTTP-DATASOURCE-CHECK-103 Acceptance

- Verdict: PASS
- Accepted at: 2026-08-05T13:57:07+08:00
- Scope: administrator-only `HTTP_JSON` data-source draft configuration and
  explicit safe connection check, with Native behavior preserved and HTTP
  publication/runtime deliberately blocked.

## Delivered behavior

- Added `NATIVE_MODULE` / `HTTP_JSON` draft kinds and the bounded HTTPS
  endpoint, optional SecretRef and 1..10 second timeout contract. Old stored
  JSON, the four-argument Java constructor and the Native publication canonical
  identity remain compatible.
- Reused `un_module_data_source.draft_json`; no migration, table or column was
  added. `moduleId` remains the schema and permission anchor.
- Extended create/save/detail mapping and the existing data-source page. HTTP
  configuration is editable without selecting runtime output fields; stale
  connection results clear on edit and duplicate checks are disabled.
- Added `POST .../{id}/draft:connection-check` with exact `draftVersion` CAS
  and a six-field safe result. The UI and API never accept a custom method,
  header, query or request body.
- Added a Module-local deny-by-default host allowlist with exact and controlled
  wildcard rules. The checker performs one fixed POST through Core
  `OutboundHttpTransport`; the production application still uses Flow's
  hardened HTTPS/DNS/private-address/redirect/peer/size adapter without any
  Module-to-Flow implementation dependency.
- Enforced exact tenant/system `env://EXAMINE_DS_S{systemId}_T{tenantId}_`
  SecretRef scope, optional Bearer use, full resolver scope and deterministic
  clearing of the resolved value plus copied bytes.
- Enforced 64 KiB response, 200 row, 50 field, scalar/null, name/value length,
  nesting, duplicate-key and trailing-token limits. Target, secret, status,
  JSON, contract and transport failures map to stable bounded codes/messages
  without endpoint, body, header, secret or exception details.
- HTTP draft checks always include `SOURCE_RUNTIME_UNAVAILABLE`; the existing
  publish path refuses them and the UI disables publication. Native runtime,
  statistics, report, dashboard and KPI consumers remain unchanged.
- Corrected the existing data-source UI CAS owner so save, connection check and
  publish all use `draftVersion`, even after root version changes at publish.

## Automated verification

- Backend B103 combined focused selection: `32/32` passed, including legacy
  JSON, canonical identity, API mapping, Native regression, HTTP blocker,
  repository JSON roundtrip and connection-check contracts.
- HTTP security checker and configuration subset: `13/13` passed.
- Web standalone `DataSourceControllerTest`: `1/1` passed.
- Full Core: `81/81`; full Module: `492/492`; zero failures, errors or skips.
- Real `FlowApiJourneyIntegrationTest`: `1/1` passed in `229.8s` after fresh
  MySQL/Redis startup; Maven reactor completed in `4:26` and compiled all 14
  modules. The first run exposed only a root-owned evidence SQL column typo;
  after correcting `data_source_id` to the real `id` primary key, the unchanged
  product journey passed.
- Frontend focused data-source tests: 2 files, `13/13`; full frontend: 108
  files, `561/561`; typecheck PASS; production build PASS with 5769 modules and
  only the existing large-chunk warning.
- Real Microsoft Edge function journey: `1/1` passed in `15.4s` total. It
  created an actual published anchor module, created and edited an HTTP draft,
  exercised two real default-deny checks, proved automatic save with zero
  output fields, observed safe `SAFE_TARGET`, read the stored draft, observed
  `SOURCE_RUNTIME_UNAVAILABLE` and received the expected publish refusal.
- Strict UTF-8, framework Active mode, project-progress, seven cadence cases,
  VS4 contract/API validation and `git diff --check` passed. VS4 remains 43
  endpoints, 77 DTOs, 49 field types and 20 tables; only existing CRLF notices
  were emitted by Git.

## Real data and browser evidence

- Fresh browser database applied `100/100` Flyway migrations.
- Before cleanup it contained two real HTTP drafts from the assertion/final
  Edge runs, zero active HTTP versions, and two drafts matching the denied test
  target with neither `Authorization` nor `Bearer` persisted.
- Screenshot:
  `.cursor/session/evidence/fast-module-http-datasource-check-103/http-draft-safe-check.png`
- Screenshot SHA-256:
  `895ca9dd7aa8e0646e4bff57c5d6acb187437de7008a1d7e98cd8b070414fe0f`
- Preserved Playwright JSON:
  `.cursor/session/evidence/fast-module-http-datasource-check-103/playwright-results.json`
- Playwright JSON SHA-256:
  `02193d0ae3cac56c1ac4eea50a1fd13453eca009a9e85ba2232897477b7ff8aa`
- Temporary source backend PID `28968`, containers
  `examine2-batch103-mysql` / `examine2-batch103-redis`, and managed Vite port
  `5177` were verified stopped/removed after evidence capture.

## Boundaries

- No HTTP row runtime, schema discovery/projection publication, published HTTP
  version, report/dashboard/KPI/statistics/Agent consumption, custom request,
  retry/scheduler, JDBC, migration, route/layout or responsive sweep.
- No package, install or jar command was run. Package checkpoint attempts remain
  `0/3`; CP1, CP2 and CP3 stay frozen at their project-level gates.
