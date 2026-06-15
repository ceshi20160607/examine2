# Phase 2: Contract

## 入口

- `gates.design_user_approved = true` **（必须）**

## 步骤

| # | Worker | 输出 |
|---|--------|------|
| 1 | dba | `docs/api/_draft/db-impact.md` |
| 2 | backend | `docs/api/_draft/backend-proposal.md` |
| 3 | frontend | `docs/api/_draft/frontend-mapping.md` |
| 4 | test | `docs/api/_draft/test-contract.md` |
| 5 | pm | 合并 `docs/api/api.md`，裁决冲突 |
| 6 | 各角色新会话复核 | issues → pm 关闭 |
| 7 | skill contract-sync | evidence |

## 并行

步骤 1–4 **可并行**（输出路径不重叠）

步骤 5 必须串行。

## 退出

- [ ] `docs/api/api.md` 有版本号与冻结时间
- [ ] contract-sync pass
- [ ] 无 open P0 contract issues
- [ ] `gates.api_frozen = true`

## 下一步

- planner → `docs/tasks/plan.md`
- → `phase-3-build.md`
