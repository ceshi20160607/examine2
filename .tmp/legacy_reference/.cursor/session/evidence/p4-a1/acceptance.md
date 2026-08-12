# P4-A1 Read Runtime Acceptance

## Verdict

Leader verdict: **PASS** at `2026-07-17T14:06:00+08:00`.

## Accepted Outcome

An authorized system member can enter a published module, read a real server-paged record list, open a typed field detail, reload the same URL, switch to an empty archived view, and receive a non-leaking not-found response for an unavailable direct ID.

## Evidence

- `P4-A1-01`: migration, four-table typed read model, generated base, constraints, upgrade and restart evidence in `task-01.json`.
- `P4-A1-02`: published-snapshot HTTP schema/list/detail, authorization scope, MySQL integration and precise jar restart in `task-02.json`.
- `P4-A1-03`: real desktop/mobile UI, focused unit tests, three viewport browser journeys and ordinary-member regression in `task-03.json`.
- Demo entry: `http://127.0.0.1:5173/systems/2077996667744235522/workbench?module=work_order`.

## Gate Checks

- API: pass.
- DB: pass; active demo contains exactly two records.
- UI: pass; typecheck, unit and production build pass.
- Browser: pass at `1440x900`, `1280x720` and `390x844`; no horizontal page overflow.
- Restart: pass; the exact packaged backend and fresh browser contexts read the same published record/version.
- Permission: pass; a role without `module.work_order.view` receives `PERMISSION_DENIED`, and direct ID rejection does not disclose existence.

## Completion Boundary

This acceptance closes only P4-A1 read runtime. It does not claim record creation, edit, activation, workflow, task center, release, or final-system completion. The next engineering node is `P4-A2-CREATE-ACTIVATE`.
