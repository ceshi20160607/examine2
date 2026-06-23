# 测试差距分析（无代码平台 · 2026-06-23）

> 案例：~150 字需求 → FlexBase 无代码平台（平台层+系统层+SSO+多租户+API+Flow）  
> 方法：hu / RPFD 五层  
> 对照：examine2 redesign（28+ P0 HTML · 车系统）

---

## 1. 差距量化

### 1.1 三层对比

| 层级 | 无规约直接画 | hu + RPFD（本次） | examine2 最终 | 说明 |
|------|:------------:|:-----------------:|:-------------:|------|
| **L0-L3 文档** | 0～20% | **~95%** | 100% | 本次 spec 与最终同密度；差在未写 ui-spec 全文 |
| **P0 页表** | ~40% 页数 | **100%**（33/33） | 100% | 页表是最大差距来源 |
| **P0 HTML** | ~40% | **12%**（4/33） | 100% | HTML 需 L4 专门跑一轮，测试未跑满 |
| **可 Coding** | ❌ | ✅（spec） | ✅ | 有 config-spec + 页表即可契约；HTML 辅助 |

### 1.2 用数字说话

- **页数差距：** 无规约约 **9 页** vs 应有 **33 页** → 少 **73%**（examine2 v1 为 9/21 ≈ 少 57%，同类问题）
- **Spec 文件：** 0 份 vs **10+ 份** → Agent 补全工作量主要在 L0-L3，**约 1～2 会话**
- **HTML 差距：** 4 vs 33 → 再 **1 次 Open Design**（或 Agent 批量）可闭合，**不是重新想 IA**

---

## 2. 为啥 spec 能拉满、HTML 还少？

| 原因 | 说明 |
|------|------|
| **规约故意分阶** | SKILL 禁止 L2 前 HTML → 先保证「数对页、写对按钮」 |
| **测试范围** | 本次验证「架构是否能把粗糙需求展开」，只抽 4 页代表：平台入口、API 控制台、SSO、标准列表 |
| **L4 成本** | 33 页 HTML 是 **执行量** 问题，不是 **设计质量** 问题；有 prototype-brief 后一次 OD 即可 |
| **examine2 历史** | 最终 28+ 页也经历了 brief 整合 + OD，不是一段话直接出 |

**结论：** 差距主要在 **「有没有 hu」**（spec 0%→95%），不在 **「hu 能不能出 HTML」**（出 HTML 是 L4 执行一步）。

---

## 3. 与 examine2 最终原型的逐项差异

| 项 | examine2 | FlexBase 测试 | 差距性质 |
|----|----------|---------------|----------|
| 三层 IA | ✅ | ✅ | **无实质差距** |
| 模块组+列表 deep | ✅ | ✅ | **同 lessons** |
| 平台租户页 | 弱 | ✅ P0 | **需求驱动增量** |
| SSO 专页 | 弱 | ✅ P0 | **需求+external-references** |
| API 控制台+映射 | external-app | ✅ 独立 Console | **Kong 规约增量** |
| 演示 seed | 车系统 | 人事请假 | **仅样例不同** |
| 全量 HTML | ✅ | 4 页 | **L4 未跑满** |

**Spec 与 examine2 最终：同量级（~95%）。**  
**HTML 与 examine2 最终：差 ~88% 页数，因测试未执行全量 L4。**

---

## 4. 若无 hu，还会错在哪（模拟 v1）

1. **缺 50%+ 页面** — 无 design-package 先数页  
2. **SSO/租户/API 合并或消失** — 无 L3 deep + 无 external-references  
3. **「应用」再次歧义** — 无 glossary  
4. **列表无场景/抽屉/8 行** — 无 config-spec + lessons C  
5. **平台 Flow vs 系统 Flow 混** — 无 IA 规则表  

→ 与 examine2 **v1 作废** 同一类错误，需 **3～5 轮** 用户改稿才能到 redesign。

---

## 5. hu 包如何缩小差距

| 机制 | 缩小什么差距 |
|------|--------------|
| RPFD 五层顺序 | spec 0% → 95% |
| prototype-pipeline-lessons | 跨项目不重复 v1 错误 |
| external-references | SSO/租户/Console 不漏页 |
| checklist + design-diff | HTML 机械验收 |
| learning-loop | 每次返工 → 下项目 F1…Fn |
| defaults 关键词扩页 | 无代码/多租户/API 自动增页 |

---

## 6. 试跑产物位置（examine2 仓库内）

```
docs/_test/nocode-platform/
├── product/           L0-L1
├── design/            L2-L4 spec
│   └── prototypes/    4 HTML + index
└── TEST-REPORT.md
```

打开：`docs/_test/nocode-platform/design/prototypes/index.html`

---

## 7. 你要的「完美」还差什么

| 待补 | 负责 |
|------|------|
| 全量 33 P0 HTML | L4：一次 OD 或 Agent 批量 |
| ui-spec 全文 | L2 补写（本次写了壳+config） |
| 2 个 pending（Portal P0? 租户两层?） | 你确认 → resolution |
| Java phase-2+ 包 | 签字后另拷 backend workflow |

**有 hu 后，从粗糙需求到「可签字 spec」≈ 1～2 轮 Agent；到「全 HTML」再 +1 轮 L4。**
