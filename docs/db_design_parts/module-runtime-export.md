# DBA-003 模块配置运行记录导出表设计分片

## 一、分片边界

本分片只覆盖 DBA-003，输出路径为 `docs/db_design_parts/module-runtime-export.md`。设计依据仅使用任务文件、冻结任务计划、PRD、项目理解、冻结 API、API 冻结结论、服务信息、旧项目参考摘要、DBA-001 表域映射和 `.codex/state.json`。

本分片只设计 `un_module_` 表域中的应用配置、模块建模、字段与页面配置、发布版本、运行记录、动态字段 EAV、索引值、唯一性、历史快照、自动编号和导出任务闭环。不生成 SQL，不写 `docs/db_design.md`，不设计 `un_flow_`、`un_upload_`、`un_openapi_`、`un_sys_` 或 `un_audit_` 表。附件元数据、文件物理存储、文件引用表和审计日志由 DBA-004 负责；本分片只保存业务字段值中的 `fileId` 数组、导出结果 `result_file_id` 逻辑引用和历史展示快照。

运行态约束：

1. 运行台 schema、记录查询、新增、编辑、删除、提交审批和导出只能读取 `un_module_publish_version` 中已发布且当前有效的配置快照。
2. API 的 `values[]` 不暴露物理 EAV 表结构；后端负责在 `un_module_record_value`、`un_module_record_index`、`un_module_record_unique_index`、`un_module_record_relation` 和 `un_module_record_child_row` 之间转换。
3. 动态字段唯一性只约束非 `DELETED` 业务记录。软删除后允许复用唯一值；恢复已删除记录必须重新校验唯一性。
4. 导出任务创建时必须保存筛选快照、排序快照、字段权限快照、数据范围快照、脱敏快照和发布版本快照，后续权限变化不影响已创建任务的可审计解释。

## 二、表与功能映射

| 表名 | 功能模块 | 描述 |
| --- | --- | --- |
| `un_module_app` | 应用配置 | 系统/租户下的业务应用主表，不使用旧 `un_app_` 前缀。 |
| `un_module_app_version` | 应用配置 | 应用级配置版本和发布快照摘要。 |
| `un_module_model` | 模块建模 | 动态模块主表；API 中的 `moduleId/moduleCode` 对应该表。 |
| `un_module_field` | 字段配置 | 模块字段定义、类型、校验、唯一、关联、子表、自动编号配置。 |
| `un_module_field_option` | 字段配置 | 单选、多选、标签等字段的静态选项。 |
| `un_module_unique_constraint` | 字段配置 | 组合唯一约束配置，字段级唯一由 `un_module_field.unique_flag` 表达。 |
| `un_module_page_schema` | 页面配置 | 列表、表单、详情 schema 草稿。 |
| `un_module_menu` | 菜单配置 | 运行菜单和模块入口配置；角色授权映射由 DBA-002 负责。 |
| `un_module_action` | 动作配置 | 模块按钮、行操作、详情操作和导出入口动作配置。 |
| `un_module_publish_version` | 发布版本 | 模块发布版本快照，运行态只读该表的快照。 |
| `un_module_serial_sequence` | 自动编号 | 自动编号字段的事务内原子序号段。 |
| `un_module_record` | 运行记录 | 动态业务记录主表，承载状态、流程摘要、锁定和审计字段。 |
| `un_module_record_value` | 运行记录 | EAV 字段值表，使用 typed columns 和展示快照保存动态值。 |
| `un_module_record_index` | 运行记录 | 动态字段查询、排序、筛选的 typed index 表。 |
| `un_module_record_unique_index` | 运行记录 | 字段级唯一和组合唯一的 typed hash 索引表。 |
| `un_module_record_relation` | 运行记录 | 关联字段保存的记录间关系。 |
| `un_module_record_child_row` | 运行记录 | 子表字段的行数据和行顺序。 |
| `un_module_record_history` | 运行记录 | 记录每次变更的字段、状态、附件和展示快照。 |
| `un_module_export_template` | 导出 | 导出模板主表。 |
| `un_module_export_template_field` | 导出 | 导出模板字段列、顺序和脱敏配置。 |
| `un_module_export_job` | 导出 | 导出任务、筛选快照、权限快照、结果文件引用和重试状态。 |
| `un_module_export_job_log` | 导出 | 导出任务状态流转、领取、失败和重试日志。 |

## 三、状态与枚举

| 枚举字段 | 可选值 | 说明 |
| --- | --- | --- |
| `app_status` | `DRAFT`、`ENABLED`、`DISABLED`、`ARCHIVED` | 应用草稿、启用、停用、归档。 |
| `module_status` | `DRAFT`、`PUBLISHED`、`DISABLED`、`ARCHIVED` | 模块草稿、已发布、停用、归档。 |
| `field_status` | `DRAFT`、`ENABLED`、`DISABLED`、`DELETED` | 字段草稿、启用、停用、删除标记。 |
| `page_type` | `LIST`、`FORM`、`DETAIL` | 页面 schema 类型。 |
| `publish_status` | `PUBLISHED`、`DEPRECATED`、`ROLLBACK_RESERVED` | 已发布、已废弃、预留回滚。 |
| `record_status` | `DRAFT`、`SUBMITTED`、`IN_APPROVAL`、`APPROVED`、`REJECTED`、`WITHDRAWN`、`ARCHIVED`、`DELETED` | 业务记录状态。 |
| `field_type` | `TEXT`、`TEXTAREA`、`NUMBER`、`MONEY`、`DATE`、`DATETIME`、`SELECT`、`MULTI_SELECT`、`SWITCH`、`MEMBER`、`DEPT`、`ATTACHMENT`、`IMAGE`、`AUTO_NO`、`RELATION`、`SUB_TABLE`、`ADDRESS`、`TAG`、`JSON` | MVP 字段类型。 |
| `export_template_status` | `ENABLED`、`DISABLED`、`DELETED` | 导出模板状态。 |
| `export_job_status` | `QUEUED`、`PROCESSING`、`SUCCESS`、`FAILED`、`CANCELED` | 导出任务状态。 |
| `export_job_log_type` | `CREATE`、`CLAIM`、`PROGRESS`、`SUCCESS`、`FAIL`、`RETRY`、`CANCEL` | 导出任务日志类型。 |

