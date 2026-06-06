# 工作区清理报告

## 清理时间

2026-06-05 23:36:25

## 清理原因

上一轮线性流程产物已经被理解审查证明不适合作为当前实现基线，尤其是旧 `docs/prd.md`、`docs/db_design.md`、`sql/init.sql` 和旧代码中仍使用错误平台前缀 `un_platt_`。这些产物继续留在活动区会污染 PM、DBA、backend、frontend 和 test 的后续判断。

## 归档位置

`.codex/archive/pre-understanding-cleanup-20260605-233625/`

## 已归档为历史参考

- `backend/`：旧线性流程生成的后端代码，仅作为反例和历史实现参考，不作为当前开发基线。
- `frontend/` 源码与配置：旧线性流程生成的前端源码、API 映射和配置，仅作为历史参考；未归档 `node_modules`、`dist`、tsbuildinfo 和 dev 日志。
- `sql/init.sql`：旧线性流程生成的初始化 SQL，存在错误平台表前缀，仅作为 DBA 反例参考。
- `docs/prd.md`、`docs/api.md`、`docs/db_design.md`、`docs/build_report.md`、`docs/review.json`、`docs/reviews/`：旧流程产物已归档，不作为当前审查模式输入。
- `baseCode.zip`：历史输入压缩包，归档保存。

## 已删除为无参考价值产物

- `frontend/node_modules/`
- `frontend/dist/`
- `frontend/tsconfig.node.tsbuildinfo`
- `frontend/tsconfig.tsbuildinfo`
- `frontend/vite-dev.err.log`
- `frontend/vite-dev.out.log`

删除前曾发现 Vite/esbuild 进程占用文件，已停止当前 `frontend` 相关进程后完成清理。

## 当前活动区保留

- `docs/user_requirement.md`
- `docs/service_info.md`
- `docs/requirement_analysis.md`
- `docs/legacy_reference.md`
- `docs/understanding/`
- `docs/tasks/`
- `AGENTS.md`
- `.codex/`

## 后续规则

- 当前阶段继续保持审阅模式，不写业务代码，不生成 SQL。
- 新 PRD 必须重新生成，不得沿用已归档的旧 `docs/prd.md`。
- API 契约必须在项目理解闭环通过后重新生成，不得沿用已归档的旧 `docs/api.md`。
