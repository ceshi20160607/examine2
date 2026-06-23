# 全页面配置清单（Page Inventory）

> **版本:** 1.0.0 · 2026-06-17  
> **用途:** Coding 时每一页的按钮、字段、面板、权限、API 的唯一索引。  
> **数据:** 样例一律用 [`che-system-seed.md`](./che-system-seed.md)  
> **细粒度:** 字段抽屉/映射见 [`config-spec.md`](./config-spec.md)

---

## 0. 壳与导航（全局）

| 壳 | 顶栏 | 左栏 | 禁止 |
|----|------|------|------|
| 平台工作台 | Logo·上下文·待办·消息·用户 | 我的系统/Flow/应用/日志 | 侧栏待办消息 |
| 系统运行态 | Logo·**仪表盘+模块组**·待办·消息·用户 | **点组后出现组内模块** | che 见系统后台 |
| 系统配置态 | 同运行态 + 系统后台 Tab | 后台分组菜单 | che 不可进 |
| 平台后台 | 平台工作台 Tab | 概览/用户/角色/Flow/应用/日志/配置 | — |

---

## 1. 平台层（15 页）

### P-LOGIN `platform/login.html`

| 区域 | 控件 | 说明 |
|------|------|------|
| 表单 | 账号、密码 | 必填 |
| 按钮 | 登录 | 主按钮 → my-systems |
| 链接 | 注册、忘记密码 | 次链接 |
| API | AUTH-002 | |

### P-REGISTER `platform/register.html`

| 字段 | 必填 |
|------|:----:|
| 姓名、手机、邮箱、密码、确认密码 | ✅ |
| 系统名称、系统编码 | ✅ |
| 按钮 | 注册并创建系统 | 主 → 系统后台引导 |
| API | AUTH-001, PLAT-002 | |

### P-MY-SYSTEMS `platform/my-systems.html`

| 按钮 | 位置 | 说明 |
|------|------|------|
| 进入系统 | 卡片 | → SYS-001 后进仪表盘 |
| 创建系统 | 页头 | 弹窗 M-CREATE-SYSTEM |
| 平台后台 | 底栏/侧栏 | 管理员可见 |
| 列 | 系统名、角色、最近访问、状态 | 车系统样例 |
| API | PLAT-001, SYS-001 | |

### P-PLAT-TODOS / P-PLAT-MESSAGES

| 列 | 按钮 |
|----|------|
| 标题、来源系统、时间、状态 | 标记已读、跳转处理 |
| 样例 | 「京B·67890 待年检审批」 |

### P-PLAT-ADMIN-OVERVIEW `plat-admin-overview.html`

| 区块 | 内容 |
|------|------|
| KPI 卡 | 系统数、用户数、今日登录 |
| 快捷 | 用户管理、Flow、应用 |
| 无 | generic-table 占位 |

### P-PLAT-ADMIN-USERS `plat-admin-users.html`

| 列 | 按钮 |
|----|------|
| 登录账号、姓名、手机、状态、最近登录 | 新建、编辑、停用 |
| 样例 | plat_admin, chenhe, zhangwei |
| API | PLAT-006~008, PLAT-013~016 | |

### P-PLAT-ADMIN-ROLES `plat-admin-roles.html`

| 结构 | 说明 |
|------|------|
| 列表 | 角色名、菜单权限摘要 |
| 编辑 | 菜单树勾选（平台级 PLAT_*） |
| API | PLAT-009, PLAT-010 | |

### P-PLAT-ADMIN-FLOW `plat-admin-flow.html`

| 结构 | 三栏 |
|------|------|
| 左 | 节点面板 |
| 中 | 画布 |
| 右 | **节点属性**（名称、审批人、绑定 Flow scope） |
| 样例 | 平台应用调用 → 平台管理员审批 |

### P-PLAT-ADMIN-APPS `plat-admin-apps.html`

| 列 | 编辑面板 |
|----|----------|
| 应用名、AppKey 脱敏、状态、绑定 Flow | scope 勾选、IP 白名单 |
| 样例 | 「车系统数据开放」绑定 Flow |

### P-PLAT-ADMIN-LOGS / P-PLAT-ADMIN-CONFIG

| 页 | 列/表单 |
|----|---------|
| 日志 | 时间、操作人、模块、摘要、IP |
| 配置 | key-value 表单（非 generic-table） |

---

## 2. 系统运行态（5 页）

### P-RUNTIME-LIST `system/runtime-list.html` ★ 标准页

见 `config-spec §1.1` + `ui-spec §2.5`。  
**样例数据:** seed §9 · **API:** RUN-002~010

### P-RUNTIME-FORM `system/runtime-form.html`

| 字段 | 见 seed §7 |
|------|------------|
| 按钮 | 保存(主)、取消、保存并继续 |
| 布局 | 50%/100% 按建模配置 |
| API | RUN-003, RUN-004 | |

### P-RUNTIME-DETAIL

与 list 右侧详情同结构；独立页 P1 可选。