## 四、字段说明

所有表统一包含 `created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` 和 `updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)`。ID 字段在 API 中序列化为字符串，DB 层建议使用 `BIGINT UNSIGNED` 或雪花 ID 数值类型，由 DBA-005/DBA-006 汇总时统一。

### 4.1 `un_module_app`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `app_id` | BIGINT UNSIGNED | 是 | 无 | 应用 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户；单租户系统使用默认租户。 |
| `name` | VARCHAR(128) | 是 | 无 | 应用名称。 |
| `code` | VARCHAR(64) | 是 | 无 | 应用编码，同系统同租户唯一。 |
| `icon` | VARCHAR(128) | 否 | NULL | 应用图标。 |
| `description` | VARCHAR(512) | 否 | NULL | 应用描述。 |
| `app_status` | VARCHAR(32) | 是 | `DRAFT` | 应用状态。 |
| `current_app_version_id` | BIGINT UNSIGNED | 否 | NULL | 当前应用版本。 |
| `module_count` | INT | 是 | 0 | 模块数量冗余，用于列表展示。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `version` | INT | 是 | 0 | 乐观锁版本。 |
| `delete_marker` | VARCHAR(64) | 是 | `0` | 软删除唯一复用标记；未删除固定为 `0`，删除后写入应用 ID 或删除批次。 |
| `created_by` | BIGINT UNSIGNED | 是 | 无 | 创建成员 ID。 |
| `updated_by` | BIGINT UNSIGNED | 否 | NULL | 更新成员 ID。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.2 `un_module_app_version`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `app_version_id` | BIGINT UNSIGNED | 是 | 无 | 应用版本 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `app_id` | BIGINT UNSIGNED | 是 | 无 | 应用 ID。 |
| `version_no` | INT | 是 | 无 | 应用版本号，按应用递增。 |
| `version_name` | VARCHAR(128) | 否 | NULL | 版本名称。 |
| `publish_status` | VARCHAR(32) | 是 | `PUBLISHED` | 版本状态。 |
| `snapshot_json` | JSON | 是 | 无 | 应用、模块、菜单和发布模块摘要快照。 |
| `publish_remark` | VARCHAR(512) | 否 | NULL | 发布说明。 |
| `published_by` | BIGINT UNSIGNED | 是 | 无 | 发布成员 ID。 |
| `published_at` | DATETIME(3) | 是 | 当前时间 | 发布时间。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.3 `un_module_model`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 模块 ID，对应 API `moduleId`。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `app_id` | BIGINT UNSIGNED | 是 | 无 | 所属应用。 |
| `name` | VARCHAR(128) | 是 | 无 | 模块名称。 |
| `code` | VARCHAR(64) | 是 | 无 | 模块编码，同应用下唯一。 |
| `description` | VARCHAR(512) | 否 | NULL | 模块描述。 |
| `module_status` | VARCHAR(32) | 是 | `DRAFT` | 模块状态。 |
| `current_publish_version_id` | BIGINT UNSIGNED | 否 | NULL | 当前运行态发布版本。 |
| `flow_binding_id` | BIGINT UNSIGNED | 否 | NULL | 流程绑定逻辑引用，实际流程表由 DBA-004 设计。 |
| `title_field_id` | BIGINT UNSIGNED | 否 | NULL | 记录标题字段。 |
| `record_no_field_id` | BIGINT UNSIGNED | 否 | NULL | 记录编号字段，可为空。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `version` | INT | 是 | 0 | 乐观锁版本。 |
| `delete_marker` | VARCHAR(64) | 是 | `0` | 软删除唯一复用标记。 |
| `created_by` | BIGINT UNSIGNED | 是 | 无 | 创建成员 ID。 |
| `updated_by` | BIGINT UNSIGNED | 否 | NULL | 更新成员 ID。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.4 `un_module_field`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `field_id` | BIGINT UNSIGNED | 是 | 无 | 字段 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 所属模块。 |
| `name` | VARCHAR(128) | 是 | 无 | 字段名称。 |
| `code` | VARCHAR(64) | 是 | 无 | 字段编码，同模块唯一。 |
| `field_type` | VARCHAR(32) | 是 | 无 | 字段类型。 |
| `required_flag` | TINYINT | 是 | 0 | 是否必填。 |
| `unique_flag` | TINYINT | 是 | 0 | 是否字段级唯一。 |
| `index_flag` | TINYINT | 是 | 0 | 是否生成查询索引。 |
| `default_value_json` | JSON | 否 | NULL | 默认值。 |
| `dict_type_id` | BIGINT UNSIGNED | 否 | NULL | 字典类型引用，由 DBA-002 字典表提供。 |
| `relation_config_json` | JSON | 否 | NULL | 关联模块、展示字段和数据范围配置。 |
| `sub_table_config_json` | JSON | 否 | NULL | 子表列定义。 |
| `serial_config_json` | JSON | 否 | NULL | 自动编号规则。 |
| `validation_json` | JSON | 否 | NULL | 长度、范围、格式等校验。 |
| `display_config_json` | JSON | 否 | NULL | 前端展示和格式化配置。 |
| `field_status` | VARCHAR(32) | 是 | `DRAFT` | 字段状态。 |
| `sort_order` | INT | 是 | 0 | 字段排序。 |
| `version` | INT | 是 | 0 | 乐观锁版本。 |
| `delete_marker` | VARCHAR(64) | 是 | `0` | 软删除唯一复用标记。 |
| `created_by` | BIGINT UNSIGNED | 是 | 无 | 创建成员 ID。 |
| `updated_by` | BIGINT UNSIGNED | 否 | NULL | 更新成员 ID。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.5 `un_module_field_option`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `option_id` | BIGINT UNSIGNED | 是 | 无 | 选项 ID。 |
| `field_id` | BIGINT UNSIGNED | 是 | 无 | 字段 ID。 |
| `code` | VARCHAR(64) | 是 | 无 | 选项编码。 |
| `label` | VARCHAR(128) | 是 | 无 | 展示文本。 |
| `value` | VARCHAR(128) | 是 | 无 | 选项值。 |
| `color` | VARCHAR(32) | 否 | NULL | 颜色标识。 |
| `enabled_flag` | TINYINT | 是 | 1 | 是否启用。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `delete_marker` | VARCHAR(64) | 是 | `0` | 软删除唯一复用标记。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.6 `un_module_unique_constraint`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `constraint_id` | BIGINT UNSIGNED | 是 | 无 | 组合唯一约束 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 所属模块。 |
| `constraint_code` | VARCHAR(64) | 是 | 无 | 约束编码。 |
| `constraint_name` | VARCHAR(128) | 是 | 无 | 约束名称。 |
| `field_ids_json` | JSON | 是 | 无 | 参与唯一的字段 ID 数组，顺序固定。 |
| `field_codes_json` | JSON | 是 | 无 | 参与唯一的字段编码数组，便于快照和错误返回。 |
| `enabled_flag` | TINYINT | 是 | 1 | 是否启用。 |
| `version` | INT | 是 | 0 | 乐观锁版本。 |
| `delete_marker` | VARCHAR(64) | 是 | `0` | 软删除唯一复用标记。 |
| `created_by` | BIGINT UNSIGNED | 是 | 无 | 创建成员 ID。 |
| `updated_by` | BIGINT UNSIGNED | 否 | NULL | 更新成员 ID。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.7 `un_module_page_schema`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `schema_id` | BIGINT UNSIGNED | 是 | 无 | 页面 schema ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 所属模块。 |
| `page_type` | VARCHAR(32) | 是 | 无 | `LIST`、`FORM`、`DETAIL`。 |
| `schema_code` | VARCHAR(64) | 是 | `default` | schema 编码。 |
| `schema_name` | VARCHAR(128) | 是 | 无 | schema 名称。 |
| `schema_json` | JSON | 是 | 无 | 列表列、筛选、排序、表单分区或详情区块配置。 |
| `draft_version` | INT | 是 | 1 | 草稿版本号。 |
| `schema_status` | VARCHAR(32) | 是 | `DRAFT` | 草稿状态。 |
| `version` | INT | 是 | 0 | 乐观锁版本。 |
| `delete_marker` | VARCHAR(64) | 是 | `0` | 软删除唯一复用标记。 |
| `created_by` | BIGINT UNSIGNED | 是 | 无 | 创建成员 ID。 |
| `updated_by` | BIGINT UNSIGNED | 否 | NULL | 更新成员 ID。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.8 `un_module_menu`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `menu_id` | BIGINT UNSIGNED | 是 | 无 | 菜单 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 否 | NULL | 租户；系统级菜单可为空。 |
| `parent_id` | BIGINT UNSIGNED | 否 | NULL | 父菜单。 |
| `app_id` | BIGINT UNSIGNED | 否 | NULL | 应用入口。 |
| `module_id` | BIGINT UNSIGNED | 否 | NULL | 模块入口。 |
| `code` | VARCHAR(64) | 是 | 无 | 菜单编码。 |
| `name` | VARCHAR(128) | 是 | 无 | 菜单名称。 |
| `menu_type` | VARCHAR(32) | 是 | `RUNTIME` | 菜单类型，如 `CONFIG`、`RUNTIME`。 |
| `route_path` | VARCHAR(256) | 否 | NULL | 前端路由。 |
| `icon` | VARCHAR(128) | 否 | NULL | 图标。 |
| `visible_flag` | TINYINT | 是 | 1 | 是否可见。 |
| `enabled_flag` | TINYINT | 是 | 1 | 是否启用。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `delete_marker` | VARCHAR(64) | 是 | `0` | 软删除唯一复用标记。 |
| `created_by` | BIGINT UNSIGNED | 是 | 无 | 创建成员 ID。 |
| `updated_by` | BIGINT UNSIGNED | 否 | NULL | 更新成员 ID。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.9 `un_module_action`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `action_id` | BIGINT UNSIGNED | 是 | 无 | 动作 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 否 | NULL | 所属租户。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 所属模块。 |
| `action_code` | VARCHAR(64) | 是 | 无 | 动作编码，如 `RECORD_CREATE`、`RECORD_EXPORT`。 |
| `action_name` | VARCHAR(128) | 是 | 无 | 动作名称。 |
| `action_type` | VARCHAR(32) | 是 | 无 | `BUTTON`、`ROW`、`DETAIL`、`EXPORT`、`FLOW`。 |
| `danger_flag` | TINYINT | 是 | 0 | 是否危险操作。 |
| `confirm_required` | TINYINT | 是 | 0 | 是否需要确认。 |
| `enabled_flag` | TINYINT | 是 | 1 | 是否启用。 |
| `config_json` | JSON | 否 | NULL | 前端按钮、状态规则和权限提示配置。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `delete_marker` | VARCHAR(64) | 是 | `0` | 软删除唯一复用标记。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.10 `un_module_publish_version`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `publish_version_id` | BIGINT UNSIGNED | 是 | 无 | 发布版本 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `app_id` | BIGINT UNSIGNED | 是 | 无 | 应用 ID。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 模块 ID。 |
| `version_no` | INT | 是 | 无 | 模块发布版本号。 |
| `publish_status` | VARCHAR(32) | 是 | `PUBLISHED` | 发布状态。 |
| `field_snapshot_json` | JSON | 是 | 无 | 字段定义、字段权限基础信息、选项和唯一配置快照。 |
| `page_snapshot_json` | JSON | 是 | 无 | 列表、表单、详情 schema 快照。 |
| `menu_action_snapshot_json` | JSON | 是 | 无 | 菜单和动作配置快照。 |
| `flow_binding_snapshot_json` | JSON | 否 | NULL | 流程绑定快照。 |
| `export_template_snapshot_json` | JSON | 否 | NULL | 可用导出模板摘要快照。 |
| `check_result_json` | JSON | 是 | 无 | 发布检查结果。 |
| `publish_remark` | VARCHAR(512) | 否 | NULL | 发布说明。 |
| `published_by` | BIGINT UNSIGNED | 是 | 无 | 发布成员 ID。 |
| `published_at` | DATETIME(3) | 是 | 当前时间 | 发布时间。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.11 `un_module_serial_sequence`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `sequence_id` | BIGINT UNSIGNED | 是 | 无 | 序号规则 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 所属模块。 |
| `field_id` | BIGINT UNSIGNED | 是 | 无 | 自动编号字段。 |
| `scope_key` | VARCHAR(128) | 是 | `GLOBAL` | 序号作用域，如年度、月份、租户、模块。 |
| `prefix_snapshot` | VARCHAR(128) | 否 | NULL | 当前前缀快照。 |
| `current_value` | BIGINT | 是 | 0 | 当前最大序号。 |
| `step_value` | INT | 是 | 1 | 步长。 |
| `version` | INT | 是 | 0 | 乐观锁版本，用于原子更新。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

