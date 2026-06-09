# Development Raw Issues - Orchestrator

| issueId | stage | raisedBy | owner | problem | impact | suggestion | blockerLevel |
| --- | --- | --- | --- | --- | --- | --- | --- |
| DEV-GOV-001 | development | user | orchestrator | 后续开发被动等待用户多次说“继续”，且 Orchestrator 直接发现问题、直接交付，没有稳定执行多角色提问、PM 裁决、责任方回复和复核闭环。 | 项目管理失效，PM、frontend、test、validator、reviewer 职责无法体现，容易把半成品误判为完整系统。 | 将开发治理规则升级为硬闸门，写入 AGENTS、development mode、agent 配置和进度状态。 | blocker |
| DEV-GOV-002 | development | user | pm | PM 没有在每期开始前明确业务目标、验收口径、不可宣称事项和问题裁决机制。 | 用户无法看到总任务、角色责任、完成度和当前缺口，阶段结论可信度不足。 | PM 必须在每批次先输出验收口径，并在 test/validator/reviewer 复验前禁止宣称完成。 | blocker |
| DEV-GOV-003 | development | orchestrator | orchestrator | 上一轮尝试调度 planner/frontend 子 agent 后，后续唤醒返回 `agent not found`。 | 子 agent 工作不可追踪，不能把它们作为真实完成依据。 | 记录 agent session 降级规则：不可寻址时停止依赖该 agent 结论，重新调度或本地接管并记录。 | major |
