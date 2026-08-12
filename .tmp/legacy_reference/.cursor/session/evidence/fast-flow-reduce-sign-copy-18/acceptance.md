# FAST-FLOW-REDUCE-SIGN-COPY-18 Acceptance

Accepted at: 2026-07-27T20:07:59+08:00

Verdict: PASS

## Delivered functional closures

- A current approver with `flow.instance.reduce-sign` can idempotently remove exactly one future runtime step by
  zero-based index. Current/completed/out-of-range steps and invalid reasons are rejected without mutation.
- Index-based reduction remains unambiguous when the same member occurs more than once in the runtime sequence.
  Success keeps cursor, current owner, claim state and instance status unchanged, persists the shorter snapshot
  and appends `SIGN_REMOVED` with actor, removed member, target step index, normalized reason and time.
- Later approval follows the shortened runtime snapshot and can complete immediately after its new last step.
  Same-key replay cannot remove a second step; changed-body reuse conflicts.
- A member with `flow.instance.copy` can copy one active non-self same-tenant member on a pending or terminal
  instance with an optional normalized message. Instance existence is checked before target lookup to avoid
  member enumeration.
- Copy persists one immutable tenant-scoped fact and dispatches one `FLOW_INSTANCE_COPIED` unread inbox message
  with Flow-instance target, business key and optional message in the same transaction through the existing Core
  message facade.
- The instance/recipient unique key prevents duplicate facts. Same-key replay produces neither another copy nor
  another notification; a different key for the same recipient returns `FLOW_COPY_ALREADY_EXISTS`.
- Copy lists verify the scoped instance and use stable chronological pagination. Copy does not alter decision
  history or grant read permission.
- V8.12 adds nullable `target_step_index`, preserves every prior history fact, creates the restrictive copy table
  with exact unique/page/FK contracts, backfills both permissions/root grants and advances authorization epochs.
  `PermissionCatalog` covers newly created systems.
- The frontend lists only future reduce-sign candidates from the runtime snapshot, preserves failed reason/index
  drafts and refreshes instance/tasks/history after success. Pending and terminal details can copy through the
  existing member picker, preserve failed drafts and refresh the copy timeline. Assignment history renders the
  removed target step. Responsive/visual hardening remains deferred to P10-H1.

## Verification

- Core + Flow: 42 test files / 118 tests passed with zero failures, errors or skips:
  - `examine-core`: 3 files / 5 tests.
  - `examine-flow`: 39 files / 113 tests.
- `PermissionCatalogTest`: 6/6 passed.
- Frontend: 37 test files / 147 tests passed; Vue/TypeScript typecheck and production build passed.
- Real authenticated HTTP on MySQL 8.0.44:
  - `FlowApiJourneyIntegrationTest` passed 1/1.
  - All 34 migrations applied through V8.12.
  - A repeated-member three-step sequence proved current/out-of-range/reason validation, index-two removal,
    exact replay/changed-body conflict, reload-safe `[first, second]` snapshot, complete short path and exact
    `STARTED -> SIGN_REMOVED -> APPROVED -> APPROVED` history with target member/index.
  - The terminal instance then proved self/inactive/501-character copy validation, normalized success, exact
    replay/changed-body conflict, different-key duplicate conflict, one stable copy fact, unchanged Flow history
    and exactly one unread recipient inbox message with the expected template/sender/recipient/target/body.
- `P4A1SchemaIntegrationTest` passed 1/1 on MySQL 8.4.10. A populated V8.11 database applied exactly V8.12 once
  and zero on rerun; the new history column/table, exact unique and page indexes, restrictive instance FK, two
  permissions and null prior facts all passed without weakening V8.11/terminal/assignment constraints.
- Batch18 scoped trailing-whitespace checks passed.

## Non-blocking observation

The production build retains the existing warning for chunks above 500 kB. Configured copy nodes and the full
viewport/accessibility/visual matrix remain later bounded work.
