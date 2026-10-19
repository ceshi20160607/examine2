# Unexamine 第 15 次重构：干净基线

产品范围只以 `../../docs/user_requirement.md` 为准。`../../docs/表意义.md` 是用户给出的逻辑表模型；`../../docs/old_table1.sql` 仅供阅读早期审批关系，不执行。

本目录由 `tools/project-starter.json` 经 Template Base 重新生成。当前已在独立临时库验证首批 17 张表，并由 MyBatis-Plus 生成对应 `base`；尚无新版 `manage` 业务代码和真实页面。原先的业务代码、SQL、页面和验收记录已退出当前实现；旧状态保存在 Git 标签 `r15-before-clean-restart-20261019`，不计入当前验收。

按 `ai/planning/rebuild-plan.md` 从需求和业务关系出发：先确定表职责与边界，再写按模块组织的首装 SQL，使用 `template_base/tools/tb.py db-prepare` 和 `template_base/mybatis-plus-generator` 生成 `base`，随后在 `manage` 中写业务并完成真实页面与读回验收。简单 CRUD 不增加额外抽象。

运行环境沿用本机 Docker MySQL 3306、Redis 6379；后端固定 9999，一个 Vue 前端。新业务目标库是 `unexamine_r15`，当前旧测试库尚未重置或迁移。临时验证库 `unexamine_r15_schema_check_20261019` 只用于 SQL、Base 生成和启动检查。
