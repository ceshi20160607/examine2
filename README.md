# examine2

examine2 包含可配置业务系统产品及为其开发服务的工程框架。

- [用户原始需求](docs/user_requirement.md)：只由用户维护。
- [最终需求理解](docs/requirement-understanding.md)：开发方唯一维护的产品与实现理解。
- [工程框架](template_base/README.md)：需求理解、职责分工、业务拆分、生成和验证流程。
- `back/`：后端 Maven 多模块工程，唯一服务入口为 `examine-web`。
- `font_web/`：后端接口经用户验收后开发的唯一前端。

`docs/表意义.md`、`docs/old_table1.sql` 是用户提供的只读参考，不作为并列的开发理解版本。工程配置位于 `workspace.json`，不存储中间计划、状态或变更记录。
