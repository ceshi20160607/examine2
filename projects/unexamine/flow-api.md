# 流程、审批与 KPI 后端接口

本文对应当前 `projects/unexamine/backend` 实现。先调用 `POST /api/system/session` 登录；下列接口使用同一会话 Cookie。路径中的 `tenantId` 都会校验当前登录成员是否属于该租户。管理配置需租户管理员；关联模块记录的发起人还须有该记录的修改权限。实例历史仅发起人、实际处理人和租户管理员可读。时间字段使用 `*Time`，日期时间由后端按本地时间返回。

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
| `POST /{flowId}/examples` | `{ "moduleId": 1, "recordId": 2 }`；无条件流程可传 `{}` | 流程实例，含 `id/versionId/status/currentNodeKey` |
| `GET /tasks` | 无 | 当前成员可处理的 `UnFlowTask[]` |
| `POST /examples/{exampleId}/actions` | `{ "actionType": "APPROVE", "taskId": 8, "requestKey": "唯一请求号", "remark": "同意" }`；拒绝用 `REJECT`，发起人撤回用 `WITHDRAW` 且无需 `taskId` | 操作记录 `UnFlowAction`；相同请求号同操作重试返回原记录 |
| `GET /examples/{exampleId}` | 无 | `{ example, tasks, actions, events }`，实例和完整事件历史 |

`nodes[]` 的字段为 `nodeKey/name/nodeType/assigneeType/assigneeId/sortNum`。节点类型只支持 `START/TASK/END`；`TASK` 的处理人来源只支持 `STARTER/MEMBER/ROLE`。`MEMBER` 的 `assigneeId` 是系统成员主键；`ROLE` 的 `assigneeId` 是同租户启用角色主键。角色在发布时必须有有效成员，运行到节点时重新解析有效成员；多人候选中首个处理人完成任务，其余待办取消。

`edges[]` 的字段为 `sourceNodeKey/targetNodeKey/conditionRule/sortNum`。每个非结束节点必须有且只有一条空 `conditionRule` 的默认连线，条件线按 `sortNum` 升序尝试。规则是**字符串形式的 JSON**，例如：

```json
{"field":"amount","operator":"GTE","value":5}
```

仅支持 `EQ/NE/GT/GTE/LT/LTE`；数值比较的 `value` 必须是数字，等值比较可用数字或字符串。值来自关联记录的 `un_module_record_data`；字段缺失或无值时不命中。条件流程发起时必须同时传 `moduleId` 和 `recordId`。发布会拒绝循环、不可达节点、缺少默认连线及无效规则；不执行脚本，不支持 `CONDITION` 节点、并行网关或自动触发绑定。

## 审批 Examine

路径前缀：`/api/tenants/{tenantId}/examines`。

| 方法与路径 | 请求关键字段 | 返回及用途 |
| --- | --- | --- |
| `GET /` | `page=1&size=20&sortField=sortNum&direction=asc`；排序字段 `sortNum/name/createTime` | MyBatis 分页对象 |
| `POST /` | `{ "name": "采购审批", "groupId": 1, "icon": "...", "description": "...", "sortNum": 1 }` | 审批定义，初始 `DRAFT` |
| `GET /groups`、`POST /groups` | 创建分组传 `{ "name": "...", "sortNum": 1 }` | 分组列表或新分组 |
| `GET /{examineId}` | 无 | `{ examine, config, configUsers, nodes, users, fields }` |
| `PUT /{examineId}/design` | 完整设计体，见下文 | 整体替换草稿；状态回到 `DRAFT` |
| `POST /{examineId}/enable`、`POST /{examineId}/disable` | 无请求体 | 校验后启用或停用新发起 |
| `POST /{examineId}/examples` | `{ "moduleId": 2, "recordId": 3, "selectedMemberIds": [] }` | 新实例；要求模块已绑定此审批且记录属于该模块与租户 |
| `GET /tasks` | 无 | 当前成员可处理的实例节点人员 `UnExamineExampleNodeUser[]` |
| `POST /examples/{exampleId}/actions` | `{ "nodeUserId": 5, "actionType": "APPROVE", "requestKey": "唯一请求号", "opinion": "同意" }`；拒绝用 `REJECT` | 更新后的实际处理人记录；相同操作重试返回原记录 |
| `POST /examples/{exampleId}/transfer` | `{ "nodeUserId": 5, "targetMemberId": 2, "requestKey": "唯一请求号", "opinion": "请代办" }` | `{ previous, target }`；原处理人 `CANCELLED`，新成员获得待办，历史保留两人 |
| `POST /examples/{exampleId}/withdraw` | 无请求体 | 发起人撤回后的实例；须允许撤回且仍在审批中 |
| `POST /examples/{exampleId}/timeout` | `PASS` 传 `{}`；`TRANSFER` 传 `{ "targetMemberId": 2 }` | 超时异常记录；仅租户管理员可在当前节点实际到期后显式触发 |
| `GET /examples/{exampleId}` | 无 | `{ example, nodes, users, exceptions }`，含逐人意见和超时处置 |

