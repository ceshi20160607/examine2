# TEST-004 权限异常幂等 OpenAPI 测试

- taskId: TEST-004
- 标题: 权限异常幂等 OpenAPI 测试
- 负责角色: test
- 所属大任务/模块: 测试 / 风险场景
- 目标: 执行权限、异常、状态冲突、幂等、并发、OpenAPI 签名和限流测试。
- 输入文件: `docs/test_plan.md`、`backend/`、`frontend/`、`docs/service_info.md`
- 输出文件或输出目录: `docs/test_runs/permission-exception-idempotency-openapi.md`

## 详细工作内容

- 测试跨系统、跨租户、字段无写权、数据范围越权、无导出权限、无文件下载权限。
- 测试必填缺失、类型错误、唯一冲突、状态冲突、流程重复处理、导出失败重试。
- 测试 OpenAPI 签名、timestamp、nonce、body hash、scope、IP 白名单、幂等回放、幂等冲突和限流。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 风险场景执行结果写入 `docs/test_runs/permission-exception-idempotency-openapi.md`，最终 `docs/test_report.md` 只由 TEST-005 汇总写入。

## 验收标准

- 所有失败响应包含模块化错误码和 requestId。
- 幂等处理结果符合冻结契约。

## 测试/自检要求

- 输出失败日志摘要和复现参数，写入独立测试执行记录。

## 依赖任务

- BE-015
- FE-012
- TEST-002

## 可并行关系

- 可与 TEST-003 并行。

## 不允许事项

- 不把前端隐藏按钮作为权限测试通过依据。
- 不跳过 OpenAPI 安全负向用例。
