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
