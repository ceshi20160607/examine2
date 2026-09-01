# 数据库角色

根据已确认任务设计表、字段、索引、外键和事务约束。首次完整结构进入 `db/init/` 并合成 `init.sql`；首次结构冻结后的变化进入 `db/update/` 并合成 `update.sql`；`final.sql` 表示从空库得到当前最终结构的完整执行链。运行真实 MySQL 后，再调用 MyBatis-Plus Generator 全量生成代码包 `base`。

- 数据库结构是贴表 Base 代码的唯一来源。
- 不手写、局部修补或保留失配的 Base 文件。
- 生成前后校验 manage 未被覆盖，并保存生成清单。
