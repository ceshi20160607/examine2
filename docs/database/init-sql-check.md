# Initial SQL Baseline Check

## Scope

- Task: `TASK-DBA-010`
- Baseline: `sql/init.sql`
- Source fragments:
  - `sql/fragments/001-platform-identity.sql`
  - `sql/fragments/002-permission-rbac.sql`
  - `sql/fragments/003-module-config.sql`
  - `sql/fragments/004-dynamic-runtime.sql`
  - `sql/fragments/005-workflow-approval.sql`
  - `sql/fragments/006-message-todo-log.sql`
  - `sql/fragments/007-work-management.sql`
  - `sql/fragments/008-secret-openapi-agent.sql`
  - `sql/fragments/009-async-ops.sql`

## Merge Result

- Fragment source markers in `sql/init.sql`: 9.
- Physical tables in `sql/init.sql`: 92.
- Duplicate table names: 0.
- Source fragments are merged in dependency order, from platform identity through async ops.
- `SET FOREIGN_KEY_CHECKS = 0` wraps table creation and is restored to `1` at the end.

## Naming And Isolation

- Business tables use the frozen `un_` prefix.
- Tenant-scoped runtime tables include `system_id` and `tenant_id` lookup indexes.
- Soft-delete unique keys include `deleted` where duplicated historical names/codes are expected.
- Global unique keys are intentionally used for cross-tenant identifiers such as request numbers, task ids, snapshot ids, session ids, confirmation ids, provider codes, and secret reference versions.

## Secret Handling

- No plaintext secret columns are present.
- Secret-bearing config stores only reference ids such as `secret_ref_id`, `openapi_secret_ref_id`, and `model_credential_ref_id`.
- Upload storage policy also stores only `secret_ref_id`; object storage secrets are not in schema.
- The text scan for secret plaintext had two false positives:
  - `explain_payload` contains the substring `plain`.
  - `plain task` is a work-management comment phrase.

## Self Check Commands

```powershell
Test-Path sql/init.sql
Test-Path docs/database/init-sql-check.md

$tables = Select-String -Path sql/init.sql -Pattern '^CREATE TABLE IF NOT EXISTS\s+([a-zA-Z0-9_]+)' |
  ForEach-Object { if ($_.Line -match '^CREATE TABLE IF NOT EXISTS\s+([a-zA-Z0-9_]+)') { $matches[1] } }
$tables.Count
$tables | Group-Object | Where-Object Count -gt 1
```

## Verdict

Self-check passed for the merged initial SQL baseline. Runtime execution against MySQL is left to the database integration task because this task only owns the executable baseline file and static schema check.