自动编号必须通过事务内 `UPDATE current_value = current_value + step_value` 或行锁方式生成，不允许查询最大值加一。

### 4.12 `un_module_record`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `record_id` | BIGINT UNSIGNED | 是 | 无 | 记录 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `app_id` | BIGINT UNSIGNED | 是 | 无 | 所属应用。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 所属模块。 |
| `publish_version_id` | BIGINT UNSIGNED | 是 | 无 | 创建或最后保存时使用的发布版本。 |
| `record_no` | VARCHAR(128) | 否 | NULL | 业务编号或自动编号。 |
| `title` | VARCHAR(256) | 否 | NULL | 列表展示标题冗余。 |
| `record_status` | VARCHAR(32) | 是 | `DRAFT` | 业务记录状态。 |
| `flow_status` | VARCHAR(32) | 否 | NULL | 流程摘要状态。 |
| `flow_instance_id` | BIGINT UNSIGNED | 否 | NULL | 流程实例逻辑引用。 |
| `locked_flag` | TINYINT | 是 | 0 | 是否流程锁定。 |
| `record_version` | INT | 是 | 0 | 记录乐观锁版本。 |
| `active_unique_marker` | VARCHAR(64) | 是 | `ACTIVE` | 唯一索引用记录状态标记；删除后写入记录 ID。 |
| `created_by` | BIGINT UNSIGNED | 是 | 无 | 创建成员 ID。 |
| `updated_by` | BIGINT UNSIGNED | 否 | NULL | 更新成员 ID。 |
| `deleted_by` | BIGINT UNSIGNED | 否 | NULL | 删除成员 ID。 |
| `deleted_at` | DATETIME(3) | 否 | NULL | 删除时间。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.13 `un_module_record_value`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `value_id` | BIGINT UNSIGNED | 是 | 无 | 字段值 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 所属模块。 |
| `record_id` | BIGINT UNSIGNED | 是 | 无 | 记录 ID。 |
| `field_id` | BIGINT UNSIGNED | 是 | 无 | 字段 ID。 |
| `field_code` | VARCHAR(64) | 是 | 无 | 字段编码快照。 |
| `field_type` | VARCHAR(32) | 是 | 无 | 字段类型快照。 |
| `row_key` | VARCHAR(64) | 是 | `ROOT` | 主表字段为 `ROOT`，子表字段为子表行 ID。 |
| `value_text` | TEXT | 否 | NULL | 文本值或短展示值。 |
| `value_number` | DECIMAL(30,10) | 否 | NULL | 数值、金额。 |
| `value_datetime` | DATETIME(3) | 否 | NULL | 日期时间。 |
| `value_date` | DATE | 否 | NULL | 日期。 |
| `value_bool` | TINYINT | 否 | NULL | 开关值。 |
| `value_json` | JSON | 否 | NULL | 多选、附件、图片、关联、子表、地址、标签、JSON 原始值。 |
| `display_value_json` | JSON | 否 | NULL | 后端补齐的展示值。 |
| `value_snapshot_json` | JSON | 否 | NULL | 字典、成员、部门、文件名、关联标题等历史展示快照。 |
| `value_hash` | CHAR(64) | 否 | NULL | typed value hash，供唯一和变更比较。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.14 `un_module_record_index`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `index_id` | BIGINT UNSIGNED | 是 | 无 | 索引值 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 所属模块。 |
| `record_id` | BIGINT UNSIGNED | 是 | 无 | 记录 ID。 |
| `field_id` | BIGINT UNSIGNED | 是 | 无 | 字段 ID。 |
| `field_code` | VARCHAR(64) | 是 | 无 | 字段编码快照。 |
| `row_key` | VARCHAR(64) | 是 | `ROOT` | 行标识。 |
| `index_text` | VARCHAR(512) | 否 | NULL | 文本查询索引。 |
| `index_number` | DECIMAL(30,10) | 否 | NULL | 数值索引。 |
| `index_datetime` | DATETIME(3) | 否 | NULL | 日期时间索引。 |
| `index_date` | DATE | 否 | NULL | 日期索引。 |
| `index_bool` | TINYINT | 否 | NULL | 开关索引。 |
| `index_hash` | CHAR(64) | 否 | NULL | IN、多值、关联等 hash 索引。 |
| `record_status` | VARCHAR(32) | 是 | 无 | 记录状态快照，用于过滤删除记录。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.15 `un_module_record_unique_index`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `unique_index_id` | BIGINT UNSIGNED | 是 | 无 | 唯一索引记录 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 所属模块。 |
| `constraint_code` | VARCHAR(128) | 是 | 无 | 字段级唯一写 `FIELD:{fieldId}`，组合唯一写配置编码。 |
| `field_ids_json` | JSON | 是 | 无 | 参与唯一字段 ID。 |
| `field_codes_json` | JSON | 是 | 无 | 参与唯一字段编码。 |
| `combined_value_hash` | CHAR(64) | 是 | 无 | typed value 组合 hash。 |
| `display_values_json` | JSON | 否 | NULL | 冲突提示展示值。 |
| `record_id` | BIGINT UNSIGNED | 是 | 无 | 记录 ID。 |
| `active_unique_marker` | VARCHAR(64) | 是 | `ACTIVE` | 非删除记录为 `ACTIVE`，记录软删除后写入记录 ID。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.16 `un_module_record_relation`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `relation_id` | BIGINT UNSIGNED | 是 | 无 | 关联关系 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `source_module_id` | BIGINT UNSIGNED | 是 | 无 | 来源模块。 |
| `source_record_id` | BIGINT UNSIGNED | 是 | 无 | 来源记录。 |
| `field_id` | BIGINT UNSIGNED | 是 | 无 | 关联字段。 |
| `row_key` | VARCHAR(64) | 是 | `ROOT` | 主表或子表行标识。 |
| `target_module_id` | BIGINT UNSIGNED | 是 | 无 | 目标模块。 |
| `target_record_id` | BIGINT UNSIGNED | 是 | 无 | 目标记录。 |
| `relation_type` | VARCHAR(32) | 是 | `FIELD_RELATION` | 关系类型。 |
| `display_snapshot_json` | JSON | 否 | NULL | 目标记录标题等展示快照。 |
| `delete_marker` | VARCHAR(64) | 是 | `0` | 软删除唯一复用标记。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.17 `un_module_record_child_row`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `child_row_id` | BIGINT UNSIGNED | 是 | 无 | 子表行 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 所属模块。 |
| `record_id` | BIGINT UNSIGNED | 是 | 无 | 主记录 ID。 |
| `parent_field_id` | BIGINT UNSIGNED | 是 | 无 | 子表字段 ID。 |
| `row_key` | VARCHAR(64) | 是 | 无 | 行 key，写入 `un_module_record_value.row_key`。 |
| `row_order` | INT | 是 | 0 | 行顺序。 |
| `row_status` | VARCHAR(32) | 是 | `ACTIVE` | `ACTIVE`、`DELETED`。 |
| `delete_marker` | VARCHAR(64) | 是 | `0` | 软删除唯一复用标记。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.18 `un_module_record_history`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `history_id` | BIGINT UNSIGNED | 是 | 无 | 历史 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 所属模块。 |
| `record_id` | BIGINT UNSIGNED | 是 | 无 | 记录 ID。 |
| `record_version` | INT | 是 | 无 | 变更后的记录版本。 |
| `publish_version_id` | BIGINT UNSIGNED | 是 | 无 | 本次变更解释使用的发布版本。 |
| `operation_type` | VARCHAR(32) | 是 | 无 | `CREATE`、`UPDATE`、`DELETE`、`SUBMIT`、`FLOW_UPDATE`、`RESTORE`。 |
| `before_status` | VARCHAR(32) | 否 | NULL | 变更前状态。 |
| `after_status` | VARCHAR(32) | 是 | 无 | 变更后状态。 |
| `changed_fields_json` | JSON | 是 | 无 | 变更字段编码数组。 |
| `before_snapshot_json` | JSON | 否 | NULL | 变更前字段值和展示快照。 |
| `after_snapshot_json` | JSON | 是 | 无 | 变更后字段值、附件和展示快照。 |
| `request_id` | VARCHAR(64) | 是 | 无 | 请求追踪 ID。 |
| `operator_member_id` | BIGINT UNSIGNED | 是 | 无 | 操作成员 ID。 |
| `remark` | VARCHAR(512) | 否 | NULL | 保存备注或系统说明。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.19 `un_module_export_template`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `template_id` | BIGINT UNSIGNED | 是 | 无 | 导出模板 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 所属模块。 |
| `template_code` | VARCHAR(64) | 是 | 无 | 模板编码。 |
| `template_name` | VARCHAR(128) | 是 | 无 | 模板名称。 |
| `template_status` | VARCHAR(32) | 是 | `ENABLED` | 模板状态。 |
| `file_name_pattern` | VARCHAR(256) | 否 | NULL | 结果文件名规则。 |
| `export_format` | VARCHAR(32) | 是 | `XLSX` | 导出格式，MVP 默认 `XLSX`。 |
| `include_history_flag` | TINYINT | 是 | 0 | 是否导出历史，MVP 默认否。 |
| `config_json` | JSON | 否 | NULL | 表头、冻结列、样式等配置。 |
| `version` | INT | 是 | 0 | 乐观锁版本。 |
| `delete_marker` | VARCHAR(64) | 是 | `0` | 软删除唯一复用标记。 |
| `created_by` | BIGINT UNSIGNED | 是 | 无 | 创建成员 ID。 |
| `updated_by` | BIGINT UNSIGNED | 否 | NULL | 更新成员 ID。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.20 `un_module_export_template_field`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `template_field_id` | BIGINT UNSIGNED | 是 | 无 | 模板字段 ID。 |
| `template_id` | BIGINT UNSIGNED | 是 | 无 | 模板 ID。 |
| `field_id` | BIGINT UNSIGNED | 是 | 无 | 字段 ID。 |
| `field_code` | VARCHAR(64) | 是 | 无 | 字段编码快照。 |
| `header_name` | VARCHAR(128) | 是 | 无 | 导出表头。 |
| `column_order` | INT | 是 | 0 | 列顺序。 |
| `plain_required_flag` | TINYINT | 是 | 0 | 是否要求明文导出权限。 |
| `mask_strategy` | VARCHAR(64) | 否 | NULL | 脱敏策略编码。 |
| `format_json` | JSON | 否 | NULL | 日期、金额、字典展示格式。 |
| `enabled_flag` | TINYINT | 是 | 1 | 是否启用。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.21 `un_module_export_job`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `job_id` | BIGINT UNSIGNED | 是 | 无 | 导出任务 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `module_id` | BIGINT UNSIGNED | 是 | 无 | 导出模块。 |
| `template_id` | BIGINT UNSIGNED | 否 | NULL | 导出模板；允许使用默认模板时为空。 |
| `publish_version_id` | BIGINT UNSIGNED | 是 | 无 | 创建任务时的发布版本。 |
| `job_status` | VARCHAR(32) | 是 | `QUEUED` | 导出任务状态。 |
| `progress` | INT | 是 | 0 | 进度 0-100。 |
| `selected_record_ids_json` | JSON | 否 | NULL | 选中记录 ID 快照；优先于筛选条件。 |
| `filter_snapshot_json` | JSON | 是 | 无 | 筛选条件快照。 |
| `sorter_snapshot_json` | JSON | 否 | NULL | 排序快照。 |
| `field_snapshot_json` | JSON | 是 | 无 | 导出字段和发布字段定义快照。 |
| `permission_snapshot_json` | JSON | 是 | 无 | 字段可见、导出明文、操作权限、数据范围和脱敏权限快照。 |
| `data_scope_snapshot_json` | JSON | 是 | 无 | 数据范围命中规则快照。 |
| `file_name` | VARCHAR(256) | 否 | NULL | 结果文件名。 |
| `result_file_id` | BIGINT UNSIGNED | 否 | NULL | 导出结果文件逻辑引用，对应 `un_upload_` 文件主表。 |
| `failure_code` | VARCHAR(64) | 否 | NULL | 失败错误码。 |
| `failure_message` | VARCHAR(512) | 否 | NULL | 失败提示。 |
| `failure_snapshot_json` | JSON | 否 | NULL | `failureReason`，含 retryable、stackSummary、failedAt。 |
| `retryable_flag` | TINYINT | 是 | 0 | 是否可重试。 |
| `retry_count` | INT | 是 | 0 | 已重试次数。 |
| `max_retry_count` | INT | 是 | 3 | 最大重试次数。 |
| `claimed_by` | VARCHAR(128) | 否 | NULL | 后台 runner 标识。 |
| `claimed_at` | DATETIME(3) | 否 | NULL | 领取时间。 |
| `started_at` | DATETIME(3) | 否 | NULL | 开始处理时间。 |
| `finished_at` | DATETIME(3) | 否 | NULL | 完成时间。 |
| `request_id` | VARCHAR(64) | 是 | 无 | 创建任务请求 ID。 |
| `idempotency_key` | VARCHAR(128) | 是 | 无 | 幂等键。 |
| `request_hash` | CHAR(64) | 是 | 无 | 请求摘要。 |
| `created_by` | BIGINT UNSIGNED | 是 | 无 | 创建成员 ID。 |
| `version` | INT | 是 | 0 | 任务领取和状态流转乐观锁。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

