# Template Base

只保留通用基础工具：

- `mybatis-plus-generator/`：从现有 MySQL 表生成 `base` 层的 Entity、Mapper、IService 和 ServiceImpl；`templates/service*.java.ftl` 实现基础分页、保存、读取、删除。特殊业务写在平级的 `manage`，Controller 只调用 Service。完整分层规约见产品工程的 `docs/backend-layering.md`。
- `agents/`：开发职责参考。
- `rules/`：代码与数据库的简短检查规则。
- `tools/tb.py`：读取工作区计划与断点，并在真实验证后记录证据路径。

产品需求、表名、SQL、业务代码和项目进度均放在具体项目中。生成器不创建表，也不生成业务判断。
