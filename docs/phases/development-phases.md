# 开发分期计划

本文档由 `planner` 维护分期结构，由 `pm` 维护每期验收结论，由 Orchestrator 根据当前期调度 agent 并同步 `.codex/state.json` 与 `docs/progress.md`。

## 角色分工

| 角色 | 职责 |
| --- | --- |
| planner | 将 `docs/task_plan.md` 与 `docs/tasks/` 的小任务归入期次，维护依赖、并行条件和退出标准。 |
| pm | 确认每期业务目标、验收口径、满意度结论，以及是否进入下一期。 |
| Orchestrator | 只调度当前期任务，维护状态、关闭/恢复 agent、验证产物和更新进度看板。 |
| backend/frontend/dba/test/validator/reviewer | 只执行当前分配任务；跨期或契约冲突必须回报 PM/Planner。 |

## 分期总览

| 期次 | 名称 | 任务范围 | 退出标准 | 状态 |
| --- | --- | --- | --- | --- |
| P0-foundation | 基础冻结与骨架期 | DBA-001 至 DBA-006、TEST-001 至 TEST-002、FE-001 至 FE-007、FE-011、BE-001 至 BE-002、GEN-001 | DB/SQL、后端骨架、core、生成器骨架、前端 SDK/Layout/基础页面和测试计划完成，相关自检通过。 | done |
| P1-generator | 生成器闭环期 | GEN-002、GEN-003、GEN-004 | 生成器能按表前缀和模块映射生成各业务模块 `base` 包，并输出生成报告；后端 compile 通过。 | pending |
| P2-auth-platform | 认证与平台期 | BE-003、BE-004、FE-003/FE-004 联调补充、阶段测试验证 | 登录、刷新、退出、当前用户、我的系统、平台系统创建、平台账号角色核心闭环通过。 | pending |
| P3-system-config | 系统配置与权限期 | BE-005、BE-006、BE-007、BE-014、FE-005/FE-006 联调补充 | 系统成员、部门、角色、权限、字典、应用/模块/字段/页面配置闭环，权限与数据范围基础可用。 | pending |
| P4-runtime-mvp | 运行台 MVP 期 | BE-008、FE-008、阶段测试验证 | 动态 schema、记录列表/详情/保存/历史/提交审批入口按权限跑通。 | pending |
| P5-workflow-files-openapi | 流程文件导出 OpenAPI 期 | BE-009 至 BE-013、FE-009 至 FE-011 联调补充 | 流程待办、附件、导出、OpenAPI、审计运维核心链路闭环。 | pending |
| P6-final-acceptance | 集成验收与上线判断期 | BE-015、FE-012、TEST-003 至 TEST-005、VAL-001 至 VAL-004、REV-001 至 REV-004 | 后端自检、前端契约闭环、E2E、clean build、review 全部通过，输出可上线判断。 | pending |

## 当前期

当前推荐进入：`P1-generator`。

原因：

- P0 已完成 19 个开发执行任务，并完成 SQL、后端骨架、core、生成器骨架、前端 SDK/Layout/基础页面。
- 后续后端业务任务依赖 `GEN-004` 生成的各模块 `base` 包；先完成生成器可以避免手写大批量 CRUD。
- P1 范围小，适合作为分期机制调整后的第一期验收样本。

## 暂停与继续

暂停时：

1. Orchestrator 关闭所有 running agent。
2. 未完成任务保持 `pending` 或标记为 `partial`，不得记为 `done`。
3. 更新 `.codex/state.json.active_mode = "paused"`、`current_phase`、`running_tasks`、`paused_at`。
4. 更新 `docs/progress.md` 的 agent 状态和下一步。

继续时：

1. 读取 `.codex/state.json`、`docs/progress.md` 和本文档。
2. 复核当前期是否有 `partial` 产物。
3. 从当前期第一个 `pending/partial` 任务继续。
4. 当前期未通过 PM 验收前，不启动下一期任务。
