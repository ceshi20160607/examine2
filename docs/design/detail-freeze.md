# 细节冻结清单（Coding 前 Gate）

> **规则:** 本清单全部 ✅ 后，才允许进入 Build 写业务代码。  
> **维护:** Conductor · 2026-06-17

---

## A. 产品规格（文档）

| # | 项 | 权威文件 | 状态 |
|---|-----|----------|:----:|
| A1 | PRD + MVP 边界 | `docs/product/prd.md` | ✅ |
| A2 | IA + 运行态骨架 §2.5 | `docs/design/ui-spec.md` | ✅ |
| A3 | 按钮/字段/映射细粒度 | `docs/design/config-spec.md` | ✅ |
| A4 | 全页按钮字段清单 | `docs/design/page-inventory.md` | ✅ |
| A5 | 车系统种子数据 | `docs/design/che-system-seed.md` | ✅ |
| A6 | 设计范围 Build 分期 | `docs/design/design-scope.md` | ✅ |
| A7 | 用户授权开发 | `docs/design/user-approval.md` | ✅ |

## B. 原型（HTML 结构样张）

| # | 页 | 必须结构 | 状态 |
|---|-----|----------|:----:|
| B1 | runtime-list | §2.5 九点 + 车系统数据 | ✅ |
| B2 | admin-modeling | 六 Tab + 字段抽屉 | ✅ |
| B3 | runtime-form | 车辆表单字段 | ✅ |
| B4 | admin-roles | 左角色 + 四 Tab | ✅ |
| B5 | admin-flow | 三栏+节点属性 | ✅ |
| B6 | admin-external-app | 列表+映射抽屉 | ✅ |
| B7 | admin-users | 列表+邀请成员弹窗 | ✅ |
| B8 | admin-dashboard-cfg | 组件库+画布+属性 | ✅ |
| B9 | plat-admin-flow | 三栏 | ✅ |
| B10 | 其余 P0 页 | 车系统真实数据非占位 | 🔄 T001b |

**B 组目标:** P0 28 页 **pass ≥ 20**，partial 页须引用 `che-system-seed.md`。

## C. 契约（Contract）

| # | 项 | 文件 | 状态 |
|---|-----|------|:----:|
| C1 | API 端点事实标准 | `frontend/src/api/endpoints.ts` | ✅ |
| C2 | 前端路由映射 | `frontend/docs/api-contract-map.md` | ✅ |
| C3 | 合并冻结 api.md | `docs/api/api.md` | 🔄 骨架+待合并 |
| C4 | DB 与 IA 差异 | `docs/api/db-impact.md` | ✅ |
| C5 | 测试契约 | `docs/test/contract.md` | ✅ |

## D. 任务与分期

| # | 项 | 文件 | 状态 |
|---|-----|------|:----:|
| D1 | 总任务计划 | `docs/tasks/plan.md` | ✅ |
| D2 | 开发分期 | `docs/phases/development-phases.md` | ✅ |
| D3 | E2E 验收剧本 | `docs/test/e2e-car-system.md` | ✅ |
| D4 | 进度看板 | `docs/progress.md` | ✅ |

## E. 工程基线

| # | 项 | 状态 |
|---|-----|:----:|
| E1 | `backend/` 自 `.oldbk` 恢复 | ✅ |
| E2 | `frontend/` 自 `.oldbk` 恢复 | ✅ |
| E3 | `sql/init.sql` | ✅ |
| E4 | `docs/service_info.md` + `DEPLOY.md` | ✅ |
| E5 | 本机 mvn/npm build 通过 | ⬜ 待开发机 |

---

## 冻结结论

| 阶段 | 条件 | 当前 |
|------|------|------|
| **可开 Contract** | A 全 ✅ | ✅ |
| **可开 Build** | A 全 ✅ + B1–B9 ✅ + C4/C5 + D 全 ✅ + T010 api 冻结 | 🔄 差 T010 |
| **可上线** | Build 完成 + E5 + E2E pass | ⬜ |

**下一步（自动）:** 补齐 B6–B10 原型 → 写 C3–C5 → 冻结 D → 按 `development-phases.md` 分批 Coding。
