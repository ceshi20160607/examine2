# MEETING-S2-001：产品、UI 与工程设计集成会审

## 1. 身份

- node_id: `S2-DESIGN-PACKAGE`
- organized_by: `pm`
- date: `2026-07-10`
- participants: `product, uiux, architect, dba, backend, frontend, test, ops`
- node_acceptor: `leader`
- status: `completed`
- reviewed_output: `.cursor/session/rebuild/design-package.md` version `0.9`

## 2. 会审目标

判断设计包是否已经把已验收需求转成足以启动第一个数据库先行纵向切片的合同。重点检查四套壳、模块/data ownership、动态数据、配置发布、认证权限、API、生成边界、事件/作业、测试、发布和失败恢复是否一致，以及下游是否仍需猜测关键前提。

本会议不声称任何业务功能已经实现，也不设置最终用户验收状态。

## 3. 各角色独立意见

| role | verdict | 主要检查与结论 | 约束/证据 |
|---|---|---|---|
| product | pass | 15 个最终范围域均保留，VS1-VS12 只是实施顺序；四套壳、12 类角色入口和完整用户结果没有被压缩成 MVP/CRUD | 需求覆盖区包含全部 57 条正式 REQ 和 52 条 JRN；最终仍需用户整体验收 |
| uiux | pass | 企业工作台方向、四套壳、六类页面模式、列表整行详情、配置状态、移动替代布局、错误/空/禁用状态已可执行；旧原型仅作覆盖参考 | 实现按真实 API/权限/读回重做；Playwright 检查 1440x900、1280x720、390x844 |
| architect | pass | 模块化单体、owner write、公开 facade、outbox、Redis job、provider SPI 和 web/core 边界没有循环责任；关键失败模式已补齐 | `examine-web` 无业务；业务模块不依赖 web/generator；跨模块不引用 mapper/entity，由 ArchUnit 验证 |
| dba | pass | 110 个命名核心表覆盖身份、配置、动态值/索引、Flow、文件、OpenAPI、工作、待办、消息、AI、统计和审计；隔离、版本、迁移、索引和生成边界明确 | 平台/系统共用表已补 `scope_type` 约束；实际 DDL 按切片冻结、Flyway 执行和 generator 读回 |
| backend | pass | Java 21 + Boot 3.5.16 + Undertow + MyBatis-Plus 3.5.16 兼容路线明确；事务、幂等、权限、API、状态、副作用和 readback 可落地 | 默认环境旧 JDK 路径已发现并修正；VS1 API、表和负例已冻结 |
| frontend | pass | Vue/Ant Design Vue/TanStack/Vue Flow/ECharts 责任分明；route/store/context 刷新、schema renderer、表格 URL 状态、PWA 敏感缓存边界明确 | lockfile 固定依赖；不引入 AG Grid；移动端不复制第二套业务模型 |
| test | pass | 静态、unit、MySQL/Redis 集成、API、component、52 旅程 E2E、视觉、可访问、安全性能和 release evidence 分层完整 | mock 不能作为旅程验收；每切片必须允许/拒绝、跨 context、持久化读回和副作用证据 |
| ops | pass | dev/test/prod、外置配置、制品目录、preflight/启停/健康/备份恢复/升级回滚和指标均已进入设计 | 本机 JDK 21/Maven/Node 已实际验证；生产 RPO/RTO 在上线前按业务确认并演练 |

## 4. 分歧与取舍

