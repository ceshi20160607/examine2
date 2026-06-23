# Phase 0：粗糙需求 → 设计包（Agent 主导）

> **入口：** 新项目或 `docs/product/rough-input.md` 存在且 `design_package_complete = false`  
> **Skill：** [`.cursor/skills/rough-to-prototype/SKILL.md`](../skills/rough-to-prototype/SKILL.md)  
> **出口：** `design_package_complete = true` → 进入 [phase-1-internal-design-complete.md](./phase-1-internal-design-complete.md) 或直接 Open Design

## 与用户的关系

| 用户给 | Agent 做 |
|--------|----------|
| 粗糙需求（通常很短） | L0～L3 全部文档 |
| P0 产品决策（pending） | 写入 resolution，继续 |
| 无 | 按 defaults.md 补全并标注 AUTO |
| **签字** | 仅 L4 后 `user-approval.md`（审美与顺手） |

用户**不参与**页表勾选、IA 表格填写。

## Conductor 调度

```
rough-input 落盘
    → analyst: L0 + L1（prd, design-scope, glossary）
    → [若有 pending] 暂停等用户
    → uiux: L2（design-package, ui-spec 壳, seed）
    → uiux+frontend: L3（config-spec, page-inventory）
    → 六角色 reviews（可同会话多文件）
    → conductor: design_package_complete = true
    → uiux: L4 prototype-brief → Open Design / HTML
    → uiux: design-diff
    → 用户: user-approval
```

## 阶段 Gate

| 步骤 | Gate 字段 | 条件 |
|------|-----------|------|
| L1 完 | `prd_frozen` | MVP + 无 open P0 discovery |
| L3 完 | `design_package_complete` | checklist A～E 全 ✅ |
| L4 + 用户 | `design_user_approved` | user-approval approved:true |

## 禁止

- L2 前生成 HTML
- L3 前调用 Open Design
- 未 `design_user_approved` 写 backend/frontend/sql
- 在 brief 外「聊天补丁」式改原型需求

## 粗糙输入示例

用户只需类似：

> 做一个内部工单系统，管理员配字段和流程，员工提单和处理，要列表筛选和导出，能接外部 API。

Agent 应产出 ≥15 页 design-package、完整 config-spec、工单演示 seed、可浏览 P0 原型。

## 下一步

→ [phase-1-internal-design-complete.md](./phase-1-internal-design-complete.md)（若 reviews 未并入 L3）  
→ Open Design → [phase-1-design-freeze.md](./phase-1-design-freeze.md)
