# 沉淀知识库（Knowledge）

> **目的：** 从 `docs/user_requirement.md` + `.oldbk/` 已验证实现中提炼「必须/禁止」，  
> **避免** 重跑 v1 的 P7–P15 错误循环。  
> 新 Worker **先读这里**，再读需求；只补充缺口，不从零争论已冻结结论。

## 文件索引

| 文件 | 内容 |
|------|------|
| [frozen-rules.md](./frozen-rules.md) | 必须 / 禁止（产品、技术、流程） |
| [domain-model.md](./domain-model.md) | 平台层 vs 系统层、账号、权限、术语 |
| [agent-operating-rules.md](./agent-operating-rules.md) | 通用工作规约：适配所有项目的落盘事实、连带推理、主动作唯一、列表/详情、设计自检 |
| [project-operating-rules.md](./project-operating-rules.md) | unexamine 项目规约：四套壳、工作管理、消息待办、设计闸门和后台配置 |
| [failure-lessons.md](./failure-lessons.md) | v1 失败教训，不得重复 |
| [decision-authority.md](./decision-authority.md) | PM 决策 vs 上报你 |
| [validated-capabilities.md](./validated-capabilities.md) | `.oldbk` 后端已具备、可复用能力清单 |

## 使用方式

### Phase 0 不再「从零理解」

```
1. 所有 Worker 先读 knowledge/*，其中 `agent-operating-rules.md`、`project-operating-rules.md` 与 `failure-lessons.md` 必读
2. analyst 只输出：需求与 knowledge 的差异、不确定项
3. pm 基于 knowledge + analyst 差异写 prd（引用 frozen-rules，不重写）
4. 无差异项 → 跳过重复评审轮次
```

### 与 Gate 的关系

- `frozen-rules.md` 中的 **P0 禁止项** 违反 → 直接开 issue，PM 不得放行 Gate
- 需求与 frozen-rules 冲突 → PM 必须 `escalated` 给你，不得自行改规则

## 维护

- 用户决策写入 `docs/decisions/resolution.md` 后同步更新本目录
- 最新：`RES-2026-06-15-001`（导出动作化、统一认证、平台 IA、generator、plat_admin）
