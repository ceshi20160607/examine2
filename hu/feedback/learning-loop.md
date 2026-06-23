# 原型流水线：自我学习循环

> **目标：** 每个项目结束后，把「这次为何返工」变成 **下个项目 Agent 的默认知识**，而不是重复踩坑。  
> **适用范围：** 设计阶段 portable 包；Java coding 阶段另有 `failure-lessons.md`。

---

## 1. 何时触发

| 事件 | 动作 |
|------|------|
| `user-approval.md` 为 false + notes | **必须** 走学习循环 |
| `design-diff` 有 fail 项 | **必须** |
| 用户口头「这里不对」 | Agent 写 learning 草稿 |
| 项目原型签字通过 | **建议** 回顾 1 条可改进点 |

---

## 2. 五步循环

```
① 捕获 Capture   → 具体现象（可含项目名）
② 抽象 Abstract  → 去掉项目名，写成通用「错/对/断言」
③ 归类 Classify  → 写入 prototype-pipeline-lessons.md 的 A～F 区
④ 固化 Embed     → 必要时改 defaults.md / checklist.md / SKILL.md
⑤ 验证 Verify    → 下个项目 L4 不断言复发
```

---

## 3. 文件职责

| 文件 | 内容 | 生命周期 |
|------|------|----------|
| `knowledge/learnings/YYYY-MM-DD-{slug}.md` | 单次事件 **原始记录**（可含项目名） | 归档 |
| `knowledge/prototype-pipeline-lessons.md` | **通用**教训库 | 持续追加 |
| `skills/rough-to-prototype/defaults.md` | Agent 沉默时的默认 | 少改、慎改 |
| `skills/rough-to-prototype/checklist.md` | 机械断言 | 可增项 |
| `session/issues/registry.jsonl` | 结构化 issue | 可选 |

---

## 4. Learning 条目模板

创建：`knowledge/learnings/YYYY-MM-DD-{slug}.md`

```markdown
# Learning: {简短标题}

- **date:**
- **project:** （内部代号，可写）
- **trigger:** user-approval false / design-diff fail / …
- **symptom:** 用户看到什么不对
- **root_cause:** 违反哪条 RPFD 层 / 哪条 anti-pattern
- **fix_applied:** 改了哪些 spec/原型文件
- **generalized_rule:** （去项目名，准备写入 lessons）
- **embedded_to:** lessons §Xn / defaults / checklist
- **status:** captured | abstracted | embedded
```

---

## 5. 抽象规则（写入 lessons 前）

Agent 必须：

1. 去掉业务专名（车系统 → 「示例业务域」）
2. 一条 learning 只沉淀 **一条** 可断言规则
3. 若与现有 lessons 重复 → **合并**，不堆 duplicate
4. 若仅适用于 Java coding → 写 `failure-lessons.md`，**不**写进 prototype lessons

---

## 6. 固化决策树

```
新教训
  ├─ 是「默认补全」类？ → 更新 defaults.md + resolution AUTO-*
  ├─ 是可机械检查？     → 更新 checklist.md 新 checkbox
  ├─ 是流程顺序？       → 更新 SKILL.md 或 METHODOLOGY.md
  └─ 仅叙事参考？       → 只留 lessons + learnings 归档
```

**禁止：** 未经抽象直接把项目 RES 全文拷进 portable 包。

---

## 7. Agent 在项目结束时的口令

```text
若 user-approval 曾 false 或 design-diff 有 fail：
1. 写 knowledge/learnings/YYYY-MM-DD-*.md
2. 更新 prototype-pipeline-lessons.md（通用一条）
3. 判断是否更新 defaults/checklist
4. 在 design-diff 或 notes 引用新 lesson ID
```

---

## 8. 与 superpowers 的关系

| 场景 | 用 |
|------|-----|
| 开始展开需求 | **brainstorming** — 避免 L1 切错 |
| 声称「原型完成」 | **verification-before-completion** — 先跑 checklist |
| 收到 review 反馈 | **receiving-code-review** — 先验证再改 spec |
| 大改后 | 再走 learning-loop ⑤ |

---

## 9. portable 包复制时注意

- 复制时 **带上** `knowledge/prototype-pipeline-lessons.md` + `learning-loop.md`
- **不带** `knowledge/learnings/*`（项目私有归档）
- 新项目产生的新 learnings 留在该项目仓库
