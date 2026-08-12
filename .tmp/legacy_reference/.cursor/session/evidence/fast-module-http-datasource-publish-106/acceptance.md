# FAST-MODULE-HTTP-DATASOURCE-PUBLISH-106 Acceptance

- Verdict: PASS
- Accepted at: 2026-08-05T16:43:39+08:00
- Scope: publish one immutable HTTP JSON data-source snapshot only after one
  fresh safe projection preflight, while ordinary HTTP runtime and every
  Native-only downstream consumer remain unavailable.

## Delivered behavior

- HTTP publication keeps exact draft-version CAS, ownership, anchor-schema
  validation and immutable fingerprint/version semantics. Local publication
  ignores only the deliberately retained `SOURCE_RUNTIME_UNAVAILABLE` check
  issue; every other blocker fails before outbound I/O.
- An identical active HTTP snapshot replays before preflight with zero network
  calls. A genuinely new publication performs exactly one fixed DISCOVERY POST
  `{"page":1,"size":25}` and then the existing repository publish CAS. Failed
  preflight or CAS creates no version and is never retried automatically.
- The server preflight requires 1..25 bounded flat rows, strict source names,
  collision-free field identity, every projected field on every row and at
  least one non-null type proof per projection. STRING/INTEGER/BOOLEAN are
  exact; DECIMAL accepts integral or decimal JSON numbers. Missing, null-only,
  mixed or drifted fields fail the complete publication with fixed redacted
  errors.
- HTTP STRING projections targeting RICH_TEXT are rejected before preflight.
  SecretRef lifecycle, allowlist, TLS/redirect/timeout/64 KiB limits and byte
  clearing continue through the hardened outbound probe.
- Published HTTP snapshots contain connection configuration and ordered
  projections only. No response row, sample value, inferred schema or
  preflight evidence is persisted.
- Active and pinned HTTP snapshots are rejected before Native schema/record or
  statistics gateway access with `DATA_SOURCE_HTTP_RUNTIME_UNAVAILABLE`.
  Dashboard, report and KPI source catalogs filter both active and pinned HTTP
  versions before module/JDBC lookup. Native behavior is unchanged.
- The existing data-source page publishes a mapped HTTP draft. A dirty action
  saves once and publishes the returned draft version directly, without the
  ordinary check or retry. Double-submit and stale source/generation/version
  responses are guarded. Successful HTTP publication can show active/version
  history, while Native rows, pagination and statistics stay hidden.

## Automated verification

- Focused publication and downstream-isolation selection passed `39/39`:
  service sequencing, HTTP preflight, active/pinned runtime, statistics,
  dashboard, report and KPI coverage all passed with zero failures/errors.
- The complete external HTTP security selection passed `40/40`, including 8
  publication-preflight, 9 preview, 11 connection-check, 10 discovery and 2
  Spring configuration tests.
- Full Core passed `81/81`; full Module passed `537/537`, with zero failures,
  errors or skips.
- Real `FlowApiJourneyIntegrationTest` passed `1/1` in `286.8s`; the complete
  14-module Maven reactor passed in `6:26` with all 100 Flyway migrations. It
  proved one fresh publication request, zero-call replay, immutable no-sample
  snapshot, active runtime/statistics rejection and unchanged Native journeys.
- Frontend focused data-source tests passed 2 files / `21/21`; full frontend
  passed 108 files / `569/569`. Typecheck passed. Production build passed,
  transformed 5,769 modules and emitted only the existing large-chunk warning.
- Real Microsoft Edge passed `1/1` (`14.2s` test, `36.0s` total). The page
  performed one dirty save and one publication, no ordinary check and no
  runtime request, displayed only the fixed safe-target message, leaked no
  endpoint through that error and created no active version.

## Real data and browser evidence

- The isolated browser database applied 100 migrations. Four browser attempts
  created four HTTP drafts with four explicit projections; all four remained
  unpublished after the default-deny preflight. Authorization/Bearer markers
  and sample-value markers were both zero.
- The accepted attempt ended at draft version `3`, active version `NULL`, with
  projection `external_id -> external_id / STRING` and an empty version list.
- Screenshot:
  `.cursor/session/evidence/fast-module-http-datasource-publish-106/http-publish-default-deny.png`
- Screenshot SHA-256:
  `3544d48af7ca206e82a354d5591253271c25f81e7cb308eccc86ba4bf744f6ff`
- Playwright JSON:
  `.cursor/session/evidence/fast-module-http-datasource-publish-106/playwright-results.json`
- Playwright JSON SHA-256:
  `1872235d38cf1aace6e56965bf29449b39fa2b889cfa0195b1787f5c4d48da45`

## Environment and boundaries

- The real browser run used the existing verification archive only as a
  dependency carrier and overlaid all current `target/classes`; it did not
  package or install. The exact backend process, managed Vite server, ports
  `18080/5177/33316/36380` and isolated MySQL/Redis containers were verified
  stopped or removed after capture.
- No published HTTP rows, page 2+, total, filter, sort, search, HTTP
  statistics, dashboard/report/KPI/Agent execution, export, schedule, cache,
  job, custom request controls, JDBC source or responsive hardening was added.
- No package checkpoint was attempted. Package attempts remain `0/3`; CP1,
  CP2 and CP3 stay frozen at their project-level gates.
