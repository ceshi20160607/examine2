# FAST-BATCH-TRASH-MY-DRAFTS-FLOW-HANDOFF-10 Acceptance

Accepted at: 2026-07-27T10:46:06+08:00

Verdict: PASS

## Delivered functional closures

- Runtime record lists can move an explicit cross-page selection to trash in one idempotent, all-or-nothing
  command. Records are locked in numeric order, every precondition is checked before mutation, and response
  items remain in request order.
- The runtime exposes the authenticated member's own drafts through a fixed owner/status query. Other owners are
  excluded server-side, normalized record-number/title search is supported, and activating a draft removes it
  from the next result.
- The existing workbench exposes a dedicated My Drafts scope and batch trash controls while reusing record
  pagination, detail, editing, autosave and activation. Fixed-scope drafts do not expose saved-view or advanced
  query controls.
- Sequential Flow is proven with two real registered members and independent sessions. A pending task is handed
  from the owner to the second member, early/late unauthorized decisions are forbidden, and terminal replays
  conflict without duplicate transitions.
- Responsive, breakpoint, accessibility and visual hardening remains deferred to P10-H1.

## Verification

- `examine-module`: 74 tests passed; its `examine-core` dependency: 3 tests passed.
- `examine-flow`: 26 tests passed.
- Real authenticated HTTP journeys passed:
  - `SystemFieldJourneyIntegrationTest`: no-CSRF own-draft query, member isolation, normalized search, invalid
    query rejection, activation removal, batch-trash zero-mutation failure, mixed-state atomic success, request
    order and same-key replay without duplicate history/audit/outbox effects.
  - `FlowApiJourneyIntegrationTest`: two real members, independent cookies/CSRF, owner-to-member task handoff,
    approve and reject terminal paths, 403/409 safety, persisted actor history and tenant isolation.
- MySQL 8.0.44 empty databases applied 26 Flyway migrations through `V8_4_0`.
- Frontend: 31 test files and 85 tests passed.
- Frontend TypeScript/Vue typecheck and production build passed.
- Instance framework structure validation passed and `state.json` parsed successfully.

## Non-blocking observation

The production build retains the existing warning for chunks above 500 kB. Functional output is valid; code
splitting and the full viewport/accessibility/visual matrix remain grouped with P10-H1.
