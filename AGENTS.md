# 开发入口与资料边界

- `docs/user_requirement.md` 是用户原始需求，只有用户可以修改；`docs/表意义.md` 和 `docs/old_table1.sql` 是用户原始参考，同样只读，旧 SQL 不得执行。
- 开发方唯一可维护的需求理解是 `docs/requirement-understanding.md`，包含当前产品、目录、分层和工程执行规则。用户补充后直接修改对应正文，不追加变更历史，不新建其他理解、评审、计划、状态或证据文件。
- 开始工作先读最终需求理解及 `template_base/README.md`、`template_base/agents/README.md`、`template_base/rules/README.md`，执行 `python template_base/tools/tb.py execution-context --workspace workspace.json`。从当前需求和实际代码接续，不读取历史版本推导产品需求。
- 工程框架负责完整理解、按业务结果拆分、实现及验证；agent 和 skills 按职责及任务需要使用，不要求固定编排。单批估算不超过 240 分钟，前置依赖、共享迁移和联调保持有序。任务安排留在当前工作上下文中；一个小批次结束后接续下一项依赖工作，不把局部编译或单个 CRUD 当作整项交付，也不要求用户反复发“继续”才推进。

# 产品实现

- `back/` 是后端 Maven 根目录，使用 `un-plat`、`un-sys`、`un-work`、`un-dashboard`、`un-app`、`un-examine`、`un-core`、`un-file`、`un-web`。只有 `un-web` 是 Boot 入口，模块间直接调用本地 Service；`font_web/` 是唯一前端，后端经用户验收后开发前端。
- `un-sys` 包含系统、模块定义及相关 Flow，域内分包；打印与详情、转移、转化、导入导出同属可配置动作，不单独拆 Maven 模块。`un-examine` 负责审批，`un-core` 负责共用能力，`un-file` 提供可复用上传存储接入。
- 每个业务包下 `base` 与 `manage` 平级。生成器提供 Entity、Mapper、IService、ServiceImpl 及真实基础 CRUD；`manage/controller` 接口只调用 `manage/service` 业务服务，枚举、工具类等按实际职责分包。业务 Service 实现授权、主数据与自定义数据保存、关联检查和编排。
- 基础方法参考 `70a3979a` 的 Controller → Service 调用和 `simple` 模板结构：`queryPageList`、`addOrUpdate`、`queryById`、`information`、`deleteByIds`。不复制旧业务依赖。
- 字段配置已确定行、位置和宽度；单独占一行的 30% 字段仍只占该行 30%。新建、编辑、详情复用原配置并叠加登录人角色和租户字段权限；权限过滤不补位或拉伸。information 绑定有权读取的值，queryById 只读基础记录。
- 管理员可配置系统预置动作和自定义动作。转化配置来源与目标模块的字段映射，执行时校验两端动作、字段及数据权限和目标发布规则，事务保存记录、关系与日志。
- 改固定业务表前，先一起阅读用户初始需求、`docs/old_table1.sql` 的旧字段与关系参考及 `docs/表意义.md` 的 94 个原始表名；连同已确认的 `un_sys_role_field` 共 95 个基础表，构成主要业务骨架。按完整业务流程、跨域引用、状态、权限和审计核对当前 SQL、数据库及已有代码；已有 46 张表也必须检查，不能只补缺失表，不能按单个表名或一段局部需求直接动表。优先在这 95 个表内设计必要字段和关联，仅在具体业务无法合理表达时补表，不为未来设想增加复杂结构。模型整体确定后再编辑各业务模块的最终 SQL，在目标库执行并核对，然后运行生成器，最后适配 manage。旧 SQL 只读且不得执行；不建数据库外键或触发器，引用约束由业务 Service 执行。
- 生成器即使只指定本批表，也必须先通过目标库的整套配置表结构检查；缺表、中文备注、时间命名、外键或触发器检查失败时停止，不能改用手工生成或缩小生成配置绕过。机械检查不能代替业务语义核对。
- 使用现有 Docker MySQL 3306、Redis 6379 和独立库 `unexamine_r16`；后端固定 9999，不新增数据库容器或 Nginx。
- 固定字段类型与通用校验放 `un-core`；权限统一计算动作、字段和负责人范围，业务 Service 使用结果过滤和检查。字典基础项逐条保存稳定 ID，新增及改名即生效且不可删除；模块字段引用字典项的选项配置区分草稿与发布，影响旧值时先确定迁移并逐条留痕。Redis 缓存按系统、租户和数据库版本隔离，提交后失效，异常回源；不缓存未提交授权或敏感明文。

# 编码与验证

- 表及所有字段必须有中文备注；复杂 SQL、修复和迁移说明用途。时间列使用 `*_time`，Java 属性使用 `*Time`；已有数据变更提供迁移，不删表重建。
- Java 类、方法、业务字段和关键逻辑有适当中文业务注释，HTTP 接口使用 `@Tag`、`@Operation`。简单 CRUD 不添加无用层，不用 JdbcTemplate 绕开 base Service。
- 使用 `python template_base/tools/tb.py execution-verify --workspace workspace.json --target generator` 或 `--target backend` 执行相关真实编译测试；工具不保存验收标记。随后按功能检查真实入口、持久化、授权和失败结果，在回复中如实说明。
