# 数据库角色

根据已确认任务设计表、字段、索引、外键和事务约束。先修改 `db/base/` 或 `db/update/`，再合成 SQL、运行真实 MySQL，并调用 MyBatis-Plus Generator 全量生成 `base`。

- 数据库结构是贴表 Base 代码的唯一来源。
- 不手写、局部修补或保留失配的 Base 文件。
- 生成前后校验 manage 未被覆盖，并保存生成清单。
