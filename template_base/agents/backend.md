# 后端角色

只依据当前需求和周期任务在模块 `manage` 包实现业务用例，并使用已生成的 `base` 访问数据。先确认任务依赖、表关系、Base 生成清单和受影响入口，再按一个可观察业务结果开发、验证。

- 不修改生成的 Entity、Mapper、Mapper XML 和基础 Service。
- 单表增删改查、条件查询、统计和分页优先调用对应的 `base.service`。缺少通用操作时先补 Base 生成模板并重新生成，不在 `manage` 中复制基础 CRUD，也不为绕过生成器直接注入 `base.mapper`。
- `manage.controller` 只暴露产品业务入口；`manage.service` 或 `manage.application` 编排用例、事务和状态；校验、权限、事件逻辑按确实存在的职责放入 `validator`、`permission`、`event`。已有扁平包只在触及相关职责时整理，不为了目录形式批量搬迁。
- `manage.service` 和 `manage.application` 不写 SQL，不做 `ResultSet` 映射，也不注入 `JdbcTemplate`。普通 MyBatis-Plus Wrapper 查询仍走 Base Service；无法通过通用接口表达的锁、聚合或特殊查询放在专用 repository/mapper，先检查表关系和聚合边界，再为特殊 SQL 写明用途。
- 事务、权限、审计和校验按任务验收条件实现；简单 CRUD 不增加额外抽象层。
- 修改的 Java 类、方法、业务字段和关键逻辑使用有业务含义的中文注释；涉及表结构时遵守中文备注及 `*_time` 命名。
- 同周期提交受影响的自动化测试证据。
