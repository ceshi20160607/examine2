# 当前数据库表结构清单（2026-10-17）

范围：`projects/unexamine/db/init/` 的 18 份建表 SQL、`db/update/` 的新增表；与本地候选库 `unexamine_s1911_final_20261016` 的实际表名逐一比对。数据库已到 V24。表名和用途取自当前 SQL 的中文备注；字段及字段备注以 V24 候选库实际结构为准。行数来自候选库的 COUNT(*)，只是该隔离测试库的使用情况。

**规模：190 张 un_ 业务表，另有 1 张 Flyway 迁移记录表；业务表共 2523 个字段；99 张业务表当前为空。**

## 先看结构上的疑点

- **明显超前的表群**：AI 的 12 张表和项目/任务/工作日志的 12 张表，在当前候选库中全部为空。它们的 SQL 已完整铺开，现有整站验收却没有覆盖相应完整能力。这说明建模范围大于当前已证明的产品能力；空表本身不等于代码未实现。
- **通知与待办重复度高**：通知域 15 张表，其中平台通知和系统通知各有消息、接收人、投递、模板、模板版本、偏好 6 张近似结构；本候选库 11 张为空。需要判断两个范围是否真有不同的生命周期和权限规则。
- **运维表较多**：运维域 14 张，本候选库 11 张为空；发布、部署、备份、恢复演练、升级、验证等既由部署脚本处理，又有数据库记录模型。需要逐项核对页面或自动任务是否写读这些记录，避免两套事实来源。
- **配置版本链成本高**：全库有 15 张名称以 _version 结尾、8 张以 _publication 结尾的表。历史快照有合理用途，但每个配置对象都付出草稿、版本、发布指针、回滚的一整套维护成本。
- **宽表需要逐项解释**：`un_work_task` 有 36 个字段、`un_flow_instance` 有 31 个字段、`un_work_log` 有 29 个字段；其中任务和工作日志在本候选库均无记录。字段多不自动等于设计错误，但这些表最值得检查必需性和字段归属。
- **当前明确的功能反差**：系统字典有 4 张配置/版本/发布相关表，平台字典管理仍是通用配置编辑器，没有完成专门的字典管理页面。表结构规模没有转化成对应的完整产品入口。

以上是结构审视重点，不把测试库空表直接判作废表。两张旧版兼容表在新装候选库为空是预期情况。

## 按领域汇总

| 领域 | 业务表 | 字段 | 当前空表 |
| --- | ---: | ---: | ---: |
| 平台账号与组织 | 14 | 145 | 6 |
| 系统、租户与成员 | 13 | 170 | 2 |
| 会话与权限 | 3 | 46 | 0 |
| 业务审计 | 3 | 41 | 1 |
| 自定义模块配置 | 17 | 218 | 9 |
| 自定义业务记录 | 14 | 169 | 11 |
| 基础能力 | 8 | 95 | 4 |
| 后台作业 | 2 | 29 | 0 |
| 审批流程 | 17 | 218 | 3 |
| 应用接入 | 16 | 202 | 8 |
| 项目任务与工作日志 | 12 | 164 | 12 |
| 通知与待办 | 15 | 214 | 11 |
| AI | 12 | 199 | 12 |
| 文件 | 7 | 98 | 2 |
| 导入导出 | 6 | 101 | 0 |
| 仪表盘与指标 | 11 | 179 | 5 |
| 打印 | 4 | 53 | 0 |
| 运维 | 14 | 168 | 11 |
| 旧打印兼容 | 1 | 7 | 1 |
| 旧首页兼容 | 1 | 7 | 1 |
| **合计** | **190** | **2523** | **99** |

## 逐表清单

“主要字段”列选出归属字段和前几个业务字段，保留 SQL 的原始中文说明；该列是摘要，不是全部字段。字段总数可直接暴露宽表。 V19 和 V20 升级为 4 张 Flow 表增加了 7 个应用身份字段，已计入当前字段数。

### 平台账号与组织（14 张）

