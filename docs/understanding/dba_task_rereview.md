# DBA task rereview

- status: fail

## Issues

### DBA-TASK-REREVIEW-001

- owner: planner
- problem: `docs/task_plan.md` 的 B3 并行批次把 `BE-002`、`BE-003`、`BE-014` 列为可并行，但 `BE-003`、`BE-014` 都依赖 `BE-002`；同时 `BE-002` 与 `BE-014` 都输出 `backend/examine-core/`，存在共享输出路径竞争。
- impact: 调度器可能并行写同一模块，导致 core 公共能力、权限拦截器和认证基础代码互相覆盖或依赖未满足。
- suggestion: 将 B3 拆为 `BE-002` 先执行；`BE-002` 完成后再并行 `BE-003`、`BE-014`、`GEN-001`，并在任务计划中明确输出路径不重叠。
- closeCondition: B3 并行批次、依赖图、总任务矩阵和对应任务文件的依赖/可并行说明一致，且不存在同批次共享输出路径。

### DBA-TASK-REREVIEW-002

- owner: planner
- problem: B4、B6 并行批次仍把存在直接依赖关系的任务放在同一可并行描述中：`BE-011` 依赖 `BE-010`，`BE-012` 依赖 `BE-009/BE-010`，`BE-013` 依赖 `BE-012`；`FE-009/FE-010` 依赖 `FE-008`，但 B6 写成 `FE-008/FE-009/FE-010` 可在 schema 后并行。
- impact: 批次表会误导 Orchestrator 或执行 agent 提前并行，造成依赖未完成、共享能力未就绪和返工。
- suggestion: 按真实拓扑拆分批次，例如 `BE-008 -> BE-009/BE-010 -> BE-011/BE-012 -> BE-013`；`FE-008 -> FE-009/FE-010`。
- closeCondition: 并行批次表不再列入互相依赖的任务；依赖图、总任务矩阵和任务文件保持一致。

### DBA-TASK-REREVIEW-003

- owner: planner
- problem: `DBA-005` 是最终写入 `docs/db_design.md` 的任务，但任务输入和验收没有显式要求复核 `docs/project_understanding.md`、`docs/legacy_reference.md` 中的旧表差异、迁移风险和 PM 决策。
- impact: 后续 DB 总设计可能只合并分片和 seed/索引，遗漏与旧项目表结构和迁移脚本的参考关系、差异说明，影响 `init.sql` 和代码生成映射质量。
- suggestion: 在 `DBA-005` 输入和验收中补充 `docs/project_understanding.md`、`docs/legacy_reference.md`，并明确最终 `docs/db_design.md` 必须包含旧项目参考关系、差异说明、历史/迁移注意事项。
- closeCondition: `DBA-005` 任务文件和总任务矩阵同步补齐上述输入与验收项。

## Prefix Check

表前缀本身已按冻结契约保持为 `un_plat_`、`un_module_`、`un_flow_`、`un_upload_`、`un_openapi_`、`un_sys_`/`un_audit_`；未发现 `un_platt_`，也未发现把 OpenAPI 表设计为 `un_app_` 的任务要求。
