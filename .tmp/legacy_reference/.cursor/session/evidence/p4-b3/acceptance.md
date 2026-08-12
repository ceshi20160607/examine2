# P4-B3 Query And Saved Views Acceptance

## Verdict

PASS for the bounded `P4-B3-QUERY-SAVED-VIEW` slice.

An explicitly authorized non-root member can search indexed text, combine typed filters, apply typed sorting and visible columns, page server results, switch among permitted active/archive/trash scopes, persist and manage personal saved views, share and restore canonical URL state, navigate list history, explicitly repair a stale saved view, and read the same view and records after a packaged-backend restart.

## Demo

- Entry: `http://127.0.0.1:5173/systems/2079081732209344514/workbench?module=work_order`
- Account: `p4b3_member_3976ab72`
- Password: `P4-B3-Member-3976ab72!`
- Main fixture: `.cursor/session/evidence/p4-b3/fixture.json`
- Isolated stale-view fixture: `.cursor/session/evidence/p4-b3/stale-fixture.json`

## Accepted Tasks

- `P4-B3-01`: strict typed query/search/index/scope execution and MySQL permission negatives in `task-01.json`.
- `P4-B3-02`: owner-scoped versioned saved-view persistence, validation, CAS, idempotency and stale-node semantics in `task-02.json`.
- `P4-B3-03`: desktop/mobile workbench, URL/history restoration, personal view workflow, explicit repair and restart readback in `task-03.json`.

## Independent Evidence

- Testcontainers integration passed against MySQL 8.0.44 after all nine Flyway migrations through `v4.3.0`; the new assertion proves search rows become ACTIVE at activation time.
- The remote database reports Flyway `4.3.0` successful. Main ACTIVE, ARCHIVED and TRASHED records each have matching typed-index and search-row lifecycle states.
- Frontend production build and typecheck passed; Vitest passed 7 files / 22 tests.
- Real Edge browser journeys passed at desktop `1440x900` and mobile `390x844` with no unexpected console/page errors, HTTP 5xx or document overflow.
- An isolated published-schema change retired `description`; the ordinary member saw server `invalidNodes`, confirmed repair, and API/MySQL readback showed version 1 with null filter and no retired column.
- After packaged-backend PID `22104` was replaced by PID `33064`, fresh ordinary-member login restored view `2079090116388147202`, its full URL query and record `2079090072419258370`.

## Resolved Findings

- Activation left search rows in DRAFT while the aggregate became ACTIVE. The activation transaction now updates both index tables, and integration coverage asserts the state before any later record update.
- User list changes used replace-navigation, so browser back skipped list states. User actions now push history while normalization and internal synchronization continue to replace.
- The reusable fixture script lost authorization after department creation. It now re-authenticates after that permission-epoch change and reports HTTP status on provisioning failures.

## Completion Boundary

This acceptance closes only P4-B3 typed query, scope list and personal saved-view behavior. It does not claim batch operations, additional field packages, relation/subtable, collaboration, Flow, task center, reports, release readiness, complete P4, final-system completion or user final acceptance. The next planned product slice is `P4-C1`; its bounded contract must be reviewed before implementation.
