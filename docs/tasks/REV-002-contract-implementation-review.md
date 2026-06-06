# REV-002 契约实现审查

- taskId: REV-002
- 标题: 契约实现审查
- 负责角色: reviewer
- 所属大任务/模块: Review / 契约一致性
- 目标: 审查冻结 API 与后端实现、前端 SDK、页面映射和测试断言是否一致。
- 输入文件: `docs/api.md`、`backend/`、`frontend/src/api/`、`frontend/docs/api-contract-map.md`、`docs/test_report.md`
- 输出文件或输出目录: `docs/review_parts/rev-002-contract.md`

## 详细工作内容

- 检查接口路径、方法、入参、出参、错误码、枚举、状态值和权限点。
- 检查前端页面是否只通过 typed SDK 调用接口。
- 检查测试报告是否覆盖关键 API 契约。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 契约一致性结论形成 `docs/review_parts/rev-002-contract.md`，供 REV-003/REV-004 汇总。

## 验收标准

- 契约本身错则 target=api。
- 后端未实现则 target=backend；前端未同步则 target=frontend；两端都错则 target=both。

## 测试/自检要求

- 对照 `docs/api.md` 和 `frontend/docs/api-contract-map.md` 核对。

## 依赖任务

- REV-001

## 可并行关系

- 不可并行；依赖架构审查结论。

## 不允许事项

- 不私自修改冻结 API。
- 不把未覆盖测试误判为实现通过。
