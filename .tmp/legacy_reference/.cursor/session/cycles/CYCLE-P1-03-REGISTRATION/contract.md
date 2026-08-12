# CYCLE-P1-03-REGISTRATION 冻结执行合同

## 周期结果

未登录用户在 `/register` 只填写用户名、显示名称、密码、系统名称、系统编码五项内容。一次成功提交必须在同一事务中创建账号、Argon2id 凭据、首系统、唯一默认租户、所有者成员、租户成员关系、全系统数据范围、系统超级管理员角色、两项 P1 shell 权限、角色关系、SYSTEM 会话、刷新令牌、安全审计和已完成幂等记录；事务提交后才允许写 Cookie。用户随后直接进入 `/systems/{firstSystemId}/admin/onboarding`，刷新页面仍能读回同一系统、租户、成员和超级管理员上下文。

本周期最长 240 分钟。后端注册与前端注册写集隔离，可并行执行；`TASK-P1-03-VERIFY` 只能在两项实现任务都有非零通过证据后启动。功能与边界正确优先，本周期不做全量回归、响应式统一、性能、容量或百万/千万数据测试。

## 已纠正的开发前边界

`RegisterFirstSystem` 除 13 个 plat vNext 生成 IService 外，确实还需要 core 生成的 `IIdempotencyService` 和 `ISecurityService`。这两个依赖已经补入 accepted backend boundary；schema、migration 和生成 Base 无缺口，不允许借此新增 Mapper、JDBC 或 SQL。

禁止复用 legacy `RegistrationService`、`IdempotencyFacade/IdempotencyService`、旧注册页、旧 SystemOnboarding 和旧 SystemAdminLayout。它们分别包含直接 Mapper/JDBC、平台成员语义、二次系统切换和 P1 以后导航，不能作为本周期实现基础。

## HTTP 合同

### POST `/api/v1/auth/register`

- auth：anonymous；`VNextAuthenticationFilter` 明确 bypass。
- header：`Idempotency-Key` 必填，必须是 canonical UUID；缺失或非法返回 HTTP 400 + `REGISTER_INVALID`。
- body：
  - `username`: `^[A-Za-z][A-Za-z0-9_]{2,31}$`
  - `displayName`: trim 后 1..120
  - `password`: 10..200
  - `systemName`: trim 后 1..160
  - `systemCode`: `^[a-z][a-z0-9_]{2,31}$`
- normalization：username 做 NFKC、trim 并以小写写入 `username_normalized`；systemCode 保持小写；显示名称和系统名称 trim。
- success：HTTP 200 + `OK` + SYSTEM `AccountContext`；只在事务提交成功后写 access/refresh HttpOnly SameSite=Lax Cookie 和非 HttpOnly CSRF Cookie。
- conflict：重复 username 返回 HTTP 409 + `REGISTER_USERNAME_CONFLICT`；重复 systemCode 返回 HTTP 409 + `REGISTER_SYSTEM_CODE_CONFLICT`。
- same key/different payload：HTTP 409 + `REGISTER_INVALID`，不创建任何新图或会话。
- unexpected persistence failure：HTTP 500 + `REGISTER_FAILED`，不写 Cookie，不遗留业务图或 PROCESSING 幂等行。
- 响应、日志、SQL、evidence 和 idempotency responseBody 均不得出现明文密码或原始 access/refresh/CSRF token。

成功 `AccountContext` 必须精确满足：

- `account` 是新账号；`firstSystemId` 等于新系统 ID。
- `context.type=SYSTEM`，system/tenant/member 的 ID 与名称全部存在且属于同一系统。
- `permissionVersion=1`，permissions 精确为 `system.runtime.access`、`system.admin.access`。
- shells 精确为 `SYSTEM_RUNTIME`、`SYSTEM_ADMIN`。
- systems 只含新系统，状态 ACTIVE、成员状态 ACTIVE、角色名为“系统超级管理员”、defaultTenantId 为新默认租户。
- tenants 只含新默认租户，code=`default`、status=ACTIVE、isDefault=true。

## TX-P1-REGISTER-FIRST-SYSTEM

一个 registration-local transactional worker 只能通过以下 15 个生成 IService 写数据：

