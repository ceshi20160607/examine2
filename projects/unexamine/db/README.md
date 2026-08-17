# 数据库唯一来源

本目录与 `backend` 平级，避免数据库设计隐藏在后端代码里。

- `base/`：首次正式基线包含的版本化 SQL 源文件。全量需求、任务和表结构评审完成前均为候选草稿。
- `update/`：正式基线冻结后新增的版本化升级 SQL，版本号必须大于 `base/` 中最后一个版本。
- `base.sql`：由 `base/` 确定性合成的基础全量 SQL。
- `update.sql`：由 `update/` 确定性合成的后续变更 SQL。
- `all.sql`：由 `base.sql + update.sql` 合成，供空库一次性创建最终结构。
- `migration/`：生成给 Flyway 使用的版本文件，禁止手改。

表结构变更顺序固定为：先改 SQL 源文件，运行 `python template_base/tools/tb.py db-prepare --project-root projects/unexamine`，在 MySQL 执行后，再运行 MyBatis-Plus Generator 全量替换对应模块的 `base`。业务代码只能写在模块的 `manage`。
