# unexamine 数据库设计

## 版本与输入边界

本文件为 DBA-005 汇总产物，基于冻结 API、冻结任务计划和 DBA-001 至 DBA-004 分片生成。当前只输出数据库设计文档，不生成 SQL；`sql/init.sql` 由 DBA-006 在本设计冻结后生成。

输入来源限定为 DBA-005 任务声明的文件：`docs/tasks/DBA-005-seed-index-constraint-design.md`、`docs/prd.md`、`docs/project_understanding.md`、`docs/legacy_reference.md`、`docs/api.md`、`docs/api_review.md`、`docs/task_plan.md`、`docs/db_design_parts/*.md`、`docs/service_info.md` 和 `.codex/state.json`。

设计结论：API 已冻结，DB 设计可进入 SQL 初始化任务；新建表统一以 `un_` 开头，按业务模块前缀拆分。旧项目 `un_app_*` 只作为 OpenAPI 历史表域参考，不进入 MVP 新建表和代码生成映射。

## 一、表与功能映射

| 表名 | 功能模块 | 描述 |
|------|----------|------|
| `un_plat_account` | 平台账号 | 全局登录主体，承载登录名、密码哈希、状态和安全字段。 |
| `un_plat_system` | 平台系统 | 自定义系统容器，承载系统编码、租户模式、创建人和状态。 |
| `un_plat_tenant` | 平台租户 | 系统下租户，单租户系统也初始化默认租户。 |
| `un_plat_role` | 平台 RBAC | 平台中心角色。 |
| `un_plat_menu` | 平台 RBAC | 平台中心菜单树。 |
| `un_plat_operation` | 平台 RBAC | 平台中心操作权限点。 |
| `un_plat_account_role` | 平台 RBAC | 平台账号与平台角色关联。 |
| `un_plat_role_menu` | 平台 RBAC | 平台角色与菜单授权关联。 |
| `un_plat_role_operation` | 平台 RBAC | 平台角色与操作权限授权关联。 |
| `un_plat_config` | 平台配置 | 密码策略、会话策略、文件存储、OpenAPI 全局策略和审计保留配置。 |
| `un_module_member` | 系统成员 | 平台账号在系统内的成员扩展，不是独立登录账号。 |
| `un_module_member_tenant` | 系统成员 | 成员可访问租户集合。 |
| `un_module_dept` | 系统组织 | 系统内部门树。 |
| `un_module_member_dept` | 系统组织 | 成员与部门关联。 |
| `un_module_role` | 系统 RBAC | 系统内角色，含系统超级管理员。 |
| `un_module_member_role` | 系统 RBAC | 成员与系统角色关联。 |
| `un_module_system_menu` | 系统 RBAC | 系统管理菜单和运行菜单授权目录。 |
| `un_module_system_operation` | 系统 RBAC | 系统内操作权限目录。 |
| `un_module_role_menu` | 系统 RBAC | 系统角色菜单授权。 |
| `un_module_role_operation` | 系统 RBAC | 系统角色操作授权。 |
| `un_module_role_field_permission` | 系统 RBAC | 字段可见、可写、导出明文和 OpenAPI 读写授权。 |
| `un_module_role_data_scope` | 系统 RBAC | 系统角色数据范围规则。 |
| `un_module_role_openapi_scope` | 系统 RBAC | 系统角色可授权 OpenAPI scope 边界。 |
| `un_module_role_explicit_deny` | 系统 RBAC | 显式禁用权限项，优先于授权并集。 |
| `un_module_permission_version` | 系统 RBAC | 权限缓存版本。 |
| `un_module_dict_type` | 系统字典 | 系统级或租户级字典类型。 |
| `un_module_dict_item` | 系统字典 | 字典项，支持层级和内置只读。 |
| `un_module_dict_reference` | 系统字典 | 字典被字段、发布版本、记录值引用的摘要。 |
| `un_module_app` | 应用配置 | 系统/租户下业务应用主表。 |
| `un_module_app_version` | 应用配置 | 应用级配置版本和发布快照摘要。 |
| `un_module_model` | 模块建模 | 动态模块主表。 |
| `un_module_field` | 字段配置 | 模块字段定义、校验、唯一、关联、子表和自动编号配置。 |
| `un_module_field_option` | 字段配置 | 单选、多选、标签等字段静态选项。 |
| `un_module_unique_constraint` | 字段配置 | 组合唯一约束配置。 |
| `un_module_page_schema` | 页面配置 | 列表、表单、详情 schema 草稿。 |
| `un_module_menu` | 菜单配置 | 运行菜单和模块入口配置。 |
| `un_module_action` | 动作配置 | 模块按钮、行操作、详情操作和导出入口动作。 |
| `un_module_publish_version` | 发布版本 | 模块发布版本快照，运行态只读。 |
| `un_module_serial_sequence` | 自动编号 | 自动编号字段的事务内原子序号段。 |
| `un_module_record` | 运行记录 | 动态业务记录主表。 |
| `un_module_record_value` | 运行记录 | EAV 字段值 typed columns 和展示快照。 |
| `un_module_record_index` | 运行记录 | 动态字段查询、排序、筛选 typed index。 |
| `un_module_record_unique_index` | 运行记录 | 字段级唯一和组合唯一 typed hash 索引。 |
| `un_module_record_relation` | 运行记录 | 关联字段保存的记录间关系。 |
| `un_module_record_child_row` | 运行记录 | 子表字段行数据和行顺序。 |
| `un_module_record_history` | 运行记录 | 记录变更、状态、附件和发布版本历史快照。 |
| `un_module_export_template` | 导出 | 导出模板主表。 |
| `un_module_export_template_field` | 导出 | 导出模板字段列、顺序和脱敏配置。 |
| `un_module_export_job` | 导出 | 导出任务、筛选快照、权限快照、结果文件引用和重试状态。 |
| `un_module_export_job_log` | 导出 | 导出任务状态流转、领取、失败和重试日志。 |
| `un_flow_template` | 流程配置 | 流程模板主表。 |
| `un_flow_template_version` | 流程配置 | 流程发布版本和结构快照。 |
| `un_flow_template_node` | 流程配置 | 发布版本内节点结构。 |
| `un_flow_template_line` | 流程配置 | 发布版本内连线结构。 |
| `un_flow_template_condition` | 流程配置 | 连线条件表达式结构化存储。 |
| `un_flow_binding` | 流程配置 | 模块提交动作与流程版本绑定。 |
| `un_flow_instance` | 流程运行 | 业务记录发起后的流程实例。 |
| `un_flow_task` | 流程运行 | 待办任务、领取、处理和并发版本控制。 |
| `un_flow_task_actor` | 流程运行 | 任务候选人、处理人和转交目标。 |
| `un_flow_cc` | 流程工作台 | 流程抄送与已读状态。 |
| `un_flow_action_log` | 流程审计 | 审批动作日志。 |
| `un_flow_trace_log` | 流程审计 | 流程推进轨迹日志。 |
| `un_upload_storage_config` | 文件存储 | 存储配置和默认存储策略。 |
| `un_upload_file` | 上传文件 | 文件元数据、临时状态、对象存储定位和安全属性。 |
| `un_upload_file_part` | 上传文件 | 分片上传预留表。 |
| `un_upload_file_reference` | 文件引用 | 文件与业务对象、动态字段、导出结果之间的引用关系。 |
| `un_openapi_client` | OpenAPI 管理 | 外部客户端，绑定系统、租户和状态。 |
| `un_openapi_client_credential` | OpenAPI 安全 | AK/SK 凭证、密钥密文、轮换和过期状态。 |
| `un_openapi_client_scope` | OpenAPI 授权 | scope、模块、动作、字段读写权限和数据范围。 |
| `un_openapi_ip_whitelist` | OpenAPI 安全 | IP/CIDR 白名单。 |
| `un_openapi_nonce` | OpenAPI 防重放 | nonce 去重和 TTL。 |
| `un_openapi_idempotency_record` | OpenAPI 幂等 | 外部写接口幂等记录、请求摘要和结果快照。 |
| `un_openapi_rate_limit_policy` | OpenAPI 限流 | 客户端限流策略。 |
| `un_openapi_rate_limit_counter` | OpenAPI 限流 | 限流窗口计数。 |
| `un_openapi_access_log` | OpenAPI 审计 | 外部调用日志。 |
| `un_sys_idempotency_record` | 通用幂等 | 内部写接口幂等记录。 |
| `un_sys_request_log` | 请求日志 | 内部请求日志。 |
| `un_sys_error_log` | 错误日志 | 错误码、栈摘要和 requestId。 |
| `un_audit_operation_log` | 操作审计 | 平台、系统、运行、流程、文件和 OpenAPI 操作审计。 |
| `un_audit_record_change` | 业务审计 | 动态记录字段变更前后快照。 |
| `un_sys_health_check_result` | 运维配置 | 健康检查结果。 |
| `un_sys_runtime_config_check` | 运维配置 | 运行配置检查结果。 |
| `un_sys_migration_status` | 运维配置 | DB migration 状态查询落点。 |

## 二、模块表前缀与命名规则

- 所有新表统一以 `un_` 开头，后接模块前缀；禁止无模块前缀的平铺表名。
- `un_plat_`：平台、租户、账号、平台角色、平台权限、系统上下文和平台配置。
- `un_module_`：动态模块、模型、字段、页面、菜单、系统成员扩展、系统内 RBAC、字典、记录、导出和数据权限。
- `un_flow_`：流程模板、版本、实例、任务、审批日志和流程运行轨迹。
- `un_upload_`：文件、附件、临时文件、分片、存储配置和业务引用。
- `un_openapi_`：开放接口客户端、凭证、scope、白名单、nonce、幂等、限流和调用日志。
- `un_sys_` / `un_audit_`：通用幂等、请求日志、错误日志、健康检查、migration 状态、操作审计和业务变更审计。
- 禁止 `un_platt_`。旧项目 `un_app_*` 仅作为 OpenAPI 历史表域参考，不进入 MVP 新建表和生成器映射；业务应用、应用版本归 `un_module_`，OpenAPI 后端模块 `examine-app` 的表归 `un_openapi_`。
- 平台账号、系统成员扩展和系统内授权三类对象必须分表表达：`un_plat_account` 是唯一登录主体；`un_module_member` 是账号在某个系统中的成员扩展；`un_module_role_*` 表达系统内权限。
- 跨模块关系默认使用逻辑外键，不在 MVP 直接创建跨域物理外键，避免代码生成、迁移和后台任务处理被跨域锁放大。

## 三、字段说明

字段级设计按表分别在本文后续“字段级设计分片全文”中完整保留 DBA-002、DBA-003、DBA-004 的字段表。全局统一规则如下：

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
|------|------|----------|--------|------|
| `id` 或本表业务 ID | BIGINT / BIGINT UNSIGNED | 是 | 无 | 主键，雪花 ID 或等价全局 ID；API 序列化为字符串。 |
| `created_at` | DATETIME(3) | 是 | CURRENT_TIMESTAMP(3) | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) | 更新时间。 |
| `created_by` | BIGINT | 否 | NULL | 创建人；平台域为平台账号，系统域为系统成员，seed 或系统任务可为空。 |
| `updated_by` | BIGINT | 否 | NULL | 更新人；平台域为平台账号，系统域为系统成员。 |
| `version` / `version_no` / `task_version` | INT / BIGINT | 按表 | 0 或 1 | 乐观锁或缓存版本。 |
| `deleted_flag` / `deleted` | TINYINT | 按表 | 0 | 逻辑删除标记。 |
| `delete_token` / `delete_marker` / `active_unique_marker` | BIGINT / VARCHAR | 按表 | 0 / `0` / `ACTIVE` | 软删除唯一复用标记。 |
| `system_id` | BIGINT | 系统内表必填 | 无 | 自定义系统 ID，用于系统级隔离。 |
| `tenant_id` | BIGINT | 按表 | 0 或 NULL | 租户 ID；系统级共享记录使用 0 或 NULL，具体规则见各表字段。 |

