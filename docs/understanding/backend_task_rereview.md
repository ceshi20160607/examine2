# Backend task rereview

- status: fail

## Issues

### BACKEND-TASK-REREVIEW-001

- owner: planner
- problem: 后端并行批次描述与任务依赖不一致。`docs/task_plan.md` B3 将 `BE-002`、`BE-003`、`BE-014`、`GEN-001` 列为可并行，但 `BE-003/BE-014` 依赖 `BE-002`；B4 又描述 `BE-004`、`BE-006`、`BE-007` 的准备可并行，但 `BE-006` 依赖 `BE-005`，`BE-005` 依赖 `BE-004`，`BE-007` 依赖 `BE-006`。
- impact: 可能诱导 Orchestrator 提前启动后端业务任务，造成未满足前置能力、同模块目录并发写入或编译不可用。
- suggestion: 将 B3/B4 拆成严格依赖批次，例如 `BE-001` 后仅 `BE-002` 与 `GEN-001` 可并行，`BE-002` 后再启动 `BE-003/BE-014`；`BE-004 -> BE-005 -> BE-006 -> BE-007` 串行或只允许明确不落盘的设计准备。
- closeCondition: `docs/task_plan.md` 并行批次、Mermaid 图、各 BE 任务文件的依赖和可并行描述完全一致，且不再把有前置依赖的任务列入同一可执行批次。

### BACKEND-TASK-REREVIEW-002

- owner: planner
- problem: `GEN-004` 输出路径仍过宽，写成 `backend/`，没有在任务文件中逐项锁定 MyBatis-Plus 生成产物的 base 层目标路径。
- impact: 无法证明 `GEN-004` 与后端 manage 任务输出不重叠，也容易让实现阶段把生成代码写入 `examine-web` 或业务 `manage` 包。
- suggestion: 在 `GEN-002/GEN-004` 和总任务矩阵中明确 `un_plat_`、`un_module_`、`un_flow_`、`un_upload_`、`un_openapi_` 分别生成到对应模块的 `base/` 包；`GEN-004` 输出不再笼统写 `backend/`，只写具体 base 路径和 `backend/docs/mybatis-plus-generation.md`。
- closeCondition: 生成器任务文件明确 `examine-generator` 职责、表前缀映射、package 映射、具体输出路径，并说明 `un_openapi_` 落到 `examine-app`，不使用 `examine-genger`，不把 OpenAPI 表错归到 `un_app_`。

### BACKEND-TASK-REREVIEW-003

- owner: planner
- problem: `REV-001`、`REV-002`、`REV-003` 的输出都包含 `docs/review.json`，但 `REV-004` 才是最终 `review.json` 汇总任务。
- impact: 共享总报告可能被中间审查任务提前写入或覆盖，违反共享总报告由串行汇总任务生成的约束。
- suggestion: 将 `REV-001` 至 `REV-003` 输出改为互不重叠的审查分片路径，例如 `docs/review_parts/rev-001-architecture.md`、`rev-002-contract.md`、`rev-003-quality.md`；只允许 `REV-004` 写 `docs/review.json`。
- closeCondition: `REV-001/002/003` 不再声明输出 `docs/review.json`，`REV-004` 唯一负责生成合法 JSON 总结论。
