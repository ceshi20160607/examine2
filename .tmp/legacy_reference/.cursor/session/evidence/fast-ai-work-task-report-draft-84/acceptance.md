# FAST-AI-WORK-TASK-REPORT-DRAFT-84 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-04T19:08:04+08:00`
- Delivery mode: functionality-first confirmation slice; unified responsive,
  accessibility and exhaustive visual adaptation remains following work.

## Delivered

- Added exact `WORK_TASK_DRAFT` and `WORK_DAILY_REPORT_DRAFT` planning. Task
  confirmation creates exactly one existing-semantics `OPEN` Work task; report
  confirmation creates exactly one current-member `DRAFT` daily report and
  never completes a task or submits a report.
- Added the Core `AiWorkDraftFacade` and Work-owned adapter, live context reader,
  AES-GCM sealed command and durable execution ledger. Prepare performs no Work
  writes; execute rechecks live permission, member, project, report uniqueness,
  authorization epoch, expiry and request binding in the owner transaction.
- Added strict Agent parsing, policy routing and a redacted proposal/attempt/event
  lifecycle covering pending, executing, succeeded, rejected, expired, stale,
  permission-denied, exact replay and changed-replay conflict behavior.
- Added nested Work proposal detail/confirm/reject HTTP APIs and function-first
  task/report preview, confidence, clarification, explicit confirm/reject and
  created readback states in the system Agent UI.
- Added `V8_67_0` with AI proposal/attempt/event evidence and the Work owner
  execution ledger. AI persistence contains redacted previews/results and
  ciphertext rather than task/report narrative plaintext.

## Verification

- Affected backend full regression passed Core `56/56`, Work `91/91` and AI
  `80/80`, totaling `227/227` with no failure, error or skip.
- Owner-focused tests passed `18/18`; AI focused parser/orchestration tests
  passed `13/13`; Web Agent controller contracts passed `3/3`.
- The real Spring Boot + MySQL + Redis + local OpenAI-compatible HTTP-provider
  journey passed `1/1` in `199.9s`, applying all `89` migrations and retaining
  the complete existing Flow/configuration/confirmation/AI regression.
- The independent MySQL 8.4 schema test passed `1/1`, validated all `89`
  migrations through `V8_67_0` and verified the four new tables, foreign keys,
  checks, owner uniqueness and plaintext exclusion contract.
- All `14` Maven child modules test-compiled successfully.
- Full frontend verification passed `98` files / `459` tests; focused Agent
  verification passed `4` files / `36` tests. Typecheck and production build
  passed over `5,761` transformed modules.
- Staged and unstaged diff checks, strict UTF-8/mojibake checking, base/instance
  framework validation, all `7` cadence cases and both VS4 contract validators
  passed; compatibility remains at `43` endpoints.

## Demonstrated journey

An administrator publishes both Work write operations. A member requests a task
for an active assignee and observes zero Work writes, confirms it, receives one
OPEN task, replays the exact request to the same id and receives a conflict for
a changed replay. Two report proposals for the same date both start with zero
writes; the first confirmation creates one personal DRAFT report and the second
becomes stale. Revoking `work.task.create` after proposal creation makes its
confirmation permission-denied. Another tenant cannot read the session or Work
proposal. Stored AI evidence contains none of the task title/description or
report narratives.

## Integration corrections

- Marked the intended Spring constructor explicitly after the real application
  context exposed ambiguity between the production and test-support owner-store
  constructors.
- Supplied the test command-secret reference and normalized proposal expiry to
  MySQL `DATETIME(6)` precision so an untampered first confirmation and exact
  replay compare the same authenticated timestamp.
- Preserved live permission precedence so revocation reports
  `PERMISSION_DENIED`, while other changed owner facts report `STALE`; aligned
  the tenant-hidden nested resource with the public `AI_NOT_FOUND` API code.

## Deferred

- confirmation-gated Flow definition and generated report/print configuration
  drafts
- platform task lifecycle/message writes if required
- optional comments/history/Flow timeline context aggregation
- responsive, accessibility, animation and exhaustive visual-state hardening
- clean-environment release packaging and user final acceptance
