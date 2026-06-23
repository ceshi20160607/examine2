---
name: rough-to-prototype
description: Autonomously expands raw 需求.md into full P0 HTML prototypes and coding-ready specs in one run. Use when user provides requirements, 需求.md exists, or design is not complete. Never ask user to choose IA; use defaults, reference-case, and self-learning.
---

# Rough → Codable Prototype（自治模式）

**契约：** [AUTONOMOUS.md](../../AUTONOMOUS.md) — 用户只给 `需求.md`，Agent 一次交付全 spec + **全 P0 HTML**。

## 自动触发

- 存在 `./需求.md` 或用户消息含产品需求描述
- 或 `gates.design_delivered != true`
- **跳过：** 仅当用户明确「只改代码/不要动设计」且 `design_delivered=true`

## 执行前必读（顺序）

1. [AUTONOMOUS.md](../../AUTONOMOUS.md)
2. [reference-case.md](../../knowledge/reference-case.md) — examine2 质量基线
3. [prototype-pipeline-lessons.md](../../knowledge/prototype-pipeline-lessons.md)
4. [external-references.md](../../knowledge/external-references.md)
5. [defaults.md](./defaults.md)

## 输入

`./需求.md` → 归档 `docs/product/rough-input.md` → 开始 L0。  
**缺信息不提问** → `defaults.md` + `resolution.md` AUTO-*。

## 输出（全部完成才叫 done）

| # | 交付物 |
|---|--------|
| 1-8 | product/*, decisions/resolution.md, design/* spec |
| 9 | **全部 P0 HTML**（页数 = design-package P0，**禁止抽样**） |
| 10 | `design-diff.md` checklist 全 pass |
| 11 | `session/state.json` → `design_delivered: true`, `prd_frozen`, `design_package_complete` |
| 12 | learning-loop 归档（自动） |

`user-approval.md` 可生成 `approved: false` 供用户**可选**浏览，**不得**等待用户签字才继续 HTML。

## 五层（单会话连续执行，禁止跳层）

### L0
rough-input, domain-glossary, resolution AUTO（**禁止 pending.md**）

### L1
prd, design-scope, 15步剧本, 关键词→external-references 扩页

### L2
design-package（零 TBD）, ui-spec, *-seed.md

### L3
config-spec（全 P0 控件）, page-inventory, reviews/* 六份 Agent 自写

### L4
prototype-brief → **批量生成全部 P0 HTML** + index → design-diff

**L4 硬指标：** `count(P0 html) / count(P0 in design-package) = 100%`

无 Open Design 时 Agent **自己写**静态 HTML，复用企业后台组件模式（见 reference-case）。

## 禁止

- 问用户选型（见 AUTONOMOUS §2）
- L2 前 HTML；L3 前停步
- 抽样 HTML 后声称完成
- 聊天 patch 代替 brief
- `design_delivered` 前写 backend/frontend/sql

## 完成后

告知用户打开 `docs/design/prototypes/index.html`。  
可选：用户不满意 → 同一自治流程重跑，learning-loop 先更新 lessons。

## 关联

- [checklist.md](./checklist.md) — verification-before-completion
- [learning-loop.md](../../knowledge/learning-loop.md) — 自动执行
