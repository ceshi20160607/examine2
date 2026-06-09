# Development PM Decisions - Orchestrator

| issueId | stage | raisedBy | owner | problem | impact | pmDecision | actionRequired | round | status | verifier | closeCondition |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DEV-GOV-001 | development | user | orchestrator | 开发推进没有稳定执行多角色问题发现、PM 裁决、责任方回复和复核闭环。 | 半成品可能被误判为完整交付。 | 接受。将 `docs/process/development_governance.md` 设为开发批次硬闸门，并写入 `AGENTS.md`、`docs/process/development_mode.md` 和角色 agent 配置。 | Orchestrator 更新治理文档、agent 规则、进度和状态，并提交 Git。 | 1 | resolved | reviewer | 文档和 agent 配置包含强制流程、问题流转、PM/Orchestrator 禁止行为和 agent 不可用降级规则。 |
| DEV-GOV-002 | development | user | pm | PM 未在每期开始前明确验收口径和不可宣称事项。 | 用户无法判断当前是否可上线或只是局部通过。 | 接受。PM 后续每期必须先写验收口径，禁止在 test/validator/reviewer 复验前宣称完成。 | PM 规则写入治理文档和 `pm.toml`；后续 P9 起执行。 | 1 | resolved | reviewer | `docs/process/development_governance.md` 和 `.codex/agents/pm.toml` 已包含 PM 禁止行为。 |
| DEV-GOV-003 | development | orchestrator | orchestrator | 子 agent 启动后不可寻址。 | 无法追踪 agent 状态，不能证明其完成任务。 | 接受。不可寻址 agent 的工作不计完成；必须记录降级并重新调度或本地接管。 | 写入治理文档、进度和状态。 | 1 | resolved | reviewer | 进度和 state 已记录本轮 agent session failure。 |
