# TEST-011 P13 可用性返工浏览器 E2E

- taskId：TEST-011
- 标题：P13 可用性返工浏览器 E2E
- 负责角色：test
- 所属大任务/模块：P13-usability-rework / 测试验收
- 任务类型：test
- 状态：pending

## 目标

用真实浏览器验证 P13 可用性返工是否解决用户反馈，不以接口 smoke 或 build 通过替代用户可正常使用结论。

## 输入文件

- `docs/ui/p13-usability-rework-spec.md`
- `frontend/docs/page-contracts/FE-025-p13-usability-frontend-rework.md`
- `frontend/dist/`
- `backend/examine-web/target/unexamine.jar`
- `docs/api.md`

## 输出文件或输出目录

- `docs/test_runs/p13-usability-e2e.md`
- `docs/test_report.md`

## 详细工作内容

1. 启动后端 jar 与前端生产预览或等价本地部署环境。
2. 使用真实浏览器执行 P13 设计修订中定义的主链路，不只做静态 DOM 检查。
3. 至少覆盖登录、进入系统、系统总览、应用/模块/字段/页面配置、运行台记录、流程处理、文件导出、OpenAPI 或审计运维中的用户反馈相关链路。
4. 记录关键截图、DOM 断言、接口错误摘要、失败步骤和 target。

## 完成状态定义

- `docs/test_runs/p13-usability-e2e.md` 包含执行环境、命令、账号、步骤、断言、结果和失败摘要。
- `docs/test_report.md` 汇总 P13 结论，明确 `pass/fail` 与 target。
- 状态保持 `pending`，由执行 agent 完成后更新。

## 验收标准

- 用户反馈对应链路均可由真实浏览器完成。
- 失败时不能模糊写“体验待优化”，必须定位到页面、步骤、接口或设计口径。
- 若无真实 UI 或浏览器 E2E 记录，P13 必须判定 fail，target 指向 frontend 或 uiux。

## 测试/自检要求

- 浏览器端必须验证桌面主视口，必要时补移动视口。
- 测试报告必须区分后端 API 可用、前端 UI 可用和部署包是否刷新。

## 依赖任务

- FE-025

## 可并行关系

- 不与 VAL-009、REV-009、PKG-002 并行；这些任务依赖本任务结果。
- 可在 FE-025 实现后使用独立 `docs/test_runs/` 输出，不与前端输出路径重叠。

## 不允许事项

- 不修改代码或文档以“顺手修复”测试问题。
- 不绕过浏览器直接调用接口替代 E2E。
- 不在失败时生成最终包。

## 具体实现范围

仅限 P13 可用性返工的浏览器 E2E、测试记录和测试报告。

## 不做事项

不负责前端修复、不负责构建验证、不负责最终审查。

## 单元测试或自检要求

检查测试记录是否覆盖每条 P13 用户反馈和对应验收断言。

## 交给 test 的集成测试入口

本任务自身即为 P13 集成测试入口，输出 `docs/test_runs/p13-usability-e2e.md`。
