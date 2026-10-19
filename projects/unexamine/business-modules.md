# 业务模块与表归属

本文件按 `docs/表意义.md` 的 94 张原名表划分业务所有权。产品仍只有一个 Spring Boot 服务、一个 `unexamine_r15` 库和一个 9999 入口。Maven 子模块是代码边界，不是微服务，也不增加新的数据库或运行容器。

| 业务模块 | 自有表（张） | 用户实际完成的事 | 对外入口 |
| --- | --- | --- | --- |
| `identity` 平台与系统租户 | `un_plat_*` 12、`un_sys_*` 14 | 平台开通系统和租户；成员登录、组织、角色、菜单、数据范围；平台及系统消息和日志 | `/api/platform/**`、`/api/system/**`、平台与租户消息/日志 |
| `commonresource` 公共资源 | `un_common_*` 3 | 按平台、系统或租户范围上传文件、发布公告、保存 AI 配置 | `/api/common/**` |
| `module` 可配置业务模块 | `un_module*` 中 19 张 | 管理员定义字段、表单、查询字段、表头、动作和关联；成员保存记录、使用场景、查看历史 | `/api/modules/**` |
| `flow` 通用流程 | `un_flow*` 12、`un_module_flow` 1 | 定义草稿、发布版本、绑定模块事件；自动或手动发起实例、处理待办、定时和异常 | `/api/tenants/{tenantId}/flows/**`、`/api/modules/{moduleId}/flow-bindings/**` |
| `examine` 审批 | `un_examine*` 11、`un_module_examine` 1 | 配置审批及人员、节点与规则；从模块记录发起独立审批，处理和查看历史 | `/api/tenants/{tenantId}/examines/**`、`/api/modules/{moduleId}/examine-bindings/**` |
| `work` 工作协作 | `un_work_*` 10 | 建项目、任务组和任务，协作处理、记历史、填写和评论日报 | `/api/tenants/{tenantId}/work/**` |
| `data` 数据展示 | `un_data_source*`、`un_dashboard*`、`un_kpi*` 共 8 | 以有权限的模块记录为数据源，保存仪表盘和 KPI，并返回实际统计值 | `/api/tenants/{tenantId}/data-sources/**`、`dashboards/**`、`kpis/**` |
| `print` 打印 | `un_print` 1 | 保存模板，并把当前成员有权读取的模块记录代入模板 | `/api/tenants/{tenantId}/prints/**` |
| `app` 应用接入 | `un_app*` 2 | 创建和轮换应用密钥，验证应用身份，维护事件订阅配置 | `/api/apps/**` |

表数为 `26 + 3 + 19 + 13 + 12 + 10 + 8 + 1 + 2 = 94`。每张表的 Entity/Mapper 由自己的业务模块生成并编译；`foundation` 没有业务表和业务判断，只放分页、数据库冲突等通用设施；`server` 只组合业务模块并启动服务。`identity` 承担会话、租户身份和操作审计，其他业务模块不自行判断客户端传来的身份。

`identity` 内部仍分别有 `platform` 与 `system` 代码目录：前者负责平台账号与授权，后者负责业务系统、租户成员与授权；开通入口供平台管理员使用。两者共同维护同一条开通和登录链，底层会话校验只实现一次。

## 逐表归属与业务写入边界

下表把《表意义.md》的原名表放入实际操作，不按 Controller 数量或一次对话任务划分。表由所属业务模块写入；其他模块只在已明确的业务链中读取，不能直接替它维护状态。

