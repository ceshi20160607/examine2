# TASK-BE-040 Self Check - G9

- Task: `TASK-BE-040`
- Owner: backend
- Evidence time: 2026-06-24T10:30:00+08:00
- Output evidence: `docs/evidence/backend-integration-smoke.md`

## Build

Command:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -pl examine-web -am -DskipTests package
```

Result: PASS.

## Integration Smoke

Command: start `backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar` on port `18089`, then execute the cross-module smoke script recorded in `docs/evidence/backend-integration-smoke.md`.

Result:

- 75 HTTP calls passed.
- 0 HTTP calls failed.
- Required SSO, SecretRef, OpenAPI, Ops, Work, and Agent assertions passed.
- Startup log: `docs/evidence/build-g9-server.out.log`
- Error log: `docs/evidence/build-g9-server.err.log` is empty.

## Acceptance Mapping

| TASK-BE-040 acceptance item | Evidence |
|---|---|
| SSO links identity provider, org mapping, employee binding, and `NoMemberAccessRequest`/member-binding flow | `sso.identityProviders.*`, `sso.systemPolicy.patch`, `sso.orgSync.precheck`, `sso.memberBinding.confirm` all PASS |
| Agent confirmations are split into platform, system write, and work draft confirmation | `agent.platformConfirm.systemBoundary`, `agent.systemWrite.*`, and `agent.workDraft.confirm` all PASS |
| Work tasks support project and plain task boundaries | project task create returns `taskType=PROJECT` with `projectId`; plain task create returns `taskType=PLAIN` without `projectId` |
| `task-accept` verdict is pass | Pending independent G9 task-accept after FE-030 completes |

## Notes

- No backend source changes were required during this BE-040 step.
- The task's high-level "data source" wording has no explicit frozen endpoint in `docs/api/api.md`; this was recorded as a contract-boundary note in `docs/evidence/backend-integration-smoke.md` rather than adding post-freeze API surface.
