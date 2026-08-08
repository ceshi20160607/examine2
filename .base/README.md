# 可复用软件工程模板

本目录是一套可独立复制的软件工程模板。它负责把项目提供的粗糙输入逐步转化为可设计、可开发、可测试、可部署并由用户验收的系统。

模板只定义工程方法，不包含项目名称、业务术语、固定目录、技术栈、账号、当前进度或历史任务。实例化完成后，项目工程实例必须自包含运行，不依赖模板源目录。

## 1. 模板目标

- 用 `leader` 统一控制工程状态、节点验收和升级。
- 用 `pm` 管理计划、问题、会议、依赖和风险。
- 用相互独立的专业角色完成需求、设计、实现、测试和交付。
- 用落盘文件隔离上下文，防止长聊天、传话和历史噪声污染结论。
- 用 Gate 约束完成声明，用模块 DAG 让无依赖、写集不重叠的工作默认并行，允许独立后续域提前开发。
- 用受影响层 -> slice -> phase/release 的分层验证避免每个小任务重复全量回归；响应式和视觉矩阵集中到 hardening。
- 用角色旅程和真实读回判断可用性，不用页面、接口、生成代码或构建结果冒充最终完成。

## 2. 目录结构

```text
README.md
LEADER.md
GOVERNANCE.md
INSTANCE.template.md
agents/
  README.md
  _role-template.md
  {role}/role.md
skills/
  README.md
  playbooks.md
workflows/lifecycle.md
templates/
  engineering-node.md
  requirement-package.md
  design-package.md
  final-goal-ledger.md
  journey-gate.md
  task.md
  issue.md
  meeting-decision.md
  acceptance.md
  pending-user-decision.md
session/state.template.json
scripts/validate-framework.ps1
```

## 3. 公共角色

| 角色 | 主责 |
|---|---|
| `leader` | 总体协调、节点验收、工程决策与升级 |
| `pm` | 项目计划、问题分流、会议组织、依赖和风险 |
| `product` | 产品目标、范围、用户价值和产品决策建议 |
| `analyst` | 来源追踪、需求结构化、冲突和遗漏分析 |
| `architect` | 系统边界、跨层契约、非功能与技术决策 |
| `uiux` | 信息架构、交互、状态、原型和可用性 |
| `planner` | 任务 DAG、切片、并行组和路径所有权 |
| `dba` | 数据域、模型、约束、迁移和生成边界 |
| `backend` | 服务端业务行为、权限、事务和集成 |
| `frontend` | 用户界面、真实接口绑定和可见状态 |
| `test` | 独立测试、回归、旅程证据和节点判定 |
| `ops` | 环境、部署、可观测性、发布和回滚 |

角色按工程节点启动，不常驻。一个小项目可以由同一执行主体先后扮演多个角色，但每次必须使用独立上下文，并且实现者不能验收自己的结果。

角色的“必需技能”不是能力标签。`skills/playbooks.md` 为每项技能规定输入、真实检查步骤、通过条件和失败升级；验证器会拒绝引用了不存在 playbook 的角色。

## 4. 实例化方法

1. 将本目录全部内容复制到项目选定的工程实例目录。
2. 将 `INSTANCE.template.md` 复制为 `INSTANCE.md`，填写项目路径、输入来源、技术约束、启用角色和当前节点。
3. 将 `session/state.template.json` 复制为 `session/state.json`。
4. 项目有特殊要求时，在对应角色目录创建 `update.md`；公共 `role.md` 不写项目内容。
5. 运行 `scripts/validate-framework.ps1 -Instance -Mode Structure` 做日常结构验证；功能批次收口用 `-Mode Active`，发布前用 `-Mode Release`。

角色加载顺序固定为：

```text
agents/{role}/role.md
  -> agents/{role}/update.md（可选）
  -> 当前任务上下文包
```

后加载内容可以补充或收紧规则，不能静默改变角色权力、放宽 Gate 或替代用户签字。

## 5. 运行入口

实例每次恢复工作按以下顺序读取：

1. `INSTANCE.md`
2. `session/state.json`
3. `LEADER.md`
4. `GOVERNANCE.md`
5. `workflows/lifecycle.md`
6. 当前角色的 `role.md` 和可选 `update.md`
7. 当前节点声明的输入文件

聊天内容、旧任务目录、历史证据和未声明文件都不是默认输入。

## 6. 文档控制

- 每个工程关注点只有一个当前主文件，其他文件通过引用连接，不复制全文。
- 临时分析在节点关闭前合并、登记或删除，不能变成新的事实来源。
- 删除旧资料前必须先盘点其通用规则、项目规则、历史证据和无用噪声。
- 同一结论不得同时散落在多个状态、会议纪要和任务文件中。
- 模板改进只接收经过项目验证、去除项目特征后仍成立的规则。

## 7. 完成边界

工程证据和用户验收分离。任务通过、测试通过、构建成功、接口成功、页面存在和部署成功都只是不同层级的证据。

最终完成必须证明：目标角色从真实入口完成业务结果，数据真实持久化并可读回，权限正反例成立，关键状态和失败路径可理解，发布包可运行，并由用户或指定验收人完成最终确认。
