# FAST-AI-TODO-MESSAGE-READ-87 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T22:28:32+08:00`
- Delivery mode: functionality-first parallel owner/Agent/frontend batch;
  responsive, accessibility and exhaustive visual adaptation remain deferred.

## Delivered

- Added exact policy-gated `TODO_QUERY` and `MESSAGE_QUERY` plans. Both reject
  unknown, duplicate, missing, unsupported and out-of-range inputs, fix page 1,
  cap `limit` at 20 and accept no caller-selected identity, route, SQL or sort.
- Added narrow Core read ports with Todo- and Event-owned adapters. Todo reuses
  `TodoService.page/counts`; messages reuse `MessageInboxService.inbox` and
  `unreadCount` with live `event.message.access`. Both are current
  system/tenant/member scoped and transactionally read-only.
- Added mutually exclusive typed `contextResult.todos` and `.messages` live
  branches and frontend cards. AI performs no Todo/Event SQL, refresh, action,
  mark-read or archive, and introduces no action buttons in this read batch.
- Kept Todo titles/routes and message titles/bodies/paths available only to the
  authorized live result and in-memory summary provider request. Durable AI
  session/message/turn/tool/usage evidence contains redacted summaries,
  hashes, counts and structural facts rather than those plaintext values.
- Made task/daily-report navigation reachable with either task or report
  permission; report-only members default to reports and issue no task,
  project or delegated-approval requests.
- Added the independent message-template administration route/page/navigation
  for `event.template.manage`, while retaining the existing Studio tab.
- Extended report creation to select, reorder, remove and submit one ordered
  unique list of 1..100 output field codes.
- Added `V8_69_0` to allow the two new operation names in the existing
  hash-only AI tool ledger constraint.

## Verification

- Affected backend full regression passed Core `64/64`, Todo `17/17`, Event
  `32/32` and AI `90/90`, totaling `203/203` with no failure, error or skip.
- Todo/Core focused verification passed `4/4`; Event/Core focused verification
  passed `4/4`; Agent parser/orchestration verification passed `5/5`; Web Agent
  controller contracts passed `3/3`.
- All `14` Maven modules and their tests compiled successfully.
- The real Spring Boot + MySQL + Redis + local OpenAI-compatible HTTP-provider
  journey passed `1/1` in `191.7s` test time (`3:31` reactor total), applying
  all `91` migrations and retaining the complete existing Flow/Work/Event/
  OpenAPI/AI regression.
- The independent MySQL 8.4 schema test passed `1/1`, validated all `91`
  migrations through `V8_69_0`, applied `49` migrations from V8.20 and proved
  the final repeat migrate executes zero work.
- Frontend focused verification passed `6` files / `60` tests; final full
  verification passed `99` files / `473` tests. Typecheck and production build
  passed over `5,764` transformed modules.
- Strict UTF-8/mojibake, `git diff --check`, base/instance framework, seven
  cadence cases and VS4 `43`-endpoint machine-contract validators all passed.

## Demonstrated journey

An authorized current member reads one native Todo page and one unread native
message page through the Agent and receives the same typed owner facts and
counts. Todo and message row counts plus version sums remain unchanged. The
system disables `event.message.access`, bumps authorization, refreshes the live
session and records a failed hash-only `MESSAGE_QUERY` tool call without a
summary call, then restores the permission. Switching tenant hides both owner
lists and the Agent session. A union of persisted session/message/turn/tool/
usage projections contains none of the returned Todo or message plaintext.

## Integration correction

- The existing `un_ai_agent_tool_call` check constraint ended at the three
  Batch83 context-read names. The real insert would therefore reject both new
  tools even though unit tests passed. `V8_69_0` deliberately broadens only
  that name set; no table or evidence payload was added.

## Deferred

- Work project progress/metrics Agent reads and later evidence-ranked context
  aggregations
- platform task lifecycle/message writes only when a complete owner lifecycle
  is deliberately added
- responsive, accessibility, animation and exhaustive visual-state hardening
- clean release packaging and user final acceptance

## Next node

`FAST-AI-WORK-METRICS-READ-88`
