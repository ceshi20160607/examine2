# Open Design 执行 Brief（v3 — 21 页 P0）

> **差距：** v1 仅 9 页且 IA 错误，见 `design-gap-analysis.md` — **勿改 v1，输出到 `v3/`**

> **前置：** 团队内部 `design_package_complete = true`（见 `design-package.md`）  
> **输出目录：** `docs/design/prototypes/v3/`  
> **风格：** Linear / Notion 克制企业后台 · 中文 · Naive UI 气质 · token 见 `prototypes/DESIGN.md`

---

## 产品

unexamine — 企业可配置业务系统平台。MVP 验收剧本：**车系统 + 成员 che**。

---

## 强制 IA 规则（违反即失败）

1. **模块组导航：** 运行态顶栏显示模块组名（如「车辆管理」）；点击后左侧栏显示组内模块（车辆档案、维保记录、驾驶员）；右侧业务内容。
2. **配置顺序：** 先模块组 → 再组下模块 → 字段 → 页面动作 → 菜单 → 发布。**禁止「应用与模块」父级结构。**
3. **对外应用：** 系统后台独立页，本系统对外 API 入口，与模块无关。
4. **平台待办/消息：** 仅顶栏图标+角标；平台侧栏**不得**出现待办/消息文字菜单。
5. **导出：** 仅在业务列表工具栏，禁止「导出中心」导航。
6. **che 视角：** 无系统后台、无平台后台。

---

## 须生成的 P0 页面（21 个 HTML + index）

### platform/

| 文件 | 说明 |
|------|------|
| `login.html` | 居中卡片登录 |
| `register.html` | 单页：账号+密码+系统名 |
| `my-systems.html` | 侧栏：我的系统/Flow/应用/日志；顶栏待办消息图标；底栏平台后台 |
| `todos.html` | 待办列表（顶栏点击进入） |
| `messages.html` | 消息列表 |
| `plat-admin-overview.html` | 平台后台概览卡片 |
| `plat-admin-users.html` | 平台用户 CRUD |

### system/

| 文件 | 说明 |
|------|------|
| `dashboard-che.html` | **配置驱动**：统计卡+筛选按钮+模块组卡；顶栏组导航 |
| `dashboard-admin.html` | 同上 + 系统后台入口 |
| `admin-basic.html` | 系统名称、图标、编码、**域名/访问地址** |
| `admin-dict.html` | 字典类型+字典项；说明可关联字段下拉 |
| `admin-datasource.html` | 三类型 Tab：系统内(含公式)/外部API/数据库直连 |
| `admin-dashboard-cfg.html` | 组件面板+栅格画布+绑定数据源/图标/筛选 |
| `admin-home.html` | 系统后台引导首页 |
| `admin-members.html` | 成员 Tab + 角色 Tab **三维权限**（模块/字段/数据三子 Tab） |
| `admin-module-group.html` | 模块组列表+新建 |
| `admin-module.html` | 选定组下的模块列表 |
| `admin-modeling.html` | 字段、页面动作(含导出)、菜单、发布清单 |
| `admin-flow.html` | 流程画布+节点面板（审批/条件/抄送/外部API；动态加签标二期） |
| `admin-external-app.html` | 列表+新建 + **参数映射表**（字段→接口参数 3~5 行） |
| `runtime-list.html` | **完整运行态壳** + 车辆列表 + 工具栏导出 |
| `runtime-form.html` | 同上壳 + 新建车辆表单 |

### 根

| 文件 | 说明 |
|------|------|
| `index.html` | 导航索引，链到以上全部页面 |
| 复用 `../css/app.css` `../js/app.js` 或 v3 下新建 |

---

## 车系统 Demo 数据

- 模块组：车辆管理
- 组内模块：车辆档案、维保记录、驾驶员
- 成员：plat_admin（管理员）、che（陈和，车辆操作员）
- 车辆样例：京A·12345

---

## P1（OD 有余力再做，共 7 页）

`platform/flow.html`、`apps.html`、`logs.html`  
`system/runtime-detail.html`、`flow-todo.html`、`admin-org.html`、`admin-logs.html`

## 系统后台侧栏（所有 admin-* 页须一致）

首页引导 → 基础参数 → 数据字典 → 数据源 → 仪表盘配置 → 模块组 → 模块 → 建模 → 成员与角色 → 流程 → 对外应用

- 应用下挂模块的文案或结构
- 侧栏一级展平所有模块而无顶栏组
- 裸露 moduleId/API 编号给用户
- 平台侧栏待办/消息重复

---

## 完成后

uiux 写 `design-diff.md`；用户审 `user-approval.md`。
