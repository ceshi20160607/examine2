# FAST-WORK-TASK-REMINDER-58 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-01T00:40:50+08:00`
- Delivery mode: functionality first; responsive, accessibility and exhaustive
  visual hardening remain deferred to the final unified pass.

## Delivered

- Added nullable task reminder times with future/due-time validation, compatible
  omitted/present/null update semantics and sanitized reminder state in task APIs.
- Added immutable tenant-scoped reminder generations with PENDING, PROCESSING,
  SENT, CANCELLED and FAILED states, optimistic CAS, bounded stable claiming,
  renewable leases, expired-lease recovery and terminal-state write fences.
- Rescheduling creates a new generation, removal/completion cancels live work,
  reopening does not restore it, and managers can retry failed generations.
- Added deterministic Event inbox delivery keyed by system, tenant, task and
  generation. Crash replay returns the existing message and never duplicates it.
- Added a configurable production scheduler with a one-second default first run,
  five-second fixed delay, bounded 1..100 batch size and deterministic run-once
  entrypoint for integration verification.
- V8.48 adds task `reminder_at`, the restrictive reminder table, generation
  uniqueness, due/lease indexes, strict state checks and a restrictive task FK.
- Added function-first create/edit reminder controls, state display and manager
  retry to the shared list, Kanban and calendar task experience.

## Verification

- Final affected backend regression passed Core `25/25`, Work `67/67` and Event
  `26/26`, totaling `118/118` with no failure, error or skip.
- Focused reminder domain/repository/worker/migration verification passed
  `20/20`; focused service/API/Event/notifier/scheduler classes passed `32/32`.
- Backend test compilation passed across all `12` Maven modules.
- Real MySQL 8.4 schema integration passed `1/1`; all `70` Flyway migrations
  applied through V8.48 and its columns, checks, indexes and restrictive FK
  matched the frozen contract.
- Real Spring Boot + MySQL 8.0.44 + Redis 7.4 authenticated HTTP journey passed
  `1/1`, including schedule, due claim, Event delivery, replay dedupe,
  rescheduling, completion cancellation and reopen-without-restore.
- Frontend focused verification passed `2` files / `16` tests; the canonical
  full suite passed `55` files / `296` tests. Typecheck and Vite production build
  passed with only the existing non-blocking chunk-size warning.
- Scoped `git diff --check` passed. Framework and contract validators are run
  after activation of the immediately following unified Todo node.

## Demonstrated journey

An authenticated manager schedules a future reminder on the same durable task
used by list, Kanban and calendar views. The real worker claims it, writes one
actionable `WORK_TASK_REMINDER` Event message, marks the generation SENT and a
second worker run creates nothing. Rescheduling creates generation two. Completing
the task cancels it, and reopening the task leaves that reminder cancelled.

## Integration corrections

- Added the missing production scheduler after integration review found that a
  callable worker alone would not execute automatically in a running service.
- Aligned scheduler batch validation with the repository's 1..100 claim bound.
- Added latest-generation/lease fencing and SENT/CANCELLED repository guards so
  an older worker result cannot overwrite a newer or terminal fact.

## Deferred

- Unified Todo aggregation and source-delegated actions (the next active batch).
- Recurring reminders, snooze, escalation chains and delivery preferences.
- Configurable Work fields, report comments and workload/stat dashboards.
- Responsive, accessibility and exhaustive visual-state hardening.
