# FAST-FLOW-TRIGGER-CONDITIONS-FANOUT-21 Acceptance

Accepted at: 2026-07-27T21:28:30+08:00

Verdict: PASS

## Delivered functional closures

- Module emits a defensively copied activation snapshot keyed by canonical field code. Values are compact
  complete JSON, limited to 256 fields and 16 KiB per value; `IDENTITY` and `SECRET` never leave Module.
- Flow trigger bindings now snapshot up to ten flat `ALL` conditions. `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`,
  `EMPTY` and `NOT_EMPTY` follow structural JSON, number-only comparison and explicit empty-value semantics.
- Published candidates are evaluated completely before any start and remain ordered by
  `priority DESC, definitionId ASC`.
- Any matched exclusive binding suppresses every nonexclusive binding and starts only the first matched
  exclusive. Without an exclusive match, every matched nonexclusive binding starts in stable order.
- The first instance keeps the Batch19 primary record projection. Later instances use the narrow
  `bindAdditional` Core port and Module-owned `un_module_record_flow_state_item`.
- `/flow-state` remains backward compatible; `/flow-states` returns the primary first and every additional
  state in creation order. Primary and additional terminal transitions keep independent monotonic versions.
- Manual binding retains the original one-pending conflict: a terminal primary cannot be rebound while any
  additional projection is still `PENDING`; after all are terminal, primary rebind remains allowed.
- `un_flow_trigger_dispatch_instance` persists the exact ordinal result list. The Batch20 parent keeps the first
  result or no-match nulls; replay reads children in ordinal order without reevaluation, restart or reprojection.
- V8.16 creates the additional Module projection with scoped keys, restrictive record FK, status/version checks
  and pending/event indexes. V8.17 adds condition JSON, permits nonexclusive bindings, creates ordered dispatch
  children and backfills every existing matched parent to ordinal zero.
- Flow definition UI supports exclusive/nonexclusive mode and up to ten conditions while preserving failed
  form drafts. Record detail displays every projected automatic approval. Responsive consolidation remains
  deferred to P10-H1.
- Trigger, all Flow starts/history, all Module projections, dispatch parent/children, record activation and
  activation history share one transaction. A forced second-projection conflict rolled the whole fan-out back.

## Verification

- Full backend Reactor `test-compile` passed through all 11 modules and `examine-web`.
- Core + Module full suite passed 157 tests:
  - `examine-core`: 12.
  - `examine-module`: 145.
- Core + Flow full suite passed 166 tests:
  - `examine-core`: 12.
  - `examine-flow`: 154.
- Frontend passed 38 files / 159 tests, Vue/TypeScript typecheck and production build.
- `P4A1SchemaIntegrationTest` passed 1/1 on MySQL 8.4.10 in 37.09 seconds:
  - 39 migrations validated.
  - populated V8.11 applied V8.12 through V8.15 exactly four times, then V8.16/V8.17 exactly twice.
  - both reruns applied zero migrations.
  - the pre-V8.17 matched dispatch was backfilled exactly once to ordinal zero.
- `FlowApiJourneyIntegrationTest` passed 1/1 on MySQL 8.0.44 in 78.78 seconds:
  - all 39 migrations applied through V8.17.
  - two conditional nonexclusive definitions started and projected in stable order.
  - activation replay returned the exact same two instances without duplicates.
  - both terminal decisions projected independently.
  - a lower-priority exclusive match suppressed a higher-priority nonexclusive match.
  - a database trigger forced the second additional projection to fail; the record remained `DRAFT` v0 with
    zero Flow instances, primary/additional projections, dispatch rows or activation history.

## Acceptance repairs

- The first reactor compile exposed a missing checked `JsonProcessingException` translation in the JDBC
  condition mapper; the boundary now reports invalid stored bindings as SQL mapping failures.
- Core initially rejected precision-preserving compact decimals such as `100.00` by comparing parsed and
  reserialized JSON byte-for-byte. Validation now requires complete parse plus no JSON whitespace outside
  strings, while Module serializes persisted values directly so decimal scale is retained.

## Non-blocking observation

The production build retains the existing chunk-size warning. OR/nested/relation predicates, other record
events, arbitrary STATUS-field mapping and the final responsive/accessibility/visual matrix remain bounded
later work.
