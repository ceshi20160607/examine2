# G2 Task Acceptance Evidence

> worker: independent G2 task-accept
> scope: `TASK-BE-003`, `TASK-BE-004`, `TASK-BE-005`, `TASK-BE-006`
> result: PASS
> checked_at: 2026-06-23T21:53:00+08:00

## Conclusion

G2 acceptance PASS.

- `TASK-BE-003` core/plat base Java and mapper XML outputs exist and compile.
- `TASK-BE-004` module/upload base Java and mapper XML outputs exist; `un_upload_*` maps to `examine-upload`.
- `TASK-BE-005` flow/message-log base Java and mapper XML outputs exist with separated ownership.
- `TASK-BE-006` app/ai-work base Java and mapper XML outputs exist; OpenAPI maps to `examine-app`, Agent/work maps to `examine-ai-work`.
- G2 precondition correction keeps the G1 SQL baseline consistent: `sql/init.sql` has 92 tables, no duplicate table names, and no unresolved generator prefixes.

## Inputs Read

- `.cursor/README.md`
- `.cursor/session/state.json`
- `.cursor/knowledge/agent-operating-rules.md`
- `.cursor/knowledge/project-operating-rules.md`
- `.cursor/knowledge/failure-lessons.md`
- `docs/tasks/plan.md`
- `docs/tasks/TASK-BE-003.md`
- `docs/tasks/TASK-BE-004.md`
- `docs/tasks/TASK-BE-005.md`
- `docs/tasks/TASK-BE-006.md`
- `docs/evidence/build-g2.md`

## Output Checks

Task outputs declared by `docs/tasks/plan.md` and the four G2 task files were checked.

| Area | Expected | Result |
|---|---|---|
| `TASK-BE-003` | `examine-core/base/**`, `examine-plat/base/**`, mapper XML | PASS |
| `TASK-BE-004` | `examine-module/base/**`, `examine-upload/base/**`, mapper XML | PASS |
| `TASK-BE-005` | `examine-flow/base/**`, `examine-message-log/base/**`, mapper XML | PASS |
| `TASK-BE-006` | `examine-app/base/**`, `examine-ai-work/base/**`, mapper XML | PASS |
| Generator parser/writer/CLI | SQL parser, deterministic base writer, CLI entry | PASS |
| G2 build evidence | `docs/evidence/build-g2.md` exists and records generator/compile/diff checks | PASS |

Generated source counts under `backend/*/src/main`:

| Module | Java | XML |
|---|---:|---:|
| `examine-core` | 56 | 14 |
| `examine-plat` | 76 | 19 |
| `examine-module` | 80 | 20 |
| `examine-flow` | 32 | 8 |
| `examine-message-log` | 32 | 8 |
| `examine-upload` | 20 | 5 |
| `examine-app` | 8 | 2 |
| `examine-ai-work` | 64 | 16 |
| Total | 368 | 92 |

All 460 generated source files under `src/main` contain the generator marker:

`Generated from sql/init.sql by examine-generator. Do not hand edit.`

Spot checks:

- `backend/examine-upload/src/main/java/com/unique/examine/upload/base/entity/UploadFile.java` exists, has the generated marker, and maps `un_upload_file`.
- `backend/examine-plat/src/main/java/com/unique/examine/plat/base/entity/PlatSystemSsoPolicy.java` exists, has the generated marker, and maps `un_plat_system_sso_policy`.
- `backend/examine-upload/src/main/resources/mapper/base/UploadFileMapper.xml` exists and has the generated marker.
- `backend/examine-plat/src/main/resources/mapper/base/PlatSystemSsoPolicyMapper.xml` exists and has the generated marker.

## SQL Baseline Check

Command summary:

```powershell
$sql = Get-Content -Raw sql/init.sql
# count CREATE TABLE names, duplicate names, unresolved prefixes, upload tables, and SSO policy table
```

Result:

- table count: 92
- duplicate table names: 0
- unresolved generator prefixes: 0
- upload tables present: `un_upload_file`, `un_upload_file_access_log`, `un_upload_file_recycle`, `un_upload_file_version`, `un_upload_storage_policy`
- SSO policy table present: `un_plat_system_sso_policy`

SQL table ownership by generator prefix:

| Module | Tables |
|---|---:|
| `examine-core` | 14 |
| `examine-plat` | 19 |
| `examine-module` | 20 |
| `examine-flow` | 8 |
| `examine-message-log` | 8 |
| `examine-upload` | 5 |
| `examine-app` | 2 |
| `examine-ai-work` | 16 |

## Generator Check

Checked files:

- `backend/examine-generator/src/main/java/com/unique/examine/generator/sql/SqlInitParser.java`
- `backend/examine-generator/src/main/java/com/unique/examine/generator/output/GeneratedBaseWriter.java`
- `backend/examine-generator/src/main/java/com/unique/examine/generator/cli/GeneratorCli.java`
- `backend/examine-generator/src/main/java/com/unique/examine/generator/GeneratorModuleRegistry.java`

Non-writing CLI mapping check:

```powershell
java -cp backend/examine-generator/target/classes com.unique.examine.generator.cli.GeneratorCli
```

Result: PASS. The CLI prints mappings for all registered prefixes:

- `un_plat_ -> examine-plat`
- `un_module_ -> examine-module`
- `un_flow_ -> examine-flow`
- `un_message_` and `un_audit_ -> examine-message-log`
- `un_upload_ -> examine-upload`
- `un_openapi_ -> examine-app`
- `un_agent_` and `un_work_ -> examine-ai-work`
- `un_sys_` and `un_ops_ -> examine-core`

The exact `--execute --sql sql/init.sql --backend-root backend` generation command was not rerun by this acceptance worker because the worker's only allowed write scope is `docs/evidence/task-accept-g2.md`; the command is an implementation-source writer. Instead, this acceptance复核ed `docs/evidence/build-g2.md`, the generator source, CLI mappings, SQL prefix distribution, generated source counts, generated markers, and Maven compile.

## Compile

Initial environment check:

- `D:\Tools\JDK\JDK-21.0.6+7\bin\java.exe`: absent
- `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd`: absent
- `D:\java\jdk\jdk21\bin\java.exe`: present
- `D:\java\apache-maven-3.8.5\bin\mvn.cmd`: present

Command run:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

Result: PASS.

Maven reactor modules compiled successfully:

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

## Diff Check

Command run:

```powershell
git diff --check
```

Result: PASS with line-ending warnings only.

Warnings:

- `.cursor/session/issues/registry.jsonl`: LF will be replaced by CRLF
- `.cursor/session/state.json`: LF will be replaced by CRLF
- `docs/design/pre-coding-readiness.md`: LF will be replaced by CRLF
- `docs/design/user-approval.md`: LF will be replaced by CRLF

No whitespace error was reported.

## Existing Worktree State

The worktree already contains broad build outputs and untracked project directories from G0/G1/G2 work, including `backend/`, `docs/api/`, `docs/database/`, `docs/evidence/`, `docs/tasks/`, `frontend/`, and `sql/`.

This task-accept worker only writes:

- `docs/evidence/task-accept-g2.md`

## Blockers And Risks

Blockers: none.

Follow-up risks:

- The generator execute command writes implementation source files; independent acceptance used复核 instead of rerunning it to honor the worker write boundary.
- Local environment paths differ from the project default documented in `AGENTS.md`; `D:\java\jdk\jdk21` and `D:\java\apache-maven-3.8.5` were used successfully.