### 字段说明使用约定

1. 字段枚举均使用见名知义的大写英文值，例如 `ENABLED`、`DISABLED`、`PROCESSING`、`SUCCESS`。
2. 敏感字段只保存哈希、密文或密钥引用；默认账号密码、OpenAPI secret 明文和对象存储密钥不得落入本文或生产 SQL。
3. JSON 字段保存结构化快照，必须用于历史解释、权限快照、发布版本快照、失败快照或配置对象；不得用于逃避核心查询索引。
4. 动态记录值不向 API 暴露 EAV 物理表；后端在 `values[]` 与 `un_module_record_value/index/unique_index/relation/child_row` 之间转换。

## 四、表关系

- 平台账号与系统成员：`un_plat_account.id` 一对多 `un_module_member.account_id`；同一系统内 `system_id + account_id` 唯一。
- 系统与租户：`un_plat_system.id` 一对多 `un_plat_tenant.system_id`；单租户系统也有默认租户 `default`。
- 平台 RBAC：平台账号通过 `un_plat_account_role` 关联平台角色；平台角色通过 `un_plat_role_menu`、`un_plat_role_operation` 获得平台中心权限。
- 系统 RBAC：系统成员通过 `un_module_member_role` 关联角色；角色通过菜单、操作、字段权限、数据范围、OpenAPI scope 和显式禁用表共同计算权限。
- 组织关系：`un_module_dept` 自关联形成部门树；`un_module_member_dept` 表达成员与部门多对多。
- 字典关系：`un_module_dict_type` 一对多 `un_module_dict_item`；`un_module_dict_reference` 记录字段配置、发布版本和运行记录值引用摘要。
- 应用与模块：`un_module_app` 一对多 `un_module_model`；应用和模块均有版本或发布快照表。
- 模块与字段/页面/动作：`un_module_model` 一对多 `un_module_field`、`un_module_page_schema`、`un_module_action`；发布后写 `un_module_publish_version`。
- 运行记录：`un_module_record` 一对多 `un_module_record_value`、`un_module_record_index`、`un_module_record_unique_index`、`un_module_record_relation`、`un_module_record_child_row`、`un_module_record_history`。
- 导出：`un_module_export_template` 一对多 `un_module_export_template_field`；`un_module_export_job` 一对多 `un_module_export_job_log`，结果文件逻辑引用 `un_upload_file`。
- 流程：`un_flow_template` 一对多 `un_flow_template_version`；版本一对多节点、连线和条件；`un_flow_binding` 绑定模块提交动作；`un_flow_instance` 一对多任务、抄送、动作日志和轨迹日志。
- 文件：`un_upload_file` 一对多 `un_upload_file_reference` 和 `un_upload_file_part`；引用表逻辑关联动态记录、字段、导出任务或流程评论。
- OpenAPI：`un_openapi_client` 一对多凭证、scope、白名单、限流策略和访问日志；nonce、幂等、限流计数独立承载安全状态。
- 审计链路：`request_id` 串联 `un_sys_request_log`、`un_sys_error_log`、`un_audit_operation_log`、`un_audit_record_change`、`un_openapi_access_log` 以及流程/导出/上传日志。

## 五、索引与约束

### 5.1 主键

每张表使用雪花 ID 或等价全局 ID 作为主键。DBA-003 分片中使用 `app_id`、`module_id`、`field_id`、`record_id` 等业务主键命名的表，SQL 阶段应保持分片字段命名，不额外再增加无意义 `id` 字段；其它表使用 `id`。

### 5.2 唯一约束总规则

- 平台账号登录名全局唯一：`uk_plat_account_login_name(login_name, delete_token)`。
- 系统编码全局唯一：`uk_plat_system_code(code, delete_token)`。
- 租户编码同系统唯一：`uk_plat_tenant_system_code(system_id, code, delete_token)`。
- 系统成员唯一：`uk_module_member_system_account(system_id, account_id, delete_token)`；成员编码同系统唯一。
- 系统内组织、角色、菜单、操作、字典、应用、模块、字段等均以 `system_id`、必要的 `tenant_id`、父级或所属对象、业务 `code` 和软删标记构造唯一约束。
- 动态字段唯一和组合唯一统一写入 `un_module_record_unique_index`，唯一键为 `system_id + tenant_id + module_id + constraint_code + combined_value_hash + active_unique_marker`。
- 自动编号序号作用域唯一：`system_id + tenant_id + module_id + field_id + scope_key`。
- 导出任务创建幂等唯一：`system_id + created_by + idempotency_key`。
- 流程模板、绑定、节点、连线、任务候选人等按分片定义建立版本内或业务作用域唯一。
- OpenAPI 幂等记录唯一：`scope_key`；nonce 唯一维度为 `client_id + access_key + nonce`；限流窗口唯一为 `policy_id + dimension_key + window_start_at`。
- 内部幂等记录唯一：`scope_key`。

### 5.3 nullable 唯一规则

MySQL 的 nullable 唯一索引允许多条 `NULL`，因此本设计禁止依赖 nullable 字段表达关键业务唯一性。规则如下：

1. 根节点统一使用 `parent_id=0`，不使用 `NULL`。
2. 系统级共享字典或角色使用 `tenant_id=0` 或 `scope_tenant_id=0`，不使用 `NULL` 参与唯一键。
3. 字段级唯一中 `NULL`、空字符串、空数组默认跳过唯一校验，不向唯一索引表写入冲突键；必填字段先返回必填错误。
4. 对确需可空引用的菜单来源、模块来源、流程来源等字段，不把可空字段单独放入业务唯一约束，改以来源类型、来源 ID 归一化或由后端校验。
5. 软删除复用不依赖 `deleted=1` 一个布尔值；需要复用的表使用 `delete_token/delete_marker/active_unique_marker`，未删除固定为 0、`0` 或 `ACTIVE`，删除后写本行 ID 或删除批次。

### 5.4 逻辑删除、租户维度与历史数据

- 配置态表允许软删除后复用编码；软删除必须同时设置删除标记并保留历史解释所需快照。
- 运行记录软删除时状态进入 `DELETED`，唯一索引释放；恢复时必须重新校验当前 `ACTIVE` 唯一值。
- 字典删除为软删除终态 `DELETED`，被引用字典项不得直接删除或停用到影响历史解释。
- 租户维度默认贯穿系统内应用、模块、记录、导出、流程运行、文件和 OpenAPI；系统级共享配置使用 `tenant_id=0` 或 `NULL`，必须在字段说明中固定。
- 历史数据不强制回填未明确要求的字段；后续迁移若无法确定值，应允许为空并在迁移报告中记录来源和默认策略。

### 5.5 幂等键、处理中与回放快照

- 内部写接口使用 `un_sys_idempotency_record`；OpenAPI 写接口使用 `un_openapi_idempotency_record`。
- 幂等 scope 必须由调用主体、系统、租户、API ID、业务动作和幂等键归一化生成，不能只按裸 `idempotency_key` 判断。
- 首次请求插入 `PROCESSING`，保存 `request_hash`、`request_id` 和 `expires_at`；相同 scope 且 hash 相同的重复请求返回 `PROCESSING` 或回放 `SUCCESS` 快照。
- 相同 scope 但 hash 不同返回 `COMMON_IDEMPOTENCY_CONFLICT` 或 `OPENAPI_IDEMPOTENCY_CONFLICT`，不得覆盖原请求。
- 成功后写 `SUCCESS` 和 `result_snapshot_json`；确定性失败可写失败快照；存储不可用、流程引擎不可用、文件生成失败等可重试失败不得以成功快照回放。
- 幂等记录过期清理只删除判定记录和快照引用，不删除业务结果。

### 5.6 自动编号和并发策略

- 自动编号使用 `un_module_serial_sequence`，按 `system_id + tenant_id + module_id + field_id + scope_key` 定位序号行。
- 生成序号必须在业务保存事务内通过原子 `UPDATE current_value = current_value + step_value`、乐观锁版本或行锁完成，再读取更新后的值。
- 禁止使用“查询当前最大记录编号 + 1”保存；并发场景必须覆盖多线程同时创建记录、失败回滚和幂等重放。
- 自动编号字段生成后写入记录字段值、索引值、唯一索引和历史快照；更新逻辑默认不重算自动编号，除非 API 明确提供重算语义。

### 5.7 普通索引

普通索引以 API 查询路径、数据范围和审计检索为核心：

- 系统/租户/状态/排序索引用于平台系统、租户、应用、模块、字段、菜单、字典、流程模板和导出模板列表。
- `system_id + tenant_id + module_id + record_status + updated_at` 用于运行台列表。
- `module_id + field_id + typed_value` 用于动态字段筛选、排序和范围查询。
- `request_id` 在请求日志、错误日志、流程日志、导出日志、上传、OpenAPI 调用和审计表中建立索引。
- OpenAPI 调用日志按 `client_id + created_at`、`system_id + tenant_id + created_at`、`error_code + created_at` 建索引。
- 大日志表后续可按时间分区或归档，但 MVP 初始化 SQL 先给出普通索引。

## 六、初始化数据

### 6.1 生产建库 seed

生产 `init.sql` 只应包含可上线使用的基础 seed，不写测试样例数据，不写明文默认密码。

必须初始化：

- 默认平台管理员账号：`login_name=platform_admin`，`status=NORMAL`，`first_login_change_pwd=1`，密码使用安全占位哈希或部署变量注入，不写明文。
- 平台角色：`PLAT_SUPER_ADMIN`、`PLAT_ADMIN`、`PLAT_AUDITOR`。
- 平台菜单：`PLAT_MY_SYSTEM`、`PLAT_SYSTEM`、`PLAT_TENANT`、`PLAT_ACCOUNT`、`PLAT_ROLE`、`PLAT_CONFIG`、`PLAT_HEALTH`、`PLAT_AUDIT_LOG`、`PLAT_VERSION`、`PLAT_OPENAPI_POLICY`。
- 平台操作权限：围绕系统创建/查看/状态、账号查看/创建/状态、角色查看/授权、配置查看/编辑、审计查看、运维查看建立。
- 平台配置：`SECURITY_PASSWORD_POLICY`、`SESSION_POLICY`、`FILE_STORAGE_DEFAULT`、`OPENAPI_GLOBAL_POLICY`、`AUDIT_RETENTION_POLICY`。
- 字段类型元数据：`TEXT`、`TEXTAREA`、`NUMBER`、`MONEY`、`DATE`、`DATETIME`、`SELECT`、`MULTI_SELECT`、`SWITCH`、`MEMBER`、`DEPT`、`ATTACHMENT`、`IMAGE`、`AUTO_NO`、`RELATION`、`SUB_TABLE`、`ADDRESS`、`TAG`、`JSON`。字段类型 metadata 由模块字段表或平台配置承接，供创建系统时引用。
- 默认平台管理员绑定 `PLAT_SUPER_ADMIN`；`PLAT_SUPER_ADMIN` 绑定全部平台菜单和平台操作。

不得进入生产 seed：演示系统、演示租户、演示部门、演示成员、演示角色、演示授权、用户自定义字典、OpenAPI 测试客户端、业务样例应用/模块/记录/流程/附件/导出任务。

### 6.2 创建系统事务初始化

`PLAT-002` 创建系统必须在一个业务事务内完成以下初始化，任一步失败整体回滚：

