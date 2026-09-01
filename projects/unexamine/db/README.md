# 数据库唯一来源

本目录与 `backend` 平级，避免数据库设计隐藏在后端代码里。

- `init/`：已冻结的首次完整表结构，共 18 个有序迁移、150 张表。
- `update/`：首次结构冻结后新增的版本化升级 SQL，版本号必须大于 `init/` 中最后一个版本。
- `init.sql`：由 `init/` 确定性合成的首次完整结构。
- `update.sql`：由 `update/` 确定性合成的后续变更 SQL。
- `final.sql`：由 `init.sql + update.sql` 确定性合成，表示从空库得到当前最终结构的完整执行链。
- `migration/`：生成给 Flyway 使用的版本文件，禁止手改。

首次基线已经从空 MySQL 8.4 执行成功，并生成 18 个模块、600 个 `base` 文件。当前 `final.sql` 由 18 个冻结基线迁移和 8 个后续升级迁移组成，共 26 个迁移；最新结构共 153 张表，并生成 18 个模块、612 个 `base` 文件。后续结构变化仍只能新增到 `update/`，运行 `python template_base/tools/tb.py db-prepare --project-root projects/unexamine`，在 MySQL 执行后，再运行 MyBatis-Plus Generator 全量替换受影响模块的代码包 `base`。业务代码只能写在模块的 `manage`。

自定义模块始终使用 `cfg_*` 定义表与 `biz_record`、`biz_record_value`、`biz_record_index`、`biz_record_relation` 等通用运行时表；系统页面创建模块时不得生成模块专用物理表或动态 Java。
