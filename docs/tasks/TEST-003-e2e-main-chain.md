# TEST-003 E2E 主链路执行

- taskId: TEST-003
- 标题: E2E 主链路执行
- 负责角色: test
- 所属大任务/模块: 测试 / 集成场景
- 目标: 执行从平台初始化到运行填报、审批、导出、OpenAPI、审计的主链路。
- 输入文件: `docs/test_plan.md`、`backend/`、`frontend/`、`docs/service_info.md`
- 输出文件或输出目录: `docs/test_runs/e2e-main-chain.md`

## 详细工作内容

- 执行登录、创建系统、初始化默认租户和系统管理员、配置应用模块字段页面、发布、运行填报、提交审批、处理待办、导出、OpenAPI 查询和审计检索。
- 记录命令、环境、数据、结果和失败日志，写入 `docs/test_runs/e2e-main-chain.md`。
- 验证页面刷新、权限禁用、错误 requestId 和状态联动。

## 完成状态定义

- 默认状态: pending。
- 当前状态: done。
- 完成条件: E2E 主链路执行记录已写入 `docs/test_runs/e2e-main-chain.md`，最终 `docs/test_report.md` 只由 TEST-005 汇总写入。

## 执行记录

- 2026-06-08 已完成 backend API 主链路执行，记录见 `docs/test_runs/e2e-main-chain.md`。
- 前端真实浏览器刷新与页面联动测试因当前前端工程入口缺失，保留为 TEST-005 汇总风险，不在本任务中伪造通过。

## 验收标准

- 主链路成功时关键数据落点和状态变化可追踪。
- 失败时给出 target：backend/frontend/both/api/pm/planner/test。

## 测试/自检要求

- 端到端场景必须覆盖审批和导出任务闭环。

## 依赖任务

- BE-015
- FE-012
- TEST-002

## 可并行关系

- 可与 TEST-004 分场景并行执行。

## 不允许事项

- 不使用生产 seed 充当测试样例。
- 不忽略失败日志和 requestId。
