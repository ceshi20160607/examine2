# Unexamine

本轮从用户的[表定义与用途](docs/表意义.md)重新开始，按[基础需求](docs/user_requirement.md)实现。94 个表名保持原样，物理表随对应业务落地，不用上轮的替代表和旧代码。

- [早期审批 SQL](docs/old_table1.sql) 只供阅读，不执行。
- [Template Base](template_base/README.md) 只保留通用 MyBatis-Plus 代码生成器和简短开发参考。
- [后端工程](back/pom.xml) 位于 `back/`，业务模块为 `examine-*`，入口为 `examine-web`。
- [项目文档](docs/README.md) 保存需求、接口与验收记录；`template_base/` 保存生成器；`font_web/` 为前端根目录。

清理前状态已保存在 Git 标签 `pre-table-first-reset-20261019`，只用于恢复；不能作为本轮需求或完成证据。
