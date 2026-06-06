# 数据库设计文档

## 一、表与功能映射

| 表名 | Maven/业务模块 | 功能模块 | 描述 |
|------|----------------|----------|------|
| un_platt_system | examine-plat / 平台中心 | 系统管理 | 平台下可接入的业务系统上下文 |
| un_platt_tenant | examine-plat / 平台中心 | 租户管理 | 企业租户基础信息与启停状态 |
| un_platt_account | examine-plat / 平台中心 | 账号管理 | 平台账号、登录凭证摘要与基础身份信息 |
| un_platt_account_tenant | examine-plat / 平台中心 | 账号租户关系 | 账号可访问租户、系统与是否默认上下文 |
| un_platt_department | examine-plat / 系统管理中心 | 部门管理 | 租户内部门树 |
| un_platt_role | examine-plat / 系统管理中心 | 角色管理 | 租户、系统、应用维度角色 |
| un_platt_permission | examine-plat / 系统管理中心 | 权限管理 | 菜单、按钮、接口、字段、数据范围等权限点 |
| un_platt_role_permission | examine-plat / 系统管理中心 | 角色授权 | 角色与权限点多对多关系 |
| un_platt_account_role | examine-plat / 系统管理中心 | 用户授权 | 账号与角色多对多关系 |
| un_platt_dict | examine-plat / 系统管理中心 | 字典管理 | 租户或系统级字典分类 |
| un_platt_dict_item | examine-plat / 系统管理中心 | 字典管理 | 字典枚举项 |
| un_app_application | examine-app / 应用配置中心 | 应用管理 | 可配置业务应用定义 |
| un_app_version | examine-app / 应用配置中心 | 应用版本 | 应用发布版本与配置快照 |
| un_module_model | examine-module / 应用配置中心 | 模块模型 | 应用下动态业务模块模型 |
| un_module_field | examine-module / 应用配置中心 | 字段配置 | 模块字段元数据、校验、权限建议 |
| un_module_field_option | examine-module / 应用配置中心 | 字段选项 | 单选、多选、枚举字段可选值 |
| un_module_page | examine-module / 应用配置中心 | 页面配置 | 列表、表单、详情、仪表盘布局 |
| un_module_menu | examine-module / 应用配置中心 | 菜单配置 | 应用与模块菜单树 |
| un_module_record | examine-module / 应用运行台 | 业务记录 | 动态模块运行态记录主表 |
| un_module_record_value | examine-module / 应用运行台 | 业务记录值 | EAV typed value 字段值表 |
| un_module_data_scope | examine-module / 权限规则 | 数据权限 | 模块级数据范围规则 |
| un_module_export_job | examine-module / 上传与导入导出 | 导出任务 | 业务数据导出异步任务 |
| un_flow_template | examine-flow / 流程工作台 | 流程模板 | 模块关联流程模板主信息 |
| un_flow_template_version | examine-flow / 流程工作台 | 流程版本 | 已发布或草稿流程图快照 |
| un_flow_instance | examine-flow / 流程工作台 | 流程实例 | 业务记录发起后的流程运行实例 |
| un_flow_task | examine-flow / 流程工作台 | 待办任务 | 审批任务、候选人、处理状态 |
| un_flow_approval_log | examine-flow / 流程工作台 | 审批日志 | 流程动作、意见和流转记录 |
| un_upload_storage_config | examine-upload / 上传中心 | 存储配置 | 本地、对象存储等文件存储策略 |
| un_upload_file | examine-upload / 上传中心 | 文件管理 | 文件元数据、存储位置和上传状态 |
| un_upload_attachment | examine-upload / 上传中心 | 附件引用 | 文件与业务对象、流程对象的引用关系 |
| un_upload_import_export_job | examine-upload / 上传中心 | 导入导出任务 | 导入、导出统一任务状态与结果文件 |
| un_openapi_client | examine-app / OpenAPI 中心 | 开放接口客户端 | 接入方客户端基础信息与限流策略 |
| un_openapi_credential | examine-app / OpenAPI 中心 | 开放接口凭证 | 客户端密钥版本与签名算法 |
| un_openapi_scope | examine-app / OpenAPI 中心 | 授权范围 | 客户端可访问应用、模块与动作范围 |
| un_openapi_ip_whitelist | examine-app / OpenAPI 中心 | IP 白名单 | 客户端来源 IP 限制 |
| un_openapi_idempotent | examine-app / OpenAPI 中心 | 幂等控制 | 开放接口幂等键与响应摘要 |
| un_openapi_access_log | examine-app / OpenAPI 中心 | 调用日志 | OpenAPI 调用结果、耗时与失败摘要 |
| un_sys_config | examine-core/examine-web / 运维中心 | 系统配置 | 平台运行配置项 |
| un_sys_login_log | examine-core/examine-web / 运维中心 | 登录日志 | 登录成功、失败、锁定等日志 |
| un_audit_operation_log | examine-core/examine-web / 审计中心 | 审计日志 | 管理端、运行台、OpenAPI 操作审计 |

