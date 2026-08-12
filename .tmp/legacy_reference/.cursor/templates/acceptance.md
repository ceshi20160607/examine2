# {{SCOPE_ID}} 验收记录

- level: task | slice | phase | journey | release | final
- scope_id:
- implementer:
- acceptor:
- environment/version:
- checked_at:
- verdict: pass | fail | blocked

## 目标结果

- role:
- entry_point:
- expected_business_outcome:
- linked_requirements:
- linked_journeys:
- linked_slice_ids:
- covered_goal_items:
- remaining_goal_items:

## 证据矩阵

| 证据 | required | 路径/命令 | SHA-256 | 结果 | 证明什么 | 不证明什么 |
|---|---:|---|---|---|---|---|
| Requirement/design contract | yes |  |  |  |  |  |
| Build/unit | depends |  |  |  |  |  |
| API/data readback | depends |  |  |  |  |  |
| Permission positive/negative | depends |  |  |  |  |  |
| Browser journey | depends |  |  |  |  |  |
| States/errors | depends |  |  |  |  |  |
| Reload/relogin/restart | depends |  |  |  |  |  |
| Migration/release/rollback | depends |  |  |  |  |  |
| Visual screenshot | depends |  |  |  |  |  |

## 检查结果

1.

## 问题

| issue | impact | owner | close condition |
|---|---|---|---|
|  |  |  |  |

## 结论边界

- 当前证据允许声明：
- 当前证据不允许声明：
- user_signoff_required: yes/no
- user_signoff_path:

完成层级不可越级：task 通过不自动推进 slice；slice 通过不自动推进 phase/journey；release 工程通过不等于 final 或用户已验收。
