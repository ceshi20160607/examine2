# FAST-IMPORT-JOB-PREVIEW-COMMIT-27 Acceptance

## Verdict

PASS at 2026-07-29T12:10:00+08:00.

Batch 27 delivers the first complete module-data import vertical slice on the
published runtime schema. Import remains a module-runtime capability; record
mutations still belong to `RecordRuntimeService`, and durable job facts remain
authoritative in MySQL when Redis signalling is unavailable.

## Delivered behavior

- Every published module receives an independent
  `module.{moduleCode}.import` permission. Existing modules and ROOT roles are
  migrated, and new module drafts include the permission from creation.
- The template API returns the current schema/checksum, import-safe writable
  fields, explicit exclusions, supported modes and the 200-row limit.
- NEW and UNIQUE-field UPSERT preview requests create durable batch, row and
  `un_sys_job` facts. Preview uses the runtime canonical codec, detects missing
  values, database conflicts and request-internal duplicate unique values, and
  never mutates records.
- Redis Stream notification is emitted after commit as an acceleration signal.
  Database CAS claiming, leases, retry bounds and scheduled recovery remain the
  source of truth.
- Explicit asynchronous commit is atomic and uses the existing record owner for
  create/activate or optimistic update. Exact commit replay does not create a
  second job or duplicate records.
- Conditional rollback first verifies every committed target version. It
  trashes batch-created records or restores only batch-changed fields; a later
  record edit rejects the entire rollback without partial mutation.
- Batch readback is isolated by system, tenant, module and original member and
  exposes row/action/error/status/count summaries without raw sensitive values.
- The system workbench now exposes a permission-scoped import drawer with
  dynamic template fields, JSON rows, NEW/UPSERT selection, asynchronous status
  polling, row results, explicit commit and safe rollback. Responsive polish is
  intentionally deferred.

## Verification

- `mvn -pl examine-module -am test`
  - Core: 19 passed, including 3 durable-job tests.
  - Module: 154 passed, including import API and migration contracts.
  - Affected reactor total: 173 passed.
- `ImportJourneyIntegrationTest`
  - Real MySQL 8.0 + Redis 7.4 HTTP journey: 1 passed in 98.69 s.
  - Proves template exclusion, permission publication, invalid duplicate
    zero-write preview, NEW commit, mixed UPSERT, Redis Stream signal, exact
    replay, durable readback, safe rollback conflict and successful rollback.
- `P4A1SchemaIntegrationTest`
  - 1 passed; all 44 migrations validate and populated legacy data upgrades to
    V8.22 with import tables, permissions, ROOT grants and authz epoch changes.
- `npm.cmd test`
  - 44 files, 181 tests passed, including import API and drawer orchestration.
- `npm.cmd run build`
  - TypeScript check and Vite production build passed.
- `mvn -DskipTests test-compile`
  - All 12 backend reactor modules passed.
- `git diff --check`
  - Passed; only pre-existing CRLF conversion notices were reported.

## Demo path

Publish a module with a UNIQUE field -> open workbench import -> load the live
template -> submit duplicate rows and observe zero writes -> preview NEW or
UPSERT rows -> inspect row totals -> explicitly commit -> reload records ->
replay commit without duplicates -> edit a committed target and observe atomic
rollback refusal -> commit another batch -> safely roll it back -> inspect
durable jobs, Redis signals, record history and operation audit.

## Deferred

- XLSX template download/upload and error workbook generation
- more than 200 rows and chunked large-file execution
- export jobs, print templates and PDF output
- import-completed Flow trigger, notification and external callback
- responsive, accessibility and exhaustive visual hardening