## 二、模块表前缀与命名规则

- 所有表名统一以 `un_` 开头，后接模块前缀和业务名，格式为 `un_{module_prefix}_{business_name}`。
- 禁止生成无模块前缀的平铺表名；每张表必须从表名前缀识别 Maven/业务模块归属。
- 平台、租户、账号、角色、权限、系统上下文使用 `un_platt_`，对应 `examine-plat`。这是对旧项目 `un_plat_` 的显式更正，避免与本轮用户指定的平台前缀不一致。
- 动态模块、模型、字段、页面、菜单、记录、数据权限使用 `un_module_`，对应 `examine-module`。
- 流程模板、版本、实例、任务、审批日志使用 `un_flow_`，对应 `examine-flow`。
- 文件、附件、导入导出任务使用 `un_upload_`，对应 `examine-upload`。
- 应用和版本使用 `un_app_`，对应 `examine-app` 的应用配置能力。
- 开放接口客户端、凭证、scope、白名单、幂等和调用日志使用 `un_openapi_`。实现上仍落在 `examine-app`，但表名前缀独立体现 OpenAPI 边界，避免继续混入应用版本概念。
- 系统运行配置和登录日志使用 `un_sys_`，审计日志使用 `un_audit_`，由 `examine-core` 提供基础能力，`examine-web` 统一装配。
- 跨模块公共字段包括 `tenant_id`、`system_id`、`app_id`、`module_id`、`created_at`、`updated_at` 等，仅作为上下文和审计冗余，不单独抽公共业务表。

## 三、字段说明

