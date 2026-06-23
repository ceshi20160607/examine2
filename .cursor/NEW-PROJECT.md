# 新项目：从 需求.md 到可 Coding 原型

> **用途：** 复制 `.cursor` 到新仓库后，只看本文件 + 写 `需求.md` 即可启动。  
> **方法论总纲：** [METHODOLOGY.md](./METHODOLOGY.md)（错因、RPFD 五层、自我学习）  
> **不依赖** unexamine 旧代码、`.oldbk`。

## 你要做的（3 步）

### 1. 复制 `.cursor`

整个目录拷到新项目根目录。

可选：删掉 examine2 专用内容（见 [PORTABLE.md](./PORTABLE.md) §「复制后清理」）。

### 2. 初始化 Session

```text
复制 .cursor/session/state.template.json → .cursor/session/state.json
清空 .cursor/session/issues/registry.jsonl（留空文件即可）
```

### 3. 写粗糙需求

在项目**根目录**创建 `需求.md`（或 `docs/需求.md`），例如：

```markdown
# 工单系统

管理员配置字段和审批流程，员工提交和处理工单。
需要列表筛选、导出，外部系统能调 API 创建工单。
首版不做移动端。
```

## 给 Agent 的一句话（复制发送）

```text
读 .cursor/METHODOLOGY.md
读 .cursor/knowledge/prototype-pipeline-lessons.md
读 .cursor/skills/rough-to-prototype/SKILL.md
输入：项目根目录 需求.md
从 L0 执行到 L4；完成前跑 checklist；禁止跳层写代码
L4 后若曾返工，执行 knowledge/learning-loop.md
```

## Agent 应交付什么

| 阶段 | 你得到 |
|------|--------|
| L0～L1 | `docs/product/*` + 若有 `pending.md` 等你回复 |
| L2～L3 | `docs/design/design-package.md` … `config-spec.md`（页表+按钮级） |
| L4 | `docs/design/prototypes/**/*.html` + `design-diff.md` |
| 你签字 | 改 `docs/design/user-approval.md` → `approved: true` |

之后才可进入 API 契约与 Coding（若你也复制了 build 相关 workflow）。

## 输入文件约定

Agent 按以下顺序找需求（找到即用，并复制到 `docs/product/rough-input.md` 留档）：

1. `./需求.md`
2. `./docs/需求.md`
3. `./docs/user_requirement.md`

## 与 Open Design

- **有 Open Design：** L4 用 `prototype-brief.md` 调一次 OD（见 `skills/open-design.md`）
- **没有 OD：** Agent 直接生成静态 HTML，标准同 brief

## 常见问题

**Q：整个 `.cursor` 都要拷吗？**  
A：只做原型时拷「最小包」即可，见 PORTABLE.md。

**Q：需求只有三句话够吗？**  
A：够。Agent 按 Skill 五层展开；只有产品模型冲突时才问你。

**Q：怎么知道可以开始写代码？**  
A：`user-approval.md` 你签字 + `state.json` 里 `design_user_approved: true`。
