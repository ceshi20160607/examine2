# 设计差距分析（v1 原型 vs 目标 vs 最终愿景）

> **日期:** 2026-06-15  
> **对照基准:** `design-package.md` v1.1、`prototype-brief.md`、`user_requirement.md` 附录 A、PRD 车系统剧本  
> **v1 原型路径:** `prototypes/prototypes/**`（**作废**，仅作差距参考）  
> **v3 目标路径:** `prototypes/v3/**`（待 Open Design 生成）

---

## 1. 总览

| 维度 | v1 现状 | v3 目标 | 差距 |
|------|---------|---------|------|
| P0 HTML 数量 | **9** | **21** | 缺 **12** 页 |
| IA 正确性 | 多处错误 | RES-001~004 | v1 **不可**作实现依据 |
| ui-spec 覆盖 | 部分 | 应全覆盖 P0 | 已补 §后台侧栏、日志、附件 |
| 车系统剧本 | 缺配置链多步 | 完整配置→使用 | 剧本已扩展 §3 |

**结论：** v1 仅保留 css/token 参考；**必须**按 v3 brief 重做全部 P0 原型。

---

## 2. v1 原型逐页差距

| v1 文件 | 状态 | 主要问题 | v3 要求 |
|---------|------|----------|---------|
| `login.html` | ⚠️ 可复用骨架 | 基本可用 | 保留风格，链到 v3 |
| `register.html` | ⚠️ | 成功应进 `admin-home` 向导 | 改引导文案 |
| `my-systems.html` | ❌ | 侧栏重复待办/消息；无 plat-admin 链 | 侧栏仅4项+顶栏图标 |
| `dashboard-che.html` | ❌ | 无顶栏模块组；侧栏展平模块；静态欢迎卡 | 配置驱动统计卡+顶栏组 |
| `dashboard-admin.html` | ❌ | 同上 | 同上+系统后台入口 |
| `admin-members.html` | ⚠️ | 无角色三维权限 Tab | 加厚三 Tab |
| `admin-modeling.html` | ❌ | 标题「应用与模块」；无模块组；无字典关联 | 字段/动作/菜单/发布；字典下拉 |
| `runtime-list.html` | ❌ | 无顶栏模块组壳 | 完整运行态壳+导出 |
| `runtime-form.html` | ⚠️ | 无顶栏组壳；无附件区 | 加壳；附件 P1 占位 |
| — | 缺 | — | 见 §3 缺失 P0 列表 |

---

## 3. 缺失 P0 页面（v1 完全没有）

### 平台（4）

| 文件 | ID |
|------|-----|
| `platform/todos.html` | P-PLAT-TODOS |
| `platform/messages.html` | P-PLAT-MESSAGES |
| `platform/plat-admin-overview.html` | P-PLAT-ADMIN-OVERVIEW |
| `platform/plat-admin-users.html` | P-PLAT-ADMIN-USERS |

### 系统后台（8）

| 文件 | ID |
|------|-----|
| `system/admin-home.html` | P-SYS-ADMIN-HOME |
| `system/admin-basic.html` | P-SYS-ADMIN-BASIC |
| `system/admin-dict.html` | P-SYS-ADMIN-DICT |
| `system/admin-datasource.html` | P-SYS-ADMIN-DATASOURCE |
| `system/admin-dashboard-cfg.html` | P-SYS-ADMIN-DASHBOARD-CFG |
| `system/admin-module-group.html` | P-SYS-ADMIN-MODULE-GROUP |
| `system/admin-module.html` | P-SYS-ADMIN-MODULE |
| `system/admin-flow.html` | P-SYS-ADMIN-FLOW |
| `system/admin-external-app.html` | P-SYS-ADMIN-EXTERNAL-APP |

---

## 4. P1 页面（目标有、OD 时间够再做）

| ID | 文件 | 原因 |
|----|------|------|
| P-PLAT-FLOW | `platform/flow.html` | 平台跨系统 Flow 汇总 |
| P-PLAT-APPS | `platform/apps.html` | 平台应用入口 |
| P-PLAT-LOGS | `platform/logs.html` | 平台日志 |
| P-RUNTIME-DETAIL | `system/runtime-detail.html` | 详情可抽屉代替 |
| P-SYS-FLOW-TODO | `system/flow-todo.html` | 审批处理运行态 |
| P-SYS-ADMIN-ORG | `system/admin-org.html` | 组织结构 |
| P-SYS-ADMIN-LOGS | `system/admin-logs.html` | **系统内**操作/审计日志 |

