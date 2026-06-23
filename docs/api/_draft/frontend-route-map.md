# 前端路由 · 新 IA 映射（草案）

> P1 · frontend · 对照 page-inventory 与现有 router

## 1. 壳切换

| 壳 | 路由前缀 | 布局组件 |
|----|----------|----------|
| 平台 | `/platform/*` | PlatformShell |
| 系统运行态 | `/systems/:id/runtime/*` | **RuntimeShell**（新建） |
| 系统后台 | `/systems/:id/admin/*` | AdminShell |

## 2. 运行态路由（新）

| 页面 | 路径 | 原型 |
|------|------|------|
| 仪表盘 | `.../dashboard` | dashboard-che |
| 模块列表 | `.../g/:groupId/m/:moduleId` | runtime-list |
| 表单 | `.../g/:groupId/m/:moduleId/new` | runtime-form |
| 编辑 | `.../records/:recordId/edit` | runtime-form |

## 3. 与旧路由差异

| 旧 | 新 |
|----|-----|
| app → modules 树 | **moduleGroup 顶栏** |
| runtime 侧栏大导航 | module-rail 仅组内模块 |
| 详情独立路由为主 | **列表同屏详情** 为主 |

## 4. SDK

- 保留 `frontend/src/api/*`
- 新增 `runtimeShellStore`：currentGroupId, currentModuleId, detailOpen
- 页面禁止直接 fetch

## 5. 并行实现顺序

1. T040 三壳路由  
2. T050 RuntimeShell  
3. T051 列表+详情  
4. T060 admin 页逐页
