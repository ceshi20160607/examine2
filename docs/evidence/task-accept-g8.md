# G8 / TASK-BE-037 独立验收报告

Verdict: PASS

## 验收范围

- 任务：`TASK-BE-037`
- 批次：`G8`
- 本次实现范围：`backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/agent/**`
- 本 worker 只写入本报告：`docs/evidence/task-accept-g8.md`

## 已读取输入

- `.cursor/README.md`
- `.cursor/session/state.json`
- `.cursor/knowledge/agent-operating-rules.md`
- `.cursor/knowledge/project-operating-rules.md`
- `.cursor/knowledge/failure-lessons.md`
- `docs/tasks/TASK-BE-037.md`
- `docs/api/api.md` AI Agent 段落（第 12 节）及确认拆分约束
- `docs/evidence/build-g8.md`
- `docs/evidence/build-g8-be037.md`
- `docs/evidence/build-g8-smoke.json`

## 检查项

1. G8 是否只实现 TASK-BE-037 范围：PASS
   - `TASK-BE-037` 输出限定为 `backend/examine-ai-work/src/main/java/com/unique/examine/aiwork/manage/agent/**`。
   - 实现目录仅包含 `AgentController.java`、`AgentService.java`、`AgentModels.java` 三个文件，均为 AI Agent manage API 合同与样例实现。
   - scoped `git status` 显示 G8 新增项为 agent 实现目录与 G8 证据文件；本验收 worker 未修改其他实现文件。

2. 是否暴露 platform/system/work AI Agent endpoints：PASS
   - `AgentController.java` 暴露冻结 API 中的模型授权、平台会话/消息、平台确认、系统策略 CRUD/publish-check、系统会话/消息、系统写入确认 create/confirm/reject、工作草稿确认、Agent 审计日志查询。
   - 端点行号覆盖：`AgentController.java:55`、`:69`、`:84`、`:98`、`:111`、`:125`、`:140`、`:155`、`:172`、`:188`、`:201`、`:216`、`:232`、`:249`、`:267`、`:284`、`:301`。

3. 三个确认合同是否独立：PASS
   - 平台确认使用 `PlatformAgentConfirmRequest/PlatformAgentConfirmResultVO` 和 `PLATFORM_AGENT_CONFIRM`。
   - 系统写入确认使用 `SystemWriteConfirmationRequest/SystemWriteConfirmationVO` 和 `SYSTEM_AGENT_WRITE_CONFIRM`，并拆分 create、confirm、reject。
   - 工作草稿确认使用 `WorkDraftConfirmRequest/WorkDraftConfirmResultVO` 和 `WORK_AGENT_DRAFT_CONFIRM`。
   - 未发现共用泛确认 endpoint。

4. 平台 Agent 是否不能打开或写入系统业务数据，且有明确拒绝结果：PASS
   - `AgentService.java:360-365` 平台边界允许 `PLATFORM_AUTHORIZATION/PLATFORM_TASK/PLATFORM_LOG/MODEL_QUOTA/SYSTEM_HEALTH/SYSTEM_SWITCH_GUIDE`，拒绝 `SYSTEM_BUSINESS_RECORD/SYSTEM_MODULE_WRITE/SYSTEM_APPROVAL_WRITE`。
   - `AgentService.java:202-223` 平台确认遇到非 platform scope 返回 `REJECTED_BY_SCOPE`、`allowed=false`、`RejectedItemVO("SYSTEM_BUSINESS_DATA", disabledReason)`，拒绝说明为“平台 Agent 不能打开或写入系统业务数据，请先完成系统切换。”
   - smoke 摘要中 `platformBoundaryDenied` 包含三类系统业务禁止目标，`platformConfirmRejectedAllowed=false`。

5. 是否没有绕过人工确认：PASS
   - 平台消息推荐确认为 `WAITING_HUMAN_CONFIRM`，且 `manualConfirmRequired=true`。
   - 系统消息推荐 `SYSTEM_AGENT_WRITE_CONFIRM` 与 `WORK_AGENT_DRAFT_CONFIRM`，均为 `WAITING_HUMAN_CONFIRM`。
   - `AgentService.java:234-248` 系统写入创建固定为 `WAITING_HUMAN_CONFIRM`，忽略请求中的直接写入意图。
   - `AgentController.java:249` 与 `:267` 将系统写入确认和拒绝拆分为独立 endpoint。
   - `AgentService.java:301-316` 工作草稿确认返回 `manualConfirmRequired=true`。
   - smoke 摘要中 `systemWriteCreateStatus=WAITING_HUMAN_CONFIRM`、`systemWriteConfirmStatus=CONFIRMED`、`systemWriteRejectStatus=REJECTED`、`workDraftManualConfirm=true`。

6. Audit logs 是否包含 model、prompt、policy、permission、desensitize、trace data：PASS
   - `AgentModels.java:533-540` 的 `AgentAuditLogVO` 包含 `modelVersion`、`promptVersion`、`policyVersion`、`permissionSnapshotId`、`conversationSnapshot`、`toolCallSnapshot`、`desensitizeResult`、`outboundSnapshot`、`traceId`、`auditLogId`。
   - `AgentService.java:384-394` 实际构造审计日志时写入模型版本、提示词版本、策略版本、权限快照、原始对话、工具调用、脱敏结果、外发快照、traceId 和 auditLogId。
   - smoke 摘要中 `auditModelVersion`、`auditPromptVersion`、`auditPolicyVersion`、`auditPermissionSnapshot`、`auditDesensitizeMode`、`auditTraceId` 均有值。

7. 编译、web package、startup smoke、git diff --check 证据是否足够支持通过：PASS
   - `docs/evidence/build-g8.md` 与 `docs/evidence/build-g8-be037.md` 记录 backend reactor compile PASS、web package PASS、startup smoke PASS。
   - `docs/evidence/build-g8-smoke.json` 覆盖模型授权、策略 CRUD/publish-check、platform/system sessions/messages、三类确认合同、Agent audit logs。
   - `docs/evidence/build-g8-server.out.log` 显示 Spring Boot 以 Java 21 在 `18088` 启动成功；`build-g8-server.err.log` 仅有本机 Java agent 动态加载 warning，未见启动失败。
   - 本验收重跑 `git diff --check`，结果仅有 `.cursor/session/issues/registry.jsonl`、`.cursor/session/state.json`、`docs/design/pre-coding-readiness.md`、`docs/design/user-approval.md` 的已知 LF/CRLF warning。
   - 未重跑 Maven compile/package，原因是本 worker 被限制只写入验收报告，Maven 会产生或刷新构建产物；当前落盘 build 与 smoke 证据足够支持本任务验收。

## 风险/备注

- 未发现阻断问题。
- 当前实现是 contract-first/sample manage API，未在本任务范围内要求数据库持久化或真实模型调用；验收按 `TASK-BE-037` 与冻结 API 首期范围判定。
- 工作区已有未跟踪的 G8 实现与证据文件由实现 worker 产生；本验收 worker 仅新增本报告。