### 4.22 `un_module_export_job_log`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `log_id` | BIGINT UNSIGNED | 是 | 无 | 日志 ID。 |
| `job_id` | BIGINT UNSIGNED | 是 | 无 | 导出任务 ID。 |
| `system_id` | BIGINT UNSIGNED | 是 | 无 | 所属系统。 |
| `tenant_id` | BIGINT UNSIGNED | 是 | 无 | 所属租户。 |
| `log_type` | VARCHAR(32) | 是 | 无 | 日志类型。 |
| `from_status` | VARCHAR(32) | 否 | NULL | 变更前状态。 |
| `to_status` | VARCHAR(32) | 是 | 无 | 变更后状态。 |
| `message` | VARCHAR(512) | 否 | NULL | 日志说明。 |
| `snapshot_json` | JSON | 否 | NULL | 领取、失败、重试等上下文快照。 |
| `request_id` | VARCHAR(64) | 是 | 无 | 请求或后台任务追踪 ID。 |
| `operator_id` | VARCHAR(128) | 否 | NULL | 操作成员 ID或 runner 标识。 |
| `created_at` | DATETIME(3) | 是 | 当前时间 | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | 当前时间 | 更新时间。 |

## 五、索引与约束

### 5.1 主键

每张表以本表 ID 作为主键：`app_id`、`app_version_id`、`module_id`、`field_id`、`option_id`、`constraint_id`、`schema_id`、`menu_id`、`action_id`、`publish_version_id`、`sequence_id`、`record_id`、`value_id`、`index_id`、`unique_index_id`、`relation_id`、`child_row_id`、`history_id`、`template_id`、`template_field_id`、`job_id`、`log_id`。

