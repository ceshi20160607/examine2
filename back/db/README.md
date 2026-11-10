# 数据库源码

按后端业务模块维护当前建表 SQL，汇总为 `final.sql`；已有数据的结构升级写入可执行的 `update.sql`。SQL 是产品实现源码，不在此保存设计讨论、草案或变更说明文档。

`python back/db/build_sql.py --refresh-init` 仅用于首装基线尚未冻结或隔离空库重建时，从各模块源 SQL 更新 `init.sql`。已有数据后只维护模块当前定义及 `update.sql` 中的可执行迁移；运行 `python back/db/build_sql.py` 从当前模块源 SQL 合成空库使用的 `final.sql`。`update.sql` 仅对相应已有库执行，不叠加到空库建表脚本，避免重复创建字段和索引。

设计依据为 `docs/requirement-understanding.md` 和用户原始表定义。日常开发库为 `unexamine_r16`；`final.sql` 不创建或指定数据库，空库验收时须先建立新的项目专用库并明确选中该库执行。`docs/old_table1.sql` 只读，不执行。