1. 写 `un_plat_system`，记录系统编码、名称、租户模式、创建人和初始状态。
2. 写默认租户 `un_plat_tenant(code=default,status=ENABLED)`，并回填 `un_plat_system.default_tenant_id`。
3. 写创建人的 `un_module_member` 成员扩展，唯一键为 `system_id + account_id`，并绑定默认租户 `un_module_member_tenant`。
4. 创建系统超级管理员角色 `un_module_role(code=SYS_SUPER_ADMIN,protected_flag=1,status=ENABLED)`。
5. 写 `un_module_member_role`，将创建人成员绑定 `SYS_SUPER_ADMIN`。
6. 初始化系统默认菜单和操作权限目录：`un_module_system_menu`、`un_module_system_operation`。
7. 写系统超级管理员的菜单、操作、字段、数据范围和 OpenAPI scope 授权：`un_module_role_menu`、`un_module_role_operation`、`un_module_role_field_permission`、`un_module_role_data_scope`、`un_module_role_openapi_scope`。
8. 初始化 `un_module_permission_version(version_no=1)`。
9. 创建默认应用 `un_module_app(code=default_app,status=DRAFT)`，归属默认租户。
10. 建立字段类型引用，供模块字段配置使用。

API 响应必须返回 `initializedObjects`，包含对象类型、编码、ID、状态，供 test 断言。事务必须配合内部幂等键，重复请求按幂等规则回放或返回处理中/冲突。

## 七、设计说明

### 7.1 为什么拆表

- 平台账号、系统成员和系统内授权职责不同，拆表可以避免把登录主体误当组织成员，也避免平台超级管理员绕过系统权限直接写业务数据。
- 动态模块配置态、发布态和运行态拆表，保证运行记录、流程实例、导出任务和历史快照能按发布版本解释。
- EAV 值、typed index、unique index、relation、child row 和 history 拆表，分别解决动态字段存储、查询性能、唯一性、关联、子表和审计解释问题。
- 流程模板、版本、节点、连线、条件、实例和任务拆表，避免仅靠流程 JSON 运行，支撑发布检查、待办并发和历史轨迹。
- 文件主表和引用表拆开，支持临时文件、引用计数、业务权限下载和过期清理。
- OpenAPI 安全对象拆为客户端、凭证、scope、白名单、nonce、幂等、限流和日志，避免安全状态混入业务应用或内部登录态。
- 审计和运维日志独立，支撑 requestId 串联、错误排查和平台只读运维。

### 7.2 为什么这样建索引

- 唯一约束围绕业务自然键和软删除复用设计，保证配置编码、成员身份、动态字段唯一值和幂等 scope 可由 DB 防重复。
- 普通索引优先覆盖冻结 API 的列表查询、状态筛选、数据范围、任务领取、OpenAPI 审计和 requestId 检索。
- 动态字段查询使用 typed index，避免所有筛选都扫 EAV JSON 或 `value_text`。
- 幂等、nonce、限流窗口、自动编号序号行使用唯一键和原子更新承载并发控制。

### 7.3 冗余字段

- 多数系统内表冗余 `system_id`、`tenant_id`，用于隔离、查询和权限校验，减少跨表 join。
- `operation_code`、`field_code`、`display_name_snapshot`、`field_snapshot_json`、`permission_snapshot_json` 等快照字段用于历史解释、审计和异步任务，不作为唯一事实源。
- `item_count`、`enabled_item_count`、`ref_count`、`module_count` 等计数字段为列表性能冗余，写操作必须同事务维护。

### 7.4 旧项目可参考表与不沿用原因

| 旧项目方向 | 可参考内容 | 新结构 | 不沿用或调整原因 |
| --- | --- | --- | --- |
| 平台 `un_plat_*` | 账号、系统、租户、平台配置、平台 RBAC 领域拆分 | 保持 `un_plat_`，补安全字段、角色菜单操作、系统创建事务 | 不能把平台账号直接当系统成员；旧 CRUD 接口不作为新 API。 |
| 模块 `un_module_*` | 应用、模型、字段、页面、菜单、成员、部门、权限、记录、EAV、导出 | 统一为 `un_module_`，补发布版本、typed index、唯一索引、权限快照和导出任务闭环 | 旧运行时 schema repair、EAV 补丁、权限颗粒度不足，不能直接照搬。 |
| 流程 `un_flow_*` | 旧 DDL 中模板、版本、节点、连线、记录、任务、日志拆分 | 采用 `template/version/instance/task` 清晰术语 | 旧实体与 Flyway DDL 不一致，不能从实体包反推新表；旧任务字段曾补丁对齐。 |
| 上传 `un_upload_*` | 文件主表、分片、存储配置 | 补文件引用、临时/已引用/删除状态、引用计数、对象存储安全字段 | 旧设计缺业务引用和权限下载边界。 |
| OpenAPI 历史 `un_app_*` | client、credential、scope、IP 白名单、access log 方向 | 重命名并重构为 `un_openapi_*` | `un_app_*` 容易与业务应用 `un_module_app` 混淆；旧凭证字段靠补丁补齐，安全字段不足。 |
| Flyway/SQL migration | 分模块组织 migration 的思路 | DBA-006 生成完整初始化 SQL，后续 migration 显式演进 | 不沿用运行时静默补 schema、baseline 跳版本、手工标记 migration 成功。 |

### 7.5 新旧结构差异与迁移注意事项

- OpenAPI 历史数据迁移必须显式从旧 `un_app_*` 读出并写入新 `un_openapi_*`，不得在新库保留双前缀并行。
- 旧 flow 缩写表和实体未确认表不直接进入新库；如迁移历史流程，只能按旧 DDL 确认表映射到新 `template/version/instance/task` 结构，并保留迁移映射报告。
- 旧动态记录 `un_module_record_data` 迁移到新 `un_module_record_value`、`un_module_record_index`、`un_module_record_unique_index` 时，应按字段类型重建 typed columns 和唯一 hash；无法识别的历史字段允许保留展示快照，不强制补唯一索引。
- 历史空值不强制回填；新增唯一索引前必须先清理或隔离冲突数据，空值唯一默认跳过。
- 旧运行时 schema repair 只能作为风险清单，不作为新库初始化策略。DBA-006 的 `init.sql` 应能在未建表情况下直接导入建库建表。
- 迁移凭证时不得生成或回填明文 secret；只能迁移哈希、密文、maskedSecret 或要求重新轮换。
- 日志和审计大表迁移可按时间分批，保留 requestId、operator、systemId、tenantId、bizType、bizId 和错误码优先。

### 7.6 API 数据落点汇总

| 场景 | API 分组 | 数据落点 | 事务边界 |
| --- | --- | --- | --- |
| 认证/当前用户 | AUTH | `un_plat_account`、请求/登录审计 | 平台账号是唯一登录主体。 |
| 创建系统 | PLAT-002 | `un_plat_system`、`un_plat_tenant`、`un_module_member`、系统 RBAC、默认应用、权限版本 | 单事务，失败整体回滚。 |
| 平台账号角色配置 | PLAT | `un_plat_account`、`un_plat_role`、`un_plat_menu`、`un_plat_operation`、关联表 | 授权保存需乐观锁和审计。 |
| 进入系统、成员和租户 | SYS/MEM | `un_module_member`、`un_module_member_tenant`、`un_module_member_dept`、`un_module_member_role` | 校验系统、租户、账号、成员状态。 |
| 系统 RBAC | RBAC | `un_module_role_*`、`un_module_permission_version` | 授权与权限版本递增同事务。 |
| 字典 | DICT | `un_module_dict_type`、`un_module_dict_item`、`un_module_dict_reference` | 写操作递增 cacheVersion。 |
| 应用/模块/字段/UI | APP/MOD/FIELD/UI | `un_module_app`、`un_module_model`、`un_module_field`、`un_module_page_schema`、`un_module_publish_version` | 发布成功写快照并更新当前版本。 |
| 运行记录 | RUN | `un_module_record`、值、索引、唯一、关联、子表、历史、附件引用 | 保存/编辑/删除/提交审批本地事务一致。 |
| 流程 | FLOW | `un_flow_*`，并联动 `un_module_record` 状态 | 任务处理使用幂等和 `task_version` 防重复。 |
| 文件 | FILE | `un_upload_file`、`un_upload_file_reference`，字段值保存 fileId 快照 | 上传与业务保存分离；绑定在业务事务中完成。 |
| 导出 | EXP | `un_module_export_template`、`un_module_export_job`、日志、结果文件 | 创建任务保存筛选/权限快照；后台状态流转。 |
| OpenAPI 管理/外部调用 | OPM/OPN | `un_openapi_*`，写接口复用内部业务表 | 先验签、scope、限流、幂等，再进入业务事务。 |
| 审计/运维 | AUD/OPS | `un_sys_*`、`un_audit_*`、`un_openapi_access_log` | 默认只读；审计写失败至少写错误日志。 |

## 八、字段级设计分片全文

以下保留 DBA-001 至 DBA-004 分片的字段、索引、状态和 API 落点细节，供 DBA-006 生成 `sql/init.sql` 与后续 MyBatis-Plus 代码生成使用。

---

# DBA-001 表域与命名映射

## 一、输入与产出边界

本分片只执行 `DBA-001 表域与命名映射设计`，依据以下冻结输入建立业务域、表前缀、后端模块和 API 数据落点之间的映射：

- `docs/tasks/DBA-001-db-domain-map.md`
- `docs/task_plan.md`
- `docs/prd.md`
- `docs/project_understanding.md`
- `docs/api.md`
- `docs/api_review.md`
- `docs/service_info.md`
- `docs/legacy_reference.md`
- `.codex/state.json`

本分片不生成字段级表结构，不生成 `docs/db_design.md`，不生成 `sql/init.sql`，不读取旧项目目录。旧项目信息只来自 `docs/legacy_reference.md` 的摘要。

## 二、总体结论

冻结 PRD 和 API 已能支撑后续 DB 设计分片。MVP 表域固定为：

- `un_plat_`：平台、账号、系统、租户、平台 RBAC、平台配置和平台级入口数据。
- `un_module_`：系统内成员扩展、组织角色权限、字典、业务应用、模块、字段、页面、菜单、运行记录、动态字段值、历史、关系、导出模板和导出任务。
- `un_flow_`：流程模板、版本、节点连线、实例、任务、审批日志和业务状态联动。
- `un_upload_`：文件元数据、存储配置、文件引用、临时文件和导出结果文件引用。
- `un_openapi_`：OpenAPI 客户端、凭证、scope、IP 白名单、签名、nonce、幂等、限流和调用日志。
- `un_sys_` / `un_audit_`：跨模块请求日志、错误日志、操作审计、业务变更审计、健康检查和运维只读日志。

旧项目 `un_app_*` 只作为 OpenAPI 历史表域参考。新项目 MVP 不新建该前缀表，不用该前缀表达业务应用，也不用该前缀表达 OpenAPI。`examine-app` 是后端 OpenAPI 模块名，但数据库表前缀固定为 `un_openapi_`。

## 三、模块表前缀与命名规则

| 表前缀 | 业务域 | 代表领域对象 | 主要后端模块 | 主要 API 分组 | 后续分片 |
| --- | --- | --- | --- | --- | --- |
| `un_plat_` | 平台中心 | 平台账号、平台角色、平台菜单、平台系统、租户、平台配置、平台登录日志、平台操作入口日志 | `examine-plat`、`examine-web` | AUTH、PLAT | DBA-002 |
| `un_module_` | 系统配置与动态模块 | 系统成员扩展、部门、系统角色、系统菜单、操作权限、字段权限、数据范围、字典、业务应用、应用版本、模块、字段、页面、发布版本、运行记录、字段值、索引值、关联、子表、历史、导出模板、导出任务 | `examine-module`、必要时由 `examine-plat` 提供平台账号引用 | SYS、MEM、RBAC、DICT、APP、MOD、FIELD、UI、RUN、EXP | DBA-002、DBA-003 |
| `un_flow_` | 流程审批 | 流程模板、模板版本、节点、连线、条件、模块绑定、流程实例、任务、候选人、抄送、动作日志、轨迹日志 | `examine-flow` | FLOW | DBA-004 |
| `un_upload_` | 上传与文件 | 文件元数据、文件分片、存储配置、业务引用、临时文件过期、导出结果文件引用 | `examine-upload` | FILE，EXP 结果文件 | DBA-004 |
| `un_openapi_` | 开放接口 | 客户端、凭证、scope、IP 白名单、nonce、幂等、限流、调用日志、签名结果 | `examine-app`、`examine-web` | OPM、OPN | DBA-004 |
| `un_sys_` / `un_audit_` | 系统日志与审计运维 | 请求日志、错误日志、操作审计、记录变更、健康检查、版本和运行配置检查 | `examine-core`、`examine-plat`、`examine-web` | AUD、OPS | DBA-004 |

