# BE-015 幂等并发与 API 自检

- taskId: BE-015
- 标题: 幂等并发与 API 自检
- 负责角色: backend
- 所属大任务/模块: 后端 / 自检
- 目标: 对 BE-004 至 BE-014 已实现的后端业务 API 执行最终自检，确认幂等、并发、权限、事务、OpenAPI 和错误码闭环。
- 输入文件: `docs/api.md`、`docs/db_design.md`、`backend/`
- 输出文件或输出目录: `backend/docs/backend-self-check.md`

## 详细工作内容

- 汇总并验证各业务模块对 `X-Idempotency-Key`、requestHash、resultSnapshot、TTL、处理中锁和冲突响应的使用结果。
- 验证自动编号、乐观锁、流程任务并发处理、导出任务领取并发控制和 OpenAPI nonce/限流的落地效果。
- 对 AUTH、PLAT、SYS、RBAC、DICT、APP、MOD、FIELD、UI、RUN、FLOW、FILE、EXP、OPM、OPN、AUD、OPS 进行接口自检。
- 在 `backend/docs/backend-self-check.md` 中记录接口清单、执行命令、单元测试/集成测试结果、失败日志摘要、未覆盖风险、幂等/权限/OpenAPI 自检结论和 pass/fail。

## 完成状态定义

- 默认状态: done。
- 完成条件: `backend/docs/backend-self-check.md` 存在且记录后端核心 API 自检通过，或输出明确失败摘要和 target。

## 完成记录

- 完成时间: 2026-06-07。
- 输出: `backend/docs/backend-self-check.md`。
- 执行命令: `mvn -pl examine-web -am test`。
- 自检结果: core 12、plat 12、upload 4、module 21、flow 2、app 4、web 4 个测试通过，合计 59 个测试；报告记录接口清单、命令、结果、失败摘要、幂等/权限/OpenAPI 结论和 pass/fail。

## 验收标准

- 幂等回放、冲突、处理中响应符合冻结契约。
- 自检覆盖统一响应、错误码、权限、事务和 requestId。
- 自检报告必须包含接口清单、测试命令、结果、失败摘要、幂等/权限/OpenAPI 结论和最终 pass/fail。

## 测试/自检要求

- 运行后端单元测试、接口自检或等价验证，并在固定报告中记录命令、结果和失败日志摘要。

## 依赖任务

- BE-004
- BE-005
- BE-006
- BE-007
- BE-008
- BE-009
- BE-010
- BE-011
- BE-012
- BE-013
- BE-014

## 可并行关系

- 不可并行；必须在后端业务模块完成后执行。

## 不允许事项

- 不以跳过测试或只编译通过代替 API 自检。
- 不私自调整冻结 API 字段、枚举或错误码。
