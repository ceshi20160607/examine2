# Examine2 分期交付路线图

## 1. 最终目标

依据当前项目 `docs` 的完整需求和 `.cursor` 工程实例，按已验收设计逐节点实现、测试、发布一个普通人可实际使用的完整管理系统，最终提交用户整体验收；不得以框架、文档、原型、生成代码或局部切片冒充完成。

## 2. 整体慢的诊断

VS1、VS2、VS3 和原 VS4 都按身份、权限、配置、运行时等技术横层串行推进，并默认对每层使用完整合同、多人复审和全套证据。单层虽有工程价值，但普通成员的完整业务结果被推迟到 VS4；原 VS4 又把 49 字段、20 表、42 接口集中成一个大合同，反馈周期再次拉长。

此前的纠偏仍不够：P4-C4 后端功能 157 分钟完成、前端非浏览器功能约 48 分钟完成，却因重复响应式截图、重启证据、墙钟 target 和状态文档继续停滞。当前执行模型改为：

- Phase 只表达宏观产品阶段，不受 4 小时时限限制。
- 功能批次目标 240 分钟，task 数量由工程模块 DAG 决定，不固定为 2..3 个。
- Coding Task 在单一 `module_scope/write_scope` 内交付一个结果，目标估算 10..120 分钟，推荐 45..90 分钟。
- 项目基线最长 240 分钟；后续需求和设计按切片深化，不一次冻结全部远期细节。
- 不同 Maven/feature 模块、无依赖且写集不重叠时默认并行；阶段 Gate 只限制完成声明，不阻止独立后续模块提前开发。
- routine task 只验证受影响模块；slice 验证一次真实入口和跨层读回；phase/release 再运行全量回归、重启和浏览器矩阵。
- 前端功能先在参考桌面视口可操作；移动端、关键断点、视觉、无障碍和全状态截图统一进入 `P10-H1-UI-HARDENING`。
- 墙钟 target 过期只告警；active time 超过 120/240 分钟才记录一次偏差和调度调整。
- 任何 slice 都必须同时写明 `delivers`、`doesNotDeliver`、`remainingGoalItems`、`deferredTo` 和 `acceptanceDemo`。

## 3. 阶段边界

| phase | delivers | doesNotDeliver | remainingGoalItems | deferredTo | acceptanceDemo |
|---|---|---|---|---|---|
| P0 工程理解基线 | docs 事实、最终目标、角色旅程、模块边界、风险、分期 | 详细远期设计、代码、发布 | P1-P10 | P1 | 人能从主包看懂目标、边界、当前切片和下一结果 |
| P1 设计与工程基础 | 可运行骨架、模块/POM、DB/API 基线、generator、首批 base | 完整业务功能、用户验收 | P2-P10 | P2 | 空库构建启动、健康、首批生成结果和工程入口 |
| P2 登录与运行骨架 | 注册/登录、session/context、系统切换、应用壳、健康 | 组织治理、配置、动态业务 | P3-P10 | P3 | 用户登录、切换上下文、退出并重启读回 |
| P3 平台与自建系统 | 平台治理、组织权限、自建系统、模块/字段配置发布 | 动态记录、Flow、工作、集成、AI | P4-P10 | P4 | 管理员配置发布，普通成员只见授权入口 |
| P4 动态业务运行 | 列表、创建、编辑、详情、生命周期、查询、字段、协作、效率 | Flow、文件交换、报表、OpenAPI、AI | P5-P10 | P5 | 普通成员使用已发布模块完成真实记录工作 |
| P5 Flow 与审批 | 流程设计/发布、实例、审批、历史 | 工作项目、文件交换、报表、外部集成、AI | P6-P10 | P6 | 管理员发布流程，成员发起并完成审批 |
| P6 工作/任务/待办/消息 | 任务、待办、提醒、消息、工作台统计 | 文件交换、报表、外部集成、AI | P7-P10 | P7 | 成员从行动中心完成任务闭环 |
| P7 文件与数据交换 | 附件、导入、导出、打印 | 报表、外部集成、AI | P8-P10 | P8 | 记录带文件并完成批量交换和打印 |
| P8 仪表盘与报表 | 真实统计、图表、报表、权限过滤和钻取 | OpenAPI、AI、最终发布 | P9-P10 | P9 | 管理者按权限查看并钻取真实业务数据 |
| P9 OpenAPI 与集成 | 应用、凭据、scope、回调、限流、调用日志 | AI、整体验收 | P10 | P10 | 外部应用按授权调用并按 trace 审计 |
| P10 AI、统一 hardening 与整体验收 | AI 查询/确认写入、全站响应式/可访问性/视觉收口、全回归、发布、用户验收 | 无；未验收项保持 OPEN | FINAL | FINAL | 干净环境交付完整系统并由用户整体验收 |

