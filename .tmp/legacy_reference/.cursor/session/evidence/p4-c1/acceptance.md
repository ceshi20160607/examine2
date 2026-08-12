# P4-C1 Numeric, Time And Selection Fields Acceptance

## Verdict

PASS for the bounded `P4-C1-NUMERIC-TIME-SELECTION-FIELDS` slice.

An explicitly authorized ordinary member can configure, create, autosave, activate, edit, query and read records containing `PERCENT`, `MONEY`, `DATE_RANGE`, `TIME`, `TIME_RANGE`, `MULTI_SELECT`, `CASCADE`, `SWITCH`, `RATING`, `PROGRESS` and `TAG`. Values use the accepted canonical shapes, typed server queries and transactional uniqueness from tasks 01/02, render through domain controls on desktop and mobile, and survive a packaged-backend restart.

## Demo

- Entry: `http://127.0.0.1:5173/systems/2079127650187034626/workbench?module=work_order`
- Account: `p4c1q_member_fa5a8b3d`
- Password: `P4-C1-Q-Member-fa5a8b3d!`
- Fixture: `.cursor/session/evidence/p4-c1/task-02-fixture.json`
- Desktop accepted record: `2079153913844924418`
- Mobile accepted record: `2079154029737738242`

## Accepted Tasks

- `P4-C1-01`: publication validation, canonical normalization, typed persistence, boundaries, permission negatives and restart readback in `task-01.json`.
- `P4-C1-02`: all 57 explicit query operators, six sortable types and transactional scalar uniqueness through lifecycle/restart in `task-02.json`.
- `P4-C1-03`: responsive configuration, domain form controls, detail/edit/refresh, typed query, unique feedback and packaged restart in `task-03.json`.

## Independent Evidence

- Frontend Vitest passed 8 files / 27 tests; strict typecheck and the Vite production build passed.
- Backend passed 1 core test and 8 module tests. `P4A1SchemaIntegrationTest` applied and validated all 11 migrations through V4.5.0 on an independent MySQL 8.4 container.
- Real Edge journeys passed at desktop `1440x900` and mobile `390x844`; configuration, top/bottom form, detail, typed query and result evidence is stored in this directory.
- Browser journeys reported zero unexpected console/page errors, HTTP 5xx or document horizontal overflow.
- The ordinary member created and activated all eleven values, edited progress, refreshed, retained the other canonical values, queried by percentage and exact CNY money, and received field-level unique-conflict feedback.
- Packaged JAR SHA-256 `a900b5e4f15a6000d4e3f6d43c44740eef93b3f3845f960b4213357868c34a4a` replaced PID `26840`; PID `46940` reached readiness `UP` and fresh login read desktop record `2079153913844924418` with the same eleven canonical values.
- `.base` reusable framework and the `.cursor` project instance both passed framework validation.

## Resolved Findings

- Published cascade options lacked `parentValue`; the runtime schema now exposes the parent relationship and the Cascader renders a real tree.
- Cascader, Rate and Slider were not registered; all three now render and are exercised in both responsive journeys.
- Edit controls appeared before the route record loaded; `formReady` now blocks editing and actions until the active aggregate matches the route.
- TAG Enter could submit the outer form; tag creation now completes without implicit submit and values persist.
- Configuration selects lacked explicit labels and the shared modal mixed English `Cancel` into Chinese UI; labels and localized actions are now consistent.

## Completion Boundary

This acceptance closes only P4-C1 and does not claim complete P4, release readiness, final-system completion or user final acceptance. `PHONE`, `EMAIL`, `URL`, `IDENTITY`, `ADDRESS`, `GEO`, `RICH_TEXT`, `JSON`, `SECRET`, `STATUS` and `BARCODE` remain the next bounded slice, `P4-C2`; all relation, derived-field, file, collaboration, Flow, task, report and release work remains open.
