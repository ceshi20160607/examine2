# RPFD 测试报告：无代码平台需求

> **输入：** 用户 1 段粗糙需求（~150 字）  
> **方法：** `.cursor/skills/rough-to-prototype` L0～L4（L4 HTML 抽样 4/33 P0）  
> **对照：** examine2 redesign（34 页 · 车系统）

---

## 1. 展开结果摘要

| 维度 | 粗糙输入 | RPFD 展开后 |
|------|----------|-------------|
| 文档页数 | 0 | 10+ spec 文件 |
| P0 页面规划 | 未定义 | **33 页**（design-package） |
| 名词定义 | 6 个模糊概念 | **14 项** glossary（是什么/不是什么） |
| 演示剧本 | 无 | **15 步** 配置→使用 |
| 标准页 deep spec | 无 | 列表+建模+**API控制台+SSO+租户** |
| HTML 原型 | 无 | **4 页抽样** + index |

---

## 2. 与 examine2 最终态差异

| 项 | examine2 redesign | 本次 FlexBase 测试 | 差异说明 |
|----|-------------------|-------------------|----------|
| P0 页数 | ~28–34 | 33 | 同量级；**+租户、+SSO、+API控制台** |
| 三层 IA | ✅ | ✅ | 同族结构，glossary 更强调 Console/Portal |
| 模块组导航 | ✅ | ✅ | 复用 lessons |
| 平台租户管理 | 弱/无独立页 | **P-PLAT-ADMIN-TENANTS** | 用户明确多租户 → **需补 examine2** |
| SSO | 需求有、原型弱 | **独立 P0 页** | 网络规约 WorkOS/OIDC |
| API 映射配置 | external-app | **plat-admin-api-console** | Kong/AWS APIM 规约 |
| 开发者 Portal | 无 | P1 占位 | ADR-055 Portal/Console 分离 |
| 演示域 | 车系统 | 人事请假 | 仅 seed 不同 |
| HTML 完成度 | 28+ 全 P0 | **4/33 抽样** | 测试未跑满 L4 OD |

**结论：** 粗糙需求经 RPFD **可展开到与 examine2 同密度 spec**；增量页来自用户提到的 SSO/多租户/API，**external-references.md 有帮助**。

---

## 3. 若只给模型「一段话」直接画原型（模拟 v1 错误）

| 预测缺失 | 原因 |
|----------|------|
| 缺 50%+ P0 页 | 无 design-package 先数页 |
| SSO/租户/API 控制台合并或缺失 | 无 L3 deep spec |
| 「应用」再次歧义 | 无 glossary |
| 列表简易无场景/抽屉 | 无 config-spec + lessons C |
| 平台/系统 Flow 混淆 | 无 IA 规则表 |

→ 与 examine2 v1（9/21 页）同类问题。

---

## 4. 规约包需补充项（本次测试发现）

| # | 补充 | 状态 |
|---|------|------|
| 1 | `.cursor/knowledge/external-references.md` 网络规约 | ✅ 已写 |
| 2 | glossary 模板含 **Console/Portal、Tenant scope** | 建议并入 templates |
| 3 | config-spec 模板增 **API 映射表、SSO、租户模式** 标准节 | 待补 templates |
| 4 | defaults.md 增「无代码+多租户+SSO」默认页 | 建议更新 |
| 5 | pack-portable.ps1 含 external-references | 待更新脚本 |
| 6 | L4 全量 33 HTML 需 Open Design 一次或 Agent 批量 | 测试仅抽样 |
| 7 | `learnings/2026-06-23-nocode-test.md` | 见下 |

---

## 5. 建议用户确认（pending · 可选）

1. API **Console 与 Developer Portal** 是否首期都要 P0？（当前 Console P0 / Portal P1）
2. 平台租户 vs 系统内租户：两层都要，还是系统租户即可？

---

## 6. 产出路径

```
docs/_test/nocode-platform/
├── product/          L0-L1
├── decisions/        AUTO
├── design/           L2-L4
│   └── prototypes/   4 HTML + index
└── TEST-REPORT.md    本文件
```

## 7. 下一步

- 你确认 pending → 合并进 resolution → 一次 OD 生成 33 P0 HTML  
- 或将 `external-references` + 租户/SSO/API 节并入 examine2 主 design-package
