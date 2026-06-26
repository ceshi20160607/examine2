# G9 Build Evidence

- Batch: G9
- Tasks: `TASK-BE-040`, `TASK-FE-030`
- Evidence time: 2026-06-24T10:43:00+08:00
- Status: self-check PASS, pending independent `task-accept`

## Implemented / Verified

### TASK-BE-040

Evidence:

- `docs/evidence/backend-integration-smoke.md`
- `docs/evidence/build-g9-be040.md`

Result:

- Backend package passed.
- Startup smoke passed on `http://127.0.0.1:18089`.
- 75 HTTP integration calls passed.
- 0 HTTP integration calls failed.
- SSO, SecretRef, OpenAPI, Ops, Work, and Agent cross-module assertions passed.

Key boundaries verified:

- SSO links identity provider, system policy, organization precheck, and member binding confirmation.
- SecretRef and OpenAPI secret rotation do not expose plaintext secret material.
- Platform Agent cannot write/open system business data.
- Agent confirmation APIs are split into platform confirmation, system write confirmation, and work draft confirmation.
- Work project task and plain task boundaries are separate.

Contract-boundary note:

- `TASK-BE-040.md` mentions "data source" in the high-level goal.
- The frozen contract `docs/api/api.md` does not define standalone data-source endpoints.
- No post-freeze API was invented; the current frozen coverage is represented through OpenAPI app/source governance and configuration-oriented endpoints.

### TASK-FE-030

Evidence:

- `docs/evidence/build-g9-fe030.md`

Result:

- Runtime dynamic record page implemented.
- System shell now uses top module-group navigation and lets the business module page own its left module list.
- Business rows open the right detail panel by row click.
- Row actions contain only edit/delete/print, not duplicate detail/view actions.
- Batch actions show disabled states and visible reasons.
- Import/export are toolbar or batch entry points and open side panels with precheck/task feedback.
- Detail panel includes summary, detail tabs, attachments, print records, operation logs, approval timeline/actions, `traceId`, and `auditLogId`.

Scope-boundary note:

- `docs/tasks/plan.md` describes `TASK-FE-030` broadly as runtime/work/messages.
- Dedicated `docs/tasks/TASK-FE-030.md` explicitly excludes todo/message center, work management, and Agent confirmation pages.
- This batch followed the dedicated task file and did not expand into excluded pages.

## Verification Commands

Backend:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -pl examine-web -am -DskipTests package
```

Result: PASS.

Frontend:

```powershell
& 'D:\java\nodejs\npm.cmd' --prefix frontend run build
```

Result: PASS.

Static diff check:

```powershell
git diff --check
```

Result: PASS with only known LF/CRLF warnings on:

- `.cursor/session/issues/registry.jsonl`
- `.cursor/session/state.json`
- `docs/design/pre-coding-readiness.md`
- `docs/design/user-approval.md`

## Remaining Gate

- Independent `task-accept` is required before G9 can be marked accepted and G9-QA can start.