### un_platt_system

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| system_code | VARCHAR(64) | 是 | 无 | 系统编码，全局唯一 |
| system_name | VARCHAR(128) | 是 | 无 | 系统名称 |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED |
| description | VARCHAR(500) | 否 | NULL | 系统说明 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_platt_tenant

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_code | VARCHAR(64) | 是 | 无 | 租户编码，全局唯一 |
| tenant_name | VARCHAR(128) | 是 | 无 | 租户名称 |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED、EXPIRED |
| admin_account_id | BIGINT | 否 | NULL | 默认管理员账号 ID |
| expire_at | DATETIME | 否 | NULL | 有效期截止时间 |
| config_json | JSON | 否 | NULL | 租户扩展配置 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_platt_account

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| username | VARCHAR(64) | 是 | 无 | 登录账号，全局唯一 |
| display_name | VARCHAR(128) | 是 | 无 | 显示名称 |
| mobile | VARCHAR(32) | 否 | NULL | 手机号 |
| email | VARCHAR(128) | 否 | NULL | 邮箱 |
| password_hash | VARCHAR(255) | 是 | 无 | 密码哈希 |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED、LOCKED |
| last_login_at | DATETIME | 否 | NULL | 最近登录时间 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_platt_account_tenant

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| account_id | BIGINT | 是 | 无 | 账号 ID |
| tenant_id | BIGINT | 是 | 无 | 租户 ID |
| system_id | BIGINT | 是 | 无 | 系统 ID |
| is_default | TINYINT | 是 | 0 | 是否默认上下文：0-否，1-是 |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_platt_department

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 是 | 无 | 租户 ID |
| system_id | BIGINT | 是 | 无 | 系统 ID |
| parent_id | BIGINT | 否 | NULL | 父部门 ID |
| dept_code | VARCHAR(64) | 是 | 无 | 部门编码 |
| dept_name | VARCHAR(128) | 是 | 无 | 部门名称 |
| sort_order | INT | 是 | 0 | 排序号 |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_platt_role

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 是 | 无 | 租户 ID |
| system_id | BIGINT | 是 | 无 | 系统 ID |
| app_id | BIGINT | 否 | NULL | 应用 ID，平台/系统角色为空 |
| role_code | VARCHAR(64) | 是 | 无 | 角色编码 |
| role_name | VARCHAR(128) | 是 | 无 | 角色名称 |
| role_type | VARCHAR(32) | 是 | TENANT | 类型：PLATFORM、TENANT、APP |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_platt_permission

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 否 | NULL | 租户 ID，平台权限可为空 |
| system_id | BIGINT | 否 | NULL | 系统 ID |
| app_id | BIGINT | 否 | NULL | 应用 ID |
| module_id | BIGINT | 否 | NULL | 模块 ID |
| permission_code | VARCHAR(128) | 是 | 无 | 权限编码 |
| permission_name | VARCHAR(128) | 是 | 无 | 权限名称 |
| permission_type | VARCHAR(32) | 是 | MENU | 类型：MENU、BUTTON、API、FIELD、DATA_SCOPE |
| resource_path | VARCHAR(255) | 否 | NULL | 路由、接口或字段路径 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_platt_role_permission

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| role_id | BIGINT | 是 | 无 | 角色 ID |
| permission_id | BIGINT | 是 | 无 | 权限 ID |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_platt_account_role

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| account_id | BIGINT | 是 | 无 | 账号 ID |
| role_id | BIGINT | 是 | 无 | 角色 ID |
| tenant_id | BIGINT | 是 | 无 | 租户 ID |
| system_id | BIGINT | 是 | 无 | 系统 ID |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_platt_dict

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 否 | NULL | 租户 ID，全局字典为空 |
| dict_code | VARCHAR(64) | 是 | 无 | 字典编码 |
| dict_name | VARCHAR(128) | 是 | 无 | 字典名称 |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_platt_dict_item

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| dict_id | BIGINT | 是 | 无 | 字典 ID |
| item_value | VARCHAR(128) | 是 | 无 | 字典项值 |
| item_label | VARCHAR(128) | 是 | 无 | 字典项显示名 |
| sort_order | INT | 是 | 0 | 排序号 |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_app_application

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 是 | 无 | 租户 ID |
| system_id | BIGINT | 是 | 无 | 系统 ID |
| app_code | VARCHAR(64) | 是 | 无 | 应用编码 |
| app_name | VARCHAR(128) | 是 | 无 | 应用名称 |
| status | VARCHAR(32) | 是 | DRAFT | 状态：DRAFT、PUBLISHED、DISABLED |
| published_version_id | BIGINT | 否 | NULL | 当前发布版本 ID |
| visible_scope | VARCHAR(32) | 是 | TENANT | 可见范围：TENANT、ROLE、CUSTOM |
| description | VARCHAR(500) | 否 | NULL | 应用说明 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_app_version

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| app_id | BIGINT | 是 | 无 | 应用 ID |
| version_no | INT | 是 | 无 | 版本号 |
| version_name | VARCHAR(128) | 是 | 无 | 版本名称 |
| status | VARCHAR(32) | 是 | DRAFT | 状态：DRAFT、PUBLISHED、ARCHIVED |
| snapshot_json | JSON | 否 | NULL | 发布配置快照 |
| published_at | DATETIME | 否 | NULL | 发布时间 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_module_model

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 是 | 无 | 租户 ID |
| system_id | BIGINT | 是 | 无 | 系统 ID |
| app_id | BIGINT | 是 | 无 | 应用 ID |
| module_code | VARCHAR(64) | 是 | 无 | 模块编码 |
| module_name | VARCHAR(128) | 是 | 无 | 模块名称 |
| data_scope_type | VARCHAR(32) | 是 | OWNER | 数据范围：OWNER、DEPT、DEPT_TREE、ROLE、ALL |
| flow_enabled | TINYINT | 是 | 0 | 是否启用流程：0-否，1-是 |
| import_enabled | TINYINT | 是 | 0 | 是否允许导入 |
| export_enabled | TINYINT | 是 | 1 | 是否允许导出 |
| status | VARCHAR(32) | 是 | DRAFT | 状态：DRAFT、PUBLISHED、DISABLED |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_module_field

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| module_id | BIGINT | 是 | 无 | 模块 ID |
| field_code | VARCHAR(64) | 是 | 无 | 字段编码 |
| field_name | VARCHAR(128) | 是 | 无 | 字段名称 |
| field_type | VARCHAR(32) | 是 | TEXT | 类型：TEXT、NUMBER、DECIMAL、DATE、DATETIME、SELECT、MULTI_SELECT、USER、DEPT、FILE |
| required_flag | TINYINT | 是 | 0 | 是否必填 |
| unique_flag | TINYINT | 是 | 0 | 是否唯一 |
| list_visible | TINYINT | 是 | 1 | 列表是否可见 |
| searchable | TINYINT | 是 | 0 | 是否可搜索 |
| editable | TINYINT | 是 | 1 | 是否可编辑 |
| default_value | VARCHAR(500) | 否 | NULL | 默认值 |
| validation_json | JSON | 否 | NULL | 校验规则 |
| sort_order | INT | 是 | 0 | 排序号 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_module_field_option

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| field_id | BIGINT | 是 | 无 | 字段 ID |
| option_value | VARCHAR(128) | 是 | 无 | 选项值 |
| option_label | VARCHAR(128) | 是 | 无 | 选项显示名 |
| sort_order | INT | 是 | 0 | 排序号 |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_module_page

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| module_id | BIGINT | 是 | 无 | 模块 ID |
| page_code | VARCHAR(64) | 是 | 无 | 页面编码 |
| page_name | VARCHAR(128) | 是 | 无 | 页面名称 |
| page_type | VARCHAR(32) | 是 | LIST | 类型：LIST、FORM、DETAIL、DASHBOARD |
| layout_json | JSON | 否 | NULL | 布局配置 |
| button_json | JSON | 否 | NULL | 按钮配置 |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_module_menu

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 是 | 无 | 租户 ID |
| system_id | BIGINT | 是 | 无 | 系统 ID |
| app_id | BIGINT | 否 | NULL | 应用 ID |
| module_id | BIGINT | 否 | NULL | 模块 ID |
| parent_id | BIGINT | 否 | NULL | 父菜单 ID |
| menu_code | VARCHAR(64) | 是 | 无 | 菜单编码 |
| menu_name | VARCHAR(128) | 是 | 无 | 菜单名称 |
| route_path | VARCHAR(255) | 否 | NULL | 前端路由 |
| sort_order | INT | 是 | 0 | 排序号 |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_module_record

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 是 | 无 | 租户 ID |
| system_id | BIGINT | 是 | 无 | 系统 ID |
| app_id | BIGINT | 是 | 无 | 应用 ID |
| module_id | BIGINT | 是 | 无 | 模块 ID |
| record_no | VARCHAR(64) | 是 | 无 | 业务记录编号 |
| owner_account_id | BIGINT | 否 | NULL | 负责人账号 ID |
| dept_id | BIGINT | 否 | NULL | 归属部门 ID |
| record_status | VARCHAR(32) | 是 | DRAFT | 状态：DRAFT、ACTIVE、FLOWING、ARCHIVED |
| flow_instance_id | BIGINT | 否 | NULL | 当前流程实例 ID |
| version_no | INT | 是 | 1 | 记录版本号 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_module_record_value

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| record_id | BIGINT | 是 | 无 | 记录 ID |
| module_id | BIGINT | 是 | 无 | 模块 ID，查询冗余 |
| field_id | BIGINT | 是 | 无 | 字段 ID |
| field_code | VARCHAR(64) | 是 | 无 | 字段编码，查询冗余 |
| value_text | VARCHAR(1000) | 否 | NULL | 文本值 |
| value_number | DECIMAL(30,8) | 否 | NULL | 数值 |
| value_datetime | DATETIME | 否 | NULL | 日期时间值 |
| value_json | JSON | 否 | NULL | 复杂值 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_module_data_scope

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| module_id | BIGINT | 是 | 无 | 模块 ID |
| role_id | BIGINT | 是 | 无 | 角色 ID |
| scope_type | VARCHAR(32) | 是 | OWNER | 范围：OWNER、DEPT、DEPT_TREE、ROLE、ALL、CUSTOM |
| scope_json | JSON | 否 | NULL | 自定义范围配置 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_module_export_job

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 是 | 无 | 租户 ID |
| module_id | BIGINT | 是 | 无 | 模块 ID |
| job_type | VARCHAR(32) | 是 | EXPORT | 类型：EXPORT |
| status | VARCHAR(32) | 是 | PENDING | 状态：PENDING、RUNNING、SUCCESS、FAILED |
| request_json | JSON | 否 | NULL | 导出参数 |
| result_file_id | BIGINT | 否 | NULL | 结果文件 ID |
| failure_reason | VARCHAR(1000) | 否 | NULL | 失败原因 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_flow_template

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 是 | 无 | 租户 ID |
| app_id | BIGINT | 是 | 无 | 应用 ID |
| module_id | BIGINT | 是 | 无 | 模块 ID |
| template_code | VARCHAR(64) | 是 | 无 | 流程模板编码 |
| template_name | VARCHAR(128) | 是 | 无 | 流程模板名称 |
| status | VARCHAR(32) | 是 | DRAFT | 状态：DRAFT、PUBLISHED、DISABLED |
| published_version_id | BIGINT | 否 | NULL | 当前发布版本 ID |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_flow_template_version

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| template_id | BIGINT | 是 | 无 | 模板 ID |
| version_no | INT | 是 | 无 | 版本号 |
| status | VARCHAR(32) | 是 | DRAFT | 状态：DRAFT、PUBLISHED、ARCHIVED |
| graph_json | JSON | 否 | NULL | 流程图快照 |
| published_at | DATETIME | 否 | NULL | 发布时间 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_flow_instance

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 是 | 无 | 租户 ID |
| module_id | BIGINT | 是 | 无 | 模块 ID |
| record_id | BIGINT | 是 | 无 | 业务记录 ID |
| template_id | BIGINT | 是 | 无 | 流程模板 ID |
| template_version_id | BIGINT | 是 | 无 | 流程模板版本 ID |
| status | VARCHAR(32) | 是 | RUNNING | 状态：RUNNING、APPROVED、REJECTED、CANCELED、TERMINATED |
| current_node_key | VARCHAR(128) | 否 | NULL | 当前节点 key |
| started_by | BIGINT | 是 | 无 | 发起人账号 ID |
| started_at | DATETIME | 是 | CURRENT_TIMESTAMP | 发起时间 |
| ended_at | DATETIME | 否 | NULL | 结束时间 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_flow_task

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| instance_id | BIGINT | 是 | 无 | 流程实例 ID |
| node_key | VARCHAR(128) | 是 | 无 | 节点 key |
| task_name | VARCHAR(128) | 是 | 无 | 任务名称 |
| assignee_id | BIGINT | 否 | NULL | 当前处理人 |
| candidate_json | JSON | 否 | NULL | 候选人快照 |
| status | VARCHAR(32) | 是 | PENDING | 状态：PENDING、APPROVED、REJECTED、CANCELED、TRANSFERRED、RETURNED |
| due_at | DATETIME | 否 | NULL | 到期时间 |
| handled_at | DATETIME | 否 | NULL | 处理时间 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_flow_approval_log

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| instance_id | BIGINT | 是 | 无 | 流程实例 ID |
| task_id | BIGINT | 否 | NULL | 任务 ID |
| action_type | VARCHAR(32) | 是 | SUBMIT | 动作：SUBMIT、APPROVE、REJECT、TRANSFER、RETURN、CANCEL、TERMINATE |
| operator_id | BIGINT | 是 | 无 | 操作人账号 ID |
| comment_text | VARCHAR(1000) | 否 | NULL | 审批意见 |
| snapshot_json | JSON | 否 | NULL | 动作快照 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_upload_storage_config

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| config_code | VARCHAR(64) | 是 | 无 | 存储配置编码 |
| storage_type | VARCHAR(32) | 是 | LOCAL | 类型：LOCAL、S3、MINIO、OSS |
| bucket_name | VARCHAR(128) | 否 | NULL | 桶名 |
| endpoint | VARCHAR(255) | 否 | NULL | 访问端点 |
| base_path | VARCHAR(255) | 否 | NULL | 基础路径 |
| config_json | JSON | 否 | NULL | 扩展配置 |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_upload_file

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 否 | NULL | 租户 ID |
| storage_config_id | BIGINT | 是 | 无 | 存储配置 ID |
| original_name | VARCHAR(255) | 是 | 无 | 原始文件名 |
| file_ext | VARCHAR(32) | 否 | NULL | 文件扩展名 |
| mime_type | VARCHAR(128) | 否 | NULL | MIME 类型 |
| file_size | BIGINT | 是 | 0 | 文件大小 |
| storage_path | VARCHAR(500) | 是 | 无 | 存储路径 |
| sha256 | VARCHAR(128) | 否 | NULL | 文件哈希 |
| status | VARCHAR(32) | 是 | TEMP | 状态：TEMP、REFERENCED、DELETED |
| uploaded_by | BIGINT | 否 | NULL | 上传人账号 ID |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_upload_attachment

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| file_id | BIGINT | 是 | 无 | 文件 ID |
| biz_type | VARCHAR(64) | 是 | 无 | 业务类型：MODULE_RECORD、FLOW_TASK、CONFIG |
| biz_id | BIGINT | 是 | 无 | 业务对象 ID |
| field_code | VARCHAR(64) | 否 | NULL | 关联字段编码 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_upload_import_export_job

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 是 | 无 | 租户 ID |
| module_id | BIGINT | 否 | NULL | 模块 ID |
| job_type | VARCHAR(32) | 是 | IMPORT | 类型：IMPORT、EXPORT |
| status | VARCHAR(32) | 是 | PENDING | 状态：PENDING、RUNNING、SUCCESS、FAILED |
| source_file_id | BIGINT | 否 | NULL | 导入源文件 ID |
| result_file_id | BIGINT | 否 | NULL | 结果文件 ID |
| request_json | JSON | 否 | NULL | 请求参数 |
| failure_reason | VARCHAR(1000) | 否 | NULL | 失败原因 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_openapi_client

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 是 | 无 | 租户 ID |
| system_id | BIGINT | 是 | 无 | 系统 ID |
| client_code | VARCHAR(64) | 是 | 无 | 客户端编码 |
| client_name | VARCHAR(128) | 是 | 无 | 客户端名称 |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED、EXPIRED |
| rate_limit_per_minute | INT | 是 | 600 | 每分钟限流 |
| expired_at | DATETIME | 否 | NULL | 过期时间 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除：0-否，1-是 |

