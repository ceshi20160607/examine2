# FAST-MODULE-XLSX-EXPORT-JOB-29 Acceptance

## Verdict

PASS at 2026-07-29T13:14:00+08:00.

Batch 29 delivers the first complete module-data export vertical slice. Export
is an independent permissioned action, while data selection remains owned by
the accepted runtime query and durable execution remains owned by the shared
job framework.

## Delivered behavior

- Existing and newly created modules receive `module.{moduleCode}.export`; ROOT
  grants and authorization epochs are migrated with the same permission owner.
- Creating an export snapshots the current canonical scope/filter/sort, ordered
  readable field codes, schema identity, caller context and permission set into
  one durable task plus one `MODULE_XLSX_EXPORT` job.
- Unknown, duplicate, masked, hidden and composition-owned fields are rejected.
  The worker pages the existing runtime query in deterministic 200-row windows,
  uses its data scope and field projection, and enforces a 5,000-row first-slice
  limit.
- The result workbook has stable record columns, readable field names and field
  codes, typed booleans/numbers, frozen headers and an autofilter. Formula-like
  input remains a literal string; no formula, macro or external link is emitted.
- Durable task status records queued/running/succeeded/failed state, row counts,
  sanitized failure details, result filename/content/size and timestamps. Redis
  signalling is acceleration only; database claiming, leases and retry remain
  authoritative.
- Recent history, task detail and result download are scoped to the requesting
  member/system/tenant/module. Download rechecks current export permission and
  returns a sanitized, non-cacheable XLSX attachment.
- The workbench exposes a permission-scoped Export drawer that submits the
  current applied query and visible fields, polls status, reopens recent tasks
  and downloads completed workbooks without blocking record-list interaction.

## Verification

- `mvn -pl examine-module -am test`
  - Core: 19 passed.
  - Module: 161 passed, including export API/migration/XLSX tests.
  - Affected reactor total: 180 passed.
- `ImportJourneyIntegrationTest`
  - Real MySQL 8.0 + Redis 7.4 HTTP import/export journey passed in 69.38 s.
  - Proves filtered current-query export, durable completion, XLSX contents,
    recent history, audit, anonymous rejection, cross-member exclusion and
    cross-module non-enumeration.
- `P4A1SchemaIntegrationTest`
  - Passed in 47.48 s; all 45 migrations validate and the populated upgrade
    includes export storage, permission registration, ROOT grant and epoch bump.
- `npm.cmd test`
  - 46 files, 186 tests passed, including export API, drawer polling/download
    and independent workbench permission orchestration.
- `npm.cmd run build`
  - TypeScript check and Vite production build passed.
- `mvn test-compile`
  - All 12 backend reactor modules passed.
- `git diff --check`
  - Passed for the Batch 29 change set.

## Demo path

Open a filtered module list -> choose Export -> retain or change readable fields
-> create task -> continue using the list while the durable job runs -> reopen
the task from recent history -> download XLSX -> inspect system columns, stable
field codes and exactly the records visible to the accepted query.

## Deferred

- more than 5,000 rows, streaming workbook/object-storage offload and resumable jobs
- CSV, scheduled delivery and external callback
- selected-record-only export
- print templates, PDF rendering and print history
- responsive, accessibility and exhaustive visual hardening
