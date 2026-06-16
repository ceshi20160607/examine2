# 设计差异报告（v3 原型审阅）

> **日期:** 2026-06-15  
> **对照:** `design-package.md` v1.1、`design-gap-analysis.md` §8  
> **原型路径:** `docs/design/prototypes/`（platform/ + system/）

## 1. 总览

| 项 | 结果 |
|----|------|
| P0 页面数量 | **21/21 ✅** |
| P1 额外产出 | **+3**（flow、apps、logs） |
| P1 仍缺 | **4**（可接受，见 §4） |
| P0 IA 断言 | **11/12 通过** |
| 阻塞签字 | **无 P0** |
| 建议 | 可先审阅签字；§3 小偏差可在 notes 标注或二期改 |

---

## 2. P0 页面清单核对

### 平台 7/7 ✅

login、register、my-systems、todos、messages、plat-admin-overview、plat-admin-users

### 运行态 4/4 ✅

dashboard-che、dashboard-admin、runtime-list、runtime-form

### 系统后台 10/10 ✅

admin-home、admin-basic、admin-dict、admin-datasource、admin-dashboard-cfg、admin-module-group、admin-module、admin-modeling、admin-members、admin-flow、admin-external-app

> 计 21 页（index 另计）

---

## 3. P0 IA 断言

| # | 断言 | 结果 | 说明 |
|---|------|------|------|
| 1 | 平台侧栏无待办/消息文字 | ✅ | my-systems 仅顶栏图标 |
| 2 | 顶栏模块组 + 左栏组内模块 | ✅ | runtime-shell 已实现 |
| 3 | che 无系统后台 | ✅ | dashboard-che 无入口 |
| 4 | admin 有系统后台 | ✅ | dashboard-admin 右上 |
| 5 | 导出在列表工具栏 | ✅ | runtime-list `data-export` |
| 6 | 无「应用与模块」 | ✅ | admin-modeling 正确 |
| 7 | 字典关联字段 | ✅ | 建模表「字典·车辆状态」 |
| 8 | 角色三维权限 | ✅ | 模块/字段/数据权限 Tab |
| 9 | 流程节点面板 | ✅ | 审批/条件/抄送/外部API |
| 10 | 对外应用参数映射 | ✅ | 映射表多行 |
| 11 | 配置化仪表盘 | ✅ | 统计卡+筛选按钮 |
| 12 | 顶栏独立「仪表盘」Tab | ⚠️ | 用侧栏「概览」代替，见 §5 |

---

## 4. P1 遗漏（不阻签字）

| 页面 | 说明 |
|------|------|
| `system/runtime-detail.html` | 详情可用列表行「查看」代替 |
| `system/flow-todo.html` | 审批处理运行态 |
| `system/admin-org.html` | 组织结构 |
| `system/admin-logs.html` | 系统内审计日志 |

---

## 5. 小偏差（P2，可选改）

| 项 | 现状 | 规格建议 |
|----|------|----------|
| 顶栏「仪表盘」 | 仅「车辆管理」组按钮；仪表盘在左栏「概览」 | 顶栏增加「仪表盘」与模块组并列 |
| 列表页左栏 | runtime-list 无「概览」 | 与 dashboard 侧栏保持一致 |
| 表单附件 | runtime-form 无附件区 | P1 占位「添加附件」 |
| 状态下拉 | 有选项但未写「来自字典」 | 加 field-help 文案即可 |
| 技术 ID | 对外应用表有 App ID | 管理页可接受；运行态须避免 |

---

## 6. 超额完成

| 文件 | 级别 |
|------|------|
| `platform/flow.html` | P1 |
| `platform/apps.html` | P1 |
| `platform/logs.html` | P1 |

---

## 7. 车系统剧本可走通性

| 步 | 可浏览 |
|----|--------|
| 登录→我的系统 | ✅ |
| 平台用户 | ✅ |
| 后台配置链 home→basic→dict→datasource→module→modeling→dashboard-cfg | ✅ |
| 成员+che | ✅ |
| che 仪表盘+列表+表单 | ✅ |
| che 无后台 | ✅ |

---

## 8. uiux 结论

**v3 相对 v1 已对齐 RES-001~004 与 design-package（21 P0 页）。**