### un_openapi_credential

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| client_id | BIGINT | 是 | 无 | 客户端 ID |
| access_key | VARCHAR(128) | 是 | 无 | 访问 key |
| secret_hash | VARCHAR(255) | 是 | 无 | 密钥哈希或密文摘要 |
| secret_version | INT | 是 | 1 | 密钥版本 |
| sign_algorithm | VARCHAR(32) | 是 | HMAC_SHA256 | 签名算法：HMAC_SHA256 |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_openapi_scope

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| client_id | BIGINT | 是 | 无 | 客户端 ID |
| app_id | BIGINT | 否 | NULL | 授权应用 ID |
| module_id | BIGINT | 否 | NULL | 授权模块 ID |
| scope_code | VARCHAR(128) | 是 | 无 | 授权范围编码 |
| actions | VARCHAR(255) | 是 | 无 | 动作集合：READ、WRITE、DELETE、FLOW |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_openapi_ip_whitelist

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| client_id | BIGINT | 是 | 无 | 客户端 ID |
| ip_value | VARCHAR(64) | 是 | 无 | IP 或 CIDR |
| status | VARCHAR(32) | 是 | ENABLED | 状态：ENABLED、DISABLED |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_openapi_idempotent

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| client_id | BIGINT | 是 | 无 | 客户端 ID |
| idempotent_key | VARCHAR(128) | 是 | 无 | 幂等键 |
| request_hash | VARCHAR(128) | 是 | 无 | 请求摘要 |
| response_hash | VARCHAR(128) | 否 | NULL | 响应摘要 |
| status | VARCHAR(32) | 是 | PROCESSING | 状态：PROCESSING、SUCCESS、FAILED |
| expired_at | DATETIME | 是 | 无 | 过期时间 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_openapi_access_log

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| client_id | BIGINT | 否 | NULL | 客户端 ID |
| request_id | VARCHAR(128) | 是 | 无 | 请求 ID |
| request_path | VARCHAR(255) | 是 | 无 | 请求路径 |
| http_method | VARCHAR(16) | 是 | 无 | HTTP 方法 |
| status | VARCHAR(32) | 是 | SUCCESS | 状态：SUCCESS、FAILED |
| response_code | VARCHAR(64) | 否 | NULL | 响应码 |
| cost_ms | INT | 是 | 0 | 耗时毫秒 |
| remote_ip | VARCHAR(64) | 否 | NULL | 来源 IP |
| error_message | VARCHAR(1000) | 否 | NULL | 错误摘要 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_sys_config

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| config_key | VARCHAR(128) | 是 | 无 | 配置键 |
| config_value | VARCHAR(1000) | 否 | NULL | 配置值 |
| config_type | VARCHAR(32) | 是 | STRING | 类型：STRING、NUMBER、BOOLEAN、JSON |
| description | VARCHAR(500) | 否 | NULL | 配置说明 |
| created_by | BIGINT | 否 | NULL | 创建人账号 ID |
| updated_by | BIGINT | 否 | NULL | 更新人账号 ID |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_sys_login_log

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| account_id | BIGINT | 否 | NULL | 账号 ID |
| username | VARCHAR(64) | 是 | 无 | 登录账号 |
| login_status | VARCHAR(32) | 是 | SUCCESS | 状态：SUCCESS、FAILED、LOCKED |
| remote_ip | VARCHAR(64) | 否 | NULL | 来源 IP |
| user_agent | VARCHAR(500) | 否 | NULL | 客户端 UA |
| failure_reason | VARCHAR(500) | 否 | NULL | 失败原因 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

