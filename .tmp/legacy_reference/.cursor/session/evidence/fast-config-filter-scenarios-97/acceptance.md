# FAST-CONFIG-FILTER-SCENARIOS-97 Acceptance

- Verdict: `PASS`
- AcceptedAt: `2026-08-05T05:22:18+08:00`
- PackageCheckpointAttempted: `false`
- SchemaMigrationAdded: `false`

## Delivered owner capability

- Added the canonical optional LIST-page layout contract
  `filterScenarios[{code,name,filter,sort}]` plus
  `defaultFilterScenarioCode`, stored only in existing `layout_json`.
- Draft mutation now rejects non-LIST ownership, unknown/missing keys,
  non-canonical or duplicate codes/names, invalid defaults, empty scenarios,
  filter depth/predicate overflow and invalid/duplicate sort definitions.
- Configuration check now blocks missing, disabled, sensitive, unsupported or
  non-indexed field references and invalid operator/value combinations by
  reusing the native record-query parser/compiler.
- The existing AI page-layout writer preserves native scenario keys under the
  same page-version CAS, preventing an older capability from erasing the new
  native configuration.
- Configuration Studio authors up to ten LIST scenarios with client-side exact
  JSON validation and default selection; FORM/DETAIL omit scenario keys.
- Runtime reads only the published enabled default LIST page. Default and
  manually selected scenarios copy only filter/sort, detach member saved views,
  reset page 1, synchronize the route and execute the existing query endpoint.
  Runtime schema capability and sensitive-field sanitization remain
  authoritative.

## Verification

- Backend focused owner regression: `33/33` tests, zero failures/errors/skips.
- Backend affected full regression: examine-core `78/78`, examine-module
  `452/452`, total `530/530`; all three Maven reactor modules `SUCCESS` on
  JDK 21.
- Frontend focused regression: `3` files, `35/35` tests.
- Frontend full regression: `104` files, `514/514` tests.
- Frontend production typecheck/build: pass; `5,767` modules transformed. The
  only build notice is the pre-existing chunk-size warning.
- Playwright collection: `1` file, `12` project tests discovered.
- Real Microsoft Edge desktop journey: `1/1` passed (`41.9s` test, `46.0s`
  total) against isolated MySQL 8.4 and Redis with all `97` Flyway migrations
  validated and applied.
- The browser journey proved exact LIST create/update bodies, V1 active
  readback, V2 draft non-leakage with unchanged active version, V2 published
  canonical readback, two active records, default alpha query, manual beta
  query, exact query request/response rows, route state and zero saved-view
  mutation.
- Strict UTF-8, canonical project progress, framework `Active`, VS4 PowerShell
  and JavaScript machine contracts (`43` endpoints), and `git diff --check`
  passed.

## Harness notes and cleanup

- The first isolated backend launch used only the current examine-module class
  overlay and failed during Spring metadata scanning because the matching
  current examine-core classes were absent from the older executable jar. The
  class-only overlay was corrected to all current reactor classes; no product
  source or package checkpoint changed as a result.
- The isolated backend/frontend processes, MySQL/Redis containers and temporary
  2,867-class overlay were stopped/removed after the passing journey. No ports
  or Batch97 containers remain.

## Deferred boundary

- Generic field read/write declarations and safe role grant sequencing remain
  the next native configuration owner gap.
- AI configuration suggestions remain blocked until that owner is accepted.
- Responsive, accessibility, exhaustive visual-state hardening, release gates,
  packaging checkpoints and final user acceptance remain later project gates.
