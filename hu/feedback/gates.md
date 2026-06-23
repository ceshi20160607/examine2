# 冻结闸门（Gates）

见 [feedforward/contract.md](../feedforward/contract.md)。状态：`session/state.json` → `gates`。

## 闸门定义

| Gate | 谁置 true | 条件 | 阻塞 |
|------|-----------|------|------|
| `prd_frozen` | Agent | PRD + MVP；无 open issue | L2+ |
| `design_package_complete` | Agent | design-package 零 TBD；reviews pass | L4 |
| **`design_delivered`** | **Agent** | **全 P0 HTML + design-diff pass** | 声称设计完成 |
| `design_user_approved` | 用户（可选） | user-approval approved | **Coding** |
| `api_frozen` | Agent/pm | api.md 冻结 | Build |
| `build_batch_accepted` | skill | task-accept | Verify |

## 自治模式（hu 默认）

用户**只给** `需求.md`。Agent 目标：`design_delivered: true`。

- **不要求** 用户填 IA、答 pending、分阶段确认
- `design_user_approved` **可选** — 只拦代码，不拦 Agent 交付原型
- 歧义一律 `resolution.md` AUTO-*，**禁止** pending 阻塞

见 [AUTONOMOUS.md](../AUTONOMOUS.md)。

## 开发硬停

`design_user_approved: false` 时禁止 `backend/**`、`frontend/**`、`sql/**`。

`design_delivered: false` 时禁止声称「原型已完成」。

## Gate 记录

写入 `session/events/*.jsonl`（可选）。
