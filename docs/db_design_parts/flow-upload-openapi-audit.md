# DBA-004 流程、上传、OpenAPI、审计运维表设计分片

## 一、边界与结论

本分片只覆盖 `DBA-004`：流程审批、上传文件、OpenAPI、安全幂等、审计/运维表设计。本文不生成 `docs/db_design.md`，不生成 `sql/init.sql`，最终总设计由 `DBA-005` 串行合并。

冻结输入结论：

- API 已冻结，`api_frozen=true`，当前为开发模式 DB 设计阶段。
- 表前缀固定使用 `un_flow_`、`un_upload_`、`un_openapi_`、`un_sys_`、`un_audit_`。
- 旧项目 `un_app_*` 只作为 OpenAPI 历史表域参考，新项目不新建 `un_app_*`。
- 流程新设计采用 `template/version/instance/task` 术语；旧项目未被 Flyway DDL 确认的 flow 实体表名不直接沿用。
- 业务应用、运行记录、导出模板和导出任务归 `un_module_`，本分片只设计与其关联的流程、文件、OpenAPI 和审计落点。

## 二、表与功能映射

| 表名 | 表域 | 功能模块 | 描述 |
| --- | --- | --- | --- |
| `un_flow_template` | `un_flow_` | 流程配置 | 流程模板主表，承载编码、名称、状态和当前发布版本。 |
| `un_flow_template_version` | `un_flow_` | 流程配置 | 流程发布版本和结构快照。 |
| `un_flow_template_node` | `un_flow_` | 流程配置 | 发布版本内节点结构。 |
| `un_flow_template_line` | `un_flow_` | 流程配置 | 发布版本内连线结构。 |
| `un_flow_template_condition` | `un_flow_` | 流程配置 | 连线条件表达式结构化存储。 |
| `un_flow_binding` | `un_flow_` | 流程配置 | 模块提交动作与流程版本绑定。 |
| `un_flow_instance` | `un_flow_` | 流程运行 | 业务记录发起后的流程实例。 |
| `un_flow_task` | `un_flow_` | 流程运行 | 待办任务、领取、处理和并发版本控制。 |
| `un_flow_task_actor` | `un_flow_` | 流程运行 | 任务候选人、处理人和转交目标。 |
| `un_flow_cc` | `un_flow_` | 流程工作台 | 流程抄送与已读状态。 |
| `un_flow_action_log` | `un_flow_` | 流程审计 | 审批动作日志。 |
| `un_flow_trace_log` | `un_flow_` | 流程审计 | 流程推进轨迹和节点进入/离开日志。 |
| `un_upload_storage_config` | `un_upload_` | 文件存储 | 存储配置和默认存储策略。 |
| `un_upload_file` | `un_upload_` | 上传文件 | 文件元数据、临时状态、对象存储定位和安全属性。 |
| `un_upload_file_part` | `un_upload_` | 上传文件 | 分片上传预留表。 |
| `un_upload_file_reference` | `un_upload_` | 文件引用 | 文件与业务对象、动态字段、导出结果之间的引用关系。 |
| `un_openapi_client` | `un_openapi_` | OpenAPI 管理 | 外部客户端，绑定系统、租户和状态。 |
| `un_openapi_client_credential` | `un_openapi_` | OpenAPI 安全 | AK/SK 凭证、密钥密文、轮换和过期状态。 |
| `un_openapi_client_scope` | `un_openapi_` | OpenAPI 授权 | scope、模块、动作、字段读写权限和数据范围。 |
| `un_openapi_ip_whitelist` | `un_openapi_` | OpenAPI 安全 | IP/CIDR 白名单。 |
| `un_openapi_nonce` | `un_openapi_` | OpenAPI 防重放 | nonce 去重和 TTL。 |
| `un_openapi_idempotency_record` | `un_openapi_` | OpenAPI 幂等 | 外部写接口幂等记录、请求摘要和结果快照。 |
| `un_openapi_rate_limit_policy` | `un_openapi_` | OpenAPI 限流 | 客户端限流策略。 |
| `un_openapi_rate_limit_counter` | `un_openapi_` | OpenAPI 限流 | 限流窗口计数。 |
| `un_openapi_access_log` | `un_openapi_` | OpenAPI 审计 | 外部调用日志、签名/scope/限流/幂等结果。 |
| `un_sys_idempotency_record` | `un_sys_` | 通用安全 | 内部写接口幂等记录。 |
| `un_sys_request_log` | `un_sys_` | 审计日志 | 请求日志，支持 requestId 检索。 |
| `un_sys_error_log` | `un_sys_` | 审计日志 | 错误日志，保留错误码、栈摘要和 requestId。 |
| `un_audit_operation_log` | `un_audit_` | 操作审计 | 平台、系统、运行、流程、文件、OpenAPI 的操作审计。 |
| `un_audit_record_change` | `un_audit_` | 业务审计 | 动态记录字段变更前后快照。 |
| `un_sys_health_check_result` | `un_sys_` | 运维配置 | 健康检查结果。 |
| `un_sys_runtime_config_check` | `un_sys_` | 运维配置 | 配置检查结果。 |
| `un_sys_migration_status` | `un_sys_` | 运维配置 | DB migration 状态查询落点。 |

## 三、公共字段规则

除日志明细表可按归档策略扩展分区外，所有表都必须包含以下公共字段：

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id` | bigint | 是 | 无 | 主键，雪花 ID 或等价全局唯一 ID。 |
| `created_at` | datetime | 是 | `CURRENT_TIMESTAMP` | 创建时间。 |
| `updated_at` | datetime | 是 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间。 |

业务表增加：

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 自定义系统 ID。 |
| `tenant_id` | bigint | 否 | `NULL` | 租户 ID；平台级或全局记录可为空。 |
| `deleted` | tinyint | 是 | `0` | 逻辑删除标记，`0-正常，1-删除`。 |
| `created_by` | bigint | 否 | `NULL` | 创建人平台账号或系统成员 ID，按业务上下文解释。 |
| `updated_by` | bigint | 否 | `NULL` | 更新人平台账号或系统成员 ID。 |

## 四、流程表字段设计

### 4.1 `un_flow_template`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `tenant_id` | bigint | 否 | `NULL` | 所属租户。 |
| `code` | varchar(64) | 是 | 无 | 流程模板编码，同系统租户内唯一。 |
| `name` | varchar(128) | 是 | 无 | 流程模板名称。 |
| `status` | varchar(32) | 是 | `DRAFT` | `DRAFT`、`PUBLISHED`、`DISABLED`。 |
| `current_version_id` | bigint | 否 | `NULL` | 当前发布版本 ID。 |
| `description` | varchar(500) | 否 | `NULL` | 模板说明。 |
| `version_no` | int | 是 | `0` | 乐观锁版本。 |
| `deleted` | tinyint | 是 | `0` | 逻辑删除。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_flow_template_code(system_id, tenant_id, code, deleted)`。
- 索引：`idx_flow_template_system_status(system_id, tenant_id, status)`。

