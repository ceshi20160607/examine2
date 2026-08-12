# P4-B1 Edit And Autosave Acceptance

## Verdict

Leader verdict: **PASS** at `2026-07-17T20:53:00+08:00`.

## Accepted Outcome

A non-root ordinary member can reopen a server DRAFT URL, manually edit DRAFT or ACTIVE records, autosave a DRAFT after three seconds idle or field blur, recover server values after refresh and backend restart, retain local input after failure, and resolve a CAS conflict by reloading the server snapshot or copying local input into a distinct draft. The client permits one mutation in flight and coalesces later edits into the next versioned snapshot.

## Evidence

- `P4-B1-01`: authorized DRAFT read and idempotent DRAFT/ACTIVE manual update in `task-01.json`.
- `P4-B1-02`: DRAFT-only autosave, expiry renewal, typed replacement and structured CAS conflict in `task-02.json`.
- `P4-B1-03`: desktop/mobile UI, single-flight behavior, failure retry, conflict recovery, permission negatives, DB state, regressions and restart readback in `task-03.json`.
- Demo entry: `http://127.0.0.1:5173/systems/2078026635551383554/workbench?module=work_order`.
- Restart readback record: `2078099185690107905`.

## Gate Checks

- API: pass; manual update and autosave enforce idempotency, schema, state and expected-version CAS, and conflicts return an authorized current snapshot.
- DB: pass; the final ACTIVE v2 record contains exactly 8 typed values and 8 typed indexes, stale rows are absent, and no P4-B1 DRAFT remains.
- UI: pass; production build and all 19 frontend unit tests pass; saved, dirty, saving, error and conflict states are visible.
- Browser: pass; ordinary-member desktop and mobile full journeys plus post-restart desktop readback pass without unexpected page errors, HTTP 5xx or horizontal overflow.
- Permission: pass; a view-only ordinary member receives non-leaking 404 for DRAFT read and 403 for update/autosave.
- Restart: pass; packaged backend PID `1568` was replaced by PID `39888`, health returned `UP`, and a fresh ordinary-member login read the same ACTIVE edit from the server.
- Regression: pass; P4-A2 create/activate, P4-A1 list/detail and evolved VS3 configuration/publication journeys pass.

## Completion Boundary

This acceptance closes only P4-B1 existing-record edit, DRAFT autosave/recovery and conflict handling. It does not claim archive, trash, discard, restore, expiry recovery, my-drafts, advanced query/view/bulk behavior, additional field families, relation/subtable behavior, comments/history/files, Flow, task center, reports, release, final-system completion or user final acceptance. The next engineering node is `P4-B2-LIFECYCLE-RECOVERY`.
