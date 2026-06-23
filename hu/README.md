# hu — Agent Harness（需求 → 全量原型）

> **Harness**：围绕模型的 feedforward（规约）+ feedback（传感器）+ state（闸门）+ correction（学习循环）。  
> 参考：[Harness Engineering (Martin Fowler)](https://martinfowler.com/articles/harness-engineering.html) · [awesome-harness-engineering](https://github.com/ai-boost/awesome-harness-engineering)

## 你只做什么

在项目根目录维护 **`需求.md`**（原始需求原文）。其余全自动。

## 复制到新项目

```powershell
.\hu\install.ps1 -Target "D:\your-java-project"
```

生成：`AGENTS.md` + `.cursor/`（Cursor 兼容布局）。

## 目录（Harness 四层）

| 目录 | 作用 | Harness 术语 |
|------|------|--------------|
| [feedforward/](./feedforward/) | 做什么、怎么做、质量基线 | Feedforward |
| [pipeline/](./pipeline/) | L0→L4 执行剧本 | Agent loop |
| [feedback/](./feedback/) | checklist、gates、完成契约 | Computational sensors |
| [state/](./state/) | session 闸门模板 | Memory / state |
| [rules/](./rules/) | Cursor 自动触发 | Hooks |
| [templates/](./templates/) | 产出物骨架 | Artifacts |
| [examples/](./examples/) | 试跑样例（无代码平台） | Reference run |

配置清单：[harness.yaml](./harness.yaml)

## 为何不用「直接画原型」

| 方式 | P0 页 | 可 Coding |
|------|-------|-----------|
| 直接画 | ~40% | ❌ |
| 本 Harness | 100% 页表 + 100% HTML | ✅ |

详见 [feedforward/methodology.md](./feedforward/methodology.md) §差距分析。

## Java Coding 阶段

本包只覆盖 **设计/原型**。签字后在新项目追加 `backend/`、`phase-2-contract` 等（与 Harness 解耦）。

## 维护

本仓库 `hu/` 为唯一权威源。安装脚本：`install.ps1` → 目标项目的 `.cursor/` + `AGENTS.md`。

## 自测

```powershell
$t = Join-Path $env:TEMP "hu-smoke-test"
New-Item -ItemType Directory -Force -Path $t | Out-Null
.\install.ps1 -Target $t
Test-Path (Join-Path $t ".cursor\session\state.json")   # 应为 True
Test-Path (Join-Path $t ".cursor\feedforward\contract.md")
```