### 4.2 `un_flow_template_version`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `tenant_id` | bigint | 否 | `NULL` | 所属租户。 |
| `template_id` | bigint | 是 | 无 | 流程模板 ID。 |
| `version_no` | int | 是 | 无 | 发布版本号，同模板递增。 |
| `status` | varchar(32) | 是 | `PUBLISHED` | `PUBLISHED`、`DISCARDED`。 |
| `publish_comment` | varchar(500) | 否 | `NULL` | 发布说明。 |
| `graph_snapshot_json` | json | 是 | 无 | 发布时流程图完整快照，用于历史解释。 |
| `check_result_json` | json | 否 | `NULL` | 发布检查结果快照。 |
| `published_by` | bigint | 是 | 无 | 发布人系统成员 ID。 |
| `published_at` | datetime | 是 | `CURRENT_TIMESTAMP` | 发布时间。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_flow_template_version(template_id, version_no)`。
- 索引：`idx_flow_version_template_status(template_id, status)`。

### 4.3 `un_flow_template_node`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `template_version_id` | bigint | 是 | 无 | 流程发布版本 ID。 |
| `node_key` | varchar(64) | 是 | 无 | 节点稳定编码，版本内唯一。 |
| `node_name` | varchar(128) | 是 | 无 | 节点名称。 |
| `node_type` | varchar(32) | 是 | 无 | `START`、`APPROVAL`、`CC`、`END`。 |
| `actor_strategy` | varchar(32) | 否 | `NULL` | 审批人策略，如 `ROLE`、`MEMBER`、`DEPT_MANAGER`、`INITIATOR`。 |
| `actor_config_json` | json | 否 | `NULL` | 候选人配置快照。 |
| `approval_required` | tinyint | 是 | `1` | 是否需要审批意见。 |
| `sort_order` | int | 是 | `0` | 图中排序。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_flow_node_key(template_version_id, node_key)`。
- 索引：`idx_flow_node_version(template_version_id, node_type)`。

### 4.4 `un_flow_template_line`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `template_version_id` | bigint | 是 | 无 | 流程发布版本 ID。 |
| `line_key` | varchar(64) | 是 | 无 | 连线稳定编码，版本内唯一。 |
| `from_node_key` | varchar(64) | 是 | 无 | 起点节点编码。 |
| `to_node_key` | varchar(64) | 是 | 无 | 终点节点编码。 |
| `condition_mode` | varchar(32) | 是 | `ALWAYS` | `ALWAYS`、`EXPRESSION`。 |
| `sort_order` | int | 是 | `0` | 条件匹配顺序。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_flow_line_key(template_version_id, line_key)`。
- 索引：`idx_flow_line_from(template_version_id, from_node_key)`。

### 4.5 `un_flow_template_condition`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `line_id` | bigint | 是 | 无 | 连线 ID。 |
| `field_code` | varchar(64) | 否 | `NULL` | 动态字段编码。 |
| `operator` | varchar(32) | 是 | 无 | 条件操作符，如 `EQ`、`NE`、`GT`、`IN`、`EMPTY`。 |
| `compare_value_json` | json | 否 | `NULL` | 比较值快照。 |
| `expression_json` | json | 否 | `NULL` | 复杂表达式结构。 |
| `sort_order` | int | 是 | `0` | 条件排序。 |

索引与约束：

- 主键：`id`。
- 索引：`idx_flow_condition_line(line_id)`、`idx_flow_condition_field(system_id, field_code)`。

### 4.6 `un_flow_binding`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `tenant_id` | bigint | 否 | `NULL` | 所属租户。 |
| `module_id` | bigint | 是 | 无 | 动态模块 ID，逻辑关联 `un_module_`。 |
| `action_code` | varchar(64) | 是 | `SUBMIT` | 触发动作，MVP 为提交审批。 |
| `template_id` | bigint | 是 | 无 | 流程模板 ID。 |
| `template_version_id` | bigint | 是 | 无 | 绑定的发布版本 ID。 |
| `status` | varchar(32) | 是 | `ENABLED` | `ENABLED`、`DISABLED`。 |
| `version_no` | int | 是 | `0` | 乐观锁版本。 |
| `deleted` | tinyint | 是 | `0` | 逻辑删除。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_flow_binding_module_action(system_id, tenant_id, module_id, action_code, deleted)`。
- 索引：`idx_flow_binding_version(template_version_id, status)`。

### 4.7 `un_flow_instance`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `tenant_id` | bigint | 否 | `NULL` | 所属租户。 |
| `module_id` | bigint | 是 | 无 | 动态模块 ID。 |
| `record_id` | bigint | 是 | 无 | 业务记录 ID。 |
| `template_id` | bigint | 是 | 无 | 流程模板 ID。 |
| `template_version_id` | bigint | 是 | 无 | 发起时发布版本 ID。 |
| `status` | varchar(32) | 是 | `IN_APPROVAL` | `IN_APPROVAL`、`APPROVED`、`REJECTED`、`WITHDRAWN`、`TERMINATED`。 |
| `starter_member_id` | bigint | 是 | 无 | 发起人系统成员 ID。 |
| `current_node_keys` | varchar(500) | 否 | `NULL` | 当前活跃节点编码列表。 |
| `started_at` | datetime | 是 | `CURRENT_TIMESTAMP` | 发起时间。 |
| `finished_at` | datetime | 否 | `NULL` | 结束时间。 |
| `request_id` | varchar(64) | 是 | 无 | 发起请求 requestId。 |
| `version_no` | int | 是 | `0` | 实例乐观锁版本。 |

索引与约束：

- 主键：`id`。
- 索引：`idx_flow_instance_record(system_id, tenant_id, module_id, record_id)`。
- 索引：`idx_flow_instance_status(system_id, tenant_id, status, updated_at)`。
- 索引：`idx_flow_instance_request(request_id)`。

### 4.8 `un_flow_task`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `tenant_id` | bigint | 否 | `NULL` | 所属租户。 |
| `instance_id` | bigint | 是 | 无 | 流程实例 ID。 |
| `node_key` | varchar(64) | 是 | 无 | 当前节点编码。 |
| `task_name` | varchar(128) | 是 | 无 | 任务名称。 |
| `status` | varchar(32) | 是 | `PENDING` | `PENDING`、`DONE`、`CANCELED`、`TRANSFERRED`、`RETURNED`。 |
| `claim_member_id` | bigint | 否 | `NULL` | 领取人系统成员 ID。 |
| `handler_member_id` | bigint | 否 | `NULL` | 实际处理人系统成员 ID。 |
| `due_at` | datetime | 否 | `NULL` | 到期时间。 |
| `claimed_at` | datetime | 否 | `NULL` | 领取时间。 |
| `handled_at` | datetime | 否 | `NULL` | 处理时间。 |
| `task_version` | int | 是 | `0` | 任务并发控制版本。 |
| `idempotency_key` | varchar(128) | 否 | `NULL` | 最近一次处理幂等键。 |
| `request_id` | varchar(64) | 否 | `NULL` | 最近一次处理 requestId。 |

