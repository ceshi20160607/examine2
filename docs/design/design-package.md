# 设计包（Design Package）— 内部完工清单

> **版本:** 1.3.0-res007
> **差距分析:** 见 [`design-gap-analysis.md`](./design-gap-analysis.md)（v1 原型 9/21，作废）  
> **维护:** uiux 汇总；PM 裁决；Conductor 置 Gate

**用户不需要填本文件。** 内部角色审阅见 `reviews/`。

---

## 1. 冻结 IA（团队共识，RES-003）

| 域 | 规则 |
|----|------|
| 模块组 | 先建组 → 组下建模块；运行态**顶栏组名**，点组后**左栏模块** |
| 对外应用 | 本系统对外入口；与模块组/模块解耦 |
| 平台待办/消息 | **仅顶栏图标**；侧栏不得重复 |
| 导出 | 模块列表工具栏动作，无导出中心 |
| 账号 | 平台用户表唯一；添加成员=选平台用户 |
| 仪表盘 | 后台配置发布；绑定数据源 |
| 字典 | 字段下拉可关联字典 |
| 平台 Flow/应用 | 平台后台独立配置；平台 Flow 经平台应用对外开放（RES-005） |
| 业务列表 | 业务列合理展示、快捷检索、高级筛选、场景、列设置、全部导出、导出选中、表头排序、序号勾选、批量操作限制、导入、详情抽屉；顶部控制区必须紧凑，首屏优先展示表格（RES-005/RES-006/RES-007） |
| 用户/角色 | 平台用户与平台角色拆页；系统成员与系统角色拆页，角色页承载模块/字段/数据/动作权限（RES-007） |

---

## 2. MVP 页面总表（共 34 个逻辑页面 + 3 弹窗）

### 2.1 平台层（15 页）

| ID | 页面 | 原型文件 | P |
|----|------|----------|---|
| P-LOGIN | 登录 | `platform/login.html` | P0 |
| P-REGISTER | 注册并建系统 | `platform/register.html` | P0 |
| P-MY-SYSTEMS | 我的系统 | `platform/my-systems.html` | P0 |
| P-PLAT-TODOS | 待办列表（顶栏进入） | `platform/todos.html` | P0 |
| P-PLAT-MESSAGES | 消息列表（顶栏进入） | `platform/messages.html` | P0 |
| P-PLAT-FLOW | Flow 汇总 | `platform/flow.html` | P1 |
| P-PLAT-APPS | 平台应用入口 | `platform/apps.html` | P1 |
| P-PLAT-LOGS | 平台日志 | `platform/logs.html` | P1 |
| P-PLAT-ADMIN-OVERVIEW | 平台后台概览 | `platform/plat-admin-overview.html` | P0 |
| P-PLAT-ADMIN-USERS | 平台用户管理 | `platform/plat-admin-users.html` | P0 |
| P-PLAT-ADMIN-ROLES | 平台角色管理 | `platform/plat-admin-roles.html` | P0 |
| P-PLAT-ADMIN-FLOW | 平台 Flow 配置 | `platform/plat-admin-flow.html` | P0 |
| P-PLAT-ADMIN-APPS | 平台应用配置 | `platform/plat-admin-apps.html` | P0 |
| P-PLAT-ADMIN-LOGS | 平台后台日志 | `platform/plat-admin-logs.html` | P0 |
| P-PLAT-ADMIN-CONFIG | 平台全局配置 | `platform/plat-admin-config.html` | P0 |

### 2.2 系统运行态（5 页，均含顶栏组+左栏模块壳）

| ID | 页面 | 原型文件 | P |
|----|------|----------|---|
| P-SYS-DASHBOARD | 仪表盘（che / admin 各一） | `system/dashboard-che.html`, `dashboard-admin.html` | P0 |
| P-RUNTIME-LIST | 模块列表（车辆档案） | `system/runtime-list.html` | P0 |
| P-RUNTIME-FORM | 新建/编辑 | `system/runtime-form.html` | P0 |
| P-RUNTIME-DETAIL | 详情（抽屉或独立页） | `system/runtime-detail.html` | P1 |
| P-SYS-FLOW-TODO | 系统内待办处理（简） | `system/flow-todo.html` | P1 |

**运行态壳（所有 system/runtime 页强制）：**

```
顶栏：[仪表盘] [车辆管理●] … | 待办图标 | 消息图标 | 用户
点「车辆管理」→ 左栏：车辆档案 | 维保记录 | 驾驶员
```