- core：`IIdempotencyService`、`ISecurityService`。
- plat：`IVNextPlatAccountService`、`IVNextPlatCredentialService`、`IVNextPlatSystemService`、`IVNextPlatTenantService`、`IVNextPlatMemberService`、`IVNextPlatMemberTenantService`、`IVNextPlatDataScopeService`、`IVNextPlatRoleService`、`IVNextPlatPermissionService`、`IVNextPlatRolePermissionService`、`IVNextPlatMemberRoleService`、`IVNextPlatContextSessionService`、`IVNextPlatRefreshTokenService`。

事务顺序固定如下；任一 save/update 返回 false 或抛错时全部回滚：

1. 以 `scopeType=REGISTER_FIRST_SYSTEM`、`scopeKey=GLOBAL`、UUID key 创建 PROCESSING idempotency，TTL 24h。requestHash 输入固定为 UTF-8 canonical JSON，属性顺序精确为 `version=p1-register-v1`、`usernameNormalized`、`displayName`、`systemName`、`systemCode`，使用标准 JSON escaping 后做 SHA-256；禁止用未转义分隔符拼接。密码是否相同必须通过 receipt 中 accountId 对应的 Argon2id credential 调用 `PasswordService.matches` 判断，禁止持久化密码的快速摘要。
2. 通过 IService 检查 `username_normalized` 和 `system_code`；数据库唯一键仍作最终裁决。
3. 创建 ACTIVE Account：`accountCode=ACC_{id}`、locale=`zh-CN`、timeZone=`Asia/Shanghai`、lastLoginAt=now；创建 PASSWORD Credential，算法必须为 ARGON2ID，passwordChangedAt=now、failedAttempts=0、lockedUntil=null。
4. 创建 System：status 先为 INITIALIZING，tenantMode=SINGLE，ownerAccountId=新账号，permissionVersion=1。
5. 创建唯一默认 Tenant：code=`default`、name=`默认租户`、isDefault=true、status=ACTIVE；generated `active_default_system_id` 不得由业务赋值。
6. 创建 ACTIVE owner Member：`memberCode=OWNER_{memberId}`、displayName=注册 displayName、defaultTenantId=默认租户；创建 ACTIVE MemberTenant，expiresAt=null。
7. 创建内置全系统 DataScope：scopeType=SYSTEM、scopeKey/systemId=新系统、tenantId=null、scopeCode=`system_owner_all`、name=`全系统数据`、scopeKind=ALL、isBuiltin=true、status=ACTIVE。
8. 创建内置 Role：scopeType=SYSTEM、scopeKey/systemId=新系统、tenantId=null、roleCode=`system_owner`、name=`系统超级管理员`、roleType=ROOT、permissionVersion=1、publishedVersion=1、dataScopeId=上述范围、isBuiltin=true、status=ACTIVE。
9. 只创建两项 SYSTEM/SHELL Permission：`system.runtime.access`/`进入系统工作台`、`system.admin.access`/`进入系统管理`，resourceType=SHELL、status=ACTIVE；分别创建 ALLOW RolePermission。不得提前灌入模块、组织、租户管理、SSO、报表、AI 等后续权限。
10. 创建 MemberRole：system/member/role 一致，tenantId=null、validFrom=now、validUntil=null。不得因为 nullable unique key 而宣称数据库已提供通用防重；本周期只有一次不可达重复的首图创建。
11. 创建 SYSTEM ContextSession：account/system/defaultTenant/member 同 scope；permissionVersion=authzEpoch=1；权限、角色、数据范围使用结构化 JSON 快照；access token 只保存 SHA-256。
12. 创建 ACTIVE RefreshToken：同 context、随机 family UUID；refresh token 只保存 SHA-256。
13. 通过 `ISecurityService` 创建 `REGISTER_FIRST_SYSTEM/SUCCESS` 安全审计，包含 account/system/tenant 与 request/trace，P1 operation-audit no-op 边界不得吞掉它。
14. 把 System 改为 ACTIVE、initializedAt=now，并检查 update 成功；仍在同一事务中，失败时前述全部写入回滚。
15. 基于 ACTIVE System 生成最终 AccountContext，将稳定 receipt/AccountContext 写入 idempotency responseBody，状态改为 COMPLETED、HTTP 200、code OK；receipt 只能含稳定 ID 和非秘密上下文。提交后 controller 才写 Cookie。

