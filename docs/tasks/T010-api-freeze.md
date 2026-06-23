# T010 · API 契约冻结

| 项 | 值 |
|----|-----|
| taskId | T010 |
| 阶段 | P1 |
| 责任 | pm + backend + frontend + test |
| 依赖 | T005, T006, T007 |
| 状态 | pending |

## 目标

合并 174 端点为 `docs/api/api.md`，标注新 IA（模块组）相关 gap。

## 输入

- `frontend/src/api/endpoints.ts`
- `frontend/docs/api-contract-map.md`
- `docs/design/page-inventory.md` §5
- `docs/api/_draft/*`（待写）

## 输出

- `docs/api/api.md`（冻结）
- `gates.api_frozen = true`

## 必须回答的 gap

| gap | 说明 |
|-----|------|
| 模块组 API | 顶栏 moduleGroup 菜单 vs 旧 app/module 树 |
| RUN 列表场景 | 场景/列设置/个人偏好端点 |
| 详情审批侧栏 | FLOW 实例与 record 并列 |

## 完成标准

- 各角色 review pass
- frontend SDK 与 api.md 零 diff（或 diff 已登记变更）