索引与约束：

- 主键：`id`。
- 索引：`idx_flow_task_todo(system_id, tenant_id, status, updated_at)`。
- 索引：`idx_flow_task_instance(instance_id, node_key)`。
- 索引：`idx_flow_task_handler(handler_member_id, status, handled_at)`。
- 并发规则：处理、领取、取消领取必须带 `task_version`，更新条件固定为 `id = ? AND status = 'PENDING' AND task_version = ?`，成功后 `task_version + 1`。
- 幂等规则：`FLOW-009`、`FLOW-015`、`FLOW-016` 同时写 `un_sys_idempotency_record`，任务表只保存最近一次 requestId 便于排障。

### 4.9 `un_flow_task_actor`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `task_id` | bigint | 是 | 无 | 流程任务 ID。 |
| `actor_member_id` | bigint | 是 | 无 | 候选或处理成员 ID。 |
| `actor_type` | varchar(32) | 是 | `CANDIDATE` | `CANDIDATE`、`CLAIMER`、`HANDLER`、`TRANSFER_TARGET`。 |
| `source_type` | varchar(32) | 否 | `NULL` | 来源，如角色、部门、成员、发起人。 |
| `status` | varchar(32) | 是 | `ACTIVE` | `ACTIVE`、`INACTIVE`。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_flow_task_actor(task_id, actor_member_id, actor_type)`。
- 索引：`idx_flow_actor_member(system_id, actor_member_id, status)`。

### 4.10 `un_flow_cc`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `tenant_id` | bigint | 否 | `NULL` | 所属租户。 |
| `instance_id` | bigint | 是 | 无 | 流程实例 ID。 |
| `task_id` | bigint | 否 | `NULL` | 来源任务 ID。 |
| `cc_member_id` | bigint | 是 | 无 | 抄送成员 ID。 |
| `read_status` | varchar(32) | 是 | `UNREAD` | `UNREAD`、`READ`。 |
| `read_at` | datetime | 否 | `NULL` | 已读时间。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_flow_cc_once(instance_id, task_id, cc_member_id)`。
- 索引：`idx_flow_cc_member(system_id, cc_member_id, read_status, created_at)`。

### 4.11 `un_flow_action_log`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `tenant_id` | bigint | 否 | `NULL` | 所属租户。 |
| `instance_id` | bigint | 是 | 无 | 流程实例 ID。 |
| `task_id` | bigint | 否 | `NULL` | 流程任务 ID。 |
| `action` | varchar(32) | 是 | 无 | `APPROVE`、`REJECT`、`TRANSFER`、`RETURN`、`TERMINATE`、`WITHDRAW`、`CLAIM`、`UNCLAIM`。 |
| `operator_member_id` | bigint | 是 | 无 | 操作成员 ID。 |
| `comment` | varchar(1000) | 否 | `NULL` | 审批意见。 |
| `from_node_key` | varchar(64) | 否 | `NULL` | 来源节点。 |
| `to_node_key` | varchar(64) | 否 | `NULL` | 目标节点。 |
| `result_status` | varchar(32) | 是 | 无 | 操作后的实例或任务状态。 |
| `request_id` | varchar(64) | 是 | 无 | 请求追踪 ID。 |

索引与约束：

- 主键：`id`。
- 索引：`idx_flow_action_instance(instance_id, created_at)`、`idx_flow_action_request(request_id)`。

### 4.12 `un_flow_trace_log`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `instance_id` | bigint | 是 | 无 | 流程实例 ID。 |
| `from_node_key` | varchar(64) | 否 | `NULL` | 来源节点。 |
| `to_node_key` | varchar(64) | 否 | `NULL` | 目标节点。 |
| `event_type` | varchar(32) | 是 | 无 | `START`、`ENTER_NODE`、`LEAVE_NODE`、`FINISH`、`CANCEL`。 |
| `event_snapshot_json` | json | 否 | `NULL` | 节点变量、候选人和条件命中快照。 |
| `request_id` | varchar(64) | 是 | 无 | 请求追踪 ID。 |

索引与约束：

- 主键：`id`。
- 索引：`idx_flow_trace_instance(instance_id, created_at)`、`idx_flow_trace_request(request_id)`。

## 五、上传文件表字段设计