命名规则：

1. 所有新表必须以 `un_` 开头，后接上表列出的模块前缀。
2. 表名必须从前缀看出模块归属，禁止无模块前缀的平铺表名。
3. 平台账号是全局登录主体，系统内成员是平台账号在某个系统中的成员扩展；二者必须分表、分前缀、分唯一约束设计。
4. 业务应用使用 `un_module_app` 方向；OpenAPI 使用 `un_openapi_` 方向；后端模块名 `examine-app` 不等同于 DB 表名前缀。
5. 旧项目可参考表名不等于新项目最终表名。最终表名、字段、索引和迁移说明由 DBA-002 至 DBA-005 继续细化。

## 四、后端模块与表域映射

| 后端模块 | 表域映射 | 说明 |
| --- | --- | --- |
| `examine-core` | 不单独表达业务域；可承载 `un_sys_` / `un_audit_` 公共日志审计基础能力 | core 提供统一响应、错误码、上下文、幂等抽象、审计基础服务和 MyBatis-Plus 基础配置，不承载平台、模块、流程等业务表域。 |
| `examine-plat` | `un_plat_`，并在系统成员场景引用 `un_module_` 成员扩展 | 平台账号、系统、租户、平台角色菜单归平台域；创建系统事务会初始化默认租户、成员扩展、系统超级管理员、默认应用等跨域对象。 |
| `examine-module` | `un_module_` | 系统内成员、组织、RBAC、字典、业务应用、模块配置、运行记录、EAV、历史、导出均归动态模块域。 |
| `examine-flow` | `un_flow_` | 流程配置、版本、实例、任务、审批日志归流程域；业务记录状态仍回写 `un_module_` 记录域。 |
| `examine-upload` | `un_upload_` | 文件元数据、存储配置和引用关系归上传域；业务附件字段值归 `un_module_`，导出结果文件引用跨 `un_module_` 和 `un_upload_`。 |
| `examine-app` | `un_openapi_` | 仅表示 OpenAPI 后端模块。客户端、凭证、scope、签名、幂等、限流、调用日志必须使用 `un_openapi_` 前缀。 |
| `examine-generator` | 读取全部表域映射，生成到各业务模块 `base` 包 | 生成器按表前缀映射目标模块；OpenAPI 表生成到 `examine-app/base`，不是动态模块，也不是旧 app 前缀。 |
| `examine-web` | 不拥有业务表域 | 只承载启动、Web 装配、全局过滤器和必要聚合入口，不写具体业务事务。 |

## 五、API 数据落点映射

| API 分组/场景 | 数据落点方向 | 事务和跨域说明 |
| --- | --- | --- |
| AUTH 登录、刷新、退出、当前用户 | `un_plat_` 平台账号、登录日志、会话/安全策略方向 | 登录主体只能是平台账号；进入系统后再解析 `systemId + accountId` 的成员扩展。 |
| PLAT 平台账号、平台角色、系统创建、平台配置 | `un_plat_` 为主；创建系统时同时初始化 `un_module_` 默认成员、角色、菜单、默认应用等对象 | 创建系统必须单事务，任一步失败整体回滚。平台管理员默认不绕过系统权限写业务数据。 |
| SYS/MEM/RBAC 系统、租户、成员、部门、角色权限 | 系统和租户基础信息归 `un_plat_`；系统成员扩展、部门、系统角色、系统菜单、操作、字段权限、数据范围归 `un_module_` | DBA-002 需明确平台账号与系统成员的唯一约束：平台账号 `loginName` 全局唯一；系统成员按 `systemId + accountId` 唯一。 |
| DICT 字典类型和字典项 | `un_module_` | 字典属于系统配置能力，需支持内置只读、引用限制、启停状态、层级和缓存版本。 |
| APP/MOD/FIELD/UI 应用、模块、字段、页面、菜单、发布版本 | `un_module_` | 业务应用和应用版本不使用旧 app 前缀；运行态只读取发布版本。 |
| RUN 运行记录保存、查询、提交、历史、关联 | `un_module_` 主记录、字段值、索引、关联、子表、历史；附件引用联动 `un_upload_` | RUN-004/RUN-006 必须同事务处理主记录、字段值、索引、历史、关联、附件引用和审计。 |
| FLOW 流程模板、发布、实例、任务处理 | `un_flow_`；业务记录状态联动 `un_module_` | 流程任务处理需幂等和并发防重复；流程实例、任务、审批日志和业务状态联动在同一业务事务内完成。 |
| FILE 文件上传、预览、下载、删除 | `un_upload_`；动态附件字段值和业务引用关联 `un_module_`；导出结果关联 `un_module_` 导出任务 | 物理上传与业务保存分离；业务绑定在记录保存事务内完成，下载必须校验业务对象权限和文件引用关系。 |
| EXP 导出模板和导出任务 | `un_module_` 导出模板、任务、筛选快照、权限快照；结果文件落 `un_upload_` | MVP 只做导出任务闭环，不把导入执行纳入强依赖。 |
| OPM/OPN OpenAPI 管理与外部调用 | `un_openapi_` 客户端、凭证、scope、白名单、nonce、幂等、限流、调用日志；写接口复用 `un_module_`、`un_flow_`、`un_upload_` 业务事务 | 外部调用先验签和幂等，再进入内部业务事务；不得复用内部 Bearer 登录态绕过 scope。 |
| AUD/OPS 审计与运维 | 跨模块日志和健康检查归 `un_sys_` / `un_audit_`；平台局部登录/操作入口可在 `un_plat_` 中保留引用 | 审计日志必须贯穿 requestId、traceId、operator、systemId、tenantId、bizType、bizId 和结果。健康异常不得静默通过。 |

## 六、后续分片归属边界

| 后续任务 | 负责表域 | 必须承接的设计点 |
| --- | --- | --- |
| DBA-002 平台系统 RBAC 字典表设计 | `un_plat_`、系统管理相关 `un_module_` | 平台账号和系统成员分离、平台/系统角色边界、系统部门、系统角色、菜单、操作、字段权限、数据范围、字典、缓存版本、唯一约束和状态枚举。 |
| DBA-003 模块配置运行记录导出表设计 | `un_module_` | 业务应用、应用版本、模块、字段、页面、菜单、发布版本、运行记录、动态字段值、索引值、关联、子表、历史、自动编号、导出模板、导出任务、筛选快照和权限快照。 |
| DBA-004 流程文件 OpenAPI 审计表设计 | `un_flow_`、`un_upload_`、`un_openapi_`、`un_sys_` / `un_audit_` | 流程结构化存储、流程实例和任务并发、文件引用和临时文件、OpenAPI 凭证与签名、nonce、幂等、限流、调用日志、审计和健康检查。 |
| DBA-005 seed 索引约束并发设计 | 汇总全部表域 | 统一命名、外键关系、seed、唯一索引、nullable 唯一规则、逻辑删除、租户维度、幂等锁、自动编号并发策略、旧项目差异和迁移注意事项。 |
| DBA-006 init.sql 与迁移检查 | 已冻结的最终 DB 设计 | 只在 DBA-005 完成后生成 SQL，并回写迁移检查结果。 |

## 七、旧项目参考边界

旧项目可参考方向：

- 平台域可参考 `un_plat_account`、`un_plat_system`、`un_plat_tenant`、`un_plat_config`、`un_plat_menu`、`un_plat_role` 等表的领域拆分，但不能把平台账号表直接当系统成员表使用。
- 动态模块域可参考旧 `un_module_app`、`un_module_app_version`、`un_module_model`、`un_module_field`、`un_module_record`、`un_module_record_data`、`un_module_record_history`、`un_module_index`、`un_module_member`、`un_module_dept`、`un_module_role`、权限表和导出任务表的方向，但字段、索引、唯一性、历史解释和归档规则必须重新设计。
- 流程域只能把 Flyway DDL 已确认的 `un_flow_` 表作为参考，不能从旧实体包全量反推新表。旧 flow 任务曾经历字段兼容补丁，新设计应直接采用明确字段。
- 上传域可参考旧 `un_upload_file`、`un_upload_file_part`、`un_upload_storage_config`，但新库必须补充文件引用、临时/已引用/删除状态、对象存储、安全治理和权限校验。
- OpenAPI 旧 `un_app_client`、`un_app_client_credential`、`un_app_client_scope`、`un_app_ip_whitelist`、`un_app_access_log` 只作为历史参考，必须迁移为 `un_openapi_client`、`un_openapi_client_credential`、`un_openapi_client_scope`、`un_openapi_ip_whitelist`、`un_openapi_access_log` 方向。
- 旧项目运行时 schema 修复和手工标记 migration 成功只作为反例，新项目不得用运行时补表补列替代 DB 设计和初始化 SQL。

## 八、后续字段级设计待补充点

| 表域 | 待 DBA 后续细化的字段级设计点 |
| --- | --- |
| `un_plat_` | 平台账号安全字段、账号状态、登录失败锁定、平台角色菜单唯一性、系统编码唯一性、租户模式和系统停用访问边界。 |
| `un_module_` | 系统成员唯一性、部门树、角色权限模型、字段权限、数据范围、动态字段类型、EAV typed columns、唯一字段和组合唯一、软删除复用、自动编号原子并发、历史快照、导出任务状态。 |
| `un_flow_` | 模板/版本/实例/任务统一术语、流程图结构化校验、任务候选人、处理原因、幂等键、任务并发版本、业务状态联动、审批历史快照。 |
| `un_upload_` | 文件状态、引用计数、业务引用对象、临时文件过期、对象存储配置、下载权限、结果文件引用和存储失败补偿。 |
| `un_openapi_` | secret 不落明文、凭证轮换、scope 细粒度授权、IP/CIDR 白名单、nonce TTL、幂等 requestHash/resultSnapshot、限流维度、调用日志大表索引和归档。 |
| `un_sys_` / `un_audit_` | requestId/traceId、operatorType/operatorId、systemId/tenantId、bizType/bizId、错误码、前后快照、健康检查项、日志保留和归档策略。 |

## 九、自检结果

| 自检项 | 结果 |
| --- | --- |
| API 数据落点是否都有明确表域 | 通过。平台、系统管理、动态模块、运行记录、流程、文件、导出、OpenAPI、审计均已映射到固定表域。 |
| OpenAPI 是否错误归入旧 app 前缀 | 通过。OpenAPI 表域固定为 `un_openapi_`，`examine-app` 只作为后端模块名。 |
| 业务应用是否误用旧 app 前缀 | 通过。业务应用和应用版本归 `un_module_`。 |
| 平台账号和系统成员是否区分 | 通过。平台账号归 `un_plat_`，系统成员扩展归 `un_module_`。 |
| 是否生成 SQL 或总 DB 设计 | 通过。本分片未生成 SQL，未写 `docs/db_design.md`。 |
| 是否直接读取旧项目目录 | 通过。旧项目信息只引用 `docs/legacy_reference.md` 摘要。 |


---

# DBA-002 平台、系统、RBAC、字典表设计分片

## 一、分片边界

本分片只覆盖 `DBA-002`：平台账号、系统、租户、平台 RBAC、系统成员扩展、系统内组织/RBAC、系统字典及其缓存版本和引用关系。

本分片不生成 SQL，不写 `docs/db_design.md`，不设计运行记录、应用版本、字段配置、流程、上传、OpenAPI 凭证、审计日志等其它分片表。OpenAPI scope 在本分片中只作为系统角色可授权对象边界记录，不重复设计 `un_openapi_` 客户端、凭证、白名单、幂等和调用日志表。

## 二、表与功能映射

