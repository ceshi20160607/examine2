# Agent 流程升级报告

## 本次升级结论

已把当前协作方式拆成两个独立模式：

- 审阅模式：只写需求、理解、PRD、API、任务拆分和问题闭环文档，不写代码、不生成 SQL、不创建后端/前端工程。
- 开发模式：只在审阅模式冻结 PRD、API 和任务计划后启动，按 `docs/tasks/` 的具体任务执行。

## 已沉淀的确定性优化

| 优化项 | 落点 |
|--------|------|
| 审阅模式和开发模式独立，审阅模式绝不写实现产物。 | `AGENTS.md`、`docs/process/review_mode.md`、`docs/process/development_mode.md` |
| 常驻/唤醒式 agent 优先，尽量复用同一角色上下文。 | `AGENTS.md`、`.codex/agents/*.toml` |
| PM 统一读取全部问题，再按责任方分发裁决版问题文档。 | `AGENTS.md`、`.codex/agents/pm.toml` |
| 问题文档按 raw、pm、replies、verification 分层，避免多个角色抢写同一文档。 | `AGENTS.md`、`docs/issues/` |
| PM 不能决策的问题必须写入 `docs/issues/user_questions.md`。 | `AGENTS.md`、`docs/issues/user_questions.md` |
| 可并行任务必须输出路径不重叠。 | `AGENTS.md`、`.codex/agents/planner.toml` |
| 共享总文档必须采用分片输出，再由串行汇总任务写总文档。 | `AGENTS.md`、`.codex/agents/planner.toml` |
| 前端页面任务必须输出页面级 API 映射证据，FE-012 只做汇总。 | `.codex/agents/frontend.toml`、`.codex/agents/planner.toml` |
| 真实用户前端必须先有 UI/UX 设计冻结，不能从 API 直接堆页面；缺少设计时 frontend 必须回报 `ui-design-missing`。 | `AGENTS.md`、`.codex/agents/uiux.toml`、`.codex/agents/frontend.toml`、`.codex/agents/planner.toml`、`.codex/agents/reviewer.toml` |
| 测试/构建并行任务写分报告，最终报告由串行汇总任务写。 | `.codex/agents/test.toml`、`.codex/agents/validator.toml` |
| 后端最终自检任务不得作为业务实现任务前置依赖。 | `.codex/agents/backend.toml`、`.codex/agents/planner.toml` |
| 后端、前端、DBA、test 的并行批次必须按真实依赖拓扑表达，不能把存在直接依赖的任务写到同一可执行批次。 | `docs/task_plan.md`、`.codex/agents/planner.toml` |
| `REV-001/002/003` 只写 `docs/review_parts/*.md`，`REV-004` 唯一写 `docs/review.json`。 | `docs/tasks/REV-001..004`、`.codex/agents/reviewer.toml` |
| `GEN-004` 不允许输出笼统 `backend/`，必须精确到各业务模块 `base/` 包和生成报告。 | `docs/tasks/GEN-004-generator-run-report.md`、`.codex/agents/backend.toml` |
| `un_app_*` 只作为旧 OpenAPI 历史表域参考；MVP 不新建该前缀表，不进入生成器映射。 | `AGENTS.md`、`.codex/agents/dba.toml`、`.codex/agents/backend.toml`、`.codex/agents/planner.toml` |

## 已沉淀的禁止项

- 审阅模式禁止创建或修改 `backend/`、`frontend/`、`sql/`、`docs/db_design.md`、`sql/init.sql`。
- backend/frontend/test 在审阅模式禁止为了“先跑起来”私自补接口、字段、页面或 SQL。
- 冻结 API 后，任何接口、字段、枚举、错误码、状态或权限语义变化都必须重新打开 API 契约评审。
- PM 未裁决的问题不得直接写入最终冻结文档。
- agent 不属于自己责任的问题不得自行处理，必须退回 PM 转交。
- 可并行任务不得写同一文件或同一总目录根。
- `un_platt_` 禁止使用；OpenAPI 新表域固定 `un_openapi_`，旧 `un_app_*` 仅作历史参考，不进入 MVP 新建表和生成器映射。

## 当前目录环境结论

活动区当前没有 `backend/`、`frontend/`、`sql/`、`docs/db_design.md` 或 `sql/init.sql`，未发现审阅模式误写实现产物。

保留参考：

- `docs/requirement_analysis.md`
- `docs/legacy_reference.md`
- `docs/prd.md`
- `docs/project_understanding.md`
- `docs/api.md`
- `docs/api_review.md`
- `docs/task_plan.md`
- `docs/tasks/`
- `docs/understanding/`

`.codex/archive/` 中原保存旧错误流水线和清理前产物；项目迁移到 `examine2/` 并接入 git 后，后续流程不再依赖该归档，已于 2026-06-06 删除。

新增结构：

- `docs/process/review_mode.md`
- `docs/process/development_mode.md`
- `docs/process/agent_upgrade_report.md`
- `docs/issues/raw/`
- `docs/issues/pm/`
- `docs/issues/replies/`
- `docs/issues/verification/`
- `docs/issues/user_questions.md`

## 后续建议

任务拆分已经通过 DBA、backend、frontend、test 复核，当前可标记为冻结。下一步仍不自动进入开发模式；只有用户明确切换到开发模式后，才按冻结 `docs/task_plan.md`、`docs/tasks/` 和 `docs/api.md` 执行。
