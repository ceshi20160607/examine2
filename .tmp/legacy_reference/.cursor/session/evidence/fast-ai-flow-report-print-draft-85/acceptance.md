# FAST-AI-FLOW-REPORT-PRINT-DRAFT-85 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T20:13:02+08:00`
- Delivery mode: functionality-first confirmation slice; unified responsive,
  accessibility and exhaustive visual adaptation remains deferred.

## Delivered

- Added exact `FLOW_DEFINITION_DRAFT`, `CONFIG_REPORT_DRAFT` and
  `CONFIG_PRINT_TEMPLATE_DRAFT` planning with strict policy gating,
  clarification and confirmation. Unknown, duplicate, missing, oversized,
  unsafe or stale plans fail closed.
- Added narrow Core ports and domain-owned implementations. Flow creates one
  unpublished definition draft; Module creates one unpublished report draft or
  one `DISABLED`, unpublished print-template draft through the existing owner
  services. AI owns no Flow/report/print mutation SQL.
- Added live permission/member/module/data-source/field/code rechecks,
  authenticated AES-GCM commands and separate durable Flow/Module execution
  ledgers. Prepare creates no domain artifact; exact confirmation replay returns
  the original result and changed replay conflicts.
- Added one generated-draft proposal/attempt/event state machine with pending,
  executing, succeeded, rejected, expired, stale and permission-denied states,
  plus tenant/session/account ownership and redacted persistence.
- Added nested generated-draft detail/confirm/reject HTTP APIs and function-first
  frontend previews/readbacks for Flow definitions, reports and print templates.
- Added `V8_68_0` with three AI evidence tables and two owner execution ledgers.

## Verification

- Affected backend full regression passed Core `60/60`, Flow `390/390`, Module
  `431/431` and AI `89/89`, totaling `970/970` with no failure, error or skip.
- Owner-focused Core/Flow/Module verification passed `21/21`; AI focused
  parser/orchestration verification passed `14/14`; Web Agent controller
  contracts passed `3/3`.
- The real Spring Boot + MySQL + Redis + local OpenAI-compatible HTTP-provider
  journey passed `1/1` in `314.0s`, applying all `90` migrations and retaining
  the complete existing Flow/configuration/confirmation/AI regression.
- The independent MySQL 8.4 schema test passed `1/1`, validated all `90`
  migrations through `V8_68_0` and verified the five new tables, columns,
  foreign keys, checks and owner uniqueness contracts.
- All `14` Maven child modules test-compiled successfully.
- Full frontend verification passed `98` files / `464` tests; focused Agent
  verification passed `4` files / `41` tests. Typecheck and production build
  passed over `5,761` transformed modules.
- `git diff --check` passed; the final framework, encoding, cadence and contract
  validators are recorded in the session state after node transition.

## Demonstrated journey

An administrator publishes all three operations. An authorized member requests
one Flow definition and observes zero Flow writes, confirms it and receives one
unpublished definition, then exactly replays the confirmation to the same id
while a changed replay conflicts. Two report proposals for one new code start
with zero report writes; the first creates one unpublished draft and the second
becomes stale. A print proposal creates one disabled, unpublished template.
Revoking `flow.definition.manage` after proposal creation makes confirmation
permission-denied. Another tenant cannot read the generated proposal. AI
proposal snapshots redact narrative text and authenticated command ciphertext
contains no plaintext payload.

## Integration corrections

- Removed invalid `final` declarations from three transactional Flow AI beans so
  the real Spring application can create CGLIB transaction proxies.
- Marked the intended Flow ledger production constructor explicitly after the
  application context exposed ambiguity with its test-support constructor.
- Kept Flow prepare zero-write while using a normal transaction, because active
  member validation intentionally performs `SELECT ... FOR UPDATE` and MySQL
  forbids that lock in a read-only transaction.
- Normalized Flow command expiry to MySQL `DATETIME(6)` precision, preventing an
  untampered first confirmation from being misclassified as a ledger conflict.

## Deferred

- platform task lifecycle/message writes and optional comment/history/Flow
  timeline aggregation only if the function-gap audit proves user value
- responsive, accessibility, animation and exhaustive visual-state hardening
- clean-environment release packaging and user final acceptance

## Next node

`FAST-AI-FUNCTION-GAP-AUDIT-86`