| 表名 | 功能模块 | 描述 |
| --- | --- | --- |
| `un_plat_account` | 平台账号 | 全局登录主体，承载登录名、密码哈希、账号状态和安全字段。 |
| `un_plat_system` | 平台系统 | 自定义系统容器，承载系统编码、租户模式、创建人和系统状态。 |
| `un_plat_tenant` | 平台租户 | 系统下租户，单租户系统也必须有默认租户。 |
| `un_plat_role` | 平台 RBAC | 平台中心角色，控制平台账号、系统、配置和审计入口。 |
| `un_plat_menu` | 平台 RBAC | 平台中心菜单树。 |
| `un_plat_operation` | 平台 RBAC | 平台中心操作权限点。 |
| `un_plat_account_role` | 平台 RBAC | 平台账号与平台角色关联。 |
| `un_plat_role_menu` | 平台 RBAC | 平台角色与平台菜单关联。 |
| `un_plat_role_operation` | 平台 RBAC | 平台角色与平台操作权限关联。 |
| `un_plat_config` | 平台配置 | 密码策略、会话策略、默认文件存储、OpenAPI 全局策略、审计保留策略等全局配置。 |
| `un_module_member` | 系统成员 | 平台账号在某个系统中的成员扩展，不是独立登录账号。 |
| `un_module_member_tenant` | 系统成员 | 成员可访问租户集合，支持多租户切换和默认租户。 |
| `un_module_dept` | 系统组织 | 系统内部门树。 |
| `un_module_member_dept` | 系统组织 | 成员与部门多对多关联，支持主部门。 |
| `un_module_role` | 系统 RBAC | 系统内角色，含系统超级管理员等保护角色。 |
| `un_module_member_role` | 系统 RBAC | 成员与系统角色关联。 |
| `un_module_system_menu` | 系统 RBAC | 系统管理菜单和运行菜单授权目录。 |
| `un_module_system_operation` | 系统 RBAC | 系统内操作权限目录。 |
| `un_module_role_menu` | 系统 RBAC | 系统角色菜单授权。 |
| `un_module_role_operation` | 系统 RBAC | 系统角色操作授权。 |
| `un_module_role_field_permission` | 系统 RBAC | 系统角色字段可见、可写、导出明文、OpenAPI 读写授权。 |
| `un_module_role_data_scope` | 系统 RBAC | 系统角色数据范围规则。 |
| `un_module_role_openapi_scope` | 系统 RBAC | 系统角色可授权 OpenAPI scope 边界，引用 scope 编码，不保存凭证。 |
| `un_module_role_explicit_deny` | 系统 RBAC | 显式禁用权限项，优先级高于授权并集。 |
| `un_module_permission_version` | 系统 RBAC | 系统权限缓存版本，支撑 `EffectivePermissionVO.version`。 |
| `un_module_dict_type` | 系统字典 | 系统级或租户级字典类型。 |
| `un_module_dict_item` | 系统字典 | 字典项，支持层级树、内置只读和引用限制。 |
| `un_module_dict_reference` | 系统字典 | 字典被字段、发布版本、记录值引用的关系摘要，用于 `DICT-009` 和删除/停用判断。 |

## 三、命名与公共字段规则

### 3.1 表前缀

- `un_plat_`：平台账号、系统、租户、平台 RBAC、平台配置。
- `un_module_`：系统内成员扩展、组织、角色权限、字典。
- 禁止 `un_platt_`。
- 禁止用 `un_app_` 表达 OpenAPI 或业务应用；OpenAPI 其它对象由 DBA-004 使用 `un_openapi_` 设计。

### 3.2 公共字段

以下字段默认出现在本分片所有业务表中，后续 DBA-005 汇总时可统一展开到 `docs/db_design.md`。

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT | 是 | 无 | 主键，雪花 ID 或等价全局 ID。 |
| `created_at` | DATETIME(3) | 是 | CURRENT_TIMESTAMP(3) | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新时间。 |
| `created_by` | BIGINT | 否 | NULL | 创建人平台账号 ID；seed 或系统任务可为空。 |
| `updated_by` | BIGINT | 否 | NULL | 更新人平台账号 ID；seed 或系统任务可为空。 |
| `version` | INT | 是 | 0 | 乐观锁版本，状态变更、授权保存、字典修改必须校验。 |
| `deleted_flag` | TINYINT | 是 | 0 | 0-未删除，1-已删除。 |
| `delete_token` | BIGINT | 是 | 0 | 唯一索引软删复用判别值；未删除为 0，软删除时写入本行 ID 或删除时间戳。 |

MySQL 唯一索引不直接依赖 nullable 字段表达业务唯一性。根节点、系统级字典等 API 为空值的场景，DB 层使用 `0` 作为归一化 key，例如 `parent_id=0` 表示根节点，`scope_tenant_id=0` 表示系统级字典。

## 四、平台域表字段

### 4.1 `un_plat_account`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `login_name` | VARCHAR(64) | 是 | 无 | 登录名，全局唯一。 |
| `password_hash` | VARCHAR(255) | 是 | 无 | 密码哈希，不返回前端。 |
| `display_name` | VARCHAR(64) | 是 | 无 | 展示名称。 |
| `mobile` | VARCHAR(32) | 否 | NULL | 手机号。 |
| `email` | VARCHAR(128) | 否 | NULL | 邮箱。 |
| `status` | VARCHAR(32) | 是 | `NORMAL` | 账号状态：`NORMAL`、`DISABLED`、`LOCKED`。 |
| `first_login_change_pwd` | TINYINT | 是 | 0 | 是否首次登录必须改密。 |
| `failed_login_count` | INT | 是 | 0 | 连续登录失败次数。 |
| `locked_until` | DATETIME(3) | 否 | NULL | 锁定截止时间。 |
| `last_login_at` | DATETIME(3) | 否 | NULL | 最近登录时间。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_account_login_name(login_name, delete_token)`。
- 普通索引：`idx_plat_account_status(status)`、`idx_plat_account_mobile(mobile)`、`idx_plat_account_email(email)`。
- 逻辑外键：被 `un_module_member.account_id`、`un_plat_account_role.account_id` 引用。

### 4.2 `un_plat_system`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `code` | VARCHAR(64) | 是 | 无 | 系统编码，全局唯一。 |
| `name` | VARCHAR(128) | 是 | 无 | 系统名称。 |
| `description` | VARCHAR(512) | 否 | NULL | 系统描述。 |
| `tenant_mode` | VARCHAR(16) | 是 | `SINGLE` | 租户模式：`SINGLE`、`MULTI`。 |
| `default_tenant_id` | BIGINT | 否 | NULL | 默认租户 ID，创建系统事务内回填。 |
| `owner_account_id` | BIGINT | 是 | 无 | 创建人平台账号 ID。 |
| `owner_member_id` | BIGINT | 否 | NULL | 创建人在系统内的成员扩展 ID，初始化完成后回填。 |
| `status` | VARCHAR(32) | 是 | `DRAFT` | 系统状态：`DRAFT`、`ENABLED`、`DISABLED`、`ARCHIVED`。 |
| `domain` | VARCHAR(128) | 否 | NULL | 可选系统访问域名或入口标识。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_system_code(code, delete_token)`。
- 普通索引：`idx_plat_system_owner(owner_account_id)`、`idx_plat_system_status(status)`、`idx_plat_system_default_tenant(default_tenant_id)`。
- 逻辑外键：`owner_account_id -> un_plat_account.id`，`default_tenant_id -> un_plat_tenant.id`，`owner_member_id -> un_module_member.id`。

### 4.3 `un_plat_tenant`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `code` | VARCHAR(64) | 是 | 无 | 租户编码，同系统唯一；默认租户为 `default`。 |
| `name` | VARCHAR(128) | 是 | 无 | 租户名称。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 租户状态：`ENABLED`、`DISABLED`。 |
| `description` | VARCHAR(512) | 否 | NULL | 租户描述。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_tenant_system_code(system_id, code, delete_token)`。
- 普通索引：`idx_plat_tenant_system_status(system_id, status)`。
- 逻辑外键：`system_id -> un_plat_system.id`。

### 4.4 `un_plat_role`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `code` | VARCHAR(64) | 是 | 无 | 平台角色编码，全局唯一。 |
| `name` | VARCHAR(128) | 是 | 无 | 平台角色名称。 |
| `description` | VARCHAR(512) | 否 | NULL | 角色说明。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 角色状态：`ENABLED`、`DISABLED`。 |
| `protected_flag` | TINYINT | 是 | 0 | 是否保护角色；保护角色禁止删除和关键权限移除。 |
| `sort_order` | INT | 是 | 0 | 排序。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_role_code(code, delete_token)`。
- 普通索引：`idx_plat_role_status(status)`。

### 4.5 `un_plat_menu`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `parent_id` | BIGINT | 是 | 0 | 父菜单 ID，0 表示根菜单。 |
| `code` | VARCHAR(64) | 是 | 无 | 菜单编码，同父级唯一。 |
| `name` | VARCHAR(128) | 是 | 无 | 菜单名称。 |
| `path` | VARCHAR(255) | 否 | NULL | 前端路由或入口路径。 |
| `icon` | VARCHAR(128) | 否 | NULL | 图标编码。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 菜单状态：`ENABLED`、`DISABLED`。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `depth_level` | INT | 是 | 1 | 菜单层级。 |
| `depth_path` | VARCHAR(512) | 是 | `/` | 菜单路径，用于树查询。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_menu_parent_code(parent_id, code, delete_token)`。
- 普通索引：`idx_plat_menu_parent(parent_id, sort_order)`、`idx_plat_menu_status(status)`。

### 4.6 `un_plat_operation`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `menu_id` | BIGINT | 是 | 无 | 所属平台菜单。 |
| `code` | VARCHAR(64) | 是 | 无 | 操作权限编码，例如 `PLAT_ACCOUNT_CREATE`。 |
| `name` | VARCHAR(128) | 是 | 无 | 操作名称。 |
| `api_pattern` | VARCHAR(255) | 否 | NULL | 对应 API 路径模式。 |
| `method` | VARCHAR(16) | 否 | NULL | HTTP 方法。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 状态：`ENABLED`、`DISABLED`。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_operation_code(code, delete_token)`。
- 普通索引：`idx_plat_operation_menu(menu_id)`。
- 逻辑外键：`menu_id -> un_plat_menu.id`。

### 4.7 `un_plat_account_role`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `account_id` | BIGINT | 是 | 无 | 平台账号 ID。 |
| `role_id` | BIGINT | 是 | 无 | 平台角色 ID。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_account_role(account_id, role_id, delete_token)`。
- 普通索引：`idx_plat_account_role_role(role_id)`。
- 逻辑外键：`account_id -> un_plat_account.id`，`role_id -> un_plat_role.id`。

### 4.8 `un_plat_role_menu`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `role_id` | BIGINT | 是 | 无 | 平台角色 ID。 |
| `menu_id` | BIGINT | 是 | 无 | 平台菜单 ID。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_role_menu(role_id, menu_id, delete_token)`。
- 普通索引：`idx_plat_role_menu_menu(menu_id)`。

### 4.9 `un_plat_role_operation`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `role_id` | BIGINT | 是 | 无 | 平台角色 ID。 |
| `operation_id` | BIGINT | 是 | 无 | 平台操作权限 ID。 |
| `operation_code` | VARCHAR(64) | 是 | 无 | 操作编码快照，便于权限计算。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_role_operation(role_id, operation_id, delete_token)`。
- 普通索引：`idx_plat_role_operation_code(operation_code)`。

### 4.10 `un_plat_config`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `config_key` | VARCHAR(128) | 是 | 无 | 配置 key，全局唯一。 |
| `config_name` | VARCHAR(128) | 是 | 无 | 配置名称。 |
| `config_value` | JSON | 是 | 无 | 配置值；敏感字段只保存密文或引用。 |
| `sensitive_flag` | TINYINT | 是 | 0 | 是否敏感配置。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 状态：`ENABLED`、`DISABLED`。 |
| `remark` | VARCHAR(512) | 否 | NULL | 备注。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_config_key(config_key, delete_token)`。
- 普通索引：`idx_plat_config_status(status)`。

