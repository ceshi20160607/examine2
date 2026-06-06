# PM adjudicated task issues for planner

## PM Decision

本轮问题全部归属 planner 的任务拆分质量问题，不需要回到 PRD 或 API，不允许进入开发模式。planner 只修改 `docs/task_plan.md` 和 `docs/tasks/`，不得创建 `backend/`、`frontend/`、`sql/`、`docs/db_design.md`、`sql/init.sql`。

## Decisions

### TASK-PM-001

- sourceIssues: `DBA-TASK-REREVIEW-001`, `BACKEND-TASK-REREVIEW-001`
- owner: planner
- status: closed
- pmDecision: 接受。B3/B4 的并行表必须按真实依赖拓扑重写；任务文件中的可并行说明也要同步，不能出现同批次直接依赖或共享输出。
- actionRequired: 修改 `docs/task_plan.md` 依赖图、总任务矩阵、并行批次计划，并同步 `BE-002` 至 `BE-014` 相关任务文件。
- closeCondition: DBA/backend 复核通过。

### TASK-PM-002

- sourceIssues: `DBA-TASK-REREVIEW-002`
- owner: planner
- status: closed
- pmDecision: 接受。B4 和 B6 必须拆成严格拓扑批次，前端 `FE-008` 不能和依赖它的 `FE-009/FE-010` 同批启动。
- actionRequired: 修改 `docs/task_plan.md` B4/B6 和 `FE-008` 可并行说明。
- closeCondition: DBA/backend/frontend 复核通过。

### TASK-PM-003

- sourceIssues: `DBA-TASK-REREVIEW-003`
- owner: planner
- status: closed
- pmDecision: 接受。最终 `docs/db_design.md` 必须继承旧项目参考、迁移风险和 PM 决策，避免 DBA 只合并新表分片。
- actionRequired: 修改 `docs/task_plan.md` 的 DBA-005 行和 `docs/tasks/DBA-005-seed-index-constraint-design.md`。
- closeCondition: DBA 复核通过。

### TASK-PM-004

- sourceIssues: `BACKEND-TASK-REREVIEW-002`
- owner: planner
- status: closed
- pmDecision: 接受。`examine-generator` 是规范模块名；禁止 `examine-genger`。生成输出必须精确到各业务模块 `base/` 包和生成报告，不能写成笼统 `backend/`；OpenAPI 表使用 `un_openapi_` 前缀但生成到 `examine-app/base`。
- actionRequired: 修改 `docs/task_plan.md` GEN 行、`docs/tasks/GEN-002-generator-db-mapping-config.md`、`docs/tasks/GEN-004-generator-run-report.md`。
- closeCondition: backend 复核通过。

### TASK-PM-005

- sourceIssues: `BACKEND-TASK-REREVIEW-003`
- owner: planner
- status: closed
- pmDecision: 接受。`REV-001/002/003` 只能输出 review 分片，`REV-004` 唯一输出 `docs/review.json`。
- actionRequired: 修改 `docs/task_plan.md` reviewer 行和 `docs/tasks/REV-001-architecture-review.md`、`docs/tasks/REV-002-contract-implementation-review.md`、`docs/tasks/REV-003-quality-test-build-review.md`。
- closeCondition: backend/test/reviewer 后续复核通过。

## User Questions

PM 本轮可以决策，无需追加用户问题。

## Final Verification

- DBA: pass，见 `docs/issues/verification/task/dba_verification.md`。
- backend: pass，见 `docs/issues/verification/task/backend_verification.md`。
- frontend: pass，见 `docs/issues/verification/task/frontend_verification.md`。
- test: pass，见 `docs/issues/verification/task/test_verification.md`。
- conclusion: task plan frozen。
