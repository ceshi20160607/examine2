# G4 / G4-QA Task Acceptance Evidence

## Verdict

PASS.

G4 batch `TASK-BE-014`, `TASK-BE-020`, `TASK-BE-032`, `TASK-BE-034`, `TASK-BE-038`, `TASK-FE-010`, `TASK-QA-012`, and `TASK-QA-015` are accepted for the current contract-first build scope.

G5 unlock: allowed.

## Worker Scope

- Worker role: independent `task-accept` worker, not implementation worker.
- Input policy: only read persisted project files and current code under `E:\workspace\03_project\unique\java\cursor\examine2`.
- Write policy: no implementation code was edited. The only intentional report output is this file: `docs/evidence/task-accept-g4.md`.

## Required Files Read

- `.cursor/README.md`
- `.cursor/session/state.json`
- `.cursor/knowledge/agent-operating-rules.md`
- `.cursor/knowledge/project-operating-rules.md`
- `.cursor/knowledge/failure-lessons.md`
- `docs/tasks/TASK-BE-014.md`
- `docs/tasks/TASK-BE-020.md`
- `docs/tasks/TASK-BE-032.md`
- `docs/tasks/TASK-BE-034.md`
- `docs/tasks/TASK-BE-038.md`
- `docs/tasks/TASK-FE-010.md`
- `docs/tasks/TASK-QA-012.md`
- `docs/tasks/TASK-QA-015.md`
- `docs/evidence/build-g4.md`
- `docs/evidence/backend-plat-smoke.md`
- `docs/evidence/build-g4-fe010.md`
- `docs/evidence/permission-matrix.md`
- `docs/evidence/role-route-check.md`

Additional supporting evidence read:

- `docs/evidence/build-g3.md`
- `docs/evidence/build-g4-be020.md`
- `docs/evidence/build-g4-be032.md`
- `docs/evidence/build-g4-be034.md`
- `docs/evidence/build-g4-be038.md`

## Task Output Coverage

| Task | Acceptance check |
|---|---|
| `TASK-BE-014` | `docs/evidence/backend-plat-smoke.md` exists and reports G3 platform smoke PASS for login, system creation, system switch, tenant switch, role permission, and permission preview. |
| `TASK-BE-020` | `backend/examine-module/src/main/java/com/unique/examine/module/manage/config/**` exists and exposes module groups, modules, fields, dictionaries, scenes, list schema, actions, permissions, import/export metadata, print templates, publish check, publish, and rollback endpoints. |
| `TASK-BE-032` | `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/message/**` and `manage/notification/**` exist and expose platform/system message search/state actions plus notification template and delivery log APIs. |
| `TASK-BE-034` | `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/sso/**`, `manage/secret/**`, and `manage/nomember/**` exist and expose SSO policy, SecretRef/rotation, mapping precheck, member binding, and no-member request APIs. |
| `TASK-BE-038` | `backend/examine-core/src/main/java/com/unique/examine/core/manage/ops/**` exists and exposes health check, feature flag, quota, rate limit, backup/restore, archive restore, deployment rollback, and cache policy APIs. |
| `TASK-FE-010` | `frontend/src/features/auth/**`, `frontend/src/features/platform/**`, `frontend/src/features/system-shell/**`, plus app route/state wiring exist and build successfully. |
| `TASK-QA-012` | `docs/evidence/permission-matrix.md` and `docs/evidence/role-route-check.md` exist and cover platform root/member, system creator admin, and normal system member route boundaries. |
| `TASK-QA-015` | `docs/evidence/build-g3.md` exists and supports G3 clean-build/static evidence needed by G4-QA. |

No G4 task required editing generated `base/**` Java files. The G4 implementation paths are under `manage/**` plus POM aggregation/dependency wiring and evidence files. Because `backend/` is currently an untracked directory in Git, Git cannot conclusively prove per-file edit ancestry for untracked generated base files; however the G4 evidence and code path scan did not identify base generated files as G4 outputs or required edits.

## Web Aggregation

- `backend/examine-web/pom.xml` depends on `examine-core`, `examine-plat`, `examine-message-log`, and `examine-module`.
- `backend/examine-web/src/main/java/com/unique/examine/web/ExamineWebApplication.java` scans `com.unique.examine`.
- Web package command passed and produced `backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar`.
- Startup smoke against the packaged jar passed for all required G4 endpoints.

## Boundary Checks

- Platform message boundary: smoke returned platform message target types `platform_auth` and `system_switch`; no `business_record` target was returned from the platform message endpoint.
- Secret boundary: `/api/v1/secrets/sec_idp_dingtalk_v2` response did not contain obvious plaintext fields such as `plainText`, `plaintext`, `secretValue`, `storageRef`, or `clientSecret`.
- No-member boundary: `/api/v1/systems/sys_vehicle/no-member-access-requests` returned `businessAccessAllowed=false` for the first sample request before approval/binding.
- Ops boundary: dangerous operations are represented as sync result or `AsyncTaskView` placeholders; code scan found rollback/backup/restore style APIs create task/dry-run boundaries rather than executing destructive infrastructure actions.
- Module configuration boundary: list schema exposes columns, filters, sorters, pagination, row click target, batch actions, import/export metadata, and permission metadata; import/export execution is not implemented in G4.

