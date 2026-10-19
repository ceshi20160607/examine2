# 第 15 次重构表关系契约（设计稿）

依据：`docs/user_requirement.md` §2.3、§5、A.14–A.20；`docs/表意义.md` 的 94 个表名只表示用户理解的业务事实。最终需求 A.19 的业务域命名优先于参考表名。本稿明确首条业务旅程及后续能力的边界；物理列、索引和服务事务在 SQL 步骤定稿。

## 94 个参考表的逐域处理

| 参考表 | 当前处理与业务事实 | 实施时机 |
| --- | --- | --- |
| `un_plat_account` | 保留。统一登录账号、口令哈希和账号状态；系统身份另由成员关系表示。 | 首批 |
| `un_plat_config`、`un_plat_department`、`un_plat_member`、`un_plat_role`、`un_plat_member_role`、`un_plat_menu`、`un_plat_role_menu`、`un_plat_role_data` | 保留平台配置、组织成员、角色、菜单与数据范围各自的事实；平台角色不自动授予系统权限。 | 平台后台与权限中心 |
| `un_plat_log_login`、`un_plat_log_opt`、`un_plat_message` | 登录和操作事件归平台审计域；平台消息独立于系统消息，仍用 `un_plat_*`。 | 审计与消息 |
| `un_common_file`、`un_common_notice`、`un_common_ai` | 不建立模糊的 `un_common_*` 主表。文件对象与引用归 `un_file_*`；公告按平台或系统归属；AI 配置归 `un_ai_*`。 | 对应能力 |
| `un_sys_system`、`un_sys_tenant` | 保留。系统创建即产生主租户；单租户运行也保存租户标识。 | 首批 |
| `un_sys_member`、`un_sys_tenant_member` | 保留。账号加入系统、成员加入租户是两种不同关系，均不能由平台身份推断。 | 首批 |
| `un_sys_role`、`un_sys_member_role`、`un_sys_role_menu` | 保留系统内角色、成员角色和菜单/动作授权；角色授予按当前系统、租户和成员校验。 | 首批 |
| `un_sys_system_config`、`un_sys_department`、`un_sys_menu`、`un_sys_role_data` | 保留系统配置、组织、可见菜单和数据范围的独立事实，字段与动作授权进入权限中心时一起定稿。 | 组织与权限中心 |
| `un_sys_log_login`、`un_sys_log_opt`、`un_sys_message` | 系统审计记录明确系统及租户；系统消息统一归 `un_msg_*`，与平台消息不混表。 | 审计与消息 |
| `un_module_group`、`un_module`、`un_module_field`、`un_module_menu`、`un_module_action` | 对应 `un_cfg_module_group`、`un_cfg_module`、`un_cfg_module_field`、`un_cfg_module_menu`、`un_cfg_module_action`。组是运行态导航分组，模块只属于一个组；字段、菜单、动作是草稿配置。另设 `un_cfg_module_version` 保存不可变发布快照。 | 首批 |
| `un_module_dict`、`un_module_rule`、`un_module_query_field`、`un_module_field_sort` | 对应字典、规则、查询与字段展示配置；简单排序可归字段或发布页面配置，不因旧表名额外建空表。 | 字典、规则、页面 |
| `un_module_tenant`、`un_module_tenant_field` | 记录主租户配置与租户扩展的来源、覆盖和发布状态；租户扩展不能改变主租户稳定字段类型。 | 多租户扩展 |
| `un_module_record`、`un_module_record_data` | 对应 `un_biz_record` 主记录与 `un_biz_record_data` 字段值；增加 `un_biz_record_index` 作为可重建的类型化查询索引。自定义模块不生成独立物理表。 | 首批 |
| `un_module_scence`、`un_module_scence_config` | 更正为 `un_cfg_module_scene` 等页面/场景配置；只在可视化场景确有独立版本和查询行为时建表。 | 页面配置 |
| `un_module_teamer`、`un_module_relation`、`un_module_relation_config` | 团队/相关人员归业务记录参与关系；记录间关联与关联配置分开，子表关系按运行模型处理。 | 记录协作与关联 |
| `un_module_log` | 归 `un_audit_*` 不可变业务操作日志，记录人、作用域和权限快照。 | 审计 |
| `un_module_examine`、`un_module_flow` | 合并到统一 `un_flow_binding`：模块动作或触发点绑定一个生效 Flow，不另造平行审批引擎。 | Flow |
| `un_flow`、`un_flow_node`、`un_flow_edge`、`un_flow_version`、`un_flow_binding` | 分别承载 Flow 定义、节点、连线、不可变版本与生效绑定；`un_flow` 规范为 `un_flow_definition`。 | Flow |
| `un_flow_example`、`un_flow_task`、`un_flow_task_candidate`、`un_flow_action`、`un_flow_exception`、`un_flow_event`、`un_flow_job` | 分别承载实例、处理轮次、候选人、幂等操作、异常、不可变事件和定时任务；实例保存启动版本与运行快照。`example` 规范为 `instance`。 | Flow |
| `un_examine`、`un_examine_group`、`un_examine_config`、`un_examine_exception`、`un_examine_config_user`、`un_examine_node`、`un_examine_node_field`、`un_examine_node_user`、`un_examine_example`、`un_examine_example_node`、`un_examine_example_node_user` | 只参考其“定义→节点/人员→实例→实例节点/处理人”的审批关系。审批是 Flow 的配置和运行场景；人员、节点字段和实例历史由 Flow 同一套表与快照表达，不创建这 11 张平行表。 | Flow |
| `un_app`、`un_app_event` | 规范为 `un_app_application`、`un_app_event`；应用承担平台内外受控访问，后续补凭据、授权、审计事实。 | 应用 |
| `un_work_project`、`un_work_project_member`、`un_work_task_group`、`un_work_task`、`un_work_task_field`、`un_work_task_member`、`un_work_task_history` | 保留工作项目、任务、成员和历史的独立事实；任务不等于 Flow 待办。 | 工作任务 |
| `un_work_daily`、`un_work_daily_field`、`un_work_daily_comment` | 保留用户手动创建的工作日志及其字段、评论；不得由定时任务或 AI 自动创建。 | 工作日志 |
| `un_dashboard`、`un_dashboard_component`、`un_dashboard_component_field`、`un_dashboard_user`、`un_data_source`、`un_data_source_config`、`un_kpi`、`un_kpi_user` | 统一归 `un_ana_*`；仪表盘、组件、数据源及 KPI 是发布配置，查询仍按当前访问者权限运行。 | 分析与仪表盘 |
| `un_print` | 规范为 `un_print_template`，保存模板定义与版本；打印属于模块动作。 | 打印 |

