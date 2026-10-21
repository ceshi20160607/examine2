# 工作区边界

- `docs/user_requirement.md` 是唯一产品需求源。
- `docs/表意义.md` 是用户提供的基础表模型参考；`docs/old_table1.sql` 只供研究早期审批逻辑，含旧数据与删表语句，不得执行。
- `back/` 是后端 Maven 工程根目录，`back/examine-*` 是业务模块；仓库根目录的 `docs/` 保存需求、设计和验收记录，`template_base/` 保存通用工程工具、MyBatis-Plus 生成器、Agent 职责和校验规则，`font_web/` 是前端根目录。
- 旧实现保存在 Git 标签 `pre-rebuild-15-20261017`，不得作为本轮需求或完成证据重新引入。

# 开发规约

- `base` 放生成器产出的 Entity、Mapper、IService 和 ServiceImpl，基础 CRUD 方法由模板实际生成实现。`manage` 只写特殊规则和业务编排；Controller 只做 HTTP 参数绑定、校验和调用 Service，不直接注入 Mapper 或编写 SQL/查询条件、持久化、事务和业务规则。参考 `70a3979a` 中 Controller → Service → Mapper 的调用方向及 `examine-core/src/main/resources/simple` 的 Controller、Service、ServiceImpl 模板结构；不照搬旧 CRM 依赖、错误或旧产品逻辑。
- 具体分层判定、生成器职责和迁移检查见 `docs/backend-layering.md`；再次生成不得覆盖手写业务逻辑。
- 先看需求原文与 `docs/ai/planning/rebuild-plan.md`，按当前业务旅程设计必要表，再通过 `db-prepare` 和 `template_base/mybatis-plus-generator` 生成各业务模块自己的 `base`，业务代码写在同一模块、同一业务包下与 `base` 平级的 `manage`，最后实现真实页面并验收。表到源码目录的归属以 `back/base-codegen.json` 为准。
- 后端按 `docs/business-modules.md` 的业务所有权放入 `back/examine-*` Maven 子模块，由 `examine-web` 组合成一个服务。平台与系统分别归 `examine-plat`、`examine-sys`，仪表盘与 KPI 归 `examine-dashboard`；不再建立 `foundation` 或 `identity` 聚合模块。迁移包路径时连同空的旧目录一起清理；每个业务模块源码树只保留 `com/unique/unexamine/<业务包>/base` 与同级 `manage`，不得留下顶层 `com/unique/unexamine/manage/<业务包>`。简单 CRUD 不增加无用层，只在真实跨模块调用时建立最小接口。
- 不为尚未实施的能力预建表或多余抽象。每批工作围绕一个可观察结果，估算不超过 240 分钟；前置依赖、共享迁移和测试资源保持串行。
- 新建和修改的表及所有字段必须有中文备注；复杂 SQL 和迁移说明用途。数据库时间列用 `*_time`，Java 属性用 `*Time`。已有数据变更提供迁移方案，不直接删表重建。
- Java 类、方法、业务字段和关键逻辑写有意义的中文注释；沿用现有模块结构，简单 CRUD 不增加无用层。
- 复用本机现有 Docker MySQL（3306）和 Redis（6379）；后端固定 9999；一个前端、一个后端，不增加 Nginx 或新的数据库容器。新实现使用隔离库 `unexamine_r15`，不改旧库。
- 交付前检查注释、时间命名和不必要的封装，执行相关编译、测试与真实入口验证，说明结果。后端需用户验收后再开发前端。

# 当前接续

- 重开会话先读 `docs/ai/planning/rebuild-plan.md` 和 `docs/ai/planning/rebuild-state.json`，运行 `python template_base/tools/tb.py project-status --workspace workspace.json`。
- 实施当前步骤前运行 `python template_base/tools/tb.py execution-context --workspace workspace.json`。业务结果、持久化、授权和失败结果未验收时保持未完成。
- 只在当前步骤真实验证后运行 `execution-verify` 并更新准确断点。工程校验或局部测试不等于全产品完成。