### un_audit_operation_log

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| id | BIGINT | 是 | 无 | 主键 |
| tenant_id | BIGINT | 否 | NULL | 租户 ID |
| system_id | BIGINT | 否 | NULL | 系统 ID |
| operator_id | BIGINT | 否 | NULL | 操作人账号 ID |
| operation_type | VARCHAR(64) | 是 | 无 | 操作类型 |
| target_type | VARCHAR(64) | 是 | 无 | 操作对象类型 |
| target_id | VARCHAR(128) | 否 | NULL | 操作对象 ID |
| request_source | VARCHAR(32) | 是 | WEB | 来源：WEB、MOBILE、OPENAPI、SYSTEM |
| before_json | JSON | 否 | NULL | 变更前 |
| after_json | JSON | 否 | NULL | 变更后 |
| result_status | VARCHAR(32) | 是 | SUCCESS | 结果：SUCCESS、FAILED |
| error_message | VARCHAR(1000) | 否 | NULL | 错误摘要 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

## 四、表关系

- `un_platt_system` 与 `un_platt_tenant` 为平台基础上下文；租户、账号、部门、角色、权限通过逻辑字段关联系统与租户。
- `un_platt_account` 与 `un_platt_tenant` 通过 `un_platt_account_tenant` 多对多关联；账号与角色通过 `un_platt_account_role` 多对多关联。
- `un_platt_role` 与 `un_platt_permission` 通过 `un_platt_role_permission` 多对多关联。
- `un_platt_dict` 与 `un_platt_dict_item` 为一对多关系。
- `un_app_application` 属于租户和系统，`un_app_application` 与 `un_app_version` 为一对多关系。
- `un_app_application` 与 `un_module_model` 为一对多关系，`un_module_model` 与字段、页面、菜单、记录、数据权限、导出任务为一对多关系。
- `un_module_field` 与 `un_module_field_option` 为一对多关系。
- `un_module_record` 与 `un_module_record_value` 为一对多关系；`un_module_record_value` 冗余 `module_id`、`field_code` 用于 EAV 查询索引。
- `un_flow_template` 关联应用和模块，`un_flow_template` 与 `un_flow_template_version` 为一对多关系。
- `un_flow_instance` 关联业务记录、流程模板和流程版本，`un_flow_instance` 与 `un_flow_task`、`un_flow_approval_log` 为一对多关系。
- `un_upload_storage_config` 与 `un_upload_file` 为一对多关系，`un_upload_file` 与业务对象通过 `un_upload_attachment` 形成多态引用。
- `un_openapi_client` 与凭证、scope、白名单、幂等记录、访问日志为一对多关系。
- `un_sys_login_log`、`un_audit_operation_log` 通过逻辑字段关联账号、租户、系统或业务对象，不建立反向强依赖。