所有 `createdAt/updatedAt/joinedAt/grantedAt/validFrom/issuedAt` 使用事务开始时捕获的同一个 `now`；所有可写的 `createdBy/updatedBy/grantedBy` 使用新 accountId，不能依赖数据库隐式默认或填充器。

本事务不创建 `un_plat_account_role` 或 `un_plat_authz_epoch`。注册账号没有平台角色；初始 context 的 permissionVersion/authzEpoch 直接取本次系统/owner role 版本 1。

## 幂等与并发不变量

1. 同 key 竞争依赖数据库唯一键，不能通过“先查再插”假装并发安全。唯一冲突发生后，失败的 create 事务必须完整回滚；外层非事务 coordinator 再开启新读快照通过 `IIdempotencyService` 读取 winner。
2. 同 key、同四个非秘密字段且密码通过 Argon2id credential 验证时，不重复创建 account/system/tenant/member/role 图。replay 必须先通过 IService 重新读回 account/system/default tenant/member/member-tenant/owner role 与两项 permission；只有全部仍为 ACTIVE 且 scope/绑定/权限仍满足本合同原始不变量时，才从 COMPLETED receipt 恢复同一业务图、撤销 receipt 当前指向的注册 SYSTEM session family并签发一套新的 hashed session/cookies。任何状态、绑定或权限已变化时返回 HTTP 409 + `REGISTER_INVALID`，不得用旧快照恢复 SYSTEM_RUNTIME/SYSTEM_ADMIN。replay 必须在同一事务把 idempotency responseBody 中的 contextSessionId/refreshTokenId 指针更新为新会话，确保第三次及以后重放不会遗漏上一套 replay session。由于原始 token 禁止落库，绝不能伪称可以重发原 Cookie。
3. 同 key但非秘密字段或密码不同，返回 `REGISTER_INVALID`，不得产生新会话。
4. 非 idempotency 唯一冲突同样先让事务回滚，再通过 IService 读回 username/systemCode 映射合同错误；禁止解析数据库异常字符串。
5. 强制失败只允许由 test profile 的 registration-local failure probe 注入，不能通过生产 HTTP header/query 参数开启。失败后所有图、会话、安全审计和幂等行均不存在，因此原 key 可以安全重试。

## TASK-P1-03-REGISTER-BACKEND

- owner：`backend-registration`
- verifier：`leader`
- cases：`CASE-P1-REGISTER-FIRST-SYSTEM`、`CASE-P1-REGISTER-CONFLICT`、`CASE-P1-REGISTER-ROLLBACK`
- writes：
  - `backend/examine-plat/src/main/java/com/unique/examine/plat/vnext/manage/registration/**`
  - system-aware context 所需的最小 auth 扩展：`VNextSessionService.java`、`VNextSystemSessionService.java`、`VNextSecurityAuditService.java`、新 `SystemSummary.java`、新 `TenantSummary.java`；其中通用 SYSTEM issue/revoke 不得依赖 registration 类型或 failure probe，供 P1-04 直接复用
  - `backend/examine-web/src/main/java/com/unique/examine/web/vnext/registration/**`
  - `backend/examine-web/src/main/java/com/unique/examine/web/vnext/auth/VNextAuthenticationFilter.java`
  - `backend/examine-web/src/test/java/com/unique/examine/web/P1RegistrationUseCaseContractTest.java`