### P-SYS-DASHBOARD `dashboard-che.html` / `dashboard-admin.html`

| 组件 | che | admin |
|------|-----|-------|
| 统计卡 | 4 张 seed §10 | 同 + 管理快捷 |
| 模块组入口 | 车辆管理卡片 | 同 |
| 壳 | **runtime-shell** module-rail | 同 + 系统后台入口 |
| 禁止 | 系统后台入口 | — |

---

## 3. 系统后台（14 页）

### P-SYS-ADMIN-HOME `admin-home.html`

| 区块 | 内容 |
|------|------|
| 步骤条 | 15 步配置链（basic→…→publish） |
| 进度 | 已完成 8/15 |
| 快捷 | 未完成项链接 |

### P-SYS-ADMIN-BASIC `admin-basic.html` ✅

| 字段 | 样例 |
|------|------|
| 系统名称、编码、图标、访问域名 | 车系统 / che_system |

### P-SYS-ADMIN-DICT `admin-dict.html`

| 结构 | 左类型列表 · 右字典项表 |
|------|-------------------------|
| 样例 | 车辆状态 5 项 seed §5.1 |
| API | DICT-* | |

### P-SYS-ADMIN-DATASOURCE `admin-datasource.html`

| Tab | 列 |
|-----|-----|
| 系统内 | 名称、模块、公式、状态 |
| 外部 API | URL、缓存、状态 |
| 数据库 | 连接串(脱敏)、只读 SQL |
| 样例 | 「车辆总数」COUNT 车辆档案 |

### P-SYS-ADMIN-DASHBOARD-CFG `admin-dashboard-cfg.html`

| 三栏 | 内容 |
|------|------|
| 左 | 组件库：统计卡、模块组卡、筛选按钮、图表占位 |
| 中 | 栅格画布（拖拽占位） |
| 右 | 属性：数据源、标题、图标、筛选绑定 |
| 按钮 | 预览、保存、发布 |
| **禁止** | 仅展示运行态 dashboard 预览而无设计器 |

### P-SYS-ADMIN-USERS `admin-users.html`

| 列 | 样例 seed §3 |
|----|--------------|
| 姓名、账号、部门、角色、状态 | 陈和、张伟… |
| 按钮 | **邀请成员**(主) → 弹窗 M-ADD-MEMBER |
| 弹窗 | 搜索平台用户、选角色、确认 |
| API | MEM-* | |

### P-SYS-ADMIN-ROLES `admin-roles.html` ✅

见 `config-spec §9.4` · 左列表 + 四 Tab

### P-SYS-ADMIN-MODULE-GROUP / MODULE

| 页 | 列 | 样例 |
|----|-----|------|
| 模块组 | 名称、排序、发布状态、模块数 | 车辆管理 |
| 模块 | 所属组、名称、编码、状态 | 车辆档案、维保、驾驶员 |

### P-SYS-ADMIN-MODELING `admin-modeling.html` ✅

见 `config-spec §2~§8`

### P-SYS-ADMIN-FLOW `admin-flow.html` ✅

三栏 + seed §11

### P-SYS-ADMIN-EXTERNAL-APP `admin-external-app.html`

| 列表列 | 编辑抽屉 |
|--------|----------|
| 应用名、AK 脱敏、状态、授权范围 | Tab：基础 / **参数映射** / 调用日志 |
| 映射表 | config-spec §9.1 · seed §12 |
| 按钮 | 新建应用(主)、轮换密钥 |

---

## 4. 弹窗（3）

| ID | 触发 | 字段 | 按钮 |
|----|------|------|------|
| M-ADD-MEMBER | admin-users 邀请 | 搜索平台用户、角色、部门 | 取消、确认添加 |
| M-CREATE-SYSTEM | my-systems | 系统名、编码 | 取消、创建 |
| M-COLUMN-SETTINGS | runtime-list 列设置 | 列显隐、顺序、宽度、固定 | 重置、保存 |

---

## 5. 页面 → API 快速索引

| 页面 ID | 主要 API 组 |
|---------|-------------|
| runtime-list | RUN, SYS-001 |
| admin-modeling | FIELD, UI, MOD |
| admin-roles | RBAC-005~013 |
| admin-users | MEM-001~007 |
| admin-flow | FLOW-001~006 |
| admin-dict | DICT-001~011 |
| plat-admin-* | PLAT-006~012 |

完整 174 端点见 `frontend/docs/api-contract-map.md`。

---

## 6. MVP vs 增强（Build 不扩 scope）

| 页 | MVP 必做 | 增强 |
|----|----------|------|
| runtime-list | 骨架九点+CRUD+场景+导出 | 导入、批量编辑 |
| admin-flow | 审批+条件+抄送 | 外部API实调、加签 |
| admin-external-app | 映射表 UI+单模块 | 嵌套 JSON |
| admin-dashboard-cfg | 统计卡+模块组卡 | 拖拽、图表 |
| plat-admin-flow/apps | 配置 IA | 全链路开放 |
