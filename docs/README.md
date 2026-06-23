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

**阶段:** Build（2026-06-17 用户授权全量开发）

| 项 | 状态 |
|----|------|
| 设计包 | ✅ ui-spec + config-spec + 37 页原型 |
| 用户签字 | ✅ `user-approval.md` approved |
| 代码基线 | ✅ `backend/` `frontend/` `sql/` 已从 `.oldbk` 恢复 |
| API 契约 | 🔄 `docs/api/README.md` + 前端 SDK |
| 本地构建 | ⚠️ 需 JDK21/Maven/Node，见 [`DEPLOY.md`](./DEPLOY.md) |
| UI 对齐新原型 | 🔄 运行态 module-rail 壳等待 frontend rework |

**完备性评估:** [`design/build-readiness.md`](./design/build-readiness.md)