**RES-005（2026-06-15 用户补充）后新增缺口 — 签字前建议补 v3.1 或 notes 注明：**

| 域 | v3 现状 | 目标 |
|----|---------|------|
| 平台 Flow/应用**配置** | flow/apps 仅 P1 占位 | 平台后台 `plat-admin-flow` / `plat-admin-apps` |
| 平台 Flow→应用开放 | 未演示 | 平台应用页展示「绑定 Flow」 |
| 业务列表 | 简易搜索+导出 | 高级搜索、场景、序号+勾选、批量条、导入、列设置 |
| 详情 | 无抽屉 | 列表行 → 右侧详情抽屉 |
| 建模列表场景 | 未在 admin-modeling | 列表场景 Tab/区块 |

无 RES-001~004 的 P0 **页面数**遗漏；RES-005 为**体验与平台配置加深**，不推翻已冻结 IA。

---

## 9. v3.1 补齐记录（RES-006）

| 补齐项 | v3.1 落点 | 验收要点 |
|------|----------|----------|
| 业务数据列 | `system/runtime-list.html` | 车牌号、品牌型号、车辆状态、归属部门、驾驶员、购置日期、年检到期、最近维保、操作列 |
| 检索与高级筛选 | `system/runtime-list.html` | 快捷搜索 + 高级筛选抽屉；筛选字段覆盖状态、部门、驾驶员、购置日期、年检到期、最近维保 |
| 列表场景 | `system/runtime-list.html`、`system/admin-modeling.html` | 运行态展示全部车辆、在用车辆、待年检、维保中、我负责的；建模侧可配置默认条件和默认列集 |
| 设置与表头 | `system/runtime-list.html`、`system/admin-modeling.html` | 列设置支持显示/隐藏、拖拽排序、列宽、固定列、保存为我的表头 |
| 导出 | `system/runtime-list.html` | 工具栏固定“全部导出”；勾选后批量条展示“导出选中”，二者范围明确区分 |
| 排序与序号 | `system/runtime-list.html` | 序号列、表头排序态、当前排序说明 |
| 勾选后操作限制 | `system/runtime-list.html`、`system/admin-modeling.html` | 转移、删除、导出选中、批量编辑按权限和数据状态启用/禁用，并展示禁用原因 |
| 详情布局 | `system/runtime-list.html` | 行点击打开右侧详情抽屉，不打断列表上下文 |
| 平台配置 | `platform/plat-admin-flow.html`、`platform/plat-admin-apps.html` | Flow 与应用在平台后台单独配置；应用页体现绑定 Flow 和开放范围 |

v3.1 已补齐用户在 2026-06-15 追加的列表体验要求；签字前仍需用户在 `user-approval.md` 勾选确认。

---

## 10. redesign 待验收项（RES-007）

用户确认当前主要问题不是能力缺失，而是顶部控件信息密度与页面 IA；新的全量 brief 已将这些要求合入主设计目标：

| 待验收项 | redesign 目标 |
|----------|-----------|
| 列表首屏 | 1440×900 首屏至少展示 8 行业务数据 |
| 场景 | 不再横向铺满一整行；改为当前场景下拉/少量快捷场景 |
| 搜索 | 搜索框内置触发行为；高级筛选用抽屉；已应用筛选用 chips |
| 选择项目/系统 | 放在顶栏或标题行小下拉，不做大卡片/大区域 |
| 主按钮 | 全页只保留一个“新建”主按钮 |
| 批量条 | 勾选后才出现，高度不超过 40px，禁用原因短提示 |
| 用户/角色 | 系统成员、系统角色、平台用户、平台角色拆成独立页面 |

redesign 原型尚未生成；生成后需在本节追加实际验收结论。

---

## 11. 术语对照（RES-005）

| 用户说法 | 设计术语 |
|----------|----------|
| 平台层 flow/应用单独设置 | 平台后台配置；侧栏为运行入口 |
| 平台 flow 通过应用对外开放 | 平台应用 scope 授权平台 Flow API |
| 高级搜索 | 高级搜索抽屉 + 可配置搜索字段 |
| 表头自定义 | 列设置 + 用户个人表头偏好 |
| 添加场景配置 | 建模「列表场景」+ 运行态「保存视图」 |
| 右侧弹窗 | **右侧详情抽屉**（非 modal） |
