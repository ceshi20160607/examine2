# BE-012 OpenAPI 安全与业务接口

- taskId: BE-012
- 标题: OpenAPI 安全与业务接口
- 负责角色: backend
- 所属大任务/模块: 后端 / examine-app
- 目标: 实现 OpenAPI 管理接口、AK/SK 鉴权、签名、scope、限流、幂等和外部记录/流程/文件接口。
- 输入文件: `docs/api.md`、`docs/db_design.md`、`backend/examine-app/`、`backend/examine-core/`
- 输出文件或输出目录: `backend/examine-app/`、`backend/examine-web/`

## 详细工作内容

- 实现 OPM-001 至 OPM-009 管理接口和 OPN-001 至 OPN-007 外部接口。
- 实现 canonical request、body hash、timestamp、nonce、IP 白名单、scope、数据范围和限流校验。
- OpenAPI 写接口复用内部业务事务，但不得绕过字段、状态、流程和数据范围校验。

## 完成状态定义

- 默认状态: pending。
- 完成条件: OpenAPI 基础安全和业务调用闭环可用。

## 验收标准

- secret 只返回一次，调用日志包含签名结果、scope 结果、requestId。
- 幂等冲突、处理中、回放符合冻结契约。

## 测试/自检要求

- 测试签名错误、时间过期、nonce 重放、IP 拒绝、scope 越界、限流、幂等冲突。

## 依赖任务

- BE-008
- BE-009
- BE-010
- BE-014

## 可并行关系

- 可与 BE-011 并行；BE-013 必须等待本任务完成，且本任务不能依赖最终后端自检任务 BE-015。

## 不允许事项

- 不复用内部 Bearer 登录态作为外部鉴权。
- 不使用 `un_app_` 表域。
