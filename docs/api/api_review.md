# API 契约审查 · PM 决议

| 项 | 值 |
|----|-----|
| **审查日期** | 2026-06-22 |
| **冻结版本** | `1.0.0-mvp` |
| **契约文件** | [`api.md`](./api.md) |
| **PM 决议** | **通过 · 准予 MVP Build** |

---

## 1. 审查输入

| 来源 | 角色 | 结论摘要 |
|------|------|----------|
| [`frontend/docs/api-contract-map.md`](../../frontend/docs/api-contract-map.md) | Frontend | 174 端点定义完整；163 MVP；路由引用无悬空 ID |
| [`_draft/backend-gap.md`](./_draft/backend-gap.md) | Backend | 现有 Controller 基本覆盖；新 IA 需扩展 SYS-001 menu 结构与 2~3 可选端点 |
| [`_draft/frontend-route-map.md`](./_draft/frontend-route-map.md) | Frontend | 三壳路由（platform / runtime / admin）与 API 映射清晰 |
| [`db-impact.md`](./db-impact.md) | DBA | schema 基本可复用；模块组字段待 init.sql 核对，P2 migration |
| [`page-inventory.md`](../design/page-inventory.md) §5 | PM / UX | 页面→API 索引与 MVP scope 一致 |

---

## 2. PM 决议

**通过 MVP Build。** 冻结 [`api.md`](./api.md) 版本 **`1.0.0-mvp`**（2026-06-22）为 Build 期唯一 API 契约源。

### 通过条件

- 163 个 MVP 端点（§2）路径、方法、模块分组与 typed SDK 一致。
- 11 个 ENH / PLACEHOLDER / INTERNAL 端点（§3）明确排除在 MVP 验收之外。
- §4 所列 8 项 gap 登记为 **P1 增强**，不阻塞当前 Build 启动。

### 附带约束

1. Build 不得删除或重命名 §2 已有端点。
2. 模块组导航（G1）、列偏好（G4）等增强须升版契约（`1.1.x`）后再改语义。
3. DBA 在 P2 退出检查前完成 init.sql 模块组字段核对（见 `db-impact.md` §5）。

---

## 3. 已知风险（已接受）

| 风险 | 缓解 |
|------|------|
| SYS-001 menu 尚无 group 层级 | Build 先扩展响应；前端 fallback 扁平菜单 |
| 运行态详情 + 审批同屏 | 双请求 RUN-005 + FLOW-011 |
| 仪表盘 designer API 缺失 | JSON stub |
| 导入 IMP-* 为占位 | runtime-list 导入功能不在 MVP scope |

---

## 4. 下一步

- [ ] Backend / Frontend 以 `api.md` §2 为契约实现与联调基线
- [ ] Test 参照 `../test/contract.md`（待产出）编写契约测试
- [ ] P1 增强项在 MVP 闭环后单独排期升版

---

**签字：** PM · 2026-06-22 · **PASS · freeze `1.0.0-mvp`**
