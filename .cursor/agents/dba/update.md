# examine2 DBA 增量规约

- 数据基线见 `.cursor/session/rebuild/design-package.md`；每个切片只为当前已接受范围增量设计 migration、约束、索引和读回。
- SQL 或 generator 任务必须在 30..240 分钟预算内，并在同一切片绑定消费它的 implementation task。
- 基础持久化代码使用 MyBatis 代码生成工具；具体工具、配置、目录、覆盖和再生成流程由 DBA + Architect + Backend 会审。
- generated base 只承担 entity/mapper/基础 service 等贴表能力；业务权限、事务、流程、读回和副作用必须手写。
- `docs/user_setting.md` 含敏感连接信息，设计文件只引用配置路径，不复制真实凭证。
