# 设计与工程契约包

## 1. Meta

- package_id:
- linked_requirement_package:
- owner: architect
- product_owner:
- uiux_owner:
- data_owner:
- reviewers:
- status: draft | review | accepted | blocked
- version:

## 2. 设计目标与非目标

- 目标角色旅程：
- 本设计解决：
- 本设计不解决：
- 关键约束：

## 3. 信息架构与交互

| 角色 | 入口/导航 | 主任务表面 | 关键动作 | 状态/权限 | 规格或原型路径 |
|---|---|---|---|---|---|
|  |  |  |  |  |  |

## 4. 系统与模块边界

| 模块/上下文 | 职责 | 非职责 | 输入输出 | 数据归属 | 依赖 |
|---|---|---|---|---|---|
|  |  |  |  |  |  |

## 5. 关键流程与失败模式

| flow_id | 正常流程 | 状态 | 失败/超时/重试 | 幂等/并发 | 审计/可观测 |
|---|---|---|---|---|---|
|  |  |  |  |  |  |

## 6. 数据设计

| 数据域/实体 | 生命周期 | 关系与约束 | 隔离/权限 | 历史/版本 | 迁移/回滚 |
|---|---|---|---|---|---|
|  |  |  |  |  |  |

## 7. 跨层契约

| contract_id | 用户动作 | Frontend | API/Backend | Data | Permission | State/Error | Side Effects |
|---|---|---|---|---|---|---|---|
| CTR-001 |  |  |  |  |  |  |  |

## 8. 生成与手写边界

| 范围 | generated plumbing | handwritten behavior | 禁止覆盖 | 验证 |
|---|---|---|---|---|
|  |  |  |  |  |

## 9. 测试、运行与发布设计

| 维度 | 方案 | 进入条件 | 证据 | owner |
|---|---|---|---|---|
| task/journey/security/performance/migration/deploy/rollback |  |  |  |  |

## 10. 决策和风险

| id | 类型 | 选项/风险 | 决定/缓解 | owner | verifier | 状态 |
|---|---|---|---|---|---|---|
|  |  |  |  |  |  | OPEN |

## 11. 多角色 Review

| role | 关注点 | verdict | issue/evidence |
|---|---|---|---|
| product/uiux/architect/dba/backend/frontend/test/ops |  | pass/fail/blocked |  |

## 12. Gate

- [ ] 需求行和角色旅程均有设计映射。
- [ ] UI、模块、数据、接口、权限、状态、错误和副作用一致。
- [ ] 生成/手写边界、迁移、测试和发布方案清楚。
- [ ] P0 设计问题关闭或升级。
- [ ] 首个开发任务不需要猜测关键前提。
- [ ] 实例要求的用户设计确认已完成。
- [ ] Leader 验收完成。
