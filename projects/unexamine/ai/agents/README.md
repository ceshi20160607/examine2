# 项目角色任务区

总控只从 `ai/requirements`、`ai/architecture`、`ai/planning`、`ai/decisions` 和验证证据计算项目状态。角色任务文件按 `TASK-<role>-<number>.json` 命名，至少绑定原子用例、输入、允许修改目录、依赖、预计分钟、交付物和验证方法。

角色边界继承 `template_base/agents/workflow.json`。数据库角色先改 `db`；后端角色不编辑 `base`；测试角色从每个周期排期开始介入，不等到开发结束才补测试。

本项目已经进入持续执行循环。总控在每个周期结束后写入 `ai/evidence`、重算项目状态并立即开始下一未完成周期；周期边界只做进度通报，不再等待用户重复输入“继续”。只有全部周期及门禁完成、用户明确停止、超出授权/破坏性操作，或 `question.md` 中存在 PM 无法裁决的真实产品问题时才暂停。
