# 自治模式（Autonomous）— hu 包核心契约

> **用户只做一件事：** 写或更新 `需求.md`（原始需求，长短不限）。  
> **其余全部 Agent 自治：** 规约、展开、全量原型、自检、学习沉淀。  
> **用户不需要：** 指定 Skill、分阶段口令、填 IA 表、回答 pending、限制怎么做。

---

## 1. 输入 / 输出

| 用户 | Agent |
|------|-------|
| `需求.md` 原文 | L0→L4 **一次跑完** |
| （可选）看一眼原型不满意再说 | 10+ spec + **全部 P0 HTML** + design-diff |
| （可选）满意后允许 Coding | 自动 learning-loop 沉淀 |

**完成定义（Agent 自称 done 的条件）：**

- [ ] `design-package` P0 页数 = `prototypes/` 下 P0 HTML 数 = **100%**
- [ ] checklist A～F 无 fail
- [ ] `design-diff.md` 已写
- [ ] **无** `pending.md` 阻塞（歧义一律 `resolution.md` AUTO-*）
- [ ] 已读 [reference-case.md](../feedforward/reference-case.md)（质量基线）

---

## 2. 禁止问用户（一律 AUTO）

以下 **不得** 因「不确定」停下来问用户；按 [defaults.md](../feedforward/defaults.md) + [external-refs.md](../feedforward/external-refs.md) + [reference-case.md](../feedforward/reference-case.md) 自行裁决：

- 产品模型（平台/系统/运行态、模块组、对外应用）
- MVP 切刀（什么 P1 占位）
- 演示业务域与 seed 数据命名
- SSO OIDC vs SAML（MVP OIDC，SAML 占位）
- Console vs Portal（Console P0，Portal P1）
- 平台租户 vs 系统租户（两层都要时两套页都出）
- 布局密度、中文文案、色 token

**唯一可问用户：** 需求原文互相矛盾且无法合理解释（极罕见）。问法：一条消息列清矛盾，仍给出 Agent 推荐方案。

---

## 3. 执行顺序（单会话、不停顿）

```
检测 需求.md
  → 读 lessons + reference-case + external-references
  → L0 glossary + AUTO resolution
  → L1 prd + design-scope + 15步剧本
  → L2 design-package + ui-spec + seed
  → L3 config-spec + page-inventory + reviews（Agent 自写六角色）
  → L4 prototype-brief + 生成全部 P0 HTML + index
  → design-diff + 更新 state.json gates
  → learning-loop（若有 fail 或对照 reference-case 有 gap）
  → 告知用户：打开 docs/design/prototypes/index.html
```

**禁止：** 只出「抽样 HTML」就停；禁止把 pending 甩给用户。

---

## 4. 参照标准（已提炼基线）

质量基线见 [reference-case.md](../feedforward/reference-case.md)：

- 三层 IA、模块组导航、标准 biz-list、config-spec 深度
- 平台 Flow/API 与系统 Flow/对外应用分离
- 待办消息仅顶栏

新需求在同样结构上 **增页**（如 SSO/租户），不 **降标**（少页、简易表）。

---

## 5. 自我学习（自动、无需用户触发）

每轮完成后 Agent **必须**：

1. 对照 reference-case + checklist，差项写入 `.cursor/knowledge/learnings/YYYY-MM-DD-*.md`
2. 可泛化条目追加 [lessons.md](../feedforward/lessons.md)
3. 必要时改 defaults / checklist

用户不参与 learning 流程。

---

## 6. 与 Coding 的边界

- 自治模式交付：**设计包 + 全 P0 原型**（可 Coding 的 spec）
- `design_user_approved`：可选；用户不看也能继续 spec 驱动契约（新项目默认 false 只拦 **代码**）
- Java 实现：另阶段，不在本契约内

---

## 7. Agent 入口（用户无需复制）

用户只发需求内容或说「按需求做原型」。Agent 见 `需求.md` 即 **自动** 读 [contract.md](../feedforward/contract.md) 并执行 [runbook.md](../pipeline/runbook.md)。
