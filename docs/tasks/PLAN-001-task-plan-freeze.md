# PLAN-001 任务拆分冻结

- taskId: PLAN-001
- 标题: 任务拆分冻结
- 负责角色: planner
- 所属大任务/模块: 任务计划
- 目标: 基于冻结 PRD、项目理解和 API 契约形成可执行小任务清单。
- 输入文件: `docs/prd.md`、`docs/project_understanding.md`、`docs/api.md`、`docs/api_review.md`、`docs/service_info.md`、`.codex/state.json`
- 输出文件或输出目录: `docs/task_plan.md`、`docs/tasks/`

## 详细工作内容

- 拆分 DBA、examine-generator、后端、前端、test、validator、reviewer 小任务。
- 定义任务依赖、并行条件、完成标准和自检要求。
- 明确当前阶段不进入 DB、SQL、后端或前端实现。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 全局计划和每个任务 Markdown 文件存在，必填字段完整。

## 验收标准

- 任务清单覆盖冻结 API 的主要业务域和验证回环。
- 所有任务状态均为 pending。

## 测试/自检要求

- 检查 `docs/task_plan.md` 非空。
- 检查 `docs/tasks/` 下每个文件包含 taskId、负责角色、输入、输出、依赖和不允许事项。

## 依赖任务

- 无。

## 可并行关系

- 不可并行，任务清单必须先冻结。

## 不允许事项

- 不修改 PRD、API、项目理解或 API 评审文件。
- 不生成 DB、SQL、后端或前端产物。

