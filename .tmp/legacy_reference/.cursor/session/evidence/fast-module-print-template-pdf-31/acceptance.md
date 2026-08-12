# FAST-MODULE-PRINT-TEMPLATE-PDF-31 Acceptance

## Verdict

PASS at 2026-07-29T14:42:00+08:00.

Batch 31 closes the first usable module printing vertical slice. Administrators
own structured, versioned and schema-bound print templates; permitted members
preview authoritative records, create durable server-side PDF results and
reopen immutable member-scoped history.

## Delivered behavior

- Print-template drafts have stable codes, optimistic versions, A4/A5 paper and
  orientation settings, ordered field-code bindings and a publish operation.
  Publication creates an immutable version bound to the current published
  module schema; unknown, duplicate, hidden, secret and composition fields are
  rejected.
- Runtime template discovery and preview reuse current record visibility and
  readable canonical fields. Preview HTML is escaped and returned without
  creating history.
- PDF creation snapshots the template, schema, record version, business key and
  rendered values before a durable `MODULE_RECORD_PDF_PRINT` job is accepted.
  Server rendering supports CJK text, A4/A5, portrait/landscape and multipage
  values without retaining every rendered page in memory.
- Exact idempotency replay returns the original print task. History and PDF
  download enforce system, tenant, module, record, member, current permission
  and current data-scope boundaries without leaking another task's existence.
- Historical snapshot and PDF bytes remain immutable after the source record is
  edited. Result downloads are non-cacheable and use sanitized filenames.
- The config studio exposes a structured template manager. The record-detail
  workbench exposes template selection, sandboxed preview, browser print,
  durable PDF creation/polling/download and recent history.
- Apache PDFBox is pinned to the current secure 3.0.8 line; duplicate
  `commons-logging` is excluded so Spring's logging bridge remains singular.

## Verification

- Targeted print contract/rendering tests: 3 passed.
- `ImportJourneyIntegrationTest`: passed 1/1 in 75.95 s against real MySQL 8.0
  and Redis. All 47 migrations applied; the journey publishes a template,
  previews and prints an imported record, downloads a valid `%PDF`, proves exact
  replay, historical-value retention, and member/module/record isolation.
- `P4A1SchemaIntegrationTest`: passed 1/1 in 44.03 s against real MySQL with all
  47 migrations, three print tables, module print permissions and ROOT grants.
- `mvn -pl examine-module -am test`: core 19 and module 164 tests passed.
- `npm.cmd test`: 49 files and 190 tests passed.
- `npm.cmd run build`: TypeScript validation and Vite production build passed.
- `mvn -DskipTests test-compile`: all 12 backend reactor modules passed.

## Demo path

Open system configuration -> select a published module -> create and publish a
structured print template -> open an eligible record -> choose Print -> preview
the resolved values -> create PDF -> download it -> edit the record -> reopen
print history and observe the original immutable result.

## Deferred

- drag-and-drop canvas designer, arbitrary HTML/CSS, signatures, seals, QR codes
  and batch printing
- object-storage offload and scheduled/external delivery
- responsive, accessibility and exhaustive visual hardening
