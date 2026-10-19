# Unexamine 第 15 次重构

本工作区按照[产品需求](docs/user_requirement.md)和用户提供的[表模型参考](docs/表意义.md)从干净工程外壳重新实施。当前版尚无已验收的业务功能；旧实现及其验收记录只保存在 Git 标签 `r15-before-clean-restart-20261019`，不计入当前进度。

- [早期审批 SQL](docs/old_table1.sql)：只阅读审批关系，不执行其中的删表和旧数据脚本。
- [工程模板](template_base/README.md)：保留通用工具、Agent 规则与 MyBatis-Plus Base 生成器。
- [当前计划](projects/unexamine/ai/planning/rebuild-plan.md)和[表关系契约](projects/unexamine/ai/architecture/table-model.md)：先确定业务关系，再写 SQL、生成 Base、实现 `manage` 与真实页面。

当前实现目录为 `projects/unexamine/`。后端固定 9999，复用本机现有 Docker MySQL 3306 和 Redis 6379；隔离库为 `unexamine_r15`。当前旧测试库尚未修改。
