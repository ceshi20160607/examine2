# FE-011 OpenAPI 审计运维页面

- taskId: FE-011
- 标题: OpenAPI 审计运维页面
- 负责角色: frontend
- 所属大任务/模块: 前端 / 集成与审计运维
- 目标: 实现 OpenAPI 客户端、凭证、scope、IP 白名单、调用日志、审计日志和健康运维页面。
- 输入文件: `docs/api.md`、`frontend/src/api/`
- 输出文件或输出目录: `frontend/src/pages/openapi/`、`frontend/src/pages/audit/`、`frontend/src/pages/ops/`、`frontend/docs/page-contracts/FE-011-openapi-audit-ops-pages.md`

## 详细工作内容

- 实现 OPM、AUD、OPS 接口对应页面。
- 支持 secret 只展示一次、scope catalog、调用日志、requestId 检索、日志详情、健康检查和版本信息。
- 处理无权限只读、日志为空、健康异常和复制 requestId。
- 补齐页面级 API 映射证据，覆盖路由、API ID、必填入参、响应字段、上下文依赖、枚举/状态/错误码、权限禁用态、空态/错误态、requestId 展示和无旁路请求检查。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 系统管理员和运维审计人员可完成集成和审计查询，`FE-011` 页面级契约证据文件已补齐。

## 验收标准

- 凭证轮换后只展示新 secret 一次。
- 所有错误页面和日志检索支持 requestId。
- 权限禁用态、空态、错误态和完成证据可被 FE-012 汇总到 `frontend/docs/api-contract-map.md`。

## 测试/自检要求

- 自检 OpenAPI 权限禁用、scope 编辑、健康异常、日志详情和 requestId 复制。

## 依赖任务

- FE-002

## 可并行关系

- 可与 FE-003、FE-004、FE-005、FE-006 并行。

## 不允许事项

- 不持久化明文 secret。
- 不让运维审计页面修改业务数据。
