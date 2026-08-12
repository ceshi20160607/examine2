# CYCLE-P1-04-SYSTEM-ENTRY 冻结执行合同

## 周期结果

已登录账号进入 `/platform/systems` 时，只看到自己当前拥有 ACTIVE 成员关系的系统。页面以紧凑列表显示系统、状态、角色和最近进入时间；选择系统后，后端先重新解析成员、默认租户、角色、数据范围和权限，再原子替换当前会话并返回完整 SYSTEM `AccountContext`。ACTIVE 系统进入配置引导；DISABLED 系统只有拥有 `system.admin.access` 的管理员可进入 `ADMIN_SETTINGS_ONLY`；无成员、成员停用、普通成员进入停用系统都得到明确拒绝且不产生新会话。

本周期最长 240 分钟。后端 system-entry 与前端 system shell 写集隔离，允许并行；`TASK-P1-04-VERIFY` 只能在两项实现任务都有非零通过证据后启动。功能和边界正确优先，本周期不做全量回归、响应式统一、性能、容量或百万/千万数据测试。

## 开发前边界结论

现有 17 张 P1 表和生成 Base 足够实现本周期，不新增 migration、表、列、Mapper 或业务 SQL。manage 只编排生成 IService；controller 只做 HTTP、请求上下文和事务提交后的 Cookie 写入。

P1 API 统一使用已经交付的 wire 字段 `memberId`，不再同时制造 `systemMemberId` 别名。SYSTEM `SessionContext` 在原字段上新增：

- `roleIds`: 按 ID 升序的非空字符串数组。
- `dataScope`: `DataScopeContext`，精确字段为 `id`、`code`、`kind`；PLATFORM context 为 null。
- `restrictedMode`: 只允许 `NONE` 或 `ADMIN_SETTINGS_ONLY`；PLATFORM 和正常 ACTIVE SYSTEM 均为 `NONE`。

`AccountContext.systems`、`tenants` 和 `firstSystemId` 始终存在；`firstSystemId` 类型固定为 `string|null`。登录/列表阶段的 PLATFORM context 使用空 tenants 与 null firstSystemId；注册成功使用新系统 ID；普通 switch 成功使用 null，禁止以 optional 字段掩盖不完整响应。

ContextSession 现有 `role_snapshot_json` 与 `data_scope_snapshot_json` 足以持久化这些信息，不需要 schema 变更。角色快照项精确为 `{id,code,name,publishedVersion}`；数据范围快照仍为数组，每项精确为 `{roleId,id,code,kind,restrictedMode}`。P1-03 已存在且没有 `restrictedMode` 的旧格式按 `NONE` 兼容读取；P1-04 新签发的 SYSTEM context 必须显式写入该字段。current 和 refresh 必须从同一持久化快照恢复完全相同的 roleIds、dataScope、permissionVersion、permissions 和 restrictedMode。

`VNextSystemSessionService` 是注册与系统进入共用的 SYSTEM session issuer/revoker。本周期允许把它从硬编码版本 1 扩成通用 snapshot 输入，但不得依赖 systementry 或 registration 类型。注册调用继续产生原有 P1-03 结果；system-entry 直接只依赖 accepted boundary 中的 IService，账号读取可由既有 auth/session 投影内部完成，不能为业务 use case 新增直接 Account Mapper/SQL。

禁止复用 legacy `SessionService`、旧 SystemLayout/SystemAdminLayout、PlatformWorkbench、NoMemberView 或旧 router。P3 runtime-module-list 的列表搜索、高级筛选、导入导出、批量、详情抽屉、关联、评论、文件、历史、收藏、关注、运营、报表、Flow、AI 等全部继续冻结。

## HTTP 与 wire 合同

### GET `/api/v1/context/systems`

