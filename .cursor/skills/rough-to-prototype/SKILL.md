---
name: rough-to-prototype
description: Expands rough or minimal product requirements into a coding-ready design package (PRD, IA, ui-spec, config-spec, seed data, prototype-brief, HTML prototypes). Use when starting a new project, when requirements are vague, when the user asks to flesh out specs before Open Design or coding, or when design_package_complete is not yet true.
---

# Rough → Codable Prototype

Agent-driven pipeline: **粗糙需求 → 可 Coding 的设计包 + 原型**。

**可移植用法：** 复制 `.cursor` 到新项目 → 根目录写 `需求.md` → 读 [METHODOLOGY.md](../../METHODOLOGY.md) + [NEW-PROJECT.md](../../NEW-PROJECT.md)。  
本 Skill 假设输入只有 1～3 页口语/要点；**完善工作由 Agent 完成**。用户只在 P0 产品模型歧义和产品边界切刀上签字。

## 执行前必读（禁止跳过）

1. [METHODOLOGY.md](../../METHODOLOGY.md) §2 — 错因与正解
2. [prototype-pipeline-lessons.md](../../knowledge/prototype-pipeline-lessons.md) — 通用教训
3. [external-references.md](../../knowledge/external-references.md) — 网络规约（多租户/Console/SSO/API）
4. [defaults.md](./defaults.md) — 沉默时默认

## 执行后必做（曾返工或 user-approval 为 false）

按 [learning-loop.md](../../knowledge/learning-loop.md) 写 `knowledge/learnings/` 并更新 lessons。

## 输入文件（按优先级）

| 路径 | 说明 |
|------|------|
| `./需求.md` | **推荐** — 新项目唯一必填 |
| `./docs/需求.md` | 备选 |
| `./docs/user_requirement.md` | 长需求亦可 |

找到后：**原文归档**到 `docs/product/rough-input.md`，再开始 L0。

## 输入门槛

接受任一形式：

- 几句话 + 目标用户
- 竞品参照（「像 XX 但要做 YY」）
- 旧项目提炼（长短均可）

**最低必须提取到：**

| 项 | 缺则先问用户（最多 3 问） |
|----|---------------------------|
| 产品一句话 | 做什么系统 |
| 2～3 类用户 | 谁用、谁配 |
| 1 条端到端故事 | 从登录到完成核心业务 |
| MVP 边界暗示 | 首版必须有什么 / 明确不要什么 |

若用户说「你定」，按 [defaults.md](./defaults.md) 补全并写入 `docs/decisions/resolution.md` 标注 `AUTO-*`。

## 输出清单（Coding Ready 定义）

全部完成且 Gate 通过，才称 **Coding Ready**：

| # | 文件 | 完成标准 |
|---|------|----------|
| 1 | `docs/product/prd.md` | MVP 范围 + 不做事项 + 用户故事 |
| 2 | `docs/product/design-scope.md` | Design 有 / Build 后做 |
| 3 | `docs/design/design-package.md` | 全页 ID + 原型路径 + P0/P1，**零 TBD** |
| 4 | `docs/design/ui-spec.md` | 三套壳 + 导航规则 + 标准页 wire |
| 5 | `docs/design/config-spec.md` | P0 每页：控件 × 权限点 × 显隐 |
| 6 | `docs/design/page-inventory.md` | 全页按钮字段索引 |
| 7 | `docs/design/{demo}-seed.md` | 一条垂直业务真实样例数据 |
| 8 | `docs/design/prototype-brief.md` | Open Design **唯一**输入，与 design-package P0 1:1 |
| 9 | `docs/design/prototypes/**` | P0 HTML 可浏览器打开 |
| 10 | `docs/design/design-diff.md` | 断言 checklist 全 pass |
| 11 | `docs/design/user-approval.md` | 用户 `approved: true` |

模板见 [`.cursor/templates/design/`](../templates/design/)。

## 五层展开（Agent 顺序执行）

```
L0 捕获 → L1 切片 → L2 IA冻结 → L3 细粒度 → L4 原型
```

**禁止跳层。** 禁止在 L2 完成前生成 HTML。禁止在 L3 完成前调用 Open Design。

### L0 — 捕获（analyst）

1. 原文落盘 `docs/product/rough-input.md`
2. 提取名词表 → `docs/product/domain-glossary.md`（每个词：**是什么 / 不是什么**）
3. 识别歧义 → `registry.jsonl`；P0 歧义无默认则 `docs/decisions/pending.md` 等用户
4. 有默认则写 `docs/decisions/resolution.md`（`AUTO-001`…）

### L1 — 切片（analyst + pm）

