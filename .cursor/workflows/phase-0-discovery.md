# Phase 0: Discovery（增量，非重跑 v1）

## 入口

- `session/state.json` → `phase: discovery`
- **先读** `.cursor/knowledge/*`（已沉淀，默认生效）

## 与 v1 的区别

| v1 | v2 Phase 0 |
|----|------------|
| 全员从零理解需求 | knowledge 已含领域模型、冻结规则、失败教训 |
| 多轮争论平台/系统分层 | 已冻结，analyst 只找**差异** |
| analyst 写 50 页分析 | analyst 写 **delta + 不确定项** |

## 步骤

| # | Worker | 输入 | 输出 |
|---|--------|------|------|
| 0 | 所有 Worker | `.cursor/knowledge/*` | （只读，不写） |
| 1 | analyst | `user_requirement.md` + knowledge | `docs/requirements/analysis.md`（**差异与风险**，非全文复述） |
| 2 | analyst | `.oldbk/` 结构只读 | `docs/requirements/legacy-summary.md`（能力索引，指向 validated-capabilities） |
| 3 | pm | analysis + knowledge + frozen-rules | `docs/product/prd.md`（**引用** frozen-rules，不重复造轮子） |
| 4 | pm | — | `docs/product/understanding.md`（仅未决项与 MVP 边界） |
| 5 | 各角色 | prd | 仅对 **与 knowledge 冲突** 或 **prd 未覆盖** 点提 issue |
| 6 | pm | registry | triage；可决策则关闭，否则 escalated |

## analyst 输出契约

`analysis.md` 必须包含：

- 需求与 `frozen-rules.md` 一致的部分（列表引用即可）
- **新增/变更** 需求相对 knowledge 的差异
- **不确定项**（须 PM 或用户决）
- **不建议讨论** 的已冻结项（标明「见 knowledge/xxx」）

## 退出

- [ ] prd 引用 frozen-rules，含车系统验收剧本
- [ ] 无 open P0 discovery issues（或已 escalated 等你）
- [ ] `gates.prd_frozen = true`（PM 建议；范围变更须你已 resolution）

## 下一步

→ `phase-1-design-freeze.md`（仍须 `design_user_approved` 你才能签字）

## 禁止

- 重写已冻结的 domain-model / backend-structure
- 创建 backend/frontend/sql
- 为「证明理解」而重复 v1 式长篇评审
