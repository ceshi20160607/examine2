# P4 全项目交付节奏与 P4-A1 开工会审

- reviewed_at: `2026-07-15T19:04:26+08:00`
- user_decision_applied_at: `2026-07-15T20:01:58+08:00`
- scope: `P0-P10 delivery cadence; P2-P3 combined demo; P4-A1 requirement and task contracts`
- decision_owner: `leader`
- status_ref: `.cursor/session/state.json`

## 结论

Architect、PM、Test 独立复审均为 `PASS`。用户随后明确裁决：`30..240` 分钟是进入执行前用于拆分和排程的目标估算，不是 kill deadline；实际超过估算后原任务必须继续到验收完成，并由 PM 重估和调整串并行。全项目使用 `Phase -> target<=720m Vertical Slice -> 2..3 Tasks`，小期必须最终交付真实可运行入口并明确未交付范围。

## 已关闭阻塞

| issue | reviewer | verdict | closure evidence |
|---|---|---|---|
| `CADENCE-ARCH-REVIEW` | Architect | PASS + 用户裁决修订 | 检查点、overrun 连续重估、结构化证据和完成层级由 validator 阻断；超过估算不终止原任务 |
| `CADENCE-PM-REVIEW` | PM | PASS | P0-P10 阶段边界完整；P2/P3 合并展示；P4-A1/A2 与后续小期均保留 delivers/doesNotDeliver/remaining/deferred/demo |
| `CADENCE-TEST-REVIEW` | Test | PASS | Requirement Gate、2..3 tasks、owner/verifier、五类证据、demo manifest 及负向绕过均可机器验证 |

## 已执行验证

- 正向：base framework、instance framework、VS4 machine contracts、UTF-8、git diff check 全部通过。
- 旧的“到估算即停止/timeboxed_out”负向结论已被用户裁决废止。新回归测试证明：估算 `240m`、实际执行 `250m` 且有原因、剩余估算、修订目标、排期调整、PM 决定和证据的同一任务保持 `in_progress` 并继续完成。
- 节奏回归共 7 例通过：1 个合法 overrun 正例；初始估算 `241m`、缺 overrun、缺排期调整、修订目标再次过期、串行任务伪挂并行组、并行净收益不成立共 6 个反例均被拒绝。
- 依赖环、未过 Gate 开工、Gate hash 漂移、spent 不一致、并行隔离冲突、release 越级、issue 漏登记、假证据和无 demo manifest 的完成层级仍必须拒绝。

## 决定边界

- 接受 P2/P3 组合展示 slice；Playwright 独立 runner 未作为 pass 证据，结论只覆盖真实 in-app Browser、API、DB 和 restart 复现。
- 接受 P4-A1 Requirement Gate，只允许 `P4-A1-01` 进入 ready；任务开始时才写 startedAt/targetAt，超过 target 时追加 overrun history 并继续完成。
- 本会审不证明 P4-A1 已完成，不证明动态记录 CRUD、P5-P10、发布或用户最终验收。