### 5.2 唯一约束

| 表名 | 唯一约束 | 说明 |
| --- | --- | --- |
| `un_module_app` | `uk_module_app_code(system_id, tenant_id, code, delete_marker)` | 应用软删除后可复用编码。 |
| `un_module_app_version` | `uk_module_app_version(app_id, version_no)` | 应用版本号唯一。 |
| `un_module_model` | `uk_module_model_code(system_id, tenant_id, app_id, code, delete_marker)` | 模块软删除后可复用编码。 |
| `un_module_field` | `uk_module_field_code(module_id, code, delete_marker)` | 字段软删除后可复用编码。 |
| `un_module_field_option` | `uk_module_field_option_code(field_id, code, delete_marker)`、`uk_module_field_option_value(field_id, value, delete_marker)` | 同字段选项编码和值唯一。 |
| `un_module_unique_constraint` | `uk_module_unique_constraint_code(module_id, constraint_code, delete_marker)` | 组合唯一配置编码唯一。 |
| `un_module_page_schema` | `uk_module_page_schema(module_id, page_type, schema_code, delete_marker)` | 默认列表/表单/详情 schema 唯一。 |
| `un_module_menu` | `uk_module_menu_code(system_id, parent_id, code, delete_marker)` | 同父菜单下编码唯一。 |
| `un_module_action` | `uk_module_action_code(module_id, action_code, delete_marker)` | 同模块动作编码唯一。 |
| `un_module_publish_version` | `uk_module_publish_version(module_id, version_no)` | 模块发布版本号唯一。 |
| `un_module_serial_sequence` | `uk_module_serial_scope(system_id, tenant_id, module_id, field_id, scope_key)` | 自动编号作用域唯一。 |
| `un_module_record` | `uk_module_record_no(system_id, tenant_id, module_id, record_no, active_unique_marker)` | 非空记录编号在非删除记录中唯一；空值规则由后端跳过。 |
| `un_module_record_value` | `uk_module_record_value(record_id, row_key, field_id)` | 同记录同子表行同字段只有一条值。 |
| `un_module_record_unique_index` | `uk_module_record_unique(system_id, tenant_id, module_id, constraint_code, combined_value_hash, active_unique_marker)` | 字段级唯一和组合唯一统一落点；删除记录不参与冲突。 |
| `un_module_record_relation` | `uk_module_relation(source_record_id, field_id, row_key, target_record_id, delete_marker)` | 同字段同目标记录不重复关联。 |
| `un_module_record_child_row` | `uk_module_child_row(record_id, parent_field_id, row_key, delete_marker)` | 子表行 key 唯一。 |
| `un_module_record_history` | `uk_module_record_history(record_id, record_version, operation_type)` | 同记录版本同操作只有一条主历史。 |
| `un_module_export_template` | `uk_module_export_template_code(system_id, tenant_id, module_id, template_code, delete_marker)` | 导出模板软删除后可复用编码。 |
| `un_module_export_template_field` | `uk_module_export_template_field(template_id, field_id)` | 模板字段不重复。 |
| `un_module_export_job` | `uk_module_export_idempotency(system_id, created_by, idempotency_key)` | 导出任务创建幂等。 |