建表来源：[V1__platform.sql](../../db/init/V1__platform.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_plat_account` | 平台账号 | username：登录用户名；email：账号绑定邮箱；mobile：账号绑定手机号；display_name：账号展示名称；avatar_file_id：头像文件标识；locale：界面语言区域；status：平台账号状态 | 13 | 21 |
| `un_plat_account_credential` | 账号口令凭据 | account_id：所属平台账号标识；password_hash：口令哈希值；password_algorithm：口令哈希算法；must_change_password：下次登录是否必须修改口令；failed_attempts：连续登录失败次数；lock_expire_time：账号口令锁定截止时间；credential_version：口令凭据版本号 | 9 | 21 |
| `un_plat_account_mfa` | 账号多因素认证方式 | account_id：所属平台账号标识；method_type：多因素认证方式类型；secret_ref：认证密钥的受控引用；display_label：认证方式展示标签；verify_time：认证方式最近验证时间；status：账号多因素认证方式状态 | 10 | 0 |
| `un_plat_sso_provider` | 平台统一身份源配置 | platform_id：所属平台标识；code：平台范围内身份源编码；name：身份源显示名称；protocol：身份源认证协议；issuer：身份源发行者标识；client_id：身份源客户端标识；status：身份源启用状态 | 15 | 0 |
| `un_plat_sso_provider_version` | 平台身份源的测试与发布版本 | provider_id：所属平台身份源标识；version_number：身份源内递增的版本号；protocol：OIDC、SAML、OAuth2、LDAP 或企业身份协议；issuer：身份源发行者标识；client_id：身份源客户端标识；client_secret_ref：客户端密钥的受控引用；status：草稿、发布或归档状态 | 21 | 0 |
| `un_plat_sso_identity` | 外部身份与平台账号绑定 | provider_id：所属平台身份源标识；external_subject：外部身份主体标识；account_id：绑定的平台账号标识；attributes_json：外部身份属性快照；last_login_time：该外部身份最近登录时间 | 8 | 0 |
| `un_plat_password_history` | 账号历史口令 | account_id：所属平台账号标识；password_hash：历史口令哈希值；credential_version：对应口令凭据版本号；change_time：该口令启用时间 | 5 | 0 |
| `un_plat_password_reset` | 平台账号一次性密码找回请求 | account_id：被找回密码的平台账号主键；token_hash：一次性随机令牌的 SHA-256 摘要；expire_time：令牌失效时间；used_time：令牌消费或被新请求取代的时间 | 6 | 0 |
| `un_plat_platform` | 平台配置 | code：平台唯一编码；name：平台展示名称；logo_file_id：平台标识文件标识；contact_name：平台联系人姓名；contact_email：平台联系人邮箱；settings_json：平台运行设置；status：平台启用状态 | 11 | 1 |
| `un_plat_department` | 平台组织部门 | platform_id：所属平台标识；parent_id：上级平台部门标识；code：平台内部门编码；name：部门展示名称；path_code：部门层级编码路径；sort_order：同级部门排序值；status：平台组织部门状态 | 11 | 3 |
| `un_plat_member` | 平台成员 | platform_id：所属平台标识；account_id：对应平台账号标识；department_id：所属平台部门标识；title：平台成员职务名称；status：平台成员状态 | 9 | 21 |
| `un_plat_role` | 平台角色 | platform_id：所属平台标识；code：平台内角色编码；name：角色展示名称；description：角色用途说明；built_in：是否为平台内置角色；status：平台角色状态 | 10 | 5 |
| `un_plat_member_role` | 平台成员角色关系 | platform_id：所属平台标识；member_id：获授权的平台成员标识；role_id：授予的平台角色标识 | 5 | 4 |
| `un_plat_role_permission` | 平台角色的正向资源动作授权 | platform_id：所属平台标识；role_id：平台角色标识；resource_type：授权资源类型；resource_code：授权资源编码；action_code：允许执行的动作编码；data_scope_type：数据范围类型；data_scope_json：数据范围参数 | 12 | 6 |

### 系统、租户与成员（13 张）

建表来源：[V2__system.sql](../../db/init/V2__system.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_sys_system` | 平台内业务系统 | platform_id：所属平台标识；code：平台内系统编码；name：系统展示名称；description：系统用途说明；logo_file_id：系统标识文件标识；creator_account_id：当前系统所属平台账号标识，所属权转移时更新；status：系统运行状态 | 13 | 2 |
| `un_sys_system_domain` | 业务系统访问域名 | system_id：所属业务系统标识；tenant_id：访问地址绑定的系统租户标识；domain_type：域名用途类型；host：访问域名主机名；base_path：系统访问路径前缀；tls_required：是否要求加密传输；status：业务系统访问域名状态 | 12 | 0 |
| `un_sys_sso_binding` | 系统租户对平台身份源的独立配置与发布绑定 | platform_id：系统与身份源共同所属的平台标识；system_id：目标系统标识；tenant_id：目标租户标识；provider_id：平台身份源标识；provider_version_id：固定的平台身份源发布版本标识；binding_version：该系统租户身份源绑定的配置版本号；status：草稿、启用或停用状态 | 21 | 0 |
| `un_sys_tenant` | 系统租户 | system_id：所属业务系统标识；code：系统内租户编码；name：租户展示名称；login_title：租户专属登录页标题，空值使用租户名称；login_subtitle：租户专属登录页说明，空值使用系统默认说明；is_main：是否为系统主租户；status：系统租户状态 | 13 | 3 |
| `un_sys_member` | 系统成员 | system_id：所属业务系统标识；account_id：对应平台账号标识；employee_number：系统内成员工号；display_name：系统内成员显示名称；status：系统成员状态；join_time：加入系统时间 | 10 | 10 |
| `un_sys_member_invitation` | 系统成员一次性邀请 | system_id：邀请加入的系统标识；tenant_id：邀请加入的租户标识；target_account_id：指定受邀平台账号标识，为空表示持链接的登录账号可接受；token_hash：一次性邀请令牌摘要；department_id：接受后所属部门标识；role_ids_json：接受后授予的当前租户角色标识列表；status：邀请状态，PENDING、ACCEPTED 或 REVOKED | 17 | 5 |
| `un_sys_access_request` | 系统成员访问申请及审批历史 | system_id：目标系统标识；tenant_id：目标租户标识，未确定租户时为空；account_id：申请的平台账号标识；identity_provider：申请所用身份源编码；external_user_id：身份源中的外部用户标识；request_reason：申请原因；status：申请状态，PENDING 表示未决 | 19 | 3 |
| `un_sys_department` | 租户组织部门 | system_id：所属业务系统标识；tenant_id：所属租户标识；parent_id：上级租户部门标识；code：租户内部门编码；name：部门展示名称；path_code：部门层级编码路径；status：租户组织部门状态 | 12 | 3 |
| `un_sys_tenant_member` | 租户成员 | system_id：所属业务系统标识；tenant_id：所属租户标识；system_member_id：对应系统成员标识；department_id：所属租户部门标识；tenant_admin：是否为租户管理员；status：租户成员状态 | 10 | 12 |
| `un_sys_role` | 租户角色 | system_id：所属业务系统标识；tenant_id：所属租户标识；code：租户内角色编码；name：角色展示名称；description：角色用途说明；built_in：是否为租户内置角色；status：租户角色状态 | 16 | 7 |
| `un_sys_member_role` | 租户成员角色关系 | tenant_id：所属租户标识；tenant_member_id：获授权的租户成员标识；role_id：授予的租户角色标识 | 5 | 8 |
| `un_sys_role_permission` | 系统租户角色的正向资源动作授权 | system_id：所属系统标识；tenant_id：所属租户标识；role_id：系统角色标识；resource_type：授权资源类型；resource_code：授权资源编码；action_code：允许执行的动作编码；data_scope_type：数据范围类型 | 13 | 34 |
| `un_sys_member_reporting_line` | 租户成员职位及直属上级关系 | system_id：成员所属系统主键；tenant_id：成员所属租户主键；tenant_member_id：具有该职位和汇报关系的租户成员主键；manager_tenant_member_id：同租户直属上级成员主键，无上级时为空；position_title：成员在租户组织中的职位名称 | 9 | 8 |

### 会话与权限（3 张）

建表来源：[V3__authentication.sql](../../db/init/V3__authentication.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_auth_field_policy` | 平台或系统角色的字段读取写入策略 | platform_id：所属平台标识；system_id：所属系统标识，平台策略为空；tenant_id：所属租户标识，平台策略为空；context_type：策略范围类型，PLATFORM 或 SYSTEM；role_id：业务查询使用的角色标识；platform_role_id：平台策略对应的平台角色标识；system_role_id：系统策略对应的系统角色标识 | 17 | 11 |
| `un_auth_permission_version` | 平台或系统租户权限的版本流水 | platform_id：所属平台标识；system_id：所属系统标识，平台版本为空；tenant_id：所属租户标识，平台版本为空；context_type：权限范围类型，PLATFORM 或 SYSTEM；version_number：该范围内递增的权限版本号；reason：权限版本变更原因；changed_by_account_id：发起变更的平台账号标识 | 9 | 19 |
| `un_auth_session` | 登录会话 | platform_id：当前平台范围标识；system_id：当前业务系统范围标识；tenant_id：当前租户范围标识；access_token_hash：访问令牌哈希值；refresh_token_hash：刷新令牌哈希值；account_id：登录的平台账号标识；member_id：当前系统成员标识 | 20 | 437 |

### 业务审计（3 张）

建表来源：[V4__audit.sql](../../db/init/V4__audit.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_audit_event` | 不可变的业务操作审计事件 | platform_id：事件所属平台主键；system_id：事件所属系统主键，平台事件为空；tenant_id：事件所属租户主键，平台或系统公共事件为空；trace_id：跨服务调用链追踪标识；request_id：触发操作的请求标识；context_type：事件所属的平台或系统范围类型；actor_account_id：执行操作的平台账号主键，系统事件也使用统一账号 | 20 | 993 |
| `un_audit_field_change` | 不可变的业务字段变更前后值 | audit_event_id：对应的操作审计事件主键；field_code：发生变更的业务字段编码；value_type：字段值的数据类型；before_value_json：变更前字段值，敏感信息须脱敏；after_value_json：变更后字段值，敏感信息须脱敏；sensitivity：字段值敏感等级 | 8 | 10 |
| `un_audit_retention_marker` | 业务数据清理后的不可变留痕 | system_id：被清理对象所属系统主键；tenant_id：被清理对象所属租户主键；context_type：被清理对象所属的平台或系统范围类型；object_type：被清理业务对象类型；object_id：被清理业务对象标识；business_key：便于后续追溯的业务编号；snapshot_hash：清理前业务快照的摘要 | 13 | 0 |

### 自定义模块配置（17 张）

建表来源：[V5__moduleconfig.sql](../../db/init/V5__moduleconfig.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_cfg_module_group` | 模块分组配置 | system_id：所属系统标识；owner_tenant_id：配置所属租户标识；code：范围内稳定编码；name：显示名称；icon：展示图标；sort_order：显示排序值；status：生命周期状态 | 12 | 3 |
| `un_cfg_module` | 业务模块草稿定义 | system_id：所属系统标识；owner_tenant_id：配置所属租户标识；group_id：所属模块分组标识；code：范围内稳定编码；name：显示名称；description：业务说明；status：生命周期状态 | 16 | 3 |
| `un_cfg_dictionary` | 系统数据字典定义 | system_id：所属系统标识；owner_tenant_id：配置所属租户标识；code：范围内稳定编码；name：显示名称；dictionary_type：字典业务类型；category_code：字典分类编码；status：生命周期状态 | 14 | 0 |
| `un_cfg_dictionary_item` | 数据字典草稿选项 | dictionary_id：所属字典标识；parent_id：上级配置项标识；code：范围内稳定编码；label：选项显示文本；path_code：选项层级编码路径；depth_level：选项所在层级，根节点为零；status：生命周期状态 | 18 | 0 |
| `un_cfg_dictionary_version` | 不可变的数据字典发布版本 | system_id：所属系统标识；owner_tenant_id：配置所属租户标识；dictionary_id：所属字典标识；version_number：发布版本序号；draft_revision：草稿修订号；snapshot_json：发布时冻结的完整配置快照；snapshot_hash：字典发布快照内容摘要 | 10 | 0 |
| `un_cfg_dictionary_publication` | 数据字典当前生效发布版本指针 | system_id：所属系统标识；owner_tenant_id：配置所属租户标识；dictionary_id：所属字典标识；current_version_id：当前生效版本标识；published_by_member_id：发布系统成员标识；publish_time：发布时间 | 8 | 0 |
| `un_cfg_module_field` | 模块字段草稿定义 | system_id：所属系统标识；owner_tenant_id：配置所属租户标识；module_id：所属模块标识；code：范围内稳定编码；name：显示名称；field_type：字段业务类型；status：生命周期状态 | 19 | 5 |
| `un_cfg_module_page` | 模块页面草稿配置 | system_id：所属系统标识；owner_tenant_id：配置所属租户标识；module_id：所属模块标识；page_type：页面用途类型；page_code：模块内稳定页面编码；name：显示名称；status：生命周期状态 | 12 | 9 |
| `un_cfg_module_menu` | 模块菜单草稿配置 | system_id：所属系统标识；owner_tenant_id：配置所属租户标识；module_id：所属模块标识；parent_id：上级配置项标识；code：范围内稳定编码；name：显示名称；status：生命周期状态 | 15 | 3 |
| `un_cfg_module_action` | 模块动作草稿配置 | system_id：所属系统标识；owner_tenant_id：配置所属租户标识；module_id：所属模块标识；code：范围内稳定编码；name：显示名称；action_type：动作执行类型；status：生命周期状态 | 14 | 39 |
| `un_cfg_module_rule` | 模块规则草稿配置 | system_id：所属系统标识；owner_tenant_id：配置所属租户标识；module_id：所属模块标识；code：范围内稳定编码；name：显示名称；rule_type：规则业务类型；status：生命周期状态 | 20 | 0 |
| `un_cfg_query_index` | 模块查询索引草稿定义 | system_id：所属系统标识；owner_tenant_id：配置所属租户标识；module_id：所属模块标识；code：范围内稳定编码；name：显示名称；unique_index：是否为业务唯一索引；status：生命周期状态 | 11 | 0 |
| `un_cfg_query_index_field` | 查询索引包含的模块字段 | module_id：所属模块标识；query_index_id：所属查询索引标识；field_id：索引使用的模块字段标识；sort_order：显示排序值；sort_direction：索引字段排序方向 | 7 | 0 |
| `un_cfg_module_version` | 不可变的模块发布版本及完整配置快照 | system_id：所属系统标识；owner_tenant_id：配置所属租户标识；module_id：所属模块标识；version_number：发布版本序号；draft_revision：草稿修订号；schema_hash：发布配置内容摘要；snapshot_json：发布时冻结的完整配置快照 | 11 | 6 |
| `un_cfg_module_publication` | 模块当前生效发布版本指针 | system_id：所属系统标识；owner_tenant_id：配置所属租户标识；module_id：所属模块标识；current_version_id：当前生效版本标识；updated_by_member_id：最近更新系统成员标识 | 8 | 3 |
| `un_cfg_tenant_extension` | 租户对基础模块的扩展草稿 | system_id：所属系统标识；tenant_id：扩展所属租户标识；base_module_id：扩展的基础模块标识；draft_revision：草稿修订号；extension_json：租户扩展草稿配置；status：生命周期状态；current_version_id：当前生效的租户扩展发布版本标识 | 13 | 0 |
| `un_cfg_tenant_extension_version` | 不可变的租户扩展发布版本 | system_id：所属系统标识；extension_id：所属租户扩展标识；base_module_id：扩展的基础模块标识；version_number：发布版本序号；base_module_version_id：扩展所基于的模块发布版本标识；snapshot_json：发布时冻结的完整配置快照；schema_hash：发布配置内容摘要 | 10 | 0 |

### 自定义业务记录（14 张）

建表来源：[V6__runtimedata.sql](../../db/init/V6__runtimedata.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_biz_record` | 自定义模块的通用业务主记录 | system_id：业务数据所属系统；tenant_id：业务数据归属租户；module_id：业务记录所属模块主键；created_config_version_id：创建业务记录时固定的已发布模块版本；updated_config_version_id：最近修改业务记录时固定的已发布模块版本；record_number：模块内可展示的业务编号；status：当前业务状态或处理状态 | 20 | 6 |
| `un_biz_record_value` | 业务主记录的自定义字段值 | module_id：业务记录所属模块主键；record_id：业务主记录主键；field_id：模块字段配置主键；field_code：写入时字段的稳定业务编码；value_type：字段值存储类型；value_text：文本类型原始值；value_number：数值类型原始值 | 17 | 8 |
| `un_biz_record_child_row` | 通用业务记录的子表明细行 | system_id：主记录所属系统；tenant_id：主记录的数据归属租户；module_id：主记录所属模块；record_id：主业务记录主键；child_field_id：定义此子表的主模块字段主键；row_key：同一记录和子表内稳定的行标识；sort_order：子表行显示顺序 | 12 | 0 |
| `un_biz_record_child_value` | 通用子表明细行的独立列字段值 | module_id：子表所属模块；child_field_id：子表定义字段主键；child_row_id：所属子表行主键；field_id：子表列字段主键；value_type：字段值存储类型；value_text：文本类型原始值；value_number：数值类型原始值 | 17 | 0 |
| `un_biz_record_comment` | 业务记录评论与同记录回复 | system_id：业务记录所属系统；tenant_id：业务记录的数据归属租户；module_id：业务记录所属模块；record_id：被评论的业务记录主键；parent_comment_id：同一记录内被回复的评论主键；body：评论正文；created_by_member_id：发表评论的系统成员主键 | 13 | 0 |
| `un_biz_record_index` | 业务记录按发布索引配置生成的查询键 | system_id：业务数据所属系统；tenant_id：业务数据归属租户；module_id：业务记录所属模块主键；record_id：业务主记录主键；query_index_id：本模块查询索引定义主键；index_key_hash：复合查询键的哈希摘要；unique_key_hash：唯一索引的规范化键摘要；非唯一索引为空 | 11 | 0 |
| `un_biz_record_relation` | 业务记录之间由关联字段定义的独立关系 | system_id：业务数据所属系统；tenant_id：业务数据归属租户；source_module_id：来源业务记录所属模块；source_record_id：来源业务记录主键；field_id：来源模块中的关联字段配置主键；target_module_id：目标业务记录所属模块；target_record_id：目标业务记录主键 | 10 | 0 |
| `un_biz_record_participant` | 业务记录的负责人以外的团队参与成员 | system_id：业务数据所属系统；tenant_id：业务数据归属租户；record_id：业务主记录主键；system_member_id：参与记录的系统成员主键；participant_type：参与关系类型，例如协作人或关注人；created_by_member_id：创建业务数据的系统成员主键 | 8 | 0 |
| `un_biz_record_state_history` | 业务记录状态变更历史 | record_id：业务主记录主键；from_status：变更前业务状态；to_status：变更后的业务状态；reason：业务变更原因；source_type：发起状态变更的来源类型；source_id：发起状态变更的来源对象标识；changed_by_member_id：执行变更的系统成员主键 | 9 | 2 |
| `un_biz_record_owner_history` | 业务记录负责人和部门变更历史 | record_id：业务主记录主键；from_owner_member_id：变更前负责人主键；to_owner_member_id：变更后负责人主键；from_department_id：变更前负责部门主键；to_department_id：变更后负责部门主键；reason：业务变更原因；changed_by_member_id：执行变更的系统成员主键 | 9 | 0 |
| `un_biz_record_conversion` | 业务记录转化执行及永久成功身份 | system_id：业务数据所属系统；tenant_id：业务数据归属租户；source_module_id：来源记录及转化动作共同所属模块；source_record_id：来源业务记录主键；action_id：来源模块的转化动作配置主键；target_module_id：成功转化永久唯一键中的目标模块；status：转化状态：处理中、失败、成功或已解除关系 | 14 | 0 |
| `un_biz_record_conversion_result` | 一次转化创建的目标记录 | tenant_id：来源和目标记录共同所属的数据租户；conversion_id：所属转化执行记录主键；target_module_id：目标业务记录所属模块；target_record_id：目标业务记录主键；result_type：目标记录在转化结果中的类型 | 7 | 0 |
| `un_biz_tenant_share` | 同系统跨租户记录及动态模块范围共享授权，不改变数据所有权 | system_id：共享双方所属系统；source_tenant_id：拥有业务数据的来源租户；target_tenant_id：获得访问关系的目标租户；module_id：共享数据所属模块；record_id：指定记录授权的原记录主键，动态范围授权为空；scope_type：授权范围类型：RECORD指定记录，DATA_RANGE动态数据范围；status：授权存储状态：ACTIVE有效，REVOKED已撤销 | 16 | 0 |
| `un_biz_tenant_share_usage` | 目标租户通过共享授权访问业务数据的历史 | share_id：共享授权主键；target_tenant_member_id：执行共享访问的目标租户成员；action_code：通过共享授权执行的业务动作编码；result_code：共享访问结果编码；occur_time：共享访问发生时间 | 6 | 0 |

### 基础能力（8 张）

建表来源：[V7__foundation.sql](../../db/init/V7__foundation.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_core_setting` | 平台、系统和租户范围内的普通业务配置 | platform_id：所属平台主键；system_id：所属系统主键，平台配置为空；tenant_id：所属租户主键，平台和系统配置为空；context_type：配置归属：平台、系统或租户；category：业务配置类别；setting_key：归属范围内的配置键；status：配置启停状态 | 14 | 4 |
| `un_core_feature_flag` | 平台或系统租户的受控特性开关 | platform_id：平台范围主键，系统租户开关为空；system_id：系统范围主键，平台开关为空；tenant_id：系统租户主键，平台开关为空；context_type：特性开关归属：平台或系统租户；flag_key：归属范围内的特性编码；enabled：开关默认启用状态；status：配置启停状态 | 12 | 0 |
| `un_core_quota_policy` | 平台或系统租户的配额上限策略 | platform_id：平台范围主键，系统租户配额为空；system_id：系统范围主键，平台配额为空；tenant_id：系统租户主键，平台配额为空；context_type：配额归属：平台或系统租户；quota_code：归属范围内的配额编码；period_type：统计周期类型；status：策略启停状态 | 13 | 0 |
| `un_core_quota_usage` | 配额周期内的已用和预留计数 | quota_policy_id：所属配额策略主键；period_key：配额统计周期标识；used_value：当前周期已正式占用的配额量；reserved_value：当前周期已预留但尚未正式占用的配额量 | 7 | 0 |
| `un_core_idempotency_record` | 业务操作幂等请求及首次执行结果 | context_key：幂等键所属业务范围标识；operation_code：被保护的业务操作编码；idempotency_key：调用方提供的幂等请求标识；request_hash：原始请求内容摘要，用于识别同键不同请求；status：幂等请求处理状态；response_code：首次执行的业务结果编码；response_json：可供重复请求返回的首次执行结果 | 13 | 11 |
| `un_core_sequence` | 租户业务编号规则及当前序号 | system_id：编号序列所属系统主键；tenant_id：编号序列所属租户主键；sequence_code：租户范围内的编号序列编码；pattern：生成业务编号的格式模板；reset_period：计数器重置周期类型；current_period_key：当前计数周期标识；status：编号序列启停状态 | 13 | 0 |
| `un_core_event_outbox` | 待投递的本地业务事件及投递结果 | system_id：系统事件所属系统主键；tenant_id：租户事件所属租户主键；context_type：事件所属的平台或系统范围类型；aggregate_type：产生事件的业务聚合类型；aggregate_id：产生事件的业务聚合标识；event_type：需要投递的业务事件类型；status：事件投递状态 | 16 | 35 |
| `un_core_cache_epoch` | 按上下文隔离的缓存命名空间失效版本 | context_key：缓存归属的业务上下文标识；cache_namespace：需要失效的缓存命名空间；epoch_value：当前缓存失效世代号；reason：推进缓存世代的业务原因 | 7 | 4 |

### 后台作业（2 张）

建表来源：[V8__backgroundjobs.sql](../../db/init/V8__backgroundjobs.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_job_background` | 后台作业业务申请与最终审计；运行状态和重试事实保存在 Redis | platform_id：所属平台主键；system_id：系统层申请所属系统主键；tenant_id：系统层申请所属租户主键，可为空表示系统公共作业；context_type：申请层级，PLATFORM 或 SYSTEM；job_type：作业业务类型，如导入、导出、打印或文件处理；source_type：发起作业的业务对象类型；source_id：发起作业的业务对象标识 | 19 | 2 |
| `un_job_item_result` | 后台作业逐项业务结果，不保存运行尝试记录 | job_id：所属后台作业申请主键；item_key：作业内稳定的业务项标识，用于幂等写入；row_number：导入导出等行处理场景的原始行号；status：该业务项的最终处理状态；result_json：该业务项的结果数据或文件引用；error_code：该业务项的失败错误码；error_message：该业务项的失败原因摘要 | 10 | 1 |

### 审批流程（17 张）

建表来源：[V9__flow.sql](../../db/init/V9__flow.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_flow_definition` | 流程定义与草稿作用域 | platform_id：所属平台ID；system_id：所属系统ID；owner_tenant_id：配置来源租户ID；context_type：流程所属上下文类型；owner_application_id：配置此流程的应用ID；系统后台配置为空；code：稳定业务编码；status：当前业务状态 | 15 | 4 |
| `un_flow_node` | 流程草稿节点 | flow_id：流程定义ID；node_key：流程节点稳定编码；node_type：节点类型；name：显示名称；position_x：设计画布横坐标；position_y：设计画布纵坐标；assignee_policy_json：审批人解析策略 | 15 | 5 |
| `un_flow_edge` | 流程草稿连线 | flow_id：流程定义ID；edge_key：流程连线稳定编码；source_node_key：连线起点节点编码；target_node_key：连线终点节点编码；condition_expression：触发或连线条件表达式；priority_order：条件或绑定优先级；config_json：节点或连线业务配置 | 11 | 3 |
| `un_flow_version` | 不可变流程发布版本 | flow_id：流程定义ID；version_number：流程发布版本号；draft_revision：草稿修订号；definition_hash：发布定义内容摘要；snapshot_json：发布时冻结的完整流程定义快照；simulation_result_json：发布前模拟验证结果；change_summary：发布变更说明 | 10 | 3 |
| `un_flow_publication` | 流程当前发布指针 | flow_id：流程定义ID；current_version_id：当前生效流程版本ID；updated_by_account_id：最后修改人平台账号ID | 6 | 2 |
| `un_flow_trigger_binding` | 模块事件和动作的租户流程绑定 | system_id：所属系统ID；owner_tenant_id：配置来源租户ID；module_id：关联业务模块ID；action_id：绑定的模块动作ID；trigger_event：模块业务触发事件编码；flow_id：命中的流程定义ID；直接执行业务动作时为空；status：当前业务状态 | 13 | 2 |
| `un_flow_binding_resolution` | 触发时的最终生效绑定解析 | system_id：所属系统ID；tenant_id：实际运行租户ID；module_id：关联业务模块ID；trigger_event：模块业务触发事件编码；binding_id：命中的流程绑定ID；flow_version_id：固定的流程发布版本ID；resolution_reason：默认继承或租户覆盖原因 | 9 | 2 |
| `un_flow_instance` | 运行流程实例及启动快照 | platform_id：所属平台ID；system_id：所属系统ID；tenant_id：实际运行租户ID；context_type：流程所属上下文类型；flow_id：流程定义ID；flow_version_id：固定的流程发布版本ID；status：当前业务状态 | 31 | 3 |
| `un_flow_subflow_link` | 父子流程实例关系 | parent_instance_id：父流程实例ID；parent_execution_id：启动子流程的父节点执行轮次ID；child_instance_id：子流程实例ID；parent_node_key：父流程节点编码快照；status：子流程关系状态；completed_time：子流程结束并处理父流程的时间 | 8 | 0 |
| `un_flow_execution` | 流程节点执行轮次 | instance_id：所属流程实例ID；parent_execution_id：父节点执行轮次ID；node_key：流程节点稳定编码；node_type：节点类型；status：当前业务状态；enter_time：节点进入时间；leave_time：节点离开时间 | 10 | 8 |
| `un_flow_instance_variable` | 流程实例变量 | instance_id：所属流程实例ID；variable_key：实例变量编码；value_type：实例变量值类型；value_json：实例变量JSON值 | 7 | 4 |
| `un_flow_task` | 节点执行轮次的待办任务 | instance_id：所属流程实例ID；execution_id：任务所属的节点执行轮次；node_key：流程节点稳定编码；task_type：待办任务类型；status：当前业务状态；assignee_account_id：当前处理人平台账号ID；assignee_tenant_member_id：当前处理人在实际运行租户的成员ID | 17 | 2 |
| `un_flow_task_candidate` | 待办任务候选处理人 | task_id：所属流程待办任务ID；candidate_type：候选主体类型；candidate_id：候选主体标识；resolution_reason：默认继承或租户覆盖原因 | 6 | 2 |
| `un_flow_action` | 流程操作及幂等记录 | instance_id：所属流程实例ID；task_id：所属流程待办任务ID；node_key：流程节点稳定编码；action_code：流程操作编码；comment_text：操作意见正文；input_json：本次操作输入快照；result_json：本次操作结果快照 | 14 | 8 |
| `un_flow_exception` | 流程异常与处置 | instance_id：所属流程实例ID；node_key：流程节点稳定编码；exception_type：流程异常类型；error_code：失败错误编码；error_message：失败原因说明；policy_action：异常策略选择的处置动作；status：当前业务状态 | 14 | 0 |
| `un_flow_history_event` | 不可变流程事件历史 | instance_id：所属流程实例ID；execution_id：关联节点执行轮次ID；task_id：所属流程待办任务ID；node_key：流程节点稳定编码；event_type：历史事件类型；event_name：历史事件显示名称；actor_account_id：事件操作者平台账号ID | 13 | 10 |
| `un_flow_job` | 流程定时与异步等待任务 | instance_id：所属流程实例ID；execution_id：关联节点执行轮次ID；node_key：流程节点稳定编码；job_type：流程任务类型；status：当前业务状态；payload_json：流程异步任务内容；idempotency_key：操作幂等键 | 19 | 0 |

### 应用接入（16 张）

建表来源：[V10__application.sql](../../db/init/V10__application.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_app_definition` | 平台或系统范围内的受控访问应用定义 | platform_id：所属平台主键；owner_tenant_id：所属租户主键，平台应用为空；context_type：应用所属上下文：PLATFORM平台、SYSTEM系统；owner_system_id：所属系统主键，平台应用为空；code：当前上下文内唯一的应用编码；name：应用名称；status：应用状态：DRAFT草稿、ACTIVE启用、DISABLED停用、DELETED删除 | 16 | 2 |
| `un_app_callback` | 应用事件回调配置 | application_id：所属应用主键；callback_type：回调业务类型；url：接收回调的HTTPS地址；event_codes_json：订阅的业务事件编码列表；signing_secret_ref：回调签名密钥安全引用；timeout_millis：单次回调超时毫秒数；status：回调启停状态 | 12 | 0 |
| `un_app_grant` | 应用按目标资源和动作配置的访问授权草稿 | application_id：所属应用主键；target_type：授权目标：PLATFORM平台、SYSTEM系统；target_system_id：目标系统主键，平台授权为空；target_tenant_id：目标租户主键，平台授权为空；resource_type：授权资源类型：FLOW流程、MODULE模块；resource_id：目标资源业务编码；status：授权启停状态 | 14 | 4 |
| `un_app_grant_field` | 应用授权内的字段读写与脱敏策略 | grant_id：所属授权主键；field_code：目标模块字段编码；readable：是否允许返回字段值；writable：是否允许写入字段值；mask_strategy：字段返回时的脱敏方式 | 7 | 2 |
| `un_app_provider_connection` | 应用外部平台连接 | application_id：所属受控应用主键；provider_code：平台编码：WECOM企业微信、FEISHU飞书、DINGTALK钉钉、WECHAT微信；product_code：平台产品形态编码；connection_key：不可猜测的公开回调连接标识；display_name：连接显示名称；tenant_key：平台租户标识，例如企业微信CorpID；status：连接状态：DRAFT草稿、ACTIVE启用、DISABLED停用 | 16 | 0 |
| `un_app_provider_secret` | 应用外部平台密钥版本 | connection_id：所属外部平台连接主键；secret_type：密钥用途：API_SECRET接口密钥、CALLBACK_TOKEN回调令牌、ENCODING_AES_KEY回调加密密钥；secret_version：同一连接同一用途的递增版本号；secret_ciphertext：使用应用主密钥加密后的密文；secret_hint：仅用于辨认版本的脱敏尾号；status：密钥状态：ACTIVE启用、REVOKED已撤销；created_by_account_id：创建人平台账号主键 | 10 | 0 |
| `un_app_provider_route` | 应用外部平台事件路由 | connection_id：所属外部平台连接主键；event_code：规范化平台事件编码；resource_type：已发布应用授权资源类型；resource_id：已发布应用授权资源业务编码；action_code：已发布应用授权动作编码；target_system_id：目标系统主键；status：路由状态：ACTIVE启用、DISABLED停用 | 12 | 0 |
| `un_app_provider_event` | 应用外部平台回调事件 | connection_id：所属外部平台连接主键；provider_event_id：平台事件幂等标识或服务端稳定摘要；event_code：规范化平台事件编码；sender_external_id：平台侧发送人标识；payload_hash：解密后原始载荷SHA-256摘要；status：处理状态：RECEIVED已接收、PROCESSED已完成、IGNORED无路由、FAILED失败；application_call_id：进入应用网关后生成的调用主键 | 11 | 0 |
| `un_app_provider_delivery` | 应用外部平台消息投递扩展 | connection_id：所属外部平台连接主键；identity_id：目标外部平台成员映射主键；message_id：统一消息主键；message_delivery_id：统一消息投递主键；idempotency_key：同一连接内的业务幂等键；request_hash：规范化投递请求SHA-256摘要；status：投递状态：PENDING待发送、BLOCKED_EXTERNAL缺外部条件、SENT已发送、DELIVERED已送达、FAILED失败、CANCELED已撤销 | 16 | 0 |
| `un_app_provider_identity` | 应用外部平台成员映射 | connection_id：所属外部平台连接主键；external_user_id：平台侧稳定用户标识，例如企业微信UserID；target_type：目标成员类型：PLATFORM平台成员、SYSTEM系统租户成员；target_platform_id：目标平台主键；target_platform_member_id：目标平台成员主键；target_system_id：目标系统主键；status：映射状态：ACTIVE启用、DISABLED停用 | 14 | 0 |
| `un_app_version` | 应用发布时冻结的不可变配置版本 | application_id：所属应用主键；version_number：递增的发布版本号；draft_revision：发布时的草稿修订号；snapshot_hash：发布快照内容摘要；snapshot_json：冻结的应用、授权及回调配置；published_by_account_id：发布人的平台账号主键；publish_time：发布时间 | 8 | 7 |
| `un_app_publication` | 应用当前生效版本指针 | application_id：所属应用主键；current_version_id：当前生效应用版本主键；updated_by_account_id：修改发布指针的平台账号主键 | 6 | 2 |
| `un_app_credential` | 应用访问凭证与密钥版本 | application_id：所属应用主键；credential_version：应用内递增的凭证版本号；client_id：公开的应用访问标识；secret_hash：凭证密钥的不可逆摘要；signing_secret_ref：签名材料的受控密钥引用，不存明文；secret_hint：仅供识别的脱敏尾号；status：凭证状态：ACTIVE启用或REVOKED撤销 | 13 | 10 |
| `un_app_call` | 应用调用审计与幂等结果 | application_id：被调用的目标应用主键；application_version_id：实际执行的不可变应用发布版本主键，前置拒绝时为空；credential_version：验签所用凭证版本，内部可信调用为0；grant_id：命中的草稿授权主键，前置拒绝或授权历史清理后为空；request_id：调用请求唯一标识；trace_id：跨服务追踪标识；status：调用处理状态 | 27 | 41 |
| `un_app_call_nonce` | 已接受应用签名nonce的防重放记录 | application_id：目标应用主键；credential_version：验签所用应用凭证版本号；nonce：请求防重放随机值；request_id：首次接受该nonce的请求标识；request_time：调用方签名请求时间；expire_time：nonce失效时间 | 8 | 21 |
| `un_app_callback_delivery` | 应用事件回调投递及重试结果 | callback_id：所属回调配置主键；event_id：业务事件唯一标识；payload_hash：投递内容摘要；status：投递状态；attempt_count：已尝试投递次数；next_attempt_time：下次允许重试时间；response_status：接收端HTTP状态码 | 12 | 0 |

### 项目任务与工作日志（12 张）

建表来源：[V11__work.sql](../../db/init/V11__work.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_work_project` | 工作项目 | platform_id：所属平台标识；system_id：所属系统标识，平台范围为空；tenant_id：所属租户标识，平台范围为空；context_type：业务上下文类型：PLATFORM 或 SYSTEM；code：同一工作范围内的项目编码；name：名称；status：业务状态 | 24 | 0 |
| `un_work_project_member` | 工作项目参与人 | project_id：所属项目标识；account_id：参与人平台账号标识；tenant_member_id：项目参与人租户成员ID；project_role：项目成员角色；status：业务状态；join_time：加入时间 | 7 | 0 |
| `un_work_task_group` | 项目任务组 | project_id：所属项目标识；name：名称；sort_order：组内排序；status：业务状态；owner_account_id：任务组负责人账号标识；owner_tenant_member_id：任务组负责人租户成员标识；due_time：任务组截止时间 | 12 | 0 |
| `un_work_task` | 工作任务 | platform_id：所属平台标识；system_id：所属系统标识，平台范围为空；tenant_id：所属租户标识，平台范围为空；context_type：业务上下文类型：PLATFORM 或 SYSTEM；project_id：所属项目标识；task_group_id：所属项目任务组标识；status：业务状态 | 36 | 0 |
| `un_work_task_member` | 任务协作、关注和验收成员 | task_id：关联任务标识；account_id：参与人平台账号标识；tenant_member_id：任务参与人租户成员ID；member_type：任务成员参与类型 | 6 | 0 |
| `un_work_task_history` | 任务状态与业务操作历史 | task_id：关联任务标识；action_code：任务操作编码；before_json：操作前任务快照；after_json：操作后任务快照；comment_text：操作补充说明；changed_by_account_id：操作人平台账号标识；change_time：任务操作时间 | 8 | 0 |
| `un_work_log` | 用户手动创建的每日工作日志 | platform_id：所属平台标识；system_id：所属系统标识，平台范围为空；tenant_id：所属租户标识，平台范围为空；context_type：业务上下文类型：PLATFORM 或 SYSTEM；project_id：日志关联的同一工作范围项目标识；author_account_id：日志作者平台账号标识；status：业务状态 | 29 | 0 |
| `un_work_log_task_link` | 同一范围的工作日志与任务关联 | work_log_id：所属工作日志标识；task_id：同一工作范围的任务标识 | 4 | 0 |
| `un_work_log_revision` | 工作日志修订快照 | work_log_id：关联工作日志标识；revision_number：日志修订序号；snapshot_json：日志修订内容快照；revision_reason：日志修订原因；revised_by_account_id：修订人平台账号标识；revise_time：日志修订时间 | 7 | 0 |
| `un_work_log_material` | 工作日志人工选材快照 | work_log_id：工作日志主键；material_type：材料类型：任务、待办、消息或业务操作；material_id：来源材料标识；material_title：选中时的材料标题快照；material_summary：选中时的材料摘要快照；source_time：来源材料发生时间；created_by_account_id：选择材料的平台账号主键 | 9 | 0 |
| `un_work_log_comment` | 工作日志评论与管理反馈 | work_log_id：工作日志主键；comment_type：沟通类型：评论或反馈；comment_text：评论或反馈正文；created_by_account_id：发表人的平台账号主键 | 6 | 0 |
| `un_work_field_config` | 工作域的可配置字段 | platform_id：所属平台标识；system_id：所属系统标识，平台范围为空；tenant_id：所属租户标识，平台范围为空；context_type：业务上下文类型：PLATFORM 或 SYSTEM；target_type：可配置字段所属对象类型；field_code：同一对象范围内的字段编码；status：业务状态 | 16 | 0 |

### 通知与待办（15 张）

建表来源：[V12__notification.sql](../../db/init/V12__notification.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_plat_todo_item` | 平台自身业务的待办投影 | platform_id：待办所属平台主键；assignee_account_id：待办接收平台成员账号主键；todo_type：平台待办类别；source_type：平台来源业务类别；source_id：平台来源业务标识；title：待办标题快照；status：平台待办投影状态 | 16 | 0 |
| `un_plat_msg_template` | 平台消息模板草稿 | platform_id：模板所属平台主键；code：平台范围内的模板编码；name：模板显示名称；channel：通知发送渠道；draft_revision：当前草稿修订号；subject_template：消息主题模板；status：模板草稿或发布状态 | 14 | 0 |
| `un_plat_msg_template_version` | 平台消息模板不可变发布版本 | platform_id：所属平台主键；template_id：所属平台消息模板主键；version_number：模板发布版本号；draft_revision：被发布的草稿修订号；snapshot_json：发布时不可变的模板内容快照；published_by_account_id：发布模板的平台成员账号；publish_time：模板发布时间 | 8 | 0 |
| `un_plat_msg_message` | 平台自身通知消息及受限跳转目标 | platform_id：消息所属平台主键；template_version_id：模板消息的发布版本主键；平台内部事件消息为空；source_type：平台通知来源类别；source_id：来源事件去重键，为空时不参与去重；subject：发送时渲染的主题快照；content_text：发送时渲染的正文快照；target_type：平台目标类别，不得指向系统业务详情 | 13 | 0 |
| `un_plat_msg_recipient` | 平台消息成员收件箱及回执 | platform_id：所属平台主键；message_id：所属平台消息主键；account_id：接收平台成员账号主键；status：消息阅读状态；read_time：首次阅读时间；archive_time：归档时间 | 10 | 0 |
| `un_plat_msg_delivery` | 平台消息渠道投递及失败重试记录 | message_id：所属平台消息主键；account_id：投递目标账号主键；channel：投递渠道；destination_masked：脱敏后的投递目的地；status：投递状态；attempt_count：已执行的投递次数；next_attempt_time：下一次允许重试时间 | 16 | 0 |
| `un_plat_msg_user_preference` | 平台成员消息渠道和免打扰偏好 | platform_id：偏好所属平台主键；account_id：偏好所属平台成员账号主键；event_code：通知事件编码；channel：偏好对应投递渠道；enabled：是否启用此类通知；quiet_hours_json：免打扰时段定义 | 10 | 0 |
| `un_todo_item` | 系统租户内可处理待办投影 | platform_id：系统所属平台主键；system_id：待办所属系统主键；tenant_id：待办所属租户主键；context_type：固定为系统上下文；assignee_account_id：待办接收账号主键；assignee_member_id：接收人在当前系统中的成员主键；status：待办投影状态，不替代来源状态 | 20 | 2 |
| `un_msg_template` | 系统租户消息模板草稿 | platform_id：系统所属平台主键；system_id：模板所属系统主键；tenant_id：模板所属租户主键；context_type：固定为系统上下文；code：租户范围内的模板编码；name：模板显示名称；status：模板草稿或发布状态 | 18 | 0 |
| `un_msg_template_version` | 系统消息模板不可变发布版本 | system_id：模板所属系统主键；tenant_id：模板所属租户主键；template_id：所属消息模板主键；version_number：模板发布版本号；draft_revision：被发布的草稿修订号；snapshot_json：发布时不可变的模板内容快照；published_by_account_id：发布模板的系统成员账号 | 10 | 0 |
| `un_msg_message` | 系统租户通知消息及跳转目标快照 | platform_id：系统所属平台主键；system_id：消息所属系统主键；tenant_id：消息所属租户主键；context_type：固定为系统上下文；template_version_id：模板消息的发布版本主键；内部事件消息为空；source_type：通知来源业务类别；source_id：来源事件去重键，为空时不参与事件去重 | 17 | 6 |
| `un_msg_recipient` | 系统消息成员收件箱及回执 | system_id：所属系统主键；tenant_id：所属租户主键；message_id：所属系统消息主键；account_id：接收人的平台账号主键；system_member_id：接收人在系统中的成员主键；status：消息阅读状态；read_time：首次阅读时间 | 12 | 6 |
| `un_msg_delivery` | 系统消息渠道投递及失败重试记录 | message_id：所属系统消息主键；account_id：投递目标账号主键；channel：投递渠道；destination_masked：脱敏后的投递目的地；status：投递状态；attempt_count：已执行的投递次数；next_attempt_time：下一次允许重试时间 | 16 | 6 |
| `un_msg_user_preference` | 系统租户成员消息渠道和免打扰偏好 | platform_id：系统所属平台主键；system_id：偏好所属系统主键；tenant_id：偏好所属租户主键；account_id：偏好所属平台账号主键；system_member_id：偏好所属系统成员主键；context_type：固定为系统上下文；event_code：通知事件编码 | 14 | 0 |
| `un_todo_contact_rule` | 业务模块日期字段的今日联系待办生成规则 | platform_id：所属平台主键；system_id：所属系统主键；tenant_id：所属租户主键；context_type：固定为系统上下文，平台不承载模块数据联系规则；scope_key：平台、系统和租户组成的稳定配置范围键；name：规则显示名称；status：规则状态：草稿、已发布或已停用 | 20 | 0 |

### AI（12 张）

建表来源：[V13__ai.sql](../../db/init/V13__ai.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_ai_model` | 平台模型与受控密钥引用配置 | platform_id：模型所属平台标识；system_id：系统自建模型所属系统主键，平台模型为空；scope_owner_id：平台模型为平台主键，系统模型为系统主键；scope_type：模型配置归属层级，PLATFORM 或 SYSTEM；code：平台内模型编码；name：模型显示名称；status：模型启停状态 | 22 | 0 |
| `un_ai_system_model_grant` | 系统模型授权草稿与状态 | platform_id：授权所属平台标识；system_id：获授权系统标识；model_id：平台模型标识；usage_limit_json：系统可用额度与并发限制；status：授权状态；granted_by_account_id：授权平台账号标识；grant_time：授权时间 | 10 | 0 |
| `un_ai_system_model_grant_version` | 不可变系统模型授权版本 | platform_id：授权版本所属平台标识；grant_id：所属系统模型授权标识；version_number：授权发布版本号；policy_snapshot_json：授权额度、外发、脱敏及模型范围快照；published_by_account_id：发布授权的平台账号标识；published_time：授权版本发布时间 | 7 | 0 |
| `un_ai_agent` | 平台或系统 Agent 可编辑草稿 | platform_id：所属平台标识；system_id：系统 Agent 所属系统，平台 Agent 为空；owner_tenant_id：系统 Agent 所属租户，平台 Agent 为空；context_type：Agent 范围：PLATFORM 或 SYSTEM；model_id：使用的平台模型标识；model_grant_id：系统 Agent 使用的系统模型授权，平台 Agent 为空；status：Agent 草稿或发布状态 | 22 | 0 |
| `un_ai_agent_tool` | Agent 工具及显式访问范围草稿 | platform_id：工具所属平台标识；system_id：系统工具所属系统标识；owner_tenant_id：系统工具所属租户标识；agent_id：所属 Agent 标识；context_type：工具可用平台或系统范围；tool_type：查询、建议或写入工具类别；status：工具启停状态 | 20 | 0 |
| `un_ai_agent_version` | 不可变 Agent 发布配置、模型及策略快照 | platform_id：发布版本所属平台标识；system_id：系统发布版本所属系统标识；owner_tenant_id：系统发布版本所属租户标识；agent_id：所属 Agent 标识；context_type：发布版本平台或系统范围；model_id：发布时固定模型标识；model_grant_id：系统发布时固定模型授权标识 | 22 | 0 |
| `un_ai_agent_publication` | Agent 当前生效发布版本指针 | platform_id：所属平台标识；system_id：系统发布指针所属系统标识；owner_tenant_id：系统发布指针所属租户标识；agent_id：所属 Agent 标识；context_type：发布指针平台或系统范围；current_version_id：当前生效 Agent 版本标识；updated_by_account_id：更新发布指针的平台账号标识 | 12 | 0 |
| `un_ai_conversation` | 固定 Agent 版本与成员上下文的 AI 对话 | platform_id：对话所属平台标识；system_id：系统对话所属系统标识；tenant_id：系统对话所属租户标识；context_type：对话平台或系统范围；agent_id：对话使用 Agent 标识；agent_version_id：对话固定已发布 Agent 版本标识；status：对话状态 | 16 | 0 |
| `un_ai_message` | AI 对话消息与结构化识别内容 | conversation_id：所属 AI 对话标识；role_type：消息角色：用户、助手或工具；content_text：消息正文；structured_content_json：结构化识别结果或呈现内容；model_usage_json：模型调用用量与费用摘要；error_code：失败错误码 | 8 | 0 |
| `un_ai_execution` | 固定模型授权与权限策略快照的 AI 执行 | platform_id：执行所属平台标识；system_id：系统执行所属系统标识；tenant_id：系统执行所属租户标识；conversation_id：所属对话标识；context_type：执行平台或系统范围；agent_id：实际执行 Agent 标识；status：执行状态 | 25 | 0 |
| `un_ai_execution_step` | AI 执行步骤、工具调用与结果摘要 | execution_id：所属执行标识；agent_id：执行 Agent 标识，用于限制工具归属；step_number：本次执行内步骤序号；step_type：步骤类别；tool_id：使用的 Agent 工具标识；input_json：步骤输入摘要，不含密钥明文；status：步骤状态 | 12 | 0 |
| `un_ai_pending_write` | 人工确认前不得写入正式业务表的 AI 提议 | platform_id：待确认写入所属平台标识；system_id：系统写入的目标系统标识；tenant_id：系统写入的目标租户标识；execution_id：提出建议的 AI 执行标识；step_id：提出建议的执行步骤标识；context_type：待确认写入平台或系统范围；status：等待确认、拒绝或执行结果状态 | 23 | 0 |

### 文件（7 张）

建表来源：[V14__file.sql](../../db/init/V14__file.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_file_storage_backend` | 平台或系统的文件存储后端配置 | platform_id：后端所属平台；system_id：系统后端所属系统，平台后端为空；context_type：后端作用域：PLATFORM 或 SYSTEM；scope_owner_id：后端作用域主体，平台取平台标识，系统取系统标识；code：同一作用域内的后端编码；name：存储后端显示名称；status：后端启用状态 | 16 | 2 |
| `un_file_upload_session` | 大文件上传会话及目标范围 | platform_id：上传所属平台；system_id：系统上传所属系统，平台上传为空；tenant_id：系统上传所属租户，平台上传为空；context_type：上传作用域：PLATFORM 或 SYSTEM；storage_backend_id：实际写入的存储后端主键；uploader_account_id：上传人的平台账号主键；status：上传会话状态 | 19 | 3 |
| `un_file_upload_chunk` | 上传会话的分片校验与完成明细 | upload_session_id：所属上传会话主键；chunk_number：从零开始的分片序号；byte_offset：分片在完整文件中的起始字节位置；size_bytes：分片实际字节数；sha256：分片内容 SHA-256 校验值；status：分片接收和校验状态；completed_time：分片写入并校验完成时间 | 9 | 0 |
| `un_file_object` | 可被业务引用的文件对象元数据 | platform_id：文件所属平台；system_id：系统文件所属系统，平台文件为空；tenant_id：系统文件所属租户，平台文件为空；context_type：文件作用域：PLATFORM 或 SYSTEM；storage_backend_id：文件字节所在存储后端主键；upload_session_id：原始上传会话主键；生成的预览产物可为空；status：文件可用或删除状态 | 20 | 6 |
| `un_file_preview` | 文件预览状态及同范围衍生文件 | platform_id：预览所属平台；system_id：预览所属系统，平台预览为空；tenant_id：预览所属租户，平台预览为空；source_file_id：需要预览的原文件对象主键；preview_file_id：成功生成的预览文件对象主键；context_type：预览所属作用域：PLATFORM 或 SYSTEM；status：预览处理状态 | 12 | 0 |
| `un_file_reference` | 文件与业务对象的同范围引用关系 | platform_id：引用所属平台；system_id：引用所属系统，平台引用为空；tenant_id：引用所属租户，平台引用为空；file_id：被引用的文件对象主键；context_type：引用作用域：PLATFORM 或 SYSTEM；owner_type：引用目标类型，由业务服务核验目标身份和权限；owner_id：引用目标在该类型下的稳定业务标识 | 12 | 6 |
| `un_file_security_scan` | 文件安全扫描结果与历史 | file_id：被扫描文件对象主键；scanner：扫描器标识；scan_version：扫描规则或引擎版本；status：扫描任务状态和结果；result_code：扫描结果编码；result_detail_json：不含文件正文的扫描详情；start_time：扫描开始时间 | 10 | 6 |

### 导入导出（6 张）

建表来源：[V15__dataexchange.sql](../../db/init/V15__dataexchange.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_exchange_mapping` | 模块数据导入导出字段映射草稿 | system_id：所属系统主键；owner_tenant_id：映射配置所属租户主键；module_id：映射所属模块主键；code：模块内映射编码；name：映射名称；mapping_type：映射类型，IMPORT 或 EXPORT；status：草稿、已发布或停用等配置状态 | 16 | 2 |
| `un_exchange_mapping_version` | 不可变的模块导入导出映射发布版本 | owner_tenant_id：映射所属租户主键，用于校验发布人；module_id：映射所属模块主键；mapping_id：来源映射草稿主键；module_config_version_id：发布时引用的不可变模块配置版本；version_number：同一映射内递增的发布版本号；draft_revision：发布所依据的草稿修订号；snapshot_hash：映射快照摘要 | 11 | 3 |
| `un_exchange_mapping_publication` | 模块导入导出映射当前生效版本指针 | owner_tenant_id：映射所属租户主键；mapping_id：映射草稿主键；current_version_id：当前生效的不可变映射版本；updated_by_member_id：最近发布或回滚的系统成员主键 | 7 | 2 |
| `un_exchange_import_batch` | 固定模块、映射和权限版本的导入预检与执行批次 | system_id：执行系统主键；tenant_id：执行租户主键；module_id：导入目标模块主键；module_config_version_id：导入预检时固定的模块配置版本；mapping_id：导入字段映射主键；mapping_version_id：导入预检时固定的映射发布版本；status：预检、执行和回滚业务状态 | 24 | 1 |
| `un_exchange_import_row` | 导入逐行操作、前镜像及版本化回滚依据 | tenant_id：批次执行租户主键；batch_id：所属导入批次主键；module_id：批次目标模块主键；row_number：原文件中的行号；raw_json：原文件行内容快照；normalized_json：映射及预检后的字段值快照；status：预检、执行或回滚的行级状态 | 20 | 2 |
| `un_exchange_export_batch` | 固定数据范围、字段、权限和结果文件的异步导出批次 | system_id：执行系统主键；tenant_id：执行租户主键；module_id：导出来源模块主键；module_config_version_id：导出提交时固定的模块配置版本；permission_version_id：导出提交时固定的权限版本；mapping_id：采用已发布导出映射时的映射主键；status：导出业务状态 | 23 | 1 |

### 仪表盘与指标（11 张）

建表来源：[V16__analytics.sql](../../db/init/V16__analytics.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_ana_data_source` | 可复用数据源草稿及当前发布指针 | platform_id：所属平台主键；system_id：系统数据源所属系统主键；owner_tenant_id：租户扩展数据源所属租户主键，系统默认配置为空；context_type：运行上下文，PLATFORM 或 SYSTEM；scope_type：唯一归属层级，PLATFORM、SYSTEM 或 TENANT；scope_owner_id：归属层级对应的平台、系统或租户主键；status：草稿、已发布或停用状态 | 19 | 2 |
| `un_ana_data_source_version` | 不可变数据源发布版本 | scope_owner_id：发布时固定的归属主体主键；data_source_id：所属数据源主键；scope_type：发布时固定的归属层级；version_number：递增发布版本号；draft_revision：发布时采用的草稿修订号；definition_hash：数据源发布快照的 SHA-256 摘要；snapshot_json：不可变的数据源定义、输出字段与权限口径快照 | 10 | 2 |
| `un_ana_dashboard` | 平台、系统、模块或个人仪表盘草稿 | platform_id：所属平台主键；system_id：系统仪表盘所属系统主键；owner_tenant_id：租户扩展仪表盘所属租户主键；context_type：运行上下文，PLATFORM 或 SYSTEM；scope_type：唯一归属层级，PLATFORM、SYSTEM 或 TENANT；scope_owner_id：归属层级对应的平台、系统或租户主键；status：草稿、已发布或停用状态 | 19 | 2 |
| `un_ana_dashboard_component` | 可视化仪表盘组件草稿与固定数据源版本 | scope_owner_id：组件归属主体主键；dashboard_id：所属仪表盘主键；scope_type：组件归属层级，与仪表盘及数据源版本一致；component_key：仪表盘内稳定的组件编码；component_type：统计卡、图表、列表、待办、KPI 或快捷入口类型；title：组件显示标题；data_source_id：组件使用的数据源主键，无查询来源的组件可为空 | 19 | 2 |
| `un_ana_dashboard_version` | 不可变仪表盘发布版本及组件快照 | scope_owner_id：发布时固定的归属主体主键；dashboard_id：所属仪表盘主键；scope_type：发布时固定的归属层级；version_number：递增发布版本号；draft_revision：发布时采用的草稿修订号；snapshot_hash：仪表盘发布快照的 SHA-256 摘要；snapshot_json：不可变组件、布局及数据源版本快照 | 10 | 2 |
| `un_ana_dashboard_publication` | 运行态仪表盘发布和回滚指针 | dashboard_id：所属仪表盘主键；current_version_id：当前对运行态生效的仪表盘版本主键；updated_by_account_id：最后发布或回滚操作者账号主键 | 6 | 2 |
| `un_ana_dashboard_permission` | 仪表盘可见及编辑授权，实际数据权限仍由运行态逐次校验 | platform_id：权限所属平台主键；system_id：系统权限所属系统主键；tenant_id：系统角色或租户成员所属租户主键；dashboard_id：所属仪表盘主键；scope_type：权限归属层级，与仪表盘一致；scope_owner_id：权限归属主体主键；permission_code：VIEW 或 EDIT；运行时还需叠加数据和字段权限 | 15 | 0 |
| `un_ana_dashboard_cache` | 按发布版本、访问者、租户和权限口径隔离的仪表盘组件缓存 | platform_id：当前访问平台主键；system_id：当前访问系统主键；tenant_id：当前访问租户主键；dashboard_id：所属仪表盘主键；dashboard_version_id：缓存对应的不可变仪表盘发布版本主键；scope_type：仪表盘归属层级；scope_owner_id：仪表盘归属主体主键 | 19 | 0 |
| `un_ana_kpi` | 按发布数据源版本计算的 KPI 目标及提醒口径 | platform_id：所属平台主键；system_id：系统 KPI 所属系统主键；owner_tenant_id：租户 KPI 所属租户主键；context_type：运行上下文，PLATFORM 或 SYSTEM；scope_type：唯一归属层级，PLATFORM、SYSTEM 或 TENANT；scope_owner_id：归属层级对应的平台、系统或租户主键；status：草稿、启用或停用状态 | 24 | 0 |
| `un_ana_kpi_result` | 不可覆盖的 KPI 计算批次与重算历史 | scope_owner_id：计算结果归属主体主键；kpi_id：所属 KPI 目标主键；scope_type：计算结果归属层级，与 KPI 一致；period_key：月、季度或年的周期标识；dimension_key：人员、部门、角色或分组维度稳定键；calculation_batch_key：同一次重算中各维度共享的计算批次键；status：达标、未达标或计算失败状态 | 19 | 0 |
| `un_ana_kpi_reminder` | KPI 结果关联同上下文待办与消息的提醒投影 | platform_id：平台或系统所属平台主键；system_id：系统提醒所属系统主键；tenant_id：系统提醒实际投递租户主键；kpi_result_id：触发提醒的 KPI 计算结果主键；context_type：提醒投影上下文，PLATFORM 或 SYSTEM；platform_todo_id：平台待办投影主键；status：待发送、已发送、已确认或失败状态 | 19 | 0 |

### 打印（4 张）

建表来源：[V17__print.sql](../../db/init/V17__print.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_print_template` | 绑定业务模块的打印模板草稿 | system_id：模板所属系统主键；owner_tenant_id：模板配置所属租户主键；module_id：模板绑定的业务模块主键；code：模块内模板编码；name：模板展示名称；page_size：纸张尺寸；status：草稿、已发布或停用等模板状态 | 15 | 1 |
| `un_print_template_version` | 不可变打印模板发布版本与模块配置依赖 | owner_tenant_id：模板配置所属租户主键，用于校验发布人；module_id：模板绑定的业务模块主键；template_id：来源模板草稿主键；module_config_version_id：发布时固定的模块配置版本；version_number：同一模板内递增的发布版本号；draft_revision：发布所依据的草稿修订号；snapshot_hash：模板与字段引用快照摘要 | 11 | 1 |
| `un_print_template_publication` | 打印模板当前生效版本指针，可回滚至历史版本 | owner_tenant_id：模板配置所属租户主键；module_id：模板绑定的业务模块主键；template_id：模板草稿主键；current_version_id：当前生效的不可变模板版本；updated_by_member_id：最近发布或回滚的系统成员主键 | 8 | 1 |
| `un_print_job` | 绑定同模块模板版本与记录的打印结果审计 | system_id：执行系统主键；tenant_id：执行租户主键；module_id：目标业务模块主键；record_id：打印目标业务记录主键；record_version：生成快照时目标记录乐观锁版本；template_version_id：固定使用的不可变打印模板版本；status：生成中、成功或失败状态 | 19 | 2 |

### 运维（14 张）

建表来源：[V18__operations.sql](../../db/init/V18__operations.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_ops_secret_ref` | 受控密钥引用及当前版本，不保存密钥明文 | platform_id：所属平台主键；system_id：系统密钥所属系统主键；context_type：密钥归属层级，PLATFORM 或 SYSTEM；scope_owner_id：稳定归属主键，平台密钥为平台主键，系统密钥为系统主键；secret_code：同一归属内稳定的密钥引用编码；provider：受控密钥服务提供方；status：密钥引用的启停状态 | 14 | 0 |
| `un_ops_secret_rotation` | 受控密钥版本轮换与验证审计 | secret_ref_id：被轮换的受控密钥引用主键；from_version：轮换前密钥版本；to_version：待切换到的密钥版本；status：新版本验证、切换、旧版停用或回滚状态；verification_json：双版本验证与失败回滚的审计结果；requested_by_account_id：发起轮换的平台账号主键；start_time：轮换开始时间 | 11 | 0 |
| `un_ops_health_run` | 平台或系统健康检查运行及结果汇总 | platform_id：所属平台主键；system_id：系统级检查所属系统主键；context_type：检查层级，PLATFORM 或 SYSTEM；scope_owner_id：平台或系统的稳定归属主键；run_type：健康检查类型；release_version：检查时运行的产品版本；status：健康检查结果状态 | 12 | 7 |
| `un_ops_health_item` | 健康检查逐项结果 | health_run_id：所属健康检查运行主键；check_code：检查项稳定编码；check_name：检查项显示名称；category：检查项业务类别；status：检查项结果状态；message：结果说明和修复建议；metric_json：检查项数值与证据 | 9 | 35 |
| `un_ops_release` | 全局部署产物及兼容性版本目录 | version_name：全局唯一的产品发布版本号；artifact_hash：部署产物校验摘要；database_version：数据库结构版本；config_version：配置结构版本；compatibility_json：升级兼容性及环境要求；release_notes：发布内容和升级说明；status：发布版本状态 | 10 | 0 |
| `un_ops_deployment` | 多环境部署、验证及回滚证据 | release_id：目标产品发布版本主键；environment_code：开发、测试或生产等环境编码；deployment_type：部署或回滚操作类型；from_release_id：部署前产品发布版本主键；status：部署执行结果状态；step_state_json：部署步骤及校验结果；rollback_point_json：可恢复的回滚点信息 | 13 | 0 |
| `un_ops_backup` | 分范围备份任务及加密密钥引用 | platform_id：所属平台主键；system_id：系统备份所属系统主键；context_type：备份层级，PLATFORM 或 SYSTEM；scope_owner_id：平台或系统的稳定归属主键；backup_type：数据库、文件、配置或密钥策略等备份类型；status：备份执行结果状态；manifest_hash：备份清单校验摘要 | 15 | 0 |
| `un_ops_backup_item` | 备份清单内容及完整性校验 | backup_id：所属备份任务主键；item_type：数据库、文件、配置或模板等内容类别；storage_uri：备份内容的受控存储位置；size_bytes：备份内容字节大小；sha256：备份内容 SHA-256 校验摘要；status：备份内容校验状态 | 8 | 0 |
| `un_ops_restore_drill` | 备份恢复演练及可恢复性证据 | backup_id：被验证备份任务主键；environment_code：隔离恢复环境编码；status：恢复演练执行结果状态；verification_json：恢复完整性及业务验收结果；requested_by_account_id：发起演练的平台账号主键；start_time：恢复演练开始时间；finish_time：恢复演练结束时间 | 10 | 0 |
| `un_ops_upgrade` | 有备份和回滚点的产品升级任务 | platform_id：所属平台主键；system_id：系统级升级所属系统主键；context_type：升级层级，PLATFORM 或 SYSTEM；release_id：升级目标发布版本主键；backup_id：升级前同范围备份任务主键；scope_owner_id：平台或系统的稳定归属主键；status：升级执行结果状态 | 15 | 0 |
| `un_ops_upgrade_step` | 升级步骤及失败处置证据 | upgrade_id：所属升级任务主键；step_number：升级任务内执行顺序号；step_type：数据库、配置、页面、流程或权限升级步骤类型；source_version：步骤执行前版本；target_version：步骤目标版本；mapping_json：配置或数据升级映射及校验依据；status：步骤执行结果状态 | 11 | 0 |
| `un_ops_verification_run` | 可复现的产品功能与部署验收记录 | platform_id：所属平台主键；system_id：系统验收所属系统主键；context_type：验收层级，PLATFORM 或 SYSTEM；verification_type：验收类型，如部署、升级或业务场景；scope_owner_id：平台或系统的稳定归属主键；release_id：验收对应的产品发布版本主键；status：验收结果状态 | 15 | 0 |
| `un_ops_verification_finding` | 验收发现的问题及整改证据 | verification_run_id：所属验收运行主键；severity：问题严重程度；finding_code：问题稳定编码；title：问题标题；detail_text：复现步骤、预期与实际结果；evidence_json：日志、截图或读回数据证据引用；status：问题处理状态 | 12 | 0 |
| `un_ops_request_log` | 不含敏感请求内容的调用观测与追踪记录 | system_id：系统上下文主键，平台请求为空；tenant_id：租户上下文主键，平台或系统公共请求为空；request_id：对外请求标识，用于定位一次调用；trace_id：跨服务调用链追踪标识；account_id：已识别的平台账号主键，匿名请求为空；client_source：发起请求的客户端渠道；http_method：HTTP 请求方法 | 13 | 6565 |

### 旧打印兼容（1 张）

建表来源：[V22__print_legacy_upgrade.sql](../../db/update/V22__print_legacy_upgrade.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_print_legacy_template` | 升级前已发布打印模板的限定兼容清单 | system_id：业务模块所属系统主键；owner_tenant_id：业务模块配置所属租户主键；module_id：升级时已有打印模板的业务模块主键；template_id：升级时已发布的打印模板主键；source_module_version_id：升级时固定的模块发布版本主键；retired_time：模块正式发布打印动作后的兼容退出时间 | 7 | 0 |

### 旧首页兼容（1 张）

建表来源：[V23__module_dashboard_legacy.sql](../../db/update/V23__module_dashboard_legacy.sql)。

| 表 | 作用（表备注） | 主要字段（字段备注） | 字段数 | 当前行数 |
| --- | --- | --- | ---: | ---: |
| `un_ana_legacy_module_dashboard` | 升级前已发布模块首页的限定兼容清单 | system_id：模块与首页共同所属系统主键；tenant_id：首页归属租户主键，不向其他租户回退；dashboard_id：升级前已发布的模块首页主键；module_id：首页绑定的当前业务模块主键；source_module_version_id：升级时模块当前发布版本主键，用于核对来源；source_dashboard_version_id：升级时首页当前发布版本主键，用于核对来源 | 7 | 0 |

### Flyway 自身

`flyway_schema_history`：数据库迁移版本记录，非产品业务表；当前 24 行。

## 核对结果

- 190 张 SQL 建表与候选库中 190 张 un_ 表一一对应；额外一张为 flyway_schema_history。
- 190 张表和当前 2523 个字段的备注均含中文；当前候选库没有发现以 _at 结尾的时间列。
- 本清单未修改数据库、表结构、Java 或页面；仅做 SQL 解析和数据库只读计数。
