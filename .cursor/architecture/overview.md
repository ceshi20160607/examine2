# 架构总览（面向本项目的用法）

## 你的 8 点诉求如何落地

| # | 你的要求 | 落地方式 |
|---|----------|----------|
| 1 | `.cursor` 新架构，参考不沿用 `.codex` | 本目录为唯一规范；`.codex` 只读参考 |
| 2 | Open Design 确认前不开发 | `gates.design_user_approved` 硬闸门 |
| 3 | Open Design 怎么装 | 见 [open-design/install.md](../open-design/install.md) |
| 4 | Agent 还是 Skill？如何并行？ | 见下文 + [agents-vs-skills.md](./agents-vs-skills.md) |
| 5 | 不传上下文，新会话读文档 | [context-policy.md](./context-policy.md) |
| 6 | 先需求/工程/UI 契约 → UI 认同 → 再拆任务 → 多角色 API → 再开发 | [work-graph.md](./work-graph.md), [ui-system.md](./ui-system.md) |
| 7 | 每任务独立验收，不当裁判又当选手 | [acceptance.md](./acceptance.md) |
| 8 | 除 `user_requirement.md` 外旧文档删除 | 已执行；新产物按本架构路径生成 |

## 三层结构（请按这个理解流程图）

```
┌─────────────────────────────────────────────────────────┐
│  Layer 1: Conductor（1 个逻辑角色，可用当前 Cursor 会话）   │
│  管 Session、Gate、并行、事件；不传递聊天上下文              │
└──────────────────────────┬──────────────────────────────┘
                           │ 读 state.json，按 phase 调度
┌──────────────────────────▼──────────────────────────────┐
│  Layer 2: Worker Agents（多个，每次新建会话）              │
│  pm / analyst / uiux / planner / dba / backend /         │
│  frontend / test — 有判断力、多轮、读文档产出文件           │
└──────────────────────────┬──────────────────────────────┘
                           │ 提 issue 给 PM；实现写代码
┌──────────────────────────▼──────────────────────────────┐
│  Layer 3: Skills（无状态脚本/检查，可并行）                │
│  open-design / clean-build / contract-sync /             │
│  task-accept / review-gate — 不「感觉完成」，只输出证据    │
└─────────────────────────────────────────────────────────┘
```

**不是「很多 Agent 围在一起聊天」**，而是：

- **1 个 Conductor** 看黑板（`state.json` + 文件）
- **多个 Worker** 各自领任务、各自新开会话、各自写文件
- **多个 Skill** 在关键节点跑确定性检查

## 并行怎么发生？

并行发生在 **Layer 3 Skills** 和 **无依赖的 Worker 任务** 上，例如：

| 可并行 | 不可并行 |
|--------|----------|
| `dba` 表设计与 `uiux` 写 ui-spec（Design 阶段） | `api.md` 编写（需多角色回合） |
| `backend` 模块 A 与 `frontend` 页面 B（路径不重叠） | 同一文件的两人同时改 |
| 多个 `task-accept` skill 验不同任务 | PM 未关闭 P0 issue 时开 Build |

**发现问题**不靠「大家同时聊」，靠：

1. 每个 Worker 交付时必填 `issues` 字段或写 registry
2. PM 专门一轮 **只读各方产物** 做 Triage
3. Skill `review-gate` 对照 Gate 条件机械检查

## 为什么这样比旧架构好？

| 旧 `.codex` | 新 `.cursor` |
|-------------|--------------|
| 20 步 Pipeline 必须顺序跑完 | Work Graph 按 Gate 推进，可停在你签字处 |
| Orchestrator 兼 PM + 写代码 | 三者分离 |
| 200+ 文档当状态 | `state.json` + 事件流 + 分阶段目录 |
| UI 只有 markdown | Open Design 可预览原型 + 你签字 |
| validator/reviewer 也是 Agent 写 md | 验收用 Skill + 独立 test Worker |
| 对话上下文传递 | **只传文件路径，新会话读盘** |

## 产物目录（新）

```
docs/
  user_requirement.md
  requirements/ product/ design/ api/ tasks/ evidence/ decisions/
.oldbk/          # 旧 backend、frontend、sql、.codex（只读）
backend/         # Gate 后重建
frontend/        # Gate 后重建
sql/             # Gate 后重建
```

**`requirements_rebuild_accepted = false` 或 `design_user_approved = false` 时不得创建 backend/frontend/sql。**

## 当前新增框架

- [requirements-rebuild.md](./requirements-rebuild.md)：当前需求重整和工程完善阶段。
- [ui-system.md](./ui-system.md)：高密度企业业务系统 UI 框架，约束顶栏、列表、详情、状态和视觉验收。
