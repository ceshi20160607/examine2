# 审批 API 契约

本文对应 `examine` 业务域。先调用 `POST /api/system/session` 登录，后续携带服务端会话 Cookie。路径中的 `tenantId` 必须是当前成员所属租户；审批配置需租户管理员，模块记录发起人还须有记录修改权限。实例历史仅发起人、实际处理人和租户管理员可读。时间字段使用 `*Time`，日期时间由后端按本地时间返回。

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

## 与模块的审批绑定

模块先配置 `examine` 类型动作。以下入口保留原 `/api/modules/{moduleId}` 地址，由审批域校验启用定义并维护 `un_module_examine`；读取要求有效租户成员，写入要求租户管理员。

| 方法与路径 | 请求关键字段 | 返回及用途 |
| --- | --- | --- |
| `GET /api/modules/{moduleId}/examine-bindings?tenantId={tenantId}` | 无 | 有效 `UnModuleExamine[]` |
| `POST /api/modules/{moduleId}/examine-bindings?tenantId={tenantId}` | `{code,definitionId,actionCode}` | 绑定同租户已启用的审批；返回 `UnModuleExamine` |
| `PUT /api/modules/{moduleId}/examine-bindings/{id}?tenantId={tenantId}` | `{code,definitionId,actionCode}` | 更新定义或动作，绑定编码不可改 |
| `DELETE /api/modules/{moduleId}/examine-bindings/{id}?tenantId={tenantId}` | 无 | 停用绑定，保留历史 |

编码须符合模块编码规则且不能重复；`actionCode` 必须指向当前模块启用的 `examine` 类型动作。返回实体中的定义主键为 `examineId`，另含 `bindingCode/startActionCode/status`。

## 错误与状态

列表页码从 1 开始，`size` 为 1 至 100；排序字段使用上述白名单。无会话、越权、资源不存在、无效输入和状态冲突分别按 HTTP `401/403/404/400/409` 处理。审批操作的 `requestKey` 最多 64 字符，前端每次新操作生成新值，网络重试沿用原值。