## Commands Run

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -DskipTests compile
```

Result: PASS. Reactor included `examine-core`, `examine-plat`, `examine-module`, `examine-flow`, `examine-message-log`, `examine-upload`, `examine-app`, `examine-ai-work`, `examine-generator`, and `examine-web`; Maven reported `BUILD SUCCESS`.

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-web -am -DskipTests package
```

Result: PASS. Maven reported `BUILD SUCCESS` and Spring Boot repackage replaced the web jar with an executable archive.

```powershell
& 'D:\java\nodejs\npm.cmd' --prefix frontend run build
```

Result: PASS. `tsc --noEmit && vite build` completed and produced `dist/index.html`, CSS, and JS assets.

```powershell
git diff --check
```

Result: PASS with LF/CRLF warnings only:

- `.cursor/session/issues/registry.jsonl`
- `docs/design/pre-coding-readiness.md`
- `docs/design/user-approval.md`

No whitespace errors were reported.

## Startup Smoke

Temporary jar command pattern:

```powershell
D:\java\jdk\jdk21\bin\java.exe -jar backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar --server.port=<free-port>
```

Smoke run used free port `60467` with Java process `17128`. The process was stopped after smoke. A pre-existing unrelated Java process was still present afterward: PID `16468`, `D:\java\jdk\jdk8\bin\java.exe`, started at `2026/6/23 16:19:31`; it was not touched.

Smoke results:

| Endpoint | Method | Result |
|---|---:|---|
| `/api/v1/health` | GET | SUCCESS |
| `/api/v1/systems/sys_vehicle/module-groups` | GET | SUCCESS |
| `/api/v1/systems/sys_vehicle/modules/module_vehicle/list-schema` | GET | SUCCESS |
| `/api/v1/platform/messages/search` | POST | SUCCESS |
| `/api/v1/systems/sys_vehicle/messages/search` | POST | SUCCESS |
| `/api/v1/platform/identity-providers` | GET | SUCCESS |
| `/api/v1/systems/sys_vehicle/sso/policies` | GET | SUCCESS |
| `/api/v1/secrets/sec_idp_dingtalk_v2` | GET | SUCCESS |
| `/api/v1/systems/sys_vehicle/no-member-access-requests` | GET | SUCCESS |
| `/api/v1/platform/ops/health-check` | POST | SUCCESS |

Smoke details:

```json
{
  "moduleGroupCount": 2,
  "listSchemaHasRowClickTarget": true,
  "listSchemaColumnCount": 3,
  "listSchemaFilterCount": 3,
  "listSchemaSorterCount": 2,
  "listSchemaBatchActionCount": 1,
  "platformMessageTargetTypes": ["platform_auth", "system_switch"],
  "platformHasBusinessRecordTarget": false,
  "systemMessageCount": 2,
  "identityProviderCount": 1,
  "secretHasPlaintext": false,
  "noMemberFirstBusinessAccessAllowed": false,
  "opsHealthStatus": "WARN"
}
```

`opsHealthStatus=WARN` is accepted for G4 because the endpoint is reachable, returns a structured health result, and does not perform destructive actions.

## FE-010 Check

- Auth routes include `#/login`, `#/register-with-system`, and `#/forgot-password`.
- Register flow includes create-system result and next-step entry without making technical IDs the primary copy.
- Password reset includes request and confirm states with field-level feedback placement.
- Platform shell includes platform workbench, platform admin gated entry, create system, messages, profile, and system switch cards.
- System shell includes configured module groups/modules, todo/messages/profile entries, system switching, and permission-gated system backend entry.
- Unauthorized platform/system backend entries are hidden or disabled with `disabledReason` in the shell state.

Residual FE risk: G4 evidence did not include browser visual QA; this is not blocking for the requested G4 task-accept because the mandatory frontend build passed and QA-012 route evidence covers the shell/permission boundary. Full browser E2E remains for later QA batches after admin/runtime pages are implemented.

## Issues Found

No G4-blocking P0/P1 issues found.

Non-blocking observations:

- G3/G4 backend remains contract-first deterministic/sample implementation, not persistent database-backed behavior. This is already declared in `build-g4.md` and is acceptable for the current batch scope.
- Git cannot provide full tracked diff proof for backend/frontend generated or implementation files because large project directories are still untracked. This limits ancestry-level verification of "no hand-edited base generated file", but task outputs and scans show G4 work located in declared manage/feature paths.
- `git diff --check` reports only known LF/CRLF warnings on existing tracked files listed above.
- Additional no-index whitespace check on this newly added report file reported only an LF/CRLF warning for `docs/evidence/task-accept-g4.md`; no trailing whitespace or conflict-marker issue was found.

## Final Decision

G4/G4-QA task acceptance: PASS.

G5 unlock: allowed.
