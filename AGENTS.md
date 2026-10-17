# 工作区边界

- `docs/user_requirement.md` 是唯一产品需求源。
- `docs/表意义.md` 是用户提供的基础表模型参考；`docs/old_table1.sql` 只供研究早期审批逻辑，含旧数据与删表语句，不得执行。
- `template_base/` 保存通用工程工具、MyBatis-Plus 生成器、Agent 职责和校验规则；`projects/unexamine/` 是唯一当前产品实现。
- 旧实现保存在 Git 标签 `pre-rebuild-15-20261017`，不得作为本轮需求或完成证据重新引入。

# 开发规约

- 先看需求原文与 `projects/unexamine/ai/planning/rebuild-plan.md`，按当前业务旅程设计必要表，再通过 `db-prepare` 和 `template_base/mybatis-plus-generator` 生成 `base`，业务代码写入 `manage`，最后实现真实页面并验收。
- 不为尚未实施的能力预建表或多余抽象。每批工作围绕一个可观察结果，估算不超过 240 分钟；前置依赖、共享迁移和测试资源保持串行。
- 新建和修改的表及所有字段必须有中文备注；复杂 SQL 和迁移说明用途。数据库时间列用 `*_time`，Java 属性用 `*Time`。已有数据变更提供迁移方案，不直接删表重建。
- Java 类、方法、业务字段和关键逻辑写有意义的中文注释；沿用现有模块结构，简单 CRUD 不增加无用层。
- 复用本机现有 Docker MySQL（3306）和 Redis（6379）；后端固定 9999；一个前端、一个后端，不增加 Nginx 或新的数据库容器。新实现使用隔离库 `unexamine_r15`，不改旧库。
- 交付前检查注释、时间命名和不必要的封装，执行相关编译、测试与真实入口验证，说明结果。

# 当前接续

- 重开会话先读 `projects/unexamine/ai/planning/rebuild-plan.md` 和 `projects/unexamine/ai/planning/rebuild-state.json`，运行 `python template_base/tools/tb.py project-status --workspace workspace.json`。
- 实施当前步骤前运行 `python template_base/tools/tb.py execution-context --workspace workspace.json`。业务结果、持久化、授权和失败结果未验收时保持未完成。
- 只在当前步骤真实验证后运行 `execution-verify` 并更新准确断点。工程校验或局部测试不等于全产品完成。