### 5.3 普通索引

| 表名 | 普通索引 | 用途 |
| --- | --- | --- |
| `un_module_app` | `idx_module_app_system_status(system_id, tenant_id, app_status, sort_order)` | 应用列表。 |
| `un_module_model` | `idx_module_model_app_status(app_id, module_status, sort_order)` | 应用下模块列表。 |
| `un_module_field` | `idx_module_field_module_status(module_id, field_status, sort_order)` | 字段设计器和发布检查。 |
| `un_module_publish_version` | `idx_module_publish_current(module_id, publish_status, published_at)` | 查当前发布版本和历史版本。 |
| `un_module_record` | `idx_module_record_query(system_id, tenant_id, module_id, record_status, updated_at)` | 运行台列表。 |
| `un_module_record` | `idx_module_record_creator(module_id, created_by, created_at)` | 数据范围 `SELF` 和审计查询。 |
| `un_module_record_value` | `idx_module_record_value_field(module_id, field_id, updated_at)` | 字段值统计和历史排查。 |
| `un_module_record_index` | `idx_module_index_text(module_id, field_id, index_text)`、`idx_module_index_number(module_id, field_id, index_number)`、`idx_module_index_datetime(module_id, field_id, index_datetime)`、`idx_module_index_hash(module_id, field_id, index_hash)` | 动态筛选、排序、范围查询。 |
| `un_module_record_relation` | `idx_module_relation_target(target_module_id, target_record_id)` | 反查关联记录。 |
| `un_module_record_child_row` | `idx_module_child_record(record_id, parent_field_id, row_order)` | 子表展示。 |
| `un_module_record_history` | `idx_module_history_record(record_id, created_at)`、`idx_module_history_request(request_id)` | 历史列表和 requestId 追踪。 |
| `un_module_export_job` | `idx_module_export_job_status(job_status, created_at)`、`idx_module_export_job_creator(system_id, created_by, created_at)`、`idx_module_export_job_result(result_file_id)` | 导出任务领取、列表、结果文件追踪。 |
| `un_module_export_job_log` | `idx_module_export_job_log(job_id, created_at)`、`idx_module_export_log_request(request_id)` | 任务日志和 requestId 追踪。 |

