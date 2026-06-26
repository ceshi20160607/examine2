# Task Accept G3 Evidence

## Verdict

PASS

`TASK-BE-010`、`TASK-BE-011`、`TASK-BE-012`、`TASK-BE-013`、`TASK-BE-033` 独立验收通过。允许 G4 解锁。

## Read Inputs

本轮只读落盘文件和当前代码，不依赖父会话聊天上下文；未修改实现代码。

- `.cursor/README.md`
- `.cursor/session/state.json`
- `.cursor/knowledge/agent-operating-rules.md`
- `.cursor/knowledge/project-operating-rules.md`
- `.cursor/knowledge/failure-lessons.md`
- `docs/tasks/TASK-BE-010.md`
- `docs/tasks/TASK-BE-011.md`
- `docs/tasks/TASK-BE-012.md`
- `docs/tasks/TASK-BE-013.md`
- `docs/tasks/TASK-BE-033.md`
- `docs/evidence/build-g3.md`
- `docs/api/api.md`

## Scope And Output Paths

PASS

G3 代码输出覆盖五个任务声明的 manage 路径：

- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/auth/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/account/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/system/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/context/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/tenant/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/org/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/member/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/role/**`
- `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/permission/**`
- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/audit/**`
- `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/task/**`

Supporting aggregation changes are present and consistent with G3 runtime needs:

- `backend/examine-plat/pom.xml` includes `spring-boot-starter-web`.
- `backend/examine-message-log/pom.xml` includes `spring-boot-starter-web`.
- `backend/examine-web/pom.xml` depends on `examine-plat` and `examine-message-log`.
- `ExamineWebApplication` scans `com.unique.examine`.

## Base Generated Files

PASS

未发现 G3 手改 base 生成区的证据。

- `backend/*/src/main/**/base/**` 源文件数：460。
- base 源文件最新时间：`2026-06-23 21:45:02`。
- G3 manage 源文件数：33。
- manage 源文件时间范围：`2026-06-23 22:02:07` 到 `2026-06-23 22:25:43`。
- 抽样 base Java/XML 文件均包含生成标记：`Generated from sql/init.sql by examine-generator. Do not hand edit.`

限制说明：当前 `backend/` 整体仍是未跟踪目录，不能仅靠 `git diff --name-only` 精确区分 G2 生成和 G3 新增；以上结论结合 G2/G3 evidence、源码路径、生成标记和文件时间线判断。

## API Coverage

PASS

静态扫描确认 G3 controller 覆盖冻结 API 合同中本批任务要求的端点组：

- Auth/account: login、logout、token refresh、register-with-system、password reset request/confirm、account profile、profile update、password update、login logs。
- Platform systems: list、create、detail、update、enable、disable、delete、restore、platform health。
- Context/tenant/org/member: system switch options、system switch、tenant switch、tenant CRUD、department tree/create、member list/create/update/bind-account、member bindings。
- Role/permission: platform/system role CRUD、assign members、role permissions get/save、effective permission、permission preview。
- Audit/task: platform/system log search/detail、task search/detail/cancel/retry。

Acceptance spot checks:

- Login response includes token、profile、default landing、SSO binding summary、requestId、traceId。
- Register-with-system response includes account/system/member/super-admin bootstrap result.
- `SystemSwitchContext` includes `accountMemberBindingId`、`tenantId`、`systemMemberId`、roles、dataScope、permission snapshot summary。
- `TenantSwitchContext` includes tenant data scope and refreshed permission snapshot summary。
- `EffectivePermissionSnapshot` includes permission version、source roles、deny policies、field/action/dataScope decisions and explain data。
- Permission preview and write-like actions include trace/audit metadata。
- Async task responses include status/progress/resultFile/errorFile/retryable/cancelable fields。

Observation: `GET /api/v1/context/current-system` exists in code but was not found in the frozen G3 endpoint list. It does not replace a required endpoint and is treated as non-blocking for this batch, but should be either documented or removed before final API hardening.

## Commands And Results

### Maven Compile

Command:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -DskipTests compile
```

Result: PASS.

Reactor result: all 11 modules succeeded: `examine`, `examine-core`, `examine-plat`, `examine-module`, `examine-flow`, `examine-message-log`, `examine-upload`, `examine-app`, `examine-ai-work`, `examine-generator`, `examine-web`.

### Web Package

Command:

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-web -am -DskipTests package
```

Result: PASS.

Reactor result: `examine`, `examine-core`, `examine-plat`, `examine-message-log`, `examine-web` succeeded. Spring Boot repackage produced `backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar`.

Note: the requested smoke path `backend/examine-web/target/examine-web-0.1.0-SNAPSHOT.jar` does not exist in the current Maven version. The current parent POM version is `0.0.1-SNAPSHOT`, so smoke used the actual packaged executable jar.

### Startup Smoke

Command shape:

```powershell
java -jar backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar --server.port=61317
```

Smoke requests:

- `GET /api/v1/health`
- `POST /api/v1/auth/login`
- `POST /api/v1/platform/system-switch`
- `POST /api/v1/platform/logs/search?pageNo=1&pageSize=20`
- `GET /api/v1/tasks/TASK-20260623-001`

Result: PASS.

Response summary:

```json
{
  "requestedJarExists": false,
  "health": "SUCCESS",
  "login": "SUCCESS",
  "loginTraceId": "trc_db957db2-ea2f-4515-97c9-d9df88f0bf79",
  "systemSwitch": "SUCCESS",
  "switchSystemMemberId": "member_001",
  "platformLogs": "SUCCESS",
  "logsTotal": 1,
  "taskDetail": "SUCCESS",
  "taskStatus": "SUCCESS"
}
```

The Java process started for smoke was stopped in the script `finally` block. A follow-up `Get-NetTCPConnection -LocalPort 61317 -State Listen` returned no listener.

### Diff Check

Command:

```powershell
git diff --check
```

Result: PASS with LF/CRLF warnings only.

Warnings:

- `.cursor/session/issues/registry.jsonl`
- `.cursor/session/state.json`
- `docs/design/pre-coding-readiness.md`
- `docs/design/user-approval.md`

No whitespace error was reported.

## Findings

No blocking issue found.

Non-blocking observations:

- The packaged jar version is `0.0.1-SNAPSHOT`, while the smoke instruction named `0.1.0-SNAPSHOT`. Current POM and package output are internally consistent.
- `GET /api/v1/context/current-system` is an extra endpoint compared with the frozen G3 endpoint list; document or remove it before final API hardening.
- G3 remains contract-first and deterministic in-memory/demo behavior. Persistence, deep validation, and real authorization decisions still need later integration slices.
- G3 BO/VO records do not yet carry Swagger `@Schema`; `docs/evidence/build-g3.md` records this as blocked by missing Swagger annotation dependency and should be closed when API doc dependencies are formalized.

## G4 Unlock

Allowed. G3 task acceptance is PASS and no blocker prevents G4/G4-QA implementation from starting.
