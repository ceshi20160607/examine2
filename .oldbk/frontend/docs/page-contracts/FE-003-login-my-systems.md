# FE-003 登录注册与我的系统契约证据

> 本文件只记录 FE-003 页面级证据，不替代 FE-012 的 `frontend/docs/api-contract-map.md` 汇总。

## 页面基本信息

| 项 | 内容 |
| --- | --- |
| 任务 ID | FE-003 |
| 页面/模块 | 登录、注册、刷新、退出、当前用户、我的系统 |
| 路由 | `/auth/login`、`/auth/register`、`/platform/my-systems`；退出为页面动作返回 `/auth/login` |
| 入口 | 未登录守卫跳转、登录成功默认进入我的系统、创建系统后进入系统运行台 |
| 依赖上下文 | accessToken / refreshToken / systemId / tenantId / memberId |
| SDK 引用 | `frontend/src/api` |

## API 映射证据

| API ID | 方法 | 路径 | 触发动作 | 必填 path 入参 | 必填 query 入参 | 必填 body 入参 | 必填 header | 响应字段 | 枚举/状态 | 错误码 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AUTH-001 | POST | `/api/v1/auth/register` | 注册提交 | 无 | 无 | loginName、password；displayName/mobile/email 可选 | X-Request-Id | accountId、loginName、displayName、accountStatus、accessToken、refreshToken、expiresIn、platformRoles、platformPermissions | accountStatus: NORMAL/DISABLED/LOCKED | PLAT_ACCOUNT_DUPLICATED、COMMON_PARAM_INVALID | 成功后写入 authStore 并调用 AUTH-005 补齐当前用户 |
| AUTH-002 | POST | `/api/v1/auth/login` | 登录提交 | 无 | 无 | loginName、password；captchaToken 可选 | X-Request-Id | accessToken、refreshToken、expiresIn、accountId、loginName、displayName、platformRoles、platformPermissions | accountStatus: NORMAL/DISABLED/LOCKED | AUTH_INVALID_CREDENTIAL、AUTH_ACCOUNT_DISABLED、AUTH_ACCOUNT_LOCKED、COMMON_PARAM_INVALID | 登录表单不展示 token 内部信息 |
| AUTH-003 | POST | `/api/v1/auth/refresh` | token 续签 | 无 | 无 | refreshToken | X-Request-Id | accessToken、refreshToken、expiresIn | accountStatus | AUTH_TOKEN_EXPIRED、AUTH_REFRESH_INVALID | 缺少 refreshToken 时前端阻止请求并标记过期 |
| AUTH-004 | POST | `/api/v1/auth/logout` | 退出登录 | 无 | 无 | 无 | Authorization / X-Request-Id | success/code/message | 无 | AUTH_TOKEN_EXPIRED | 失败也清理本地 auth/system/permission 状态 |
| AUTH-005 | GET | `/api/v1/auth/me` | 登录后补齐用户、会话恢复 | 无 | 无 | 无 | Authorization / X-Request-Id | accountId、loginName、displayName、accountStatus、platformRoles、platformPermissions、lastLoginAt | accountStatus | AUTH_TOKEN_EXPIRED | 当前用户信息只来自 me，不从登录表单回显 |
| PLAT-001 | GET | `/api/v1/platform/my-systems` | 打开/刷新我的系统 | 无 | 无 | 无 | Authorization / X-Request-Id | systemId、systemCode、systemName、status、tenantMode、tenantId、tenantName、tenantStatus、memberId、memberRoleNames、availableActions | systemStatus、tenantStatus | PERM_DENIED、AUTH_TOKEN_EXPIRED | 空数组进入无系统空态 |
| PLAT-002 | POST | `/api/v1/platform/systems` | 创建系统 | 无 | 无 | name、code、tenantMode；description 可选 | Authorization / X-Request-Id / X-Idempotency-Key | systemId、systemCode、systemName、tenantMode、tenantId、initializedObjects | tenantMode: SINGLE/MULTI；systemStatus | PLAT_SYSTEM_CODE_DUPLICATED、PLAT_SYSTEM_INIT_FAILED、PERM_DENIED、COMMON_IDEMPOTENCY_CONFLICT | 成功后刷新 PLAT-001，并用响应或列表解析 systemId |
| SYS-001 | POST | `/api/v1/systems/{systemId}/enter` | 进入系统 | systemId | 无 | tenantId 可选 | Authorization / X-Tenant-Id / X-Request-Id | systemId、systemName、status、tenantMode、tenantId、tenantStatus、memberId、memberRoles、memberRoleNames、memberStatus、runtimeHomePage | systemStatus、tenantStatus、member status | SYS_NOT_FOUND、SYS_DISABLED、SYS_CONTEXT_REQUIRED、SYS_MEMBER_DISABLED、SYS_TENANT_DISABLED | 进入系统必须先建立系统成员上下文，不跳过成员上下文 |