### 5.4 逻辑外键

不建议在 MVP 直接使用跨模块物理外键，避免代码生成、迁移和后台任务处理被跨域锁放大；通过逻辑外键和索引保证可追踪：

| 来源字段 | 目标对象 | 说明 |
| --- | --- | --- |
| `system_id`、`tenant_id` | `un_plat_` 系统/租户 | 平台和租户表由 DBA-002 汇总设计。 |
| `created_by`、`updated_by`、`operator_member_id` | `un_module_member` | 系统成员表由 DBA-002 设计。 |
| `dict_type_id` | `un_module_dict` | 字典表由 DBA-002 设计。 |
| `flow_binding_id`、`flow_instance_id` | `un_flow_` | 流程表由 DBA-004 设计。 |
| `result_file_id` 和附件字段值中的 `fileId` | `un_upload_` 文件主表和引用表 | 文件表由 DBA-004 设计，本分片只保存逻辑引用和展示快照。 |

## 六、动态字段唯一性与软删除复用规则

1. 字段级唯一配置来源为 `un_module_field.unique_flag=1`，运行保存时写入 `un_module_record_unique_index.constraint_code = FIELD:{fieldId}`。
2. 组合唯一配置来源为 `un_module_unique_constraint`，运行保存时按 `field_ids_json` 顺序拼接 typed value hash 后写入 `combined_value_hash`。
3. `null`、空字符串、空数组默认跳过唯一校验；必填字段先按 `FIELD_REQUIRED_MISSING` 返回。
4. 支持唯一的字段类型为 `TEXT`、`NUMBER`、`MONEY`、`DATE`、`DATETIME`、`SELECT`、`SWITCH`、`MEMBER`、`DEPT`、`AUTO_NO`、单值 `RELATION`。
5. 不支持唯一的字段类型为 `TEXTAREA`、`MULTI_SELECT`、`ATTACHMENT`、`IMAGE`、`SUB_TABLE`、`ADDRESS`、`TAG`、`JSON`；发布检查发现配置错误时返回发布检查失败。
6. 非删除记录的 `active_unique_marker` 固定为 `ACTIVE`，软删除记录时 `un_module_record.active_unique_marker` 和相关 `un_module_record_unique_index.active_unique_marker` 更新为 `recordId` 字符串，释放唯一值。
7. 恢复已删除记录时，必须先把待恢复记录的唯一值与当前 `ACTIVE` 数据重新比对；冲突时返回 `FIELD_UNIQUE_CONFLICT`。

## 七、运行记录事务与历史快照

`RUN-004`、`RUN-006` 必须在一个本地事务中完成：

1. 读取 `un_module_model.current_publish_version_id` 和 `un_module_publish_version` 快照。
2. 校验模块状态、字段可写、字段类型、必填、唯一、数据范围和流程锁定。
3. 写 `un_module_record` 主记录和 `record_version`。
4. 写或覆盖 `un_module_record_value`。
5. 重建本记录相关 `un_module_record_index`、`un_module_record_unique_index`、`un_module_record_relation`、`un_module_record_child_row`。
6. 对附件/图片字段，只保存 `fileId` 数组、文件名等展示快照；实际引用绑定由 DBA-004 的 `un_upload_` 文件引用表在同一业务事务中完成。
7. 写 `un_module_record_history`，保存 before/after 字段值、状态、附件展示和 requestId。
8. 写审计日志由 DBA-004 的审计域承接；本分片保留 requestId 方便串联。

`RUN-008` 提交审批由流程域创建实例和任务；本分片只更新 `un_module_record.record_status`、`flow_status`、`flow_instance_id`、`locked_flag` 并写历史。流程创建失败时整体回滚。

## 八、导出任务边界

1. `EXP-001` 至 `EXP-003` 落点为 `un_module_export_template` 和 `un_module_export_template_field`。
2. `EXP-004` 创建导出任务落点为 `un_module_export_job`，必须保存 `selected_record_ids_json`、`filter_snapshot_json`、`sorter_snapshot_json`、`field_snapshot_json`、`permission_snapshot_json` 和 `data_scope_snapshot_json`。
3. `selectedRecordIds` 与 `filters` 同时存在时，两者都保存快照，实际导出优先使用 `selectedRecordIds`。
4. 后台 runner 领取任务必须基于 `job_status=QUEUED` 和 `version` 原子更新到 `PROCESSING`，并写 `un_module_export_job_log`，不暴露普通业务 API。
5. 导出成功后写 `result_file_id`，该 ID 逻辑引用 DBA-004 的 `un_upload_` 文件主表；下载时必须校验任务创建人、导出权限快照或审计只读权限。
6. 导出失败写 `failure_code`、`failure_message`、`failure_snapshot_json`、`retryable_flag`，`EXP-007` 仅允许 `FAILED` 且 `retryable_flag=1` 且未超过 `max_retry_count` 的任务重新排队。
7. `EXP-008` 仅允许 `QUEUED` 或可取消的 `PROCESSING` 任务取消；`SUCCESS`、`CANCELED` 不可重复取消。
8. MVP 不设计导入执行闭环表；`IMP-*` 仅为 API 占位，不进入本分片强依赖。

## 九、API 数据落点

| API 分组 | 数据落点 | 事务边界 |
| --- | --- | --- |
| `APP-001` 至 `APP-007` | `un_module_app`、`un_module_app_version` | 创建/更新应用只写配置态；发布摘要由模块发布或应用发布动作维护。 |
| `MOD-001` 至 `MOD-007` | `un_module_model`、`un_module_field`、`un_module_field_option`、`un_module_unique_constraint`、`un_module_publish_version` | 发布检查不写版本；发布成功写发布快照并更新模块当前发布版本。 |
| `FIELD-001` 至 `FIELD-005` | `un_module_field`、`un_module_field_option`、`un_module_unique_constraint` | 字段删除为软删除；有历史数据时返回 `FIELD_DELETE_HAS_DATA` 或标记删除保留历史。 |
| `UI-001` 至 `UI-009` | `un_module_page_schema`、`un_module_menu`、`un_module_action` | 保存草稿不影响运行态；发布后写入 `un_module_publish_version` 快照。 |
| `RUN-001` 至 `RUN-010` | `un_module_publish_version`、`un_module_record`、`un_module_record_value`、`un_module_record_index`、`un_module_record_unique_index`、`un_module_record_relation`、`un_module_record_child_row`、`un_module_record_history` | 记录保存、编辑、删除、提交审批必须本地事务一致。 |
| `FILE-*` 附件字段 | `un_module_record_value.value_json`、`value_snapshot_json` | 文件元数据和引用关系由 DBA-004 负责；模块域只保存业务字段值和历史展示快照。 |
| `EXP-001` 至 `EXP-008` | `un_module_export_template`、`un_module_export_template_field`、`un_module_export_job`、`un_module_export_job_log` | 创建任务事务内保存快照；后台生成文件失败只更新任务失败状态，不回滚已提交任务。 |
| `OPN-002` 至 `OPN-005` | 复用运行记录落点 | OpenAPI 先由 `un_openapi_` 验签和幂等，再进入内部运行记录事务；不得绕过字段、状态、流程和数据范围校验。 |

## 十、初始化数据与迁移说明

1. 建库 seed 中默认字段类型元数据由平台或 DBA-002 汇总设计，本分片不重复设计字段类型 seed 表。
2. 创建自定义系统事务内应创建默认应用 `default_app`，落 `un_module_app`，状态为 `DRAFT`，归属默认租户 `default`。
3. 演示应用、演示模块、演示字段、演示记录、导出任务和附件不进入生产 `init.sql`，只能作为 test 后续夹具。
4. 旧项目可参考 `un_module_app`、`un_module_app_version`、`un_module_model`、`un_module_field`、`un_module_record`、`un_module_record_data`、`un_module_record_history`、`un_module_relation`、`un_module_serial_seq`、`un_module_export_tpl`、`un_module_export_tpl_field`、`un_module_export_job` 的领域拆分。
5. 旧项目运行时 schema repair、typed column 补丁、EAV 字段修复和导出同步大操作均不直接沿用；新项目必须在初始化 SQL 中一次性定义完整表结构，后台导出用任务状态闭环。

## 十一、自检结果

| 自检项 | 结果 |
| --- | --- |
| 输出路径是否符合 DBA-003 | 通过，仅设计 `docs/db_design_parts/module-runtime-export.md`。 |
| 是否只使用 `un_module_` 前缀 | 通过，本分片新表均为 `un_module_`；跨域对象仅作逻辑引用说明。 |
| 是否误用旧 `un_app_` | 通过，业务应用使用 `un_module_app`，未使用 `un_app_`。 |
| 运行态是否只读发布版本 | 通过，运行记录和 schema 均通过 `un_module_publish_version` 快照解释。 |
| 是否暴露 EAV 给前端 API | 通过，API `values[]` 与物理 EAV 表转换由后端完成。 |
| 动态字段唯一性是否覆盖字段级、组合唯一、空值、软删除复用 | 通过，使用 `un_module_record_unique_index` 和 `active_unique_marker`。 |
| 历史记录是否覆盖字段、状态、附件和发布版本快照 | 通过，`un_module_record_history` 保存 before/after、publishVersion、requestId。 |
| 附件引用边界是否清楚 | 通过，模块域只保存 fileId 数组和展示快照，文件元数据和引用表交 DBA-004。 |
| 导出任务状态、筛选快照、权限快照和结果文件引用是否覆盖 | 通过，`un_module_export_job` 与 `un_module_export_job_log` 覆盖创建、领取、成功、失败、重试、取消和 `result_file_id`。 |
| 是否生成 SQL | 通过，未生成 SQL，未修改 `sql/`。 |
