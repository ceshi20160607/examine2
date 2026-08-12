# P4-C4 Functional Acceptance

- accepted scope: FORMULA, SUMMARY, CALCULATED, LOOKUP and AGGREGATE configuration, publication, typed materialization, deterministic recalculation, permission-aware query/readback, readonly frontend rendering and reference-desktop operation.
- backend: six-module Maven reactor passed with JDK 21; task 01/02 manifests remain the storage/runtime evidence.
- frontend: 9 files / 36 unit tests passed; typecheck and production build passed.
- real entry: `http://127.0.0.1:5175/systems/2079248383403778050/workbench?module=work_order`.
- real-browser READY readback: the published Work order list showed the materialized values `41.5`, `41.5000000000`, `42.5`, `1`, `P4-C4 Restart Acme`, and `8.25`.
- task 03 manifest: `.cursor/session/evidence/p4-c4/task-03.json`.

Verdict: `PASS_FUNCTIONAL`.

This verdict does not claim the last standalone Playwright run passed. Its configured browser executable was unavailable. It also does not treat the existing duplicate state screenshots as proof. The exhaustive desktop/mobile/breakpoint, PENDING/FAILED/retry/permission/stale/restart visual matrix, accessibility and final visual consistency are explicitly deferred to `P10-H1-UI-HARDENING`; they no longer block subsequent independent feature modules, but must pass before release.
