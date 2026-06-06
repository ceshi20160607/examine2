# DBA-006 init.sql 与迁移检查

- taskId: DBA-006
- 标题: init.sql 与迁移检查
- 负责角色: dba
- 所属大任务/模块: DB 设计 / SQL 初始化
- 目标: 基于完成的 DB 设计生成初始化 SQL，并验证可导入目标 MySQL。
- 输入文件: `docs/db_design.md`、`docs/prd.md`、`docs/api.md`、`docs/service_info.md`
- 输出文件或输出目录: `sql/init.sql`、`docs/db_design.md`

## 详细工作内容

- 根据 `docs/db_design.md` 生成建库建表、索引、约束和生产 seed SQL。
- 使用 `docs/service_info.md` 的数据库连接验证导入。
- 在 `docs/db_design.md` 的初始化数据/迁移检查章节记录迁移检查结果、失败原因和修复建议。

## 完成状态定义

- 默认状态: pending。
- 完成条件: `sql/init.sql` 存在且非空，DB 设计说明与 SQL 一致，且迁移检查结果已回写到 `docs/db_design.md` 指定章节。

## 验收标准

- SQL 字段、注释、默认值、必填状态与 DB 设计一致。
- 导入失败时必须定位环境、权限或 SQL 原因。

## 测试/自检要求

- 在目标 MySQL 执行导入或给出可复现阻塞原因。
- 检查表前缀、唯一索引和 seed 数据。

## 依赖任务

- DBA-005

## 可并行关系

- 不可并行；SQL 是后端和生成器的前置。

## 不允许事项

- 不写后端代码。
- 不用测试样例污染生产初始化 SQL。
- 不重新合并 DBA 设计分片；最终 `docs/db_design.md` 的主体汇总职责属于 DBA-005，本任务只补充迁移检查章节。
