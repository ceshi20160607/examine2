# Real System Recovery G0 Evidence

Time: 2026-06-24 16:30 Asia/Shanghai

## Scope

This batch fixes the backend persistence foundation after the leader audit found that the previous build was only contract-first/sample behavior.

## Changes

- `backend/pom.xml`
  - Added MyBatis-Plus version management.
  - Added inherited MyBatis-Plus extension and Spring service dependencies.

- `backend/examine-web/pom.xml`
  - Added MyBatis-Plus Spring Boot 3 starter.
  - Added MySQL runtime driver.

- `backend/examine-web/src/main/java/com/unique/examine/web/ExamineWebApplication.java`
  - Added generated mapper scanning for `com.unique.examine.**.base.mapper`.

- `backend/examine-web/src/main/resources/application.yml`
  - Added default runtime config, datasource config, MyBatis mapper locations, logging, upload, CORS, async, and AI Agent settings.

- `deploy/templates/backend/application.yml`
  - Added external datasource and MyBatis settings.
  - Changed sample mode default to `false`.

- `backend/examine-generator/src/main/java/com/unique/examine/generator/output/GeneratedBaseWriter.java`
  - Generated entities now use `@TableName` and `@TableId`.
  - Generated mappers now extend `BaseMapper<T>`.
  - Generated base services now extend `IService<T>`.
  - Generated service implementations now extend `ServiceImpl<Mapper, Entity>` and are Spring services.
  - Generated save helper is named `saveEntity` to avoid conflicting with MyBatis-Plus `save(T)`.

## Generation

Command:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -cp "backend\examine-generator\target\classes;backend\examine-core\target\classes" `
  com.unique.examine.generator.cli.GeneratorCli `
  --execute --sql sql\init.sql --backend-root backend
```

Output:

```text
tables=92
examine-ai-work=16
examine-app=2
examine-core=14
examine-flow=8
examine-message-log=8
examine-module=20
examine-plat=19
examine-upload=5
```

## Verification

JDK/Maven note:

- The project guide path `D:\Tools\JDK\JDK-21.0.6+7` does not exist on this machine.
- This machine's usable JDK 21 is `D:\java\jdk\jdk21`.
- Maven is `D:\java\apache-maven-3.8.5\bin\mvn.cmd`.

Compile command:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend\pom.xml -pl examine-web -am -DskipTests compile
```

Result:

```text
BUILD SUCCESS
Compiled modules: examine-core, examine-plat, examine-module, examine-flow,
examine-message-log, examine-upload, examine-app, examine-ai-work, examine-web.
```

Package command:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
powershell -ExecutionPolicy Bypass -File .\scripts\package-release.ps1 `
  -Version '0.0.1-SNAPSHOT' `
  -MavenPath 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' `
  -NpmPath 'D:\java\nodejs\npm.cmd'
```

Package result:

```text
status=PASS
releaseDir=release/unexamine-0.0.1-SNAPSHOT
zipPath=release/unexamine-0.0.1-SNAPSHOT.zip
backend/examine-web.jar=27195563 bytes
backend/application.yml exposes datasource and mybatis-plus settings
frontend production build passed
```

Database connectivity pre-check:

```text
127.0.0.1:3306       false
192.168.0.211:3306   true
```

## Remaining Risk

This is not yet the full real system. It only changes the generated persistence base from placeholders to MyBatis-Plus-capable classes.

The manage services still contain many `sample*` methods and the frontend still contains mocks. These are reopened in `docs/tasks/real-system-replan.md`.
