# DBA-004 流程文件 OpenAPI 审计表设计

- taskId: DBA-004
- 标题: 流程文件 OpenAPI 审计表设计
- 负责角色: dba
- 所属大任务/模块: DB 设计 / 流程文件集成审计
- 目标: 设计流程、上传文件、OpenAPI、幂等、限流和审计运维表。
- 输入文件: `docs/prd.md`、`docs/api.md`、`docs/service_info.md`、`docs/legacy_reference.md`
- 输出文件或输出目录: `docs/db_design_parts/flow-upload-openapi-audit.md`

## 详细工作内容

- 设计流程模板、版本、节点、连线、实例、任务、抄送、动作日志表。
- 设计文件元数据、引用关系、存储配置和临时文件过期字段。
- 设计 OpenAPI 客户端、凭证、scope、IP 白名单、nonce、幂等、限流、调用日志表。
- 设计请求日志、错误日志、操作日志、记录变更和健康检查结果表。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 上述表域写入 `docs/db_design_parts/flow-upload-openapi-audit.md` 并说明关系、索引和状态字段；最终 `docs/db_design.md` 只由 DBA-005 汇总写入。

## 验收标准

- OpenAPI 表统一使用 `un_openapi_`，系统日志、审计日志和健康检查明确归入 `un_sys_`/`un_audit_` 表域。
- 文件下载权限可通过业务引用关系校验。

## 测试/自检要求

- 自检流程任务并发处理字段。
- 自检 OpenAPI nonce、幂等、限流和调用日志可支撑测试契约。

## 依赖任务

- DBA-001

## 可并行关系

- 可与 DBA-002、DBA-003 并行；三者输出分片路径互不重叠，最终由 DBA-005 串行合并到 `docs/db_design.md`。

## 不允许事项

- 不只保存不可解释的孤立流程 JSON。
- 不让 OpenAPI 复用内部登录态权限模型。
