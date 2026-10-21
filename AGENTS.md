# 第 16 次重构开发规约

## 依据与目录

- 产品需求只从 `docs/user_requirement.md` 读取；94 张表的原名与用途只从 `docs/表意义.md` 读取。`docs/old_table1.sql` 仅供研究早期字段和审批，禁止执行。旧实现和旧验收已归档于 Git 标签 `pre-rebuild-16-20261021`，不作为新需求或完成证据。
- `back/` 为后端 Maven 工程根目录，`docs/` 放需求和验收，`template_base/` 放通用生成器与工程规则，`font_web/` 是前端根目录。前端须等后端接口经用户验收后开发。
- 先读 `docs/ai/planning/rebuild-plan.md` 与 `rebuild-state.json`，运行 `python template_base/tools/tb.py project-status --workspace workspace.json`；实施当前步骤前运行 `execution-context`。只有真实结果验证后才运行 `execution-verify`。

## 分层与生成

- 对照提交 `70a3979a` 的 Controller → Service → Mapper 调用方向，以及 `examine-core/src/main/resources/simple` 的 Controller、Service、ServiceImpl 模板。只学习结构，不恢复旧 CRM 依赖、旧产品逻辑或已知错误。
- 每个业务模块 `examine-*` 在 `com.unique.unexamine.<业务包>` 下平级放 `base` 与 `manage`。生成器把 Entity、Mapper、IService、ServiceImpl 放 `base`。基础 Service 必须真实实现 `queryPageList`、`addOrUpdate`、`queryById`、带字段布局的 `information`、`deleteByIds`；生成结果需验证，不能留下空接口/空实现。
- Controller 只做路由、参数绑定、输入校验、调用 Service 和返回响应，不注入 Mapper，不构造查询、不直接保存/删除、不写授权和跨表业务逻辑。特殊规则、会话授权和跨表编排放同业务包 `manage`，只有真实需要才新增辅助类。简单 CRUD 不加额外包装层。
- 业务模块由 `examine-web` 组合为一个服务。平台归 `examine-plat`，系统/租户归 `examine-sys`，仪表盘/KPI 归 `examine-dashboard`；不建 `foundation`、`identity` 等聚合模块，不留多余包路径。

## 数据和交付

- 按需求和业务旅程设计表，不复制第 15 次 SQL；94 张表的原名和用途保持。新增/修改表及所有字段有中文备注；复杂查询与迁移说明用途。数据库时间列使用 `*_time`，Java 属性使用 `*Time`。已有数据变更提供迁移，不直接删表重建。
- Java 类、方法、业务字段和关键逻辑有适当中文注释，解释业务含义和限制；接口用 `@Tag`、`@Operation` 说明用途。
- 使用本机现有 Docker MySQL 3306 与 Redis 6379，后端端口固定 9999；第 16 次只使用 `unexamine_r16`，一个后端、一个前端，不加 Nginx 或新容器。
- 每批工作围绕一个可观察结果，估算不超过 240 分钟。检查注释、时间命名、不必要封装和 Controller 分层；执行相关编译、测试与真实入口验证，如实记录未完成项。工程校验不等于业务验收。
