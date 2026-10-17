# Unexamine 第 15 次重构

本工作区按用户给出的工程框架重新实现可配置业务系统。当前可从页面完成账号注册、创建系统、模块组与模块、基础字段发布、角色菜单授权、添加已有账号、业务记录录入查询、单节点审批配置与办理、记录附件上传下载，以及按独立权限和当前筛选异步导出 CSV。**整个产品尚未完成**，复杂流程、Excel 导入、应用访问、移动端与其他完整交付项仍需实施和验收。

- [产品需求](docs/user_requirement.md)：唯一产品范围与验收来源。
- [表模型参考](docs/表意义.md)：用户提供的 94 张逻辑表，按业务阶段选用。
- [早期审批 SQL](docs/old_table1.sql)：只作审批设计参考，不执行其中的删表或数据插入。
- [工程模板](template_base/README.md)：通用规则、项目生成器和 MyBatis-Plus Base 生成器。
- [当前实施顺序](projects/unexamine/ai/planning/rebuild-plan.md)：先做基础业务旅程，再增加流程和其他能力。

当前实现位于 `projects/unexamine/`。运行 `projects/unexamine/tools/run-local.ps1` 后，前端为 <http://127.0.0.1:15173/>，Java 后端固定 9999。复用已有 Docker MySQL/Redis，使用隔离库 `unexamine_r15`，没有 Nginx 或 Compose。上一版工作区已保存在 Git 标签 `pre-rebuild-15-20261017`。
