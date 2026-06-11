# REV-009 P13 可用性返工审查

- taskId：REV-009
- 标题：P13 可用性返工审查
- 负责角色：reviewer
- 所属大任务/模块：P13-usability-rework / 审查
- 任务类型：review
- 状态：pending

## 目标

审查 P13 是否真正解决用户反馈中的可用性问题，并重新判断是否允许刷新最终部署包和进入后续期。

## 输入文件

- `docs/ui/p13-usability-rework-spec.md`
- `docs/ui/prototypes/p13-usability-delta.md`
- `frontend/docs/page-contracts/FE-025-p13-usability-frontend-rework.md`
- `docs/test_runs/p13-usability-e2e.md`
- `docs/test_report.md`
- `docs/build/p13-clean-build.md`
- `docs/build_report.md`
- `docs/review.json`

## 输出文件或输出目录

- `docs/review.json`
- `docs/issues/verification/development/p13_reviewer_verification.md`

## 详细工作内容

1. 对照用户反馈、P13 设计修订、前端证据、浏览器 E2E 和 clean build 逐项审查。
2. 检查是否存在原生 prompt、占位页、工程调试页、英文散落文案、硬编码 API 地址、旁路请求或缺失权限禁用说明。
3. 若发现测试或构建用旧产物冒充 P13 通过，必须 fail。
4. 在 `docs/review.json` 中给出合法 JSON 结论；pass 时 `target=none` 且 issues 为空，fail 时明确 target。

## 完成状态定义

- `docs/review.json` 为合法 JSON，反映 P13 最新审查结论。
- `docs/issues/verification/development/p13_reviewer_verification.md` 记录逐项审查结果。
- 状态保持 `pending`，由执行 agent 完成后更新。

## 验收标准

- TEST-011 与 VAL-009 必须通过。
- P13 用户反馈问题均有关闭证据。
- 允许进入 PKG-002 的唯一条件是 `docs/review.json.status=pass` 且没有 P13 P1/P0 阻塞问题。

## 测试/自检要求

- 审查 JSON 结构合法。
- 审查报告必须区分“后端可用”“前端可用”“最终包可刷新”。

## 依赖任务

- TEST-011
- VAL-009

## 可并行关系

- 不可与 TEST-011、VAL-009、PKG-002 并行。
- 可只读检查源码和文档，但不得修改前后端代码。

## 不允许事项

- 不在测试或构建失败时给出 pass。
- 不把局部页面可点误判为完整用户可用。
- 不修改代码、SQL 或 API。

## 具体实现范围

仅限 P13 审查结论、review JSON 和 verification 文档。

## 不做事项

不执行修复、不生成部署包。

## 单元测试或自检要求

验证 `docs/review.json` 可被 JSON 解析，且 status/target/issues 符合规则。

## 交给 test 的集成测试入口

若 fail，`docs/review.json.issues` 中的 target 作为回环入口；若 pass，进入 PKG-002。
