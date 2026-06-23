# 设计包 — FlexBase 无代码平台（测试 L2）

> P0 共 **33** 页 · 零 TBD

## 1. 冻结 IA

| 规则 | MUST / MUST NOT |
|------|-----------------|
| 三层 | 平台工作台 / 系统后台 / 运行态 入口分离 |
| 待办消息 | 仅顶栏图标；侧栏不得重复 |
| Console/Portal | API **配置**在 plat-admin-api-console；Portal P1 仅占位 |
| 租户 | 多租户系统：运行态+后台顶栏 TenantSwitcher |
| SSO | 平台 plat-admin-sso；登录页含 SSO 按钮 |
| 建模链 | 模块组→模块→建模→发布 |
| 平台 Flow ≠ 系统 Flow | 分菜单、分配置页 |
| 业务用户 | employee 无后台入口 |
| 列表 | 标准 biz-list：场景/筛选/抽屉详情（lessons C） |
| 用户/角色 | 平台与系统均 **成员页、角色页分离** |

## 2. 壳 ASCII

### 平台工作台

```
顶栏: Logo | 上下文 | [待办][消息] | 用户
侧栏: 我的系统 | 平台Flow | API门户(链) | 日志
禁止: 侧栏待办/消息
```

### 系统后台

```
顶栏: Logo | 租户▼(若多租户) | 运行态 | 待办 | 消息 | 用户
侧栏: 概览|基础|租户|组织|模块组|模块|建模|Flow|对外应用|角色|成员|日志
```

### 运行态

```
顶栏: Logo | 租户▼ | [仪表盘][模块组●] | 待办 | 消息 | 用户
点模块组 → 左栏组内模块 → 右业务区
```

## 3. 页面总表

### 3.1 平台层（14 P0）

| ID | 页面 | 文件 | P |
|----|------|------|---|
| P-LOGIN | 登录（含 SSO） | `platform/login.html` | P0 |
| P-REGISTER | 注册并建系统 | `platform/register.html` | P0 |
| P-MY-SYSTEMS | 我的系统 | `platform/my-systems.html` | P0 |
| P-PLAT-TODOS | 待办 | `platform/todos.html` | P0 |
| P-PLAT-MESSAGES | 消息 | `platform/messages.html` | P0 |
| P-PLAT-ADMIN-OVERVIEW | 平台概览 | `platform/plat-admin-overview.html` | P0 |
| P-PLAT-ADMIN-TENANTS | 租户管理 | `platform/plat-admin-tenants.html` | P0 |
| P-PLAT-ADMIN-USERS | 平台用户 | `platform/plat-admin-users.html` | P0 |
| P-PLAT-ADMIN-ROLES | 平台角色 | `platform/plat-admin-roles.html` | P0 |
| P-PLAT-ADMIN-SSO | SSO/IdP | `platform/plat-admin-sso.html` | P0 |
| P-PLAT-ADMIN-FLOW | 平台 Flow | `platform/plat-admin-flow.html` | P0 |
| P-PLAT-ADMIN-API-CONSOLE | API 控制台 | `platform/plat-admin-api-console.html` | P0 |
| P-PLAT-ADMIN-LOGS | 平台日志 | `platform/plat-admin-logs.html` | P0 |
| P-PLAT-ADMIN-CONFIG | 全局配置 | `platform/plat-admin-config.html` | P0 |

### 3.2 系统后台（15 P0）

| ID | 页面 | 文件 | P |
|----|------|------|---|
| P-SYS-ADMIN-HOME | 引导首页 | `system/admin-home.html` | P0 |
| P-SYS-ADMIN-BASIC | 基础信息 | `system/admin-basic.html` | P0 |
| P-SYS-ADMIN-TENANT | 租户模式 | `system/admin-tenant.html` | P0 |
| P-SYS-ADMIN-ORG | 组织架构 | `system/admin-org.html` | P0 |
| P-SYS-ADMIN-DICT | 字典 | `system/admin-dict.html` | P0 |
| P-SYS-ADMIN-DATASOURCE | 数据源 | `system/admin-datasource.html` | P0 |
| P-SYS-ADMIN-DASHBOARD-CFG | 仪表盘配置 | `system/admin-dashboard-cfg.html` | P0 |
| P-SYS-ADMIN-MODULE-GROUP | 模块组 | `system/admin-module-group.html` | P0 |
| P-SYS-ADMIN-MODULE | 模块 | `system/admin-module.html` | P0 |
| P-SYS-ADMIN-MODELING | 建模 | `system/admin-modeling.html` | P0 |
| P-SYS-ADMIN-FLOW | 系统 Flow | `system/admin-flow.html` | P0 |
| P-SYS-ADMIN-EXTERNAL-APP | 系统对外应用 | `system/admin-external-app.html` | P0 |
| P-SYS-ADMIN-USERS | 成员 | `system/admin-users.html` | P0 |
| P-SYS-ADMIN-ROLES | 角色权限 | `system/admin-roles.html` | P0 |
| P-SYS-ADMIN-LOGS | 系统日志 | `system/admin-logs.html` | P0 |

### 3.3 运行态（4 P0）

| ID | 页面 | 文件 | P |
|----|------|------|---|
| P-SYS-DASHBOARD | 仪表盘 | `system/dashboard-hr.html` | P0 |
| P-RUNTIME-LIST | 请假列表 | `system/runtime-list.html` | P0 |
| P-RUNTIME-FORM | 请假表单 | `system/runtime-form.html` | P0 |
| P-SYS-FLOW-TODO | 待办审批 | `system/flow-todo.html` | P0 |

### 3.4 P1

| ID | 页面 | 文件 |
|----|------|------|
| P-PORTAL-HOME | 开发者 Portal | `platform/portal-home.html` |
| P-RUNTIME-DETAIL | 详情整页 | `system/runtime-detail.html` |

## 4. 剧本映射

见 `product/prd.md` §5 — 15 步全覆盖。
