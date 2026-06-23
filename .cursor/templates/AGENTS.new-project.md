# 协作入口

本项目 Agent 规约来自 `.cursor/`。

## 新项目（只要：需求 → 可 Coding 原型）

```powershell
# 从 examine2 抽 portable 包（可选）
.\scripts\pack-portable.ps1 -OutDir D:\path\to\new-project\.cursor
```

1. 新项目根目录写 **`需求.md`**
2. Agent 读 **`.cursor/METHODOLOGY.md`**，执行 **rough-to-prototype** Skill（L0～L4）
3. 你签 **`docs/design/user-approval.md`**

## Java Coding（原型签字之后）

复制完整包 + `phase-2-contract.md` 起；见 `architecture/backend-structure.md`。

## 自我学习

项目返工后见 `.cursor/knowledge/learning-loop.md` — 教训写入 `prototype-pipeline-lessons.md` 供下个项目 Agent 读取。
