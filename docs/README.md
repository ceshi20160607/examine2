# docs 目录

协作规范已迁移至 **[`.cursor/`](../.cursor/README.md)**。

## 保留

- [user_requirement.md](./user_requirement.md) — 原始需求（唯一旧文档）

## 新产物将写入

| 目录 | 内容 |
|------|------|
| `requirements/` | analyst 需求分析 |
| `product/` | pm PRD、理解 |
| `design/` | ui-spec、DESIGN.md、原型、**user-approval** |
| `api/` | 冻结 API |
| `tasks/` | 任务拆分 |
| `evidence/` | 验收与构建证据 |
| `decisions/` | 需用户决策事项 |

## 当前状态

见 [`.cursor/session/state.json`](../.cursor/session/state.json)

**硬闸门：** `design_user_approved = false` → 禁止开发。