### 5.1 `un_upload_storage_config`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 否 | `NULL` | 所属系统；为空表示平台默认配置。 |
| `tenant_id` | bigint | 否 | `NULL` | 所属租户。 |
| `code` | varchar(64) | 是 | 无 | 存储配置编码。 |
| `name` | varchar(128) | 是 | 无 | 存储配置名称。 |
| `storage_type` | varchar(32) | 是 | `LOCAL` | `LOCAL`、`S3`、`MINIO`、`OSS`。 |
| `endpoint` | varchar(255) | 否 | `NULL` | 对象存储 endpoint。 |
| `bucket_name` | varchar(128) | 否 | `NULL` | bucket。 |
| `root_path` | varchar(500) | 否 | `NULL` | 本地或对象根路径。 |
| `config_json` | json | 否 | `NULL` | 非敏感配置。 |
| `secret_ref` | varchar(255) | 否 | `NULL` | 密钥引用，不保存明文。 |
| `default_flag` | tinyint | 是 | `0` | 是否默认配置。 |
| `status` | varchar(32) | 是 | `ENABLED` | `ENABLED`、`DISABLED`。 |
| `deleted` | tinyint | 是 | `0` | 逻辑删除。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_upload_storage_code(system_id, tenant_id, code, deleted)`。
- 索引：`idx_upload_storage_default(system_id, tenant_id, default_flag, status)`。

### 5.2 `un_upload_file`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `tenant_id` | bigint | 否 | `NULL` | 所属租户。 |
| `storage_config_id` | bigint | 是 | 无 | 存储配置 ID。 |
| `file_name` | varchar(255) | 是 | 无 | 原始文件名。 |
| `extension` | varchar(32) | 否 | `NULL` | 扩展名。 |
| `content_type` | varchar(128) | 否 | `NULL` | MIME 类型。 |
| `file_size` | bigint | 是 | `0` | 文件大小，字节。 |
| `sha256` | char(64) | 否 | `NULL` | 文件内容 SHA-256。 |
| `storage_key` | varchar(500) | 是 | 无 | 对象存储 key 或本地相对路径。 |
| `status` | varchar(32) | 是 | `TEMP` | `TEMP`、`REFERENCED`、`DELETED`、`EXPIRED`。 |
| `previewable` | tinyint | 是 | `0` | 是否支持预览。 |
| `owner_member_id` | bigint | 是 | 无 | 上传人系统成员 ID。 |
| `ref_count` | int | 是 | `0` | 当前有效引用数。 |
| `temp_expires_at` | datetime | 否 | `NULL` | 临时文件过期时间。 |
| `deleted_at` | datetime | 否 | `NULL` | 删除或过期时间。 |
| `request_id` | varchar(64) | 是 | 无 | 上传请求 requestId。 |
| `deleted` | tinyint | 是 | `0` | 逻辑删除。 |

索引与约束：

- 主键：`id`。
- 索引：`idx_upload_file_system_status(system_id, tenant_id, status, created_at)`。
- 索引：`idx_upload_file_owner(system_id, owner_member_id, created_at)`。
- 索引：`idx_upload_file_temp_expire(status, temp_expires_at)`。
- 索引：`idx_upload_file_request(request_id)`。
- 删除规则：`ref_count > 0` 或存在有效 `un_upload_file_reference` 时不得物理删除。

### 5.3 `un_upload_file_part`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `file_id` | bigint | 是 | 无 | 文件 ID。 |
| `upload_id` | varchar(128) | 是 | 无 | 分片上传会话 ID。 |
| `part_no` | int | 是 | 无 | 分片序号。 |
| `part_size` | bigint | 是 | `0` | 分片大小。 |
| `part_sha256` | char(64) | 否 | `NULL` | 分片 SHA-256。 |
| `storage_etag` | varchar(255) | 否 | `NULL` | 对象存储 ETag。 |
| `status` | varchar(32) | 是 | `UPLOADED` | `UPLOADED`、`MERGED`、`FAILED`。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_upload_part(file_id, upload_id, part_no)`。
- 索引：`idx_upload_part_status(file_id, status)`。

### 5.4 `un_upload_file_reference`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 所属系统。 |
| `tenant_id` | bigint | 否 | `NULL` | 所属租户。 |
| `file_id` | bigint | 是 | 无 | 文件 ID。 |
| `biz_type` | varchar(64) | 是 | 无 | 引用类型，如 `MODULE_RECORD_FIELD`、`EXPORT_RESULT`、`FLOW_COMMENT`。 |
| `biz_id` | bigint | 是 | 无 | 业务对象 ID。 |
| `module_id` | bigint | 否 | `NULL` | 动态模块 ID。 |
| `record_id` | bigint | 否 | `NULL` | 业务记录 ID。 |
| `field_code` | varchar(64) | 否 | `NULL` | 附件字段编码。 |
| `display_name` | varchar(255) | 否 | `NULL` | 引用展示名。 |
| `sort_order` | int | 是 | `0` | 排序。 |
| `status` | varchar(32) | 是 | `ACTIVE` | `ACTIVE`、`UNBOUND`。 |
| `bound_by` | bigint | 是 | 无 | 绑定人系统成员 ID。 |
| `bound_at` | datetime | 是 | `CURRENT_TIMESTAMP` | 绑定时间。 |
| `unbound_at` | datetime | 否 | `NULL` | 解绑时间。 |
| `request_id` | varchar(64) | 是 | 无 | 绑定请求 requestId。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_upload_ref(file_id, biz_type, biz_id, field_code, status)`。
- 索引：`idx_upload_ref_biz(system_id, tenant_id, biz_type, biz_id)`。
- 索引：`idx_upload_ref_record(system_id, module_id, record_id, field_code)`。
- 权限规则：预览/下载必须命中有效引用，并回到 `biz_type + biz_id` 对应业务对象做数据范围和字段权限校验。

## 六、OpenAPI 表字段设计

### 6.1 `un_openapi_client`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | bigint | 是 | 无 | 绑定系统。 |
| `tenant_id` | bigint | 否 | `NULL` | 绑定租户。 |
| `code` | varchar(64) | 是 | 无 | 客户端编码。 |
| `name` | varchar(128) | 是 | 无 | 客户端名称。 |
| `status` | varchar(32) | 是 | `DRAFT` | `DRAFT`、`ENABLED`、`DISABLED`、`EXPIRED`。 |
| `data_scope_json` | json | 否 | `NULL` | 客户端默认数据范围快照。 |
| `rate_limit_policy_json` | json | 否 | `NULL` | 管理端展示用限流策略快照。 |
| `expires_at` | datetime | 否 | `NULL` | 客户端过期时间。 |
| `last_used_at` | datetime | 否 | `NULL` | 最近调用时间。 |
| `version_no` | int | 是 | `0` | 乐观锁版本。 |
| `deleted` | tinyint | 是 | `0` | 逻辑删除。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_openapi_client_code(system_id, tenant_id, code, deleted)`。
- 索引：`idx_openapi_client_status(system_id, tenant_id, status)`。

### 6.2 `un_openapi_client_credential`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `client_id` | bigint | 是 | 无 | OpenAPI 客户端 ID。 |
| `access_key` | varchar(128) | 是 | 无 | AK，外部请求传入。 |
| `secret_hash` | char(64) | 是 | 无 | secret 哈希，用于辅助校验或审计，不保存明文。 |
| `sign_secret_enc` | varchar(1000) | 是 | 无 | 签名密钥密文。 |
| `masked_secret` | varchar(64) | 是 | 无 | 脱敏展示值。 |
| `algorithm` | varchar(32) | 是 | `HMAC-SHA256` | 签名算法。 |
| `status` | varchar(32) | 是 | `ACTIVE` | `ACTIVE`、`EXPIRED`、`REVOKED`。 |
| `secret_visible_once` | tinyint | 是 | `1` | secret 是否仍处于创建/轮换响应一次性展示窗口。 |
| `issued_at` | datetime | 是 | `CURRENT_TIMESTAMP` | 签发时间。 |
| `expires_at` | datetime | 否 | `NULL` | 凭证过期时间。 |
| `revoked_at` | datetime | 否 | `NULL` | 吊销时间。 |
| `last_used_at` | datetime | 否 | `NULL` | 最近使用时间。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_openapi_access_key(access_key)`。
- 索引：`idx_openapi_credential_client(client_id, status)`。
- 安全规则：`secretOnce` 只在创建或轮换响应中返回一次，DB 永不保存明文 secret。

### 6.3 `un_openapi_client_scope`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `client_id` | bigint | 是 | 无 | OpenAPI 客户端 ID。 |
| `system_id` | bigint | 是 | 无 | 绑定系统。 |
| `tenant_id` | bigint | 否 | `NULL` | 绑定租户。 |
| `scope_code` | varchar(64) | 是 | 无 | scope，如 `record:read`、`record:create`、`flow:task:handle`、`file:download`。 |
| `module_id` | bigint | 否 | `NULL` | 限定动态模块。 |
| `field_permission_json` | json | 否 | `NULL` | 字段可读、可写授权。 |
| `data_scope_json` | json | 否 | `NULL` | 数据范围规则。 |
| `status` | varchar(32) | 是 | `ENABLED` | `ENABLED`、`DISABLED`。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_openapi_scope(client_id, scope_code, module_id)`。
- 索引：`idx_openapi_scope_system(system_id, tenant_id, scope_code, status)`。

