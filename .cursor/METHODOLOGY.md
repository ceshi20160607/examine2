# 需求 → 可 Coding 原型：方法论总纲（RPFD）

> **适用范围：** 任意 Java（或其它栈）企业后台项目的设计阶段。  
> **与 Coding 分离：** 本文只覆盖「粗糙 `需求.md` → 完整原型」；Java 实现编排见 `workflows/phase-2-contract.md` 起（复制完整包时）。  
> **来源：** unexamine 项目 v1→redesign 多轮返工 + RES-001～007 + 跨模型改稿经验提炼。

---

## 1. 一句话

**先冻结「页表 + 名词 + 按钮」，再一次出原型；用户只签审美，Agent 负责补全。**

---

## 2. 为什么早期是错的、最终是对的

### 2.1 错误模式（Agent / 其它模型常犯）

| # | 错的做法 | 后果 | 本项目实例 |
|---|----------|------|------------|
| E1 | 需求很长 **直接 Open Design** | 页数缺 60%、IA 全错 | v1 仅 9/21 P0 页 |
| E2 | **聊天 patch** 改原型要求 | brief 分裂、无法复现 | RES-005～007 三轮补洞 |
| E3 | **名词不冻结** 就画页面 | 整页概念错误 | 「应用与模块」建模页 |
| E4 | 只画 **骨架** 不写 **按钮/密度** | 像调试台、不像产品 | 简易 runtime-list |
| E5 | **配置态与使用态** 同一导航 | 普通用户迷路 | che 见后台入口 |
| E6 | 把 IA 核对 **推给用户填表** | 用户负担大、仍漏项 | ia-confirmed 已废止 |
| E7 | **占位数据** generic-table | 无法判断业务是否合理 | Lorem 列表 |
| E8 | 未冻结就 **写代码** | 返工成本 ×10 | gates 硬停 |

### 2.2 正确模式（最终 redesign 为何好）

| # | 对的做法 | 产出 |
|---|----------|------|
| G1 | **L0 名词表**：每个核心词「是什么 / 不是什么」 | `domain-glossary.md` |
| G2 | **L1 MVP 切刀** + 15 步配置→使用剧本 | `prd.md` + seed 剧本 |
| G3 | **L2 先页表**（ID、路径、P0/P1），零 TBD | `design-package.md` |
| G4 | **L3 标准页 deep spec**（列表 + 核心配置页到按钮级） | `config-spec.md` |
| G5 | **一份** `prototype-brief.md` → **一次** OD/HTML | 可复现 |
| G6 | **真实 seed** 驱动原型 | 车系统 8 列、5 场景 |
| G7 | **design-diff 机械断言** | 21/21 页、12 条 IA |
| G8 | 用户 **只签** `user-approval.md` | 审美与顺手 |

### 2.3 跨模型改稿教训

其它模型容易：**视觉好看但 IA 错、页数少、缺配置链、列表像 demo**。

接稿时必须：

1. **不继承聊天**，只读落盘 spec（见 `architecture/context-policy.md`）
2. 用 **design-package 页数** 对照，不是「看起来差不多」
3. 用 **checklist 断言**，不是主观「还行」
4. 偏差写入 `design-diff.md`，抽象进 `knowledge/prototype-pipeline-lessons.md`

---

## 3. RPFD 五层（Agent 唯一执行顺序）

```
L0 捕获 Capture    → 需求.md → rough-input + glossary + 歧义裁决
L1 切片 Slice      → prd + design-scope + 演示剧本
L2 IA 冻结 Freeze  → design-package + ui-spec 壳 + seed
L3 细粒度 Detail   → config-spec + page-inventory + 标准页 deep
L4 原型 Prototype  → brief → HTML → design-diff → user-approval
```

**硬禁令：**

- L2 前：禁止 HTML
- L3 前：禁止 Open Design
- L4 前：禁止 backend / frontend / sql
- L4 后用户未签字：禁止 Coding

执行细节：[`skills/rough-to-prototype/SKILL.md`](./skills/rough-to-prototype/SKILL.md)

---

## 4. 文档栈（Coding Ready = 11 件）

