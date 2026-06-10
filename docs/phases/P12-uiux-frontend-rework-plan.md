# P12 UI/UX 设计与前端可用化改造计划

状态：in_progress

PM 结论：P11 功能试部署包可以继续用于接口和功能验证，但前端体验不能作为最终用户可正常使用的交付。P12 先冻结 UI/UX 设计，再进行前端改造。

## 范围

| 任务 | 负责人 | 状态 | 输出 |
| --- | --- | --- | --- |
| UIUX-001 | uiux/pm | done | `docs/ui/ui-design.md` |
| FE-023 | frontend | pending | 前端 shell、导航、系统总览和页面布局改造 |
| FE-024 | frontend | pending | 各业务域按 UI 设计重构表单、抽屉、详情、状态反馈 |
| TEST-010 | test | pending | P12 浏览器可用性 E2E |
| VAL-008 | validator | pending | 前端 clean build、后端 package、部署包核验 |
| REV-008 | reviewer | pending | UI/UX 一致性和最终可用性审查 |

## 退出标准

1. 前端页面符合 `docs/ui/ui-design.md`。
2. 平台层和系统层导航清晰。
3. 创建系统、进入系统、配置应用模块、发布、运行填报、审批、导出、审计检索主链路通过浏览器 E2E。
4. 主要页面具备空态、加载态、错误态、权限禁用态和 requestId 展示。
5. `npm.cmd run build` 与后端 package 通过。
6. 重新生成部署包并核验包含 `frontend/index.html`、`frontend/assets/*`、`backend/unexamine.jar`、`backend/start.sh`。

## PM 不可宣称事项

- P12 完成前，不能再说前端最终用户体验完成。
- P11 包只能称为功能试部署包，不能称为最终 UI 体验包。
- UI 改造不得顺手修改后端业务契约；发现接口不支撑设计时，登记问题交 PM 决策。
