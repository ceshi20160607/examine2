---
name: rough-to-prototype
description: Autonomously expands 需求.md into full P0 HTML and coding-ready specs. Reads hu harness feedforward + pipeline + feedback. Never ask user for IA choices.
---

# Pipeline Runbook（L0→L4）

**契约：** [feedforward/contract.md](../feedforward/contract.md) · **配置：** [harness.yaml](../harness.yaml)

## 执行前必读

1. `feedforward/contract.md`
2. `feedforward/reference-case.md`
3. `feedforward/lessons.md`
4. `feedforward/external-refs.md`
5. `feedforward/defaults.md`

## 输入

`./需求.md` → `docs/product/rough-input.md` → L0。**禁止 pending** → 全部 AUTO 写入 `docs/decisions/resolution.md`。

## 输出（done = 全部完成）

| # | 交付物 |
|---|--------|
| 1-8 | product/*, decisions/resolution.md, design/* spec |
| 9 | **100% P0 HTML** = design-package P0 行数 |
| 10 | design-diff.md + feedback/checklist.md 全 pass |
| 11 | state.json → design_delivered, prd_frozen, design_package_complete |
| 12 | feedback/learning-loop.md 自动执行 |

## 五层（单会话，禁止跳层）

**L0** rough-input, glossary, resolution AUTO  
**L1** prd, design-scope, 15步剧本, 关键词→external-refs 扩页  
**L2** design-package（零 TBD）, ui-spec, seed — **禁止 HTML**  
**L3** config-spec, page-inventory, reviews/* — **禁止 OD**  
**L4** prototype-brief → **全部 P0 HTML** + index → design-diff

## 完成汇报

按 [feedback/response-contract.md](../feedback/response-contract.md) 格式回复用户。

## 禁止

问用户选型 · 抽样 HTML · 聊天 patch · design_delivered 前写 backend/frontend/sql
