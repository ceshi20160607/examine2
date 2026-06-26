# G1 Task Acceptance Evidence

## Verdict

PASS

Accepted tasks:

- `TASK-DBA-010`
- `TASK-BE-002`

## Scope

Checked outputs declared by `docs/tasks/plan.md`, `docs/tasks/TASK-DBA-010.md`, and `docs/tasks/TASK-BE-002.md`.

## Output Presence

PASS:

- `sql/init.sql`
- `docs/database/init-sql-check.md`
- `backend/pom.xml`
- `backend/examine-generator/**`
- `backend/examine-plat/pom.xml`
- `backend/examine-module/pom.xml`
- `backend/examine-flow/pom.xml`
- `backend/examine-message-log/pom.xml`
- `backend/examine-upload/pom.xml`
- `backend/examine-app/pom.xml`
- `backend/examine-ai-work/pom.xml`
- `docs/evidence/build-g1.md`

No obvious missing output or naming mismatch was found against the G1 plan.

## SQL Baseline Check

Command summary:

```powershell
$markers = Select-String -Path sql/init.sql -Pattern '^-- Source: sql/fragments/'
$tables = Select-String -Path sql/init.sql -Pattern '^CREATE TABLE IF NOT EXISTS\s+([a-zA-Z0-9_]+)' |
  ForEach-Object { if ($_.Line -match '^CREATE TABLE IF NOT EXISTS\s+([a-zA-Z0-9_]+)') { $matches[1] } }
$dupes = $tables | Group-Object | Where-Object Count -gt 1
```

Result:

- Fragment source markers: 9.
- Source fragments covered once each from `001-platform-identity.sql` through `009-async-ops.sql`.
- Table count: 87.
- Duplicate table names: 0.
- Secret/plaintext scan only found documented false positives and secret reference identifiers, consistent with `docs/database/init-sql-check.md`.

## Backend Compile

Initial environment check:

- Project guide path `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd` is not present on this machine.
- Available Maven from task self-check path: `D:\java\apache-maven-3.8.5\bin\mvn.cmd`.
- Java version used by the compile command: `21.0.10`.

Command:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

Result: PASS.

Maven reactor modules reported SUCCESS:

- `examine`
- `examine-core`
- `examine-plat`
- `examine-module`
- `examine-flow`
- `examine-message-log`
- `examine-upload`
- `examine-app`
- `examine-ai-work`
- `examine-generator`
- `examine-web`

## Generator Mapping

Command:

```powershell
java -cp backend/examine-generator/target/classes com.unique.examine.generator.cli.GeneratorCli
```

Result: PASS.

Observed mappings:

- `un_plat_` -> `examine-plat`
- `un_module_` -> `examine-module`
- `un_flow_` -> `examine-flow`
- `un_message_` -> `examine-message-log`
- `un_audit_` -> `examine-message-log`
- `un_upload_` -> `examine-upload`
- `un_openapi_` -> `examine-app`
- `un_agent_` -> `examine-ai-work`
- `un_work_` -> `examine-ai-work`
- `un_sys_` -> `examine-core`
- `un_ops_` -> `examine-core`

## Diff Check

Command:

```powershell
git diff --check
```

Result: PASS with LF/CRLF warnings only:

- `.cursor/session/issues/registry.jsonl`
- `.cursor/session/state.json`
- `docs/design/pre-coding-readiness.md`
- `docs/design/user-approval.md`

No whitespace error was reported for the G1 output files.

## Blockers And Risks

No G1 acceptance blocker found.

Follow-up risk:

- The documented default tool path in the project guide points to `D:\Tools\...`, but this machine used `D:\java\...` for the successful Maven compile. This is an environment consistency note, not a code blocker.
