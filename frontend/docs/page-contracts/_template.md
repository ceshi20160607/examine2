# 页面级 API 契约映射模板

> 复制本模板到当前页面任务文件，例如 `FE-003-login-my-systems.md`。本文件只记录证据，不替代 `frontend/docs/api-contract-map.md` 的 FE-012 汇总。

## 页面基本信息

| 项 | 内容 |
| --- | --- |
| 任务 ID |  |
| 页面/模块 |  |
| 路由 |  |
| 入口 |  |
| 依赖上下文 | systemId / tenantId / appId / moduleId / memberId / 其他 |
| SDK 引用 | `frontend/src/api` |

## API 映射证据

| API ID | 方法 | 路径 | 触发动作 | 必填 path 入参 | 必填 query 入参 | 必填 body 入参 | 必填 header | 响应字段 | 枚举/状态 | 错误码 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |  | Authorization / X-Tenant-Id / X-Request-Id / X-Idempotency-Key |  |  |  |  |

## 字段映射

| UI 字段 | API 字段 | 来源 | 是否前端可写 | 提交转换规则 | 回显/空值规则 |
| --- | --- | --- | --- | --- | --- |
|  |  | 表单 / 路由 / 上下文 / 后端返回 | 是/否 |  |  |

## 枚举、状态与错误码

| 类型 | 契约来源 | 页面使用位置 | 展示/禁用规则 |
| --- | --- | --- | --- |
| 枚举 | `frontend/src/api/enums.ts` |  |  |
| 状态 | `frontend/src/api/enums.ts` |  |  |
| 错误码 | `frontend/src/api/errorCodes.ts` |  | 必须展示 `message` 与 `requestId` |

## 权限禁用态

| 控件/动作 | 权限来源 | 禁用字段 | 禁用展示 | 后端兜底错误码 |
| --- | --- | --- | --- | --- |
|  | `AvailableAction` / `PermissionHint` / `FieldPermission` | `enabled=false` / `writable=false` | `disabledReason` / `readonlyReason` | `PERM_DENIED` / `PERM_FIELD_WRITE_DENIED` |

## 空态与错误态

| 场景 | 判定条件 | 展示内容 | requestId 展示 | 重试动作 |
| --- | --- | --- | --- | --- |
| 空态 |  |  | 不需要 |  |
| 错误态 | `ApiClientError` | `message` + `requestId` | 必须展示 |  |

## requestId 与审计

- 所有 API 错误态必须展示 `requestId`。
- 日志/审计/导出/OpenAPI 页面必须支持按 `requestId` 检索或复制。
- 写接口必须记录是否传入 `X-Idempotency-Key` 或 body `idempotencyKey`。

## 无旁路请求检查

| 检查项 | 结论 |
| --- | --- |
| 页面没有直接调用 `axios` / `fetch` / `XMLHttpRequest` |  |
| 页面所有请求均通过 `frontend/src/api` typed client |  |
| API ID 均存在于 `API_ENDPOINTS` |  |
| 未伪造冻结 API 未声明的创建/编辑/删除/提交语义 |  |
| `systemId` / `tenantId` 等上下文字段由统一请求层或路由上下文补齐 |  |
| 无法补齐必填上下文时，页面阻止请求并展示空态或明确提示 |  |
