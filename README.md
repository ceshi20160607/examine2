# Unexamine

第 16 次重构从[基础需求](docs/user_requirement.md)和[94 张表的用途](docs/表意义.md)重新开始。当前只建立新工程和生成器，历史接口与验收不计入本轮进度；断点见[实施计划](docs/ai/planning/rebuild-plan.md)。

- `back/`：后端 Maven 多模块工程，入口为 `examine-web`。
- `template_base/`：通用 MyBatis-Plus 生成器、开发职责与检查规则。
- `docs/`：唯一需求源、表用途与本轮计划。
- `font_web/`：后端接口经用户验收后开发的前端。

旧状态保存在本地 Git 标签 `pre-rebuild-16-20261021`。历史 SQL 和旧实现不作为本轮产品依据。