## 五、索引与约束

- 主键：所有表均使用 `BIGINT AUTO_INCREMENT` 单列主键 `id`。
- 唯一约束：
  - `un_platt_system.system_code`
  - `un_platt_tenant.tenant_code`
  - `un_platt_account.username`
  - `un_platt_account_tenant(account_id, tenant_id, system_id)`
  - `un_platt_department(tenant_id, system_id, dept_code, deleted)`
  - `un_platt_role(tenant_id, system_id, app_id, role_code, deleted)`
  - `un_platt_permission(permission_code, deleted)`
  - `un_platt_role_permission(role_id, permission_id)`
  - `un_platt_account_role(account_id, role_id, tenant_id, system_id)`
  - `un_platt_dict(tenant_id, dict_code, deleted)`、`un_platt_dict_item(dict_id, item_value, deleted)`
  - `un_app_application(tenant_id, system_id, app_code, deleted)`、`un_app_version(app_id, version_no)`
  - `un_module_model(app_id, module_code, deleted)`
  - `un_module_field(module_id, field_code, deleted)`、`un_module_field_option(field_id, option_value, deleted)`
  - `un_module_page(module_id, page_code, deleted)`、`un_module_menu(tenant_id, system_id, menu_code, deleted)`
  - `un_module_record(tenant_id, module_id, record_no, deleted)`、`un_module_record_value(record_id, field_id)`
  - `un_module_data_scope(module_id, role_id)`
  - `un_flow_template(module_id, template_code, deleted)`、`un_flow_template_version(template_id, version_no)`
  - `un_upload_storage_config.config_code`
  - `un_upload_attachment(file_id, biz_type, biz_id, field_code)`
  - `un_openapi_client(tenant_id, system_id, client_code, deleted)`、`un_openapi_credential.access_key`
  - `un_openapi_scope(client_id, scope_code)`、`un_openapi_ip_whitelist(client_id, ip_value)`、`un_openapi_idempotent(client_id, idempotent_key)`
  - `un_openapi_access_log.request_id`、`un_sys_config.config_key`
