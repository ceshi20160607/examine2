# TASK-BE-032 G4 后端自检证据

## 实现概要

- Worker：G4 后端 worker
- 任务：TASK-BE-032 消息、通知模板、投递日志 APIs
- 范围：只新增 `backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/message/**`、`backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/notification/**` 和本证据文件。
- 实现方式：沿用 G3 contract-first 风格，返回确定性样例数据，先满足冻结 API 的响应结构、筛选项和边界规则。
- 未修改内容：未修改 POM、base 生成文件、`manage/audit/**`、`manage/task/**` 或其他模块。

## 关键实现

- 新增 `MessageTargetPolicy` 统一校验消息跳转目标：
  - `scope=platform` 只允许 `system_switch/platform_auth/platform_task/platform_log/agent_result/audit_log`。
  - `scope=system` 允许在系统成员上下文内跳 `business_record/approval_task/async_task/todo/work_item/daily_report/agent_result/audit_log`。
  - 平台消息直跳 `business_record` 等系统业务详情会抛出 `MESSAGE_TARGET_FORBIDDEN`。
- 平台消息样例只返回平台授权、平台任务、系统切换引导目标；涉及系统结果时通过 `system_switch` 和 `OPEN_SYSTEM_SWITCH` 表达。
- 系统消息样例返回 `business_record/approval_task/async_task`，并通过 `SYSTEM_MEMBER_CONTEXT_REQUIRED` 表达必须在 `SystemSwitchContext/systemMemberId` 下打开。
- 消息筛选覆盖 `systemId/tenantId/templateCode/type/readStatus/archiveStatus/timeRange/keyword` 请求字段。
- 通知模板 CRUD 和发布检查复用 `MessageTargetPolicy`，模板保存/更新会校验 targetRule，发布检查返回 failure/warning/impactRefs/traceId/auditLogId。
- 投递日志查询覆盖 `tenantId/templateCode/messageId/channel/status/archiveStatus/traceId/timeRange/keyword`。

## 端点列表

- `POST /api/v1/platform/messages/search`
- `POST /api/v1/platform/messages/load-more`
- `POST /api/v1/platform/messages/mark-read`
- `POST /api/v1/platform/messages/archive`
- `POST /api/v1/platform/messages/mark-all-read`
- `POST /api/v1/systems/{systemId}/messages/search`
- `POST /api/v1/systems/{systemId}/messages/load-more`
- `POST /api/v1/systems/{systemId}/messages/mark-read`
- `POST /api/v1/systems/{systemId}/messages/archive`
- `POST /api/v1/systems/{systemId}/messages/mark-all-read`
- `GET /api/v1/systems/{systemId}/notification-templates`
- `POST /api/v1/systems/{systemId}/notification-templates`
- `GET /api/v1/systems/{systemId}/notification-templates/{templateCode}`
- `PATCH /api/v1/systems/{systemId}/notification-templates/{templateCode}`
- `DELETE /api/v1/systems/{systemId}/notification-templates/{templateCode}`
- `POST /api/v1/systems/{systemId}/notification-templates/{templateCode}/publish-check`
- `GET /api/v1/systems/{systemId}/message-delivery-logs`

## 自检命令

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-message-log -am -DskipTests compile
```

结果：PASS。

关键输出：

```text
[INFO] examine ............................................ SUCCESS
[INFO] examine-core ....................................... SUCCESS
[INFO] examine-message-log ................................ SUCCESS
[INFO] BUILD SUCCESS
```

## diff 检查

命令：

```powershell
git diff --check
```

结果：PASS，无空白错误。仅输出工作区既有 tracked 文件的 LF/CRLF warning：

```text
.cursor/session/issues/registry.jsonl
docs/design/pre-coding-readiness.md
docs/design/user-approval.md
```

新增 Java 文件额外检查：

```powershell
$files = @(rg --files backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/message backend/examine-message-log/src/main/java/com/unique/examine/messagelog/manage/notification)
$hasIssue = $false
foreach ($file in $files) {
    $output = git diff --no-index --check -- NUL $file 2>&1 | Where-Object { $_ -notmatch 'LF will be replaced by CRLF' }
    if ($output) { $hasIssue = $true; $output }
}
if ($hasIssue) { exit 1 } else { exit 0 }
```

结果：PASS，无输出。

## 残余风险

- 当前为 contract-first 确定性样例响应，尚未接数据库持久化、真实消息状态更新、真实审计落库和补发任务。
- 系统成员上下文目前通过系统端点和 target fallback 表达，尚未接入真实认证/权限快照服务做运行时拦截。
- 冻结 API 只列出系统通知模板入口；本次在该入口内支持 system/platform scope 的 targetRule 校验，后续若需要平台模板独立后台入口，应由 PM 冻结新端点。
- 工作区中 `backend/`、`docs/api/`、`docs/evidence/` 等目录在本轮开始前已处于未跟踪状态，本轮仅在声明范围内新增文件。
