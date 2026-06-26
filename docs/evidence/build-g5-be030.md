# TASK-BE-030 自检证据

## 范围

- Worker: G5 backend
- Task: TASK-BE-030 流程定义 APIs
- 修改范围：
  - `backend/examine-flow/src/main/java/com/unique/examine/flow/manage/definition/**`
  - `backend/examine-flow/pom.xml`
  - `docs/evidence/build-g5-be030.md`

## 端点列表

- `GET /api/v1/systems/{systemId}/flows`
- `POST /api/v1/systems/{systemId}/flows`
- `GET /api/v1/systems/{systemId}/flows/{flowId}`
- `PATCH /api/v1/systems/{systemId}/flows/{flowId}`
- `GET /api/v1/systems/{systemId}/flows/node-library`
- `GET /api/v1/systems/{systemId}/flows/{flowId}/canvas`
- `PUT /api/v1/systems/{systemId}/flows/{flowId}/canvas`
- `GET /api/v1/systems/{systemId}/flows/{flowId}/nodes/{nodeKey}/properties`
- `POST /api/v1/systems/{systemId}/flows/{flowId}/simulate`
- `POST /api/v1/systems/{systemId}/flows/{flowId}/publish-check`
- `POST /api/v1/systems/{systemId}/flows/{flowId}/publish`
- `GET /api/v1/systems/{systemId}/flows/{flowId}/snapshots`
- `GET /api/v1/systems/{systemId}/flows/{flowId}/snapshots/{versionNo}`
- `GET /api/v1/systems/{systemId}/flows/{flowId}/impact-analysis`

## 覆盖能力

- 流程列表、详情、创建、更新。
- 节点库与节点属性面板。
- 画布 nodes、edges、branch labels、连通性摘要。
- 节点类型专属 payload：`approval`、`condition`、`field_update`、`external_api`、`timer`、`timeout_reminder`、`end`。
- 模拟运行返回 step traces 与 condition decisions。
- 发布检查返回 blocking items、warnings、impact refs、traceId、auditLogId。
- 发布返回不可变版本快照；快照列表/详情可查询。
- 影响分析返回模块、权限快照、OpenAPI、消息模板引用影响。

## 命令结果

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -f backend/pom.xml -pl examine-flow -am -DskipTests compile
```

结果：通过。

关键日志：

```text
Reactor Summary for examine 0.0.1-SNAPSHOT:
examine ............................................ SUCCESS
examine-core ....................................... SUCCESS
examine-flow ....................................... SUCCESS
BUILD SUCCESS
```

```powershell
git diff --check
```

结果：通过，无 whitespace error。仅提示 LF/CRLF：

- 工作区已有文件：`.cursor/session/issues/registry.jsonl`、`docs/design/pre-coding-readiness.md`、`docs/design/user-approval.md`
- 本次新增文件：`FlowDefinitionController.java`、`FlowDefinitionService.java`、`FlowDefinitionModels.java`、`build-g5-be030.md`

## 残余风险

- 当前实现是契约优先的流程定义/配置 API，不创建审批实例、待办、消息或运行时动作记录。
- `external_api`、`timer`、`timeout_reminder` 的实际执行、重试、补偿和消息投递需要后续 runtime/approval/todo/message worker 接入发布快照。
- 影响分析目前返回配置引用级估算，不扫描真实运行中实例；后续运行时表接入后需要改为真实统计。
