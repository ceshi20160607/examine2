# Pre-E2E Readiness Evidence - TASK-QA-018

- Task: `TASK-QA-018`
- Evidence time: 2026-06-24T10:56:00+08:00
- Status: PASS, pending independent task-accept

## Inputs Used

- `docs/testing/static-acceptance-checks.md`
- `docs/tasks/TASK-QA-018.md`
- `docs/tasks/TASK-BE-040.md`
- `docs/tasks/TASK-FE-030.md`
- `docs/evidence/build-g9.md`
- `docs/evidence/task-accept-g9.md`
- `docs/evidence/backend-integration-smoke.md`
- `docs/evidence/build-g9-fe030.md`

## Build Artifacts

Backend:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -pl examine-web -am -DskipTests package
```

Result: PASS.

Backend health startup:

- Started `backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar` on `http://127.0.0.1:18091`
- Checked `GET /api/v1/health`
- Result: `SUCCESS`
- Evidence: `docs/evidence/build-g9qa-backend-health.json`
- Logs:
  - `docs/evidence/build-g9qa-server.out.log`
  - `docs/evidence/build-g9qa-server.err.log`

Frontend:

```powershell
& 'D:\java\nodejs\npm.cmd' --prefix frontend run build
```

Result: PASS.

Frontend preview preparation:

- Started `vite preview` on `http://127.0.0.1:18090`
- Checked all prepared E2E entry routes return the SPA root and module script.
- Result: PASS
- Evidence: `docs/evidence/build-g9-frontend-preview.json`
- Logs:
  - `docs/evidence/build-g9-frontend-preview.out.log`
  - `docs/evidence/build-g9-frontend-preview.err.log`
- The preview process was stopped after the route checks.

## Prepared E2E Route Entrypoints

The following routes are ready for the G10 user-script/browser run:

- `#/login`
- `#/register-with-system`
- `#/forgot-password`
- `#/platform`
- `#/platform/admin`
- `#/systems/sys_vehicle/dashboard`
- `#/systems/sys_vehicle/modules`
- `#/systems/sys_vehicle/todos`
- `#/systems/sys_vehicle/work`
- `#/systems/sys_vehicle/admin`

## Static Acceptance Summary

| Check | Result |
|---|---|
| Login/register/password-reset routes exist | PASS |
| Platform shell, platform backend, system shell, and system backend stay separated | PASS |
| System shell uses top-level module groups and runtime module page owns the left module list | PASS |
| Runtime business rows open detail by row click | PASS |
| Checkbox and row action buttons do not trigger row detail | PASS |
| Runtime row actions do not include duplicate detail/view/open buttons | PASS |
| Batch actions expose disabled states and reasons | PASS |
| Runtime list includes filters, sorting labels, pagination, selected count, and async import/export task feedback | PASS |
| Detail drawer includes summary, tabs, attachments, print records, operation logs, approval sidebar, `traceId`, and `auditLogId` | PASS |
| Secret plaintext is not represented in checked SecretRef/OpenAPI evidence | PASS |
| Platform Agent, system write confirmation, and work draft confirmation flows are split | PASS |
| G9 independent task-accept is PASS | PASS |

## Commands And Results

```powershell
Test-Path docs/evidence/build-g9.md
Test-Path docs/evidence/pre-e2e-readiness.md
```

Result after this file is written: both expected outputs exist.

```powershell
git diff --check
```

Result: PASS with only known LF/CRLF warnings on:

- `.cursor/session/issues/registry.jsonl`
- `.cursor/session/state.json`
- `docs/design/pre-coding-readiness.md`
- `docs/design/user-approval.md`

## Remaining Risks Before TASK-QA-020

- This task did not mark `user_script_passed`; that remains owned by `TASK-QA-020`.
- Frontend preview checks confirmed route entry availability, but full browser click-flow coverage is still pending for G10. G10 should click through login, system switch, runtime row detail, import/export panels, approval actions, and role-gated admin entries.
- Current frontend uses mock shell data and contract-first static views. G10 should treat this as prototype-level frontend integration until real API binding is scheduled after the current task plan.
- The frozen API contract has no standalone data-source endpoint even though `TASK-BE-040.md` uses that high-level wording. This was recorded in G9 evidence and should not be reopened without a contract-change issue.

Verdict: PASS for pre-E2E readiness.
