# DBA 任务拆分审查

## 审查结论

fail。

DBA-001 至 DBA-006 的业务覆盖基本充分，能够覆盖表域映射、平台/RBAC/字典、动态模块/运行/导出、流程/上传/OpenAPI/审计、seed/索引/约束、`init.sql` 与迁移检查。但当前任务计划把 DBA-002、DBA-003、DBA-004 标记为可并行，同时三者输出同一个 `docs/db_design.md`，与任务并行要求中的“输出路径不重叠”冲突。该问题会影响后续 DB 设计执行方式和产物合并口径，DBA 角度不建议冻结任务计划。

## 审查范围

本次只基于以下允许输入审查，未读取旧项目目录，未生成 SQL，未修改 `docs/task_plan.md`、`docs/tasks/`、`docs/db_design.md`、`sql/`、`backend/` 或 `frontend/`。

- `docs/task_plan.md`
- `docs/tasks/`
- `docs/api.md`
- `docs/api_review.md`
- `docs/prd.md`
- `docs/project_understanding.md`
- `docs/service_info.md`
- `.codex/state.json`

关键依据：

- `.codex/state.json` 显示 `api_frozen=true`，当前处于任务拆分阶段。
- `docs/api_review.md` 显示 DBA、backend、frontend、test 均已 pass，API 冻结版本为 `api-frozen-v1-2026-06-06`。
- `docs/task_plan.md` 中 DBA 依赖链为 `DBA-001 -> DBA-002/003/004 -> DBA-005 -> DBA-006`，整体方向合理。
- DBA 小任务明确要求不直接读取 `.codex/oldexamine/`，只通过冻结文档和旧项目摘要承接历史参考。

## 阻塞 issue 表

| issueId | 问题 | 影响 | 建议责任方 | 建议修改点 | 是否阻塞任务冻结 |
| --- | --- | --- | --- | --- | --- |
| DBA-TASK-001 | `DBA-002`、`DBA-003`、`DBA-004` 均标记“可并行”，但三者输出均为 `docs/db_design.md`。`docs/task_plan.md` 的 B2 批次也声明三者可并行，同时又说明需要合并同一文件。 | 并行执行时存在覆盖、交叉改写或合并口径不清风险；后续 DBA 设计可能无法判断每个子任务是否独立完成，也违反“可并行任务输出路径不重叠”的调度前提。 | planner | 二选一：1. 将 `DBA-002`、`DBA-003`、`DBA-004` 改为不可并行，由 DBA 顺序写入同一 `docs/db_design.md`；2. 为三者设置不重叠的中间输出片段，并新增显式合并任务写入最终 `docs/db_design.md`。 | 是 |

## 非阻塞跟踪项

- `DBA-006` 写了“记录迁移检查结果、失败原因和修复建议”，但输出路径只列 `sql/init.sql`、`docs/db_design.md`。建议明确迁移检查记录写入 `docs/db_design.md` 的设计说明或初始化数据/迁移检查章节，避免后续 DBA 误建额外文件。
- `DBA-004` 已覆盖审计运维表，但验收标准主要写 OpenAPI 和文件权限。建议在验收标准中显式补充 `un_sys_`/`un_audit_` 表域，确保系统日志、审计日志、健康检查归属不会被弱化。
- `DBA-002` 提到 OpenAPI scope 授权表，`DBA-004` 也设计 OpenAPI scope、客户端、凭证等表。建议 DBA-001 的表域映射阶段明确 scope catalog、scope 授权关系和系统内权限之间的归属，避免 `un_plat_`/`un_openapi_` 重复建模。
- DBA 任务引用 `docs/legacy_reference.md` 作为历史摘要输入是合理的，但后续执行仍必须保持“不直接扫描旧项目目录”的限制，只能使用冻结文档和摘要产物。

## 是否允许任务计划冻结

DBA 角度结论：不允许冻结。

需要先修复 `DBA-TASK-001`，明确 DBA-002、DBA-003、DBA-004 是顺序写同一设计文档，还是并行产出独立片段后再合并。修复后，DBA-001 至 DBA-006 的粒度和依赖可以支撑后续 `docs/db_design.md` 与 `sql/init.sql` 阶段。
