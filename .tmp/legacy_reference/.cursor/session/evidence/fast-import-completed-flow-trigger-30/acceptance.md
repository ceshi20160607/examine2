# FAST-IMPORT-COMPLETED-FLOW-TRIGGER-30 Acceptance

## Verdict

PASS at 2026-07-29T13:38:00+08:00.

Batch 30 closes the import-completed automatic Flow trigger by extending the
accepted record-event trigger owner. Import execution publishes one final
record snapshot only after all row mutations succeed, and Flow dispatch remains
condition-aware, permission-safe, transactionally atomic and replay-idempotent.

## Delivered behavior

- `IMPORT_COMPLETED` is part of the shared core facade, Flow draft/published
  trigger contract, HTTP validation, persistence constraint and designer.
- A successful import commit emits the event once for every committed record,
  after the complete mutation loop and before job completion in the same
  transaction. Preview, validation failure, rollback and direct record writes do
  not emit it.
- Events carry the final record id, version, business key and existing canonical
  non-sensitive value snapshot; identity and secret fields remain redacted.
- Candidate priority, exclusive/fan-out behavior, condition matching, Flow start,
  record binding and durable dispatch reuse `FlowRecordTriggerAdapter` and the
  established Flow mutation transaction.
- Event keys include system, tenant, module, record, final version and event type.
  Exact commit replay returns the accepted result without creating another Flow
  instance or dispatch row.
- The Flow designer exposes “导入完成”; status mapping remains restricted to the
  record-activation event.

## Verification

- Targeted core/Flow/frontend contract tests: 113 passed.
- `ImportJourneyIntegrationTest`: passed in 73.49 s against real MySQL 8.4 and
  Redis. It proves no preview trigger, conditional Flow creation after a real
  XLSX commit, exact dispatch-key readback and duplicate replay suppression while
  retaining import rollback and XLSX export coverage.
- `P4A1SchemaIntegrationTest`: passed in 37.51 s. All 46 migrations validate and
  the populated upgrade applies V8.21 through V8.24, including both Flow trigger
  constraints with `IMPORT_COMPLETED`.
- `mvn -pl examine-module,examine-flow -am test`: core 19, module 161 and Flow
  174 tests passed.
- `npm.cmd test`: 46 files and 186 tests passed.
- `npm.cmd run build`: TypeScript validation and Vite production build passed.
- `mvn -DskipTests test-compile`: all 12 backend reactor modules passed.

## Demo path

Publish a Flow definition bound to a module and “导入完成” -> download and fill
the XLSX template -> preview and commit -> open the imported record -> observe
the single automatically bound Flow instance -> replay the same commit request
-> observe the same import result with no duplicate Flow instance.

## Deferred

- scheduled/time-window and anomaly/threshold Flow trigger sources
- batch-level Flow instances without a record binding
- notification delivery and external callbacks
- responsive, accessibility and exhaustive visual hardening