| 层 | 文件 | 谁写 |
|----|------|------|
| 输入 | `需求.md` | **你** |
| L0 | `docs/product/rough-input.md`, `domain-glossary.md` | Agent |
| L0 | `docs/decisions/resolution.md`, `pending.md` | Agent / 你 |
| L1 | `docs/product/prd.md`, `design-scope.md` | Agent |
| L2 | `docs/design/design-package.md`, `ui-spec.md`, `*-seed.md` | Agent |
| L3 | `docs/design/config-spec.md`, `page-inventory.md` | Agent |
| L4 | `docs/design/prototype-brief.md`, `prototypes/**`, `design-diff.md` | Agent |
| 门 | `docs/design/user-approval.md` | **你签字** |

模板：[`templates/design/`](./templates/design/)

---

## 5. 质量门（Gate）

| Gate | 条件 | 字段 |
|------|------|------|
| PRD 冻结 | MVP + 无 open P0 discovery | `prd_frozen` |
| 设计包完工 | checklist A～E 全过 | `design_package_complete` |
| 用户认可原型 | `approved: true` | `design_user_approved` |

定义：[`architecture/gates.md`](./architecture/gates.md)  
断言：[`skills/rough-to-prototype/checklist.md`](./skills/rough-to-prototype/checklist.md)

---

## 6. 用户 vs Agent 分工

| 你 | Agent |
|----|-------|
| 写 `需求.md`（可很短） | L0～L4 全部文档 + 原型 |
| 答 `pending.md`（P0 歧义，≤3 问） | 其余按 `defaults.md` AUTO |
| 签 `user-approval.md` | 内部 reviews、design-diff |
| 项目结束后指「哪里不对」 | 写入 learning，更新 lessons |

---

## 7. 可复用：复制到新项目

1. 复制 [PORTABLE.md](./PORTABLE.md) **最小包**
2. [NEW-PROJECT.md](./NEW-PROJECT.md) 三步启动
3. 根目录 `需求.md` + 启动口令

**Java Coding 阶段：** 复制完整包 + `architecture/backend-structure.md` + phase-2～4 workflows（与原型阶段解耦）。

---

## 8. 自我学习（每个项目结束后）

见 [`knowledge/learning-loop.md`](./knowledge/learning-loop.md)。

要点：

- 每次返工 / 用户否定 → **一条通用教训**（去项目名）→ `prototype-pipeline-lessons.md`
- 可固化为 `defaults.md` 或 `checklist.md` 新断言
- **不**把项目专用规则写进 portable 包

---

## 9. 关联 Skill（推荐组合）

| 阶段 | Skill | 用途 |
|------|-------|------|
| L0～L1 澄清 | superpowers **brainstorming** | 意图与边界，再展开 |
| L0～L4 主流程 | **rough-to-prototype**（本仓库） | 五层展开 |
| L4 出 HTML | **open-design** 或 Agent 静态页 | 一次 brief |
| L4 声称完成前 | superpowers **verification-before-completion** | 跑 checklist 再给结论 |
| 多租户/SSO/API | [external-references.md](./knowledge/external-references.md) | 网络规约扩页 |
| 项目结束 | **learning-loop**（本仓库 knowledge） | 沉淀教训 |

---

## 10. 启动口令（复制到新项目）

```text
读 .cursor/METHODOLOGY.md、.cursor/NEW-PROJECT.md、
.cursor/skills/rough-to-prototype/SKILL.md、
.cursor/knowledge/prototype-pipeline-lessons.md。
输入：./需求.md。从 L0 执行到 L4。
完成前跑 checklist；禁止跳层写代码。
```

---

## 附录：与本项目 examine2 的对照

| examine2 阶段 | 本方法论对应 |
|---------------|--------------|
| user_requirement 2700 行 | 未来仅 `需求.md`；Agent 展开成等价文档栈 |
| v1 原型作废 | 违反 G3/G4，触发 E1 |
| RES-001～004 | L0 glossary + L2 IA |
| RES-005～007 | L3 config-spec + 密度规约 |
| redesign 签字 | L4 + user-approval |
| P1 起 Java 开发 | 完整包 phase-2+，需 `design_user_approved` |
