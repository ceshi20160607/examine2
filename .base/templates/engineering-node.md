# 工程节点 {{NODE_ID}}：{{NODE_NAME}}

## 节点身份

- 阶段：`{{PHASE}}`
- 状态：`pending | in_progress | review | passed | blocked`
- 主责角色：`{{OWNER_ROLE}}`
- 协作角色：`{{REVIEW_ROLES}}`
- 前置节点：`{{DEPENDENCIES}}`
- 开始时间：`{{STARTED_AT}}`
- 更新时间：`{{UPDATED_AT}}`

## 为什么做

- 用户或工程目标：`{{OBJECTIVE}}`
- 本节点解决的问题：`{{PROBLEM}}`
- 不在本节点解决：`{{OUT_OF_SCOPE}}`

## 输入

| 输入路径 | 来源角色 | 使用目的 | 是否已确认 |
|---|---|---|---|
| `{{INPUT_PATH}}` | `{{SOURCE_ROLE}}` | `{{PURPOSE}}` | `yes/no` |

## 产出

| 产出路径 | 内容 | 负责人 | 验证方式 |
|---|---|---|---|
| `{{OUTPUT_PATH}}` | `{{CONTENT}}` | `{{OWNER}}` | `{{CHECK}}` |

## 当前理解或设计结论

用可执行、可验收的语言写结论，不写“完善、支持、优化”等无法判定的表述。

## 决策与问题

| 编号 | 类型 | 内容 | 决策人/负责人 | 状态 | 截止或升级条件 |
|---|---|---|---|---|---|
| `{{ID}}` | `decision/issue/risk` | `{{DETAIL}}` | `{{OWNER}}` | `{{STATUS}}` | `{{ESCALATION}}` |

## Gate 与证据

| 检查项 | 结果 | 证据路径 | 检查人 |
|---|---|---|---|
| `{{CHECK}}` | `pass/fail/blocked` | `{{EVIDENCE}}` | `{{VERIFIER}}` |

## 下一节点

- 下一节点：`{{NEXT_NODE}}`
- 进入条件：`{{ENTRY_CONDITION}}`
- 选择理由：`{{WHY_NEXT}}`
- 当前仍阻断的范围：`{{BLOCKED_SCOPE}}`

