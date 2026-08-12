# FAST-FLOW-RECORD-BINDING-19 Acceptance

Accepted at: 2026-07-27T20:33:42+08:00

Verdict: PASS

## Delivered functional closures

- Flow start accepts an optional all-or-none `recordBinding {moduleCode,recordId}`. The binding is validated as
  a canonical module code and positive decimal record id, persisted as an immutable instance snapshot and read
  back through start/list/detail/task mappings without changing unbound behavior.
- Binding reuses the existing canonical runtime record VIEW authorization, including shell permission, module
  permission and data scope, before locking the record. Only `ACTIVE` records can be bound.
- Module owns `un_module_record_flow_state`; Flow accesses it only through the new narrow Core
  `RuntimeRecordFlowFacade`. This preserves the modular-monolith owner-write boundary and keeps record lifecycle
  status separate from approval status.
- A record permits one current `PENDING` instance. A second pending bind conflicts and rolls back the newly
  inserted Flow instance/history. A terminal projection can be rebound to a later instance.
- Projection versions are monotonic across the whole record timeline: initial pending v0, terminal v1, rebound
  pending v2 and later terminal v3. They do not reset and therefore avoid stale-client ABA ambiguity.
- Final approve/reject/withdraw/terminate project `APPROVED/REJECTED/WITHDRAWN/TERMINATED`; a non-final
  sequential approval keeps `PENDING` without a write. Unbound instances do not call the projection owner and
  idempotent terminal replay does not transition twice.
- Bound start and all terminal mutations run inside Spring transactions. Projection failure rolls back Flow
  instance status, state version and history. The Module projection itself uses scoped `FOR UPDATE` reads and
  compare-and-set updates.
- The runtime record `flow-state` endpoint first reuses canonical VIEW authorization and returns the current
  `{instanceId,status,version,updatedAt}`, or successful `data:null` for an unbound visible record.
- V8.13 creates the Module-owned projection with record PK, unique scoped instance, restrictive record FK,
  status/version checks and stable indexes. V8.14 adds nullable all-or-none Flow binding columns and a scoped
  record lookup index while preserving old instances as unbound.
- The Flow start form supports paired binding fields, preserves drafts on failure and renders the returned
  binding. The runtime record detail reads and renders real Flow state without hiding the record if that
  auxiliary query fails. Responsive and visual consolidation remains deferred to P10-H1.

## Verification

- Core + Module full suite: 132 tests passed with zero failures/errors/skips:
  - `examine-core`: 8.
  - `examine-module`: 124.
- Core + Flow full suite: 135 tests passed with zero failures/errors/skips:
  - `examine-core`: 8.
  - `examine-flow`: 127.
- New Module/Core focused contracts: 14/14 passed.
- New Flow record-binding focused contracts: 42/42 passed, including two real Spring transaction rollback
  tests.
- Full backend Reactor `test-compile` passed through `examine-web`.
- Frontend: 38 test files / 153 tests passed; Vue/TypeScript typecheck and production build passed.
- Real authenticated HTTP on MySQL 8.0.44:
  - `FlowApiJourneyIntegrationTest` passed 1/1 in 71.82 seconds.
  - All 36 migrations applied through V8.14.
  - A published runtime module and active records proved bound start/readback, pending projection v0, duplicate
    pending conflict with no orphan Flow instance, terminal approve v1, terminal rebind v2, withdrawal v3,
    inactive-record rejection and cross-tenant record rejection.
  - Deleting a projection before approval forced `RECORD_FLOW_STATE_CONFLICT`; reloading proved the Flow
    instance remained `PENDING` with exactly the original `STARTED` history.
- `P4A1SchemaIntegrationTest` passed 1/1 on MySQL 8.4.10 in 36.58 seconds. A populated V8.11 database applied
  exactly V8.12, V8.13 and V8.14 once and zero on rerun; old Flow facts remained unbound, and the new table,
  columns, unique/FK/index/check contracts all passed.
- Batch19 scoped trailing-whitespace and framework structure checks passed.

## Non-blocking observation

The production build retains the existing warning for chunks above 500 kB. Definition-level module/event
binding, automatic record triggers, arbitrary STATUS-field mapping and the full responsive/accessibility/visual
matrix remain later bounded work.
