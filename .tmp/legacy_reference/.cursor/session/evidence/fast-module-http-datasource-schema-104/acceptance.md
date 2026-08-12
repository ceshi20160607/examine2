# FAST-MODULE-HTTP-DATASOURCE-SCHEMA-104 Acceptance

- Verdict: PASS
- Accepted at: 2026-08-05T14:50:00+08:00
- Scope: administrator-only safe HTTP JSON schema discovery and explicit draft
  projection to the existing anchor-module schema. HTTP publication and runtime
  remain deliberately unavailable.

## Delivered behavior

- Extended `DataSourceDraft` with a default-empty ordered
  `httpFieldProjections` list. Old B103 JSON, four-argument and six-argument
  Java callers remain compatible, and the exact Native canonical publication
  identity is unchanged.
- Added the bounded mapping
  `sourceField -> anchor fieldCode + sourceType`. Source fields are exact
  top-level JSON keys rather than JSONPath; list, name, type and uniqueness
  boundaries are enforced in domain, request mapping and UI validation.
- Reused the published anchor-module catalog so external values cannot invent
  their own field names, types, sensitivity or permission metadata. HTTP check
  rejects missing, unavailable or conservatively type-incompatible targets.
- Added administrator `POST .../{id}/draft:schema-discovery` with exact
  `draftVersion` CAS. Discovery is read-only; mappings are saved only through
  the existing save-draft CAS.
- Refactored B103 connection checking and B104 discovery onto one package-local
  safe probe. CHECK sends one fixed `{"page":1,"size":1}` request and
  DISCOVERY sends one fixed `{"page":1,"size":25}` request. They share the
  deny-by-default host allowlist, exact tenant/system SecretRef scope, resolved
  secret close/byte clearing, hardened Core transport, timeout, 64 KiB body
  bound, strict JSON parser and fixed redacted failure summaries.
- Discovery accepts only 1..25 flat scalar/null rows and a union of at most 50
  fields. It performs deterministic token-only type inference, numeric
  promotion, nullable/missing handling, UNKNOWN/MIXED review, strict field-name
  validation, NFKC/case-fold collision detection and exact-name ordering.
  Responses contain schema metadata only and never samples or raw payloads.
- Extended the existing data-source page only. HTTP drafts hide Native
  output/filter/sort/time editors, explicitly discover schema, map compatible
  fields, reorder/remove mappings and save with the current draft version.
  Dirty discovery saves first, double submission is disabled and generation,
  source-id and draft-version guards discard stale responses.
- HTTP validation keeps `SOURCE_RUNTIME_UNAVAILABLE` as the first blocker,
  forbids legacy Native query configuration and requires a valid projection for
  a complete draft. Existing publish refusal and all Native runtime consumers
  remain unchanged.
- Reused `draft_json`; no migration, new table, JDBC runtime, schema cache,
  published HTTP snapshot or downstream analytics integration was added.

## Automated verification

- Combined B104 data-source backend selection: `51/51` passed. The security
  subset passed `23/23`; standalone Web data-source controller passed `1/1`.
- Full Core passed `81/81`; full Module passed `509/509`, with zero failures,
  errors or skips. A focused post-recompile report-schedule regression passed
  `2/2`.
- Real `FlowApiJourneyIntegrationTest` passed `1/1` in `207.3s`; the Maven
  reactor completed in `3:52`, compiled all 14 modules and applied all 100
  Flyway migrations. Its deterministic transport proved the fixed discovery
  request, safe response, explicit projection JSON roundtrip and publication
  refusal.
- Frontend focused data-source tests passed 2 files / `15/15`; full frontend
  passed 108 files / `563/563`; typecheck passed and the production build
  transformed 5,769 modules with only the existing large-chunk warning.
- Real Microsoft Edge journey passed `1/1` in `13.8s`. It created a real
  system and published anchor module, exercised two default-deny discovery
  calls, persisted and reloaded `external_id -> external_id / STRING`, observed
  the first runtime blocker and received the expected 422 publish refusal.
- The first source-classpath launch exposed one pre-existing stale generated
  `ReportScheduleService.class` descriptor while its source was already
  correct. The exact generated class was rebuilt from source without clean,
  package, install or jar; the production application then became healthy and
  the Edge journey plus focused report-schedule regression passed.
- Strict UTF-8, framework Active mode, project progress, all seven cadence
  cases, VS4 contract and API validation passed. VS4 remains 43 endpoints, 77
  DTOs, 49 field types and 20 tables. Scoped `git diff --check` passed.

## Real data and browser evidence

- The fresh browser database applied `100/100` migrations and contained one
  HTTP draft, one explicit projection, zero published HTTP versions and zero
  persisted `Authorization` / `Bearer` markers before cleanup.
- Stored projection: `external_id -> external_id / STRING`.
- Screenshot:
  `.cursor/session/evidence/fast-module-http-datasource-schema-104/http-schema-projection.png`
- Screenshot SHA-256:
  `c16407a903b3a8d1898294eed4468ae43ec668b14ad2ec5359c504dd3d70ee9c`
- Playwright JSON:
  `.cursor/session/evidence/fast-module-http-datasource-schema-104/playwright-results.json`
- Playwright JSON SHA-256:
  `ed280cc266137acfe498d206d1809256abb77d4551ac0dc8ea1c50d30b198bce`
- Temporary source backend, managed Vite ports and exact Batch104 MySQL/Redis
  containers were verified stopped/removed after capture.

## Boundaries

- No HTTP publish/runtime, external pagination, filter/sort, custom request,
  retry/cache/job, schema fingerprint, nested JSON/JSONPath, JDBC, report,
  dashboard, KPI, Agent, route/layout or responsive work.
- No package, install or jar command was run. Package checkpoint attempts remain
  `0/3`; CP1, CP2 and CP3 stay frozen at their project-level gates.
