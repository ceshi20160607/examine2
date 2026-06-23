# 参照案例：examine2 最终态（Agent 质量基线）

> **用途：** 用户只给原始需求时，Agent **必须** 对照本文件判断产出是否「够好」，而不是问用户。  
> **来源：** unexamine redesign 签字版（2026-06-17）；无代码测试 FlexBase 同族结构。

---

## 1. 必须达到的结构（不可降标）

| 项 | 标准 |
|----|------|
| 分层 | 平台工作台 + 系统后台 + 运行态 |
| 页表 | P0 先数清，零 TBD，再 HTML |
| 名词 | glossary 每项「是什么/不是什么」 |
| 导航 | 待办/消息仅顶栏；配置/运行分入口 |
| 模块组 | 顶栏组名 + 左栏模块（可配置平台） |
| 列表页 | 搜索+场景+筛选+列设置+导出+序号勾选+批量条+抽屉详情+首屏≥8行 |
| 建模页 | 六 Tab + 字段抽屉 + 发布 |
| 用户/角色 | 成员页与角色页分离 |
| 对外能力 | 系统对外应用 + 平台 API 控制台（有 API/SSO/租户需求时） |
| 数据 | seed 真实业务列，禁止 generic-table |
| 剧本 | ≤15 步配置→使用，业务用户无后台入口 |

---

## 2. 页量级参考

| 产品类型 | P0 页数量级 |
|----------|-------------|
| 平台+系统+运行态无代码 | **28～38** |
| 单应用后台 | **12～18** |

少于下限时 Agent **必须** 回看 design-package 是否漏页，不得交付。

---

## 3. 文件栈（与 examine2 对齐）

```
docs/product/rough-input, prd, design-scope, domain-glossary
docs/decisions/resolution.md          ← 全部 AUTO，无 pending
docs/design/design-package, ui-spec, config-spec, page-inventory
docs/design/*-seed.md, prototype-brief, design-diff
docs/design/prototypes/**             ← 全部 P0 HTML + index
docs/design/reviews/*.md              ← Agent 自审
```

---

## 4. 已知增页规则（相对 examine2 基线）

需求含下列词 → **在基线上加页**，不合并：

| 关键词 | 增页 |
|--------|------|
| SSO / 单点登录 | plat-admin-sso, login SSO 按钮 |
| 多租户 / tenant | plat-admin-tenants, sys-admin-tenant, 顶栏 TenantSwitcher |
| 对外 API / 集成 | plat-admin-api-console（映射表） |
| 组织架构 | sys-admin-org |
| 平台工作流 | plat-admin-flow（与 sys-admin-flow 并存） |

---

## 5. 反模式（= 未达标，需自愈）

- P0 HTML < P0 页表 90%
- 运行态无模块组壳
- 列表 < 6 列业务字段
- 管理端出现 JSON/moduleId 作主内容
- 「应用与模块」混在同一建模页
- 只交付 spec 不交付 HTML 就声称完成

自愈：补 spec → 补 HTML → 更新 design-diff → learning-loop。

---

## 6. 范例路径（examine2 仓库内，仅 Agent 可读可对齐）

| 范例 | 路径 |
|------|------|
| 最终原型 | `docs/design/prototypes/` |
| design-package | `docs/design/design-package.md` |
| config-spec | `docs/design/config-spec.md` |
| 无代码试跑 | `docs/_test/nocode-platform/` |

新项目无此路径时，以 **本节标准** 为准，不降低。
