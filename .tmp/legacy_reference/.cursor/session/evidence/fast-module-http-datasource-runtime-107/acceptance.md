# FAST-MODULE-HTTP-DATASOURCE-RUNTIME-107 Acceptance

- Verdict: PASS
- Accepted at: 2026-08-05T17:20:00+08:00
- Scope: explicitly read one fixed first page from an active or exactly pinned
  published `HTTP_JSON` snapshot without modelling remote rows as Native
  records or enabling any Native-only downstream consumer.

## Delivered behavior

- Ordinary authorized members have HTTP-specific active and pinned read APIs:
  `GET /api/v1/systems/{systemId}/data-sources/{code}/http-rows` and
  `GET /api/v1/systems/{systemId}/data-sources/{dataSourceId}/versions/{versionId}/http-rows`.
  Both reuse tenant/source/version ownership and the current module-view grant.
  Missing, foreign, unpublished or wrong-kind publications fail before HTTP
  execution.
- A dedicated published-row reader performs exactly one fixed POST
  `{"page":1,"size":25}` per explicit read. It exposes no caller-controlled
  URL, method, headers, body, page, size, filter, sort or retry, and preserves
  the existing allowlist, safe-target, redirect/TLS, timeout, 64 KiB, scoped
  SecretRef and secret/response-byte clearing boundaries.
- The reader accepts 0..25 flat rows and a page-wide union of at most 50 strict,
  NFKC/case-fold collision-free source names. It applies the immutable
  publication projection order and decodes exact STRING, INTEGER, DECIMAL and
  BOOLEAN values. Every projected field must exist on every row; each field on
  a non-empty page requires at least one non-null type proof. Missing,
  null-only, mixed, out-of-range or structurally drifted pages fail completely
  with fixed redacted errors.
- The response contains only data-source/version identity, ordered
  `fieldCode/sourceType`, one-based `rowIndex` and typed projected values. It
  excludes sourceField, endpoint, SecretRef, raw/unprojected values, fake
  record identities, title/status/version, totals, query hashes and record
  actions. Result and row `toString()` redact all values.
- Existing generic metadata/rows still reject HTTP with
  `DATA_SOURCE_HTTP_RUNTIME_UNAVAILABLE`. HTTP statistics, dashboard, report,
  KPI and inherited AI consumers remain unavailable. Native active/pinned
  runtime, statistics and catalogs remain compatible and make no HTTP call.
- The existing data-source page shows one explicit load action only for an
  active HTTP publication. Opening, selecting, refreshing and publishing never
  auto-load remote rows. Double-submit, changed selection and changed active
  version responses are discarded. The table uses Vue text rendering and has
  no pagination, total, filter, sort, statistics or record action.

## Automated verification

- The B103-B107 external HTTP security selection passed `48/48`, including
  the new published reader's 8 fixed-request, typed/null/order, resource,
  collision, allowlist, secret lifecycle, transport and redaction tests.
- Runtime/API focused verification passed Module `7/7` and Web `2/2`; a root
  cross-selection of published reader, runtime and controller passed `17/17`
  across the 14-module compile reactor.
- Full Core passed `81/81`; full Module passed `548/548`, with zero failures,
  errors or skips.
- Real `FlowApiJourneyIntegrationTest` passed `1/1` in `234.1s`; the complete
  14-module reactor passed in `4:20` with all 100 Flyway migrations. It proved
  active and pinned reads each make one fixed request, return the same safe
  projection, persist nothing, and that cross-tenant, generic Native runtime
  and HTTP statistics paths make no additional request.
- Frontend focused data-source tests passed 2 files / `27/27`; full frontend
  passed 108 files / `575/575`. Typecheck passed. Production build passed,
  transformed 5,769 modules and emitted only the existing large-chunk warning.
- The accepted real Microsoft Edge run passed `1/1` (`5.8s` test, `12.2s`
  total). It proved zero runtime request before the explicit action, one request
  after it, safe field/type/value rendering, literal escaped markup and absent
  Native statistics/preview controls.

## Real data and browser evidence

- The accepted Edge journey used the real authenticated shell, system/module
  provisioning, HTTP draft persistence, MySQL, Redis and current frontend. Its
  published-version metadata and safe runtime row response were supplied by an
  exact browser API-contract fixture because the production browser backend
  correctly has no test-only outbound transport. The real backend success,
  egress count, authorization, isolation and zero-write path is covered by the
  Spring Flow journey above.
- The isolated browser database applied 100 migrations. Three fixture attempts
  created three HTTP drafts with three explicit projections, zero published
  HTTP versions, zero Authorization/Bearer markers and zero remote sample
  markers.
- Screenshot:
  `.cursor/session/evidence/fast-module-http-datasource-runtime-107/http-published-rows.png`
- Screenshot SHA-256:
  `186fa304a28211205dc6c2ad22cb1b857fc4b2732a08e3c935f53c115005bf4f`
- Playwright JSON SHA-256:
  `dac858b8d69db689352b5f70ccaff0882abeaf0257eb8b084e56aae4914e79b3`
- Playwright XML SHA-256:
  `c65f07393a95dd3f390f54a50763a1e1e5f5a1abafb681c26190cc2700cf98e8`

## Environment and boundaries

- The browser backend used the existing verification archive only as a
  third-party dependency carrier. All current project classes and mapper
  resources came from the 13 current `target/classes` directories; old
  `examine-*` jars were excluded. No package or install was run.
- The exact backend process, managed Vite server, ports
  `18080/5177/33317/36381`, isolated MySQL/Redis containers and temporary
  dependency extraction were verified stopped or removed after capture.
- No page 2+, total, filters, sorts, search, HTTP statistics,
  dashboard/report/KPI/Agent execution, export, schedule, cache/job, custom
  request controls, JDBC source or responsive/visual hardening was added.
- No package checkpoint was attempted. Package attempts remain `0/3`; CP1,
  CP2 and CP3 stay frozen at their project-level gates.
