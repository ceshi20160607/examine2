# 审阅模式流程

## 目标

审阅模式只把需求、理解、PRD、API 契约和任务拆分打磨到可开发状态，不写代码、不生成 SQL、不创建后端或前端工程。

## 入口条件

- 存在 `docs/user_requirement.md` 和 `docs/service_info.md`。
- 旧项目参考只读目录为 `.codex/oldexamine/`。
- `.codex/state.json` 记录当前处于审阅相关阶段。

## 阶段

| 阶段 | 主持角色 | 参与角色 | 输出 |
|------|----------|----------|------|
| 需求分析 | analyst | - | `docs/requirement_analysis.md`、`docs/legacy_reference.md` |
| PRD 生成 | pm | analyst 输入 | `docs/prd.md` |
| 项目理解审查 | pm | dba/backend/frontend/test | `docs/project_understanding.md`、`docs/understanding/*_review.md` |
| API 契约冻结 | pm | dba/backend/frontend/test | `docs/api.md`、`docs/api_review.md`、`docs/understanding/*_api_review.md` |
| 任务拆分冻结 | planner/pm | dba/backend/frontend/test | `docs/task_plan.md`、`docs/tasks/`、`docs/understanding/*_task_review.md` |

## 问题流转

1. 角色在 `docs/understanding/{role}_{stage}_review.md` 中提出原始问题。
2. Orchestrator 可把原始问题按责任方归类到 `docs/issues/raw/{stage}/{owner}_wenti.md`。
3. PM 读取所有问题，输出裁决版 `docs/issues/pm/{stage}/{owner}_wenti.md`。
4. 责任角色只处理 PM 裁决版分给自己的 issue。
5. 责任角色回复到 `docs/issues/replies/{stage}/{owner}_reply.md`。
6. PM 汇总解决状态，并通知原提出角色复核。
7. 原提出角色写 `docs/issues/verification/{stage}/{reviewer}_verification.md`。

## 关闭规则

- 每个 issue 最多 3 次闭环。
- 所有阻塞 issue 必须由原提出角色确认 `closed/pass`。
- PM 不能决策的问题写入 `docs/issues/user_questions.md`。
- 审阅模式最终只允许进入开发模式，不直接写实现。

## 禁止事项

- 禁止创建或修改 `backend/`、`frontend/`、`sql/`、`docs/db_design.md`、`sql/init.sql`。
- 禁止运行代码生成、数据库导入或实现性构建。
- 禁止 backend/frontend/test 私自修改冻结 API。
- 禁止可并行任务共享同一输出文件；共享总文档必须由分片任务加串行汇总任务完成。
