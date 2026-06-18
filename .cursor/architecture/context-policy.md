# 上下文策略：只读落盘，禁止传话

## 原则

LLM 上下文有限，且长对话易产生 **幻读**（把讨论当事实）和 **假完成**（感觉做过但未落盘）。

### 开工前落盘压缩

平台自动上下文压缩不可手动强制，所以本项目把“压缩上下文”定义为落盘流程：

1. 每次开始实质工作前，先读 `.cursor/session/state.json`、`.cursor/knowledge/agent-operating-rules.md`、`.cursor/knowledge/project-operating-rules.md`、`.cursor/knowledge/failure-lessons.md`、当前阶段 brief/review 和 `docs/user_requirement.md`。
2. 如果用户在本轮提出新的通用纠偏，先写入 knowledge/review/state，再继续做后续修改。
3. Worker 不能依赖聊天里的“我记得”，必须以落盘文件恢复上下文。
4. 中途上下文被系统自动压缩后，继续工作时重新读取上述文件，按文件事实续跑。

**强制：**

1. Worker 之间 **不传递** 对话摘要、聊天记录、口头结论
2. 每次 spawn Worker = **新会话**
3. 结论 **必须** 写入声明输出路径，否则视为未完成
4. Conductor 只向 Worker 提供 **文件路径列表**，不提供「上轮说了什么」

## Worker 启动包（唯一允许传入的信息）

```yaml
task_id: TASK-xxx
role: backend
agent_spec: .cursor/agents/backend.md
inputs:
  - docs/user_requirement.md
  - docs/api/auth.md
outputs:
  - backend/examine-plat/...
forbidden_reads:
  - .codex/**
  - 对话历史
done_when:
  - 所有 outputs 存在且非空
  - 已运行声明的自检命令并记录到 docs/evidence/
```

## 状态真相源（优先级）

1. `.cursor/session/state.json`
2. `.cursor/session/issues/registry.jsonl`
3. 当前阶段声明输出目录下的文件
4. `docs/evidence/` 验收证据

**不算真相：**

- 任何 Agent 在对话里说「已完成」
- 旧 `.codex/state.json`
- 已删除的旧 `docs/progress.md`

## 防幻觉检查（Conductor 每批次执行）

- [ ] 输出文件是否存在且非空？
- [ ] `task-accept` 报告是否 pass？
- [ ] 实现者 agentId 是否 ≠ 验收 skill 触发者？
- [ ] registry 中该任务相关 issue 是否 closed？

任一项否 → 不得更新 `state.json` 为 done。

## PM 的特殊性

PM 也不读聊天历史，只读：

- 各角色 **本轮输出文件**
- `registry.jsonl` 中 open issues

PM 裁决写入 `registry.jsonl` 的 `pmDecision` 字段，不靠口头。