- 普通索引：
  - 上下文索引：`tenant_id`、`system_id`、`app_id`、`module_id` 组合索引用于权限过滤和运行台查询。
  - 状态索引：`status`、`record_status`、`login_status`、`result_status` 用于列表和工作台筛选。
  - 时间索引：`created_at`、`updated_at`、`started_at`、`handled_at` 用于日志、导出任务和待办排序。
  - EAV 索引：`un_module_record_value(module_id, field_code, value_text)`、`value_number`、`value_datetime` 支持文本、数值、时间字段过滤。
- 逻辑外键：
  - 不创建跨模块物理外键，避免 MyBatis-Plus 代码生成、模块独立迁移和初始化顺序耦合。
  - 所有关联字段在业务层按 ID 校验存在性和租户上下文一致性。
  - 同模块强关系通过唯一索引和普通索引保证访问效率，不依赖数据库级级联删除。

## 六、初始化数据

- 初始化数据库：`examine1`。
- 初始化系统：`DEFAULT_SYSTEM`，名称为默认业务系统。
- 初始化租户：`DEFAULT_TENANT`，名称为默认租户。
- 初始化账号：`admin`，状态 `ENABLED`，密码字段写入占位哈希，实际部署应由后端启动或运维流程重置。
- 初始化账号租户关系：`admin` 绑定默认租户和默认系统，并设置为默认上下文。
- 初始化角色：平台超级管理员 `PLATFORM_ADMIN`、租户管理员 `TENANT_ADMIN`。
- 初始化权限：平台管理、应用管理、模块管理、流程管理、文件管理、OpenAPI 管理、审计查看。
- 初始化角色权限：平台超级管理员拥有全部初始化权限；租户管理员拥有租户内应用、模块、流程、文件权限。
- 初始化字典：通用状态 `COMMON_STATUS`，枚举 `ENABLED`、`DISABLED`。
- 初始化系统配置：文件最大上传大小、默认时区、OpenAPI 默认限流。
- 初始化存储配置：本地存储 `LOCAL_DEFAULT`。

