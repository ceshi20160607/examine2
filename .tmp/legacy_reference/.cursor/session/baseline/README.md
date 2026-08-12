# Examine2 开发前冻结基线

## 当前结论

状态：`P1_IMPLEMENTATION_ACTIVE`。本目录合同与开发 Gate 已由项目所有者确认；实现只能在冻结写集内推进，任何合同、测试计划或写入范围变更都会使 Gate 哈希失效并要求重新校验。

本基线解决的是第六次重构仍未解决的根因：需求、页面、数据、接口、任务和测试此前没有同时冻结。它不是新的“第七版实现”，而是后续唯一允许实现消费的合同入口。

## 事实优先级

1. 项目所有者已确认决定。
2. `docs/user_requirement.md`，其中 A.13 在冲突时优先。
3. 本目录内通过 Gate 的冻结合同。
4. `.cursor/session/project-delivery.json` 中当前 detailed phase 的需求、用例、任务和周期。
5. 原型、旧实现和历史 evidence 仅作参考，不能产生产品语义或完成状态。

## 冻结产物

| 产物 | 冻结内容 |
|---|---|
| `product-contract.md` | 产品区域、导航、路由、页面安置、明确删除项和阶段边界 |
| `architecture-contract.md` | Maven 模块、数据所有权、Base/manage 调用链、跨模块边界和旧实现处置 |
| `p1-api-contract.json` | P1 HTTP 方法、路径、认证、输入输出、错误码、读回和幂等 |
| `p1-data-contract.json` | P1 表、所有者、约束、事务、迁移和默认管理员 |
| `p1-backend-boundary-contract.json` | 生成代码、业务用例、Repository 例外和静态审计 Gate |
| `runtime-module-list.ui.json` | 模块运行列表、页头、筛选、批量动作、右侧标签详情和仪表盘入口边界 |
| `execution-strategy.md` | 选择性重建决策、复用/冻结范围、时间与 Token 预算、并行方式和防返工机制 |
| `completion-audit.md` | 对开发前定版目标逐项举证并给出条件准入结论 |
| `pre-development-gate.json` | 是否允许进入 P1 编码的机器状态 |
| `../project-delivery.json` | 原子需求、验收用例、任务 DAG 和四小时周期 |

## 已冻结的基础决定

- `.base` 是通用工程框架，`.cursor` 是当前项目实例；项目业务事实不得进入 Base。
- 当前仓库保留构建、模块骨架、生成器和可证明正确的基础设施；旧业务壳只读冻结。
- 新数据库使用独立 vNext 数据库和 `sql/migration-vnext`，不在 legacy 数据库上直接执行。
- 一张表只有一个 Maven 模块所有者；平台/系统差异优先使用明确 scope，不复制第二套 Flow、Dashboard、Work、OpenAPI 表。
- 普通 CRUD 只能走生成的 `IService -> ServiceImpl -> BaseMapper`；业务层不能直接调用 BaseMapper。
- 平台登录默认进入“我的系统”；系统切换后按“已发布仪表盘 -> 管理员引导 -> 首个有权限模块 -> 明确无权限页”的顺序落点。
- 运行态没有“运营”、独立“报表/报表工作室”和模块收藏导航。
- 功能阶段参考桌面视口固定为 `1440x900`；完整响应式、无障碍和跨模块视觉收口后置到 hardening。
- 性能、容量、百万/千万数据和长并发不进入核心交付计划。

## 开发准入

只有同时满足下列条件，P1 task 才能从 `planned` 变成 `ready`：

1. 产品、API、数据、后端边界和 P1 UI 合同状态为 accepted，且 SHA-256 与交付合同一致。
2. P1 每个可见动作都能追到 accepted requirement、正向/失败 case、task 和 API/导航。
3. P1 正向业务 case 包含真实入口、输入、持久化读回；失败和权限 case 不冒充正向完成。
4. 后端审计拒绝 `manage/application/controller/domain -> base.mapper`、`JdbcTemplate`、`ResultSet` 和内联 SQL。
5. 验收命令指向真实存在的测试，禁止 `failIfNoSpecifiedTests=false`，VERIFY 不能只运行计划校验器。
6. 新 evidence 只能写入 `.cursor/session/evidence/baseline/<cycleId>/`；历史 `fast-*`、`release-*`、`cycle118-performance` 不得被新进度引用。
7. `.cursor` 框架校验、交付合同校验、UI 合同校验和测试计划校验全部通过。

未通过时必须停在合同层修正，不能用实现代码绕过 Gate。