阶段状态不在本文件维护，只读取 `.cursor/session/state.json`。

## 4. P2/P3 可见性恢复

P0-P3 的代码和证据不删除，也不从头实现。增加一次组合验证切片，专门修复“用户只看到登录”的展示缺口，不把它变成 P4 coding 的长期前置设计。

- slice_id: `P2-P3-COMBINED-DEMO`
- delivers: 登录 -> 平台/系统治理 -> 模块配置发布 -> 普通成员授权入口的组合演示。
- doesNotDeliver: 动态记录 CRUD、Flow、工作、文件、报表、OpenAPI、AI。
- remainingGoalItems: `P4-A1..P10`。
- deferredTo: `P4-A1`。
- acceptanceDemo: `http://127.0.0.1:5173` 的真实 API/DB 浏览器旅程、桌面/移动边界和后端重启读回。
- status_ref: `state.deliveryHistory[P2-P3-COMBINED-DEMO]`。

| task | kind/risk | estimate | singleOutcome |
|---|---|---:|---|
| P23-D-01 | verification/routine | 180m | 复现登录、上下文、平台/系统治理、配置发布的 API/DB/重启基线 |
| P23-D-02 | verification/standard | 180m | 复现管理员发布到普通成员授权入口的桌面和移动浏览器旅程并汇总单一证据 |

## 5. P4 动态业务切片

原 VS4 机器合同保留为长期兼容目录和风险清单，不再作为单个 coding 前置节点。每个 slice 只冻结并实现其真实用户结果需要的子集。

### P4-A1 已发布记录只读链路

- delivers: 普通成员从已发布模块进入真实记录列表并打开详情；越权 direct-id 返回拒绝，重启后仍可读。
- doesNotDeliver: 创建、编辑、激活、查询视图、全部字段、协作。
- remainingGoalItems: `P4-A2`、`P4-B1..B3`、`P4-C1..C6`、`P4-D1..D4`、`P5-P10`。
- deferredTo: `P4-A2`。
- acceptanceDemo: 模块入口 -> 真实列表 -> 详情 -> 越权拒绝 -> 后端重启读回。

| task | kind/risk | estimate | singleOutcome |
|---|---|---:|---|
| P4-A1-01 | enabler/critical | 240m | 冻结只读 schema/query/detail 子合同，完成当前表子集 Flyway 与 generator base |
| P4-A1-02 | implementation/standard | 240m | 交付有权限过滤的 record list/detail HTTP 与 MySQL 读回 |
| P4-A1-03 | implementation/standard | 240m | 交付普通成员真实列表/详情 UI 与失败状态；Test 独立复现 |

### P4-A2 创建和激活链路

- delivers: 普通成员创建基础字段草稿、激活并在列表/详情读回；校验和越权失败可理解。
- doesNotDeliver: 编辑/autosave、归档/回收、复杂查询、其余字段。
- remainingGoalItems: `P4-B1..B3`、`P4-C1..C6`、`P4-D1..D4`、`P5-P10`。
- deferredTo: `P4-B1`。
- acceptanceDemo: 列表 -> 创建草稿 -> 激活 -> 详情 -> 刷新/重启读回 -> 越权拒绝。

