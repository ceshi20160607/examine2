# Agent Harness 模板仓库

本仓库 **只保留** 可复制的原型方法论包 [`hu/`](./hu/)。历史业务代码已删除，知识已沉淀进 `hu/feedforward/`。

## 维护本模板

- 权威源：`hu/`（feedforward / pipeline / feedback / state / rules / templates）
- 试跑样例：`hu/examples/nocode-platform/`
- 改规约 → 改 `hu/feedforward/` 或 `hu/feedback/`，再跑 `install.ps1` 验证

## 用到新项目

```powershell
.\hu\install.ps1 -Target "D:\your-java-project"
```

在新项目根写 `需求.md`，详见 [`hu/AGENTS.md`](./hu/AGENTS.md)。
