# VAL-008 P12 Clean Build

- 所属期次：P12-uiux-frontend-rework
- 负责人：validator
- 状态：pending

## 目标

验证 P12 UI 改造后的前端 clean build 和后端 package。P12 未完成前不生成部署包。

## 输出

- `docs/build/p12-clean-build.md`

## 验收标准

- `npm.cmd run build` 通过。
- `mvn -pl examine-web -am -DskipTests package` 通过。
- 不创建新的 `dist/unexamine-full-deploy-*.zip`。
- 如果发现测试或审查未通过，validator 必须阻止打包。
