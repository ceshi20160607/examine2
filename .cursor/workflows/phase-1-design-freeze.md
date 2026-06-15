# Phase 1: Design Freeze

## 入口

- `gates.prd_frozen = true`
- `gates.design_user_approved = false`

## 步骤

| # | 执行 | 输出 |
|---|------|------|
| 1 | uiux | `docs/design/ui-spec.md` |
| 1b | **团队内部** | **`design-package.md` + `reviews/*` → `design_package_complete`**（见 [phase-1-internal-design-complete.md](./phase-1-internal-design-complete.md)） |
| 2 | **你** 调用 Open Design | `prototypes/v3/**`（brief：`prototype-brief.md`） |
| 3 | uiux | `design-diff.md` |
| 4 | **你** | `user-approval.md` 签字 |

**步骤 1b 由 Conductor 调度各角色完成，不向你索要 IA 勾选。**

## 暂停点（硬停）

Conductor 在此 **停止一切下游**，直到：

```yaml
# user-approval.md
approved: true
```

## 你不满意时

- `approved: false` + notes → 回到步骤 1 或 2
- 可开 issue 给 uiux

## 退出

- [ ] 你签字 `approved: true`
- [ ] P0 页面均有原型
- [ ] `gates.design_user_approved = true`

## 下一步

→ `phase-2-contract.md`

## 禁止

- **一切** backend/frontend/sql 改动
- API 冻结
- 任务拆分（实现类）
