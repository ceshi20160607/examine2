# FE-009 流程工作台页面契约证据

## 范围

- 页面入口：`flow.templates`、`flow.workbench`。
- 页面模型：`frontend/src/pages/flow/flowWorkbenchPageModel.ts`。
- API 范围：FLOW-001 至 FLOW-021，全部来自冻结契约 `docs/api.md` 与 `frontend/src/api/endpoints.ts`。

## API 映射

| API | 页面能力 | 关键入参 | 权限/状态 |
| --- | --- | --- | --- |
| FLOW-001 | 流程模板列表 | systemId、分页筛选 | FLOW_TEMPLATE_VIEW |
| FLOW-002 | 新建流程模板 | templateName、moduleId | FLOW_TEMPLATE_CREATE |
| FLOW-003 | 保存流程图 | templateId、graph、graphVersion | FLOW_TEMPLATE_EDIT |
| FLOW-004 | 发布前检查 | templateId | FLOW_TEMPLATE_PUBLISH |
| FLOW-005 | 发布流程模板 | templateId、幂等键 | FLOW_TEMPLATE_PUBLISH，已发布/停用禁用 |
| FLOW-006 | 模块绑定流程 | moduleId、templateId、enabled | FLOW_BINDING_EDIT |
| FLOW-007 | 待办列表 | systemId、分页筛选 | APPROVER |
| FLOW-008 | 待办详情 | taskId | TASK_ACTOR |
| FLOW-009 | 同意/拒绝/转交/退回/终止 | taskId、taskVersion、action、幂等键 | FLOW_TASK_HANDLE，已处理禁用 |
| FLOW-010 | 撤回实例 | instanceId、comment、幂等键 | STARTER_OR_AUTHORIZED，仅审批中可撤回 |
| FLOW-011 | 实例详情 | instanceId | FLOW_INSTANCE_VIEW_DATA_SCOPE |
| FLOW-012 | 实例流程图 | instanceId | FLOW_INSTANCE_VIEW |
| FLOW-013 | 抄送列表 | systemId、分页筛选、幂等键 | 系统成员上下文；冻结幂等清单要求带键 |
| FLOW-014 | 我的申请 | systemId、分页筛选 | 系统成员上下文 |
| FLOW-015 | 领取任务 | taskId、taskVersion、幂等键 | 待处理且未领取 |
| FLOW-016 | 取消领取 | taskId、taskVersion、幂等键 | 待处理且已领取 |
| FLOW-017 | 实例列表 | systemId、分页筛选 | 数据范围 |
| FLOW-018 | 审批历史 | instanceId | 实例可见 |
| FLOW-019 | 模板详情 | templateId | 模板可见 |
| FLOW-020 | 模板流程图 | templateId | 模板可见 |
| FLOW-021 | 模板状态变更 | templateId、status、version | 模板管理权限 |

## 状态与错误处理

- 页面模型统一输出 `FlowRequestState`，保留 `requestId`、`retryable`、`errorMessage` 和本地 `validationErrors`。
- `REJECT`、`RETURN`、`TERMINATE` 必填 `comment`；`TRANSFER` 必填 `transferToMemberId`；`RETURN` 必填 `targetNodeId` 或 `returnStrategy`。
- `FLOW-005`、`FLOW-009`、`FLOW-010`、`FLOW-013`、`FLOW-015`、`FLOW-016` 均通过 `X-Idempotency-Key` 和 body 幂等键约束，避免重复提交；其中 `FLOW-013` 虽为 GET，页面模型仍按冻结幂等清单传入 header。
- 已完成任务状态 `DONE`、`CANCELED`、`TRANSFERRED`、`RETURNED` 返回 `FLOW_TASK_ALREADY_HANDLED:*` 禁用原因。
- 已结束实例状态 `APPROVED`、`REJECTED`、`WITHDRAWN`、`TERMINATED` 返回 `FLOW_INSTANCE_ALREADY_FINISHED:*` 禁用原因。
- 流程图不直接暴露不可解释 JSON，统一转换为 `FlowDiagramRenderModel` 的节点、连线、当前节点、已完成节点和驳回节点。

## 自检结论

- 已覆盖 FLOW-001 至 FLOW-021 的路由参数、请求体、响应体、权限点、幂等键、禁用态、空态/错误态和 requestId 展示。
- 页面模型没有新增旁路 `fetch`、`axios` 或手写 URL 请求，所有调用均走 `ApiClient.call` 与冻结端点 ID。
- 当前 `frontend/` 目录仍无 `package.json` 和 `tsconfig.json`，无法执行正式 build/typecheck；本任务按静态模型和契约证据完成。