---

## 5. ui-spec / 设计包曾遗漏（本轮已补）

| 遗漏项 | 处理 |
|--------|------|
| 平台侧栏文案仍写「待办/消息」 | ui-spec §2.3 已改 |
| 系统后台**统一侧栏**顺序 | ui-spec §5.5 新增 |
| `admin-home` 引导链 | ui-spec §5.5 新增 |
| 数据字典 ↔ 字段下拉 | ui-spec §5.6c + modeling |
| 仪表盘配置 ↔ 运行态 | ui-spec §5.6 + §5.6e |
| 车系统剧本缺基础/字典/数据源/仪表盘配置步 | design-package §3 扩展 |
| PRD「不做」与可配置仪表盘矛盾 | prd §不做 已澄清 |
| 表单附件区 | ui-spec §5.10 P1 占位 |
| 创建系统弹窗 | ui-spec §5.3 + M-CREATE-SYSTEM |

---

## 6. 最终愿景 vs MVP Design（仍不做 HTML）

以下在 `user_requirement.md` 有描述，**MVP 不做独立原型页**，仅 ui-spec/增强期标注：

| 能力 | Design | 原型 | Build |
|------|--------|------|-------|
| KPI / 打印模板 | 增强期 | ❌ | 后期 |
| Excel 导入 | 增强期 | ❌ | 后期 |
| 评论/备注 | P1 表单区占位 | ⭕ | 分期 |
| 子表字段 | 增强 | ❌ | 后期 |
| 多租户切换 | 增强 | ❌ | 后期 |
| Agent 问数 | 增强 | ❌ | 后期 |
| 拖拽仪表盘自由布局 | 增强 | ❌ | 配置式栅格即可 |

---

## 7. 车系统完整剧本（配置→发布→使用）

| 步 | 角色 | 动作 | 页面 |
|----|------|------|------|
| 1 | plat_admin | 登录 | P-LOGIN |
| 2 | plat_admin | 平台用户确保有 che | P-PLAT-ADMIN-USERS |
| 3 | plat_admin | 进入车系统后台 | P-SYS-ADMIN-HOME |
| 4 | plat_admin | 系统基础参数 | P-SYS-ADMIN-BASIC |
| 5 | plat_admin | 字典「车辆状态」 | P-SYS-ADMIN-DICT |
| 6 | plat_admin | 数据源「车辆总数」 | P-SYS-ADMIN-DATASOURCE |
| 7 | plat_admin | 模块组「车辆管理」 | P-SYS-ADMIN-MODULE-GROUP |
| 8 | plat_admin | 模块「车辆档案」等 | P-SYS-ADMIN-MODULE |
| 9 | plat_admin | 字段/动作/菜单/发布 | P-SYS-ADMIN-MODELING |
| 10 | plat_admin | 仪表盘配置+发布 | P-SYS-ADMIN-DASHBOARD-CFG |
| 11 | plat_admin | 角色权限+添加 che | P-SYS-ADMIN-MEMBERS |
| 12 | plat_admin | （可选）流程/对外应用 | FLOW / EXTERNAL-APP |
| 13 | che | 登录→配置化仪表盘 | P-SYS-DASHBOARD |
| 14 | che | 顶栏组→车辆 CRUD+导出 | P-RUNTIME-* |
| 15 | che | 全局无管理入口 | — |

---

## 8. Open Design 执行检查清单

生成 v3 后逐项打勾：

- [ ] 21 个 P0 HTML 齐全
- [ ] `index.html` 链到全部 P0
- [ ] 运行态 4 页均有**顶栏模块组+左栏模块**壳
- [ ] 平台 my-systems 侧栏**无**待办/消息文字
- [ ] dashboard 为**配置统计卡**非纯欢迎语
- [ ] admin-modeling **无**「应用与模块」
- [ ] admin-members 角色 **三 Tab 权限**
- [ ] admin-flow 有节点面板
- [ ] admin-external-app 有参数映射表

---

## 9. 文档同步状态

| 文档 | 版本 | 与 gap 同步 |
|------|------|-------------|
| `design-package.md` | 1.1.0 | ✅ 本轮 |
| `ui-spec.md` | 0.3.0-res004 | ✅ 本轮 |
| `prototype-brief.md` | v3 | ✅ 本轮 |
| `design-scope.md` | +§7~11 | ✅ |
| `design-diff.md` | v1 | ⚠️ 待 v3 后重写 |