## 七、设计说明

- 拆表原因：平台账号、租户、动态配置、运行态记录、流程、文件、OpenAPI、日志的生命周期和权限边界不同，拆分后可以按 Maven 模块生成 `base` CRUD，并在 `manage` 层组合业务语义。
- 索引设计原因：唯一约束以租户、系统、应用、模块和软删除维度为主，避免多租户编码冲突；运行台列表、待办、日志、OpenAPI 调用均按上下文、状态和时间高频查询，设置组合索引。
- EAV 设计：业务记录主表保存上下文和流程状态，字段值表保存 typed value。相较单纯 JSON 字段，typed value 可对文本、数值、时间建立索引，满足动态筛选和排序。
- 冗余字段：`un_module_record_value.module_id`、`field_code`，`un_module_record` 的 `tenant_id/system_id/app_id/module_id`，以及日志中的路径、状态、耗时属于查询冗余，用于减少跨表查询和保障审计不可变快照。
- 迁移注意事项：旧项目平台前缀为旧规则，本设计统一改为 `un_platt_`；OpenAPI 从旧应用表语义中拆出 `un_openapi_` 前缀，但实现模块仍可继续落在 `examine-app`。历史迁移时需要显式建立旧表到新表的字段映射，不能直接按旧前缀复制。
- 与旧项目差异：保留旧项目 Maven 多模块、Flyway 分模块组织、MyBatis-Plus 生成基础 CRUD 的方向；调整表名前缀、补齐字段 typed value 索引、强化应用版本和 OpenAPI 边界，并避免将生成式 CRUD Controller 作为正式业务 API。
