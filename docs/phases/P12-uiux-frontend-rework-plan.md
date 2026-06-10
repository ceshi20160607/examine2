# P12 UI/UX 设计与前端可用化改造计划

状态：in_progress

PM 结论：P11 功能试部署包可以继续用于接口和功能验证，但前端体验不能作为最终用户可正常使用的交付。P12 先冻结 UI/UX 设计，再进行前端改造。

## 范围

| 任务 | 负责人 | 状态 | 输出 |
| --- | --- | --- | --- |
| UIUX-001 | uiux/pm | done | `docs/ui/ui-design.md` |
| UIUX-002 | uiux/pm | done | `docs/ui/prototypes/page-prototypes.md` |
| FE-023 | frontend | done | 前端 shell、导航、系统总览和页面布局改造 |
| FE-024 | frontend | done | 各业务域按 UI 设计重构表单、步骤工作台、详情 Tabs、状态反馈 |
| TEST-010 | test | pending | P12 浏览器可用性 E2E |
| VAL-008 | validator | pending | 前端 clean build、后端 package；不生成部署包 |
| REV-008 | reviewer | pending | UI/UX 一致性和最终可用性审查 |
| PKG-001 | pm/validator | blocked | REV-008 pass 后才允许生成最终部署包 |

## 退出标准

1. 前端页面符合 `docs/ui/ui-design.md`。
2. 平台层和系统层导航清晰。
3. 创建系统、进入系统、配置应用模块、发布、运行填报、审批、导出、审计检索主链路通过浏览器 E2E。
4. 主要页面具备空态、加载态、错误态、权限禁用态和 requestId 展示。
5. `npm.cmd run build` 与后端 package 通过。
6. REV-008 审查通过。
7. REV-008 通过后，才允许进入最终打包，核验包含 `frontend/index.html`、`frontend/assets/*`、`backend/unexamine.jar`、`backend/start.sh`。

## 当前进度

- UIUX-001 已完成：`docs/ui/ui-design.md`。
- UIUX-002 已完成：`docs/ui/prototypes/page-prototypes.md`。
- FE-023 已完成：新增系统总览路由、平台/系统双层导航、系统上下文卡片和推荐路径。
- FE-024 已完成：应用配置步骤工作台、运行台详情 Tabs、流程/文件/导出/OpenAPI/审计运维状态反馈已改造；`npm.cmd run build` 通过，生产预览 `/` 与 `/#/systems/demo/overview` HTTP 200。
- TEST-010 待执行：浏览器 UI 可用性 E2E 不能由 FE-024 构建结果替代。

## PM 不可宣称事项

- P12 完成前，不能再说前端最终用户体验完成。
- P11 包只能称为功能试部署包，不能称为最终 UI 体验包。
- FE-024、TEST-010、VAL-008、REV-008 未全部通过前，禁止生成新的最终部署包。
- UI 改造不得顺手修改后端业务契约；发现接口不支撑设计时，登记问题交 PM 决策。
