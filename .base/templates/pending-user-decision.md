# 待用户决策

Leader 每轮启动时检查本文件；用户填写答案后，必须同步到需求/设计/issue/状态并标记已应用。

## DEC-{{ID}}：{{TITLE}}

- status: OPEN | ANSWERED | APPLIED
- raised_from:
- owner: user | business-owner
- decision_deadline_or_trigger:
- blocked_scope:
- affected_slice_ids: []
- nonblocking_scope:
- reopen_trigger:

### 需要决定

用一句话描述用户需要回答的问题。

### 已完成分析

- 项目内事实：
- 参与角色和会议：
- 外部权威资料（如有）：
- 为什么工程角色无法代决：

### 选项

| option | 用户可见影响 | 工程影响 | 风险 | 是否推荐 |
|---|---|---|---|---:|
| A |  |  |  | yes/no |

### 推荐

- recommended_option:
- reason:
- default_behavior_if_deferred: 保持相关 Gate 阻断，不推断用户同意

### 用户答案

- selected_option:
- answer_notes:
- answered_at:

### 应用记录

- applied_to:
- applied_by: leader
- applied_at:
- verification:
