# 数据库设计说明

## 一、表与功能映射

| 表名 | 功能模块 | 描述 |
|------|----------|------|
| platform_account | 平台中心 | 平台登录账号，承载账号、姓名、联系方式、状态和最后登录时间。 |
| tenant | 平台中心 | 租户隔离单位，承载租户名称、编码、状态和负责人。 |
| business_system | 平台中心 / 系统上下文 | 平台下业务系统，承载系统编码、名称、租户、拥有者和状态。 |
| department | 系统与权限 | 系统内部门树，用于成员归属和数据范围权限。 |
| system_member | 系统与权限 | 用户在系统内的身份，关联平台账号、部门和状态。 |
| role | 系统与权限 | 平台或系统内权限集合。 |
| member_role | 系统与权限 | 成员与角色的多对多关系。 |
| role_permission | 系统与权限 | 角色对菜单、页面、字段、动作和数据范围的授权配置。 |
| app | 应用配置台 | 系统内业务应用，承载应用编码、名称、状态、排序和当前发布版本。 |
| app_version | 应用配置台 | 应用配置发布版本，用于草稿、发布、回滚和运行时快照引用。 |
| module | 应用配置台 / 应用运行台 | 应用内业务对象，承载模块编码、名称、类型和状态。 |
| module_field | 应用配置台 | 模块字段元数据，承载字段编码、类型、必填、唯一、默认值和校验规则。 |
| field_option | 应用配置台 | 字段枚举或选项配置。 |
| page_config | 应用配置台 | 列表、表单、详情等页面配置和布局 JSON。 |
| runtime_menu | 应用配置台 / 应用运行台 | 运行台菜单入口，绑定应用、模块、页面和权限标识。 |
| data_dictionary | 应用配置台 | 系统内字典分类。 |
| dictionary_item | 应用配置台 / 应用运行台 | 字典明细项，供字段和表单引用。 |
| business_record | 应用运行台 | 模块运行数据主记录，保存隔离上下文、记录编号、状态、流程状态和配置版本快照。 |
| record_value | 应用运行台 | 业务记录字段值，按字段类型分列保存。 |
| record_unique_value | 应用运行台 | 唯一字段值索引表，用数据库唯一约束辅助并发唯一性校验。 |
| record_comment | 应用运行台 | 业务记录评论。 |
| file_object | 文件与导入导出 | 上传文件元数据，保存存储位置、文件名、大小、类型和状态。 |
| file_relation | 文件与导入导出 | 文件与业务记录、导入导出任务等对象的关联。 |
| import_export_task | 文件与导入导出 | 导入、导出异步任务及失败原因、结果文件。 |
| workflow_template | 流程工作台 | 流程定义入口，绑定模块并记录当前版本。 |
| workflow_version | 流程工作台 | 流程草稿或发布版本，保存节点、连线、条件和设置。 |
| workflow_instance | 流程工作台 | 业务记录发起后的流程实例，保存发起人、业务快照和当前状态。 |
| workflow_task | 流程工作台 | 审批待办和已办任务，保存处理人、候选人、意见和完成时间。 |
| openapi_client | OpenAPI | 外部调用应用主体，保存 clientId、名称、状态和限流规则。 |
| openapi_credential | OpenAPI | OpenAPI 凭证版本和密钥摘要，不保存明文密钥。 |
| openapi_scope | OpenAPI | 外部应用授权范围，按系统、租户、应用、模块和动作控制。 |
| openapi_ip_whitelist | OpenAPI | 外部应用 IP 白名单。 |
| openapi_idempotency | OpenAPI | OpenAPI 幂等键记录，防止重复提交。 |
| audit_log | 审计与运维 | 登录、操作、接口调用、流程动作、文件动作和异常审计。 |
| global_config | 平台中心 / 运维中心 | 全局配置项，仅保存非明文敏感值或占位状态。 |
| serial_sequence | 应用运行台 | 自动编号序号表，通过行锁或原子更新生成编号，避免最大值加一。 |

## 二、字段说明

### platform_account

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 平台账号主键。 |
| account | VARCHAR(64) | 是 | 无 | 登录账号。 |
| real_name | VARCHAR(64) | 是 | 无 | 姓名。 |
| mobile | VARCHAR(32) | 否 | NULL | 手机号。 |
| email | VARCHAR(128) | 否 | NULL | 邮箱。 |
| password_hash | VARCHAR(255) | 是 | 无 | 密码哈希。 |
| status | VARCHAR(20) | 是 | ENABLED | 账号状态：ENABLED、DISABLED。 |
| last_login_at | DATETIME | 否 | NULL | 最近登录时间。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### tenant

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 租户主键。 |
| tenant_name | VARCHAR(128) | 是 | 无 | 租户名称。 |
| tenant_code | VARCHAR(64) | 是 | 无 | 租户编码。 |
| owner_account_id | BIGINT | 否 | NULL | 负责人账号 ID。 |
| status | VARCHAR(20) | 是 | ENABLED | 租户状态：ENABLED、DISABLED。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### business_system

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 系统主键，即 systemId。 |
| tenant_id | BIGINT | 是 | 无 | 所属租户 ID。 |
| system_name | VARCHAR(128) | 是 | 无 | 系统名称。 |
| system_code | VARCHAR(64) | 是 | 无 | 系统编码。 |
| owner_account_id | BIGINT | 是 | 无 | 系统拥有者账号 ID。 |
| description | VARCHAR(500) | 否 | NULL | 系统描述。 |
| status | VARCHAR(20) | 是 | DRAFT | 系统状态：DRAFT、ENABLED、DISABLED。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### department

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 部门主键。 |
| system_id | BIGINT | 是 | 无 | 所属系统 ID。 |
| tenant_id | BIGINT | 是 | 无 | 所属租户 ID。 |
| parent_id | BIGINT | 否 | NULL | 上级部门 ID。 |
| dept_name | VARCHAR(128) | 是 | 无 | 部门名称。 |
| dept_code | VARCHAR(64) | 是 | 无 | 部门编码。 |
| sort_order | INT | 是 | 0 | 排序号。 |
| status | VARCHAR(20) | 是 | ENABLED | 部门状态：ENABLED、DISABLED。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### system_member

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 成员主键。 |
| system_id | BIGINT | 是 | 无 | 所属系统 ID。 |
| tenant_id | BIGINT | 是 | 无 | 所属租户 ID。 |
| account_id | BIGINT | 是 | 无 | 平台账号 ID。 |
| department_id | BIGINT | 否 | NULL | 所属部门 ID。 |
| status | VARCHAR(20) | 是 | ENABLED | 成员状态：ENABLED、DISABLED。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### role

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 角色主键。 |
| system_id | BIGINT | 是 | 0 | 所属系统 ID，0 表示平台级角色。 |
| tenant_id | BIGINT | 否 | NULL | 所属租户 ID，平台级角色可为空。 |
| role_name | VARCHAR(128) | 是 | 无 | 角色名称。 |
| role_type | VARCHAR(20) | 是 | SYSTEM | 角色类型：PLATFORM、SYSTEM、APP。 |
| status | VARCHAR(20) | 是 | ENABLED | 角色状态：ENABLED、DISABLED。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### member_role

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 成员角色关系主键。 |
| member_id | BIGINT | 是 | 无 | 成员 ID。 |
| role_id | BIGINT | 是 | 无 | 角色 ID。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### role_permission

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 权限主键。 |
| role_id | BIGINT | 是 | 无 | 角色 ID。 |
| system_id | BIGINT | 是 | 0 | 授权系统 ID，0 表示平台权限。 |
| tenant_id | BIGINT | 否 | NULL | 授权租户 ID。 |
| resource_type | VARCHAR(20) | 是 | 无 | 资源类型：MENU、PAGE、FIELD、ACTION、DATA_SCOPE。 |
| resource_id | BIGINT | 否 | NULL | 资源 ID，动作权限可为空。 |
| action_code | VARCHAR(64) | 否 | NULL | 动作权限标识。 |
| field_access | VARCHAR(20) | 否 | NULL | 字段权限：VISIBLE、EDITABLE、READONLY、HIDDEN。 |
| data_scope | VARCHAR(20) | 否 | NULL | 数据范围：SELF、DEPT、CHILD_DEPT、ASSIGNED_DEPT、ALL。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### app

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 应用主键，即 appId。 |
| system_id | BIGINT | 是 | 无 | 所属系统 ID。 |
| tenant_id | BIGINT | 是 | 无 | 所属租户 ID。 |
| app_name | VARCHAR(128) | 是 | 无 | 应用名称。 |
| app_code | VARCHAR(64) | 是 | 无 | 应用编码。 |
| status | VARCHAR(20) | 是 | DRAFT | 应用状态：DRAFT、ENABLED、DISABLED。 |
| sort_order | INT | 是 | 0 | 排序号。 |
| current_version_id | BIGINT | 否 | NULL | 当前发布版本 ID。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### app_version

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 应用配置版本主键。 |
| system_id | BIGINT | 是 | 无 | 所属系统 ID。 |
| tenant_id | BIGINT | 是 | 无 | 所属租户 ID。 |
| app_id | BIGINT | 是 | 无 | 应用 ID。 |
| version_no | INT | 是 | 无 | 版本号。 |
| status | VARCHAR(20) | 是 | DRAFT | 配置版本状态：DRAFT、PUBLISHED、DISABLED、ROLLED_BACK。 |
| version_note | VARCHAR(500) | 否 | NULL | 版本说明。 |
| published_at | DATETIME | 否 | NULL | 发布时间。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### module

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 模块主键，即 moduleId。 |
| system_id | BIGINT | 是 | 无 | 所属系统 ID。 |
| tenant_id | BIGINT | 是 | 无 | 所属租户 ID。 |
| app_id | BIGINT | 是 | 无 | 所属应用 ID。 |
| module_name | VARCHAR(128) | 是 | 无 | 模块名称。 |
| module_code | VARCHAR(64) | 是 | 无 | 模块编码。 |
| module_type | VARCHAR(20) | 是 | NORMAL | 模块类型：NORMAL、SUB_TABLE。 |
| status | VARCHAR(20) | 是 | ENABLED | 模块状态：ENABLED、DISABLED。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### module_field

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 字段主键，即 fieldId。 |
| system_id | BIGINT | 是 | 无 | 所属系统 ID。 |
| tenant_id | BIGINT | 是 | 无 | 所属租户 ID。 |
| module_id | BIGINT | 是 | 无 | 所属模块 ID。 |
| field_code | VARCHAR(64) | 是 | 无 | 字段编码。 |
| field_name | VARCHAR(128) | 是 | 无 | 字段名称。 |
| field_type | VARCHAR(32) | 是 | 无 | 字段类型：TEXT、LONG_TEXT、NUMBER、AMOUNT、DATE、DATETIME、BOOLEAN、SINGLE_SELECT、MULTI_SELECT、DICTIONARY、DEPARTMENT、MEMBER、ATTACHMENT、RELATION_RECORD、SUB_TABLE、AUTO_NUMBER、FORMULA、READONLY。 |
| required_flag | TINYINT | 是 | 0 | 是否必填：0-否，1-是。 |
| unique_flag | TINYINT | 是 | 0 | 是否唯一：0-否，1-是。 |
| default_value | VARCHAR(1000) | 否 | NULL | 默认值。 |
| enum_source | VARCHAR(64) | 否 | NULL | 枚举或字典来源。 |
| validate_rule | JSON | 否 | NULL | 校验规则 JSON。 |
| sort_order | INT | 是 | 0 | 排序号。 |
| status | VARCHAR(20) | 是 | ENABLED | 字段状态：ENABLED、DISABLED。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### field_option

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 字段选项主键。 |
| field_id | BIGINT | 是 | 无 | 字段 ID。 |
| option_label | VARCHAR(128) | 是 | 无 | 选项名称。 |
| option_value | VARCHAR(128) | 是 | 无 | 选项值。 |
| sort_order | INT | 是 | 0 | 排序号。 |
| status | VARCHAR(20) | 是 | ENABLED | 选项状态：ENABLED、DISABLED。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### page_config

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 页面主键，即 pageId。 |
| system_id | BIGINT | 是 | 无 | 所属系统 ID。 |
| tenant_id | BIGINT | 是 | 无 | 所属租户 ID。 |
| module_id | BIGINT | 是 | 无 | 所属模块 ID。 |
| page_type | VARCHAR(20) | 是 | 无 | 页面类型：LIST、FORM、DETAIL。 |
| app_version_id | BIGINT | 否 | NULL | 所属配置版本 ID。 |
| layout_json | JSON | 是 | 无 | 页面布局 JSON。 |
| block_json | JSON | 否 | NULL | 页面块配置 JSON。 |
| status | VARCHAR(20) | 是 | DRAFT | 页面状态：DRAFT、PUBLISHED、DISABLED、ROLLED_BACK。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### runtime_menu

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 菜单主键，即 menuId。 |
| system_id | BIGINT | 是 | 无 | 所属系统 ID。 |
| tenant_id | BIGINT | 是 | 无 | 所属租户 ID。 |
| parent_id | BIGINT | 否 | NULL | 父级菜单 ID。 |
| app_id | BIGINT | 否 | NULL | 绑定应用 ID。 |
| module_id | BIGINT | 否 | NULL | 绑定模块 ID。 |
| page_id | BIGINT | 否 | NULL | 绑定页面 ID。 |
| menu_name | VARCHAR(128) | 是 | 无 | 菜单名称。 |
| menu_code | VARCHAR(64) | 是 | 无 | 菜单编码。 |
| permission_code | VARCHAR(128) | 是 | 无 | 权限标识。 |
| sort_order | INT | 是 | 0 | 排序号。 |
| status | VARCHAR(20) | 是 | ENABLED | 菜单状态：ENABLED、DISABLED。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### data_dictionary / dictionary_item

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 主键。 |
| system_id | BIGINT | 是 | 无 | 所属系统 ID。 |
| tenant_id | BIGINT | 是 | 无 | 所属租户 ID。 |
| dict_code / item_value | VARCHAR(64) | 是 | 无 | 字典编码或字典项值。 |
| dict_name / item_label | VARCHAR(128) | 是 | 无 | 字典名称或字典项名称。 |
| dict_id | BIGINT | dictionary_item 必填 | 无 | 所属字典 ID。 |
| sort_order | INT | dictionary_item 必填 | 0 | 字典项排序。 |
| status | VARCHAR(20) | 是 | ENABLED | 状态：ENABLED、DISABLED。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### business_record / record_value / record_unique_value

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 主键。 |
| system_id | BIGINT | 是 | 无 | 所属系统 ID。 |
| tenant_id | BIGINT | 是 | 无 | 所属租户 ID。 |
| app_id | BIGINT | business_record 必填 | 无 | 所属应用 ID。 |
| module_id | BIGINT | 是 | 无 | 所属模块 ID。 |
| record_id | BIGINT | record_value、record_unique_value 必填 | 无 | 业务记录 ID。 |
| field_id | BIGINT | record_value、record_unique_value 必填 | 无 | 字段 ID。 |
| record_no | VARCHAR(64) | business_record 必填 | 无 | 记录编号。 |
| record_status | VARCHAR(20) | business_record 必填 | DRAFT | 记录状态：DRAFT、SUBMITTED、ARCHIVED、DELETED。 |
| process_status | VARCHAR(20) | business_record 必填 | NONE | 流程状态：NONE、RUNNING、APPROVED、REJECTED、WITHDRAWN、TERMINATED。 |
| app_version_id | BIGINT | business_record 否 | NULL | 运行时配置版本 ID。 |
| config_snapshot | JSON | business_record 否 | NULL | 运行时配置快照。 |
| string_value | VARCHAR(2000) | record_value 否 | NULL | 字符串、枚举、关联等值。 |
| number_value | DECIMAL(30,8) | record_value 否 | NULL | 数字或金额值。 |
| datetime_value | DATETIME | record_value 否 | NULL | 日期时间值。 |
| boolean_value | TINYINT | record_value 否 | NULL | 布尔值：0-否，1-是。 |
| json_value | JSON | record_value 否 | NULL | 多选、附件、子表、公式等复杂值。 |
| value_hash | VARCHAR(64) | record_unique_value 必填 | 无 | 唯一字段值哈希。 |
| is_deleted | TINYINT | 是 | 0 | 是否删除：0-否，1-是。 |
| created_by | BIGINT | business_record 必填 | 无 | 创建人账号 ID。 |
| updated_by | BIGINT | business_record 否 | NULL | 更新人账号 ID。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### workflow_template / workflow_version / workflow_instance / workflow_task

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 主键。 |
| system_id | BIGINT | 是 | 无 | 所属系统 ID。 |
| tenant_id | BIGINT | 是 | 无 | 所属租户 ID。 |
| module_id | BIGINT | 是 | 无 | 绑定模块 ID。 |
| template_id | BIGINT | workflow_version、workflow_instance 必填 | 无 | 流程模板 ID。 |
| version_id | BIGINT | workflow_instance 必填 | 无 | 流程版本 ID。 |
| record_id | BIGINT | workflow_instance 必填 | 无 | 业务记录 ID。 |
| instance_id | BIGINT | workflow_task 必填 | 无 | 流程实例 ID。 |
| template_name | VARCHAR(128) | workflow_template 必填 | 无 | 流程模板名称。 |
| version_no | INT | workflow_version 必填 | 无 | 流程版本号。 |
| node_json | JSON | workflow_version 必填 | 无 | 节点配置 JSON。 |
| edge_json | JSON | workflow_version 必填 | 无 | 连线配置 JSON。 |
| condition_json | JSON | workflow_version 否 | NULL | 条件配置 JSON。 |
| setting_json | JSON | workflow_version 否 | NULL | 流程设置 JSON。 |
| business_snapshot | JSON | workflow_instance 否 | NULL | 发起时业务快照。 |
| task_status | VARCHAR(20) | workflow_task 必填 | PENDING | 任务状态：PENDING、APPROVED、REJECTED、TRANSFERRED、WITHDRAWN、TERMINATED、SKIPPED。 |
| assignee_id | BIGINT | workflow_task 否 | NULL | 实际处理人账号 ID。 |
| candidate_json | JSON | workflow_task 否 | NULL | 候选人配置 JSON。 |
| comment | VARCHAR(1000) | workflow_task 否 | NULL | 处理意见。 |
| completed_at | DATETIME | workflow_task 否 | NULL | 完成时间。 |
| status | VARCHAR(20) | workflow_template、workflow_version、workflow_instance 必填 | DRAFT/RUNNING | 模板状态：DRAFT、PUBLISHED、DISABLED；版本状态：DRAFT、PUBLISHED、DISABLED、ROLLED_BACK；实例状态：RUNNING、APPROVED、REJECTED、WITHDRAWN、TERMINATED。 |
| published_at | DATETIME | workflow_version 否 | NULL | 发布时间。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### file_object / file_relation / import_export_task

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 主键。 |
| system_id | BIGINT | 是 | 无 | 所属系统 ID。 |
| tenant_id | BIGINT | 是 | 无 | 所属租户 ID。 |
| file_id | BIGINT | file_relation、import_export_task 结果文件 否 | NULL | 文件 ID。 |
| storage_path | VARCHAR(500) | file_object 必填 | 无 | 存储位置。 |
| file_name | VARCHAR(255) | file_object 必填 | 无 | 文件名。 |
| file_size | BIGINT | file_object 必填 | 无 | 文件大小，单位字节。 |
| content_type | VARCHAR(128) | file_object 否 | NULL | 文件 MIME 类型。 |
| status | VARCHAR(20) | file_object 必填 | TEMP | 附件状态：TEMP、LINKED、DELETED。 |
| relation_type | VARCHAR(20) | file_relation 必填 | 无 | 关联对象类型：RECORD、TASK、IMPORT_EXPORT。 |
| relation_id | BIGINT | file_relation 必填 | 无 | 关联对象 ID。 |
| task_type | VARCHAR(20) | import_export_task 必填 | 无 | 任务类型：IMPORT、EXPORT。 |
| template_id | BIGINT | import_export_task 否 | NULL | 导入导出模板 ID。 |
| task_status | VARCHAR(20) | import_export_task 必填 | PENDING | 任务状态：PENDING、RUNNING、SUCCESS、FAILED、CANCELED。 |
| failure_reason | VARCHAR(1000) | import_export_task 否 | NULL | 失败原因。 |
| result_file_id | BIGINT | import_export_task 否 | NULL | 结果文件 ID。 |
| created_by | BIGINT | import_export_task 必填 | 无 | 创建人账号 ID。 |
| completed_at | DATETIME | import_export_task 否 | NULL | 完成时间。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### openapi_client / openapi_credential / openapi_scope / openapi_ip_whitelist / openapi_idempotency

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 主键。 |
| system_id | BIGINT | 是 | 无 | 授权系统 ID。 |
| tenant_id | BIGINT | 是 | 无 | 授权租户 ID。 |
| client_id | VARCHAR(64) / BIGINT | 是 | 无 | 外部应用标识或外部应用主键。 |
| client_name | VARCHAR(128) | openapi_client 必填 | 无 | 外部应用名称。 |
| rate_limit_rule | JSON | openapi_client 否 | NULL | 限流规则。 |
| key_version | INT | openapi_credential 必填 | 无 | 密钥版本。 |
| secret_digest | VARCHAR(255) | openapi_credential 必填 | 无 | 加密密钥摘要，不保存明文。 |
| expires_at | DATETIME | openapi_credential 否 | NULL | 过期时间。 |
| scope_type | VARCHAR(20) | openapi_scope 必填 | 无 | 授权范围类型：SYSTEM、TENANT、APP、MODULE、ACTION、FIELD。 |
| scope_value | VARCHAR(128) | openapi_scope 必填 | 无 | 授权范围值。 |
| ip_value | VARCHAR(64) | openapi_ip_whitelist 必填 | 无 | 白名单 IP 或网段。 |
| idempotency_key | VARCHAR(128) | openapi_idempotency 必填 | 无 | 幂等键。 |
| request_hash | VARCHAR(64) | openapi_idempotency 必填 | 无 | 请求摘要哈希。 |
| response_snapshot | JSON | openapi_idempotency 否 | NULL | 首次响应快照。 |
| status | VARCHAR(20) | 是 | ENABLED | 应用状态：ENABLED、DISABLED；凭证状态：ENABLED、DISABLED、EXPIRED、ROTATED；幂等状态：PROCESSING、SUCCESS、FAILED。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

### audit_log / global_config / serial_sequence

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 自增 | 主键。 |
| system_id | BIGINT | 是 | 0 | 所属系统 ID，0 表示平台级。 |
| tenant_id | BIGINT | 否 | NULL | 所属租户 ID。 |
| actor_type | VARCHAR(20) | audit_log 必填 | 无 | 操作主体类型：ACCOUNT、OPENAPI、SYSTEM。 |
| actor_id | VARCHAR(64) | audit_log 否 | NULL | 操作主体 ID。 |
| action_type | VARCHAR(64) | audit_log 必填 | 无 | 操作类型。 |
| target_type | VARCHAR(64) | audit_log 否 | NULL | 目标对象类型。 |
| target_id | VARCHAR(64) | audit_log 否 | NULL | 目标对象 ID。 |
| result | VARCHAR(20) | audit_log 必填 | SUCCESS | 操作结果：SUCCESS、FAILED。 |
| trace_id | VARCHAR(128) | audit_log 否 | NULL | 请求追踪标识。 |
| detail_json | JSON | audit_log 否 | NULL | 日志详情。 |
| config_key | VARCHAR(128) | global_config 必填 | 无 | 配置键。 |
| config_value | VARCHAR(1000) | global_config 否 | NULL | 配置值，不保存明文敏感值。 |
| secret_placeholder_flag | TINYINT | global_config 必填 | 0 | 是否仍为敏感占位：0-否，1-是。 |
| sequence_key | VARCHAR(128) | serial_sequence 必填 | 无 | 序号规则键。 |
| prefix_rule | VARCHAR(128) | serial_sequence 否 | NULL | 编号前缀规则。 |
| next_value | BIGINT | serial_sequence 必填 | 1 | 下一个序号值。 |
| step_value | INT | serial_sequence 必填 | 1 | 序号步长。 |
| status | VARCHAR(20) | global_config、serial_sequence 必填 | ENABLED | 状态：ENABLED、DISABLED。 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间。 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间。 |

## 三、表关系

- 平台账号与租户、系统、成员、业务记录、流程任务存在一对多逻辑关系：账号可作为租户负责人、系统拥有者、系统成员、记录创建人或任务处理人。
- 租户与系统是一对多关系；系统内应用、部门、成员、角色、字典、业务记录、流程、文件和 OpenAPI 数据都带 tenantId 做隔离。
- 系统与应用是一对多关系；应用与模块是一对多关系；模块与字段、页面、菜单、业务记录、流程模板是一对多关系。
- 角色与成员是多对多关系，通过 member_role 关联。
- 角色与权限是一对多关系，role_permission 使用 resource_type + resource_id 表达菜单、页面、字段、动作和数据范围授权。
- 应用与配置版本是一对多关系，app.current_version_id 指向当前发布版本；运行记录通过 app_version_id 和 config_snapshot 保存创建时配置。
- 业务记录与记录值是一对多关系；业务记录与唯一字段值是一对多关系；业务记录与评论、附件关联、流程实例是一对多关系。
- 文件与业务记录、导入导出任务等对象是多对多逻辑关系，通过 file_relation 关联。
- 流程模板与流程版本是一对多关系；流程版本与流程实例是一对多关系；流程实例与流程任务是一对多关系。
- OpenAPI 应用与凭证、授权范围、IP 白名单、幂等记录是一对多关系。

## 四、索引与约束

- 主键：所有表均使用 BIGINT 自增主键。
- 唯一约束：
  - platform_account.account 唯一。
  - tenant.tenant_code 唯一。
  - business_system 在 tenant_id + system_code 下唯一。
  - app 在 system_id + tenant_id + app_code 下唯一。
  - module 在 system_id + tenant_id + app_id + module_code 下唯一。
  - module_field 在 system_id + tenant_id + module_id + field_code 下唯一。
  - runtime_menu 在 system_id + tenant_id + menu_code 下唯一，permission_code 在同系统租户内唯一。
  - role 在 system_id + tenant_id + role_name 下唯一。
  - record_unique_value 在 system_id + tenant_id + module_id + field_id + value_hash + is_deleted 下唯一，用于唯一字段并发校验。
  - openapi_client.client_id 唯一；openapi_credential 在 client_pk + key_version 下唯一。
  - openapi_idempotency 在 client_pk + idempotency_key 下唯一。
  - serial_sequence 在 system_id + tenant_id + module_id + sequence_key 下唯一。
- 普通索引：
  - 所有 system_id、tenant_id、app_id、module_id 高频隔离字段建立组合索引。
  - 业务记录按 module_id + record_status + updated_at 建索引，支撑列表分页和状态筛选。
  - 流程任务按 assignee_id + task_status + updated_at 建索引，支撑待办列表。
  - 审计日志按 system_id + tenant_id + action_type + created_at 和 trace_id 建索引。
  - OpenAPI 日志相关数据通过 audit_log.trace_id、openapi_idempotency 幂等键索引定位。