## 五、系统成员与系统 RBAC 表字段

### 5.1 `un_module_member`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `account_id` | BIGINT | 是 | 无 | 引用平台账号 ID。 |
| `member_code` | VARCHAR(64) | 是 | 无 | 系统内成员编码，同系统唯一。 |
| `display_name_snapshot` | VARCHAR(128) | 是 | 无 | 平台账号展示名快照，列表展示使用。 |
| `default_tenant_id` | BIGINT | 是 | 无 | 默认租户 ID。 |
| `post_name` | VARCHAR(128) | 否 | NULL | 岗位名称。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 成员状态：`ENABLED`、`DISABLED`。 |
| `super_admin_flag` | TINYINT | 是 | 0 | 是否系统超级管理员成员。 |
| `last_enter_at` | DATETIME(3) | 否 | NULL | 最近进入系统时间。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_member_system_account(system_id, account_id, delete_token)`。
- 唯一：`uk_module_member_system_code(system_id, member_code, delete_token)`。
- 普通索引：`idx_module_member_account(account_id)`、`idx_module_member_system_status(system_id, status)`、`idx_module_member_default_tenant(default_tenant_id)`。
- 逻辑外键：`system_id -> un_plat_system.id`，`account_id -> un_plat_account.id`，`default_tenant_id -> un_plat_tenant.id`。

边界说明：平台账号是唯一登录主体；系统内成员只承载系统上下文、部门、角色、租户和数据范围。登录接口不得直接使用 `un_module_member`，系统内接口必须通过 `system_id + account_id` 解析成员。

### 5.2 `un_module_member_tenant`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `member_id` | BIGINT | 是 | 无 | 系统成员 ID。 |
| `tenant_id` | BIGINT | 是 | 无 | 可访问租户 ID。 |
| `primary_flag` | TINYINT | 是 | 0 | 是否默认/主租户。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_member_tenant(member_id, tenant_id, delete_token)`。
- 普通索引：`idx_module_member_tenant_system(system_id, tenant_id)`。

### 5.3 `un_module_dept`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 所属租户；0 表示系统级共享部门。 |
| `parent_id` | BIGINT | 是 | 0 | 父部门 ID，0 表示根部门。 |
| `code` | VARCHAR(64) | 是 | 无 | 部门编码，同系统同父级唯一。 |
| `name` | VARCHAR(128) | 是 | 无 | 部门名称。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 部门状态：`ENABLED`、`DISABLED`。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `depth_level` | INT | 是 | 1 | 层级。 |
| `depth_path` | VARCHAR(512) | 是 | `/` | 部门路径。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_dept_parent_code(system_id, tenant_id, parent_id, code, delete_token)`。
- 普通索引：`idx_module_dept_tree(system_id, tenant_id, parent_id, sort_order)`、`idx_module_dept_path(system_id, depth_path)`。

### 5.4 `un_module_member_dept`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `member_id` | BIGINT | 是 | 无 | 成员 ID。 |
| `dept_id` | BIGINT | 是 | 无 | 部门 ID。 |
| `primary_flag` | TINYINT | 是 | 0 | 是否主部门。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_member_dept(member_id, dept_id, delete_token)`。
- 普通索引：`idx_module_member_dept_dept(system_id, dept_id)`。

### 5.5 `un_module_role`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 租户级角色归属；0 表示系统级角色。 |
| `code` | VARCHAR(64) | 是 | 无 | 角色编码，同系统同租户唯一。 |
| `name` | VARCHAR(128) | 是 | 无 | 角色名称。 |
| `description` | VARCHAR(512) | 否 | NULL | 角色说明。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 角色状态：`ENABLED`、`DISABLED`。 |
| `protected_flag` | TINYINT | 是 | 0 | 是否保护角色，如 `SYS_SUPER_ADMIN`。 |
| `sort_order` | INT | 是 | 0 | 排序。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_system_tenant_code(system_id, tenant_id, code, delete_token)`。
- 普通索引：`idx_module_role_status(system_id, status)`。

### 5.6 `un_module_member_role`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `member_id` | BIGINT | 是 | 无 | 成员 ID。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_member_role(member_id, role_id, delete_token)`。
- 普通索引：`idx_module_member_role_role(system_id, role_id)`。

### 5.7 `un_module_system_menu`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 租户级菜单归属；0 表示系统级。 |
| `parent_id` | BIGINT | 是 | 0 | 父菜单 ID。 |
| `code` | VARCHAR(64) | 是 | 无 | 菜单编码。 |
| `name` | VARCHAR(128) | 是 | 无 | 菜单名称。 |
| `menu_type` | VARCHAR(32) | 是 | `ADMIN` | 菜单类型：`ADMIN`、`RUNTIME`、`APP`。 |
| `source_type` | VARCHAR(32) | 是 | `SYSTEM` | 来源：`SYSTEM`、`MODULE`、`PAGE`、`FLOW`、`OPENAPI`。 |
| `source_id` | BIGINT | 否 | NULL | 关联来源对象 ID，模块/页面由 DBA-003 设计。 |
| `path` | VARCHAR(255) | 否 | NULL | 前端路由。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 状态：`ENABLED`、`DISABLED`。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `depth_level` | INT | 是 | 1 | 层级。 |
| `depth_path` | VARCHAR(512) | 是 | `/` | 菜单路径。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_sys_menu_parent_code(system_id, tenant_id, parent_id, code, delete_token)`。
- 普通索引：`idx_module_sys_menu_tree(system_id, tenant_id, parent_id, sort_order)`、`idx_module_sys_menu_source(source_type, source_id)`。

### 5.8 `un_module_system_operation`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `menu_id` | BIGINT | 否 | NULL | 所属菜单；系统级操作可为空。 |
| `code` | VARCHAR(64) | 是 | 无 | 操作编码，如 `SYS_MEMBER_VIEW`、`RECORD_EDIT`。 |
| `name` | VARCHAR(128) | 是 | 无 | 操作名称。 |
| `operation_type` | VARCHAR(32) | 是 | `API` | 类型：`API`、`BUTTON`、`FLOW_ACTION`、`EXPORT`、`OPENAPI_SCOPE`。 |
| `resource_type` | VARCHAR(32) | 是 | `SYSTEM` | 资源类型：`SYSTEM`、`MODULE`、`FIELD`、`FLOW`、`EXPORT`、`OPENAPI`。 |
| `resource_id` | BIGINT | 否 | NULL | 资源 ID，跨分片逻辑引用。 |
| `api_pattern` | VARCHAR(255) | 否 | NULL | 对应 API 路径模式。 |
| `method` | VARCHAR(16) | 否 | NULL | HTTP 方法。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 状态：`ENABLED`、`DISABLED`。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_sys_operation_code(system_id, code, delete_token)`。
- 普通索引：`idx_module_sys_operation_menu(menu_id)`、`idx_module_sys_operation_resource(system_id, resource_type, resource_id)`。

### 5.9 `un_module_role_menu`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |
| `menu_id` | BIGINT | 是 | 无 | 系统菜单 ID。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_menu(role_id, menu_id, delete_token)`。
- 普通索引：`idx_module_role_menu_menu(system_id, menu_id)`。

### 5.10 `un_module_role_operation`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |
| `operation_id` | BIGINT | 是 | 无 | 操作权限 ID。 |
| `operation_code` | VARCHAR(64) | 是 | 无 | 操作编码快照。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_operation(role_id, operation_id, delete_token)`。
- 普通索引：`idx_module_role_operation_code(system_id, operation_code)`。

### 5.11 `un_module_role_field_permission`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 租户归属；0 表示系统级。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |
| `module_id` | BIGINT | 是 | 无 | 模块 ID，逻辑引用 DBA-003 模块表。 |
| `field_id` | BIGINT | 是 | 无 | 字段 ID，逻辑引用 DBA-003 字段表。 |
| `field_code` | VARCHAR(64) | 是 | 无 | 字段编码快照。 |
| `visible` | TINYINT | 是 | 0 | 字段是否可见。 |
| `writable` | TINYINT | 是 | 0 | 字段是否可写。 |
| `export_plain` | TINYINT | 是 | 0 | 导出时是否允许明文。 |
| `openapi_readable` | TINYINT | 是 | 0 | OpenAPI 是否可读。 |
| `openapi_writable` | TINYINT | 是 | 0 | OpenAPI 是否可写。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_field(role_id, module_id, field_id, delete_token)`。
- 普通索引：`idx_module_role_field_lookup(system_id, module_id, field_code)`。

### 5.12 `un_module_role_data_scope`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 租户归属；0 表示系统级。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |
| `resource_type` | VARCHAR(32) | 是 | `MODULE` | 资源类型：`SYSTEM`、`MODULE`、`FLOW`、`EXPORT`、`OPENAPI`。 |
| `resource_id` | BIGINT | 是 | 0 | 资源 ID；0 表示该类型全局。 |
| `scope_type` | VARCHAR(32) | 是 | `SELF` | 数据范围：`SELF`、`DEPT`、`DEPT_TREE`、`ALL`、`CUSTOM`。 |
| `dept_ids_json` | JSON | 否 | NULL | 部门范围。 |
| `member_ids_json` | JSON | 否 | NULL | 成员范围。 |
| `custom_conditions` | JSON | 否 | NULL | 结构化自定义条件。 |
| `min_visible_rule` | VARCHAR(32) | 是 | `INTERSECTION` | 多角色合并规则：`INTERSECTION`、`UNION_LIMITED`。MVP 按最小可见规则。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_data_scope(role_id, resource_type, resource_id, delete_token)`。
- 普通索引：`idx_module_role_data_scope_system(system_id, resource_type, resource_id)`。

### 5.13 `un_module_role_openapi_scope`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 租户归属；0 表示系统级。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |
| `scope_code` | VARCHAR(128) | 是 | 无 | OpenAPI scope 编码，例如记录读写、流程动作、文件下载。 |
| `module_id` | BIGINT | 否 | NULL | 模块级 scope 绑定。 |
| `field_codes_json` | JSON | 否 | NULL | 字段级 OpenAPI 读写范围快照。 |
| `scope_action` | VARCHAR(32) | 是 | `READ` | scope 动作：`READ`、`WRITE`、`FLOW_ACTION`、`FILE_DOWNLOAD`。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_openapi_scope(role_id, scope_code, module_id, delete_token)`。
- 普通索引：`idx_module_role_openapi_scope(system_id, scope_code)`。
- 边界：只保存系统角色对 scope 的授权边界；真实客户端、凭证、scope catalog、白名单和调用日志由 `un_openapi_` 分片设计。

### 5.14 `un_module_role_explicit_deny`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |
| `deny_type` | VARCHAR(32) | 是 | 无 | 禁用类型：`MENU`、`OPERATION`、`FIELD_READ`、`FIELD_WRITE`、`DATA_SCOPE`、`EXPORT`、`OPENAPI_SCOPE`。 |
| `target_id` | BIGINT | 是 | 0 | 禁用对象 ID；0 表示按编码禁用。 |
| `target_code` | VARCHAR(128) | 否 | NULL | 禁用对象编码。 |
| `reason` | VARCHAR(512) | 否 | NULL | 禁用原因。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_deny(role_id, deny_type, target_id, target_code, delete_token)`。
- 普通索引：`idx_module_role_deny_system(system_id, deny_type)`。

