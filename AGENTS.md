# 协作架构已迁移

## 禁止
- 禁止过度封装
## 必须
- 编码统一UTF-8
- 每一次具体执行新得任务或者ask等开始前先压缩上下文，避免执行过程中进行压缩


**唯一规范来源：[`.cursor/README.md`](./.cursor/README.md)**

本文件（原 AGENTS.md / `.codex` 流水线）已废弃，仅作历史参考。

## 当前规则摘要

1. **Conductor** 管 Session（`.cursor/session/state.json`）
2. **Worker Agents** 每次新会话，只读落盘文件，不传聊天上下文
3. **Skills** 做确定性验收（task-accept、clean-build、e2e）
4. **Open Design** 原型 + 你在 `docs/design/user-approval.md` 签字前，**禁止一切开发**
5. 旧 `docs/*`（除 `user_requirement.md`）已删除，按新目录重建

## 启动

```
读取 .cursor/README.md 与 .cursor/session/state.json，
先读 .cursor/knowledge/agent-operating-rules.md、.cursor/knowledge/project-operating-rules.md 与 .cursor/knowledge/failure-lessons.md 做落盘上下文压缩，
执行当前 phase 对应 .cursor/workflows/ 剧本。
```
