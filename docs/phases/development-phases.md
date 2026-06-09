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
| P1-generator | 生成器闭环期 | GEN-002、GEN-003、GEN-004 | 生成器能按表前缀和模块映射生成各业务模块 `base` 包，并输出生成报告；后端 compile 通过。 | accepted |
| P2-auth-platform | 认证与平台期 | BE-003、BE-004、FE-003/FE-004 联调补充、阶段测试验证 | 登录、刷新、退出、当前用户、我的系统、平台系统创建、平台账号角色核心闭环通过。 | accepted |
| P3-system-config | 系统配置与权限期 | BE-005、BE-006、BE-007、BE-014、FE-005/FE-006 联调补充 | 系统成员、部门、角色、权限、字典、应用/模块/字段/页面配置闭环，权限与数据范围基础可用。 | accepted |
| P4-runtime-mvp | 运行台 MVP 期 | BE-008、FE-008、阶段测试验证 | 动态 schema、记录列表/详情/保存/历史/提交审批入口按权限跑通。 | accepted |
| P5-workflow-files-openapi | 流程文件导出 OpenAPI 期 | BE-009 至 BE-013、FE-009 至 FE-011 联调补充 | 流程待办、附件、导出、OpenAPI、审计运维核心链路闭环。 | accepted |
| P6-final-acceptance | 后端集成验收与误判修正 | BE-015、FE-012、TEST-003 至 TEST-005、VAL-001 至 VAL-004、REV-001 至 REV-004 | 后端接口包可试部署；前端仅契约模型通过，不具备可部署 UI。原“全项目可上线”结论撤回。 | blocked(frontend-ui) |
| P7-frontend-ui-deploy | 前端真实 UI 与部署包期 | FE-013、TEST-006：前端工程入口、真实页面组件、路由挂载、API 调用闭环、浏览器 smoke/E2E、`dist/` 产物、前后端组合 E2E | 存在 `index.html`、`src/main.*`、真实页面和可部署 `dist/`；后端 jar + 前端 dist 完成组合 E2E。 | accepted |
| P8-platform-ui-crud | 平台中心可用化期 | FE-014：平台系统、平台账号、平台角色、平台配置真实 CRUD 页面和部署包刷新 | 平台中心不再是占位/调试页；列表、创建、编辑、状态、授权、配置更新入口均通过 typed PageModel 调用；前端 clean build 与浏览器 smoke 通过。 | accepted |
| P9-system-management-ui | 系统管理域可用化期 | FE-015、TEST-007、VAL-005、REV-005：成员、部门、系统角色、字典真实业务 UI，浏览器 E2E、clean build 和审查 | 成员、部门、系统角色、字典不再是通用占位页；主要 CRUD/授权/字典 usage 链路通过 typed SDK、真实系统上下文和浏览器 E2E。 | in_progress |

## 当前期

当前正在推进：P9 系统管理域可用化期。

原因：

- P1 生成器闭环已通过验收，base CRUD 已由 `examine-generator` 自动生成。
- P2 认证与平台期已通过验收，认证会话和平台中心 API 后端测试、clean compile 通过。
- P3 系统配置与权限期已通过验收，系统 RBAC、字典和模块配置闭环可用。
- P4 运行台 MVP 已通过验收，记录运行态 CRUD 与前端页面模型可作为流程、文件、导出和 OpenAPI 的入口基础。
- P5 已通过 PM 阶段验收，验收记录为 `docs/phases/P5-workflow-files-openapi-acceptance.md`。
- P6 后端接口包可试部署，但 PM/validator/reviewer 曾误把前端 typed contract 当成可部署前端，验收结论已撤回并修正为 `blocked(frontend-ui)`。
- FE-013 已补真实浏览器前端工程、页面组件、生产构建和 `dist/` 部署产物。
- TEST-006 已通过，后端 jar + 前端 dist 的组合 E2E 已完成。
- P7 验收记录为 `docs/phases/P7-frontend-ui-deploy-acceptance.md`。
- P8 已完成平台中心真实 CRUD 页面，验收记录为 `docs/phases/P8-platform-ui-crud-acceptance.md`。
- P9 已按 `docs/process/development_governance.md` 完成多角色只读审查和 PM 裁决，进入成员、部门、系统角色、字典真实 UI 实现前置阶段。

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
