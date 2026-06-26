# Build G1 Evidence

## Scope

- `TASK-DBA-010`: merge SQL fragments into `sql/init.sql`.
- `TASK-BE-002`: create backend business module POMs and generator scaffold.

## Changed Outputs

- `sql/init.sql`
- `docs/database/init-sql-check.md`
- `backend/pom.xml`
- `backend/examine-plat/pom.xml`
- `backend/examine-module/pom.xml`
- `backend/examine-flow/pom.xml`
- `backend/examine-message-log/pom.xml`
- `backend/examine-upload/pom.xml`
- `backend/examine-app/pom.xml`
- `backend/examine-ai-work/pom.xml`
- `backend/examine-generator/**`

## SQL Baseline Check

Command summary:

```powershell
Test-Path sql/init.sql
Test-Path docs/database/init-sql-check.md

$tables = Select-String -Path sql/init.sql -Pattern '^CREATE TABLE IF NOT EXISTS\s+([a-zA-Z0-9_]+)' |
  ForEach-Object { if ($_.Line -match '^CREATE TABLE IF NOT EXISTS\s+([a-zA-Z0-9_]+)') { $matches[1] } }
$tables.Count
$tables | Group-Object | Where-Object Count -gt 1
```

Result:

- `sql/init.sql`: exists.
- `docs/database/init-sql-check.md`: exists.
- Fragment source markers: 9.
- Tables: 92.
- Duplicate table names: 0.
- Secret plaintext scan had only false positives documented in `docs/database/init-sql-check.md`; upload storage policy stores `secret_ref_id` only.

## Backend Compile

Command:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

Result: PASS.

Reactor modules:

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

Result:

- `un_plat_` -> `examine-plat`
- `un_module_` -> `examine-module`
- `un_flow_` -> `examine-flow`
- `un_message_`, `un_audit_` -> `examine-message-log`
- `un_upload_` -> `examine-upload`
- `un_openapi_` -> `examine-app`
- `un_agent_`, `un_work_` -> `examine-ai-work`
- `un_sys_`, `un_ops_` -> `examine-core`

## Diff Check

Command:

```powershell
git diff --check
```

Result: PASS with LF/CRLF warnings only on tracked files already noted in G0 evidence.

## Verdict

G1 self-check passed and is ready for independent task acceptance.
