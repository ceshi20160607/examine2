# FAST-FLOW-PERIODIC-TRIGGER-34 Acceptance

## Verdict

PASS at 2026-07-29T20:52:04+08:00.

Batch 34 closes the first durable fixed-interval Flow trigger slice. A published
definition can now own one immutable periodic schedule, and the runtime worker
starts the exact published version for each claimed slot without an online user
request.

## Delivered behavior

- Flow definitions accept a `PERIODIC` trigger with an explicit UTC start
  instant and an interval from 1 to 525600 minutes. Periodic triggers reject
  record module binding, record conditions, priority fan-out and record status
  mapping.
- The member saving the draft is frozen as the requester in both the draft and
  immutable published version.
- Publishing a periodic version atomically projects one scoped durable runtime
  schedule. A newer periodic publication replaces it and resets runtime state;
  publishing a non-periodic version removes it.
- Each due fire runs in an isolated transaction, locks and rechecks its schedule
  row, verifies the requester is still active, starts the exact definition
  version with a deterministic slot business key, and atomically advances the
  next fire.
- Concurrent workers cannot duplicate a slot. Misfires create one instance and
  skip to the first future interval. An inactive requester pauses only that
  schedule, while a failed schedule rolls back and does not block the rest of
  the batch.
- The Flow editor exposes fixed-period configuration and renders durable
  version, requester, status, next/last fire, last instance and pause reason.

## Verification

- Core regression: 20/20 tests passed.
- Flow regression: 181/181 tests passed, including 4 periodic worker tests for
  projection/disable, concurrent exact-slot dedupe, misfire skip-ahead,
  inactive-requester isolation and transactional failure rollback.
- `FlowApiJourneyIntegrationTest`: 1/1 passed in 118.4 s against real MySQL
  8.0 and Redis. It proves HTTP configuration/publication, exact requester and
  version execution, durable runtime projection, replacement without replay,
  and non-periodic disable.
- `P4A1SchemaIntegrationTest`: 1/1 passed in 44.40 s against real MySQL 8.4.
  All 50 migrations validated and the staged upgrade reached 8.28.0 with the
  periodic schedule table and strict trigger checks.
- `npm.cmd test`: 51 files and 197 tests passed.
- `npm.cmd run build`: TypeScript validation and Vite production build passed.
- `mvn -DskipTests test-compile`: all 12 backend reactor modules passed.

## Demo path

Open system Flow -> create a definition -> choose Fixed period -> set UTC start
and interval -> publish -> inspect next fire -> let the worker start the exact
version -> inspect last instance -> publish a replacement or disable the
periodic trigger and observe the durable schedule change.

## Deferred

- cron expressions, timezone calendars, holidays and daylight-saving rules
- timer/escalation nodes inside a running instance
- record-query scheduled fan-out and anomaly trigger sources
- responsive, accessibility and exhaustive visual hardening
