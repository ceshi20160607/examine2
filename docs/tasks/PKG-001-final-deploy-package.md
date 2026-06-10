# PKG-001 最终部署包

- 所属期次：P12-uiux-frontend-rework
- 负责人：pm/validator
- 状态：blocked

## 目标

只在所有 P12 角色任务完成并通过后生成最终部署包，避免未完成阶段反复打包浪费成本。

## 入口条件

- FE-024：done
- TEST-010：pass
- VAL-008：pass
- REV-008：pass
- `docs/review.json.status=pass`
- `docs/review.json.fullProjectDeployable=true`

## 输出

- `dist/unexamine-full-deploy-*.zip`

## 验收标准

- 包内包含 `frontend/index.html`、`frontend/assets/*`、`backend/unexamine.jar`、`backend/start.sh`、部署说明。
- 包路径写入 `docs/build_report.md` 和 `docs/progress.md`。
- 未满足入口条件时禁止执行。
