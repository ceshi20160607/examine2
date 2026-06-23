# hu — 你只给需求，Agent 出原型

> 复制 `hu/` 到新项目 → 运行 `install.ps1` → **只写 `需求.md`**。  
> 规约、展开、全量 HTML、自检、学习 — **全是 Agent 的事**。

---

## 你的唯一输入

```markdown
# 需求.md（根目录，原文即可，三五句话够）

我要做一个 xxx 系统，要有 xxx…
```

**不需要：** 启动口令、选 Skill、填表、答 pending、分阶段确认。

---

## Agent 自动做什么

读 [AUTONOMOUS.md](./AUTONOMOUS.md) → 对照 [reference-case.md](./knowledge/reference-case.md)（examine2 最终态）→ L0～L4 **一次跑完** → **全部 P0 HTML** → 告诉你打开 `docs/design/prototypes/index.html`。

中间规约：`skills/rough-to-prototype/`、`knowledge/*`、`METHODOLOGY.md` — 你不用管。

---

## 复制到新项目

```powershell
.\hu\install.ps1 -Target "D:\your-java-project"
```

新项目里只创建 `需求.md`，然后对 Cursor 说「按需求做」或直接打开 `需求.md` 让 Agent 工作即可。

---

## 自我学习

Agent 每轮自动对照 examine2 范例 + checklist，差项写入 `knowledge/learnings/` 并更新 lessons — **不用你教**。

---

## 文档索引

| 文件 | 给谁看 |
|------|--------|
| [AUTONOMOUS.md](./AUTONOMOUS.md) | Agent 契约 |
| [TEST-GAP-ANALYSIS.md](./TEST-GAP-ANALYSIS.md) | 为何不能「直接画原型」 |
| [METHODOLOGY.md](./METHODOLOGY.md) | RPFD 五层原理 |

---

## 维护

examine2 内：`.\scripts\pack-portable.ps1` 同步到 `hu/`。