| task | kind/risk | estimate | singleOutcome |
|---|---|---:|---|
| P4-A2-01 | implementation/standard | 240m | 交付 create-draft command、基础字段校验、权限和 DB 读回 |
| P4-A2-02 | implementation/standard | 240m | 交付 activate command、幂等/非法状态拒绝和读回 |
| P4-A2-03 | implementation/standard | 240m | 交付创建到激活的真实表单旅程；Test 独立复现桌面/移动 |

### P4-B 编辑、生命周期与查询

| slice | delivers | doesNotDeliver | remainingGoalItems | deferredTo | acceptanceDemo |
|---|---|---|---|---|---|
| P4-B1 | 编辑、autosave、草稿恢复、CAS 冲突反馈 | 归档/回收、保存视图、复杂字段 | P4-B2..P10 | P4-B2 | 编辑后刷新读回、冲突恢复和失败反馈 |
| P4-B2 | archive/trash/restore 和非法状态拒绝 | 查询视图、复杂字段、协作 | P4-B3..P10 | P4-B3 | 生命周期动作、权限负例和重启读回 |
| P4-B3 | filter/sort/search/page/saved view | 字段包、协作、后续阶段 | P4-C1..P10 | P4-C1 | 过滤排序搜索、保存视图和不同 scope total |

每个 P4-B slice 进入执行时只建立 2..3 个单目标 task，不预先把整个 B 范围打成一个任务。

### P4-C canonical 字段期

下面 49 个类型直接来自 `vs4-field-contract.json`，每个 canonical type 恰好出现一次。产品显示名只能作为 alias，不能替换合同名。

<!-- field_period_map:start -->
| slice | canonicalTypes | delivers | doesNotDeliver | deferredTo | acceptanceDemo |
|---|---|---|---|---|---|
| P4-A2 | `TEXT` `TEXTAREA` `NUMBER` `DATE` `DATETIME` `RADIO` `MEMBER` `DEPARTMENT` | 首批基础录入和读回 | 其余字段 | P4-C1 | 创建/激活/重启字段读回 |
| P4-C1 | `PERCENT` `MONEY` `DATE_RANGE` `TIME` `TIME_RANGE` `MULTI_SELECT` `CASCADE` `SWITCH` `RATING` `PROGRESS` `TAG` | 数值、时间、选择字段真实录入/查询 | 联系、关系、派生、文件 | P4-C2 | 参数化正反例、查询和浏览器读回 |
| P4-C2 | `PHONE` `EMAIL` `URL` `IDENTITY` `ADDRESS` `GEO` `RICH_TEXT` `JSON` `SECRET` `STATUS` `BARCODE` | 联系、敏感和结构化字段真实行为 | 关系、派生、文件 | P4-C3 | 校验、脱敏、结构查询和读回 |
| P4-C3 | `RELATION` `REFERENCE` `SUBTABLE` | 关系、引用和子表工作区 | 派生、文件 | P4-C4 | 关联/子表增删、权限和聚合源读回 |
| P4-C4 | `FORMULA` `SUMMARY` `CALCULATED` `LOOKUP` `AGGREGATE` | 派生计算、依赖重算和只读展示 | 系统字段、文件 | P4-C5 | 源值变化后重算、索引和失败证据 |
| P4-C5 | `TENANT` `AUTO_NUMBER` `CREATED_BY` `CREATED_AT` `UPDATED_BY` `UPDATED_AT` | 系统生成字段和租户边界 | 文件/AI 延期类型 | P4-C6 | 创建更新后系统字段和越权读回 |
| P4-C6 | `ATTACHMENT` `IMAGE` `FILE_GROUP` `SIGNATURE` `AI_FILL` | 只验证发布边界和无假控件 | 文件实际行为、AI 实际行为 | P7/P10 | 配置检查阻断未实现能力，运行态不注册假入口 |
<!-- field_period_map:end -->

