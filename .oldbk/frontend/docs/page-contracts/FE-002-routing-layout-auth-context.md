# FE-002 路由布局认证上下文契约证据

> 本文件只记录 FE-002 基础能力证据，不替代 FE-012 的 `frontend/docs/api-contract-map.md` 汇总。

## 页面基本信息

| 项 | 内容 |
| --- | --- |
| 任务 ID | FE-002 |
| 页面/模块 | 路由、布局、认证态、系统/租户/member 上下文、权限守卫、统一错误入口 |
| 路由 | `/auth/*`、`/platform/*`、`/systems/:systemId/*`、`/ops` |
| 入口 | 登录区、平台中心、系统管理、应用配置、运行台、流程、文件导出、OpenAPI、审计运维 |
| 依赖上下文 | accessToken / systemId / tenantId / memberId / EffectivePermissionVO |
| SDK 引用 | `frontend/src/api` |

## API 映射证据

| API ID | 方法 | 路径 | 触发动作 | 必填 path 入参 | 必填 query 入参 | 必填 body 入参 | 必填 header | 响应字段 | 枚举/状态 | 错误码 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AUTH-001 | POST | `/api/v1/auth/register` | 注册路由占位 | 无 | 无 | loginName、password、displayName | X-Request-Id | accessToken、refreshToken、account | accountStatus | AUTH_INVALID_CREDENTIAL、PLAT_ACCOUNT_DUPLICATED、COMMON_PARAM_INVALID | FE-003 实现页面调用 |
| AUTH-002 | POST | `/api/v1/auth/login` | 登录路由占位 | 无 | 无 | loginName、password | X-Request-Id | accessToken、refreshToken、account | accountStatus | AUTH_INVALID_CREDENTIAL、AUTH_ACCOUNT_DISABLED、AUTH_ACCOUNT_LOCKED | FE-002 仅提供 auth store 写入入口 |
| AUTH-003 | POST | `/api/v1/auth/refresh` | token 续签占位 | 无 | 无 | refreshToken | X-Request-Id | accessToken、refreshToken | accountStatus | AUTH_TOKEN_EXPIRED、AUTH_REFRESH_INVALID | token 不在 FE-002 持久化到不安全位置 |
| AUTH-004 | POST | `/api/v1/auth/logout` | 退出登录占位 | 无 | 无 | 无 | Authorization / X-Request-Id | success | 无 | AUTH_TOKEN_EXPIRED | authStore.logout 清理内存态 |
| AUTH-005 | GET | `/api/v1/auth/me` | 会话恢复占位 | 无 | 无 | 无 | Authorization / X-Request-Id | accountId、loginName、platformRoles、platformPermissions | accountStatus | AUTH_TOKEN_EXPIRED | FE-003 调用后写入 authStore |
| PLAT-001 | GET | `/api/v1/platform/my-systems` | 我的系统入口 | 无 | 无 | 无 | Authorization / X-Request-Id | systemId、systemName、tenantId、memberId、status | systemStatus、tenantStatus | PERM_DENIED | FE-003 实现系统选择 |
| SYS-001 | POST | `/api/v1/systems/{systemId}/enter` | 进入系统后建立上下文 | systemId | 无 | tenantId 可选 | Authorization / X-Tenant-Id / X-Request-Id | systemId、tenantId、memberId、memberRoles、systemPermissions、runtimeHomePage | systemStatus、tenantStatus | SYS_NOT_FOUND、SYS_DISABLED、SYS_CONTEXT_REQUIRED、SYS_MEMBER_DISABLED | FE-002 提供 systemContextStore.setContext |
| SYS-007 | POST | `/api/v1/systems/{systemId}/tenant-context/switch` | 切换租户后刷新上下文 | systemId | 无 | tenantId | Authorization / X-Tenant-Id / X-Request-Id | tenantId、tenantName、memberId、systemPermissions | tenantStatus | SYS_TENANT_DISABLED、SYS_CONTEXT_REQUIRED | 切换后 permissionStore.markStale |
| RBAC-010 | GET | `/api/v1/systems/{systemId}/rbac/effective-permissions` | 刷新有效权限 | systemId | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | memberId、roles、menus、operations、fieldPermissions、dataScopes、availableActions、version | DataScopeType | PERM_DENIED、SYS_MEMBER_DISABLED | 权限禁用态统一来源 |
| RBAC-011 | GET | `/api/v1/systems/{systemId}/rbac/runtime-menus` | 刷新运行菜单 | systemId | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | runtime menu tree/code | 无 | PERM_DENIED | 布局导航使用菜单可见性 |

