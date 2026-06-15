# Frontend 设计包审阅（概念）

> agentId: frontend | 非实现

## 结论: pass（概念）

## 布局组件

| 壳 | 路由布局 | 关键组件 |
|----|----------|----------|
| PlatformLayout | `/platform/*` | 侧栏无待办文字；顶栏 TodoBadge |
| SystemRuntimeLayout | `/s/:id/*` | TopModuleGroups + SideModuleList |
| SystemAdminLayout | `/s/:id/admin/*` | 配置侧栏 |

## 状态

- 选中模块组 → 左栏模块列表
- 权限裁剪顶栏组与左栏项
- che 路由守卫：无 `/admin`

## 实现注意

Naive UI：NLayout + NTabs（顶栏组）+ NMenu（左栏）。

## 风险

无阻塞。