- auth：任意有效 PLATFORM 或 SYSTEM context session；缺失/失效返回 HTTP 401 + `AUTH_SESSION_REQUIRED`。
- success：HTTP 200 + `OK` + `AuthorizedSystem[]`。
- 每项精确为 `id/code/name/status/memberStatus/roleNames/recentEnteredAt/defaultTenantId`。
- 只返回 `deleted_at IS NULL` 且状态为 ACTIVE 或 DISABLED，并且当前 account 在该 system 有唯一 `deleted_at IS NULL AND status=ACTIVE` Member 的系统；无成员、PENDING/DISABLED 成员、已删除/归档/初始化中系统全部不返回。
- roleNames 只来自当前时刻有效的 MemberRole 和 ACTIVE、未删除、已发布 SYSTEM Role，按 role name 后 role id 去重排序；列表不通过角色名猜权限。
- defaultTenantId 来自 Member.defaultTenantId；列表阶段允许它为空或暂时不可用，switch 时再做完整租户关系验证。
- recentEnteredAt 是该 account/system 所有 SYSTEM ContextSession 的最大 `last_seen_at`，无历史为 null。
- 排序固定为 recentEnteredAt 非空优先且降序，其余按 system name、code、id 升序，保证相同数据返回稳定。
- 只读用例精确依赖 `IVNextPlatMemberService`、`IVNextPlatSystemService`、`IVNextPlatMemberRoleService`、`IVNextPlatRoleService`、`IVNextPlatContextSessionService`。

### POST `/api/v1/context/systems/{systemId}:switch`

- auth：有效 context session；CSRF 必须通过。缺失、已失效或在并发 switch 中已经被另一请求原子替换的来源 session 返回 HTTP 401 + `AUTH_SESSION_REQUIRED`。`systemId` 必须是正整数，不合法按不存在处理。
- success：HTTP 200 + `OK` + SYSTEM `AccountContext`；controller 只在事务提交后写新的 access/refresh HttpOnly SameSite=Lax Cookie 和非 HttpOnly CSRF Cookie。
- `SYSTEM_NOT_FOUND`: HTTP 404；系统不存在、已删除或 ARCHIVED。
- `SYSTEM_MEMBER_REQUIRED`: HTTP 403；当前账号无 Member，或授权角色/数据范围图不完整。
- `SYSTEM_MEMBER_DISABLED`: HTTP 403；Member 不是 ACTIVE。
- `SYSTEM_DISABLED`: HTTP 403；DISABLED 系统的普通成员，或 INITIALIZING/INIT_FAILED 等不可进入状态。
- `SYSTEM_NO_TENANT`: HTTP 403；默认租户不存在/不 ACTIVE/不属于系统，或缺少同 scope 的 ACTIVE、未过期 MemberTenant。
- 所有失败均不写 Cookie、不撤销原会话、不创建 ContextSession/RefreshToken；业务 code 优先于 HTTP fallback。

成功 `AccountContext` 必须满足：

- `context.type=SYSTEM`；systemId/systemName、tenantId/tenantName、memberId 与数据库关系同 scope。
- roleIds 精确为本次解析的有效角色 ID；dataScope 精确为共同有效数据范围；permissionVersion 为 system.permissionVersion 与有效角色 permissionVersion 的最大值；authzEpoch 使用同值持久化。
- permissions 来自 ACTIVE Permission 和 RolePermission：同一权限任何 DENY 优先于所有 ALLOW；只返回最终 ALLOW 集合。
- role snapshot、data-scope snapshot、permissions 和版本均写入新 ContextSession；原始 access/refresh/CSRF token 不进入响应 body、日志或数据库明文。
- systems 只投影本次系统，tenants 只投影本次默认租户，firstSystemId 为 null。

## 授权解析与事务不变量

switch 在一个 `@Transactional` manage worker 中按下列顺序执行，任一检查或 IService 写失败都回滚：

