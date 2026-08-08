# MEETING-S1-001：需求理解专业会审

## 1. 身份

- node_id: `S1-REQUIREMENT-UNDERSTANDING`
- organized_by: `pm`
- date: `2026-07-10`
- participants: `product, analyst, uiux, architect, dba, backend, frontend, test, ops`
- node_acceptor: `leader`
- status: `completed`
- reviewed_output: `.cursor/session/rebuild/requirement-understanding.md`

## 2. 会审目标

判断当前需求包是否已经把粗糙 docs 转化为足以进入 S2 的产品和工程输入，重点检查：最终目标是否被缩减、角色入口是否闭环、需求是否遗漏、旧原型是否错误主导需求、下游是否需要猜测关键行为。

本会议不冻结 UI、架构、数据库、API 或技术版本，也不允许启动编码。

## 3. 各角色独立意见

| role | review verdict | 主要检查与结论 | S2 强制输入/风险 |
|---|---|---|---|
| product | pass | 最终目标明确为全量可用产品，不是 MVP；15 个范围域、12 类角色和 50+ 角色旅程覆盖主需求；“应用/模块组”旧冲突已按附录修正 | 设计不能以阶段切片删除最终范围；范围变更需用户决定 |
| analyst | pass | SRC-001 主来源和派生流程/原型的降级权威性明确；需求均能回指章节/流程；旧来源链缺失已记录但不阻断 | S2 每项契约继续保留 REQ/JRN 引用，不引用已删除旧文件 |
| uiux | pass_to_design | 已明确四套壳、真实入口、主动作、状态、移动使用和命令/搜索效率入口；原型被正确限定为粗略参考 | S2 重新形成 IA/交互/状态和可评审原型；禁止复制四分五裂、default/generic 或假成功结构 |
| architect | pass_to_design | 身份上下文、模块/应用边界、配置版本、流程快照、后台任务、SecretRef、event 和非功能要求足以进入架构设计 | S2 必须产出上下文、模块、权限、数据、流程、任务、集成、故障和部署契约 |
| dba | pass_to_design | 动态字段、关联、子表、快照、版本、隔离、审计、索引值、迁移和 generator 边界均已提出 | S2 先设计数据域/schema/迁移/生成契约，禁止直接生成 base 或创建 SQL |
| backend | pass_to_design | 业务保存、权限、事务、状态、幂等、异步、补偿、消息/待办/日志和读回要求明确 | S2 冻结跨层契约；generated base 与 handwritten behavior 必须逐域列清 |
| frontend | pass_to_design | 四套壳、动态 schema、列表/表单/详情、完整状态、真实 API、权限和移动边界明确 | S2 确定组件/表格/画布选型及 UI contract；静态原型不能转为代码任务 |
| test | pass_to_design | 需求具备角色、入口、业务结果、权限负例、数据读回、失败和发布边界；工程证据与用户签字分离 | S2 建立 goal/journey/task/evidence 分层测试契约和用户验收边界 |
| ops | pass_to_design | 多环境、配置、SecretRef、体检、可观测、备份恢复、发布回滚和文档要求已纳入 | S2 设计目标交付形式、制品、迁移、健康、回滚和运行验收，不得最后补运维 |

## 4. 会审中补回的遗漏

| requirement | 发现角色 | 修正 |
|---|---|---|
| `REQ-SHELL-001` 四套角色壳 | uiux/frontend/test | 已加入需求总账和 `RULE-018` |
| `REQ-EFFICIENCY-001` 命令中心与全局效率入口 | product/uiux | 已加入需求总账并映射 JRN-B11 |
| `REQ-DATA-MODEL-001` 类型化动态数据模型 | architect/dba/backend | 已加入 S2 数据设计输入 |
| `REQ-EVENT-001` 本地事件扩展边界 | architect/backend/ops | 已加入 S2 架构输入 |
| `REQ-FLAG-001` 功能开关与灰度 | architect/ops/test | 已加入发布和运维需求 |

## 5. PM 集成决定

1. 当前最终范围保持全量，不将“实施顺序”解释为 MVP 截断。
2. 以 `docs/user_requirement.md` 为主需求；附录 A 解决旧正文冲突；流程/人读 HTML/原型只做覆盖和设计参考。
3. 需求节点不要求用户回答普通技术选型。组件库、数据库详细模型、移动交付形式、任务机制、存储和 provider 顺序进入 S2 工程会审。
4. S2 必须同时完成产品/UI、架构、数据库、API/权限/状态、测试和发布设计，不能只画原型或只定后端结构。
5. 当前所有业务代码、SQL、release 资产继续阻断。
6. 当前没有活动 P0/P1 产品问题，也没有必须立即升级用户的待决项。

## 6. 行动与复核

| action | owner | output | verifier | status |
|---|---|---|---|---|
| 将专业 review 和补回需求写回主产出 | product + analyst | `requirement-understanding.md` | pm | done |
| 更新 S1 Gate 和 package 状态 | product + pm | `requirement-understanding.md` | leader | ready_for_review |
| 执行 Leader 一致性验收 | leader | `current-node.md` + `state.json` | framework evidence | pending |
| 验收通过后规划 S2 设计节点 | pm + planner | 后续 current-node | leader | pending |

## 7. 升级

- needs_leader: `yes`，需要 Leader 验收 S1。
- needs_user: `no`，当前无用户专属阻断决定。
- pending_user_decision_path: `.cursor/session/pending-user-decisions.md`（当前无活动项）。
