# BE-013 审计运维 API

- taskId: BE-013
- 标题: 审计运维 API
- 负责角色: backend
- 所属大任务/模块: 后端 / 审计运维
- 目标: 实现审计日志、请求日志、错误日志、记录变更、OpenAPI 日志和健康运维接口。
- 输入文件: `docs/api.md`、`docs/db_design.md`、`backend/examine-core/`
- 输出文件或输出目录: `backend/examine-core/`、`backend/examine-plat/`、`backend/examine-web/`

## 详细工作内容

- 实现 AUD-001 至 AUD-008 和 OPS-001 至 OPS-006。
- 贯穿 requestId、operator、systemId、tenantId、bizType、bizId、action、result。
- 实现数据库、Redis、文件存储、密钥、migration 和 OpenAPI 策略健康检查。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 审计和运维接口可支持问题排查和只读审计。

## 验收标准

- 运维审计人员默认只读。
- 健康异常不得静默通过，必须返回检查项和建议。

## 测试/自检要求

- 测试 requestId 检索、跨系统审计权限、查询范围过大、健康异常。

## 依赖任务

- BE-012

## 可并行关系

- 可在 BE-012 完成后与尚未收尾的 BE-011 并行；不得与 BE-012 同批启动。

## 不允许事项

- 不写入无法追踪 requestId 的审计记录。
- 不允许审计接口修改业务数据。