### 5.15 `un_module_permission_version`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 租户归属；0 表示系统级。 |
| `version_no` | BIGINT | 是 | 1 | 权限缓存版本号。 |
| `changed_reason` | VARCHAR(128) | 否 | NULL | 最近变更原因，如 `ROLE_AUTH_SAVE`。 |
| `changed_at` | DATETIME(3) | 是 | CURRENT_TIMESTAMP(3) | 最近变更时间。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_perm_version(system_id, tenant_id, delete_token)`。
- 并发规则：角色授权、成员角色、菜单/操作/字段/数据范围/OpenAPI scope 变更成功后，在同一事务内递增 `version_no`；缓存刷新失败时不得回滚已提交权限，但必须返回可重试错误或登记补偿。

## 六、字典表字段

### 6.1 `un_module_dict_type`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `scope_type` | VARCHAR(16) | 是 | `SYSTEM` | 作用域：`SYSTEM`、`TENANT`。 |
| `scope_tenant_id` | BIGINT | 是 | 0 | 作用域租户 ID；`SYSTEM` 为 0，`TENANT` 必须为有效租户 ID。 |
| `code` | VARCHAR(64) | 是 | 无 | 字典类型编码，同作用域唯一。 |
| `name` | VARCHAR(128) | 是 | 无 | 字典类型名称。 |
| `description` | VARCHAR(512) | 否 | NULL | 描述。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 状态：`ENABLED`、`DISABLED`、`DELETED`。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `system_built_in` | TINYINT | 是 | 0 | 是否内置只读。 |
| `cache_version` | BIGINT | 是 | 1 | 字典缓存版本；写操作成功后递增。 |
| `item_count` | INT | 是 | 0 | 字典项数量冗余。 |
| `enabled_item_count` | INT | 是 | 0 | 启用字典项数量冗余。 |
| `referenced_flag` | TINYINT | 是 | 0 | 是否存在字段或记录引用。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_dict_type_scope(system_id, scope_type, scope_tenant_id, code, delete_token)`。
- 普通索引：`idx_module_dict_type_status(system_id, status)`、`idx_module_dict_type_tenant(system_id, scope_tenant_id)`。
- 校验：`scope_type=SYSTEM` 时 `scope_tenant_id=0`；`scope_type=TENANT` 时 `scope_tenant_id>0`。

### 6.2 `un_module_dict_item`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID，冗余用于隔离和索引。 |
| `dict_type_id` | BIGINT | 是 | 无 | 字典类型 ID。 |
| `parent_id` | BIGINT | 是 | 0 | 父字典项 ID，0 表示根项。 |
| `code` | VARCHAR(64) | 是 | 无 | 字典项编码，同父级唯一。 |
| `label` | VARCHAR(128) | 是 | 无 | 展示文本。 |
| `value` | VARCHAR(128) | 是 | 无 | 业务值，同父级唯一。 |
| `description` | VARCHAR(512) | 否 | NULL | 描述。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 状态：`ENABLED`、`DISABLED`、`DELETED`。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `depth_level` | INT | 是 | 1 | 层级，最大 5。 |
| `depth_path` | VARCHAR(512) | 是 | `/` | 路径，形如 `/rootId/childId`。 |
| `leaf_flag` | TINYINT | 是 | 1 | 是否叶子节点。 |
| `system_built_in` | TINYINT | 是 | 0 | 是否内置只读。 |
| `referenced_flag` | TINYINT | 是 | 0 | 是否被记录值引用。 |
| `ext_json` | JSON | 否 | NULL | 扩展信息，不允许存敏感信息。 |
| `cache_version` | BIGINT | 是 | 1 | 冗余字典类型缓存版本，便于返回和测试断言。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_dict_item_code(dict_type_id, parent_id, code, delete_token)`。
- 唯一：`uk_module_dict_item_value(dict_type_id, parent_id, value, delete_token)`。
- 普通索引：`idx_module_dict_item_tree(dict_type_id, parent_id, sort_order)`、`idx_module_dict_item_status(system_id, status)`、`idx_module_dict_item_path(dict_type_id, depth_path)`。
- 校验：`depth_level <= 5`；父级停用时禁止新增或启用子项；移动父级必须防环。

### 6.3 `un_module_dict_reference`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `dict_type_id` | BIGINT | 是 | 无 | 字典类型 ID。 |
| `dict_item_id` | BIGINT | 是 | 0 | 字典项 ID；0 表示类型级引用。 |
| `reference_type` | VARCHAR(32) | 是 | 无 | 引用类型：`FIELD_CONFIG`、`PUBLISHED_FIELD`、`RECORD_VALUE`。 |
| `module_id` | BIGINT | 否 | NULL | 模块 ID。 |
| `field_id` | BIGINT | 否 | NULL | 字段 ID。 |
| `field_code` | VARCHAR(64) | 否 | NULL | 字段编码快照。 |
| `published_version_id` | BIGINT | 否 | NULL | 发布版本 ID，逻辑引用 DBA-003。 |
| `record_id` | BIGINT | 否 | NULL | 记录 ID，逻辑引用 DBA-003。 |
| `usage_count` | BIGINT | 是 | 1 | 引用数量摘要；记录值批量统计可累加。 |
| `active_flag` | TINYINT | 是 | 1 | 当前是否有效引用。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_dict_ref_unique(dict_type_id, dict_item_id, reference_type, module_id, field_id, published_version_id, record_id, delete_token)`。
- 普通索引：`idx_module_dict_ref_type(dict_type_id, reference_type, active_flag)`、`idx_module_dict_ref_item(dict_item_id, reference_type, active_flag)`、`idx_module_dict_ref_field(module_id, field_id)`。
- 边界：字段配置和发布版本引用由 DBA-003 字段/发布流程维护；记录值引用由运行记录保存事务维护。本表只保存引用摘要，不替代 EAV 字段值表。

## 七、关系说明

1. 平台账号与系统成员：`un_plat_account.id` 一对多 `un_module_member.account_id`。同一系统内通过 `system_id + account_id` 唯一，确保一个平台账号在一个系统只有一个成员扩展。
2. 系统与租户：`un_plat_system.id` 一对多 `un_plat_tenant.system_id`；单租户系统也初始化 `code=default` 的租户。
3. 系统创建初始化：`un_plat_system`、`un_plat_tenant`、`un_module_member`、`un_module_role`、`un_module_system_menu`、`un_module_system_operation`、`un_module_member_role`、`un_module_role_*`、`un_module_permission_version` 必须在 `PLAT-002` 同一事务内完成。
4. 平台 RBAC：平台账号通过 `un_plat_account_role` 关联平台角色；平台角色通过 `un_plat_role_menu` 和 `un_plat_role_operation` 获得平台中心权限。平台权限不授予系统内业务数据写入。
5. 系统 RBAC：系统成员通过 `un_module_member_role` 关联角色；角色授权由菜单、操作、字段权限、数据范围、OpenAPI scope 和显式禁用表共同计算。
6. 部门关系：部门树由 `un_module_dept.parent_id` 与 `depth_path` 表达；成员与部门是多对多，主部门由 `un_module_member_dept.primary_flag` 标记。
7. 字典关系：`un_module_dict_type` 一对多 `un_module_dict_item`；字典项自关联形成树；`un_module_dict_reference` 记录字段配置、发布版本和记录值引用摘要。
8. OpenAPI scope 边界：`un_module_role_openapi_scope.scope_code` 只用于系统角色授权计算，真实 scope catalog、客户端凭证和调用日志由 DBA-004 的 `un_openapi_` 表域承接。

## 八、状态枚举

| 对象 | 字段 | 枚举 |
| --- | --- | --- |
| 平台账号 | `status` | `NORMAL`、`DISABLED`、`LOCKED` |
| 平台系统 | `status` | `DRAFT`、`ENABLED`、`DISABLED`、`ARCHIVED` |
| 租户 | `status` | `ENABLED`、`DISABLED` |
| 平台角色/菜单/操作/配置 | `status` | `ENABLED`、`DISABLED` |
| 系统成员 | `status` | `ENABLED`、`DISABLED` |
| 部门 | `status` | `ENABLED`、`DISABLED` |
| 系统角色 | `status` | `ENABLED`、`DISABLED` |
| 系统菜单/操作 | `status` | `ENABLED`、`DISABLED` |
| 字典类型/字典项 | `status` | `ENABLED`、`DISABLED`、`DELETED` |
| 字典类型作用域 | `scope_type` | `SYSTEM`、`TENANT` |
| 数据范围 | `scope_type` | `SELF`、`DEPT`、`DEPT_TREE`、`ALL`、`CUSTOM` |

状态变更必须校验 `version`。字典删除是软删除终态，默认列表不返回 `DELETED`；平台账号、成员和角色 MVP 只有启停/锁定，不暴露物理删除能力。

## 九、初始化数据与 seed 边界

### 9.1 生产 `init.sql` 必须包含的建库 seed

本分片要求 DBA-006 在生产 `init.sql` 中初始化以下平台域数据，具体 SQL 由 DBA-006 生成：

- 默认平台管理员账号：`login_name=platform_admin`，`status=NORMAL`，`first_login_change_pwd=1`，密码使用安全占位哈希或部署变量注入，不写明文。
- 平台角色：`PLAT_SUPER_ADMIN`、`PLAT_ADMIN`、`PLAT_AUDITOR`。
- 平台菜单：`PLAT_MY_SYSTEM`、`PLAT_SYSTEM`、`PLAT_TENANT`、`PLAT_ACCOUNT`、`PLAT_ROLE`、`PLAT_CONFIG`、`PLAT_HEALTH`、`PLAT_AUDIT_LOG`、`PLAT_VERSION`、`PLAT_OPENAPI_POLICY`。
- 平台操作权限：围绕 `PLAT_SYSTEM_CREATE`、`PLAT_SYSTEM_VIEW`、`PLAT_SYSTEM_STATUS`、`PLAT_ACCOUNT_VIEW`、`PLAT_ACCOUNT_CREATE`、`PLAT_ACCOUNT_STATUS`、`PLAT_ROLE_VIEW`、`PLAT_ROLE_AUTH`、`PLAT_CONFIG_VIEW`、`PLAT_CONFIG_EDIT` 等 API 权限点建立。
- 平台配置：`SECURITY_PASSWORD_POLICY`、`SESSION_POLICY`、`FILE_STORAGE_DEFAULT`、`OPENAPI_GLOBAL_POLICY`、`AUDIT_RETENTION_POLICY`。
- 默认账号与 `PLAT_SUPER_ADMIN` 绑定，`PLAT_SUPER_ADMIN` 绑定全部平台菜单和平台操作。

字段类型元数据虽然在 PRD 中属于建库 seed，但其表结构由 DBA-003 模块/字段配置分片承接；本分片只在系统创建事务中预留“字段类型引用初始化”的跨分片边界。

### 9.2 `PLAT-002` 创建系统事务内 seed

创建系统必须在一个事务内初始化：

- `un_plat_system`：系统基础信息，初始状态按 API 入参和产品规则写入，成功后可为 `ENABLED` 或配置态状态。
- `un_plat_tenant`：默认租户 `code=default`，`status=ENABLED`。
- `un_module_member`：创建人成员扩展，唯一键 `system_id + account_id`。
- `un_module_member_tenant`：创建人成员绑定默认租户。
- `un_module_role`：系统超级管理员角色 `SYS_SUPER_ADMIN`，`protected_flag=1`，`status=ENABLED`。
- `un_module_member_role`：创建人成员绑定 `SYS_SUPER_ADMIN`。
- `un_module_system_menu` / `un_module_system_operation`：系统默认菜单和操作权限目录。
- `un_module_role_menu` / `un_module_role_operation` / 需要的字段、数据范围、OpenAPI scope 授权：`SYS_SUPER_ADMIN` 获得当前系统内全部管理权限。
- `un_module_permission_version`：初始化 `version_no=1`。
- 默认应用 `default_app` 和字段类型引用由 DBA-003 对应表承接，但必须与以上对象同事务提交。

任一步失败必须整体回滚，不允许出现“系统已创建但创建人没有系统超级管理员权限”。

### 9.3 不进入生产 seed

- 演示系统、演示租户、演示部门、演示成员、演示角色、演示授权。
- 用户自定义字典类型和字典项。
- OpenAPI 测试客户端、测试 scope、测试调用日志。
- 业务样例应用、模块、记录、流程、附件和导出任务。

