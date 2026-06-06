# BE-002 core 统一响应错误上下文

- taskId: BE-002
- 标题: core 统一响应错误上下文
- 负责角色: backend
- 所属大任务/模块: 后端 / examine-core
- 目标: 实现统一响应、错误码、requestId、traceId、上下文和公共配置基础。
- 输入文件: `docs/api.md`、`docs/prd.md`、`docs/service_info.md`
- 输出文件或输出目录: `backend/examine-core/`

## 详细工作内容

- 实现 `ApiResponse<T>`、错误响应、`errors[]` 明细结构和统一异常基类。
- 建立错误码命名空间：COMMON、AUTH、PLAT、SYS、MODULE、FIELD、PERM、FLOW、UPLOAD、EXPORT、OPENAPI、AUDIT、OPS、GENERATOR。
- 实现 requestId/traceId 生成、透传和日志上下文。
- 提供幂等基础服务抽象，包括 `X-Idempotency-Key`、requestHash、resultSnapshot、TTL、处理中锁和冲突响应的公共接口与错误码。

## 完成状态定义

- 默认状态: pending。
- 完成条件: core 公共能力可被其它模块引用。

## 验收标准

- 所有错误响应带 requestId、traceId、code、message 和可选 errors。
- 公共能力不包含具体业务流程。
- 幂等基础能力只提供公共抽象和一致响应，不依赖具体运行台或 OpenAPI 业务实现。

## 测试/自检要求

- 单元测试成功和失败响应结构。
- 单元测试 requestId 透传和自动生成。
- 单元测试幂等基础服务的回放、冲突和处理中响应结构。

## 依赖任务

- BE-001

## 可并行关系

- 可与 GEN-001 并行；BE-003、BE-014 必须在本任务完成后启动。

## 不允许事项

- 不在 core 中实现平台、运行台或流程业务。
- 不使用裸数字错误码替代模块化错误码。
