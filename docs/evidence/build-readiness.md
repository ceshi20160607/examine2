# Build Readiness Evidence - TASK-QA-020

- Task: `TASK-QA-020`
- Evidence time: 2026-06-24T11:11:25+08:00
- Verdict: PASS for build-to-coding readiness; independent `task-accept` PASS

## Linked Evidence

- G9 clean build: `docs/evidence/build-g9.md`
- Pre-E2E readiness: `docs/evidence/pre-e2e-readiness.md`
- Backend integration smoke: `docs/evidence/backend-integration-smoke.md`
- Permission matrix: `docs/evidence/permission-matrix.md`
- Che E2E evidence: `docs/evidence/e2e-che-script.md`
- Che E2E raw evidence: `docs/evidence/e2e-che-script.raw.json`
- Independent task acceptance: `docs/evidence/task-accept-g10.md`

## Build Commands

Backend package:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;D:\java\nodejs;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -pl examine-web -am -DskipTests package
```

Result: PASS.

Frontend build:

```powershell
& 'D:\java\nodejs\npm.cmd' --prefix frontend run build
```

Result: PASS.

Runtime route/API verification:

- Backend server started on `http://127.0.0.1:18092` for the E2E API run.
- Frontend preview route check ran on `http://127.0.0.1:18093`.
- Browser DOM verification ran on `http://127.0.0.1:18094`.
- All test ports were stopped after verification.

Logs:

- `docs/evidence/build-g10-server.out.log`
- `docs/evidence/build-g10-server.err.log`
- `docs/evidence/build-g10-frontend-preview.out.log`
- `docs/evidence/build-g10-frontend-preview.err.log`
- `docs/evidence/build-g10-browser-preview.out.log`
- `docs/evidence/build-g10-browser-preview.err.log`

## Readiness Matrix

| Area | Result | Notes |
|---|---:|---|
| Design/user approval gate | PASS | `design_user_approved=true`; prototype was previously locked for coding. |
| API contract gate | PASS | `api_frozen=true`; G10 did not change API contract. |
| Task plan gate | PASS | `tasks_planned=true`; G10 only wrote declared QA outputs. |
| Backend compile/package | PASS | Maven package for `examine-web` passed. |
| Frontend build | PASS | Vite/TypeScript production build passed. |
| E2E contract script | PASS | 53 checks, 0 failures. |
| Browser route shell | PASS | 10 key routes mounted; login/register/password reset, platform, system runtime, todo, work, and admin shells are reachable. |
| Permission matrix | PASS | Four-role matrix has no P0/P1 failure. |
| Import/export | PASS | Toolbar/drawer/task contract verified; no list-under-table import/export block in browser DOM. |
| Runtime detail | PASS | Row-click target and right detail panel rendered; detail actions are separated from row primary action. |
| Todo/message/work | PASS | API evidence covers list/action/target flows; browser route signals are present for todo/work. |
| Logs/traceability | PASS | Runtime responses include `traceId`/`auditLogId`; representative trace IDs are recorded in `e2e-che-script.md`. |

## Known Scope Boundaries

- This readiness file does not set `gates.user_script_passed=true`; that remains reserved for the user's own final script/run confirmation.
- Frontend shell data is still mock/contract-first. The next coding phase must bind shell roles, system switch context, route guards, field permissions, and runtime data to real APIs before treating frontend permission hiding as production enforcement.
- The in-app browser row-click interaction produced one automation-coordinate failure, but DOM evidence confirms the clickable row contract and no duplicate row detail buttons. This is tracked as a browser-tool limitation, not a product P0/P1 issue.
- The frozen API contract does not define a standalone "data source" endpoint. Current coverage is through OpenAPI app/source governance and configuration endpoints, as already recorded in G9 evidence.

## Self Check

Expected task outputs:

```powershell
Test-Path docs/evidence/e2e-che-script.md
Test-Path docs/evidence/build-readiness.md
```

Actual result: both `True`.

Port cleanup:

```powershell
foreach ($port in 18092,18093,18094) { ... }
```

Actual result:

- `PORT 18092 clear`
- `PORT 18093 clear`
- `PORT 18094 clear`

Static diff check:

```powershell
git diff --check
```

Actual result: PASS with only known LF/CRLF warnings on:

- `.cursor/session/issues/registry.jsonl`
- `.cursor/session/state.json`
- `docs/design/pre-coding-readiness.md`
- `docs/design/user-approval.md`

Independent acceptance:

- `docs/evidence/task-accept-g10.md`
- Verdict: PASS
- Conclusion: `TASK-QA-020` meets the stated acceptance criteria and G10 can be marked `accepted`.

Final status: all planned build batches through G10 are accepted. `gates.user_script_passed` remains `false` until the user's final script confirmation.