## 字段映射

| UI 字段 | API 字段 | 来源 | 是否前端可写 | 提交转换规则 | 回显/空值规则 |
| --- | --- | --- | --- | --- | --- |
| 登录名 | loginName | 登录/注册表单 | 是 | 原样提交 AUTH-001/AUTH-002 | 空值前端阻止提交 |
| 密码 | password | 登录/注册表单 | 是 | 原样提交，不展示哈希或 token 内部信息 | 空值前端阻止提交 |
| 验证码令牌 | captchaToken | 登录表单 | 是 | 有值才随 AUTH-002 body 提交 | 无值不提交 |
| 展示名 | displayName | 注册表单 / AUTH-005 | 是 | 注册时可选提交，当前用户以 AUTH-005 返回为准 | 空时使用 loginName 展示 |
| 手机 / 邮箱 | mobile / email | 注册表单 | 是 | 有值才提交 AUTH-001 | 不在登录页回显 |
| 系统名称 | name / systemName | 创建系统表单 / PLAT-001 | 是 | 创建提交 name；列表展示 systemName | 空值前端阻止创建 |
| 系统编码 | code / systemCode | 创建系统表单 / PLAT-001 | 是 | 创建提交 code；创建后用 code 辅助定位新系统 | 空值前端阻止创建 |
| 租户模式 | tenantMode | 创建系统表单 / PLAT-001 | 是 | 仅 SINGLE 或 MULTI | 缺失前端阻止创建 |
| 当前系统 | systemId | PLAT-001 / SYS-001 | 否 | 作为 SYS-001 path param | 缺失时不进入系统内页 |
| 当前租户 | tenantId / X-Tenant-Id | PLAT-001 / SYS-001 | 否 | 有 tenantId 时传 body 和上下文 header | 多租户缺失由后续系统上下文阻止业务请求 |
| 当前成员 | memberId | SYS-001 | 否 | 只写入 systemContextStore | 缺失视为 SYS_CONTEXT_REQUIRED |
| requestId | requestId / X-Request-Id | typed client / API 响应 | 否 | 每次动作生成并透传 | API 错误态必须展示 |

## 枚举、状态与错误码

| 类型 | 契约来源 | 页面使用位置 | 展示/禁用规则 |
| --- | --- | --- | --- |
| 枚举 | `frontend/src/api/enums.ts` | tenantMode、accountStatus、systemStatus、tenantStatus | tenantMode 仅允许 SINGLE/MULTI；状态不在声明集合时按错误态处理 |
| 状态 | `frontend/src/api/enums.ts` | 账号停用/锁定、系统停用/归档、租户停用、成员停用 | AUTH_ACCOUNT_DISABLED/AUTH_ACCOUNT_LOCKED 展示账号状态；SYS_DISABLED/SYS_TENANT_DISABLED/SYS_MEMBER_DISABLED 阻止进入系统 |
| 错误码 | `frontend/src/api/errorCodes.ts` | 登录失败、注册重复、刷新失效、创建系统失败、进入系统失败 | 必须展示 message 与 requestId；本地阻止类错误无 requestId |