## 路由覆盖证据

| 路由区域 | 路由前缀 | 代表路由名 | 绑定 API ID | 必填上下文 | 权限禁用来源 | 后续页面任务 |
| --- | --- | --- | --- | --- | --- | --- |
| 登录区 | `/auth` | auth.login / auth.register / auth.resetPassword | AUTH-001 至 AUTH-006 | 无 | 无 | FE-003 |
| 平台中心 | `/platform` | platform.mySystems / platform.systems / platform.accounts / platform.roles / platform.configs | PLAT-001、PLAT-003 至 PLAT-020、AUD-006、AUD-008 | accessToken | platformPermissions / permissionStore | FE-003、FE-004、FE-011 |
| 系统管理 | `/systems/:systemId/profile`、`/tenants` | system.profile / system.tenants | SYS-001 至 SYS-007 | systemId、memberId，MULTI 租户需 tenantId | EffectivePermissionVO.operations | FE-005 |
| 成员 RBAC 字典 | `/systems/:systemId/members`、`/departments`、`/roles`、`/dict` | system.members / system.departments / system.roles / system.dict | MEM-*、RBAC-*、DICT-* | systemId、memberId，租户接口按上下文带 X-Tenant-Id | AvailableAction / PermissionHint / FieldPermission | FE-005 |
| 应用配置 | `/systems/:systemId/apps`、`/modules`、`/fields`、`/ui` | apps.list / modules.list / modules.fields / modules.ui | APP-*、MOD-*、FIELD-*、UI-* | systemId、tenantId、memberId | AvailableAction / operations | FE-006 |
| 运行台 | `/systems/:systemId/runtime` | runtime.home / runtime.module | RUN-001 至 RUN-010 | systemId、tenantId、memberId、moduleId | RuntimeModuleSchemaVO.availableActions / FieldPermission | FE-008 |
| 流程 | `/systems/:systemId/flow` | flow.templates / flow.workbench | FLOW-001 至 FLOW-021 | systemId、tenantId、memberId | AvailableAction / task actor | FE-009 |
| 文件导出 | `/systems/:systemId/files`、`/exports` | files.center / exports.jobs | FILE-*、EXP-* | systemId、tenantId、memberId | file downloadable/previewable、ExportJobDetailVO.retryable | FE-010 |
| OpenAPI | `/systems/:systemId/openapi` | openapi.clients | OPM-001 至 OPM-009 | systemId、tenantId、memberId | OpenAPI scope / operations | FE-011 |
| 审计运维 | `/systems/:systemId/audit`、`/platform/audit`、`/ops` | audit.system / audit.platform / ops.health | AUD-*、OPS-* | 系统审计需 systemId/memberId；平台审计和 ops 需平台权限 | requestId、AUDIT/OPS 权限 | FE-011 |

## 字段映射

| UI 字段 | API 字段 | 来源 | 是否前端可写 | 提交转换规则 | 回显/空值规则 |
| --- | --- | --- | --- | --- | --- |
| 登录态 | Authorization Bearer token | AUTH-002 / AUTH-003 响应 | 否 | `authStore.toApiContext()` 生成 Authorization 所需 accessToken | 未登录时路由守卫跳转 `/auth/login` |
| 当前系统 | systemId | 路由 path / SYS-001 响应 | 否 | `systemContextStore.toPathParams()` 补齐 path param | 缺失时跳转 `/platform/my-systems` |
| 当前租户 | tenantId / X-Tenant-Id | SYS-001 / SYS-007 响应 | 否 | `systemContextStore.toTenantHeader()` 交给统一请求上下文 | 多租户缺失时阻止系统内业务请求 |
| 当前成员 | memberId | SYS-001 / MEM-007 / RBAC-010 响应 | 否 | 仅用于权限与展示，不写入业务 BO | 成员停用时展示 `SYS_MEMBER_DISABLED` |
| 有效权限 | operations / menus / fieldPermissions / availableActions | RBAC-010 / RBAC-011 | 否 | `permissionStore.setEffectivePermission()` 写入 | stale 时页面应刷新权限后再展示动作 |
| requestId | requestId / X-Request-Id | ApiResponse / ApiErrorResponse | 否 | 请求层可传，错误入口统一提取 | 错误态必须展示，可复制用于审计检索 |

