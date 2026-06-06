# task stage raw issues for planner

## Source Reviews

- `docs/understanding/dba_task_rereview.md`
- `docs/understanding/backend_task_rereview.md`
- `docs/understanding/frontend_task_rereview.md`
- `docs/understanding/test_task_rereview.md`

## Open Raw Issues

### DBA-TASK-REREVIEW-001

- stage: task_plan
- raisedBy: dba
- owner: planner
- status: open
- problem: B3 并行批次把 `BE-002`、`BE-003`、`BE-014` 放在同批，但 `BE-003/BE-014` 依赖 `BE-002`，且 `BE-002/BE-014` 都涉及 `backend/examine-core/`。
- impact: 会误导并行调度，造成依赖未满足或共享模块输出竞争。
- suggestion: 拆成 `BE-001 -> BE-002/GEN-001 -> BE-003/BE-014`。
- closeCondition: 批次、依赖图、任务矩阵和任务文件一致。

### DBA-TASK-REREVIEW-002

- stage: task_plan
- raisedBy: dba
- owner: planner
- status: open
- problem: B4、B6 仍将存在直接依赖关系的任务描述为可并行。
- impact: 会诱导提前并行，造成后续实现返工。
- suggestion: B4 拆成 `BE-008 -> BE-009/BE-010 -> BE-011/BE-012 -> BE-013`；B6 拆成 `FE-008 -> FE-009/FE-010`。
- closeCondition: 并行批次表不再列入互相依赖的任务。

### DBA-TASK-REREVIEW-003

- stage: task_plan
- raisedBy: dba
- owner: planner
- status: open
- problem: `DBA-005` 未显式要求读取 `docs/project_understanding.md`、`docs/legacy_reference.md` 并沉淀旧项目差异和迁移风险。
- impact: 最终 DB 设计可能遗漏旧项目参考关系，影响 SQL 和代码生成质量。
- suggestion: 在 `DBA-005` 输入和验收中补齐旧项目参考、迁移风险和 PM 决策要求。
- closeCondition: `DBA-005` 任务文件和总任务矩阵同步补齐。

### BACKEND-TASK-REREVIEW-001

- stage: task_plan
- raisedBy: backend
- owner: planner
- status: open
- problem: 后端并行批次与任务依赖不一致，B3/B4 把有前置依赖的 BE 任务放入同一批次。
- impact: 可能诱导提前启动后端业务任务，造成未满足前置能力、同模块目录并发写入或编译不可用。
- suggestion: 重写 B3/B4 为严格拓扑批次。
- closeCondition: 并行批次、Mermaid 图、BE 任务文件依赖和可并行描述完全一致。

### BACKEND-TASK-REREVIEW-002

- stage: task_plan
- raisedBy: backend
- owner: planner
- status: open
- problem: `GEN-004` 输出路径过宽，写成 `backend/`，未锁定 base 层生成目标路径。
- impact: 无法证明生成任务与 manage 任务输出不重叠。
- suggestion: 明确表前缀到模块 `base/` 包路径映射，`GEN-004` 只输出具体 base 路径和生成报告。
- closeCondition: `GEN-002/GEN-004` 与总任务矩阵均明确 package/路径映射，并明确 OpenAPI 表使用 `un_openapi_` 但生成到 `examine-app/base`。

### BACKEND-TASK-REREVIEW-003

- stage: task_plan
- raisedBy: backend
- owner: planner
- status: open
- problem: `REV-001/002/003` 输出含 `docs/review.json`，与 `REV-004` 最终汇总职责冲突。
- impact: 中间审查任务可能覆盖最终 review 总报告。
- suggestion: `REV-001/002/003` 改为输出 `docs/review_parts/*.md`，只允许 `REV-004` 写 `docs/review.json`。
- closeCondition: `REV-001/002/003` 不再声明 `docs/review.json`。