1. 从已认证 RequestSession 取得来源 `sessionId/accountId`，不得信任客户端 account/member/tenant/role 参数。
2. 读取 system；不存在、deleted、ARCHIVED 返回 `SYSTEM_NOT_FOUND`。
3. 读取唯一 system/account Member；不存在返回 `SYSTEM_MEMBER_REQUIRED`，非 ACTIVE 返回 `SYSTEM_MEMBER_DISABLED`。
4. 以 Member.defaultTenantId 读取同 system ACTIVE 默认租户，并验证 ACTIVE、未删除、未过期 MemberTenant；失败返回 `SYSTEM_NO_TENANT`。
5. 读取当前时刻有效的 MemberRole：`validFrom<=now` 且 `validUntil IS NULL OR validUntil>now`，tenantId 为 null 或等于默认 tenant；再读取同 system/scope 的 ACTIVE、未删除、publishedVersion>0 Role。
6. 所有有效角色必须引用同一个 ACTIVE、未删除、同 system 的 DataScope；P1 不做多范围合并。没有有效角色、没有共同数据范围或 scope 不一致返回 `SYSTEM_MEMBER_REQUIRED`。
7. 读取同 SYSTEM scope 的 RolePermission 与 ACTIVE Permission，计算 DENY 优先的权限集合；roleIds 和角色快照稳定排序，permissionVersion 取 system 与角色版本最大值。
8. system=ACTIVE 时必须含 `system.runtime.access`，否则返回 HTTP 403 + `SYSTEM_MEMBER_REQUIRED`；成功时 restrictedMode=`NONE`。system=DISABLED 时只有最终权限含 `system.admin.access` 才允许，写入 session 的 permissions 必须过滤为精确 `{system.admin.access}`，restrictedMode=`ADMIN_SETTINGS_ONLY`；其他状态或普通成员返回 `SYSTEM_DISABLED`。
9. 通过通用 `VNextSystemSessionService` 创建新 hashed ContextSession/RefreshToken，写入完整 snapshots。
10. 在同一事务以 `sourceContextSessionId + status=ACTIVE` 条件更新来源 ContextSession 为 REVOKED，并要求恰好成功一次；只有该 CAS 成功后，才按 sourceContextSessionId 撤销其全部 refresh token。若来源已经被另一请求领取、ContextSession 更新失败或 refresh 撤销失败，本事务新建的 session/refresh 全部回滚。并发两个 switch 最多一个提交，失败者不得留下第二套 ACTIVE 新会话。普通重复 switch 会重新解析权限、签发一套新 session 并撤销上一套，不创建或修改 membership/role/permission 图。
11. 基于新持久化 context 生成 AccountContext 并返回；事务完成后 controller 才写 Cookie。

switch use case 精确依赖：`IVNextPlatSystemService`、`IVNextPlatTenantService`、`IVNextPlatMemberService`、`IVNextPlatMemberTenantService`、`IVNextPlatMemberRoleService`、`IVNextPlatRoleService`、`IVNextPlatDataScopeService`、`IVNextPlatRolePermissionService`、`IVNextPlatPermissionService`、`IVNextPlatContextSessionService`、`IVNextPlatRefreshTokenService`。禁止 BaseMapper、JDBC、`java.sql`、内联 SQL 或新增 Base 业务方法。

## TASK-P1-04-SYSTEM-ENTRY-BACKEND

- owner：`backend-system-entry`
- verifier：`leader`
- cases：`CASE-P1-LIST-SYSTEMS`、`CASE-P1-SYSTEM-ENTRY`、`CASE-P1-SYSTEM-ENTRY-DENIED`、`CASE-P1-SYSTEM-DISABLED-ADMIN`
- writes：
  - `backend/examine-plat/src/main/java/com/unique/examine/plat/vnext/manage/systementry/**`
  - 最小通用 auth 扩展：`VNextSessionService.java`、`VNextSystemSessionService.java`、新 `SystemContextSnapshotCodec.java`、`SessionContext.java`、`AccountContext.java`、`SystemSummary.java`、`TenantSummary.java`、新 `DataScopeContext.java`；codec 只负责严格 SYSTEM snapshot 序列化/恢复，避免继续膨胀 SessionService
  - `backend/examine-web/src/main/java/com/unique/examine/web/vnext/systementry/**`
  - `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/controller/ContextController.java` 仅允许增加 `@Profile("!vnext")`，避免 legacy 与 vNext context endpoint 在真实启动时重复映射；不得修改旧接口实现
  - `backend/examine-web/src/test/java/com/unique/examine/web/P1SystemEntryUseCaseContractTest.java`
  - `backend/examine-web/src/test/java/com/unique/examine/web/P1SystemEntryCycleIntegrationTest.java`；实现任务只完成并做 focused 开发验证，正式 cycle plan 由 VERIFY 且只执行一次