1. `docs/product/prd.md` — MVP + §不做
2. `docs/product/design-scope.md` — 原型须体现 vs 可占位
3. 写 15 步以内 **配置→使用剧本** → `prd.md` §演示剧本
4. **关键词扩页：** 若需求含 SSO / 多租户 / 对外 API / 平台工作流 → 对照 [external-references.md](../../knowledge/external-references.md) 检查 design-package 是否增页
5. Gate：`prd_frozen`（无 open P0 discovery issue）

### L2 — IA 冻结（uiux）

1. 从剧本 **反推页面表** → `design-package.md`（先数页，再画）
2. 定义 **三套壳** ASCII → `ui-spec.md` §2（平台工作台 / 系统后台 / 运行态）
3. 写 **IA 规则表**（≥8 条 MUST / MUST NOT）→ `design-package.md` §1
4. 选 **演示垂直**（一个业务域 + 2 角色）→ `{demo}-seed.md`
5. 自检：剧本每一步有页面 ID

**壳数规则：** 有「平台 + 租户系统 + 业务运行」→ 三套；纯单应用 → 两套（管理 + 使用）。

### L3 — 细粒度（uiux + frontend 视角）

至少写深 **2 个标准页**（其余 P0 页写到控件级）：

| 标准页 | 必须细到 |
|--------|----------|
| 业务列表 `runtime-list` | 工具栏、场景、筛选、列、批量条、详情抽屉 — 见 config-spec §1.1 |
| 核心配置页（建模/表单设计/流程设计 择一） | Tab、抽屉分区、发布动作 |

然后：

1. `config-spec.md` — 全部 P0 页控件表
2. `page-inventory.md` — 索引 + 样例数据引用
3. `ui-spec.md` — 补中文文案、状态、错误态
4. 密度规约写入 ui-spec §2.5（首屏行数、单主按钮、批量条高度）

### L4 — 原型（uiux → Open Design 或 Agent HTML）

1. 整合 **`prototype-brief.md`**（全量一份，禁止碎片 patch）
2. 多角色自检 → `docs/design/reviews/*.md`（可 Agent 分角色写，见 checklist）
3. **一次** Open Design（`.cursor/skills/open-design.md`）或 Agent 生成静态 HTML
4. `design-diff.md` — 对照 [checklist.md](./checklist.md) 打勾
5. 缺口只改 spec + brief，**不要**在用户签字后再改 IA

## Agent 决策 vs 必须问用户

| Agent 自行决定 | 必须升级用户 |
|----------------|--------------|
| 页面布局密度、抽屉宽、色 token | 核心领域模型（父子容器关系） |
| 标准企业后台控件（搜索/筛选/导出） | MVP 砍掉用户故事主链上的能力 |
| 演示业务域名称与样例数据 | 认证/多租户/计费模型 |
| P1 页是否本期出 HTML | 两个互斥产品方向 |
| 按钮中文文案 | 合规/安全硬性约束 |

升级格式：`docs/decisions/pending.md`，阻塞 L2+。

## 与 Conductor / Gate 的关系

| Gate | 本 Skill 哪步完成 |
|------|-------------------|
| `prd_frozen` | L1 结束 |
| `design_package_complete` | L3 结束 + reviews pass |
| `design_user_approved` | L4 + 用户签字 |

未 `design_user_approved`：**禁止** backend/frontend/sql（见 `architecture/gates.md`）。

## 执行节奏（建议）

| 阶段 | Agent 产出 | 用户动作 |
|------|------------|----------|
| 第 1 轮 | L0～L1 + pending 问题 | 回复 pending（若有） |
| 第 2 轮 | L2～L3 + reviews | 无（内部） |
| 第 3 轮 | L4 brief + OD/HTML + design-diff | 审 `user-approval.md` |

目标：**用户只签字 1 次**，中间迭代在 Agent/文档内完成。

## 启动口令

```
读 .cursor/NEW-PROJECT.md
读 .cursor/skills/rough-to-prototype/SKILL.md
输入：./需求.md（或 docs/需求.md）
从 L0 执行；只写本层 outputs，不跳层写代码
```

## 关联 Skill（推荐）

| 时机 | Skill |
|------|-------|
| L0 展开前 | superpowers **brainstorming** |
| L4 声称完成 | superpowers **verification-before-completion** + [checklist.md](./checklist.md) |
| 项目结束返工 | [learning-loop.md](../../knowledge/learning-loop.md) |

## 附加资源

- Gate 与断言：[checklist.md](./checklist.md)
- 沉默时默认：[defaults.md](./defaults.md)
- 工作流编排：[phase-0-requirement-expansion.md](../../workflows/phase-0-requirement-expansion.md)
