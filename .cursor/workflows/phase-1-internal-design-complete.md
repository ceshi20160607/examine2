# Phase 1：设计包内部完工（Open Design 之前）

> **原则：** 设计完整性由**团队内部**闭环；**不**把 IA 核对推给用户。  
> 用户只在两件事上签字：① 调用 Open Design 前的「可以开始了」（可选一句确认）② 原型产出后的 `user-approval.md`。

## 流程

```
ui-spec 初稿
    ↓
【内部】design-package 补全页面清单 + 线框 + 术语
    ↓
【内部】多角色审阅 reviews/* → 提 issue → PM 裁决 → 闭环
    ↓
【内部】Conductor 判定 design_package_complete = true
    ↓
通知用户：设计包已齐，请调用 Open Design（附 brief 路径）
    ↓
Open Design → design-diff → user-approval（唯一设计硬闸门）
```

## 内部步骤（Conductor 调度）

| # | 角色 | 产出 | 完成标准 |
|---|------|------|----------|
| 1 | uiux | `design-package.md` 页面总表 + `ui-spec.md` 补全 | 无「TBD」页 |
| 2 | analyst | `reviews/analyst.md` | 与 user_requirement 附录 A 零冲突 |
| 3 | pm | `reviews/pm.md` + 裁决 open issue | 无 open P0/P1 design issue |
| 4 | backend | `reviews/backend.md` | 对象边界可落库（概念） |
| 5 | frontend | `reviews/frontend.md` | 三套壳 + 顶栏组导航可实现 |
| 6 | test | `reviews/test.md` | 车系统剧本每步有页面 ID |
| 7 | uiux | `prototype-brief.md`（OD 输入清单） | 与 design-package 1:1 |
| 8 | conductor | 更新 `state.json` `design_package_complete` | 上表全 pass |

## 禁止

- 未 `design_package_complete` 让用户调用 Open Design
- 把「请勾选 ia-confirmed」当作 Gate 甩给用户
- 原型阶段才发现缺页、缺流程、缺平台后台

## 用户触点（仅一次 OD 前）

Conductor 发送固定话术：

> 设计包已内部评审通过（见 `docs/design/design-package.md`）。  
> 请用 Open Design 按 `prototype-brief.md` 生成原型；生成后审 `user-approval.md`。

用户**无需**填写 IA 勾选表。

## 升级用户（仅当）

- PM 无法裁决（写入 `docs/decisions/pending.md`）
- 产品边界变更（新 RES）

## 退出

- [ ] `design_package_complete: true`
- [ ] `registry.jsonl` 无 open P0 design issue
- [ ] `prototype-brief.md` 页数 = `design-package.md` P0 页数

→ `phase-1-design-freeze.md` 步骤 2（Open Design）