### 6.4 `un_openapi_ip_whitelist`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `client_id` | bigint | 是 | 无 | OpenAPI 客户端 ID。 |
| `ip_rule` | varchar(64) | 是 | 无 | IP 或 CIDR。 |
| `rule_type` | varchar(16) | 是 | `CIDR` | `IP`、`CIDR`。 |
| `status` | varchar(32) | 是 | `ENABLED` | `ENABLED`、`DISABLED`。 |
| `description` | varchar(255) | 否 | `NULL` | 说明。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_openapi_ip_rule(client_id, ip_rule)`。
- 索引：`idx_openapi_ip_status(client_id, status)`。

### 6.5 `un_openapi_nonce`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `client_id` | bigint | 是 | 无 | OpenAPI 客户端 ID。 |
| `access_key` | varchar(128) | 是 | 无 | AK。 |
| `nonce` | varchar(128) | 是 | 无 | 请求 nonce。 |
| `request_id` | varchar(64) | 是 | 无 | 请求追踪 ID。 |
| `source_ip` | varchar(64) | 否 | `NULL` | 来源 IP。 |
| `expires_at` | datetime | 是 | 无 | 过期时间，默认请求时间后 10 分钟且不小于时间窗口 2 倍。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_openapi_nonce(client_id, access_key, nonce)`。
- 索引：`idx_openapi_nonce_expire(expires_at)`。

### 6.6 `un_openapi_idempotency_record`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `client_id` | bigint | 是 | 无 | OpenAPI 客户端 ID。 |
| `system_id` | bigint | 是 | 无 | 系统 ID。 |
| `tenant_id` | bigint | 否 | `NULL` | 租户 ID。 |
| `api_id` | varchar(32) | 是 | 无 | API ID，如 `OPN-003`。 |
| `biz_action` | varchar(64) | 是 | 无 | 业务动作。 |
| `idempotency_key` | varchar(128) | 是 | 无 | 幂等键。 |
| `scope_key` | varchar(500) | 是 | 无 | 归一化幂等 scope。 |
| `request_hash` | char(64) | 是 | 无 | 请求摘要 SHA-256。 |
| `status` | varchar(32) | 是 | `PROCESSING` | `PROCESSING`、`SUCCESS`、`FAILED`、`CONFLICT`。 |
| `result_snapshot_json` | json | 否 | `NULL` | 结果快照，含 code、success、data 标识、requestId。 |
| `signature_result` | varchar(32) | 否 | `NULL` | 签名结果。 |
| `scope_result` | varchar(32) | 否 | `NULL` | scope 命中结果。 |
| `request_id` | varchar(64) | 是 | 无 | 首次请求 requestId。 |
| `expires_at` | datetime | 是 | 无 | 幂等记录过期时间，默认 72 小时且不小于 24 小时。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_openapi_idempotency(scope_key)`。
- 索引：`idx_openapi_idempotency_expire(expires_at)`、`idx_openapi_idempotency_request(request_id)`。

### 6.7 `un_openapi_rate_limit_policy`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `client_id` | bigint | 是 | 无 | OpenAPI 客户端 ID。 |
| `system_id` | bigint | 是 | 无 | 系统 ID。 |
| `tenant_id` | bigint | 否 | `NULL` | 租户 ID。 |
| `api_id` | varchar(32) | 否 | `NULL` | API ID；为空表示客户端默认策略。 |
| `scope_code` | varchar(64) | 否 | `NULL` | scope；为空表示通用策略。 |
| `source_ip` | varchar(64) | 否 | `NULL` | 来源 IP 限定；为空表示不限定。 |
| `window_seconds` | int | 是 | `60` | 限流窗口秒数。 |
| `max_requests` | int | 是 | 无 | 窗口最大请求数。 |
| `burst` | int | 是 | `0` | 突发额度。 |
| `effective_from` | datetime | 是 | `CURRENT_TIMESTAMP` | 生效时间。 |
| `effective_to` | datetime | 否 | `NULL` | 失效时间。 |
| `status` | varchar(32) | 是 | `ENABLED` | `ENABLED`、`DISABLED`。 |

索引与约束：

- 主键：`id`。
- 索引：`idx_openapi_rate_policy(client_id, api_id, scope_code, status)`。

### 6.8 `un_openapi_rate_limit_counter`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `policy_id` | bigint | 是 | 无 | 限流策略 ID。 |
| `dimension_key` | varchar(500) | 是 | 无 | `clientId + systemId + tenantId + apiId + scopeCode + sourceIp`。 |
| `window_start_at` | datetime | 是 | 无 | 窗口开始时间。 |
| `window_end_at` | datetime | 是 | 无 | 窗口结束时间。 |
| `request_count` | int | 是 | `0` | 当前窗口计数。 |
| `last_request_id` | varchar(64) | 否 | `NULL` | 最近一次 requestId。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_openapi_rate_counter(policy_id, dimension_key, window_start_at)`。
- 索引：`idx_openapi_rate_counter_window(window_end_at)`。
- 并发规则：窗口计数必须使用原子递增或行锁，不能先查后写。

