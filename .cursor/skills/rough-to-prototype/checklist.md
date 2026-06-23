# Rough → Prototype 验收清单

Agent 在 L3 末（内部 reviews）和 L4 末（design-diff）各跑一遍。

## A. 文档完整性

- [ ] `rough-input.md` 保留用户原文
- [ ] `domain-glossary.md` 每个核心词有「不是什么」
- [ ] `prd.md` 有 §不做 且与 design-scope 一致
- [ ] `design-package.md` P0 页 **零 TBD**
- [ ] `prototype-brief.md` P0 页数 = design-package P0 行数
- [ ] `{demo}-seed.md` 覆盖列表/表单/详情样例字段

## B. IA 断言（12 条，按产品裁剪）

通用后台类项目至少满足：

- [ ] 管理态与使用态菜单分离；普通用户无配置入口
- [ ] 导航无重复入口（例：顶栏与侧栏不重复待办）
- [ ] 演示剧本 15 步内每步有页面 ID
- [ ] 权限分「谁配 / 谁用」两角色可走通
- [ ] 列表页：搜索 + 筛选 + 导出在工具栏，无独立「导出中心」同级菜单
- [ ] 列表页：行点击 → 详情抽屉或等价，不丢列表上下文
- [ ] 配置链顺序在 spec 中写清（先建什么后建什么）
- [ ] 运行态壳在所有 runtime 页一致
- [ ] 禁止运行态暴露技术 ID / 原始 JSON 为主内容
- [ ] 全页仅一个「新建」类主按钮
- [ ] 勾选前无批量操作条；勾选后出现且紧凑
- [ ] index.html 链到全部 P0 原型

## C. 细粒度（Coding 向）

- [ ] `config-spec` 每 P0 页有控件表
- [ ] 控件表含 **权限点** 或显隐规则
- [ ] 标准列表页含：场景、高级筛选、列设置、导入/导出、序号/勾选、批量条
- [ ] 核心配置页含：保存/发布或等价生命周期
- [ ] `page-inventory.md` 与 config-spec 无矛盾

## D. 密度与体验

- [ ] ui-spec 写明首屏业务行数目标（默认 1440×900 ≥ 8 行）
- [ ] 场景控件非整行横铺
- [ ] 用户/角色若双复杂度高 → 独立页面（非强制 Tab 堆叠）

## E. 内部 reviews（Agent 分角色写 6 文件）

| 角色 | 文件 | pass 条件 |
|------|------|-----------|
| analyst | `reviews/analyst.md` | 与 prd、resolution 零冲突 |
| pm | `reviews/pm.md` | 无 open P0 design issue |
| uiux | `reviews/uiux.md` | 壳 + 标准页 wire 可实现 |
| backend | `reviews/backend.md` | 名词可落库（概念级） |
| frontend | `reviews/frontend.md` | 路由/壳可实现 |
| test | `reviews/test.md` | 剧本每步可测 |

全部 pass → Conductor 可设 `design_package_complete: true`。

## F. 原型 diff（L4 后）

- [ ] P0 HTML 数量达标
- [ ] 样例数据来自 seed，非 Lorem / generic-table
- [ ] design-diff.md 记录 pass/fail 与 P2 偏差
- [ ] user-approval.md 审阅清单已生成（用户勾选）
