# P9 系统管理域可用化期验收

## 结论

status: `accepted`

accepted_at: `2026-06-09T14:15:00+08:00`

fullProjectDeployable: `false`

P9 已通过成员、部门、系统角色、字典真实业务 UI、前端 clean build、后端 package、真实浏览器写操作 E2E 和 reviewer 复核。P9 只代表系统管理域可用化完成，不代表完整项目可上线。

## 范围

| 任务 | 结论 | 证据 |
| --- | --- | --- |
| FE-015 | pass | `frontend/src/App.ts`、`frontend/src/pages/system/types.ts` |
| TEST-007 | pass | `docs/test_runs/p9-system-management-ui-e2e-20260609.md` |
| VAL-005 | pass | `npm.cmd run build`、`mvn -pl examine-web -am -DskipTests package` |
| REV-005 | pass | `docs/review.json`、`docs/issues/verification/development/p9_reviewer_verification.md` |

## 真实修复

- 系统角色授权从原生 prompt 改为页面表单字段，真实页面可稳定保存授权。
- `RBAC-009` 权限版本递增改为更新既有 `un_module_permission_version` 行，避免唯一键冲突。
- 部门接口字段修正为后端 `code/name`，并兼容旧 `deptCode/deptName` 显示。
- 成员、部门、角色、字典关键操作移除原生 prompt/confirm 依赖，改为页面表单和按钮驱动。

## 后续

下一期建议进入 `P10-app-runtime-ui`，范围限定为应用、模块、字段、页面配置、运行台真实 UI 和浏览器 E2E。P10 开始前必须继续执行 development governance：Planner 定界、PM 裁决、多角色审查、实现、TEST/VAL/REV 闭环和阶段提交。
