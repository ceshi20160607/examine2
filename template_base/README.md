# Template Base

只保留通用基础工具：

- `mybatis-plus-generator/`：从已建的 MySQL 业务表生成 `base` 层 Entity、Mapper、IService 和 ServiceImpl。Service 模板须实现分页、保存、按主键读取、带布局详情和批量删除；生成结果必须编译验证。
- `agents/`：开发职责参考。
- `rules/`：代码与数据库的简短检查规则。
- `tools/tb.py`：读取工作区计划与断点，并在真实验证后记录证据路径。

产品需求、表名、SQL、业务代码和项目进度放在 `docs/` 与 `back/`，生成器不创建表，也不生成业务判断。第 16 次以 `docs/ai/planning/rebuild-plan.md` 为当前断点。