## 权限禁用态

| 控件/动作 | 权限来源 | 禁用字段 | 禁用展示 | 后端兜底错误码 |
| --- | --- | --- | --- | --- |
| 创建系统按钮 | PLAT-001 availableActions 或 authStore.platformPermissions | createEnabled=false | disabledReason / PERM_DENIED | PERM_DENIED |
| 进入系统按钮 | SYS-001 后端成员上下文校验 | enteringSystemId / 系统状态 | SYS_DISABLED / SYS_MEMBER_DISABLED / SYS_TENANT_DISABLED | SYS_DISABLED、SYS_MEMBER_DISABLED、SYS_TENANT_DISABLED |
| 登录/注册提交 | AUTH 表单必填校验 | loading=true 或必填缺失 | COMMON_PARAM_INVALID | COMMON_PARAM_INVALID |
| 刷新 token | authStore.refreshToken | 缺少 refreshToken | AUTH_REFRESH_INVALID | AUTH_REFRESH_INVALID |

## 空态与错误态

| 场景 | 判定条件 | 展示内容 | requestId 展示 | 重试动作 |
| --- | --- | --- | --- | --- |
| 无系统空态 | PLAT-001 返回空数组 | 展示创建系统入口；无 PLAT_SYSTEM_CREATE 时展示无权限原因 | 不需要 | 重新加载 PLAT-001 |
| 登录失败 | AUTH-002 抛出 ApiClientError | message、code、requestId；账号锁定/停用时标记 accountStatus | 必须展示 | retryable=true 时可重新提交 |
| 注册失败 | AUTH-001 抛出 ApiClientError | message、code、requestId | 必须展示 | 修改表单后重新提交 |
| 刷新失败 | AUTH-003 返回 AUTH_REFRESH_INVALID/AUTH_TOKEN_EXPIRED | 会话过期并跳转登录 | 必须展示 | 重新登录 |
| 创建系统失败 | PLAT-002 抛出 ApiClientError | message、code、requestId；幂等冲突显示不可重试 | 必须展示 | retryable=true 时重试 |
| 进入系统失败 | SYS-001 抛出 ApiClientError | message、code、requestId，清理未完成上下文 | 必须展示 | 回到我的系统后重试进入 |

## requestId 与审计

- 所有页面动作都通过 `ApiClient.call()` 传入 `context.requestId`，错误由 `errorStore.capture()` 保留 `requestId`、`code`、`message`、`retryable`。
- PLAT-002 使用 `X-Idempotency-Key`，不把幂等键伪造成业务字段。
- AUTH-002/AUTH-003 响应 token 只写入 authStore，不在页面模型中暴露 token 内部结构或哈希。

## 无旁路请求检查

| 检查项 | 结论 |
| --- | --- |
| 页面没有直接调用 `axios` / `fetch` / `XMLHttpRequest` | 通过；`frontend/src/pages/auth/` 与 `frontend/src/pages/my-systems/` 未直接调用 |
| 页面所有请求均通过 `frontend/src/api` typed client | 通过；统一使用 `ApiClient.call()` |
| API ID 均存在于 `API_ENDPOINTS` | 通过；AUTH-001 至 AUTH-005、PLAT-001、PLAT-002、SYS-001 均在 `frontend/src/api/endpoints.ts` |
| 未伪造冻结 API 未声明的创建/编辑/删除/提交语义 | 通过；只实现注册、登录、刷新、退出、me、我的系统、创建系统、进入系统 |
| `systemId` / `tenantId` 等上下文字段由统一请求层或路由上下文补齐 | 通过；SYS-001 使用 pathParams 和 auth/system context，进入成功后写 `systemContextStore` |
| 无法补齐必填上下文时，页面阻止请求并展示空态或明确提示 | 通过；缺少 refreshToken、systemId 或创建权限时阻止请求并写错误态 |
