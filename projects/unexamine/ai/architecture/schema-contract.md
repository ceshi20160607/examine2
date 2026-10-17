# 固定能力与通用业务数据契约（设计中）

依据 `docs/user_requirement.md` A.16、A.18、A.19；`docs/表意义.md` 用于核对业务含义，不按表名清单盲目建表。此文档记录当前第 15 次重构的关系判断，不能当成已实施 SQL 或全产品完成证据。

## 当前 28 张表的业务责任

| 业务域 | 现有表 | 必须独立保存的事实 |
| --- | --- | --- |
| 平台账号 | `un_plat_account` | 登录身份、口令哈希、账号状态；与系统成员身份分开 |
| 系统与租户 | `un_sys_system`、`un_sys_tenant` | 系统归属和系统内租户；租户不能越过系统边界 |
| 成员与授权 | `un_sys_member`、`un_sys_tenant_member`、`un_sys_role`、`un_sys_member_role`、`un_sys_role_menu` | 账号进系统、成员进租户、角色授予、角色菜单和基础动作授权，是四种不同关系 |
| 模块草稿与版本 | `un_module_group`、`un_module`、`un_module_field`、`un_module_menu`、`un_module_action`、`un_module_version` | 组、模块、字段、页面入口、动作草稿和不可变发布快照；不为每个自定义模块造 Java 类或物理业务表 |
| 通用业务记录 | `un_module_record`、`un_module_record_data`、`un_module_record_index` | 主记录与负责人、原始字段值、用于筛选的类型化索引；索引可重建，不替代字段原值 |
| Flow | `un_flow`、`un_flow_node`、`un_flow_version`、`un_flow_example`、`un_flow_task`、`un_flow_event` | 流程草稿、版本、运行轮次、当前待办和不可变事件；当前仅实现指定成员单节点 |
| 文件 | `un_file_object`、`un_file_reference` | 文件元数据和业务记录引用；内容不放在数据库中 |
| 数据交换 | `un_exchange_job`、`un_exchange_import_job` | 异步导出与 Excel 导入预检查、确认、失败状态；名称应明确导入或导出 |
| 站内消息 | `un_message` | 系统收件人、通用通知标题和目标；目标内容由原业务接口实时鉴权 |

当前 28 张表没有重复的同义主表；不因为表数多而合并 Flow 实例与任务、原始字段值与查询索引、系统成员与租户成员。当前 `un_module_record_data` 不是第二张业务主表。`un_module_version` 和 `un_flow_version` 保存配置快照，不能与草稿表合并。可执行的数据操作仍由 manage 层事务校验系统、租户、角色、记录负责人和业务状态，不依赖数据库外键或触发器。

## 需要统一的表名和源码归属

| 当前表 | 候选统一表名 | 关系是否改变 |
| --- | --- | --- |
| `un_module_group`、`un_module`、`un_module_field`、`un_module_menu`、`un_module_action`、`un_module_version` | `un_cfg_module_group`、`un_cfg_module`、`un_cfg_module_field`、`un_cfg_module_menu`、`un_cfg_module_action`、`un_cfg_module_version` | 否；保持原主键与业务标识 |
| `un_module_record`、`un_module_record_data`、`un_module_record_index` | `un_biz_record`、`un_biz_record_data`、`un_biz_record_index` | 否；保持原主键、字段和索引语义 |
| `un_exchange_job` | `un_exchange_export_job` | 否；消除导出/导入名称歧义 |
| `un_message` | `un_msg_message` | 否；平台消息将另归 `un_plat_*` |

其余 17 张现有表保留表名。统一命名后，Java Base 对应 `platform`、`system`、`moduleconfig`、`runtimedata`、`flow`、`file`、`exchange`、`message` 模块；不保留新旧表名并行读写。SQL 首装源应按这些后端模块拆开，并在每个模块中维护该模块当前最终结构。生成物 `db/init.sql`、`db/update.sql`、`db/final.sql` 由 `template_base/tools/tb.py db-prepare` 确定性生成，生成的 MyBatis-Plus Base 不承载业务编排。

## 后续固定能力必须先定义的关系

| 能力域 | 关系和边界，进入物理表设计前必须确定 |
| --- | --- |
| 平台/系统组织与权限 | 部门层级、人员归属、角色和动作/字段/数据范围授权必须带平台或系统/租户作用域；撤销后实时生效，删除被引用部门时先分析影响并转移 |
| 模块配置与业务数据 | 关联字段、子表、规则、唯一约束、页面配置、历史、共享和软删除均使用通用记录模型；字段版本与记录历史可追溯 |
| 平台/系统 Flow | 定义、节点、连线、版本、绑定、实例、任务候选人、动作、异常、事件和定时任务跨模块复用；旧实例保留发起时快照 |
| 应用访问 | 平台/系统应用、凭据、发布授权、资源动作与字段范围、主体身份、请求幂等/防重放和调用审计构成唯一跨系统及外部桥梁 |
| 任务/工作日志 | 普通任务、项目任务和用户手动创建的每日工作日志分开；AI 不自动创建工作日志 |
| 消息/待办 | 平台与系统消息作用域分开，待办聚合 Flow、工作任务等来源；消息仅保存通知与目标，不复制敏感业务正文 |
| 仪表盘/KPI | 已发布模块或已发布数据源的配置和查询权限相连；当前访问者权限决定预览、下钻、导出与 AI 结果 |
| 文件/打印/AI/审计/运维 | 文件对象可被多个业务功能引用；打印保存模板版本和输出规则；AI 配置与操作审计、备份恢复和后台作业独立建模 |

这些关系来自唯一需求源，尚未形成完整固定平台能力的定稿 SQL。不得以这张表自动生成空业务表；每张表需有明确读写场景、唯一/索引约束、中文表和字段备注、`*_time` 命名以及空库验证。

## 候选数据迁移约束

当前 `unexamine_r15` 已存放本轮验收数据。改名不改变主键、列、索引和记录内容。实施时先在新隔离库演练：复制当前结构与数据，记录 28 张表的行数和逐表主键/业务键摘要，执行 `RENAME TABLE` 或等价迁移，统一 Java 映射和 SQL，运行 Base 生成及构建，再对迁移前后行数、引用标识、账号登录和同一候选旅程读回。应用切换数据库连接仅在演练通过后进行；原库保留作为回退点。未来若含真实业务数据，还须有停写窗口、备份、回滚与校验记录。不能通过直接删除现有表换取空库通过。
