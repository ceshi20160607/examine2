# Template Base

`template_base` 是可复制到不同需求上的通用工程框架，只保存确定性工具、生成器、校验规则和通用 Agent 职责，不保存 Unexamine 的产品任务或进度。

- `rules/`：JSON Schema 等机器校验规则；不是业务合同，也不是 Agent 技能。
- `agents/`：通用 Agent 职责和协作流程。
- `tools/`：可重复执行的工程命令。
- `mybatis-plus-generator/`：数据库驱动的 Base 代码生成器。

标准项目结构：

```text
projects/<project>/
├─ backend/
├─ frontend/
├─ db/             base.sql、update.sql、all.sql 与版本 SQL
├─ deploy/
├─ tools/
└─ ai/             该项目专属需求、任务、角色、决策、状态和证据
```

固定开发链路：

```text
唯一 user_requirement.md
  -> 全量需求索引与互斥分析包
  -> 唯一能力归属、原子用例和验收
  -> 原子任务、依赖与现有代码评估
  -> 项目 db 表结构
  -> MySQL + MyBatis-Plus Generator 全量生成模块 base
  -> 模块 manage 业务逻辑 + 真实前端
  -> 每个最多 240 分钟周期的测试与真实入口验证
```

关键边界：

- 只有表结构、贴表 Entity/Mapper/基础 Service 和机械骨架由工具生成；Agent 只写业务判断。
- 数据库先改 SQL，再运行代码生成器；不得手工修补 `base`。
- 业务模块采用固定表还是运行时元数据，必须由当前需求和设计决定；工程初始化器不得提前生成某一种业务模型。
- 一个产品能力只有一个主归属；安全、性能、上线规则是约束和验收任务，不得重复成另一套功能阶段。
- 全量原子任务和依赖未完成前，禁止给出总阶段数、总周期数或总体百分比。
- 执行关口只能使用 `agents/workflow.json` 中的 9 个稳定 ID，不得另建汇报阶段口径。
- 测试角色从周期排期开始介入；模拟 API、编译成功和 HTTP 200 不能冒充真实业务完成。

常用命令：

```powershell
python template_base/tools/tb.py workspace-validate --workspace workspace.json
python template_base/tools/tb.py project-status --workspace workspace.json
python template_base/tools/tb.py starter-validate --starter <project>/tools/project-starter.json
python template_base/tools/tb.py starter-check --starter <project>/tools/project-starter.json
python template_base/tools/tb.py intake --requirements docs/user_requirement.md --output <project>/ai/requirements/requirement-intake.json
python template_base/tools/tb.py requirements-plan --intake <project>/ai/requirements/requirement-intake.json --output <project>/ai/requirements/analysis-plan.json
python template_base/tools/tb.py db-prepare --project-root <project>
powershell -File <project>/tools/regenerate-base.ps1
```

初始化器只生成可构建的工程外壳，不生成具体业务表、实体、接口或页面。业务 Base 必须来自项目 SQL 经真实 MySQL 和 MyBatis-Plus Generator 生成。
