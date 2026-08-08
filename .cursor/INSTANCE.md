# examine2 工程实例配置

本目录是当前项目自包含的工程实例。它从仓库内 `.base` 模板实例化，但项目运行时不回读模板源；公共角色、技能、治理、生命周期和模板均已复制到本目录。

## 1. 实例身份

- 实例名称：`examine2-engineering`
- 项目名称：`examine2`
- 项目目标：从 `docs/` 提供的粗糙需求和设计输入，重新构建一个普通人可以实际使用、可以交付验收的可配置业务管理系统平台。
- 模板版本：`2.4.0`

## 2. 路径绑定

| 逻辑名称 | 当前路径 | 用途/状态 |
|---|---|---|
| 工程实例根 | `.cursor/` | 当前有效角色、技能、治理、状态和工程产出 |
| 项目输入根 | `docs/` | 人可读需求、配置、流程和粗略设计 |
| 工程产出根 | `.cursor/session/` | 当前状态、路线图、需求、设计、计划、会议和证据 |
| 服务端源码根 | `backend/` | VS1-VS3 已运行；按当前切片继续实现 |
| 前端源码根 | `frontend/` | VS1-VS3 已运行；按当前切片继续实现 |
| 数据库资产根 | `sql/` | 已有 V1-V3 migration；按当前切片增量迁移 |
| 测试与证据根 | `.cursor/session/evidence/` | 当前切片新增证据；历史证据只证明其已验收范围 |
| 发布资产根 | `release/` | 尚未创建；发布阶段使用 |

## 3. 权威输入

| 优先级 | 路径 | 内容 | 默认读取 | 冲突处理 |
|---|---|---|---:|---|
| 1 | `docs/user_requirement.md` | 当前产品需求主来源 | yes | analyst 标记，product 建议，PM 会审；仍无法确定则升级 |
| 2 | `docs/temp_flow.md` | 人可读功能和流程梳理 | yes | 用于发现遗漏，不静默覆盖主需求 |
| 2 | `docs/temp_flow_persion.html` | 角色与流程参考 | yes | 用于旅程交叉检查 |
| 3 | `docs/design/prototypes/index.html` | 粗略设计/原型参考 | yes | 不是冻结设计或功能完成证据 |
| 配置 | `docs/user_setting.md` | 本地环境、账号和依赖配置 | task-only | 按任务最小读取，敏感值不得复制到工程产出 |

旧代码归档和上一版重复的 `architecture/knowledge/open-design/phase-*` 工程文件已删除。可复用经验已进入 `.base` 和当前 `.cursor` 的角色、治理、生命周期、技能与模板；项目事实只从本表、当前状态、主需求/设计产出和已登记证据读取。

## 4. 技术与交付约束

- 所有活跃文本使用 UTF-8。
- 技术框架和版本在 S2 设计阶段依据需求与环境正式冻结。
- 数据库规划和设计先于服务端基础代码生成。
- 基础持久化代码使用 MyBatis 代码生成工具；具体工具、配置、目录和覆盖策略由 DBA/Architect/Backend 在 S2 会审。
- 生成代码只承担贴表基础访问；权限、事务、流程、状态、副作用、审计和真实业务语义必须手写。
- 前端必须绑定真实 API、权限和持久化读回；静态页面、原型 fixture 和假成功提示不算功能。
- 最终交付必须包含可运行系统、外置配置、启动/停止/健康/回滚说明和用户验收材料。
- 项目基线最长 240 分钟；coding task 为 10..120 分钟，推荐 45..90 分钟；功能批次目标 240 分钟，任务数量由 DAG 决定。
- 无依赖且 `module_scope/write_scope` 不重叠的任务默认并行；共享 schema/migration、lockfile、公共契约或同一聚合根写入时才串行或隔离。
- task 只验证受影响层，slice 验证一次真实入口与跨层读回，phase/release 执行全量回归、重启和浏览器矩阵。
- 功能开发先保证参考桌面视口可操作；移动端、断点、视觉和无障碍统一在 release 前 hardening 收口。
- 本实例可以收紧但不能放宽 `.base` 公共节奏上限。

## 5. 启用角色

本实例启用全部公共角色：`leader`、`pm`、`product`、`analyst`、`architect`、`uiux`、`planner`、`dba`、`backend`、`frontend`、`test`、`ops`。

角色读取顺序：

```text
.cursor/agents/{role}/role.md
  -> .cursor/agents/{role}/update.md
  -> 当前任务上下文包
```

角色按节点调用，不常驻；每次新会话只读声明输入。实现者不能验收自己的结果。

## 6. 运行状态入口

- 运行状态唯一事实源：`.cursor/session/state.json`。
- 当前节点说明：`.cursor/session/current-node.md`。
- 分期和最终目标剩余范围：`.cursor/session/delivery-roadmap.md`。

本配置不复制当前阶段、任务、Gate 或阻断范围。

## 7. 问题、会议和用户待决

- 当前 issue 注册表：`.cursor/session/issues/registry.jsonl`。
- `2026-07-10T20:07:25+08:00` 之前的记录为历史参考；当前框架只把该时间之后产生的 issue 视为活动问题。
- 跨角色会议使用 `.cursor/templates/meeting-decision.md`，按需保存到 `.cursor/session/meetings/`。
- 用户待决唯一入口：`.cursor/session/pending-user-decisions.md`。
- Leader 每轮启动时检查用户待决答案并应用到相关需求、设计、issue 和状态。

## 8. 用户专属 Gate

以下事项工程角色不能代签：重大产品范围增删、无法从需求推断的主观设计取舍、最终系统满意度和整体用户验收。
