# FAST-REPORT-SCHEDULE-DELIVERY-69 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T02:01:29+08:00`
- Delivery mode: functionality-first parallel slice; report PDF, advanced
  calendars, external delivery channels and responsive/visual hardening remain
  separate follow-up work.

## Delivered

- Added tenant-scoped report schedule create/list/detail/update/enable/disable
  and next-fire preview APIs below each published report administration root.
  Schedules use stable report-scoped codes, optimistic versions, current owners,
  1..50 recipients and nested DAILY/WEEKLY cadence definitions.
- Added deterministic UTC/IANA scheduling. Weekly cadence supports multiple
  ordered weekdays; DST gaps advance by the exact transition gap and overlaps
  select the earlier offset once.
- Added isolated due production and restart-safe occurrence identity. Each due
  instant is snapshotted with the schedule definition and configured recipients,
  while the active immutable report/source publication is resolved only when the
  existing report XLSX export is started.
- Reused the report export worker as the authority for current owner membership,
  permissions, exact fields/source pins, row data scope, the 5000-row cap and
  XLSX generation. Schedule monitoring has leases, durable PENDING/RUNNING/
  SUCCEEDED/FAILED states and three bounded attempts.
- Revalidates every recipient against current active tenant membership before
  delivery. Stable member-message keys and immutable delivery facts make replay
  singular; recipients added later cannot see an earlier occurrence.
- Added current-recipient scheduled-run list/detail/download APIs. History and
  file authorization join the immutable delivery fact rather than mutable
  schedule recipients, and cross-tenant access is hidden.
- Added five MySQL tables for schedule configuration, current recipients,
  occurrence snapshots, occurrence-recipient snapshots and actual deliveries.
  CAS, `FOR UPDATE SKIP LOCKED`, scoped uniqueness and restrictive references
  protect concurrency and restart recovery.
- Added a functional report schedule manager and extended the runtime export
  drawer with separate manual/scheduled histories, status, details and XLSX
  download. Responsive styling remains deferred.

## Verification

- Focused backend verification passed `38/38`: schedule domain/service/API/
  worker/JDBC/migration/history `22`, plus the existing report export suite `16`.
- Full affected backend regression passed Core `31/31`, Platform `43/43`, Module
  `352/352` and Event `30/30`, totaling `456/456` with no failure, error or skip.
- All `13` Maven modules production- and test-compiled successfully.
- The real Spring Boot + MySQL 8.0.44 + Redis 7.4 authenticated HTTP journey
  passed `1/1` in `172.3s`. It created and forced one due schedule, produced one
  occurrence/export/delivery, opened the recipient XLSX, and proved replay
  singularity plus cross-tenant list/detail/download hiding.
- The independent MySQL 8.4 schema test passed `1/1`, applied `37` incremental
  migrations and validated all `79` migrations. The five schedule tables have
  `76` columns, `11` restrictive schedule foreign keys, `15` checks and `31`
  physical indexes including MySQL-created FK indexes.
- Frontend focused verification passed `6` files / `14` tests. Full frontend
  verification passed `88` files / `397` tests, Vue typecheck and the production
  build over `5743` transformed modules.
- Staged and unstaged diff checks, strict UTF-8/mojibake validation, base and
  instance framework validators, cadence `7/7`, and both VS4 machine-contract
  validators passed; the endpoint ledger remains `43`.

## Demonstrated journey

An administrator previews and creates a daily Asia/Shanghai delivery for a
published report and selects another active tenant member. The due producer
creates one occurrence and advances the next fire. The occurrence worker
reauthorizes the owner, starts the existing exact-publication XLSX job, monitors
it to success, revalidates the recipient and writes one inbox message plus one
delivery fact. The recipient lists the delivered occurrence and opens the XLSX;
replayed workers create neither another occurrence nor another delivery, while
another tenant cannot resolve the report or result.

## Corrections made during integration

- Unified the frontend/backend DTO on nested cadence and stable `id`,
  `scheduledAt` and `status` fields, and aligned the recipient bound to `50`.
- Replaced mutable schedule-recipient history authorization with occurrence
  recipient snapshots and immutable delivery facts.
- Fixed NULL-lease running claims and made transient monitor failures consume
  the bounded retry count instead of retrying forever.
- Made occurrence and delivery replay compare logical identities rather than new
  snowflake IDs/timestamps, and isolated due rows so one failure does not block
  unrelated schedules.
- Replaced a fragile weekday SQL expression with MySQL JSON Schema validation.
  The real MySQL run also proved that Event uses unsigned scope IDs while the
  report/member chain uses signed IDs, so the impossible cross-module message FK
  was removed while schedule-domain restrictive FKs and unique message identity
  remain.
- Aligned schema assertions with MySQL's five automatically materialized foreign-
  key indexes.

## Deferred

- report PDF output and print-layout composition
- monthly, holiday/business calendars and catch-up policy beyond bounded retries
- email, SMS, enterprise chat and Webhook delivery channels
- external API/database and multi-module joined data-source execution
- responsive, accessibility, animation and exhaustive visual-state hardening