### 2.3 系统后台（14 页）

| ID | 页面 | 原型文件 | P |
|----|------|----------|---|
| P-SYS-ADMIN-HOME | 系统后台首页/引导 | `system/admin-home.html` | P0 |
| P-SYS-ADMIN-BASIC | **系统基础参数**（名称/图标/域名） | `system/admin-basic.html` | P0 |
| P-SYS-ADMIN-DICT | **数据字典** | `system/admin-dict.html` | P0 |
| P-SYS-ADMIN-DATASOURCE | **数据源** | `system/admin-datasource.html` | P0 |
| P-SYS-ADMIN-DASHBOARD-CFG | **仪表盘配置** | `system/admin-dashboard-cfg.html` | P0 |
| P-SYS-ADMIN-USERS | 成员管理 | `system/admin-users.html` | P0 |
| P-SYS-ADMIN-ROLES | 角色权限（模块/字段/数据/动作） | `system/admin-roles.html` | P0 |
| P-SYS-ADMIN-MODULE-GROUP | 模块组管理 | `system/admin-module-group.html` | P0 |
| P-SYS-ADMIN-MODULE | 组下模块 | `system/admin-module.html` | P0 |
| P-SYS-ADMIN-MODELING | 字段/动作/菜单/发布 | `system/admin-modeling.html` | P0 |
| P-SYS-ADMIN-FLOW | 流程配置 | `system/admin-flow.html` | P0 |
| P-SYS-ADMIN-EXTERNAL-APP | 对外应用 | `system/admin-external-app.html` | P0 |
| P-SYS-ADMIN-ORG | 组织结构 | `system/admin-org.html` | P1 |
| P-SYS-ADMIN-LOGS | 系统日志 | `system/admin-logs.html` | P1 |

### 2.4 弹窗/面板（3）

| ID | 场景 | 出现在 |
|----|------|--------|
| M-ADD-MEMBER | 搜索平台用户添加成员 | admin-users |
| M-CREATE-SYSTEM | 创建系统 | my-systems |
| M-TODO-PANEL | 顶栏待办快览（可选与 todos 页二选一） | 顶栏 |

---

## 3. 车系统剧本 → 页面（完整 15 步，见 design-gap-analysis §7）

| 步 | 角色 | 关键页面 |
|----|------|----------|
| 1-2 | plat_admin | login → my-systems → plat-admin-users |
| 3-10 | plat_admin | admin-home → basic → dict → datasource → module-group → module → modeling → dashboard-cfg → users → roles |
| 11-12 | plat_admin | flow / external-app（可选） |
| 13-15 | che | dashboard-che → runtime-list/form → 无管理入口 |

---

## 4. 内部审阅结论

| 角色 | 文件 | 结论 |
|------|------|------|
| analyst | `reviews/analyst.md` | pass |
| pm | `reviews/pm.md` | pass |
| uiux | `reviews/uiux.md` | pass |
| backend | `reviews/backend.md` | pass |
| frontend | `reviews/frontend.md` | pass |
| test | `reviews/test.md` | pass |

**PM 裁决：** 无 open P0 design issue（ISS-D03 待原型 v3 验证后关闭）。

---

## 5. Open Design 输入

| 文件 | 用途 |
|------|------|
| `ui-spec.md` | 字段、按钮、状态、中文文案 |
| **`config-spec.md`** | **配置态/运行态细粒度：按钮清单、字段抽屉、列表/场景/映射** |
| `prototype-brief.md` | Open Design 全量主 brief |
| `design-scope.md` | Design vs Build 分期裁决 |
| `design-gap-analysis.md` | v1 vs v3 差距；OD 检查清单 |
| `prototypes/DESIGN.md` | token（可 OD 更新） |
| 本文件 §2 | 不得漏页 |

**P0 原型最低数量：** **28** 个 HTML（§2 中 P0 行；dashboard-che/dashboard-admin 分别计入 HTML）。

---

## 6. 内部完工签字（非用户）

| 字段 | 值 |
|------|-----|
| design_package_complete | true |
| completed_at | 2026-06-15 |
| signed_by | conductor（依据 reviews/* 全 pass） |
| open_escalated | 0 |

---

## 7. 给用户的一句话

设计包已按 RES-007 更新，请打开 Open Design，粘贴 `prototype-brief.md` 全文作为 brief，输出到 `docs/design/prototypes/redesign/`。
产出后只需审视觉与细节，在 `user-approval.md` 签字。
