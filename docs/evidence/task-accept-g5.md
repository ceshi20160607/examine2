# G5 Task Accept Evidence

verdict: PASS

## 检查范围

- 规范与状态：`.cursor/README.md`、`.cursor/session/state.json`、`.cursor/knowledge/agent-operating-rules.md`、`.cursor/knowledge/project-operating-rules.md`、`.cursor/knowledge/failure-lessons.md`
- 任务：`docs/tasks/TASK-BE-021.md`、`docs/tasks/TASK-BE-030.md`、`docs/tasks/TASK-BE-035.md`、`docs/tasks/TASK-FE-020.md`
- G5 证据：`docs/evidence/build-g5.md`、`docs/evidence/build-g5-be021.md`、`docs/evidence/build-g5-be030.md`、`docs/evidence/build-g5-be035.md`、`docs/evidence/build-g5-fe020.md`
- 合同与设计对照：`docs/api/api.md`、`frontend/docs/api-contract-map.md`、`docs/design/prototypes/index.html`
- 代码路径：
  - `backend/examine-module/src/main/java/com/unique/examine/module/manage/runtime/**`
  - `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/definition/**`
  - `backend/examine-app/src/main/java/com/unique/examine/app/manage/openapi/**`
  - `frontend/src/app/app.ts`
  - `frontend/src/features/platform/platformShell.ts`
  - `frontend/src/features/system-shell/systemShell.ts`
  - `frontend/src/features/platform-admin/**`
  - `frontend/src/features/system-admin/**`
  - `frontend/src/styles.css`
  - `backend/pom.xml`、`backend/examine-web/pom.xml`、`backend/examine-flow/pom.xml`、`backend/examine-app/pom.xml`

## 关键证据

- `TASK-BE-021` declared output 存在：`RuntimeRecordController`、`RuntimeRecordService`、`RuntimeRecordModels` 位于 `backend/examine-module/src/main/java/com/unique/examine/module/manage/runtime/`。
- `TASK-BE-021` 覆盖动态运行态记录端点：list-schema、search、detail、create、update、delete、action execution、history；`RuntimeRecordService` 返回服务端分页、字段权限、数据范围、筛选、排序、row detail target、action disabled reason、业务详情 tabs、approvalSidebar、history hook、traceId/auditLogId 和幂等键结果。
- `TASK-BE-030` declared output 存在：`FlowDefinitionController`、`FlowDefinitionService`、`FlowDefinitionModels` 位于 `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/definition/`。
- `TASK-BE-030` 覆盖流程定义 list/detail/create/update、node-library、canvas、node properties、simulate、publish-check、publish、snapshots、impact-analysis；canvas 返回 nodes/edges/branchLabels，节点属性覆盖 approval、condition、field_update、external_api、timer、timeout_reminder、end，publish-check 返回 blocking/warning items、impactRefs、traceId、auditLogId，simulate 返回 step traces 和 condition decisions。
- `TASK-BE-035` declared output 存在：`OpenApiController`、`OpenApiService`、`OpenApiModels` 位于 `backend/examine-app/src/main/java/com/unique/examine/app/manage/openapi/`。
- `TASK-BE-035` 覆盖外部应用 CRUD、scope、rate-limit、secret rotation、call log search；写接口读取 `Idempotency-Key` 并支持 body `idempotencyKey`；响应模型使用 `OpenApiSecretRefVO`，未发现明文 app secret 响应字段；调用日志查询覆盖 app、scope、result、time range、traceId。
- Web 聚合可用：`backend/examine-web/pom.xml` 已依赖 `examine-flow` 和 `examine-app`，`examine-flow`、`examine-app` 均声明 Spring Web。
- `TASK-FE-020` declared output 存在：`frontend/src/features/platform-admin/platformAdmin.ts` 与 `frontend/src/features/system-admin/systemAdmin.ts`，并接入 `platformShell`、`systemShell` 和 `app.ts` 路由。
- 上轮 FAIL 阻塞项 1 已解除：`frontend/src/features/platform/platformShell.ts` 中 `adminButton` 仅在 `canEnterPlatformAdmin()` 为 true 时创建；`frontend/src/features/system-shell/systemShell.ts` 中 `adminButton` 仅在 `canEnterSystemAdmin()` 为 true 时创建；`frontend/src/app/app.ts` 对 `/platform/admin` 和 `/systems/{systemId}/admin` 直达路由先做权限拦截并返回无权限页。
- 上轮 FAIL 阻塞项 2 已解除：`frontend/src/features/platform-admin/platformAdmin.ts` 的系统生命周期区包含创建、启用、禁用、删除、恢复、体检、发布检查动作入口，并在 `createLifecycleActionPanel` 中展示动作类型、原因、影响范围、动作结果、`AsyncTask.taskId`、retry/rollback 状态、`traceId` 和 `auditLogId`。
- 平台角色权限边界满足：平台角色面板说明平台权限只覆盖平台菜单、按钮、日志、任务和系统创建权限，不配置系统业务模块字段。
- 系统模块配置满足：系统后台模块管理区展示模块树/list、字段、列表列、筛选与场景、页面动作、导入导出、打印模板、发布检查。

## 验证命令

- `mvn -f backend/pom.xml -DskipTests compile`：使用 `D:\java\jdk\jdk21` 与 `D:\java\apache-maven-3.8.5`，11 个模块全部 `SUCCESS`，`BUILD SUCCESS`。
- `D:\java\nodejs\npm.cmd --prefix frontend run build`：`tsc --noEmit && vite build` 通过。
- `git diff --check`：退出码 0；仅报告 `.cursor/session/issues/registry.jsonl`、`docs/design/pre-coding-readiness.md`、`docs/design/user-approval.md` 的 LF/CRLF warning，未发现 whitespace error。

## 非阻塞观察

- G5 后端实现仍是 contract-first/sample-data driven，证据文件已声明未接真实持久化、权限引擎、幂等表、Secret 托管后端和流程运行时；这些与当前任务 Do Not / 后续批次边界一致，不阻塞 G5 task-accept。
- 本机实际可用工具链为 `D:\java\jdk\jdk21`、`D:\java\apache-maven-3.8.5`、`D:\java\nodejs`，不是落盘 AGENTS 示例中的 `D:\Tools\...` 路径；已按实际路径完成只读验证。