## 枚举、状态与错误码

| 类型 | 契约来源 | 页面使用位置 | 展示/禁用规则 |
| --- | --- | --- | --- |
| 枚举 | `frontend/src/api/enums.ts` | systemStatus、tenantStatus、accountStatus、DataScopeType | 状态不在声明集合内时按错误态处理 |
| 状态 | `frontend/src/api/enums.ts` | 系统/租户/member 上下文、导出/流程/运行台后续页面 | `DISABLED`、`ARCHIVED`、成员停用进入 `contextDisabledReason` |
| 错误码 | `frontend/src/api/errorCodes.ts` | 路由守卫和错误 store | 必须展示 `message` 与 `requestId`；无 requestId 的本地守卫错误标记 `requestIdRequired=true` |

## 权限禁用态

| 控件/动作 | 权限来源 | 禁用字段 | 禁用展示 | 后端兜底错误码 |
| --- | --- | --- | --- | --- |
| 一级/二级导航 | route.meta.permission + EffectivePermissionVO.operations | visible=false / enabled=false | `PERM_DENIED` 或 disabledReason | PERM_DENIED |
| 业务按钮 | AvailableAction | enabled=false | disabledReason / stateReason | PERM_DENIED |
| 字段编辑 | FieldPermission | writable=false | readonlyReason | PERM_FIELD_WRITE_DENIED |
| 系统内页面 | systemContextStore | status=disabled / missing context | SYS_DISABLED / SYS_TENANT_DISABLED / SYS_MEMBER_DISABLED / SYS_CONTEXT_REQUIRED | SYS_DISABLED、SYS_TENANT_DISABLED、SYS_MEMBER_DISABLED、SYS_CONTEXT_REQUIRED |

## 空态与错误态

| 场景 | 判定条件 | 展示内容 | requestId 展示 | 重试动作 |
| --- | --- | --- | --- | --- |
| 未登录 | `authStore.status !== authenticated` 且访问 requiresAuth 路由 | 跳转登录 | 不需要 | 登录后回到 redirect |
| 无系统上下文 | 缺少 systemId/memberId，或多租户缺 tenantId | 返回我的系统选择 | 不需要 | 重新进入系统或切换租户 |
| 上下文停用 | system/tenant/member disabled | 展示禁用原因 | 本地守卫错误要求后续 API 错误展示 requestId | 切换系统或联系管理员 |
| 权限不足 | permissionStore.decide 返回 disabled/hidden | 展示无权限或禁用态 | API 兜底错误必须展示 requestId | 刷新权限或联系管理员 |
| API 错误 | `ApiClientError` | `message` + `code` + `requestId` | 必须展示 | retryable=true 时允许重试 |

## requestId 与审计

- 所有 API 错误态通过 `errorStore.capture()` 归一化为 `RequestErrorDisplay`，保留 `requestId`、`code`、`message`、`retryable`、`details`。
- 审计、OpenAPI、导出相关后续页面必须支持复制或检索 `requestId`；FE-002 已在 `AppShellState.requestIdVisible=true` 固化展示入口。
- 写接口的 `X-Idempotency-Key` 由 FE-001 `createApiClient` 校验，FE-002 不伪造写接口调用。

## 无旁路请求检查

| 检查项 | 结论 |
| --- | --- |
| 页面没有直接调用 `axios` / `fetch` / `XMLHttpRequest` | 通过；FE-002 未实现页面请求 |
| 页面所有请求均通过 `frontend/src/api` typed client | 通过；store 和 router 只消费 API 类型，不发请求 |
| API ID 均存在于 `API_ENDPOINTS` | 通过；`assertRouteApiIds()` 可静态检查路由 API ID |
| 未伪造冻结 API 未声明的创建/编辑/删除/提交语义 | 通过；路由只声明后续页面归属和 API 证据 |
| `systemId` / `tenantId` 等上下文字段由统一请求层或路由上下文补齐 | 通过；`systemContextStore.toPathParams()` 与 `toTenantHeader()` 统一提供 |
| 无法补齐必填上下文时，页面阻止请求并展示空态或明确提示 | 通过；`createRouteGuard()` 返回 `CONTEXT_REQUIRED` 或 `CONTEXT_DISABLED` |
