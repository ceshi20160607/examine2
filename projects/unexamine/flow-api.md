# 通用流程 API 契约

本文对应 `flow` 业务域。先调用 `POST /api/system/session` 登录，后续携带服务端会话 Cookie。路径中的 `tenantId` 必须是当前成员所属租户；配置管理需租户管理员，关联模块记录的发起人还须有记录修改权限。实例历史仅发起人、实际处理人和租户管理员可读。时间字段使用 `*Time`，日期时间由后端按本地时间返回。

## 通用流程 Flow

路径前缀：`/api/tenants/{tenantId}/flows`。

| 方法与路径 | 请求关键字段 | 返回及用途 |
| --- | --- | --- |
| `GET /` | `page=1&size=20&sortField=createTime&direction=desc`；排序字段仅 `createTime/name` | MyBatis 分页对象，`records/total/current/size` |
| `POST /` | `{ "name": "请款流程", "description": "..." }` | 流程定义，初始 `DRAFT` |
| `GET /{flowId}` | 无 | `{ flow, nodes, edges, versions }`，包含当前草稿和历史发布版本 |
| `PUT /{flowId}/draft` | `{ "name": "...", "description": "...", "nodes": [...], "edges": [...] }` | 完整替换草稿，返回详情 |
| `POST /{flowId}/publish` | 无请求体 | 不可变版本，含 `versionNo/definitionSnapshot`；定义变为 `ENABLED` |
| `POST /{flowId}/disable` | 无请求体 | 停止新发起；运行实例仍用原快照 |
| `GET /bindings`、`POST /bindings` | 查询可传 `moduleId`；创建传 `{ "moduleId": 1, "flowId": 3, "triggerEvent": "CREATE" }`，事件可为 `CREATE/UPDATE` | 管理员查看或创建自动触发绑定；绑定冻结当前 `versionId` |
| `PUT /bindings/{bindingId}/status` | `{ "enabled": false }` | 管理员启停绑定 |
| `POST /bindings/{bindingId}/refresh-version` | 无请求体 | 管理员将绑定切换到流程当前发布版本；既有实例仍用旧快照 |
| `POST /{flowId}/examples` | `{ "moduleId": 1, "recordId": 2 }`；无条件流程可传 `{}` | 流程实例，含 `id/versionId/status/currentNodeKey` |
| `GET /examples` | `moduleId=1&recordId=2&page=1&size=20` | 按记录读取权限返回 `{ records, total, page, size }`；记录项含 `id/flowId/versionId/bindingId/status/startTime/finishTime`，按发起时间倒序 |
| `GET /tasks` | 无 | 当前成员可处理的 `UnFlowTask[]` |
| `POST /examples/{exampleId}/actions` | `{ "actionType": "APPROVE", "taskId": 8, "requestKey": "唯一请求号", "remark": "同意" }`；拒绝用 `REJECT`，发起人撤回用 `WITHDRAW` 且无需 `taskId` | 操作记录 `UnFlowAction`；相同请求号同操作重试返回原记录 |
| `POST /examples/{exampleId}/tasks/{taskId}/timeout` | `{ "executeTime": "2026-10-19T13:00:00.000" }` | 管理员为运行待办安排一次 `TIMEOUT` job；同时间重试返回原 job |
| `GET /examples/{exampleId}/jobs`、`GET /examples/{exampleId}/exceptions` | 无 | 管理员查看定时任务及异常列表 |
| `POST /examples/{exampleId}/exceptions/{exceptionId}/resolve` | `{ "handleRemark": "已人工跟进" }` | 管理员将异常 `OPEN` 置为 `RESOLVED`；同一处置重试返回原记录 |
| `GET /examples/{exampleId}` | 无 | `{ example, tasks, actions, events, jobs, exceptions }`，实例和完整事件历史 |

## 与模块的流程绑定

模块先配置 `flow` 类型动作。以下入口保留原 `/api/modules/{moduleId}` 地址，由流程域校验发布定义并维护 `un_module_flow`；读取要求有效租户成员，写入要求租户管理员。

| 方法与路径 | 请求关键字段 | 返回及用途 |
| --- | --- | --- |
| `GET /api/modules/{moduleId}/flow-bindings?tenantId={tenantId}` | 无 | 有效 `UnModuleFlow[]` |
| `POST /api/modules/{moduleId}/flow-bindings?tenantId={tenantId}` | `{code,definitionId,actionCode}` | 绑定同租户已发布启用的流程；返回 `UnModuleFlow` |
| `PUT /api/modules/{moduleId}/flow-bindings/{id}?tenantId={tenantId}` | `{code,definitionId,actionCode}` | 更新定义或动作，绑定编码不可改 |
| `DELETE /api/modules/{moduleId}/flow-bindings/{id}?tenantId={tenantId}` | 无 | 停用绑定，保留历史 |

编码须符合模块编码规则且不能重复；`actionCode` 必须指向当前模块启用的 `flow` 类型动作。返回实体中的定义主键为 `flowId`，另含 `bindingCode/startActionCode/status`。

`nodes[]` 的字段为 `nodeKey/name/nodeType/assigneeType/assigneeId/sortNum`。节点类型只支持 `START/TASK/END`；`TASK` 的处理人来源只支持 `STARTER/MEMBER/ROLE`。`MEMBER` 的 `assigneeId` 是系统成员主键；`ROLE` 的 `assigneeId` 是同租户启用角色主键。角色在发布时必须有有效成员，运行到节点时重新解析有效成员；多人候选中首个处理人完成任务，其余待办取消。

`edges[]` 的字段为 `sourceNodeKey/targetNodeKey/conditionRule/sortNum`。每个非结束节点必须有且只有一条空 `conditionRule` 的默认连线，条件线按 `sortNum` 升序尝试。规则是**字符串形式的 JSON**，例如：

```json
{"field":"amount","operator":"GTE","value":5}
```

仅支持 `EQ/NE/GT/GTE/LT/LTE`；数值比较的 `value` 必须是数字，等值比较可用数字或字符串。值来自关联记录的 `un_module_record_data`；字段缺失或无值时不命中。条件流程发起时必须同时传 `moduleId` 和 `recordId`。发布会拒绝循环、不可达节点、缺少默认连线及无效规则；不执行脚本，不支持 `CONDITION` 节点或并行网关。

自动绑定在模块记录 `CREATE/UPDATE` 成功保存后、同一事务内触发。绑定捕获发布版本，后续发布不会自动改写绑定；更新绑定版本须显式调用 `refresh-version`。每个绑定、记录、记录版本产生一个 `AUTO_START` 操作请求号，重复回调只返回原实例。处理人失效等业务失败会保留 `FAILED` 实例及 `OPEN` 异常，模块记录仍保存；数据库写入失败会回滚同事务。当前自动触发只接入模块记录创建和修改，不监听删除。

超时 job 由管理员显式安排，后台每 2 秒扫描到期任务。到期仍待办时，job 变 `DONE`、任务变 `EXPIRED`、实例变 `FAILED`，并生成 `TIMEOUT/OPEN` 异常；提前完成或撤回时 job 变 `CANCELLED`。管理员的 `resolve` 仅确认异常，不恢复失败实例。当前不自动为每个节点安排期限，也不执行 REMIND 通知或自动重试。

## 错误与状态

列表页码从 1 开始，`size` 为 1 至 100；排序字段使用上述白名单。无会话、越权、资源不存在、无效输入和状态冲突分别按 HTTP `401/403/404/400/409` 处理。流程操作的 `requestKey` 最多 64 字符，前端每次新操作生成新值，网络重试沿用原值。实例快照和发布版本在后续草稿修改后保持原样。
