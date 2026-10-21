# 开发入口与资料边界

- `docs/user_requirement.md` 是用户原始需求，只有用户可以修改；`docs/表意义.md` 和 `docs/old_table1.sql` 是用户原始参考，同样只读，旧 SQL 不得执行。
- 开发方唯一可维护的需求理解是 `docs/requirement-understanding.md`，包含当前产品、目录、分层和工程执行规则。用户补充后直接修改对应正文，不追加变更历史，不新建其他理解、评审、计划、状态或证据文件。
- 开始工作先读最终需求理解及 `template_base/README.md`、`template_base/agents/README.md`、`template_base/rules/README.md`，执行 `python template_base/tools/tb.py execution-context --workspace workspace.json`。从当前需求和实际代码接续，不读取历史版本推导产品需求。
- 工程框架负责完整理解、按业务结果拆分、实现及验证；agent 和 skills 按职责及任务需要使用，不要求固定编排。单批估算不超过 240 分钟，前置依赖、共享迁移和联调保持有序。任务安排留在当前工作上下文中。

# 产品实现

- `back/` 是后端 Maven 根目录，业务模块统一 `examine-*`，`examine-web` 组合成一个服务；`font_web/` 是唯一前端，后端经用户验收后开发前端。
- 每个业务包下 `base` 与 `manage` 平级。生成器提供 Entity、Mapper、IService、ServiceImpl 及真实基础 CRUD；manage 中的业务 Service 实现授权、主数据与自定义数据保存、关联检查和编排；Controller 只调用 Service。
- 基础方法参考 `70a3979a` 的 Controller → Service 调用和 `simple` 模板结构：`queryPageList`、`addOrUpdate`、`queryById`、`information`、`deleteByIds`。不复制旧业务依赖。
- 字段配置时已经拖拽确定表单位置、占位宽度等；新建、编辑和详情复用已保存配置，information 绑定数据，不重新排版。queryById 只读基础记录。
- 先按完整业务理解设计表，在目标库执行并核对后运行生成器，再适配 manage。保留用户给定 94 张表原名；不建数据库外键或触发器，引用约束由业务 Service 执行。
- 使用现有 Docker MySQL 3306、Redis 6379 和独立库 `unexamine_r16`；后端固定 9999，不新增数据库容器或 Nginx。

# 编码与验证

- 表及所有字段必须有中文备注；复杂 SQL、修复和迁移说明用途。时间列使用 `*_time`，Java 属性使用 `*Time`；已有数据变更提供迁移，不删表重建。
- Java 类、方法、业务字段和关键逻辑有适当中文业务注释，HTTP 接口使用 `@Tag`、`@Operation`。简单 CRUD 不添加无用层，不用 JdbcTemplate 绕开 base Service。
- 使用 `python template_base/tools/tb.py execution-verify --workspace workspace.json --target generator` 或 `--target backend` 执行相关真实编译测试；工具不保存验收标记。随后按功能检查真实入口、持久化、授权和失败结果，在回复中如实说明。