P4-C6 中 `ATTACHMENT/IMAGE/FILE_GROUP/SIGNATURE` 的实现归 P7，`AI_FILL` 的实现归 P10；在目标阶段前保持 deferred，不返回假 501、不显示假按钮。

### P4-D 协作与效率

| slice | delivers | doesNotDeliver | remainingGoalItems | deferredTo | acceptanceDemo |
|---|---|---|---|---|---|
| P4-D1 | 详情 relation/subtable/team | 评论、批量、效率入口 | P4-D2..P10 | P4-D2 | 详情维护关系/子表/团队并权限读回 |
| P4-D2 | comment/history/前后记录 | 批量、效率入口 | P4-D3..P10 | P4-D3 | 评论、脱敏历史和相邻记录导航 |
| P4-D3 | 批量编辑/转交/归档/回收 | 收藏、最近、全局搜索 | P4-D4..P10 | P4-D4 | 跨页选择、原子失败和重启读回 |
| P4-D4 | 收藏、最近、全局搜索、我的草稿 | P5-P10 | P5-P10 | P5 | 用户从效率入口找到并继续真实记录工作 |

P4 只有 A1/A2/B1-B3/C1-C6/D1-D4 全部按各自边界验收后才能进入阶段验收；任一 slice 通过都不能单独声明 P4 完成。

## 6. P5-P10 执行规则

后续阶段按现有工程模块和用户结果滚动规划。数据、API、页面、生成代码或模块壳可以作为短 task，但不能冒充 slice/phase 完成。消费者只等待所需契约 artifact accepted，不等待整个上游 task passed。

coding task 目标估算 10..120 分钟；无依赖且写集不重叠的模块默认并行。跨阶段的 Flow、Work、Event、File 等独立模块可以提前开发，但阶段验收仍按 P4→P10 顺序声明。

前端新功能进入 `frontend/src/features/<domain>`，不再持续堆入 `SystemWorkbenchView.vue`。响应式和视觉矩阵统一由 P10-H1 收口。

P5-P10 每个新 slice 建立时必须从第 3 节对应阶段复制并收紧 `delivers/doesNotDeliver/remainingGoalItems/deferredTo/acceptanceDemo`，再写入 state；没有这些字段不得进入 `ready`。

## 7. 状态和报告

动态状态只写 `.cursor/session/state.json`：

- `lifecyclePhase` 表示 S0-S6 生命周期阶段。
- `deliveryPhase` 表示 P0-P10 产品交付阶段。
- 当前 slice/task Gate、状态、开始时间和耗时只在 state 更新。
- 本路线图和 `current-node.md` 只保存合同、边界及 `status_ref`，不镜像状态值。

PM 每次报告只回答当前可访问结果、运行入口、已通过证据、是否触发拆分、下一 task、当前 slice 与最终目标剩余项。文件数、代码行数、生成 CRUD 数量和“总体可行”不能代替这些结果。

## 8. 外部实践依据

- DORA Working in small batches: <https://dora.dev/capabilities/working-in-small-batches/>
- Scrum Guide: <https://scrumguides.org/docs/scrumguide/v2020/2020-Scrum-Guide-US.pdf>
- Martin Fowler Continuous Integration: <https://www.martinfowler.com/articles/continuousIntegration.html>

本项目采用 120 分钟 coding task 上限和 240 分钟功能批次目标，以模块写集隔离换取真实并行，并通过分层验证避免重复支付全量回归成本。

## 9. 完成边界

完成层级固定为 `TASK -> SLICE -> PHASE -> JOURNEY -> RELEASE -> FINAL`。低层通过只能成为高层验收输入；只有 P0-P10 工程 Gate、发布 Gate 全部通过且用户完成整体验收，项目才能标记最终完成。