设计体关键字段：`name/groupId/icon/description/sortNum/applyScope/allowWithdraw/timeoutMinutes/timeoutAction/configUsers/nodes`。`applyScope` 为 `ALL` 或 `SPECIFIED`；指定范围的 `configUsers[]` 用 `{ "targetType": "MEMBER|DEPARTMENT", "targetId": 1, "sortNum": 1 }`。`nodes[]` 用 `nodeKey/name/nodeType/sortNum/approverType/passMode/fallbackMemberId/users/fields`；节点类型 `APPROVE/COPY`，处理人来源 `MEMBER/DEPARTMENT/ROLE/SUPERIOR/STARTER_CHOICE`，通过方式 `ANY/ALL/SEQUENTIAL`。`users[]` 用同样的 `targetType/targetId/sortNum`，支持 `MEMBER/DEPARTMENT/ROLE`；`fields[]` 用 `fieldId/visible/editable/required/sortNum`。无人员或字段时传空数组，不能省略。

`timeoutMinutes` 与 `timeoutAction` 须同时填写；当前动作只支持 `PASS` 和 `TRANSFER`，时长 1 至 525600 分钟。超时以**当前实例节点的 `startTime`** 计算。`PASS` 自动结束当前节点并取消原待办；`TRANSFER` 只支持当前节点一名待处理人，目标须是同租户其他有效成员。重复触发返回 409。当前没有后台定时调度和通知投递，需显式调用超时接口；`REMIND` 不可配置。手动转办仅允许当前待办人执行，表结构没有手动转办开关。审批条件节点与 `conditionRule` 尚不能启用。

模块需先有 `examine` 类型操作，再通过 `POST /api/modules/{moduleId}/examine-bindings?tenantId={tenantId}` 绑定：`{ "code": "唯一绑定编码", "definitionId": 审批ID, "actionCode": "操作编码" }`。

## KPI

路径前缀：`/api/tenants/{tenantId}/kpis`。管理员可维护本租户全部 KPI；普通成员只可读取分配给自己且已启用的 KPI。

| 方法与路径 | 请求关键字段 | 返回及用途 |
| --- | --- | --- |
| `GET /` | `page=1&size=20&sortField=createTime&sortOrder=desc`；排序字段 `createTime/kpiName/kpiCode` | `{ records, total, page, size }` |
| `POST /` | `{ "kpiCode": "sales_count", "kpiName": "数量", "dataSourceId": 1, "aggregation": "count", "cycleType": "all", "targetValue": 5 }` | KPI 定义，初始启用 |
| `GET /{kpiId}` | 无 | KPI 定义详情 |
| `PUT /{kpiId}` | `{ "kpiName": "...", "dataSourceId": 1, "metricField": "amount", "aggregation": "sum", "cycleType": "all", "targetValue": 10 }` | 更新定义；编码不可改 |
| `PUT /{kpiId}/status` | `{ "enabled": false }` | 启停 KPI |
| `GET /{kpiId}/members` | 无 | 管理员看到全部目标；普通成员只看到自己的 `UnKpiUser[]` |
| `PUT /{kpiId}/members/{memberId}` | `{ "targetValue": 7 }` | 新建或覆盖成员目标；`memberId` 是同租户系统成员主键 |
| `DELETE /{kpiId}/members/{memberId}` | 无 | 取消目标并撤销该成员读取范围 |
| `GET /{kpiId}/value` | 无 | `{ kpiId, targetValue, actualValue, cycleType }`，总实际值 |
| `GET /{kpiId}/members/{memberId}/value` | 无 | `{ kpiId, memberId, targetValue, actualValue, cycleType }`，按模块记录负责人计算个人值 |

只支持 `cycleType=all`、`aggregation=count|sum`。`sum` 必须提供可求和的 `metricField`；`count` 会使用 `*`。`filterData` 目前不可非空，其他周期直接返回 400，避免返回虚假统计。目标值须非负，最多四位小数。统计读取已配置数据源所指向的模块记录。

## 错误与状态

列表页码从 1 开始，`size` 为 1 至 100；排序字段使用上述白名单。无会话、越权、资源不存在、无效输入和状态冲突分别按 HTTP `401/403/404/400/409` 处理。流程操作与审批操作的 `requestKey` 最多 64 字符，前端每次新操作生成新值，网络重试沿用原值。实例快照和发布版本在后续草稿修改后保持原样。
