# UIUX-003 P13 可用性问题归因与设计修订

- taskId：UIUX-003
- 标题：P13 可用性问题归因与设计修订
- 负责角色：uiux
- 所属大任务/模块：P13-usability-rework / UI/UX 可用性返工
- 任务类型：implementation
- 状态：pending

## 目标

基于用户反馈和 P12 交付包实际体验，重新梳理阻碍真实用户顺畅使用的可用性问题，输出 P13 设计修订说明和页面级验收口径，作为前端返工的唯一设计输入。

## 输入文件

- `docs/ui/ui-design.md`
- `docs/ui/prototypes/page-prototypes.md`
- `docs/phases/P12-uiux-frontend-rework-acceptance.md`
- `docs/test_runs/p12-ui-usable-e2e.md`
- `docs/review.json`
- 用户反馈记录，由 PM 在 P13 启动时整理到 development issue 或阶段验收文档

## 输出文件或输出目录

- `docs/ui/p13-usability-rework-spec.md`
- `docs/ui/prototypes/p13-usability-delta.md`

## 详细工作内容

1. 将用户反馈拆成可验证的用户任务问题，至少覆盖入口可发现性、主流程路径、表单填写负担、状态反馈、错误恢复、空态引导、权限禁用说明和部署后首次使用体验。
2. 对照 P12 设计稿标记哪些规则仍有效、哪些需要 P13 修订，避免推翻已冻结 API 和已通过的业务闭环。
3. 为每个 P13 可用性问题给出页面级改造要求、验收截图/DOM 断言建议和不改动后端契约的约束。
4. 明确 P13 不新增业务能力，只修复用户使用路径、页面组织、文案反馈和交互状态。

## 完成状态定义

- `docs/ui/p13-usability-rework-spec.md` 包含问题清单、归因、页面改造要求、验收口径和非目标。
- `docs/ui/prototypes/p13-usability-delta.md` 包含受影响页面的差异化原型或布局说明。
- 状态保持 `pending`，由执行 agent 完成并经 PM 确认后更新。

## 验收标准

- 每条用户反馈均能映射到明确页面、用户任务、现象、期望行为和验收方式。
- 设计修订不要求新增或修改冻结 API。
- 前端可以直接按该设计执行 FE-025，不需要临场补产品交互设计。

## 测试/自检要求

- 自检 P13 设计修订是否覆盖后续 TEST-011 的浏览器 E2E 场景。
- 自检文档中文文案、空态、错误态和权限禁用态是否明确。

## 依赖任务

- P12-uiux-frontend-rework accepted

## 可并行关系

- 不可与 FE-025 并行；FE-025 必须等待 UIUX-003 输出冻结。
- 可与 TEST-011 的测试场景草拟并行，但 TEST-011 最终用例必须回读本任务输出。

## 不允许事项

- 不修改代码、SQL、API 契约或数据库设计。
- 不把 P13 扩大为新业务模块开发。
- 不用“优化体验”这类不可验收表述替代具体问题和验收口径。

## 具体实现范围

仅限 UI/UX 设计修订文档与页面级差异原型。

## 不做事项

不执行前端实现、不运行测试、不生成部署包。

## 单元测试或自检要求

检查每个改造点是否具备页面、触发步骤、期望状态、失败提示和验收证据。

## 交给 test 的集成测试入口

`docs/ui/p13-usability-rework-spec.md` 中的“P13 浏览器验收场景”章节。
