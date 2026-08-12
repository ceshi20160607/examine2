# FAST-REPORT-XLSX-EXPORT-68 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T01:20:25+08:00`
- Delivery mode: functionality-first parallel slice; PDF, schedules, external
  delivery and responsive/visual hardening remain separate follow-up nodes.

## Delivered

- Added manual report XLSX start/list/detail/download APIs. `requestKey` and the
  `Idempotency-Key` header form one stable identity; replay returns the original
  run and its exact report/source version even after the active report moves.
- Added a durable report export worker over the existing job framework. The job
  payload contains only the export identity; execution re-resolves the current
  active tenant member, permission epoch, permissions, readable fields and row
  data scope instead of trusting an enqueue-time permission snapshot.
- Added immutable report version, data-source version and ordered-field snapshots,
  owner-scoped history, state CAS, terminal failure cleanup and tenant-safe result
  download. Failed runs cannot retain a partial file.
- Added a report-specific XLSX codec path with one ordered header row, canonical/
  display value handling, a summary sheet and an exact `5000`-row cap with an
  explicit truncation flag.
- Added `V8_56_0` and JDBC persistence with stable replay identity, four-state
  constraints, restrictive report/source/member references, bounded result
  content and indexed owner/status lookups.
- Added report export success/failure notification through the existing published
  export templates, audit records and deterministic delivery keys.
- Added the functional frontend export action and history drawer with queued,
  running, succeeded, failed, retry-key reuse, row/truncation, error, file and
  download states. Responsive styling remains deferred.

## Verification

- Focused backend verification passed `22/22`: report export migration/JDBC/API/
  service/worker/XLSX `16`, plus exact-publication report runtime `6`.
- Final affected backend regression passed Core `31/31`, Platform `43/43`, Module
  `330/330` and Event `30/30`, totaling `434/434` with no failure, error or skip.
- The real Spring Boot + MySQL 8.0.44 + Redis 7.4 authenticated HTTP journey
  passed `1/1`. It started and replayed a durable export, reauthorized execution,
  delivered the result notification, downloaded and opened the workbook, and
  proved tenant-safe list/detail/download hiding.
- The independent MySQL 8.4 schema test passed `1/1`, applied `36` incremental
  migrations and validated all `78` migrations. The export run table has `39`
  columns, four restrictive foreign keys, eight checks and nine physical indexes.
- The journey production/test-compiled all `13` child Maven modules.
- Full frontend verification passed `86` files / `391` tests, Vue typecheck and
  the Vite production build over `5740` transformed modules. Report-related
  focused verification passed `4` files / `8` tests.
- Staged and unstaged diff checks, strict UTF-8/mojibake validation, framework
  structure validation, cadence validation and both VS4 machine-contract
  validators passed.

## Demonstrated journey

An authorized member starts an XLSX export of the active report. A replay with
the same stable request identity returns the same durable run. The worker resolves
the current principal and exact report/source publication, pages three authorized
rows, writes the two pinned fields in their configured order, stores the completed
artifact and sends one result notification. The downloaded workbook is opened and
its header, data-row count and summary are verified; another tenant cannot list,
resolve or download the run.

## Corrections made during integration

- Fixed one Java effectively-final compilation issue introduced while joining the
  independently written worker and persistence contracts.
- Corrected the API reflection test to inspect the explicit header name and use
  the Java-time Jackson module for DTO serialization.
- Reused the already published generic export result templates so report jobs do
  not silently create skipped notifications for an unknown template code.
- Aligned schema assertions with the actual prefix-inclusive report foreign-key/
  check totals and MySQL's physical index set.

## Deferred

- PDF report output and print-layout composition
- schedules, calendars, retries, recipients and automated delivery
- external API/database data-source execution
- responsive, accessibility, animation and exhaustive visual-state hardening
