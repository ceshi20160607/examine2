# FAST-FLOW-DEFINITION-RECORD-TRIGGER-20 Acceptance

Accepted at: 2026-07-27T20:58:30+08:00

Verdict: PASS

## Delivered functional closures

- Flow definition create/revise accepts an optional immutable published
  `triggerBinding {moduleCode,event,priority,exclusive}`. This batch supports canonical runtime module codes,
  `RECORD_ACTIVATED`, integer priorities from -1000 through 1000 and exclusive dispatch only.
- Draft trigger bindings can be changed or removed. Publish snapshots the binding into the exact definition
  version, while earlier published versions remain unchanged.
- Module publishes the activation fact through the narrow Core `RuntimeRecordFlowTriggerFacade` after the
  normal record mutation facts and before returning from the existing idempotent activation transaction.
- `ObjectProvider` resolves the Flow adapter lazily, avoiding an eager
  Module -> Flow -> Module Spring bean cycle.
- Flow matches each definition's latest published version for the scoped system, tenant, module and event.
  Candidates are ordered by priority descending and definition id ascending; exactly the first exclusive
  candidate starts.
- Automatic start reuses the existing Flow start path and Batch19 record binding. The record number becomes
  the business key, the event actor becomes the requester, and the new instance is projected to the record as
  `PENDING`.
- `un_flow_trigger_dispatch` persists one immutable scoped result per event key, including explicit no-match
  results. Replay returns the original result without another instance, Flow history item or record projection.
- Trigger, Flow start, record projection and Module activation share one transaction. Projection conflict
  rolls back the record status/version, `RECORD_ACTIVATED` history, Flow instance/history and dispatch.
- V8.15 adds nullable all-or-none trigger columns and indexes/checks to definition draft/version tables, plus
  the scoped dispatch table with restrictive definition-version and instance foreign keys.
- The Flow definition UI configures, validates, removes and reads trigger bindings while preserving failed
  form drafts. It shows separate draft and immutable published summaries. Responsive and visual consolidation
  remains deferred to P10-H1.

## Verification

- Core + Module full suite: 147 tests passed with zero failures/errors/skips:
  - `examine-core`: 11.
  - `examine-module`: 136.
- Core + Flow full suite: 151 tests passed with zero failures/errors/skips:
  - `examine-core`: 11.
  - `examine-flow`: 140.
- New Core/Module focused trigger contracts: 7/7 passed.
- Full backend Reactor `test-compile` passed through all 11 modules and `examine-web`.
- Frontend: 38 test files / 157 tests passed; Vue/TypeScript typecheck and production build passed.
- Real authenticated HTTP on MySQL 8.0.44:
  - `FlowApiJourneyIntegrationTest` passed 1/1 in 75.57 seconds.
  - All 37 migrations applied through V8.15.
  - Two published bindings proved higher-priority selection and absence of a lower-priority instance.
  - Activation replay retained one instance and one dispatch; terminal approval projected `APPROVED`.
  - A second tenant proved durable no-match dispatch without a record Flow projection.
  - A conflicting pending projection forced `RECORD_FLOW_ALREADY_PENDING`; reloading proved the record stayed
    `DRAFT` v0 with no automatic Flow instance, dispatch or `RECORD_ACTIVATED` history.
- `P4A1SchemaIntegrationTest` passed 1/1 on MySQL 8.4.10 in 38.85 seconds. A populated V8.11 database applied
  exactly V8.12 through V8.15 once and zero on rerun; all 37 migrations, nullable legacy trigger state,
  all-or-none checks, scoped keys and restrictive foreign keys passed.
- Acceptance initially exposed one missing Java import and one over-narrow `data:null` JSON assertion. Both
  harness issues were corrected, then the complete suites and real journeys passed.

## Non-blocking observation

The production build retains the existing warning for chunks above 500 kB. Conditional trigger predicates,
nonexclusive fan-out, other record events, arbitrary STATUS-field mapping and the full
responsive/accessibility/visual matrix remain later bounded work.