| topic | 采用 | 未采用 | 决策理由 |
|---|---|---|---|
| Spring Boot | `3.5.16` | Boot 4.1 | 需求明确 Undertow；Boot 4 官方只保留 Tomcat/Jetty，3.5.16 官方仍支持 Undertow |
| 工程形态 | Maven 模块化单体 | 当前即拆微服务 | 单进程更适合当前事务、部署和团队规模；模块 owner/facade/outbox 保留未来拆分边界 |
| 动态数据 | record + typed value + index + relation/subtable | 整记录 JSON/EAV 全表扫描 | 满足字段扩展且能做筛选索引、唯一约束、历史解释和迁移 |
| 配置版本 | 规范化 draft + 不可变发布 snapshot | 直接修改运行配置 | 草稿不污染运行；实例/打印/流程可解释；回滚不篡改历史 |
| 高级表格 | TanStack Table v8 + 自有 Ant Design Vue 渲染 | AG Grid | 保留企业功能和视觉控制，避免不必要许可/组件体系绑定 |
| 后台作业 | DB durable job + Redis hot state/stream/lock | MQ/Quartz、只在进程内、只在 Redis 留事实 | 满足需求指定 Redis 且可在 Redis/进程故障后恢复、归档和审计 |
| 跨模块副作用 | transaction outbox + 幂等 handler | owner 直接跨模块写表 | 避免跨模块事务耦合并保证 Todo/Message/统计等副作用可补偿 |
| 移动端 | 同一 Vue 应用的响应式 PWA 使用端 | 原生 App、完整移动配置台 | 复用发布 schema/API，覆盖移动使用而不制造双实现 |

## 5. 会审中发现并关闭的问题

| issue_id | 发现 | 修正 | verifier | status |
|---|---|---|---|---|
| S2-DESIGN-001 | 平台/系统共用组织和角色表的作用域字段不够显式 | 数据通用约束增加 `scope_type=PLATFORM|SYSTEM`、system/tenant 组合约束 | dba + architect | CLOSED |
| S2-DESIGN-002 | 主设计缺少集中失败/并发/恢复矩阵 | 增加注册、切换、发布、记录、审批、job、文件、OpenAPI、AI 和部署失败模式 | backend + test + ops | CLOSED |
| S2-DESIGN-003 | PWA 缓存可能污染退出或系统切换后的数据 | 明确 service worker 不缓存认证/业务/文件/敏感响应，并在退出/context 切换清用户缓存 | frontend + security review | CLOSED |
| S2-ENV-001 | `docs/user_setting.md` 的 Node/JDK/Maven 路径与当前机器不一致，命令落到 JDK 8 | 只修正非敏感工具路径到 `D:\dev`，实际验证 Java 21.0.10、Maven 3.8.5、Node 24.14.0、npm 11.9.0 | backend + ops | CLOSED |

## 6. 自动一致性证据

- 正式需求定义：57；设计缺失：0。
- 角色旅程定义：52；设计缺失：0。
- 非功能要求定义：8；设计缺失：0。
- 工程决定 `ENG-001` 至 `ENG-007`：7/7 closed。
- 命名核心表：110；均属于声明的 10 个表前缀域。
- 当前无必须由用户回答才能启动 VS1 的待决项。

## 7. PM 集成决定

1. 接受设计包的产品、UI、架构、数据、API、安全、任务、测试和发布合同。
2. 接受 VS1 为首个开发节点：注册首个系统、登录/退出、系统切换、权限快照、系统壳和审计。
3. VS1 开始前由 Planner 把冻结合同拆成 schema、generator、backend、frontend、test、evidence 任务；不得把后续功能做成假入口。
4. 允许 DDL/代码只代表 `implementationReady`，不代表后续切片或最终系统完成。
5. 当前无开放 P0/P1 设计问题，无需升级用户；提交 Leader 做 S2 Gate 验收。

## 8. 行动与复核

| action | owner | output | verifier | status |
|---|---|---|---|---|
| 补齐 scope/PWA/失败模式合同 | architect | `design-package.md` | pm | DONE |
| 修正并验证非敏感工具链路径 | ops + backend | `docs/user_setting.md` + toolchain output | pm | DONE |
| 执行需求/旅程/NFR/ENG 一致性检查 | test | 本会议第 6 节 | pm | DONE |
| 更新设计 review 和 Gate | architect + pm | `design-package.md` | leader | READY |
| Leader 决定是否开放 VS1 | leader | `state.json` + `current-node.md` | framework evidence | PENDING |

## 9. 升级

- unresolved: `none`
- leader_decision_needed: `yes`
- user_decision_needed: `no`
- pending_decision_path: `.cursor/session/pending-user-decisions.md`（当前无活动项）
