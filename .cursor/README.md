# unexamine 协作架构 v2

> 本目录是项目唯一协作规范来源。旧工程在 **`.oldbk/`**（只读参考），**不得**再使用 `.codex/` 流水线。
> 原始需求：`docs/user_requirement.md`

## 一句话

**Conductor 管 Session，PM 管问题，Worker 读文档干活，Skill 做确定性检查；UI 未获你签字前，禁止一切开发。**

## 目录

| 路径 | 用途 |
|------|------|
| [CONDUCTOR.md](./CONDUCTOR.md) | Session 管理者：调度、闸门、事件，不写代码不做产品决策 |
| [architecture/overview.md](./architecture/overview.md) | 总览：适合本项目的用法 |
| [architecture/agents-vs-skills.md](./architecture/agents-vs-skills.md) | Agent 与 Skill 划分、并行规则 |
| [architecture/context-policy.md](./architecture/context-policy.md) | 无上下文传递，只读落盘产物 |
| [architecture/work-graph.md](./architecture/work-graph.md) | 阶段 DAG 与用户流程 |
| [architecture/issue-protocol.md](./architecture/issue-protocol.md) | 问题提出→PM 裁决→复核→升级用户 |
| [architecture/gates.md](./architecture/gates.md) | 冻结闸门（含 Open Design 用户签字） |
| [architecture/backend-structure.md](./architecture/backend-structure.md) | Maven 模块、base/manage 分层、MyBatis-Plus 生成器 |
| [knowledge/](./knowledge/) | **沉淀：冻结规则、领域模型、失败教训、决策权限**（Worker 先读） |
| [agents/README.md](./agents/README.md) | **Agent 角色总册**（身份、边界、启动协议） |
| [agents/](./agents/) | 各角色完整规范：pm / analyst / uiux / planner / dba / backend / frontend / test / conductor |
| [skills/](./skills/) | 无状态可复用能力 |
| [workflows/](./workflows/) | 各阶段执行剧本 |
| [open-design/](./open-design/) | 安装、接入、用户确认闸门 |
| [session/state.json](./session/state.json) | 当前 Session 唯一状态源 |
| [session/issues/registry.jsonl](./session/issues/registry.jsonl) | 问题注册表 |
| [architecture/acceptance.md](./architecture/acceptance.md) | 任务级验收，裁判与选手分离 |

| [templates/](./templates/) | 任务、issue、验收、接口模板 |

## 旧工程备份

| 路径 | 说明 |
|------|------|
| `.oldbk/.codex/` | 旧 agent 与 oldexamine 参考 |
| `.oldbk/backend/` | 旧后端（含 generator、base、manage 样例） |
| `.oldbk/frontend/` | 旧前端 |
| `.oldbk/sql/` | 旧 init.sql |

新 `backend/`、`frontend/`、`sql/` 待 Design Gate 与 API 冻结后重建；**`design_user_approved = false` 时不得创建**。

## 当前阶段

见 [session/state.json](./session/state.json)。

**硬闸门：** `gates.design_user_approved = false` 时，禁止进入契约拆分与一切开发。

## 与你确认的流程（第 6 点）

```
需求理解 → ui-spec
    → 【团队内部】design-package + 多角色 reviews → design_package_complete
    → 你调用 Open Design（一次，brief 已齐）
    → user-approval（你签字）
    → API 契约 → 实现 → 验收
```

## 给 Cursor / 任何 Agent 的启动口令

```
1. 读 .cursor/agents/README.md 确认角色总册
2. 读 .cursor/agents/{agentId}.md 全文 —— 你必须知道「我是谁」
3. 读 .cursor/session/state.json 与本轮 inputs
4. 用一句话开场：我是 {agentId}，本次 taskId=…，只读 inputs，只写 outputs
5. 按 .cursor/workflows/ 当前 phase 执行；禁止继承对话上下文
```
