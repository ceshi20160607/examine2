# Agent 入口

你只维护 [`需求.md`](./需求.md)。

本项目的 Agent Harness 安装在 `.cursor/`（由 `hu/install.ps1` 生成）。

## 自动行为

1. 读 `.cursor/harness.yaml` 与 `.cursor/feedforward/contract.md`
2. 执行 `.cursor/pipeline/runbook.md`（L0→L4，单会话，全 P0 HTML）
3. 跑 `.cursor/feedback/checklist.md` 传感器
4. 按 `.cursor/feedback/response-contract.md` 汇报

**禁止**向用户抛 IA/租户/SSO 选型题；歧义写入 `docs/decisions/resolution.md`（AUTO-*）。

## 交付物

`docs/design/prototypes/index.html` + 全套 spec（见 runbook）。

## 完成后

按 `response-contract.md` 格式回复；可选触发 learning-loop。