| 所属业务与操作 | 原名表 |
| --- | --- |
| 平台账号、组织与授权：登录、建立成员、分配角色和数据范围 | `un_plat_account`、`un_plat_department`、`un_plat_member`、`un_plat_role`、`un_plat_member_role`、`un_plat_menu`、`un_plat_role_menu`、`un_plat_role_data` |
| 平台设置、审计和消息 | `un_plat_config`、`un_plat_log_login`、`un_plat_log_opt`、`un_plat_message` |
| 业务系统与租户开通、成员登录和组织 | `un_sys_system`、`un_sys_system_config`、`un_sys_tenant`、`un_sys_member`、`un_sys_department`、`un_sys_tenant_member` |
| 租户授权、日志和消息 | `un_sys_menu`、`un_sys_role`、`un_sys_member_role`、`un_sys_role_menu`、`un_sys_role_data`、`un_sys_log_login`、`un_sys_log_opt`、`un_sys_message` |
| 公共文件、公告和 AI 配置 | `un_common_file`、`un_common_notice`、`un_common_ai` |
| 模块目录、菜单、字典与动作规则 | `un_module_group`、`un_module`、`un_module_dict`、`un_module_menu`、`un_module_action`、`un_module_rule` |
| 模块字段、表单、查询和租户覆盖 | `un_module_field`、`un_module_query_field`、`un_module_tenant`、`un_module_tenant_field`、`un_module_field_sort` |
| 模块记录、字段值、场景、协作、关联与变动 | `un_module_record`、`un_module_record_data`、`un_module_scence`、`un_module_scence_config`、`un_module_teamer`、`un_module_relation`、`un_module_relation_config`、`un_module_log` |
| 流程与模块的显式配置关联 | `un_module_flow` |
| 流程草稿、连线、发布版本和触发绑定 | `un_flow`、`un_flow_node`、`un_flow_edge`、`un_flow_version`、`un_flow_binding` |
| 流程实例、待办、动作、事件、异常与定时 | `un_flow_example`、`un_flow_task`、`un_flow_task_candidate`、`un_flow_action`、`un_flow_exception`、`un_flow_event`、`un_flow_job` |
| 审批与模块的显式配置关联 | `un_module_examine` |
| 审批定义、分组、高级配置、审批人和节点 | `un_examine`、`un_examine_group`、`un_examine_config`、`un_examine_config_user`、`un_examine_node`、`un_examine_node_field`、`un_examine_node_user` |
| 审批实例、逐节点处理人与异常 | `un_examine_example`、`un_examine_example_node`、`un_examine_example_node_user`、`un_examine_exception` |
| 工作项目、成员、任务组、任务、字段和协作历史 | `un_work_project`、`un_work_project_member`、`un_work_task_group`、`un_work_task`、`un_work_task_field`、`un_work_task_member`、`un_work_task_history` |
| 每日工作记录、字段与评论 | `un_work_daily`、`un_work_daily_field`、`un_work_daily_comment` |
| 数据源及配置、仪表盘组件和查看成员 | `un_data_source`、`un_data_source_config`、`un_dashboard`、`un_dashboard_component`、`un_dashboard_component_field`、`un_dashboard_user` |
| KPI 定义与个人目标 | `un_kpi`、`un_kpi_user` |
| 打印模板与应用身份、订阅配置 | `un_print`、`un_app`、`un_app_event` |

`un_module_examine` 与 `un_module_flow` 虽以 `un_module` 开头，实际写入必须校验对应审批/流程定义，因此分别由 `examine`、`flow` 持有生成代码和绑定接口；原 URL 保持 `/api/modules/{moduleId}/...-bindings`。模块域无需反向依赖审批或流程。数据展示和打印只读取模块域允许当前成员查看的记录；应用订阅目前只保存配置，未发生实际事件投递。

## 业务旅程和依赖

1. 平台管理员创建业务系统和租户，建立首位租户管理员；系统成员登录后，服务端确认其租户身份。这一步是其他租户业务的前置条件。
2. 租户管理员配置模块、字段、表单、查询能力和表头。成员保存模块记录时，模块域校验字段、版本及读写范围，并同事务记录变更历史。
3. 场景保存列顺序、筛选和排序；记录列表按场景和当前成员可见范围查询。配置能保存但查询不能执行的操作符不算完成。
4. 审批和通用流程各自有定义、运行实例和历史表。审批依模块记录与审批绑定发起；流程通过冻结的发布版本运行。模块记录保存只调用 `ModuleRecordFlowTrigger`，由流程模块实现自动触发；模块不依赖流程实现类。
5. 工作项目、任务与日报在 `work` 内闭环。数据展示和打印只读取当前成员有权访问的模块记录，不绕过记录权限。应用密钥与用户会话分离，当前不签发业务访问令牌。

依赖方向：`foundation → identity → module → flow/examine/data/print` 表示后者可以依赖前者；`work/commonresource/app` 依赖 `identity`。`server` 最后组合全部模块。`flow` 不能反向成为 `module` 的编译依赖。只有真实跨域业务需要才加接口，普通 CRUD 仍直接留在所属模块。

## 每个模块如何验收

每个业务模块至少检查：成功请求、数据库持久化、越权拒绝、无效输入或状态冲突，以及与其相邻模块的一条真实业务链。现有实测结果见 `backend-acceptance.md`；未实现的外部投递、AI 服务调用、复杂 KPI 周期等不能因为有配置表或空接口就算完成。后端按模块验收后才开发前端。
