# 公共技能契约

技能是无身份、无长期记忆、输入输出确定的一次性能力。技能不拥有产品决策权，也不维护工程状态。

每次调用必须声明：技能名、触发原因、输入、输出、判定标准和失败去向。

| 技能 | 触发 | 必需输入 | 产出 | 失败处理 |
|---|---|---|---|---|
| `source-trace` | 整理需求来源 | 来源清单、优先级 | 来源映射和冲突 | 交 analyst/pm |
| `requirement-consolidation` | 形成需求理解 | 权威输入、已有决策 | 需求总账和缺口 | 交 analyst/pm |
| `decision-review` | 需要范围或产品裁决 | 方案、影响、来源 | 决策建议或升级项 | 交 pm/用户 |
| `scope-check` | 阶段或任务进入 Gate | 目标、范围、产出 | pass/fail 与越界项 | 交 conductor |
| `journey-review` | 设计、任务或验收检查 | 角色、入口、步骤、结果 | 旅程覆盖报告 | 交所属角色 |
| `design-review` | 设计节点关闭前 | 需求、旅程、设计 | 设计问题和结论 | 交 uiux/pm |
| `data-contract-review` | 数据设计或接口设计 | 业务规则、模型、读写结果 | 数据契约问题 | 交 dba/backend |
| `migration-check` | 数据变更前后 | 迁移、回滚、样例数据 | 可执行验证结果 | 阻断发布 |
| `contract-check` | 实现前或联调前 | 接口、数据、权限、状态契约 | 一致性报告 | 阻断实现/联调 |
| `dependency-check` | 计划和调度 | 节点、依赖、路径所有权 | DAG 和冲突结果 | 交 planner/conductor |
| `build-check` | 实现批次完成 | 源码、构建说明 | 构建和单测证据 | 退回实现角色 |
| `browser-check` | 用户界面变更 | 运行地址、旅程、视口 | 交互和视觉证据 | 退回 frontend/uiux |
| `acceptance-check` | 任务或节点声称完成 | 验收契约、运行结果 | 独立 pass/fail | 退回负责人 |
| `regression-check` | 修复或集成后 | 影响范围、历史用例 | 回归报告 | 阻断节点关闭 |
| `gate-review` | 阶段推进前 | 状态、产出、问题、证据 | Gate 判定 | conductor 保持原阶段 |
| `record-sync` | 决策或状态变化后 | 变化内容、来源 | 状态与记录同步结果 | 阻断交接 |

技能输出至少包含：

```yaml
skill: "{{SKILL}}"
verdict: "pass | fail | blocked"
inputs: []
checks: []
evidence: []
issues: []
next_owner: "{{ROLE}}"
```

