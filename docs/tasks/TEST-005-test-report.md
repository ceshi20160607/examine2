# TEST-005 测试报告

- taskId: TEST-005
- 标题: 测试报告
- 负责角色: test
- 所属大任务/模块: 测试 / 报告
- 目标: 汇总测试执行命令、结果、失败日志、未覆盖风险和 test 结论。
- 输入文件: `docs/test_plan.md`、`docs/test_runs/e2e-main-chain.md`、`docs/test_runs/permission-exception-idempotency-openapi.md`
- 输出文件或输出目录: `docs/test_report.md`

## 详细工作内容

- 汇总 `docs/test_runs/e2e-main-chain.md`、`docs/test_runs/permission-exception-idempotency-openapi.md` 和其它单任务测试结果。
- 列出失败日志摘要、未覆盖风险和复现前置。
- 给出 `pass` 或 `fail`，fail 时必须给出 target。

## 完成状态定义

- 当前状态: done。
- 完成条件: `docs/test_report.md` 已符合格式契约。

## 执行记录

- 2026-06-08 已汇总 TEST-003/TEST-004 到 `docs/test_report.md`。
- 报告结论为 `fail`，target 为 `frontend`，原因是后端 API 集成 smoke 通过但前端正式浏览器 E2E 无可执行工程入口。

## 验收标准

- 报告含执行命令、结果、失败摘要、未覆盖风险和结论。
- fail target 只能为 backend/frontend/both/api/pm/planner/test。

## 测试/自检要求

- 自检报告非空且 target 合法。

## 依赖任务

- TEST-003
- TEST-004

## 可并行关系

- 不可并行；必须汇总全部测试结果。

## 不允许事项

- 不以“未执行”标记 pass。
- 不隐藏失败日志摘要。
