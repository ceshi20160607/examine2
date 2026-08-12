# FAST-PLATFORM-PERSONAL-TASK-LIFECYCLE-91 Acceptance

- Verdict: **PASS**
- Accepted at: `2026-08-05T01:27:45+08:00`
- Delivery mode: function-first parallel Platform-owner, frontend and
  integration lanes; responsive, accessibility and exhaustive visual
  adaptation remain deferred.

## Delivered

- Completed the existing `un_platform_task` owner aggregate with a native,
  self-scoped safe page at `GET /api/v1/platform/tasks` and exact complete,
  reopen and cancel lifecycle endpoints. No second task model or Agent-only
  lifecycle was introduced.
- Extended task state to `OPEN|COMPLETED|CANCELLED`. Complete and cancel accept
  only open tasks; reopen accepts either terminal state. Every successful
  transition advances the update time, increments the version once and keeps
  completed/cancelled timestamps consistent with the current state.
- Added account/id/version/state conditional writes, distinct not-found,
  version-conflict and invalid-state errors, PLATFORM-context enforcement and
  separate `platform.task.read` / `platform.task.manage` permissions. Every
  query and write derives `account_id` from the authenticated session and
  returns `PLATFORM_TASK_NOT_FOUND` for another account's task.
- Preserved confirmed Platform Agent creation and its idempotency contract.
  New rows begin at `OPEN`, version zero and `updatedAt=createdAt`; internal
  authorization, hash, idempotency and correlation fields never enter the
  native task projection.
- Added the functional personal-task section to the existing platform
  workbench: permission gating, safe metadata, status filtering, paging,
  loading/empty/error states and state-valid actions. Successful actions
  refresh the current page; failures do not optimistically alter local state.
  No native create control, route, breakpoint or visual redesign was added.
- Added `V8_72_0__platform_personal_task_lifecycle.sql`, which extends only the
  existing task table, adds lifecycle consistency checks, registers
  `platform.task.manage`, grants it only through existing active PLATFORM ROOT
  roles and bumps the platform authorization epoch.

## Verification

- Final affected backend regression passed Core `70/70` and Platform `71/71`,
  totaling `141/141` with no failure, error or skip. All `14` Maven modules and
  their tests compiled successfully.
- The independent MySQL 8.4 schema test passed `1/1` in `63.908s`, applied `52`
  migrations from V8.20, validated all `94` migrations through `V8_72_0` and
  proved the final repeat migration performs zero work.
- The full Spring Boot + MySQL + Redis + local OpenAI-compatible provider HTTP
  journey passed `1/1` in `211.717s` test time (`4:06` reactor total), while
  retaining the complete existing cross-module journey.
- Frontend full verification passed `101` files / `486` tests. Typecheck and
  production build passed over `5,766` transformed modules; the only message
  was the existing non-blocking large-chunk warning.
- Strict UTF-8/mojibake, seven cadence cases, reusable base Structure, instance
  Active, canonical progress, both VS4 `43`-endpoint validators, staged and
  unstaged `git diff --check` all passed. Exactly three package checkpoints
  remain configured with zero attempts.

## Demonstrated journey

The journey uses the existing confirmed Platform Agent proposal to create one
real task, reads it through the native safe page at version zero, then executes
`OPEN -> COMPLETED -> OPEN -> CANCELLED -> OPEN` with versions zero through
four. It proves strictly increasing microsecond-compatible update timestamps,
terminal timestamp creation/clearing and the exact safe projection.

A replay with stale version zero returns `PLATFORM_TASK_VERSION_CONFLICT` and
does not change the completed row. The journey disables
`platform.task.manage`, bumps the authorization epoch and refreshes the live
session; mutation returns `PERMISSION_DENIED` and leaves the row unchanged. It
then creates a second platform account with a valid task: the original account
cannot mutate it, receives `PLATFORM_TASK_NOT_FOUND`, cannot see it in its page,
and the foreign row remains open at version zero.

## Integration corrections

- The frozen command contract was corrected to accept nonnegative versions,
  because the first legitimate Agent-created task version is zero.
- Lifecycle timestamps are truncated to MySQL `DATETIME(6)` microsecond
  precision before persistence, and advance one microsecond when the clock is
  not later than the stored value.
- Agent creation explicitly persists all lifecycle defaults so native
  `updatedAt` remains exactly equal to `createdAt`.
- The production lifecycle-service constructor is explicitly marked for
  Spring injection while the deterministic clock constructor remains available
  to owner tests.
- Upgrade evidence compares ROOT grants with the active ROOT roles actually
  present in a historical fixture; installations with no pre-existing ROOT
  role correctly receive zero grants, while non-ROOT grants remain forbidden.

## Completion boundary

This accepts only the native platform personal-task lifecycle node. It does not
accept a project package checkpoint, responsive/visual hardening, release gates
or final user acceptance. The next function-first gap is selected from the
remaining OpenAPI call-log and inbox/delivery-preference owner work.
