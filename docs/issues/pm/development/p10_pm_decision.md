# P10 PM Decision

## 决策结论

P10 进入 `P10-app-runtime-ui`，目标是补齐应用、模块、字段、页面配置、发布和运行台记录的真实浏览器页面闭环。P10 通过后只能声明“应用模块与运行台可用化完成”，不能声明完整系统可上线。

## 范围裁决

1. P10 纳入 `APP-001` 至 `APP-005`、`MOD-001` 至 `MOD-007`、`FIELD-001` 至 `FIELD-005`、`UI-001` 至 `UI-008`、`RUN-001` 至 `RUN-010` 的 MVP 页面主链路。
2. `APP-006/APP-007`、`UI-009`、流程工作台、文件中心、导出中心、OpenAPI、审计运维留到后续 P11/P12。
3. 字段 E2E 先覆盖 `TEXT`、`NUMBER`、`DATE`、`SELECT` 等主链路；`RELATION`、`SUB_TABLE`、`JSON`、`ADDRESS`、`TAG` 暂不作为 P10 必过项。
4. 运行台提交入口按契约验证；完整流程实例可视化处理留 P11。
5. E2E 必须优先通过页面创建应用、模块、字段和发布，不使用测试 SQL 或控制台 API 替代页面主链路。

## 后端缺口处理

如果 P10 E2E 发现 APP/MOD/FIELD/UI/RUN 后端契约或实现缺口，允许登记 `BE-016-P10-runtime-ui-rework` 修复任务，经 PM 裁决后在 P10 内处理；不得由 frontend 私自绕过后端接口。
