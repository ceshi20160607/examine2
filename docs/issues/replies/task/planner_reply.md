# planner reply for task stage issues

## Reply Summary

planner 已按 PM 裁决修正任务拆分文档。本次只修改审阅模式允许的文档产物：`docs/task_plan.md` 与 `docs/tasks/`，未创建或修改 `backend/`、`frontend/`、`sql/`、`docs/db_design.md`、`sql/init.sql`。

## Issue Replies

### TASK-PM-001

- status: fixed
- action:
  - 重写 `docs/task_plan.md` B3，将 `BE-002` 与 `GEN-001` 作为 `BE-001` 后的可并行任务。
  - 明确 `BE-003`、`BE-014` 必须等待 `BE-002` 完成。
  - 同步 `docs/tasks/BE-001-backend-parent-module-skeleton.md`、`docs/tasks/BE-002-core-response-error-context.md`、`docs/tasks/GEN-001-generator-module-skeleton.md`。
- verificationTarget: dba, backend

### TASK-PM-002

- status: fixed
- action:
  - 重写 `docs/task_plan.md` B4，将后端链路调整为 `BE-004 -> BE-005 -> BE-006 -> BE-007 -> BE-008`，再进入 `BE-009/BE-010`、`BE-011/BE-012`、`BE-013`、`BE-015`。
  - 重写 `docs/task_plan.md` B6，将前端链路调整为 `FE-008` 完成后再并行 `FE-009/FE-010`。
  - 同步 `BE-004` 至 `BE-013`、`FE-008` 的可并行说明。
- verificationTarget: dba, backend, frontend

### TASK-PM-003

- status: fixed
- action:
  - `DBA-005` 输入增加 `docs/project_understanding.md`、`docs/legacy_reference.md`。
  - `DBA-005` 验收增加旧项目可参考表、迁移脚本、命名差异、不沿用原因和新旧结构差异说明。
- verificationTarget: dba

### TASK-PM-004

- status: fixed
- action:
  - `GEN-002` 输出精确到 `backend/examine-generator/src/main/resources/generator/table-module-map.yml`。
  - `GEN-004` 输出从宽泛 `backend/` 改为各模块 `base/` 包和 `backend/docs/mybatis-plus-generation.md`。
  - 明确 `examine-generator` 为规范模块名，禁止 `examine-genger`；OpenAPI 表使用 `un_openapi_` 前缀并生成到 `examine-app/base`。
- verificationTarget: backend

### TASK-PM-005

- status: fixed
- action:
  - `REV-001` 输出 `docs/review_parts/rev-001-architecture.md`。
  - `REV-002` 输出 `docs/review_parts/rev-002-contract.md`。
  - `REV-003` 输出 `docs/review_parts/rev-003-quality.md`。
  - `REV-004` 唯一输出 `docs/review.json`，并读取上述 review 分片。
- verificationTarget: backend, test

## Remaining Questions

PM 本轮无无法决策事项，`docs/issues/user_questions.md` 暂无新增问题。
