# 测试计划与完成度

## 目标流程（你要求的自主循环）

1. 跑测试 → 2. 按问题逐个修复 → 3. 全量回归  
**当前阶段：管理台 `web/vue3` 全部路由已覆盖「可打开 / 基础交互」；复杂编辑与 OpenAPI 全链路仍未覆盖。**

---

## API 冒烟（`tests/api/e2e-smoke.ps1`）— 19 步 ✅

| 步骤 | 覆盖能力 |
|------|----------|
| ping / auth / system | 平台登录、进系统 |
| meta + records | 应用/模型/字段、建记录、查详情、DSL 查询 |
| rbac | 角色、运行时菜单 |
| inbox | 消息、流程待办 |
| flow graph-designer | 模板版本、保存/加载图、发布 |
| platform permissions | 平台 RBAC |
| meta catalog | 应用列表、字段类型 |
| record update + history | 更新记录、历史 |
| dept + module auth | 部门树、模块权限 |
| flow temps list / inbox cc | 模板分页、抄送 |
| dict + pages + list-views | 字典、页面、列表视图 |
| flow-bindings + instances | 流程绑定、流程实例 |
| flow lifecycle | 发起实例、领取、同意、轨迹/任务查询 |
| rbac menus members perms | 菜单权限覆盖写、成员、账号搜索 |
| pages runtime + list-view cols | 页面 runtime/detail、列表视图列、perm-preview |
| uploads + export-jobs + platform apps | 上传、导出任务、开放应用 CRUD/轮换密钥 |

**未覆盖（API）**：OpenAPI AK/SK 全链路、流程拒绝/转办/撤回、集成事件、导出 CSV 下载二进制、全部 CRUD 删除路径等。

---

## Web UI（Playwright）— 39 用例 ✅（路由全覆盖）

### 路由覆盖对照（`web/vue3/src/router/index.js`）

| 路由 | 覆盖 spec |
|------|-----------|
| `/login` | auth |
| `/register` | auth |
| `/systems` | systems |
| `/platform/inbox` | platform |
| `/platform/open-apps` | platform |
| `/platform/open-apps/:id` | deep-pages |
| `/upload` | platform |
| `/export-jobs` | platform、app-advanced-config |
| `/apps` | apps |
| `/apps/:appId` | app-hub |
| `/apps/:id/models` | app-hub、app-config-crud |
| `/apps/:id/models/:modelId/fields` | routes-coverage |
| `/apps/:id/dicts` | app-hub、app-config-crud |
| `/apps/:id/dicts/:dictId/items` | routes-coverage |
| `/apps/:id/depts` | app-hub、app-config-crud |
| `/apps/:id/relations` | app-hub、app-advanced-config |
| `/apps/:id/pages` | app-hub、deep-pages |
| `/apps/:id/pages/:pageId/edit` | deep-pages |
| `/apps/:id/list-views` | app-hub、app-config-crud |
| `/apps/:id/exports` | app-hub、app-config-crud |
| `/apps/:id/flow-bindings` | app-hub、app-advanced-config |
| `/apps/:id/rbac` | app-hub、deep-pages |
| `/apps/:id/rbac/roles/:roleId/menus` | deep-pages |
| `/apps/:id/rbac/roles/:roleId/pages` | deep-pages |
| `/apps/:id/menus` | app-hub、app-advanced-config |
| `/records` | records |
| `/records/form` | records-pages |
| `/records/detail` | deep-pages |
| `/flow/inbox` | flow、routes-coverage |
| `/flow/task` | app-advanced-config、flow-runtime、routes-coverage |
| `/flow/start` | flow-pages、flow-runtime |
| `/flow/instances` | flow-pages |
| `/flow/instances/:instanceId` | routes-coverage |
| `/flow/temps` | flow |
| `/flow/temps/:tempId` | flow-pages |
| `/flow/temps/:id/versions/:verId/designer` | deep-pages |

### 用例分组

| 分组 | 能力 |
|------|------|
| 认证 | 登录、注册页、跳转系统 |
| 应用中心 | hub 各子页加载 |
| 应用配置 CRUD | 模型/字段、字典/项、部门、列表视图、导出模板字段 |
| 高级配置 | 模型关系、流程绑定、运行时菜单、导出任务筛选 |
| 深层页 | 页面编辑、RBAC 子页、开放应用详情、记录详情、流程图设计器 |
| 流程运行时 | UI 发起流程、任务领取、实例详情、待办跳转 |
| 平台/记录 | 平台页、记录列表/表单 |

**未覆盖（UI）**：

- 各页「删除」与批量操作
- 页面/流程图设计器的复杂编辑保存
- 流程任务「同意/拒绝」完整办理 UI
- 注册提交、导出任务下载文件

---

## 本轮修复（测试暴露）

| 问题 | 修复 |
|------|------|
| `FlowStartView` 传 `tempCode` 后端要 `defCode` | 改为 `defCode` |
| 流程绑定 API 返回嵌套结构 UI 读不到 | `SystemModuleFlowBindingService` 扁平字段 + 字符串 id |
| 页面 runtime Long id 精度丢失 | `SystemModulePageService` 返回字符串 id |
| `perm-preview` `Map.of(null)` NPE | 改用 `LinkedHashMap` |
| Playwright `postApi` Long id 精度丢失 | `tests/web/fixtures/api.ts` 响应 patch |

---

## 计划是否完成？

| 项 | 状态 |
|----|------|
| 搭建 `tests/`、API + UI 脚手架 | ✅ |
| BUGS.md 历史项 | ✅ |
| API 冒烟 19 步 | ✅ |
| UI 覆盖全部管理台路由（基础） | ✅ |
| UI 覆盖所有复杂编辑/删除 | ❌ |
| API 覆盖所有 `/v1` | ❌ |
| 移动端 `tests/mobile` | ❌ 占位 |

---

## 一键回归

```powershell
$env:SMOKE_USER = 'admin'
$env:SMOKE_PASS = '123123aa'
cd tests
.\run-all.ps1
```
