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
├─ db/             init.sql、update.sql、final.sql 与版本 SQL
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
- 固定产品能力采用物理表还是运行时元数据，必须由当前需求和设计决定；工程初始化器不得提前生成某一种业务模型。
- 一个产品能力只有一个主归属；安全、性能、上线规则是约束和验收任务，不得重复成另一套功能阶段。
- 全量原子任务和依赖未完成前，禁止给出总阶段数、总周期数或总体百分比。
- 质量关口只能使用 `agents/workflow.json` 中的 9 个稳定 ID；项目交付阶段在原子任务、依赖和现有代码评估后按里程碑计算，不能冒充另一套质量关口。
- 测试角色从周期排期开始介入；模拟 API、编译成功和 HTTP 200 不能冒充真实业务完成。
- 开发指令启动持续总控循环；单周期验收只是检查点，不是对话终点。周期证据和状态重算完成后必须自动进入下一可执行周期，直到项目完成或命中 `agents/workflow.json` 的真实停止条件。

常用命令：

```powershell
python template_base/tools/tb.py workspace-validate --workspace workspace.json
python template_base/tools/tb.py project-status --workspace workspace.json
python template_base/tools/tb.py starter-validate --starter <project>/tools/project-starter.json
python template_base/tools/tb.py starter-check --starter <project>/tools/project-starter.json
python template_base/tools/tb.py intake --requirements docs/user_requirement.md --output <project>/ai/requirements/requirement-intake.json
python template_base/tools/tb.py requirements-plan --intake <project>/ai/requirements/requirement-intake.json --output <project>/ai/requirements/analysis-plan.json
python template_base/tools/tb.py requirements-extract-literals --plan <project>/ai/requirements/analysis-plan.json --output-directory <project>/ai/requirements/fragments
python template_base/tools/tb.py requirements-collect --plan <project>/ai/requirements/analysis-plan.json --fragment <repeat-for-each-fragment> --output <project>/ai/requirements/fragment-index.json
python template_base/tools/tb.py requirements-consolidate --fragment-index <project>/ai/requirements/fragment-index.json --aliases <project>/ai/requirements/requirement-consolidation.json --output <project>/ai/requirements/requirement-analysis.json
python template_base/tools/tb.py architecture-validate --design <project>/ai/architecture/design.json
python template_base/tools/tb.py use-cases-validate --catalog <project>/ai/requirements/use-cases.json
python template_base/tools/tb.py tasks-validate --catalog <project>/ai/planning/task-catalog.json
python template_base/tools/tb.py tasks-generate --plan <project>/ai/planning/task-layering.json --project-root <project> --output <project>/ai/planning/task-catalog.json
python template_base/tools/tb.py schedule-generate --plan <project>/ai/planning/schedule-plan.json --output <project>/ai/planning/task-graph.json
python template_base/tools/tb.py task-graph-validate --graph <project>/ai/planning/task-graph.json
python template_base/tools/tb.py db-prepare --project-root <project>
powershell -File <project>/tools/regenerate-base.ps1
```

`workspace-validate` 和 `project-status` 会逐周期校验 `ai/evidence/CYCLE-xxx.json`：任务必须与排期完全一致，所有检查和已记录旅程必须通过，排期声明真实入口用例且包含前端任务的周期至少有一个真实入口旅程，证据路径必须存在，并且只能按排期顺序形成连续完成前缀。项目完成周期数、百分比和状态日期由这条证据链计算，不能通过单独修改状态文件自报进度。

`requirements-extract-literals` 只建立逐源区块、无遗漏的候选提取层，输出带有 `source-literal/unreviewed` 标记；它不能替代需求角色的语义拆分、总控消重或最终 `requirements-validate`。

`requirements-consolidate` 只执行人工审核后的语义别名，确定性合并来源块、验收条件和决策并生成最终原子需求；它不会根据文本相似度自动猜测，也不允许别名链。

`architecture-validate` 直接绑定最终需求分析，校验每条原子需求有且仅有一个能力归属、上下文和能力依赖引用有效且依赖无环；架构不反向依赖尚未生成的用例目录。

`use-cases-validate` 同时绑定最终需求与能力架构，要求用例不跨越唯一能力归属、步骤顺序连续，并覆盖全部原子需求。用例必须描述可见结果、持久化结果、授权结果和失败结果，不能只描述接口状态。

`tasks-validate` 在排期之前校验原子任务目录：每项任务必须绑定同一能力下的需求和用例，每个用例既有实现任务也有周期末集成验收任务，全部需求均被任务覆盖。任务目录不含工时和周期；现有代码逐项评估后，才在 `task-graph.json` 中写入实际分钟、依赖、阶段和周期。

`tasks-generate` 只展开人工审核的能力分层计划：能力级数据库/Base 任务、用例级 design/manage/frontend/operations 任务以及每个用例唯一的真实入口验收任务。任务验收直接继承用例的可见结果、持久化、授权和失败分支，避免人工复制后漂移。

`schedule-generate` 只在现有代码逐项审查后使用审核过的分钟基线、能力复杂度系数和少量任务覆盖值；它保证只有一次全项目 Base 生成，按依赖拓扑把每项任务恰好装入一个不超过 240 分钟的周期。`task-graph-validate` 会复算周期分钟、任务覆盖、依赖方向、阶段归属和真实入口用例集合。

初始化器只生成可构建的工程外壳，不生成具体业务表、实体、接口或页面。业务 Base 必须来自项目 SQL 经真实 MySQL 和 MyBatis-Plus Generator 生成。
