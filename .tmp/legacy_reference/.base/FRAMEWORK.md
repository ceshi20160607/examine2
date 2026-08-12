# Base 多 Agent 工程框架方案

## 1. 定位

Base 是可复制、可版本化、可校验的工程控制面。它接收一个项目的权威需求文件和实例配置，产出项目阶段、原子需求、验收用例、任务 DAG、四小时周期、专业实现和分层验证规则。Base 不包含具体项目业务、技术栈、账号、历史进度或页面名称。

实例化后的项目使用自己的 `.cursor` 独立运行；Base 升级通过 manifest + lock 同步公共文件，项目覆盖层只能收紧公共约束。真实项目中被执行、被验证且去除项目特征后仍成立的规则，才允许回灌 Base。

## 2. 控制关系

```text
用户目标/权威需求
        ↓
Leader / 主 Agent（目标、阶段、调度、Gate）
        ↓
PM + Planner（原子合同、DAG、并行写集、4h 周期）
        ↓
专业 Agent（backend/frontend/uiux/test/...）
        ↓ 显式调用
Skills（流程规范、领域约束、确定性验证器）
        ↓
代码、合同、测试证据、可运行增量
```

采用“Agent 调用 Skill”，不采用“Skill 使用 Agent”。原因是：

- Agent 对当前目标、项目上下文、取舍和异常负责；Skill 无身份、无长期状态、无产品决策权。
- Skill 固定重复且脆弱的步骤，例如任务合同校验、管理页面布局、后端分层审计和测试层级选择。
- 多 Agent 只解决可并行工作，不负责弥补需求不清。任务卡不清时不得通过增加 Agent 数量碰运气。
- 实现者不能验收自己的结果；独立 verifier 只接收合同和产物，不继承实现聊天。

## 3. 唯一交付链

```text
authoritative requirement.md
  -> project phases（从骨架到可用再到丰富）
  -> atomic requirements（一个角色/入口/触发/动作/对象/结果）
  -> acceptance cases（前置/步骤/入参/可见结果/读回/失败）
  -> artifacts（UI/API/data/permission/state/error）
  -> <=120m tasks（不允许新增语义）
  -> <=240m cycles（一个可演示结果）
  -> module/phase/project acceptance
  -> project retrospective
  -> Base candidate -> Base regression -> Base release
```

需求、用例、任务是三联单：

- 每条 committed 需求至少有一个 required 正向用例。
- 每个 required 用例必须被任务覆盖。
- 每个任务必须反向引用需求、用例和冻结 artifact。
- 测试 PASS、正向业务达成、需求方接受分别记录，禁止互相推导。
- 缺少任一方向的链接、存在未决产品判断或用错误结果冒充成功时，验证器拒绝进入开发。

## 4. 项目阶段和四小时机制

项目阶段按用户可用结果划分，不按技术层划分。典型顺序是：

1. 工程骨架、数据库、默认账号、登录/注册和首个真实入口。
2. 核心管理能力和第一个可实际使用的业务闭环。
3. 依赖核心闭环的流程、集成或高级能力。
4. 协作、消息、文件和其他丰富能力。
5. 功能完成后的统一 hardening、发布和验收。

项目复杂时可增加阶段，但每阶段结束必须比上一阶段多一个可直接使用的结果。未来阶段只保留 outline；当前阶段才展开详细需求、用例和任务，避免远期设计拖慢首个可运行增量。

每个交付周期最多 240 分钟：

- 周期开始冻结一个 demo 结果、完整范围、剩余范围、任务 DAG、写集、资源和验收用例。
- coding task 为 10..120 分钟，推荐 45..90 分钟；超过 120 分钟继续按可验证结果拆分。
- task 只做受影响编译/单测，不各自重启、全量回归、打包或跑浏览器矩阵。
- 240 分钟到点后停止扩范围，对绿色子集做一次集成和真实入口验证；未完成项如实结转。
- 一个接口、页面片段或测试证据只是 task，不能单独包装成四小时周期。

当前阶段开始编码前，实例必须生成并验证 `session/baseline/pre-development-gate.json`。工程 Gate 只证明权威需求哈希、产品/API/数据/UI/后端合同、需求-用例-任务双向追踪、测试计划和执行命令已经一致；项目所有者确认前仍不得把 task 从 `planned` 改成 `ready`。

## 5. 多 Agent 并行调度

Planner 先建立模块和 artifact DAG，再决定是否使用多 Agent。默认规则：

```text
ready
AND dependencies accepted
AND write_scope disjoint
AND database/migration resources disjoint
AND lockfile/generated output/test data/port disjoint
=> parallel
```

不同后端模块在无公共 schema/contract 变化时默认并行。共享 migration、同一聚合根、公共 DTO/契约、生成目录、lockfile、数据库测试数据或端口时串行或隔离资源。并行任务只把产物交给周期集成者，不互相靠聊天传话。

