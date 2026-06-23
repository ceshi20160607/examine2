# 完成汇报契约（Final Response Contract）

Agent 在 `design_delivered=true` 前 **必须** 按本格式回复用户。

---

## 1. 状态摘要

```yaml
harness: hu-prototype-harness
design_delivered: true | false
p0_planned: <N>
p0_html: <N>
html_ratio: <N/N = 1.0>
checklist: pass | fail
learning_written: true | false
```

## 2. 用户可见交付

| 项 | 路径 |
|----|------|
| 原型入口 | `docs/design/prototypes/index.html` |
| 页表 | `docs/design/design-package.md` |
| 验收 | `docs/design/design-diff.md` |
| 演示剧本 | `docs/product/prd.md` §演示剧本 |

## 3. 自动裁决（无需用户确认）

列出 `docs/decisions/resolution.md` 中 AUTO-* 标题（≤5 条）。

## 4. 未完成时

`design_delivered: false` 时必须说明：

- 缺哪一层 / 哪项 checklist fail
- 下一步 Agent 将做什么（**不得**要求用户填表或选型）

## 5. 禁止的结束语

- 「请确认 SSO 方案」
- 「抽样 N 页代表完成」
- 「需要你指定 IA」

## 6. 可选

用户口头不满意 → 自治重跑 runbook + learning-loop，仍用本契约汇报。