- 逻辑外键或外键约束：
  - 初始化 SQL 不启用物理外键，使用逻辑外键和索引约束，避免配置发布、历史快照、跨模块 EAV 数据在迁移和回滚时被级联误删。
  - 业务层必须校验 system_id、tenant_id、app_id、module_id 的归属一致性。

## 五、初始化数据

- 必须初始化一个平台管理员账号：account=admin，real_name=平台管理员，status=ENABLED。密码字段写入占位哈希，实际部署时必须重置。
- 必须初始化一个默认租户：tenant_code=default，tenant_name=默认租户，status=ENABLED。
- 必须初始化一个默认系统：system_code=default_system，system_name=默认业务系统，status=ENABLED。
- 必须初始化平台管理员角色：role_type=PLATFORM，role_name=平台管理员。
- 必须初始化基础全局配置：
  - OPENAPI_SECRET_PLACEHOLDER_CHECK：用于运维中心识别 OpenAPI 密钥是否仍为占位。
  - STORAGE_HEALTH_CHECK：用于运维中心展示文件存储健康检查配置状态。
  - SCRIPT_VERSION_CHECK：用于运维中心展示脚本版本检查状态。
- 其余系统、应用、模块、字段、页面、菜单、流程、OpenAPI 凭证不初始化业务默认数据，避免创建隐式业务配置。

## 六、设计说明

- 拆表原因：平台、租户、系统、应用、模块、字段、页面、运行记录、流程、文件和 OpenAPI 的生命周期不同，拆表后可以分别支持配置发布、运行查询、权限校验和审计追踪，避免把配置态和使用态混在同一张表。
- 动态业务数据采用 business_record + record_value 的 EAV 结构，是为了支持 PRD 要求的可配置字段、子表、附件、关联记录和历史配置快照。主记录保存 systemId、tenantId、appId、moduleId 和状态，字段值表保存动态字段值。
- 唯一字段没有直接在 record_value 上建立唯一索引，因为只有标记 unique_flag 的字段需要唯一约束。单独使用 record_unique_value 存储唯一字段值哈希，可以让数据库唯一索引参与并发控制，同时不限制普通字段重复值。
- 自动编号使用 serial_sequence 独立序号表，后端应在事务内对对应序号行执行原子更新或行锁读取，避免“查询最大值 + 1”在并发下重复。
- 索引设计围绕 system_id、tenant_id、app_id、module_id 隔离字段展开，符合 PRD 中所有业务接口必须按上下文隔离的要求；列表、待办、审计、OpenAPI 幂等等高频查询使用组合索引。
- 冗余字段：多张表冗余保存 system_id、tenant_id、app_id、module_id，是为了减少跨表查询时的归属校验成本，并让后端能在每个入口快速做越权判断；business_record 冗余 config_snapshot，是为了历史详情和流程审批不受后续配置变更影响。
- 历史数据与迁移注意事项：字段删除、停用、页面回滚、流程版本回滚均不得物理删除历史运行数据；迁移旧数据时应先导入平台账号、租户、系统、应用、模块和字段元数据，再导入业务记录、记录值、附件和流程实例，并补齐 app_version_id 或 config_snapshot。历史唯一字段需先清洗重复值，再写入 record_unique_value。
