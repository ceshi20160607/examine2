# VAL-008 P12 Clean Build 与部署包

- 所属期次：P12-uiux-frontend-rework
- 负责人：validator
- 状态：pending

## 目标

验证 P12 UI 改造后的前端 clean build、后端 package 和部署包内容。

## 输出

- `docs/build/p12-clean-build.md`
- `dist/unexamine-full-deploy-*.zip`

## 验收标准

- `npm.cmd run build` 通过。
- `mvn -pl examine-web -am -DskipTests package` 通过。
- 部署包包含 `frontend/index.html`、`frontend/assets/*`、`backend/unexamine.jar`、`backend/start.sh`。
