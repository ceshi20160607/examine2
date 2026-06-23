# Agent Harness 模板（hu）

可复制的 **需求 → 全量原型** 方法论。历史业务代码已移除；规约与样例均在 `hu/`。

## 快速开始

```powershell
# 复制到新 Java 项目
.\hu\install.ps1 -Target "D:\your-java-project"
# 在新项目根：复制 requirement.template.md → 需求.md，填写后打开 Cursor
```

## 目录

| 路径 | 说明 |
|------|------|
| [`hu/`](./hu/) | Harness 权威源 |
| [`hu/examples/nocode-platform/`](./hu/examples/nocode-platform/) | 试跑样例（33 P0 页表 + 4 页 HTML） |
| [`AGENTS.md`](./AGENTS.md) | 本仓库说明 |

详见 [`hu/README.md`](./hu/README.md)。

## 提炼验证（2026-06-23）

| 检查项 | 状态 |
|--------|------|
| L0→L4 剧本完整 | `hu/pipeline/runbook.md` + `harness.yaml` |
| 质量基线自包含 | `hu/feedforward/reference-case.md`（不再依赖旧仓库路径） |
| 默认补全 / 外部规约 | `defaults.md` + `external-refs.md` |
| 教训库 | `lessons.md` + `feedback/learning-loop.md` |
| 验收传感器 | `feedback/checklist.md` + `gates.md` |
| 安装脚本 | `install.ps1` → `.cursor/` + `AGENTS.md`（已 smoke test） |
| 试跑样例 | `examples/nocode-platform/` spec 层可用 |