### 6.9 `un_openapi_access_log`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `request_id` | varchar(64) | 是 | 无 | 请求追踪 ID。 |
| `trace_id` | varchar(64) | 否 | `NULL` | 链路追踪 ID。 |
| `client_id` | bigint | 否 | `NULL` | 客户端 ID；AK 无效时可为空。 |
| `access_key` | varchar(128) | 否 | `NULL` | AK，必要时脱敏。 |
| `system_id` | bigint | 否 | `NULL` | 系统 ID。 |
| `tenant_id` | bigint | 否 | `NULL` | 租户 ID。 |
| `api_id` | varchar(32) | 否 | `NULL` | API ID。 |
| `method` | varchar(16) | 是 | 无 | HTTP 方法。 |
| `path` | varchar(500) | 是 | 无 | 请求路径。 |
| `source_ip` | varchar(64) | 否 | `NULL` | 来源 IP。 |
| `body_hash` | char(64) | 否 | `NULL` | 请求 body SHA-256。 |
| `signature_result` | varchar(32) | 是 | `NOT_CHECKED` | `PASS`、`FAIL`、`NOT_CHECKED`。 |
| `nonce_result` | varchar(32) | 是 | `NOT_CHECKED` | `PASS`、`REPLAY`、`NOT_CHECKED`。 |
| `scope_result` | varchar(32) | 是 | `NOT_CHECKED` | `PASS`、`DENIED`、`NOT_CHECKED`。 |
| `rate_limit_result` | varchar(32) | 是 | `NOT_CHECKED` | `PASS`、`LIMITED`、`NOT_CHECKED`。 |
| `idempotency_result` | varchar(32) | 是 | `NOT_CHECKED` | `NEW`、`REPLAY`、`CONFLICT`、`PROCESSING`、`NOT_CHECKED`。 |
| `result` | varchar(32) | 是 | 无 | `SUCCESS`、`FAILED`。 |
| `http_status` | int | 是 | 无 | HTTP 状态码。 |
| `error_code` | varchar(64) | 否 | `NULL` | 错误码。 |
| `duration_ms` | int | 否 | `NULL` | 耗时毫秒。 |
| `biz_type` | varchar(64) | 否 | `NULL` | 业务对象类型。 |
| `biz_id` | varchar(64) | 否 | `NULL` | 业务对象 ID。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_openapi_access_request(request_id)`。
- 索引：`idx_openapi_access_client(client_id, created_at)`。
- 索引：`idx_openapi_access_system(system_id, tenant_id, created_at)`。
- 索引：`idx_openapi_access_error(error_code, created_at)`。

## 七、通用幂等、审计与运维表字段设计

### 7.1 `un_sys_idempotency_record`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `caller_type` | varchar(32) | 是 | `INTERNAL` | `INTERNAL`；OpenAPI 使用专表。 |
| `account_id` | bigint | 否 | `NULL` | 平台账号 ID。 |
| `member_id` | bigint | 否 | `NULL` | 系统成员 ID。 |
| `system_id` | bigint | 否 | `NULL` | 系统 ID。 |
| `tenant_id` | bigint | 否 | `NULL` | 租户 ID。 |
| `api_id` | varchar(32) | 是 | 无 | API ID。 |
| `biz_action` | varchar(64) | 是 | 无 | 业务动作。 |
| `idempotency_key` | varchar(128) | 是 | 无 | 幂等键。 |
| `scope_key` | varchar(500) | 是 | 无 | 归一化幂等 scope。 |
| `request_hash` | char(64) | 是 | 无 | 请求摘要 SHA-256。 |
| `status` | varchar(32) | 是 | `PROCESSING` | `PROCESSING`、`SUCCESS`、`FAILED`、`CONFLICT`。 |
| `result_snapshot_json` | json | 否 | `NULL` | 结果快照。 |
| `request_id` | varchar(64) | 是 | 无 | 首次请求 requestId。 |
| `expires_at` | datetime | 是 | 无 | 过期时间；默认 24 小时，流程任务和导出任务 72 小时。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_sys_idempotency_scope(scope_key)`。
- 索引：`idx_sys_idempotency_expire(expires_at)`、`idx_sys_idempotency_request(request_id)`。

