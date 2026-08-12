# P4-C3 Relation, Reference And Subtable Acceptance

## Verdict

PASS for the bounded `P4-C3-RELATION-REFERENCE-SUBTABLE-CONTRACT` slice.

An explicitly authorized ordinary member can search server-scoped relation candidates, atomically create and activate a record containing a required relation and typed subtable rows, observe a permission-aware reference result and state, and read the same composition through responsive desktop/mobile layouts. Exact configuration contracts, persistence, query/recalculation, failure retry, concurrency and packaged-restart readback are independently evidenced.

## Demo

- Entry: `http://127.0.0.1:5174/systems/2079248383403778050/workbench?module=work_order`
- Ordinary account: `p4c3_member_6f9a4ade`
- Accepted desktop record: `2079516923398713345`
- Accepted target record: `2079251704290177026`
- Reference state: `READY`

## Accepted Tasks

- `P4-C3-01`: exact publication snapshots, composite-key persistence, atomic relation/subtable writes, rollback and packaged restart.
- `P4-C3-02`: relation/subtable API, typed query, authorization, asynchronous reference recalculation, retry/concurrency and restart.
- `P4-C3-03`: exact configuration controls, server-scoped candidate picker, aggregate-safe create/edit, desktop/mobile form/detail and packaged restart readback.

## Independent Evidence

- Backend full Maven reactor passed all five modules; module tests passed 24/24 and Web integration tests passed 7/7 with zero failures.
- Frontend Vitest passed 8 files / 29 tests; strict typecheck and production build passed.
- VS4 API machine contract passed 43 endpoints, 77 DTOs, 49 fields, 30 derived fixtures, 20 tables and 23 failpoint operations.
- Real Edge journeys passed desktop atomic create/read, exact administrator configuration and mobile-card read with no unexpected HTTP 5xx, page errors, console errors, technical-id disclosure or horizontal overflow.
- The dedicated candidate endpoint returned exactly target `2079251704290177026` for its full display value to the authorized member; the restricted member received `403 PERMISSION_DENIED`.
- Packaged JAR SHA-256 `8a51d0426e1688a29254f47cd513fbcab5815247c209b9d28a993f62c6a561cd` restarted from PID `2584` to PID `16596`; health/readiness returned `UP` and record `2079516923398713345` retained its relation, READY reference and typed subtable row.
- P0 `P4-C3-RELATION-PICKER-SERVER-SEARCH` is closed after positive, negative and browser reproduction; reopening conditions remain recorded in the issue registry.

## Leader Boundary

This acceptance closes only P4-C3. It does not claim complete P4, release readiness, final-system completion or user final acceptance. P4-C4 and later derived-field, file/media, collaboration, Flow, task-center, report and release work remain open.
