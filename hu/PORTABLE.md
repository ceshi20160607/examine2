# .cursor 可移植包说明

> **本目录即 `hu/` 规约包内容。** 总说明与差距分析见 **[README.md](./README.md)**、**[TEST-GAP-ANALYSIS.md](./TEST-GAP-ANALYSIS.md)**。  
> 安装到新项目：`.\install.ps1 -Target D:\your-project`

---

## 适用性结论

| 你的目标 | 当前是否适用 | 说明 |
|----------|:------------:|------|
| 复制 `.cursor` + `需求.md` → 完善原型 | ✅ | 用 **最小包** + [NEW-PROJECT.md](./NEW-PROJECT.md) |
| 复制整个 examine2 的 `.cursor` 原样 | ⚠️ | 含 unexamine 专用 state/knowledge，需清理 |
| 不复制、只靠聊天临时做原型 | ❌ | 无落盘规约，会重复 unexamine 多轮返工 |

**结论：** 方法论适用；**请按下面「最小包」复制**，不要无脑拷整个 examine2 的 `.cursor`。

---

## 最小包（只做：粗糙需求 → 原型）

复制以下路径到新项目 `.cursor/`：

```text
.cursor/
├── METHODOLOGY.md              ← 总纲：错因/正解/RPFD/学习循环
├── NEW-PROJECT.md              ← 启动入口
├── PORTABLE.md                 ← 本文件
├── rules/
│   └── prototype-from-requirement.mdc
├── knowledge/
│   ├── prototype-pipeline-lessons.md   ← Agent L0 必读（通用教训）
│   ├── learning-loop.md                ← 项目结束后自我学习
│   └── learnings/                      ← 空目录即可
├── skills/
│   ├── rough-to-prototype/
│   └── open-design.md
├── templates/design/
├── workflows/
│   ├── phase-0-requirement-expansion.md
│   ├── phase-1-internal-design-complete.md
│   └── phase-1-design-freeze.md
├── architecture/
│   ├── gates.md
│   └── issue-protocol.md
└── session/
    ├── state.template.json
    └── issues/
```

**新项目额外创建（不在 .cursor 内）：**

```text
需求.md                         ← 你的粗糙需求（唯一必填）
docs/                           ← Agent 运行时自动创建子目录
```

---

## 完整包（原型 + Java Coding 编排）

在最小包基础上追加：

```text
.cursor/agents/
.cursor/workflows/phase-2-contract.md ~ phase-4-verify.md
.cursor/skills/task-accept.md, clean-build.md, contract-sync.md, e2e-user-script.md
.cursor/architecture/backend-structure.md    ← Java/Maven 专用
.cursor/knowledge/failure-lessons.md           ← Coding/验收教训
.cursor/open-design/
.cursor/templates/task.md, issue.md
```

**阶段边界：** 最小包 = **仅设计/原型**（通用）；完整包 = 原型签字后 Java 契约与实现。

**不要复制或必须重置的 examine2 专用项：**

| 路径 | 处理 |
|------|------|
| `session/state.json` | 用 `state.template.json` 覆盖 |
| `session/events/*.jsonl` | 不拷或清空 |
| `knowledge/domain-model.md` | unexamine 专用；新项目由 Agent L0 重写 glossary |
| `knowledge/frozen-rules.md` | 可选；含 unexamine 业务规则 |
| `architecture/backend-structure.md` | 仅 Java/Maven 项目需要 |

---

## 复制后清理（从 examine2 整包拷贝时）

1. `state.json` ← `state.template.json`
2. `registry.jsonl` 清空
3. 删除或覆盖 `knowledge/domain-model.md`（新项目重建）
4. 根目录写 `需求.md`，**不要**依赖 `docs/user_requirement.md`

---

## 产出目录约定（Agent 自动创建）

```text
docs/
├── product/
│   ├── rough-input.md          ← 从 需求.md 归档
│   ├── prd.md
│   ├── design-scope.md
│   └── domain-glossary.md
├── decisions/
│   ├── resolution.md           ← AUTO-* / RES-*
│   └── pending.md              ← 等你决策
└── design/
    ├── design-package.md
    ├── ui-spec.md
    ├── config-spec.md
    ├── page-inventory.md
    ├── *-seed.md
    ├── prototype-brief.md
    ├── design-diff.md
    ├── user-approval.md
    ├── reviews/*.md
    └── prototypes/**/*.html
```

---

## 质量保证（为何能接近 unexamine 最终原型）

1. **先页表后 HTML** — 避免 v1 只出 9/21 页
2. **名词表** — 避免「应用/模块」类返工
3. **config-spec** — 按钮/权限/密度在 OD 前写死
4. **一条演示剧本 + seed** — 原型用真数据不是占位表
5. **checklist 断言** — design-diff 可机械验收
6. **你只做 1 次审美签字** — `user-approval.md`

---

## 一键自检（新项目 Agent 自检）

- [ ] 已读 `NEW-PROJECT.md`
- [ ] 已找到 `需求.md` 并写入 `rough-input.md`
- [ ] L2 前未生成 HTML
- [ ] `design-package` P0 零 TBD
- [ ] `prototype-brief` 与 P0 页 1:1
- [ ] P0 原型可浏览器打开
- [ ] `design-diff` checklist 已打勾