- forbidden：migration/generated Base/legacy SessionService；Mapper/JDBC/SQL；平台切换、租户管理、system business APIs 或新业务页面。
- required check：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .cursor/scripts/run-maven-focused-test.ps1 -Module examine-web -Tests P1SystemEntryUseCaseContractTest
```

focused test 必须锁定：两个用例的精确 IService 边界、列表过滤/排序/recentEnteredAt、完整授权解析、DENY 优先、ACTIVE switch、来源 session CAS 原子替换及并发 loser 回滚、五类错误无写入、DISABLED admin 精确 settings-only、current/refresh 快照读回、Cookie 提交边界和零 Mapper/JDBC/SQL。

## TASK-P1-04-SYSTEM-SHELL-UI

- owner：`frontend-system-entry`
- verifier：`leader`
- writes：
  - 扩展现有 `frontend/src/vnext/platform-shell/**`，不得复制第二套 My Systems
  - 扩展现有 `frontend/src/vnext/onboarding/**`，不得挂旧 SystemAdminLayout
  - `frontend/src/router/index.ts`、`frontend/src/stores/session.ts`、`frontend/src/types/session.ts`、`frontend/src/services/api.ts`
  - 新 `frontend/tests/unit/p1-system-entry-foundation.spec.ts`
  - `frontend/tests/unit/p1-login-foundation.spec.ts` 只允许更新 P1-02 “My Systems mount 不取列表”的阶段性断言；注册/onboarding 不调用 list/switch 的断言继续保留
  - `frontend/tests/unit/p1-registration-foundation.spec.ts` 只允许把 fixture 补成当前 required wire：SYSTEM 补 `roleIds/dataScope/restrictedMode`，PLATFORM 补 `tenants:[]/firstSystemId:null`；既有 settings-only fixture 必须同步收窄为 admin-only permissions/shells。不得改 P1-03 断言或行为语义
  - `frontend/tests/unit/session.spec.ts`、`access-policy.spec.ts`、`account-security-navigation.spec.ts`、`system-command-center.spec.ts` 只允许给冻结旧 fixture 补齐同一 required wire，保持 `applyAuth` 和 vNext API 严格类型；不得改旧断言、路由或功能语义
  - `frontend/tests/unit/platform-agent-view.spec.ts` 只允许把既有 `switchSystem` mock 的 undefined 返回改成相同测试 system ID 字符串，以匹配 strict 返回类型；不得改任何断言或旧页面行为
  - 旧 unit 中直接调用 strict `applyAuth` 的 fixture 一次性补齐当前 required wire（context 的 `roleIds/dataScope/restrictedMode`，result 的 `tenants/firstSystemId`）；只允许修改 `ai-navigation.spec.ts`、`analytics-navigation.spec.ts`、`audit-navigation.spec.ts`、`dashboard-navigation.spec.ts`、`data-source-navigation.spec.ts`、`enterprise-identity-admin.spec.ts`、`flow-view.spec.ts`、`kpi-navigation.spec.ts`、`member-picker.spec.ts`、`message-inbox-view.spec.ts`、`openapi-admin-navigation.spec.ts`、`operations-dashboard.spec.ts`、`platform-ai-navigation.spec.ts`、`platform-settings-navigation.spec.ts`、`platform-todo-view.spec.ts`、`platform-work-view.spec.ts`、`platform-workbench.spec.ts`、`record-comment-panel.spec.ts`、`record-file-panel.spec.ts`、`record-history-panel.spec.ts`、`record-team-panel.spec.ts`、`report-navigation.spec.ts`、`system-dashboard-view.spec.ts`、`system-onboarding-navigation.spec.ts`、`system-organization-view.spec.ts`、`system-workbench-batch-commands.spec.ts`、`todo-action-center.spec.ts`、`todo-navigation.spec.ts`、`ui-hardening-state-matrix.spec.ts`、`work-daily-reports.spec.ts`、`work-message-template-navigation.spec.ts`、`work-tasks-view.spec.ts`、`work-todo-navigation.spec.ts`；不得改断言、路由、生产兼容层或旧功能语义
  - `frontend/tests/e2e/p1-system-entry-cycle.spec.ts`；实现任务只完成确定性 spec 和单 spec 开发验证，正式 cycle plan 由 VERIFY 且只执行一次
- `/platform/systems`：唯一 sidebar“我的系统”，topbar 只有当前账号/logout；表格列为系统、状态、角色、最近进入、操作，行内唯一动作“进入系统”，无页面级新建按钮、卡片墙或创建系统。
- mount 必须调用 list 精确一次并以返回值覆盖 login/current 中的 systems；retry 每次点击新增一次 list，不自动循环。状态精确为 loading、empty_create_first_system_guidance、ready、switching、system_disabled、membership_denied、error。
- switch 防双击；一次点击只发一个 POST。成功前校验 context.systemId 等于所选系统、tenantId/memberId 非空、roleIds 非空、dataScope 完整、permissionVersion/permissions/restrictedMode 合法以及对应 shell；malformed success 不 apply、不导航。
- `SYSTEM_NOT_FOUND/SYSTEM_MEMBER_REQUIRED/SYSTEM_MEMBER_DISABLED` 映射 membership_denied；`SYSTEM_DISABLED` 映射 system_disabled；`SYSTEM_NO_TENANT` 映射稳定 error。失败保留 PLATFORM context、留在 My Systems、无 current/list 自动补偿请求，不显示 raw message/requestId/traceId。
- ACTIVE owner 成功进入精确 `/systems/{systemId}/admin/onboarding`，页面显示当前系统、当前账号、可懂角色名和“系统上下文已就绪”；唯一 sidebar“配置引导”。ADMIN_SETTINGS_ONLY 是成功状态，同一路由明确显示“系统已停用，仅可进行管理设置”，不得展示 SYSTEM_RUNTIME 或任何运行入口。
- `/platform/systems` 只消费 PLATFORM context；SYSTEM context 访问时回自己的 onboarding，绝不调用 legacy platform switch。onboarding 未登录回 login；PLATFORM、错误 systemId 或缺 SYSTEM_ADMIN 时渲染 permission_denied，route guard/component 都不得按 URL 自动 switch。
- root/fallback/guestOnly 保持 context-aware：PLATFORM 到 My Systems，SYSTEM 到自己的 onboarding。运行态路由仍精确为 login/register/platform systems/onboarding/root/fallback。
- onboarding mount 本身不发业务请求；刷新只走 current，401 时至多一次 refresh。不得调用 tenant、module 或其他 system business API。
- required check：

```text
npm.cmd --prefix frontend test -- p1-system-entry-foundation.spec.ts p1-login-foundation.spec.ts p1-registration-foundation.spec.ts && npm.cmd --prefix frontend run build
```

unit 必须覆盖：list 一次/权威覆盖/五态/retry，紧凑表与单一行动作，switch 防双击，完整 context 校验，五 code 映射，enabled/settings-only/denied shell，route guard 矩阵，reload/logout，零 platform-switch/tenant/business API 和未来入口。

## TASK-P1-04-VERIFY

- owner：`test`
- verifier：`leader`
- dependencies：后端和前端任务均有真实非零通过证据。
- writes：
  - `.cursor/session/evidence/baseline/CYCLE-P1-04-SYSTEM-ENTRY/**`
- forbidden：验证阶段修改功能或测试源码、复用历史 evidence、执行全量回归/性能/容量测试。
- required check：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .cursor/scripts/run-verified-test-plan.ps1 -Plan .cursor/session/baseline/test-plans/cycle-p1-04-system-entry.json -EvidenceRoot .cursor/session/evidence/baseline/CYCLE-P1-04-SYSTEM-ENTRY
```

真实后端 integration 必须覆盖：授权列表不泄露无成员系统、roleNames/defaultTenant/recentEnteredAt，ACTIVE owner switch 的同 scope role/data-scope/permission snapshot 与来源 session 替换，current/refresh readback，无成员、成员停用、停用普通成员均无新 session，停用管理员只得到 ADMIN_SETTINGS_ONLY 且 reload 保持限制，数据库/日志/evidence 无原始 token。

浏览器用确定性内存 API 覆盖三条可见流程：再次登录后 list→enabled owner switch→onboarding→reload→logout；普通成员进入停用系统留在列表显示明确拒绝；停用管理员进入 settings-only 并在 reload 后保持。每条 spec 对 `/api/v1/**` 使用 allowlist，断言无 platform switch、tenant、module/business API 或旧导航。

## 周期 Gate

只有下列全部成立才可把本周期和 P1 记为通过：

1. 两项 task required check 的 executed test count 大于 0，skipped/failures/errors 均为 0。
2. plat/web vNext 静态审计 0 errors、0 warnings，systementry/auth 不含 Mapper/JDBC/SQL。
3. frozen cycle plan 实际执行两个 test entry，manifest 为 pass；计划校验、编译成功或历史 evidence 不能冒充周期通过。
4. 列表授权、完整上下文、拒绝、settings-only、current/refresh 和浏览器真实入口都能从同一周期 evidence 读回。
5. 页面和运行态路由没有暴露任何 P1 以后入口。