- forbidden：修改 migration/generated Base/legacy Registration；直接依赖 Mapper、JDBC、`java.sql`、内联 SQL；创建平台角色、authz epoch、其他系统或后续功能。
- required check：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .cursor/scripts/run-maven-focused-test.ps1 -Module examine-web -Tests P1RegistrationUseCaseContractTest
```

focused test 必须锁定：15 IService 边界、事务顺序、Argon2id、两项权限、SYSTEM context 投影、Cookie 提交边界、同 key replay/different payload、两个唯一冲突、强制失败回滚、无明文秘密。

## TASK-P1-03-REGISTER-UI

- owner：`frontend-registration`
- verifier：`leader`
- writes：
  - `frontend/src/vnext/register/**`
  - `frontend/src/vnext/onboarding/**`
  - `frontend/src/vnext/auth/LoginView.vue` 及必要的同目录样式
  - `frontend/src/router/index.ts`
  - 注册需要的 `frontend/src/stores/session.ts`、`frontend/src/types/session.ts`、`frontend/src/services/api.ts`
  - `frontend/tests/unit/p1-registration-foundation.spec.ts`
  - `frontend/tests/unit/p1-login-foundation.spec.ts` 仅允许把 P1-02 的阶段性“注册入口不存在”断言更新为当前已交付路由；其余登录断言不得重写
- page：五字段、一个主动作“创建账号并创建系统”、一个次动作“返回登录”；无卡片墙、无内部 ID、requestId、traceId、JSON 或调试语言。
- states：`idle`、`validation_error`、`submitting`、`username_conflict`、`system_code_conflict`、`rollback_failure`；提交中禁用并防双击。
- errors：业务 code 优先；username/systemCode 冲突锚到对应字段；`REGISTER_INVALID` 消费 `errors[].path`；`REGISTER_FAILED` 显示“创建未完成，可重新输入密码后安全重试”。HTTP 409/500 只作兜底。
- retention：失败保留 username/displayName/systemName/systemCode，始终清空 password；不显示后端原始实现文案。
- idempotency：一次点击只生成一个 UUID。服务端已明确返回失败后的再次点击是新 submission/新 key；只有网络结果未知且自动重试原 payload 时复用原 key。
- success：直接 `applyAuth`，校验 firstSystemId、SYSTEM context、相同 systemId 与 SYSTEM_ADMIN shell 后导航 onboarding；不得调用 `/context/systems`、`:switch` 或 tenant API。
- routes：运行态只新增 `/register` 和精确 `/systems/:systemId/admin/onboarding`。`/register` 是 guestOnly；已认证 PLATFORM 回 My Systems，已认证 SYSTEM 回当前 onboarding。onboarding 未登录去 login；错误 systemId 或缺 SYSTEM_ADMIN 必须显式 permission denied，禁止根据 URL 自动 switch。根与 fallback 对已认证 context 做相同定向。
- onboarding：只显示当前系统、当前账号、创建完成说明、唯一“配置引导”导航和退出；不显示模块、组织、角色、租户、SSO、运营、报表、收藏、关注、AI、创建另一系统或占位入口。mount 不发额外业务请求；刷新只走 existing current→可选一次 refresh bootstrap。
- required check：

```text
npm.cmd --prefix frontend test -- p1-registration-foundation.spec.ts
```

unit 必须覆盖字段边界、防双击、UUID header、错误映射、非秘密字段保留/密码清空、malformed success、不调用系统列表/切换、context-aware 路由、onboarding 五态、logout 和未来入口缺失。

## TASK-P1-03-VERIFY

- owner：`test`
- verifier：`leader`
- dependencies：后端和前端任务均有真实非零通过证据。
- writes：
  - `backend/examine-web/src/test/java/com/unique/examine/web/P1RegistrationCycleIntegrationTest.java`
  - `frontend/tests/e2e/p1-registration-cycle.spec.ts`
  - `.cursor/session/evidence/baseline/CYCLE-P1-03-REGISTRATION/**`
- forbidden：验证阶段修改功能、复用历史 evidence、执行全量回归/性能/容量测试。
- required check：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .cursor/scripts/run-verified-test-plan.ps1 -Plan .cursor/session/baseline/test-plans/cycle-p1-03-registration.json -EvidenceRoot .cursor/session/evidence/baseline/CYCLE-P1-03-REGISTRATION
```

真实后端 integration 必须覆盖首次成功、current/refresh readback、同 key replay、同 key不同 payload、并发同 key、username/systemCode 冲突、强制中途失败全表回滚、唯一默认租户、完整 scope、数据库/日志/evidence 无明文秘密。浏览器只用确定性内存 API 模拟可见流程：先展示一次失败保留，再成功进入 onboarding、reload、logout，并断言零次 systems list/switch 和无旧导航。

## 周期 Gate

只有下列全部成立才可把本周期记为通过：

1. 两项 task required check 的 executed test count 大于 0，skipped/failures/errors 均为 0。
2. plat/web vNext 静态审计 0 errors、0 warnings。
3. frozen cycle plan 实际执行两个 test entry，manifest 为 pass；计划校验、编译成功或历史 evidence 不能冒充周期通过。
4. 注册图、幂等、冲突、回滚、SYSTEM_ADMIN readback 与浏览器真实入口都能从同一周期 evidence 读回。
5. 页面和运行态路由没有暴露任何 P1 以后入口。
