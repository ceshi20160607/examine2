# MEETING-S3-003：VS2 组织权限集成会审

## 1. 身份

- node_id: `S3-VS2-ORG-PERMISSION`
- organized_by: `pm`
- date: `2026-07-11`
- participants: `product, uiux, architect, dba, backend, frontend, test, ops`
- node_acceptor: `leader`
- status: `completed`

## 2. 会审目标

判断 VS2 是否形成真实的平台/系统组织权限纵向切片，而不是生成 CRUD、权限预览或静态后台；检查允许/拒绝、数据范围、申请审批、上下文失效、跨 scope 拒绝、审计、迁移和桌面/移动旅程。

## 3. 各角色结论

| role | verdict | 证据与结论 | 保留边界 |
|---|---|---|---|
| product | pass | PA1-PA3/S2/S3/C2/C3/C5 已形成可执行入口和业务结果 | 无代码模块属于 VS3 |
| uiux | pass | 桌面管理流程和移动只读/context 边界可用，无水平溢出 | 后续动态表单需重新做移动验收 |
| architect | pass | permission evaluation、epoch、idempotency、audit/outbox 和 scope guard 在服务端闭环 | 后续模块权限继续复用合同 |
| dba | pass | V1 数据升级、空库重放、31 表约束和 155 个 generated base 文件验证通过 | 只接受 V1+V2 schema |
| backend | pass | 平台/系统治理、草稿检查发布、deny 优先、申请批准和 stale context 通过真实 HTTP 集成 | 不覆盖 VS3 配置发布 |
| frontend | pass | 平台/系统后台连接真实 API；无 mock 或未实现入口 | 主依赖 chunk 需后续拆分 |
| test | pass | 后端 5 tests、前端 12 tests、累积浏览器 4 journeys 通过；正反权限与 DB 读回齐全 | Firefox/WebKit 未执行，非当前最低 Gate |
| ops | pass | 持久化 MySQL/Redis 与最终应用健康，Root 重启引导幂等 | release/备份恢复尚未执行 |

## 4. 会审中发现并关闭的问题

| issue | 修正 | verifier | status |
|---|---|---|---|
| closure 表租户列不可写导致 mapper 无法安全插入 | 冻结 `tenant_id`，由生成列提供 `tenant_key`，重新迁移和生成 | dba/backend | CLOSED |
| 自定义 closure writer 不在 mapper 扫描边界 | 删除旁路 writer，使用生成 mapper 和正式实体 | architect/backend | CLOSED |
| 并发首注册竞争初始化内置授权 | MySQL advisory lock 串行一次性初始化 | backend/test | CLOSED |
| filter 级拒绝未进入全局审计 | 委托异常解析器统一记录 DENIED/FAILED | backend/test | CLOSED |
| 角色允许/拒绝互斥 watcher 递归导致空白页 | 仅在集合真实变化时更新 | frontend/test | CLOSED |
| Drawer 未注册、访问审批要求手输 ID | 注册公共 Drawer；加载真实租户/角色选择器 | frontend/uiux | CLOSED |
| Ant 中文按钮可访问名称含字距导致 E2E 假超时 | 使用可访问角色和容许字距的稳定 locator | frontend/test | CLOSED |
| E2E 源码被浏览器 tsconfig 错误检查 | 分离应用 DOM 与 Node/Playwright 类型边界 | frontend/test | CLOSED |

## 5. PM 集成决定

1. VS2-001..009 均完成，无开放 P0/P1 或用户待决项。
2. 主 chunk 体积登记为后续性能观察项，不阻断 VS2，但 VS3 需制定公共依赖拆包动作。
3. 提交 `evidence/vs2/acceptance.md` 给 Leader 做 batch Gate。
4. 下一节点只允许规划 VS3 模块/字段/字典/页面/动作/规则的配置发布，不提前创建 VS4 动态业务假入口。

## 6. Leader 决定

- decision: `PASS`
- authority: `leader node-accept`
- developmentBatchAccepted: `true`
- unresolved: `none`
- user_decision_needed: `no`
- next_node: `S3-VS3-CONFIG-PUBLISH`
- boundary: 仅接受 VS2；完整系统、release 和用户最终验收均未完成。