## 首条业务旅程的物理表关系

本批已写入首装 SQL 的表为 `un_plat_account`、`un_sys_system`、`un_sys_tenant`、`un_sys_member`、`un_sys_tenant_member`、`un_sys_role`、`un_sys_member_role`、`un_sys_role_menu`、`un_cfg_module_group`、`un_cfg_module`、`un_cfg_module_field`、`un_cfg_module_menu`、`un_cfg_module_action`、`un_cfg_module_version`、`un_biz_record`、`un_biz_record_data`、`un_biz_record_index`。其中授权与记录表在成员旅程才启用，但同一基线先确定关系、列和索引，避免开发时补出同义表。

```text
平台账号 ──< 系统成员 >── 系统 ──< 租户（创建时同步产生主租户）
                   │                         │
                   └──< 租户成员 >── 角色 ──< 菜单/动作授权
系统/租户 ──< 模块组 ──< 模块 ──< 字段、菜单、动作草稿
                              └──< 不可变发布版本
模块/租户/成员 ──< 业务主记录 ──< 字段原值、可重建查询索引
```

- 每个系统、租户范围内的对象保存显式 `system_id`、`tenant_id` 和创建/修改责任标识；发布版本记录来源模块和版本，运行读已发布快照，草稿不直接影响旧记录。
- `un_biz_record` 保存固定归属字段，包括创建人、修改人、负责人、部门、相关人员、状态、归档/删除和乐观锁版本；动态字段只写通用字段值，不按模块造新表。具体列在 SQL 定稿时与 A.14.8 对齐。
- 业务表不设置外键或触发器。关联标识、必要唯一键和查询索引进单表结构；引用存在性、作用域、授权、状态、删除/转移影响由 `manage` 事务校验并记录审计。删除被引用的部门、字段、模块或成员不能静默留下无效引用。
- 所有新表和字段使用中文 `COMMENT`，时间列统一 `*_time`，Java 属性统一 `*Time`。已发布配置与 Flow 事件不可变；授权撤销由接口实时检查，不靠页面隐藏。

## SQL、Base 和现有测试数据

首装源按后端模块维护：`db/init/V1__platform.sql`、`V2__system.sql`、`V3__module_config.sql`、`V4__runtime_data.sql`。一个模块的当前最终表定义集中在自己的文件；`db-prepare` 确定性生成 `init.sql`、`update.sql`、`final.sql` 和 Flyway 输入。基线冻结后的真实升级才进入 `db/update/`。SQL 空库执行和备注/时间/索引校验通过后，从隔离 MySQL 用 `template_base/mybatis-plus-generator` 生成对应模块 `base`，业务接口与编排只写 `manage`。

`unexamine_r15` 目前只含清理前的测试数据。本步骤不修改它。已在 `unexamine_r15_schema_check_20261019` 中执行四个 Flyway 首装迁移，17 张产品表均有中文表/字段备注，时间列均以 `_time` 结尾，未创建业务外键或触发器；Base 生成器输出四个模块、17 张表、68 个文件。下一步先记录旧测试库结构和行数，按 A.19 允许的测试数据重建隔离库，旧库之外的数据不动。若将来出现真实数据，先提供可复核的备份、迁移、校验和回滚方案，不直接删表重建。
