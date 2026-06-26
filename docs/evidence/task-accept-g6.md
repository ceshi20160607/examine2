# G6 Task Accept Evidence

verdict: PASS

## 检查范围

- 按 conductor 指定顺序读取 `.cursor/README.md`、`.cursor/session/state.json`、`.cursor/knowledge/agent-operating-rules.md`、`.cursor/knowledge/project-operating-rules.md`、`.cursor/knowledge/failure-lessons.md`。
- 读取并对照 `docs/tasks/TASK-BE-022.md`、`docs/tasks/TASK-BE-031.md`、`docs/tasks/TASK-QA-016.md` 的 outputs、self-checks、acceptance criteria。
- 读取 G6 证据：`docs/evidence/build-g6.md`、`docs/evidence/build-g6-be022.md`、`docs/evidence/build-g6-be031.md`、`docs/evidence/build-g6-smoke.json`、`docs/evidence/build-g6-server.out.log`、`docs/evidence/build-g6-server.err.log`。
- 检查代码路径：
  - `backend/examine-module/src/main/java/com/unique/examine/module/manage/importexport/**`
  - `backend/examine-module/src/main/java/com/unique/examine/module/manage/draft/**`
  - `backend/examine-module/src/main/java/com/unique/examine/module/manage/attachment/**`
  - `backend/examine-module/src/main/java/com/unique/examine/module/manage/sequence/**`
  - `backend/examine-upload/src/main/java/com/unique/examine/upload/manage/**`
  - `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/runtime/**`
  - `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/approval/**`
  - `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/todo/**`
  - `backend/examine-upload/pom.xml`
  - `backend/examine-web/pom.xml`

## 关键证据

- `TASK-BE-022` declared outputs 已存在。`ImportExportModels.AsyncTask` 包含 `taskId/bizType/idempotencyKey/status/progress/retryable/cancelable/resultFile/errorFile/failureReason/partialSuccessCount/partialFailureCount/rollbackSupported/traceId/auditLogId/createdBy/createdAt`；`ImportExportService` 的导入预检、导入确认、导出均构造 result file 与 error file。导入确认返回 `ImportConfirmResult.task`，导出返回 `ExportResult.task`。
- `docs/evidence/build-g6-smoke.json` 覆盖导入导出文件引用：`importPrecheckHasResultFile=true`、`importPrecheckHasErrorFile=true`、`importConfirmTaskStatus=RUNNING`、`exportHasResultFile=true`。
- 草稿与附件 outputs 已存在。`DraftService` 返回字段快照、子表、附件、校验问题、权限快照与审计追踪；`AttachmentService` 返回绑定附件列表、样例 preview/download URL、权限模式、retryable 与审计追踪。
- upload manage 已存在并被 web 聚合。`backend/examine-upload/pom.xml` 包含 `spring-boot-starter-web`；`backend/examine-web/pom.xml` 包含 `examine-upload` 依赖。`UploadManageService` 使用 `LOCAL_SAMPLE`，`objectStorageConnected=false`，并提供文件详情、存储策略和访问结果；生产对象存储策略返回 `productionObjectStorageEnabled=false` 和禁用说明，符合“不接生产对象存储”边界。
- sequence allocation 体现原子分配。`SequenceService` 使用 `ConcurrentHashMap.computeIfAbsent` 与 `AtomicLong.getAndAdd(count)` 一次领取号段，避免“查询最大值 + 1”。smoke 中 `sequenceFirst=["VH-0001","VH-0002","VH-0003"]`、`sequenceSecond=["VH-0004","VH-0005"]`、`sequenceAtomic=true`。
- `TASK-BE-031` declared outputs 已存在。`WorkflowRuntimeService.snapshot` 返回实例、节点、当前审批任务、历史、业务目标、权限快照、trace、audit，满足 workflow instance snapshot 完整性要求。
- approval action 幂等逻辑满足验收重点。`ApprovalService` 以 `taskId:action:idempotencyKey` 作为 key，先查已存结果，再 `putIfAbsent`；重复请求返回 `duplicate=true`、`workflowAdvanced=false` 的视图，不产生第二次推进。smoke 中 `approvalIdempotent=true`，且 approve first/second 均成功。
- reject/transfer 处理已覆盖。`ApprovalService` 对 reject 返回拒绝状态和原因，对 transfer 返回转交目标与下一任务；smoke 中 `reject=SUCCESS`、`rejectReason="missing fields"`、`transfer=SUCCESS`、`transferTarget="system_member_backup"`。
- todo 独立于业务模块侧栏并区分平台/系统。`TodoController` 分别提供 `/api/v1/platform/todos/search`、`/api/v1/systems/{systemId}/todos/search` 和系统待办动作接口；`TodoService` 返回 `TodoSearchResult.scope`、`typeTree` 与分页 `page`，平台待办 target 为平台运维/密钥轮换，系统待办 target 为当前系统内业务对象。smoke 中 `platformTodoScope=platform`、`systemTodoScope=system`、`todoActionStatus=HANDLED`。
- G6 smoke 服务残留风险已复核。证据日志显示服务曾在 18086 启动，`build-g6-server.err.log` 为空；验收时检查 `Get-NetTCPConnection -LocalPort 18086` 返回无监听，证据 PID 14900 已不在运行。

## 本次只读验证命令

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -pl examine-web -am -DskipTests package
git diff --check -- backend/examine-module/src/main/java/com/unique/examine/module/manage/importexport backend/examine-module/src/main/java/com/unique/examine/module/manage/draft backend/examine-module/src/main/java/com/unique/examine/module/manage/attachment backend/examine-module/src/main/java/com/unique/examine/module/manage/sequence backend/examine-upload/src/main/java/com/unique/examine/upload/manage backend/examine-flow/src/main/java/com/unique/examine/flow/manage/runtime backend/examine-flow/src/main/java/com/unique/examine/flow/manage/approval backend/examine-flow/src/main/java/com/unique/examine/flow/manage/todo backend/examine-upload/pom.xml backend/examine-web/pom.xml docs/evidence/build-g6.md docs/evidence/build-g6-be022.md docs/evidence/build-g6-be031.md docs/evidence/build-g6-smoke.json docs/evidence/build-g6-server.out.log docs/evidence/build-g6-server.err.log
```

结果：

- `backend/pom.xml -DskipTests compile` 通过，11 个 backend reactor 模块 `SUCCESS`。
- `examine-web -am -DskipTests package` 通过，包含 `examine-upload` 的 web 聚合 package 成功。
- `git diff --check` 对 G6 相关代码和证据范围无输出。
- 初次尝试使用项目规则中的 `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd` 失败，因为该路径在本机不存在；随后定位到实际可用的 `D:\java\apache-maven-3.8.5\bin\mvn.cmd` 和 `D:\java\jdk\jdk21` 后完成验证。默认 Maven 仍指向 JDK8，因此验证命令临时设置了 JDK21。

## 结论

G6 满足 `TASK-BE-022`、`TASK-BE-031`、`TASK-QA-016` 的 declared outputs、self-checks 和 acceptance criteria。未发现阻塞项。

## 剩余风险

- 当前 G6 为 contract-first/sample-data 实现；异步任务持久化、真实文件存储、数据库序号行锁、流程引擎推进和待办持久化仍是后续集成风险，但不阻塞本批任务验收。
- 上传样例访问以详情、策略和 access-result 形式承接；未启用生产对象存储，符合本任务 Do Not 范围。
