# P4-A2 Create And Activate Acceptance

## Verdict

Leader verdict: **PASS** at `2026-07-17T16:43:00+08:00`.

## Accepted Outcome

A non-root system member with explicitly published runtime, workbench, module view, create and update grants can open the published `work_order` module, save a real typed draft, see required-field activation errors, create all eight initial field types, activate the draft, reload its detail and read the same record after a backend restart.

## Evidence

- `P4-A2-01`: create-draft transaction, typed validation, idempotency, platform reference port and MySQL readback in `task-01.json`.
- `P4-A2-02`: activation CAS, required fields, lifecycle/idempotency, dedicated ordinary-member permission negatives and restart readback in `task-02.json`.
- `P4-A2-03`: schema-driven desktop/mobile form, visible error state, real API journey, screenshots and regressions in `task-03.json`.
- Demo entry: `http://127.0.0.1:5173/systems/2078026635551383554/workbench?module=work_order`.

## Gate Checks

- API: pass; create is `201 DRAFT v0`, activate is `200 ACTIVE v1`, and stable validation/state/version/idempotency errors are reproduced.
- DB: pass; the two final ordinary-member records each contain exactly 8 typed values and 8 typed indexes, and no test draft remains.
- UI: pass; typecheck, 18 unit tests and the production build pass.
- Browser: pass at `1440x900` and `390x844` with a non-root principal; no horizontal overflow or unexpected console/page/5xx errors.
- Permission: pass; missing create and missing update grants independently return `403 PERMISSION_DENIED`, then the final least-privilege role succeeds.
- Restart: pass; backend PID `26400` was replaced by `27040`, health returned `UP`, and a fresh ordinary-member browser context read both final records.
- Regression: pass; P4-A1 real-record read and VS3 ordinary-member isolation journeys both pass.
- Architecture: pass; `examine-module` contains no direct `un_plat_*` reference and uses the `examine-core` runtime reference port.

## Completion Boundary

This acceptance closes only P4-A2 create-draft and activate. It does not claim existing-draft edit/autosave, archive/recovery, query/view/bulk operations, additional field families, relation/subtable behavior, comments/history/files, Flow, task center, reports, release, final-system completion or user final acceptance. The next engineering node is `P4-B1-EDIT-AUTOSAVE`.
