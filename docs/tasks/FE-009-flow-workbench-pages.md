# FE-009 流程工作台页面

- taskId: FE-009
- 标题: 流程工作台页面
- 负责角色: frontend
- 所属大任务/模块: 前端 / 流程工作台
- 目标: 实现流程模板配置、待办、抄送、我的申请、实例详情、流程图和审批历史页面。
- 输入文件: `docs/api.md`、`frontend/src/api/`
- 输出文件或输出目录: `frontend/src/pages/flow/`、`frontend/docs/page-contracts/FE-009-flow-workbench-pages.md`

## 详细工作内容

- 实现 FLOW-001 至 FLOW-021 页面和交互。
- 支持发布检查、绑定模块、领取、取消领取、同意、拒绝、转交、退回、终止、撤回。
- 展示任务状态、业务摘要、流程图、审批历史和禁用原因。
- 补齐页面级 API 映射证据，覆盖路由、API ID、必填入参、响应字段、上下文依赖、枚举/状态/错误码、权限禁用态、空态/错误态、requestId 展示和无旁路请求检查。

## 完成状态定义

- 默认状态: done。
- 完成条件: 审批人和发起人可完成流程工作台主流程，`FE-009` 页面级契约证据文件已补齐。

## 完成记录

- 完成时间: 2026-06-07。
- 输出: `frontend/src/pages/flow/flowWorkbenchPageModel.ts`、`frontend/src/pages/flow/index.ts`、`frontend/docs/page-contracts/FE-009-flow-workbench-pages.md`。
- 自检: 已覆盖 FLOW-001 至 FLOW-021 的页面模型、幂等键、本地必填校验、禁用态、流程图解释模型、requestId/错误态和页面契约证据；`frontend/src/pages/flow/` 未新增旁路请求。
- 限制: 当前 `frontend/` 目录无 `package.json`/`tsconfig.json`，无法执行正式 build/typecheck。

## 验收标准

- 处理成功后移除待办并刷新详情状态。
- 重复处理和非候选人处理显示明确错误。
- 权限禁用态、空态、错误态和完成证据可被 FE-012 汇总到 `frontend/docs/api-contract-map.md`。

## 测试/自检要求

- 自检无待办空态、原因必填、重复处理、领取状态、流程图渲染。

## 依赖任务

- FE-007
- FE-008

## 可并行关系

- 可与 FE-010 并行。

## 不允许事项

- 不只渲染不可解释 JSON。
- 不允许前端自行推断业务最终状态。
