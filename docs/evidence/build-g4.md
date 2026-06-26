# G4 Build Evidence

## Verdict

PASS self-check. G4 backend slices, FE-010 shell, BE-014 smoke, and G4-QA evidence are complete enough for independent task-accept review.

## Implemented Tasks

- `TASK-BE-014`: platform integration smoke evidence.
- `TASK-BE-020`: module configuration APIs.
- `TASK-BE-032`: platform/system message, notification template, and delivery log APIs.
- `TASK-BE-034`: SSO policy, SecretRef, Secret rotation job, and no-member request APIs.
- `TASK-BE-038`: ops governance APIs.
- `TASK-FE-010`: auth pages, platform shell, and system shell entries.
- `TASK-QA-012`: permission matrix and role route check evidence.
- `TASK-QA-015`: G3 clean-build evidence remains `docs/evidence/build-g3.md`; no G3 content blockers.

## Aggregation Fixes

- `backend/examine-web/pom.xml` now depends on `examine-module` so module config controllers are available from the web entry.
- `examine-core` and `examine-module` use `spring-boot-starter-web` where controllers are declared.

## Commands

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -DskipTests compile
mvn -f backend/pom.xml -pl examine-web -am -DskipTests package
& 'D:\java\nodejs\npm.cmd' --prefix frontend run build
```

All commands passed.

## G4 Startup Smoke

Temporary server:

```powershell
java -jar backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar --server.port=18082
```

Result:

```json
{
  "health": "SUCCESS",
  "moduleGroups": "SUCCESS",
  "listSchema": "SUCCESS",
  "platformMessages": "SUCCESS",
  "systemMessages": "SUCCESS",
  "templates": "SUCCESS",
  "identityProviders": "SUCCESS",
  "ssoPolicy": "SUCCESS",
  "secret": "SUCCESS",
  "noMember": "SUCCESS",
  "opsHealth": "SUCCESS",
  "featureFlags": "SUCCESS",
  "moduleGroupCount": 2,
  "platformMessageCount": 0,
  "secretHasPlaintext": false
}
```

Server logs:

- `docs/evidence/build-g4-server.out.log`
- `docs/evidence/build-g4-server.err.log`

## Supporting Evidence

- `docs/evidence/backend-plat-smoke.md`
- `docs/evidence/build-g4-be020.md`
- `docs/evidence/build-g4-be032.md`
- `docs/evidence/build-g4-be034.md`
- `docs/evidence/build-g4-be038.md`
- `docs/evidence/build-g4-fe010.md`
- `docs/evidence/permission-matrix.md`
- `docs/evidence/role-route-check.md`

## Residual Risks

- Most G3/G4 services are contract-first deterministic responses, not persistent database-backed implementations.
- Runtime records, import/export execution, attachments, workflow runtime, OpenAPI apps, work management, AI Agent runtime, and full admin views are owned by later batches.
- `git diff --check` should still be run after this evidence file; known repository LF/CRLF warnings may appear on previously modified tracked files.
