# VS2 后端验证证据

- checked_at: `2026-07-11T10:39:26+08:00`
- verdict: `pass`
- command: `mvn.cmd test`

## 结果

- Maven 四模块 reactor `BUILD SUCCESS`。
- `OperationAuditTest`、`PasswordServiceTest`、`SessionGuardTest`、`Vs1JourneyIntegrationTest`、`Vs2JourneyIntegrationTest` 共 `5` tests，`0` failure/error/skipped。
- 集成环境使用真实 Undertow、Testcontainers MySQL `8.0.44`、Redis `7.4` 和 Flyway `V1+V2`，不是 mock controller 或内存数据库。
- 持久化验收环境 MySQL `8.4`、Redis `7.4` 持续运行，最终应用 `/management/health` 为 `UP`。

## VS2 覆盖行为

- 平台 Root 引导幂等；普通注册账号不能进入平台后台；最后一个有效 Root 不可被停用或解除。
- 平台系统、部门、账号、角色和权限草稿的创建、更新、检查、发布与幂等重放。
- 系统设置、租户、部门、成员、角色、权限、数据范围和有效权限解释。
- 允许与显式拒绝同时建模且互斥，最终权限计算验证 `DENY` 优先并返回来源角色、数据范围和 epoch。
- 访问申请提交、本人查询、管理员批准、重复批准幂等，以及批准后系统/租户上下文切换。
- 授权、成员、租户或角色发布改变 epoch 后，旧 context 返回 `AUTHZ_SNAPSHOT_STALE`。
- 跨系统、跨租户、普通成员管理请求返回真实 `403/404/409` 合同；拒绝和失败进入 operation audit。
- 数据库读回 member-tenant、role binding、role permission effect、authz version/epoch、outbox、audit 和 context snapshot。

## 回归中关闭的问题

- 并发首个注册可能同时初始化内置权限：使用 MySQL advisory lock 串行化一次性初始化，桌面/移动并发注册回归通过。
- 部门 closure 原生成模型缺少可写租户键：V2 模式改为 `tenant_id` 加生成 `tenant_key`，删除后重新生成 base。
- 注册初始平台成员误提升全局 epoch：初始绑定不再使新 session 立即过期。
- filter 直接写错误响应绕过全局审计：认证和 CSRF 异常统一交给异常解析器并记录 DENIED/FAILED。
