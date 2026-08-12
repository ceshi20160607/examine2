# 需求理解与产品边界包

## 1. Meta

- package_id:
- package_scope: project_baseline | delivery_slice
- affected_slice_ids: []
- gate_type: requirements_baseline | slice_requirement
- owner: product
- analyst:
- reviewers:
- status: draft | review | accepted | blocked
- version:
- updated_at:

## 2. 来源索引

| source_id | 路径 | 优先级 | 内容 | 是否当前事实源 | 冲突规则 |
|---|---|---:|---|---:|---|
| SRC-001 |  | 1 |  | yes/no |  |

## 3. 最终产品目标

从用户角度描述最终可使用结果，不写页面、接口或代码目录目标。

## 4. 范围

| scope_id | 内容 | in/out/deferred | 来源 | 理由 | 验收影响 |
|---|---|---|---|---|---|
| SCP-001 |  | in |  |  |  |

## 5. 角色与入口

| role_id | 真实角色 | 责任/目标 | 真实入口 | 可见范围 | 禁止范围 |
|---|---|---|---|---|---|
| ROLE-001 |  |  |  |  |  |

## 6. 原子需求总账

| req_id | source_type | 来源 | priority | phase | 角色/入口 | 触发/单一行为 | 可见结果/持久读回 | UI安置/默认显隐 | 明确排除 | 状态/批准人 |
|---|---|---|---|---|---|---|---|---|---|---|
| REQ-001 | raw_user |  | must | PHASE-01 |  |  |  |  |  | committed / user |

`source_type` 只使用 `raw_user | derived | proposal`；后两类没有权威批准人时不能进入 committed。状态只使用 `committed | deferred | excluded`。一行只允许一个角色、一个入口、一个触发、一个行为和一个结果；不能把搜索、视图、收藏、批量操作等合并为一条需求。

## 7. 验收用例索引

| case_id | req_id | level | type | 入口/步骤 | 可见结果 | 持久读回 | business outcome | required |
|---|---|---|---|---|---|---|---|---:|
| CASE-001 | REQ-001 | task/cycle/module_page/phase/project | positive |  |  |  | achieved | yes |

详细用例使用 `templates/acceptance-case.md`。`test pass`、`business achieved`、`requirement accepted` 分开记录；权限拒绝、校验失败或错误恢复用例通过不能单独关闭正向需求。

## 8. 角色旅程

| journey_id | 角色 | 入口 | 用户任务 | 关键步骤 | 持久结果 | 失败结果 | 关联需求 |
|---|---|---|---|---|---|---|---|
| JRN-001 |  |  |  |  |  |  |  |

每条关键旅程另写：

1. 前置身份、权限和数据。
2. 用户操作。
3. 系统响应、状态和副作用。
4. 成功后的可见位置和读回。
5. 权限拒绝、校验、空、错误、异步和恢复路径。

## 9. 业务规则与术语

| rule_id | 术语/规则 | 定义 | 适用范围 | 来源 | 验证方式 |
|---|---|---|---|---|---|
| RULE-001 |  |  |  |  |  |

## 10. 数据、权限、状态与副作用

| 对象/动作 | 数据归属 | 权限正例 | 权限负例 | 状态变化 | 消息/审计/外部副作用 |
|---|---|---|---|---|---|
|  |  |  |  |  |  |

## 11. 非功能与交付要求

| nfr_id | 类型 | 要求 | 当前阶段 | 启动条件 | 验证方法 | 权威来源 |
|---|---|---|---|---|---|---|
| NFR-001 | security/reliability/operations/usability |  |  |  |  |  |

性能、百万/千万数据和长并发不进入核心项目交付范围。只有项目功能全部完成后，依据明确的项目需要、容量来源和独立预算另立可选专项。

## 12. 冲突、假设与开放问题

### 来源冲突

| id | 来源 A | 来源 B | 影响 | 建议决策人 | 状态 |
|---|---|---|---|---|---|
|  |  |  |  |  | OPEN |

### 假设

| id | 假设 | 依据 | 验证人 | 到期/触发条件 | 状态 |
|---|---|---|---|---|---|
|  |  |  |  |  | OPEN |

### 开放问题

| id | 问题 | 影响 | owner | 可选方案/推荐 | 状态 |
|---|---|---|---|---|---|
|  |  |  |  |  | OPEN |

## 13. 专业角色预审

| 角色 | 结论 | issue | 是否阻断设计 |
|---|---|---|---:|
| uiux/architect/dba/backend/frontend/test/ops |  |  | yes/no |

## 14. Gate

- [ ] 来源和需求双向可追踪。
- [ ] 每条 committed 需求是单一行为原子，且至少有一个 required positive case。
- [ ] 当前期 UI 安置、默认显隐、明确排除和持久读回不需要下游猜测。
- [ ] derived/proposal 未经权威批准不会进入 committed。
- [ ] 目标、范围、角色和旅程清晰。
- [ ] 数据、权限、状态、异常、副作用和非功能要求已覆盖。
- [ ] P0 冲突关闭或形成用户待决项。
- [ ] 下游角色不需要猜测关键产品行为。
- [ ] PM 会审完成。
- [ ] Leader 验收完成。
- [ ] 当前核心交付没有性能、百万/千万数据或长并发任务。

项目基线 Gate 最长 240 分钟形成可执行路线，不要求冻结全部远期细节。每个 delivery slice 在进入设计或 coding 前另过一次 `slice_requirement` Gate；当前切片 Gate 不能被项目基线状态替代。
