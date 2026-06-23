# 开发分期（Development Phases）

> 与 [`tasks/plan.md`](../tasks/plan.md) 对齐 · 状态维护于 `docs/progress.md`

---

## P0 · 细节冻结（当前）

| 项 | 内容 |
|----|------|
| **入口** | PRD 冻结、用户授权开发 |
| **范围** | page-inventory、che-system-seed、config-spec、核心原型 pass |
| **并行** | T001 原型 / T004 E2E 草案 / T005~T007 契约草稿 |
| **退出** | `detail-freeze.md` A 全 ✅；B1–B5 ✅；B6–B10 完成 |
| **负责人** | uiux + pm |

---

## P1 · 契约冻结

| 项 | 内容 |
|----|------|
| **入口** | P0 退出 |
| **范围** | `docs/api/api.md`、`db-impact.md`、`test/contract.md` |
| **活动** | backend/frontend/test 对 174 端点 + 新 IA 字段做 gap 评审 |
| **退出** | `gates.api_frozen = true` |
| **阻塞** | 新 IA 必需但 API 无字段 → 必须先改契约 |

---

## P2 · 工程基线

| 项 | 内容 |
|----|------|
| **入口** | P1 退出 |
| **范围** | sql 导入、mvn compile、npm build |
| **并行** | T020 sql ∥ T021 backend ∥ T022 frontend |
| **退出** | 构建报告 `docs/evidence/build-report.md` pass |

---

## P3 · 后端验证

| 项 | 内容 |
|----|------|
| **入口** | P2 退出 |
| **范围** | 车系统种子数据、SYS-001、RUN、RBAC、DICT 冒烟 |
| **退出** | Postman/脚本 核心 API 全 200 |
| **不做** | 新 UI |

---

## P4 · 前端壳与路由

| 项 | 内容 |
|----|------|
| **入口** | P2 退出（可与 P3 并行） |
| **范围** | 平台壳、系统运行态壳（module-rail）、系统后台壳 |
| **并行** | T040 ∥ 后开始 T050/T060/T070 |
| **退出** | 三壳路由可切换、che 见/不见后台 |

---

## P5 · 全页功能（最大工作量）

| 子期 | 范围 | 可并行 |
|------|------|--------|
| P5a | 运行态 list/form/detail+审批侧栏 | 前端 runtime |
| P5b | 建模/字典/模块/仪表盘配置 | 前端 module-config |
| P5c | 成员/角色四 Tab | 前端 system |
| P5d | 平台后台 7 页 | 前端 platform |
| P5e | 流程配置+运行审批 | frontend+flow |

**退出:** `page-inventory.md` MVP 列全部可实现且已做

---

## P6 · 整合与 E2E

| 项 | 内容 |
|----|------|
| **入口** | P5 退出 |
| **范围** | `e2e-car-system.md` 15 步浏览器剧本 |
| **退出** | 证据 `docs/evidence/e2e-car-system.md` + 截图 |

---

## P7 · 部署上线

| 项 | 内容 |
|----|------|
| **入口** | P6 退出 |
| **范围** | dist、nginx、种子账号、DEPLOY 验证 |
| **退出** | 用户可试用全功能 MVP |

---

## 状态字段（progress.md）

```yaml
current_phase: P0-detail-freeze
phase_status:
  P0: in_progress
  P1: pending
  P2: pending
  ...
fullProjectDeployable: false
```
