# Template Base

`template_base` 是一个“可执行的软件生产底座”，不是多 Agent 会议和文档模板集合。

它把工作明确分成两类：

- 确定性工作由程序完成：项目初始化、数据库 migration、Java `base` 代码、标准 CRUD API、前端类型和标准管理页面。
- 需要判断的工作才交给 Agent：产品语义、业务规则、权限、状态流转、跨表事务和非标准交互。

当前 v0.1 开发目标是证明最小链路：

```text
user_require.md
  -> requirement-intake.json（源区块与哈希）
  -> requirement-plan.json（互斥工作包）
  -> requirement-analysis.json（原子需求、覆盖、决策与合同绑定）
  -> 已确认的 project.contract.json
  -> deterministic generator
  -> 可构建后端 + 可构建前端 + migration
  -> 真实 MySQL/Redis/API/Browser journey
```

在进入这条链路之前，必须先确定应用形态。`architecture-baseline.json` 用于区分“固定领域系统”和“运行时可配置平台”。后者的管理员配置模块必须由运行时元数据引擎解释执行，不能套用静态 CRUD 示例为每个模块生成 Java 类和物理业务表。

## 立即可用的命令

```powershell
python template_base/tools/tb.py architecture-ready --baseline projects/unexamine/architecture-baseline.json
python template_base/tools/tb.py workspace-validate --workspace workspace.json
python template_base/tools/tb.py intake --requirements template_base/examples/work-order/user_require.md --output template_base/examples/work-order/requirement-intake.json
python template_base/tools/tb.py requirements-plan --intake template_base/examples/work-order/requirement-intake.json --output template_base/examples/work-order/requirement-plan.json
# 大型产品按当前纵向切片分析，未选区块保持可见的延期状态：
python template_base/tools/tb.py requirements-plan --intake projects/unexamine/requirements/requirement-intake.json --scope projects/unexamine/requirements/slice-01.scope.json --output projects/unexamine/requirements/slice-01.plan.json
python template_base/tools/tb.py requirements-validate --analysis template_base/examples/work-order/requirement-analysis.json
python template_base/tools/tb.py validate --contract template_base/examples/work-order/project.contract.json
python template_base/tools/tb.py generate --contract template_base/examples/work-order/project.contract.json --output template_base/examples/work-order/generated
python template_base/tools/tb.py check --contract template_base/examples/work-order/project.contract.json
powershell -File template_base/tools/verify_sample.ps1
python template_base/tools/tb.py status --contract template_base/examples/work-order/project.contract.json --output template_base/examples/work-order/generated
python template_base/tools/tb.py release-check --contract template_base/examples/work-order/project.contract.json --output template_base/examples/work-order/generated
```

`check` 会在两个临时目录中重复生成并比较所有文件哈希。相同输入产生任何差异都会失败。

`intake` 和 `requirements-plan` 不调用模型。长需求会先被切成有行号、哈希和范围上限的互斥工作包；Agent 只能分析被分配的包，`requirements-merge` 要求每个包恰好提交一次。

大型产品不要求首期分析全部需求。范围文件通过完整标题路径和显式区块选择当前纵向切片，其余区块会统计为 deferred；范围、索引或原需求任一变化都会让计划和分析失效。这样既不漏掉全量需求，也不为远期能力提前消耗分析成本。

`status` 从现有工件计算阶段，不接受 Agent 自报状态。`release-check` 只有在工程证据与用户明确验收都绑定到当前合同和生成树时才返回成功。

## 硬边界

- `user_require.md` 是产品来源；工程规则不能追加到产品需求中。
- Agent 不能直接从长需求跳到编码，必须先形成机器可验证的项目合同。
- 在需求拆分前先冻结架构基线；应用形态、模块实现方式、身份和租户策略不匹配时禁止进入开发。
- `examples/work-order` 只证明固定领域 CRUD 生成链路，不代表运行时可配置平台的实现方式。
- 未解决的产品决策存在时，生成命令失败。
- 生成文件带有明确标记，手写业务代码不得放进生成目录；被人工修改的生成文件不会被静默覆盖或删除。
- 模拟 API 的浏览器测试不能被声明为真实旅程或阶段验收。
- 工程测试通过、业务旅程完成、用户接受是三个独立状态。

## 目录

```text
contracts/       机器可读合同及 Schema
tools/           无模型依赖的生成、校验和检查工具
agents/          最小角色、纵向切片和事实门禁协议
examples/        Base 自测项目，不作为业务需求来源
tests/           Base 本身的确定性回归测试
```
