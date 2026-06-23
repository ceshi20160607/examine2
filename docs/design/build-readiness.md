# 设计完备性 → 开发就绪评估

> **日期:** 2026-06-17  
> **决策:** 用户授权移除 design gate，直接进入 Build；以 `.oldbk` 实现 + 新原型 UI 为准。

## 1. 项目结构现状

| 路径 | 状态 |
|------|------|
| `docs/design/` | ✅ PRD、ui-spec、config-spec、prototype-brief、37 页 HTML 原型 |
| `docs/api/` | ❌ 尚未生成（Build 期从 `.oldbk` 接口反推并冻结） |
| `backend/` | ❌ 根目录无（`.oldbk/backend/` 有完整 Maven 多模块） |
| `frontend/` | ❌ 根目录无（`.oldbk/frontend/` 有 Vite + TS 工程） |
| `sql/` | ❌ 根目录无（`.oldbk/sql/init.sql`） |

## 2. 原型完备性（P0 28 页）

| 等级 | 数量 | 代表页 |
|------|------|--------|
| **pass** | 7 | login, register, my-systems, runtime-list, runtime-form, admin-basic, admin-modeling |
| **partial** | 21 | 多数为 `generic-table` + `名称1` 占位 |
| **missing** | 0 | 文件齐全 |

**运行态骨架（ui-spec §2.5）：** `runtime-list.html` ✅  
**配置细粒度（config-spec §10）：** `admin-modeling.html` ✅  

**编码 blocker（原型层，不阻后端）：**

1. `admin-roles.html` — 缺左列表 + 四 Tab 权限编辑区  
2. `admin-flow.html` / `plat-admin-flow.html` — 缺右侧节点属性栏  
3. `admin-external-app.html` — 缺参数映射抽屉  
4. ~21 页占位数据 — 前端实现时按 pass 页 + config-spec 补 UI  

**结论：** 规格文档（ui-spec + config-spec + brief）**足够驱动开发**；HTML 原型为**参考样张**，不必等 28 页全 polish 再写代码。

## 3. Build 策略

1. **恢复代码基线：** 从 `.oldbk` 复制 `backend/`、`sql/`、`frontend/` 到根目录。  
2. **UI 对齐：** 以 `runtime-list`、`admin-modeling`、`runtime-form` 为视觉/交互标准，逐页 rework frontend。  
3. **契约：** 首期以现有 REST 接口为事实标准，写入 `docs/api/api.md`。  
4. **验收：** 车系统 E2E（登录 → 建模 → 运行态 CRUD → 审批简链路）。

## 4. 用户授权

用户 2026-06-17 明确：移除审阅闸门限制，授权全量开发与上线目标。