### 7.2 `un_sys_request_log`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `request_id` | varchar(64) | 是 | 无 | 请求追踪 ID。 |
| `trace_id` | varchar(64) | 否 | `NULL` | 链路追踪 ID。 |
| `operator_type` | varchar(32) | 是 | 无 | `ACCOUNT`、`MEMBER`、`OPENAPI_CLIENT`、`SYSTEM`。 |
| `operator_id` | varchar(64) | 否 | `NULL` | 操作主体 ID。 |
| `system_id` | bigint | 否 | `NULL` | 系统 ID。 |
| `tenant_id` | bigint | 否 | `NULL` | 租户 ID。 |
| `method` | varchar(16) | 是 | 无 | HTTP 方法。 |
| `path` | varchar(500) | 是 | 无 | 请求路径。 |
| `module` | varchar(64) | 否 | `NULL` | 模块命名空间。 |
| `client_ip` | varchar(64) | 否 | `NULL` | 客户端 IP。 |
| `http_status` | int | 是 | 无 | HTTP 状态码。 |
| `result` | varchar(32) | 是 | 无 | `SUCCESS`、`FAILED`。 |
| `duration_ms` | int | 否 | `NULL` | 耗时毫秒。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_sys_request_id(request_id)`。
- 索引：`idx_sys_request_system(system_id, tenant_id, created_at)`、`idx_sys_request_operator(operator_type, operator_id, created_at)`。

### 7.3 `un_sys_error_log`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `request_id` | varchar(64) | 是 | 无 | 请求追踪 ID。 |
| `trace_id` | varchar(64) | 否 | `NULL` | 链路追踪 ID。 |
| `system_id` | bigint | 否 | `NULL` | 系统 ID。 |
| `tenant_id` | bigint | 否 | `NULL` | 租户 ID。 |
| `error_code` | varchar(64) | 是 | 无 | 模块化错误码。 |
| `error_message` | varchar(1000) | 是 | 无 | 稳定错误提示。 |
| `stack_summary` | text | 否 | `NULL` | 脱敏栈摘要。 |
| `biz_type` | varchar(64) | 否 | `NULL` | 业务对象类型。 |
| `biz_id` | varchar(64) | 否 | `NULL` | 业务对象 ID。 |

索引与约束：

- 主键：`id`。
- 索引：`idx_sys_error_request(request_id)`、`idx_sys_error_code(error_code, created_at)`。

### 7.4 `un_audit_operation_log`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `request_id` | varchar(64) | 是 | 无 | 请求追踪 ID。 |
| `trace_id` | varchar(64) | 否 | `NULL` | 链路追踪 ID。 |
| `operator_type` | varchar(32) | 是 | 无 | `ACCOUNT`、`MEMBER`、`OPENAPI_CLIENT`、`SYSTEM`。 |
| `operator_id` | varchar(64) | 否 | `NULL` | 操作主体 ID。 |
| `operator_name` | varchar(128) | 否 | `NULL` | 操作主体名称快照。 |
| `system_id` | bigint | 否 | `NULL` | 系统 ID。 |
| `tenant_id` | bigint | 否 | `NULL` | 租户 ID。 |
| `module` | varchar(64) | 是 | 无 | 模块，如 `FLOW`、`UPLOAD`、`OPENAPI`。 |
| `biz_type` | varchar(64) | 否 | `NULL` | 业务对象类型。 |
| `biz_id` | varchar(64) | 否 | `NULL` | 业务对象 ID。 |
| `action` | varchar(64) | 是 | 无 | 操作动作。 |
| `result` | varchar(32) | 是 | 无 | `SUCCESS`、`FAILED`。 |
| `error_code` | varchar(64) | 否 | `NULL` | 错误码。 |
| `summary` | varchar(1000) | 否 | `NULL` | 审计摘要。 |

索引与约束：

- 主键：`id`。
- 索引：`idx_audit_operation_request(request_id)`。
- 索引：`idx_audit_operation_biz(system_id, tenant_id, biz_type, biz_id, created_at)`。
- 索引：`idx_audit_operation_operator(operator_type, operator_id, created_at)`。

### 7.5 `un_audit_record_change`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `request_id` | varchar(64) | 是 | 无 | 请求追踪 ID。 |
| `system_id` | bigint | 是 | 无 | 系统 ID。 |
| `tenant_id` | bigint | 否 | `NULL` | 租户 ID。 |
| `module_id` | bigint | 是 | 无 | 动态模块 ID。 |
| `record_id` | bigint | 是 | 无 | 业务记录 ID。 |
| `change_type` | varchar(32) | 是 | 无 | `CREATE`、`UPDATE`、`DELETE`、`STATUS_CHANGE`、`FLOW_ACTION`。 |
| `before_snapshot_json` | json | 否 | `NULL` | 变更前快照。 |
| `after_snapshot_json` | json | 否 | `NULL` | 变更后快照。 |
| `changed_by_type` | varchar(32) | 是 | 无 | `MEMBER`、`OPENAPI_CLIENT`、`SYSTEM`。 |
| `changed_by_id` | varchar(64) | 是 | 无 | 变更主体 ID。 |

索引与约束：

- 主键：`id`。
- 索引：`idx_audit_record_change_record(system_id, tenant_id, module_id, record_id, created_at)`。
- 索引：`idx_audit_record_change_request(request_id)`。

### 7.6 `un_sys_health_check_result`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `request_id` | varchar(64) | 是 | 无 | 请求追踪 ID。 |
| `component` | varchar(64) | 是 | 无 | 组件，如 `DB`、`REDIS`、`UPLOAD_STORAGE`、`OPENAPI_KEY`。 |
| `status` | varchar(32) | 是 | 无 | `UP`、`WARN`、`DOWN`。 |
| `result` | varchar(32) | 是 | 无 | `SUCCESS`、`FAILED`。 |
| `message` | varchar(1000) | 否 | `NULL` | 检查消息。 |
| `suggestion` | varchar(1000) | 否 | `NULL` | 修复建议。 |
| `checked_at` | datetime | 是 | `CURRENT_TIMESTAMP` | 检查时间。 |

索引与约束：

- 主键：`id`。
- 索引：`idx_sys_health_component(component, checked_at)`、`idx_sys_health_request(request_id)`。

### 7.7 `un_sys_runtime_config_check`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `request_id` | varchar(64) | 是 | 无 | 请求追踪 ID。 |
| `config_key` | varchar(128) | 是 | 无 | 配置键。 |
| `component` | varchar(64) | 是 | 无 | 所属组件。 |
| `status` | varchar(32) | 是 | 无 | `PASS`、`WARN`、`FAIL`。 |
| `message` | varchar(1000) | 否 | `NULL` | 检查消息。 |
| `suggestion` | varchar(1000) | 否 | `NULL` | 修复建议。 |
| `checked_at` | datetime | 是 | `CURRENT_TIMESTAMP` | 检查时间。 |

索引与约束：

- 主键：`id`。
- 索引：`idx_sys_config_check_key(config_key, checked_at)`、`idx_sys_config_check_request(request_id)`。

### 7.8 `un_sys_migration_status`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `version` | varchar(64) | 是 | 无 | migration 版本。 |
| `description` | varchar(255) | 否 | `NULL` | migration 描述。 |
| `status` | varchar(32) | 是 | 无 | `SUCCESS`、`FAILED`、`PENDING`、`REPAIRED`。 |
| `checksum` | varchar(128) | 否 | `NULL` | 校验值。 |
| `installed_at` | datetime | 否 | `NULL` | 安装时间。 |
| `execution_time_ms` | int | 否 | `NULL` | 执行耗时。 |
| `error_message` | varchar(1000) | 否 | `NULL` | 失败摘要。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_sys_migration_version(version)`。
- 索引：`idx_sys_migration_status(status, installed_at)`。

## 八、状态枚举

| 对象 | 字段 | 枚举 |
| --- | --- | --- |
| 流程模板 | `status` | `DRAFT`、`PUBLISHED`、`DISABLED` |
| 流程版本 | `status` | `PUBLISHED`、`DISCARDED` |
| 流程实例 | `status` | `IN_APPROVAL`、`APPROVED`、`REJECTED`、`WITHDRAWN`、`TERMINATED` |
| 流程任务 | `status` | `PENDING`、`DONE`、`CANCELED`、`TRANSFERRED`、`RETURNED` |
| 流程抄送 | `read_status` | `UNREAD`、`READ` |
| 文件 | `status` | `TEMP`、`REFERENCED`、`DELETED`、`EXPIRED` |
| 文件引用 | `status` | `ACTIVE`、`UNBOUND` |
| OpenAPI 客户端 | `status` | `DRAFT`、`ENABLED`、`DISABLED`、`EXPIRED` |
| OpenAPI 凭证 | `status` | `ACTIVE`、`EXPIRED`、`REVOKED` |
| 幂等记录 | `status` | `PROCESSING`、`SUCCESS`、`FAILED`、`CONFLICT` |
| 审计结果 | `result` | `SUCCESS`、`FAILED` |
| 健康状态 | `status` | `UP`、`WARN`、`DOWN` |

## 九、表关系与事务边界

### 9.1 表关系

- `un_flow_template` 1 对多 `un_flow_template_version`。
- `un_flow_template_version` 1 对多 `un_flow_template_node`、`un_flow_template_line`。
- `un_flow_template_line` 1 对多 `un_flow_template_condition`。
- `un_flow_binding` 逻辑关联 `un_module_` 模块与 `un_flow_template_version`。
- `un_flow_instance` 逻辑关联 `un_module_` 业务记录，并关联发起时的 `un_flow_template_version`。
- `un_flow_instance` 1 对多 `un_flow_task`、`un_flow_cc`、`un_flow_action_log`、`un_flow_trace_log`。
- `un_flow_task` 1 对多 `un_flow_task_actor`。
- `un_upload_file` 1 对多 `un_upload_file_reference` 和 `un_upload_file_part`。
- `un_upload_file_reference` 逻辑关联 `un_module_` 业务记录、`un_module_` 导出任务结果或流程评论。
- `un_openapi_client` 1 对多 `un_openapi_client_credential`、`un_openapi_client_scope`、`un_openapi_ip_whitelist`、`un_openapi_rate_limit_policy`、`un_openapi_access_log`。
- `un_openapi_access_log` 通过 `request_id` 串联 `un_sys_request_log`、`un_sys_error_log`、`un_audit_operation_log` 和 `un_audit_record_change`。

