# 公共技能目录

技能是无身份、无长期记忆、输入输出确定的一次性能力。技能可以由角色调用，但不能拥有产品决策权、项目推进权或用户签字权。

本文件定义技能索引；每项技能的必需输入、执行步骤、通过条件和失败输出在 `skills/playbooks.md`。角色不得只引用技能名称而跳过 playbook。

## 统一调用契约

```yaml
skill: "{{SKILL_ID}}"
trigger: "{{WHY_NOW}}"
inputs: []
output: "{{OUTPUT_PATH}}"
checks: []
pass_condition: []
failure_owner: "{{ROLE}}"
```

统一输出至少包含：

```yaml
skill: "{{SKILL_ID}}"
verdict: "pass | fail | blocked"
checked_inputs: []
checks: []
evidence: []
issues: []
next_owner: "{{ROLE}}"
```

## 需求与产品技能

| skill | 触发 | 核心检查 | 输出 |
|---|---|---|---|
| `source-trace` | 整理/变更需求 | 来源存在、优先级、引用锚点、冲突 | 来源映射与冲突 |
| `requirement-consolidation` | 形成需求包 | 角色、触发、行为、结果、状态、权限、数据、异常 | 结构化需求检查 |
| `journey-review` | 需求/设计/任务/验收 | 真实入口、步骤、系统行为、持久结果、失败路径 | 旅程覆盖判定 |
| `scope-check` | 范围或任务变化 | in/out/deferred、来源、影响、越界 | 范围判定 |
| `decision-review` | 产品决策 | 事实、选项、取舍、权力边界、用户专属项 | 决策建议/升级 |

## 设计与架构技能

| skill | 触发 | 核心检查 | 输出 |
|---|---|---|---|
| `design-review` | UI/UX 节点 | IA、主任务、状态、权限、断链、重复入口 | 设计判定与问题 |
| `architecture-review` | 工程设计 | 模块、依赖、数据归属、质量属性、演进 | 架构判定与风险 |
| `contract-check` | 契约冻结/联调 | UI/API/data/permission/state/error 一致性 | 一致性报告 |
| `data-contract-review` | 数据/API 设计 | 生命周期、唯一性、隔离、审计、读回 | 数据契约问题 |
| `security-review` | 身份/权限/敏感操作 | 认证、授权、数据范围、密钥、日志、滥用 | 安全风险与措施 |
| `failure-mode-review` | 关键流程/发布 | 超时、重试、幂等、并发、补偿、恢复 | 故障模式清单 |
| `performance-review` | 数据/接口/页面 | 数据量、复杂度、索引、分页、缓存、容量 | 性能风险与基线 |
| `accessibility-check` | 用户界面 | 键盘、焦点、标签、对比、状态可感知 | 可访问性判定 |

## 计划与协作技能

| skill | 触发 | 核心检查 | 输出 |
|---|---|---|---|
| `dependency-check` | 计划/调度 | DAG、前置、并行、共享写入、关键路径 | 依赖判定 |
| `meeting-check` | 跨角色会审 | 参与人、证据、分歧、决定、行动和复核 | 会审完整性判定 |
| `gate-review` | 节点/阶段推进 | 必需产出、issue、证据、签字和阻断 | Gate pass/fail |
| `record-sync` | 决策/状态变化 | 状态、issue、决定、节点和下一步一致 | 同步结果 |
| `research-check` | 外部事实不确定 | 权威来源、时效、适用版本、推断边界 | 带来源研究结论 |

## 实现与验证技能

| skill | 触发 | 核心检查 | 输出 |
|---|---|---|---|
| `build-check` | 实现批次 | 依赖、编译、单测、制品、警告 | 构建证据 |
| `api-readback-check` | 服务端/旅程 | 写入、真实读回、权限、错误、幂等 | API/数据证据 |
| `browser-check` | 用户界面 | 路由、交互、控制台、布局、视口、真实数据 | 浏览器证据 |
| `acceptance-check` | 任务声称完成 | 任务契约、跨层证据、独立性、问题 | 独立验收判定 |
| `regression-check` | 修复/集成 | 影响范围、旧能力、关键旅程 | 回归判定 |
| `migration-check` | 数据变化/发布 | 前置、迁移、验证、回滚、不可逆项 | 迁移判定 |
| `release-check` | 发布/交付 | 制品、配置、启动、健康、重启、回滚、清理 | 发布判定 |

## 技能硬规则

- `pass` 必须有实际检查和证据；未执行是 `blocked`，不是 `pass`。
- 技能失败只报告事实并指定责任角色，不自行修改被检查产出。
- 低层技能结果不能代替高层 Gate。`build-check` 通过不能代替 `journey-review`、`release-check` 或用户验收。
- 截图只能支持视觉检查，不能单独证明数据、权限、持久化或业务完成。
