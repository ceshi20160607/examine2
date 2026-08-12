# FAST-TODO-REMINDER-CC-101 Acceptance

- Verdict: PASS
- Accepted at: 2026-08-05T12:31:03+08:00
- Scope: project native `WORK_TASK_REMINDER` and `FLOW_INSTANCE_COPIED`
  Event inbox messages into the unified Todo runtime as `REMINDER` and `CC`,
  with Event-owned `MARK_READ` and no Work/Flow business mutation.

## Delivered behavior

- Added the closed Todo values `EVENT_MESSAGE`, `REMINDER`, `CC` and
  `MARK_READ` with strict source/category/action/recipient invariants.
- Added a narrow Event owner boundary that recognizes exactly the two approved
  templates, returns only safe projection fields, enforces live
  system/tenant/recipient scope and delegates state change to the native Event
  optimistic-lock service.
- Added the Web composition adapter; Todo performs no Event SQL and Event
  performs no Todo SQL.
- Todo refresh now discovers missing unread projections, remains idempotent and
  closes them through exact owner outcomes such as `SOURCE_COMPLETED`,
  `SOURCE_MISSING`, `SOURCE_STALE` and `RECIPIENT_INELIGIBLE`.
- Extended the existing Todo page filters/cards/action flow. The UI displays
  safe title/category/creation time/route only and never requests or renders
  Event body or template variables.
- Registered Ant Design Vue `Badge` globally so Todo counts and existing layout
  count badges resolve in the actual browser runtime.
- Added an opt-in Playwright-managed Vite server so real browser acceptance can
  start and stop its frontend runtime deterministically without changing the
  default E2E mode.
- Replaced a pre-existing nondeterministic Flow test assertion that searched
  random encrypted Base64 for short plaintext substrings; the assertion now
  checks only persisted plaintext metadata fields.

## Automated verification

- Backend owner modules: Core `81/81`, Todo `23/23`, Event `47/47`.
- Focused cross-module contracts: Core `2/2`, Todo `17/17`, Event `3/3`, Web
  Event adapter `3/3`; the selected Event/Todo/Web path passed `29/29`.
- Targeted `FlowApiJourneyIntegrationTest`: `1/1` passed after replacing the
  nondeterministic encrypted-text assertion (`349.7s` test time, zero failures,
  errors or skips).
- Frontend focused Todo tests: `2` files, `12/12` tests.
- Full frontend regression: `108` files, `555/555` tests.
- Frontend typecheck: PASS.
- Frontend production build: PASS, `5769` modules transformed; only the existing
  large-chunk warning remained.
- Real Microsoft Edge journey: `1/1` passed in `20.562s` (`26.147s` total),
  with zero unexpected, flaky or skipped tests.
- Strict UTF-8, framework active-mode, project-progress, cadence and VS4
  machine-contract validations: PASS (`43` endpoints, `77` DTOs, `49` fields,
  `20` tables).
- `git diff --check`: PASS; only existing line-ending conversion warnings were
  emitted.

## Real database and browser evidence

- Fresh MySQL 8.4 schema applied `100/100` Flyway migrations through `V8.78.0`.
- Live checks prove `ck_todo_item_source` accepts only exact Event reminder/CC
  projections with canonical positive-long message ids and `MARK_READ`, and
  `ck_todo_action_source` accepts only Event `MARK_READ` actions.
- The Edge journey created an actual Work reminder and Flow copy for an ordinary
  member, refreshed Todo, displayed both safe projections, marked the CC read
  through Todo and observed Event `READ` version `2`.
- Reading the reminder through the Event owner followed by Todo refresh closed
  it as `SOURCE_COMPLETED`; the CC action closed as `ACTION_COMPLETED` and wrote
  a completed Todo action log with `SUCCESS`.
- The journey proved foreign recipient/root and isolated-tenant Todo views do
  not reveal the member messages, and Work task plus Flow history remain
  unchanged by `MARK_READ`.
- Screenshot:
  `.cursor/session/evidence/fast-todo-reminder-cc-101/todo-reminder-cc.png`
- Screenshot SHA-256:
  `468a94f50d4d144681644c134895ee517c6a122ee19526e95d72ba8a4bc47a5d`
- Playwright JSON SHA-256:
  `8cd1c17b3329beaf0a1044f3d65dca2de8e7f2d035f920bcccc33183368ca0eb`

## Boundaries

- No arbitrary Event template projection, message-body projection, delivery
  retry/transport worker, external HTTP/JDBC data source or responsive sweep.
- No package, install or jar command was run. Package checkpoint attempts remain
  `0/3`; CP1, CP2 and CP3 stay frozen at their project-level gates.