最多并行数由实际执行环境决定，不写死在 Base。可用槽位少时按关键路径优先；没有多 Agent 能力时，同一 DAG 也可以由一个 Agent 多线程或顺序执行，工程合同不变。

## 6. Skill 体系

| Skill | 谁调用 | 进入时机 | 硬结果 |
|---|---|---|---|
| `compile-project-delivery` | product/planner/PM | 项目启动、范围变化、阶段滚动 | typed 阶段/需求/用例/任务/周期合同 |
| `build-management-ui` | uiux/frontend | 管理页面编码前 | 页面头、搜索/筛选、批量、表头、右侧标签详情、状态合同 |
| `implement-backend-module` | backend | 后端任务编码前/重构时 | Base 复用、SQL/Repository 边界、复杂度止损和静态审计 |
| `verify-by-delivery-level` | backend/frontend/test | 任何测试执行前 | 当前层级唯一最小测试计划 |

Skill 只保留非显然、可复用、能改变执行质量的规则。高风险且机械的约束放进脚本；详细合同放 references；项目业务事实不得写入 Skill。

计划校验器只判断“允许测什么”，不能代表执行成功。周期 VERIFY 必须使用 `scripts/run-verified-test-plan.ps1`，要求退出码为 0、实际测试数大于 0，并保存命令、计划、源码和日志哈希。Maven 证据必须来自本次 clean 后、目标模块、每个指定测试类新生成的 Surefire XML，`tests - skipped > 0` 且 `failures = errors = 0`；XML 会在下一条命令前固化到 evidence，单条命令默认最多 120 分钟。`failIfNoSpecifiedTests=false` 永久禁止。多模块工程的 focused test 使用 `scripts/run-maven-focused-test.ps1`：它通过共享 Maven 窗口串行保护同一工作区的 `target`，先 clean 安装依赖模块、再只在目标模块执行指定测试，避免并发 clean、旧 class 复用及 `-am -Dtest=...` 在上游模块产生伪失败或跳过。

## 7. 前端基线

功能阶段先实现正确行为和参考桌面视口：

- 标题和紧凑动作同一行，最多一个主按钮。
- 模糊搜索和高级筛选语义分离。
- 批量动作只在选择后出现。
- 表格有真实表头和合理密度，整行进入右侧标签详情，不重复“查看”。
- 无来源的收藏、关注、运营、报表或占位导航默认不存在。
- 仪表盘以拖拽、调整尺寸和预览发布为主，JSON 只作高级交换格式。
- loading/empty/error/permission 状态在功能阶段完成。

完整响应式、断点矩阵、跨模块视觉一致性和可访问性集中在功能完成后的 hardening，不在每个功能 task 重复。

## 8. 后端基线

```text
schema/migration
  -> generated base.entity/base.mapper/base.service
  -> named repository/mapper extensions（仅特殊持久化）
  -> domain rules
  -> manage/application use case
  -> controller/API adapter
```

普通 CRUD 必须先复用 Base。`manage/application/domain/controller` 不允许 SQL、JdbcTemplate、ResultSet 或行映射。业务 Service 只编排一个用例的权限、事务、状态和副作用；如果它因跨多表、动态标识符、重复映射或模糊事务边界而膨胀，先回到 architect/dba 检查聚合、表关系和必要冗余，不继续堆代码。

## 9. 测试分层

| 层级 | 何时 | 只做什么 |
|---|---|---|
| task | 每个短任务 | 受影响单测/组件测试和受影响编译 |
| cycle | 每 4 小时一次 | 一次组合集成、一次真实入口、适用的持久读回和权限正反例 |
| module | 页面/模块完整时 | 页面旅程、API/DB 读回、权限和失败恢复 |
| phase | 阶段结束 | 阶段承诺的完整可用结果和阶段内回归 |
| project | 全部功能结束 | 一次全项目功能、数据、权限、部署和用户旅程验收 |
| performance_optional | 项目功能完成且另行授权 | 独立目标、数据量、并发和预算下的专项测试 |

百万/千万数据、长并发、容量和性能测试不进入核心框架，也不作为普通功能的前置 Gate。

## 10. Base 演进

每个项目关闭阶段时记录候选：现象、根因、项目修复、可复用规则、反例、验证方式。进入 Base 必须同时满足：

1. 已在真实项目执行，不是纯推测。
2. 有失败或收益证据。
3. 去除项目术语、路径和技术偶然性后仍成立。
4. 不扩大默认角色权力或替代用户决策。
5. 有 Base 自测或负例，且能同步到新实例。

Base 发布后通过 manifest 版本和实例 lock 验证公共文件一致性；实例不得人工复制修改公共规则形成双写。
