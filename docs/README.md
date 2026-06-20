# docs 目录

协作规范已迁移至 **[`.cursor/`](../.cursor/README.md)**。

## 保留

- [user_requirement.md](./user_requirement.md) — 原始需求（唯一旧文档）
- [product-ns-map.md](./product-ns-map.md) — 当前四套壳全量功能图
- [design/prototype-brief.md](./design/prototype-brief.md) — 当前唯一 Open Design brief
- [design/design-package.md](./design/design-package.md) — 当前设计包摘要
- [design/user-approval.md](./design/user-approval.md) — 设计签字硬闸门
- [design/reviews/prototype-latest-2026-06-18.md](./design/reviews/prototype-latest-2026-06-18.md) — 最新原型复审
- `design/prototypes/` — 当前最新原型输出目录，只保留可用于签字确认的原型产物

## 新产物将写入

| 目录 | 内容 |
|------|------|
| `design/` | 当前唯一 brief、最新原型、复审、**user-approval** |
| `api/` | 冻结 API |
| `tasks/` | 任务拆分 |
| `evidence/` | 验收与构建证据 |
| `decisions/` | 后续新增用户决策事项 |

## 当前状态

见 [`.cursor/session/state.json`](../.cursor/session/state.json)

**硬闸门：** `design_user_approved = false` → 禁止开发。