## 十、并发、软删与缓存规则

1. 所有状态变更、角色授权保存、成员角色保存、字典写操作必须带 `version` 乐观锁；版本不一致返回对应状态冲突或权限冲突错误。
2. `PLAT-002` 必须配合内部幂等键，幂等记录由公共/审计或 OpenAPI 分片承接；DBA-002 表结构保证系统编码、默认成员和默认角色唯一，防止重复初始化。
3. 字典类型和字典项软删除时设置 `status=DELETED`、`deleted_flag=1`、`delete_token=id`，允许同作用域重新创建同编码字典。
4. 字典类型系统级作用域使用 `scope_tenant_id=0`，字典项根节点使用 `parent_id=0`，避免 MySQL nullable 唯一索引允许重复的问题。
5. 字典写接口 `DICT-002` 至 `DICT-011` 在同一事务内递增 `un_module_dict_type.cache_version`，同步更新受影响字典项 `cache_version`；缓存刷新失败但数据库已提交时返回 `DICT_CACHE_REFRESH_FAILED`，并由后续补偿任务处理缓存。
6. 权限写接口 `RBAC-009`、成员角色变更 `MEM-006`、部门和角色变更成功后递增 `un_module_permission_version.version_no`。`RBAC-010` 返回该版本，前端据此刷新权限。
7. 系统成员没有独立登录密码，不允许通过成员表绕过平台账号状态；账号停用/锁定时所有系统成员上下文均不可进入。
8. 平台超级管理员只拥有平台中心和审计入口权限；如未被加入某系统成为成员，不得通过系统 RBAC 表获得系统内业务写权限。

## 十一、API 数据落点

| API | 数据落点 |
| --- | --- |
| AUTH-001/002/003/004/005 | 账号主体读取或更新 `un_plat_account`；登录日志不在本分片展开。 |
| PLAT-001 | 查询 `un_plat_system`、`un_plat_tenant`、`un_module_member`、`un_module_member_role`、`un_module_role` 组合得到“我的系统”。 |
| PLAT-002 | 单事务写 `un_plat_system`、`un_plat_tenant`、`un_module_member`、`un_module_member_tenant`、`un_module_role`、`un_module_member_role`、`un_module_system_menu`、`un_module_system_operation`、`un_module_role_*`、`un_module_permission_version`；默认应用和字段类型引用由 DBA-003 表承接。 |
| PLAT-003/004/005 | 查询或更新 `un_plat_system`，停用/归档影响系统进入和后续系统内写入。 |
| PLAT-006/007/008/013/014/015/016 | 平台账号管理落 `un_plat_account`、`un_plat_account_role`。 |
| PLAT-009/010/017/018/019/020 | 平台角色、菜单和操作权限落 `un_plat_role`、`un_plat_menu`、`un_plat_operation`、`un_plat_role_menu`、`un_plat_role_operation`。 |
| PLAT-011/012 | 平台配置落 `un_plat_config`。 |
| SYS-001 | 通过 `system_id + account_id` 查询 `un_module_member`，校验 `un_plat_system`、`un_plat_tenant`、成员状态并建立上下文。 |
| SYS-002/003 | 系统基础信息读取或更新 `un_plat_system`。 |
| SYS-004/005/006/007 | 租户读取、创建、启停和切换落 `un_plat_tenant`、`un_module_member_tenant`。 |
| MEM-001 至 MEM-007 | 成员扩展、租户、部门、角色落 `un_module_member`、`un_module_member_tenant`、`un_module_member_dept`、`un_module_member_role`。 |
| RBAC-001 至 RBAC-004 | 部门树落 `un_module_dept`、`un_module_member_dept`；删除前检查子部门和成员关联。 |
| RBAC-005 至 RBAC-013 | 系统角色和权限目录落 `un_module_role`、`un_module_system_menu`、`un_module_system_operation`、`un_module_role_*`、`un_module_permission_version`。 |
| DICT-001 至 DICT-011 | 字典类型、字典项、引用和缓存版本落 `un_module_dict_type`、`un_module_dict_item`、`un_module_dict_reference`。 |
| OPM-009 / RBAC-013 scope catalog | 本分片只通过 `un_module_role_openapi_scope` 记录角色授权到 scope 的边界；真实 scope catalog 由 DBA-004 的 `un_openapi_` 表设计承接。 |

## 十二、自检结果

| 自检项 | 结果 |
| --- | --- |
| 是否只输出 DBA-002 分片 | 通过。仅设计 `docs/db_design_parts/platform-system-rbac-dict.md`，不生成 SQL，不写总 DB 设计。 |
| 表前缀是否合规 | 通过。平台域使用 `un_plat_`，系统成员/RBAC/字典使用 `un_module_`，未使用 `un_platt_` 或 `un_app_`。 |
| 平台账号与系统成员是否分离 | 通过。`un_plat_account` 是登录主体，`un_module_member` 是 `system_id + account_id` 唯一的系统成员扩展。 |
| 系统创建事务 seed 是否可落库 | 通过。已列明系统、默认租户、创建人成员、系统超级管理员、默认菜单/权限、权限版本和跨 DBA-003 默认应用/字段类型引用边界。 |
| RBAC API 是否有落点 | 通过。菜单、操作、字段权限、数据范围、OpenAPI scope、显式禁用和权限缓存版本均有表承接。 |
| 字典唯一性、层级、引用、缓存版本是否可落库 | 通过。字典类型和字典项使用非空归一化 key 规避 MySQL nullable 唯一索引问题，支持 5 级层级、引用摘要和 cacheVersion 递增。 |
| 并行输出路径是否重叠 | 通过。本任务只写 `docs/db_design_parts/platform-system-rbac-dict.md`，与 DBA-003/DBA-004 分片路径不重叠，最终汇总由 DBA-005 串行写 `docs/db_design.md`。 |


---

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


---

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


## 九、DBA-005 自检结果

| 自检项 | 结果 |
| --- | --- |
| 输出路径 | 通过。仅写入 `docs/db_design.md`。 |
| 是否生成 SQL | 通过。未创建或修改 `sql/init.sql`。 |
| 表前缀 | 通过。新建表使用 `un_plat_`、`un_module_`、`un_flow_`、`un_upload_`、`un_openapi_`、`un_sys_`、`un_audit_`；`un_app_*` 仅作为旧项目历史参考。 |
| 生产 seed | 通过。已明确默认平台管理员、平台角色、平台菜单、平台配置和字段类型元数据；未写明文默认密码。 |
| 创建系统事务初始化 | 通过。已明确默认租户、成员扩展、系统超级管理员、菜单/权限、默认应用、字段类型引用同事务初始化。 |
| 唯一索引与 nullable 规则 | 通过。已明确软删复用、租户维度、MySQL nullable 唯一规避、动态字段唯一空值跳过规则。 |
| 幂等与并发 | 通过。已明确幂等 scope、PROCESSING、冲突、回放快照、自动编号原子更新和流程/导出并发控制。 |
| 旧项目差异和迁移 | 通过。已列出可参考表、不沿用原因、新旧结构差异和迁移注意事项。 |
| API 数据落点 | 通过。已按 API 分组汇总数据落点和事务边界。 |

## 十二、DBA-006 初始化 SQL 与迁移检查结果

### 12.1 SQL 生成结果

| 检查项 | 结果 |
| --- | --- |
| 输出文件 | 通过。已生成 `sql/init.sql`，包含建库、建表、字段注释、主键、唯一约束、普通索引和生产 seed。 |
| 建库名称 | `unexamine`。库名按本 DB 设计标题和 PRD 项目名选取；导入连接仍使用 `docs/service_info.md` 中的目标 MySQL 主机、端口和账号。 |
| 表数量 | 通过。`sql/init.sql` 生成 83 张 `un_*` 表。 |
| 表前缀 | 通过。新建表仅使用 `un_plat_`、`un_module_`、`un_flow_`、`un_upload_`、`un_openapi_`、`un_sys_`、`un_audit_`；未出现 `un_platt_`，未新建 `un_app_*` 表，未出现无 `un_` 模块前缀表。 |
| 字段审计时间 | 通过。每张表均包含 `created_at` 和 `updated_at`，新增记录默认当前时间，更新时 `updated_at` 自动刷新。 |
| 索引列自检 | 通过。静态检查 83 张表的主键、唯一约束和普通索引，未发现索引引用不存在字段。 |
| 生产 seed | 通过。包含默认平台管理员账号、平台角色、平台菜单、平台操作权限、平台配置、字段类型元数据、默认存储配置和 migration 状态标记；不包含演示系统、演示业务应用、业务记录、测试 OpenAPI 客户端或测试附件。 |
| 默认密码 | 通过。`platform_admin` 仅写入 `__REPLACE_WITH_DEPLOYMENT_PASSWORD_HASH__` 占位哈希，不写明文默认密码；部署前必须替换为安全哈希或由部署流程注入。 |

### 12.2 目标 MySQL 导入检查

目标连接来自 `docs/service_info.md`：`192.168.0.211:3306`，账号 `root`。检查命令使用环境变量传入密码，未把密码写入 `sql/init.sql`。

| 步骤 | 命令/动作 | 结果 |
| --- | --- | --- |
| 端口连通性 | `Test-NetConnection -ComputerName 192.168.0.211 -Port 3306` | 通过，`TcpTestSucceeded=True`。 |
| 本机 MySQL CLI | `Get-Command mysql,mariadb,mysqlsh` | 阻塞，本机 PATH 未发现 MySQL/MariaDB CLI。 |
| Docker MySQL client | `docker run --rm ... mysql:8 sh -c "mysql ... < /init.sql"` | 阻塞，Docker Desktop daemon 未运行：无法连接 `dockerDesktopLinuxEngine`。 |
| JDBC 临时导入 runner | 临时下载 MySQL Connector/J 到系统临时目录，使用 JDK 21 执行 `sql/init.sql` | 可用，成功连接目标 MySQL。 |
| 非破坏性导入到 `examine1` | 使用 service URL 原数据库名尝试导入 | 失败。`examine1` 中已存在旧结构 `un_plat_role`，seed 写入时报 `Unknown column 'code' in 'field list'`，说明目标库不是空库且与新设计不兼容。未执行 drop/alter，避免覆盖旧数据。 |
| 干净库导入 | 使用同一 MySQL 实例导入 `sql/init.sql` 创建的 `unexamine` 库 | 通过。结果：`IMPORT_OK database=unexamine executed=98 tables=83 account=1 roles=3 menus=10 operations=14 configs=6 elapsedMs=7698`。 |

### 12.3 迁移阻塞与修复建议

| 阻塞点 | 影响 | 修复建议 |
| --- | --- | --- |
| `docs/service_info.md` 原 URL 指向的 `examine1` 已有旧表结构，至少 `un_plat_role` 缺少新设计要求的 `code` 字段。 | 不能把新初始化 SQL 直接非破坏性导入 `examine1`；`CREATE TABLE IF NOT EXISTS` 会保留旧表，后续 seed 或代码生成会遇到字段不匹配。 | 后续环境准备必须二选一：使用干净库 `unexamine` 并同步后端数据源配置；或在完整备份 `examine1` 后清库重建，再导入 `sql/init.sql`。禁止在旧库上静默 repair 或手工标记 migration 成功。 |
| 本机缺少 MySQL CLI，Docker daemon 未运行。 | 无法使用标准 `mysql < sql/init.sql` 命令复现导入，只能使用临时 JDBC runner。 | 安装 MySQL client 或启动 Docker Desktop 后，可用同一 SQL 在目标库复测；导入密码应通过环境变量或交互方式传入，不写入脚本。 |

### 12.4 DBA-006 结论

DBA-006 通过。`sql/init.sql` 已可在目标 MySQL 的干净 `unexamine` 库完成建库、建表和生产 seed 导入。原 `examine1` 数据库存在旧结构冲突，属于迁移环境阻塞，不能在未备份和未确认清库策略前直接覆盖。
