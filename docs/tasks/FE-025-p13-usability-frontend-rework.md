# FE-025 P13 前端可用性返工实现

- taskId：FE-025
- 标题：P13 前端可用性返工实现
- 负责角色：frontend
- 所属大任务/模块：P13-usability-rework / 前端可用性返工
- 任务类型：deployable-ui
- 状态：pending

## 目标

按 UIUX-003 冻结的 P13 设计修订，修复用户反馈中的实际可用性问题，使最终包不只是功能可点，而是在真实用户主流程中可理解、可操作、可恢复。

## 输入文件

- `docs/ui/p13-usability-rework-spec.md`
- `docs/ui/prototypes/p13-usability-delta.md`
- `docs/ui/ui-design.md`
- `docs/api.md`
- `frontend/src/`
- `frontend/docs/api-contract-map.md`

## 输出文件或输出目录

- `frontend/src/`
- `frontend/docs/page-contracts/FE-025-p13-usability-frontend-rework.md`
- `frontend/dist/`（仅作为本任务自检构建产物，不作为最终部署包）

## 详细工作内容

1. 按 P13 设计修订调整导航入口、任务路径、页面层级、主次操作、表单布局、状态反馈和错误恢复。
2. 保留同源 `/api/v1/...` 调用语义，继续通过 typed SDK/PageModel 调接口，不新增旁路 `fetch`、`axios`、硬编码后端地址或终端用户可见 API 配置面板。
3. 修复用户反馈中影响使用的页面问题，优先覆盖登录后进入系统、系统总览、应用配置、运行台填报、流程处理、文件导出、OpenAPI/审计运维等实际链路。
4. 更新页面级契约证据，说明 P13 改动涉及的页面、接口、权限禁用态、空态/错误态和验证入口。

## 完成状态定义

- P13 反馈问题均已在真实页面中闭环，且有页面级契约证据。
- `npm.cmd run build` 通过并生成 `frontend/dist/`。
- 状态保持 `pending`，由执行 agent 完成并经 TEST-011/VAL-009/REV-009 通过后更新。

## 验收标准

- 不出现新的调试页、占位页、原生 `window.prompt`、散落英文功能文案或接口操作台式页面。
- 关键操作有明确成功/失败反馈，失败时能看到可恢复提示和 requestId 或业务错误码。
- 页面文本不重叠、不溢出，桌面和移动视口均可完成 P13 主链路。
- 不改冻结 API；如发现契约无法支撑，登记问题交 PM/API 回环，不私自改契约。

## 测试/自检要求

- 执行 `npm.cmd run build`。
- 扫描 `frontend/src` 不存在 `window.prompt` / `prompt(`。
- 扫描新增改动不包含旁路请求或硬编码 `localhost`、局域网 IP。

## 依赖任务

- UIUX-003

## 可并行关系

- 不与其他前端实现任务并行，避免 `frontend/src/` 输出路径重叠。
- TEST-011 可在本任务接近完成时准备脚本，但不得提前判定通过。

## 不允许事项

- 不修改后端、SQL、DB 设计或冻结 API。
- 不生成最终部署包。
- 不把新增业务能力塞入可用性返工。

## 具体实现范围

仅限 P13 用户反馈对应的前端页面、样式、状态反馈、页面级契约证据和前端构建产物。

## 不做事项

不处理生产增强类风险，例如 npm audit 治理、OpenAPI 高并发安全矩阵或数据库幂等存储重构。

## 单元测试或自检要求

前端 build、源码扫描和页面级契约映射自检必须通过。

## 交给 test 的集成测试入口

以 `frontend/docs/page-contracts/FE-025-p13-usability-frontend-rework.md` 和 `docs/ui/p13-usability-rework-spec.md` 作为 TEST-011 的测试入口。