### 9.2 API 数据落点

| API 场景 | 主落点 | 关联落点 |
| --- | --- | --- |
| `FLOW-002` 创建模板 | `un_flow_template` | `un_audit_operation_log`、`un_sys_idempotency_record` 按需 |
| `FLOW-003` 保存流程图 | 草稿态 `un_flow_template` 的结构草稿由业务实现持久化；发布后落 `un_flow_template_version/node/line/condition` | `un_audit_operation_log` |
| `FLOW-005` 发布流程 | `un_flow_template_version`、`un_flow_template_node`、`un_flow_template_line`、`un_flow_template_condition` | `un_sys_idempotency_record`、`un_audit_operation_log` |
| `FLOW-006` 绑定模块 | `un_flow_binding` | `un_audit_operation_log` |
| `FLOW-009` 处理任务 | `un_flow_task`、`un_flow_instance`、`un_flow_action_log`、`un_flow_trace_log` | `un_sys_idempotency_record`、`un_audit_record_change`、`un_audit_operation_log` |
| `FLOW-015/016` 领取/取消领取 | `un_flow_task`、`un_flow_task_actor` | `un_sys_idempotency_record`、`un_flow_action_log` |
| `FILE-001` 上传 | `un_upload_file` | `un_sys_idempotency_record`、`un_audit_operation_log` |
| 记录保存绑定附件 | `un_upload_file_reference`、`un_upload_file.ref_count/status` | `un_audit_record_change` |
| `FILE-004/005` 预览/下载 | `un_upload_file`、`un_upload_file_reference` 权限校验 | `un_audit_operation_log` |
| `OPM-002` 创建客户端 | `un_openapi_client`、`un_openapi_client_credential`、`un_openapi_client_scope` | `un_sys_idempotency_record`、`un_audit_operation_log` |
| `OPM-005` 凭证轮换 | `un_openapi_client_credential` | `un_sys_idempotency_record`、`un_audit_operation_log` |
| `OPN-003/004/005/006` 外部写入 | `un_openapi_access_log`、`un_openapi_nonce`、`un_openapi_idempotency_record` | 对应 `un_module_`、`un_flow_`、`un_upload_` 业务表和审计表 |
| `AUD-*` 查询 | `un_sys_request_log`、`un_sys_error_log`、`un_audit_operation_log`、`un_audit_record_change`、`un_openapi_access_log` | 只读 |
| `OPS-*` 查询 | `un_sys_health_check_result`、`un_sys_runtime_config_check`、`un_sys_migration_status` | 只读 |

### 9.3 事务与并发

- 流程提交和任务处理必须在同一业务事务内更新流程实例、任务、动作日志、轨迹日志、业务记录状态和审计。
- 流程任务并发以 `task_version` 和 `status=PENDING` 原子更新控制，重复处理返回 `FLOW_TASK_ALREADY_HANDLED` 或幂等回放结果。
- 文件物理上传和业务保存分离；上传成功但业务保存失败时文件保持 `TEMP`，由 `temp_expires_at` 清理，不删除已引用文件。
- 文件绑定/解绑必须和业务记录保存处于同一事务内，维护 `un_upload_file.ref_count`。
- OpenAPI 校验顺序固定为 accessKey -> 客户端状态 -> IP 白名单 -> timestamp -> nonce -> body hash -> signature -> scope -> rate limit -> 幂等 -> 业务事务。
- OpenAPI 写接口先写或预留 `un_openapi_access_log` 和幂等记录，再进入内部业务事务；业务成功或失败后回填签名、scope、限流、幂等和业务结果。
- 限流计数使用 `un_openapi_rate_limit_counter` 原子递增或行锁，不允许“查询计数 + 1”无锁写入。
- 审计写入失败不能静默吞掉；至少写入 `un_sys_error_log` 并保留相同 `request_id`。

## 十、旧项目参考与差异

| 旧项目方向 | 新设计处理 |
| --- | --- |
| 旧 OpenAPI 使用 `un_app_client`、`un_app_client_credential`、`un_app_client_scope`、`un_app_ip_whitelist`、`un_app_access_log`。 | 新项目统一迁移到 `un_openapi_*`，不保留 `un_app_*` 新表。 |
| 旧凭证字段通过后续 migration 补 `sign_secret_enc`。 | 新设计在 `un_openapi_client_credential` 一次性定义 `secret_hash`、`sign_secret_enc`、`masked_secret`、轮换和状态字段。 |
| 旧 flow 同时存在 DDL 确认表和实体未确认表。 | 新设计采用清晰的 `template/version/instance/task` 表名，旧未确认实体表名不纳入新表清单。 |
| 旧上传主要是文件主表、分片、存储配置。 | 新设计补 `un_upload_file_reference`、`status`、`ref_count`、`temp_expires_at`、对象存储和权限校验字段。 |
| 旧日志链路分散。 | 新设计用 `request_id` 串联请求、错误、操作、记录变更和 OpenAPI 调用日志。 |

## 十一、自检结果

| 自检项 | 结果 |
| --- | --- |
| 是否只写 DBA-004 指定分片 | 通过。本文只新增 `docs/db_design_parts/flow-upload-openapi-audit.md`。 |
| 表前缀是否合规 | 通过。新建表仅使用 `un_flow_`、`un_upload_`、`un_openapi_`、`un_sys_`、`un_audit_`；`un_app_` 仅作为旧项目历史表域说明，未作为新表使用，未出现 `un_platt_`。 |
| 是否生成 SQL | 通过。未生成 `sql/init.sql`。 |
| 流程任务并发字段是否覆盖 | 通过。`un_flow_task.task_version/status` 和 `un_sys_idempotency_record` 支撑并发与幂等。 |
| 文件引用/临时文件是否覆盖 | 通过。`un_upload_file.status/ref_count/temp_expires_at` 与 `un_upload_file_reference` 支撑引用权限和清理。 |
| OpenAPI AK/SK、nonce、幂等、限流、调用日志是否覆盖 | 通过。已设计凭证、nonce、幂等、限流策略/计数、调用日志表。 |
| requestId 审计链路是否覆盖 | 通过。请求、错误、操作、记录变更、流程、文件、OpenAPI 均保留 `request_id`。 |
| API 数据落点是否覆盖 | 通过。已按 FLOW、FILE、OPM/OPN、AUD、OPS 列出主落点和关联落点。 |
