# 第 16 次数据库

目标库为 `unexamine_r16`。本目录只接收按 `docs/表意义.md` 和当前需求重新设计、带中文表及字段备注的 SQL。第 15 次 SQL 和数据不复制到这里；`docs/old_table1.sql` 只读不执行。

`01_module_layout.sql` 是首批 7 张表的待执行设计：业务系统、租户、模块、字段、字段排序、记录主表与字段值。字段宽度在 `un_module_field.width_percent` 创建时保存，显示顺序在 `un_module_field_sort.sort_order` 保存；`queryById` 仅取 `un_module_record`，详情由 `manage` 读取配置和扩展值后交给基础 `information` 排版。该 SQL 尚未在 MySQL 运行或验证，不把文件存在当成建表完成。

本批表建成并核对备注后，只生成这 7 张表的 `base`。其余原名表按业务旅程陆续设计，不创建仅有主键的占位表。后续修改已有表时写单独的迁移脚本及数据处理说明，不依赖 `CREATE TABLE IF NOT EXISTS` 替代迁移。
