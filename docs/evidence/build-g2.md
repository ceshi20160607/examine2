# Build G2 Evidence

## Scope

- `TASK-BE-003`: core and platform base generation.
- `TASK-BE-004`: module runtime and upload base generation.
- `TASK-BE-005`: flow and message-log base generation.
- `TASK-BE-006`: app, Agent, and work base generation.

## Pre-generation Corrections

Two schema/task-boundary issues were found before generating base files:

- Upload module had a Maven module and generator mapping, but no `un_upload_` tables. Added upload storage metadata tables to `sql/fragments/004-dynamic-runtime.sql` and regenerated `sql/init.sql`.
- `un_system_sso_policy` did not match the module prefix registry. Renamed it to `un_plat_system_sso_policy` because system SSO policy belongs to the platform identity/SSO module.
- G2 task sheets were clarified to include mapper XML resource outputs because the task scope already required XML generation.

After correction:

- `sql/init.sql` table count: 92.
- Duplicate table names: 0.
- Tables with unresolved generator prefix: 0.

## Generator Command

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile

java -cp backend/examine-generator/target/classes com.unique.examine.generator.cli.GeneratorCli `
  --execute --sql sql/init.sql --backend-root backend
```

Generator output:

- `tables=92`
- `examine-core=14`
- `examine-plat=19`
- `examine-module=20`
- `examine-flow=8`
- `examine-message-log=8`
- `examine-upload=5`
- `examine-app=2`
- `examine-ai-work=16`

## Generated File Counts

Each table generates entity, mapper, service, service implementation, and mapper XML.

| Module | Java files | XML files |
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

## Spot Checks

- `backend/examine-upload/src/main/java/com/unique/examine/upload/base/entity/UploadFile.java` maps `un_upload_file`.
- `backend/examine-plat/src/main/java/com/unique/examine/plat/base/entity/PlatSystemSsoPolicy.java` maps `un_plat_system_sso_policy`.
- All generated files contain the auto-generated marker: `Generated from sql/init.sql by examine-generator. Do not hand edit.`

## Compile

Command:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

Result: PASS.

Maven compiled all 11 reactor modules. Generated base Java and XML resources were included in module compile/resource phases.

## Diff Check

Command:

```powershell
git diff --check
```

Result: PASS with LF/CRLF warnings only on tracked project/session/design files already noted in G0/G1 evidence.

## Verdict

G2 self-check passed and is ready for independent task acceptance.
