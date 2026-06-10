# P12 PM 裁决：先 UI/UX，后前端改造

阶段：development

时间：2026-06-10

## 背景

用户指出当前前端“有功能但不像正常系统”，并质疑是否缺少 UI 设计。PM 复核后确认：P11 之前的前端开发主要围绕接口闭环、可部署构建和浏览器 E2E，缺少独立 UI/UX 设计冻结，导致页面更像工程型操作台。

## 裁决

| issueId | raisedBy | owner | problem | impact | pmDecision | actionRequired | status | verifier | closeCondition |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P12-PM-UI-001 | user | uiux | 缺少 UI/UX 设计冻结，前端直接按 API 和功能链路实现。 | 页面可点但用户难以正常使用，PM 不能宣称最终体验完成。 | 接受。先冻结 UI/UX 设计，再进入前端改造。 | 输出 `docs/ui/ui-design.md`。 | resolved | pm | UI 设计覆盖信息架构、导航、主流程、页面框架、状态反馈和验收标准。 |
| P12-PM-FE-001 | pm | frontend | 当前前端需要按 UI 设计进行可用化改造。 | P12 前不能把 P11 包称为最终 UI 包。 | 启动 FE-023/FE-024。 | frontend 按 `docs/ui/ui-design.md` 重构 shell、导航、系统总览和业务域页面。 | open | reviewer | TEST-010、VAL-008、REV-008 全部通过。 |
| P12-PM-PKG-001 | user | pm/validator | 未完成时打包没有意义，会误导交付状态并浪费 token。 | 半成品被误部署、误验收。 | 接受。最终部署包改为 REV-008 pass 后的 PKG-001，未完成阶段禁止打包。 | 更新任务计划和治理规则，VAL-008 只做 build/package 验证不出 zip。 | resolved | reviewer | `PKG-001` 状态为 blocked，入口条件要求 `fullProjectDeployable=true`。 |

## 不可宣称事项

- P11 包只能称为功能试部署包。
- P12 通过前，`fullProjectDeployable=false`。
- frontend 不得在缺少或偏离 `docs/ui/ui-design.md` 的情况下自行决定产品交互。
- FE-024、TEST-010、VAL-008、REV-008 未全部通过前，禁止生成新的最终部署包。
