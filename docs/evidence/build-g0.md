# G0 Build Self-Check Evidence

## Batch

- Batch: G0
- Date: 2026-06-23
- Status: self-check pass, pending `task-accept` and build batch acceptance

## Tasks Covered

- `TASK-DBA-001` to `TASK-DBA-009`
- `TASK-BE-001`
- `TASK-FE-001`
- `TASK-QA-001`
- `TASK-QA-005`
- `TASK-QA-010`

## Outputs

- SQL fragments: `sql/fragments/001-platform-identity.sql` to `sql/fragments/009-async-ops.sql`
- Database docs: `docs/database/*.md`
- Backend scaffold: `backend/pom.xml`, `backend/examine-core/**`, `backend/examine-web/**`
- Frontend scaffold: `frontend/package.json`, `frontend/vite.config.ts`, `frontend/src/app/**`, `frontend/src/shared/**`, `frontend/src/api/client.ts`, `frontend/src/mocks/**`
- QA docs: `docs/testing/test-strategy.md`, `docs/testing/evidence-conventions.md`, `docs/testing/fixtures.md`, `docs/testing/contract-fixtures.md`, `docs/testing/static-acceptance-checks.md`, `docs/evidence/static-check-template.md`

## Environment Note

`AGENTS.md` records `D:\Tools\...` paths, but this machine currently has:

- Java: `D:\java\jdk\jdk21`, version 21.0.10
- Maven: `D:\java\apache-maven-3.8.5\bin\mvn.cmd`
- Node/npm: `D:\java\nodejs\node.exe`, `D:\java\nodejs\npm.cmd`

Validation used the actual available paths without changing global environment variables.

## Commands

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

Result: pass.

```powershell
& 'D:\java\nodejs\npm.cmd' --prefix frontend install
& 'D:\java\nodejs\npm.cmd' --prefix frontend audit fix --force
& 'D:\java\nodejs\npm.cmd' --prefix frontend run build
& 'D:\java\nodejs\npm.cmd' --prefix frontend audit --audit-level=moderate
```

Result: build pass, audit 0 vulnerabilities. Vite was upgraded to 8.0.16 by audit fix.

```powershell
$bad=@()
Get-ChildItem sql/fragments/*.sql | ForEach-Object {
  $path=$_.FullName
  $lineNo=0
  Get-Content $_ | ForEach-Object {
    $lineNo++
    $count=($_.ToCharArray() | Where-Object { $_ -eq "'" }).Count
    if (($count % 2) -eq 1) { $bad += "$path`:$lineNo`:$($_)" }
  }
}
if ($bad.Count -gt 0) { $bad; exit 1 } else { 'sql quote pairs look balanced' }
```

Result: pass. SQL fragments currently define 87 tables.

```powershell
git diff --check
```

Result: pass with LF/CRLF warnings only on pre-existing tracked files:

- `.cursor/session/issues/registry.jsonl`
- `.cursor/session/state.json`
- `docs/design/pre-coding-readiness.md`
- `docs/design/user-approval.md`

## Residual Risk

- G0 is not accepted yet. `task-accept` must review the outputs before `TASK-DBA-010` and `TASK-BE-002` are treated as unlocked.
- SQL comments were normalized to ASCII `COMMENT 'field'/'table'` because prior generated Chinese comments had broken quote pairing. Business meaning is documented in `docs/database/*.md`.

