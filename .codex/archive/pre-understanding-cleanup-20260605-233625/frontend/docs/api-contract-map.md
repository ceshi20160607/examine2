# 前端 API 契约映射

## 统一契约

- 技术栈：React 18 + TypeScript + Vite + Ant Design + Zustand + React Router + Axios。
- API 入口：页面只调用 `frontend/src/api/sdk.ts`，不得直接使用 axios/fetch。
- 统一请求层：`frontend/src/api/http.ts` 从 `useSessionStore` 注入 `X-Account-Id`、`X-Tenant-Id`、`X-System-Id`。
- 上下文补齐：SDK 的 `withContext` 自动把 `tenantId/systemId/appId/moduleId` 从状态补齐到 BO。缺少必填上下文时抛出 `MissingContextError`，页面用 `ContextGate` 阻止请求并展示空状态提示。
- 响应结构：所有 SDK 方法按 `ApiResult<T>` 解包，只接受 `code = SUCCESS`，其他错误码统一抛 `ApiError`。
- 列表语义：`docs/api.md` 明确列表接口未实现分页，所有表格 `pagination=false`，不伪造分页。
- 更新语义：只有 `PUT /api/modules/records?id={id}` 暴露更新接口，因此仅运行态记录展示“编辑”；系统、租户、账号、角色、权限、应用、模块、字段、页面、流程、上传和 OpenAPI 均不展示伪造编辑按钮。

## 枚举集中定义

- `ErrorCode`：`SUCCESS`、`SYSTEM_ERROR`、`PLAT_PARAM_REQUIRED`、`PLAT_DATA_NOT_FOUND`、`PLAT_STATUS_INVALID`、`PLAT_PERMISSION_TYPE_INVALID`、`PLAT_LOGIN_FAILED`、`MODULE_PARAM_REQUIRED`、`MODULE_DATA_NOT_FOUND`、`MODULE_FIELD_INVALID`、`MODULE_RECORD_NO_DUPLICATE`、`MODULE_STATUS_INVALID`、`FLOW_PARAM_REQUIRED`、`FLOW_DATA_NOT_FOUND`、`FLOW_STATUS_INVALID`、`FLOW_ACTION_TYPE_INVALID`、`FLOW_TASK_ASSIGNEE_INVALID`、`UPLOAD_PARAM_REQUIRED`、`UPLOAD_DATA_NOT_FOUND`、`UPLOAD_STATUS_INVALID`、`APP_PARAM_REQUIRED`、`APP_DATA_NOT_FOUND`、`APP_STATUS_INVALID`。
- 错误码同步场景：`PLAT_PERMISSION_TYPE_INVALID` 对应平台权限类型非 `MENU/BUTTON/API/FIELD/DATA_SCOPE`；`FLOW_ACTION_TYPE_INVALID` 对应流程任务动作非 `APPROVE/REJECT/TRANSFER/CANCEL`。
- `CommonStatus`：`ENABLED`、`DISABLED`、`LOCKED`、`DRAFT`、`PUBLISHED`、`ACTIVE`、`RUNNING`、`COMPLETED`、`APPROVED`、`DELETED`、`PENDING`、`DONE`、`REJECTED`、`CANCELED`、`TRANSFERRED`、`EXPIRED`、`TEMP`、`REFERENCED`。
- `ModuleStatus`：`DRAFT`、`PUBLISHED`、`DISABLED`，仅用于 `PATCH /api/modules/models/status?id={id}`；发布按钮提交 `PUBLISHED`，停用按钮提交 `DISABLED`，不提交 `ENABLED`。
- `DataScopeType`：`OWNER`、`DEPT`、`DEPT_TREE`、`ROLE`、`ALL`。
- `FieldType`：`TEXT`、`NUMBER`、`DATE`、`SELECT`、`MULTI_SELECT`、`BOOLEAN`、`FILE`。
- `PageType`：`LIST`、`FORM`、`DETAIL`。
- `FlowActionType`：`APPROVE`、`REJECT`、`TRANSFER`、`CANCEL`。
- `UploadJobType`：`IMPORT`、`EXPORT`。
- `PermissionType`：`MENU`、`BUTTON`、`API`、`FIELD`、`DATA_SCOPE`。

## 页面到接口映射

| 页面 | 接口 | 必填参数 | 响应字段 | 上下文依赖 | 新增/更新语义 |
| --- | --- | --- | --- | --- | --- |
| 登录页 `Login` | `POST /api/plat/auth/login` | `username`, `password` | `PlatformManageVO.id/code/name/tenantId/systemId` | 无；成功后写入 `accountId/tenantId/systemId` | 登录，不涉及新增/更新 |
| 工作台 `Workbench` | `GET /api/apps`; `GET /api/modules/models`; `GET /api/flows/tasks`; `GET /api/uploads/files` | 无 body | `AppManageVO[]`, `ModuleManageVO[]`, `FlowManageVO[]`, `UploadManageVO[]` | header 自动注入账号、租户、系统 | 只读摘要 |
| 平台中心-系统 | `GET /api/plat/systems`; `POST /api/plat/systems`; `PATCH /api/plat/systems/status?id={id}` | 创建：`systemCode`, `systemName`; 状态：`id`, `status` | `PlatformManageVO.id/code/name/status` | header 可注入 | 支持创建和启停；无编辑 |
| 平台中心-租户 | `GET /api/plat/tenants`; `POST /api/plat/tenants`; `PATCH /api/plat/tenants/status?id={id}` | 创建：`tenantCode`, `tenantName`; 可选 `adminAccountId/expireAt/configJson`; 状态：`id`, `status` | `PlatformManageVO.id/code/name/status` | header 可注入 | 支持创建和启停；无编辑 |
| 平台中心-账号 | `GET /api/plat/accounts`; `POST /api/plat/accounts`; `PATCH /api/plat/accounts/status?id={id}` | 创建：`username`, `displayName`, `password`; 可选 `mobile/email`; 状态：`id`, `status` | `PlatformManageVO.id/code/name/status/mobile/email` | header 可注入 | 支持创建和启停；无编辑 |
| 平台中心-角色 | `GET /api/plat/roles`; `POST /api/plat/roles`; `POST /api/plat/roles/permissions` | 创建：`roleCode`, `roleName`, `roleType`; SDK 补 `tenantId/systemId`; 授权：`roleId`, `permissionIds` | `PlatformManageVO.id/code/name/type/permissionIds` | 页面要求 `tenantId/systemId` | 支持创建和替换权限；无编辑 |
| 平台中心-权限点 | `GET /api/plat/permissions`; `POST /api/plat/permissions`; `POST /api/plat/accounts/roles` | 创建：`permissionCode`, `permissionName`, `permissionType`; SDK 补 `tenantId/systemId`; 账号授权：`accountId`, `roleIds` | `PlatformManageVO.id/code/name/type/resourcePath` | 页面要求 `tenantId/systemId` | 支持创建权限点和替换账号角色；无编辑 |
| 应用配置-应用 | `GET /api/apps`; `POST /api/apps` | `appCode`, `appName`, `visibleScope`; SDK 补 `tenantId/systemId` | `AppManageVO.id/code/name/status/versionNo` | 页面要求 `tenantId/systemId` | 支持创建；无编辑 |
| 应用配置-发布 | `POST /api/apps/publish` | `appId`, `versionNo`, `versionName`, `snapshotJson` | `AppManageVO.id/appId/versionNo/status/detail` | 使用表单中的 `appId` | 发布动作，不是更新 |
| 应用配置-模块 | `GET /api/modules/models`; `POST /api/modules/models`; `PATCH /api/modules/models/status?id={id}` | 创建：`moduleCode`, `moduleName`, `dataScopeType`, `flowEnabled`, `importEnabled`, `exportEnabled`; `flowEnabled/importEnabled/exportEnabled` 从 UI switch 显式转为 `0/1`; SDK 补 `tenantId/systemId/appId`; 状态：`id`, `status=PUBLISHED/DISABLED` | `ModuleManageVO.id/code/name/type/status/appId/moduleId/configJson` | 页面要求 `tenantId/systemId/appId` | 支持创建、发布和停用；无编辑 |
| 应用配置-字段 | `GET /api/modules/fields`; `POST /api/modules/fields` | `fieldCode`, `fieldName`, `fieldType`, `requiredFlag`, `uniqueFlag`, `listVisible`, `searchable`, `editable`; 标志位从 UI switch 显式转为 `0/1`; `options[]` 使用 `optionValue/optionLabel/sortOrder`; SDK 自动补 `moduleId` 到查询参数和创建 BO | `ModuleManageVO.id/code/name/type/configJson` | 页面要求 `moduleId`；缺失时 `ContextGate` 展示空状态且不请求列表 | 支持创建；无编辑 |
| 应用配置-页面 | `GET /api/modules/pages`; `POST /api/modules/pages` | `pageCode`, `pageName`, `pageType`, `layoutJson`; 可选 `buttonJson`; SDK 自动补 `moduleId` 到查询参数和创建 BO | `ModuleManageVO.id/code/name/type/configJson` | 页面要求 `moduleId`；缺失时 `ContextGate` 展示空状态且不请求列表 | 支持创建；无编辑 |
| 运行台-记录 | `GET /api/modules/records`; `POST /api/modules/records`; `GET /api/modules/records/detail?id={id}`; `PUT /api/modules/records?id={id}`; `DELETE /api/modules/records?id={id}` | 新增/更新：`recordNo`, `values`; 可选 `ownerAccountId/deptId`; SDK 自动补 `moduleId` 到查询参数和写入 BO；详情/删除：`id` | `ModuleManageVO.id/code/status/moduleId/values` | 页面要求 `moduleId`；缺失时 `ContextGate` 展示空状态且不请求列表 | 支持新增、详情、编辑、删除；编辑只因后端已提供 PUT |
| 运行台-导出和流程 | `POST /api/modules/export-jobs`; `POST /api/flows/instances` | 导出：SDK 补 `tenantId/moduleId`，固定 `jobType=EXPORT`; 流程：`recordId/templateId/templateVersionId/assigneeId/taskName`，SDK 补 `tenantId/moduleId` | `ModuleManageVO` 或 `FlowManageVO` | 页面要求 `moduleId`，SDK 还要求 `tenantId` | 创建任务/发起流程，不是更新 |
| 流程工作台-模板 | `GET /api/flows/templates`; `POST /api/flows/templates`; `POST /api/flows/templates/publish` | 创建：`templateCode`, `templateName`; SDK 补 `tenantId/appId/moduleId`; 发布：`templateId`, `versionNo`, `graphJson` | `FlowManageVO.id/code/name/status/templateVersionId/graphJson` | 创建要求 `tenantId/appId/moduleId` | 支持创建和发布；无编辑 |
| 流程工作台-任务 | `GET /api/flows/tasks`; `PATCH /api/flows/tasks/handle?taskId={taskId}` | `taskId`, `actionType`; 可选 `commentText/transferTo` | `FlowManageVO.id/status/assigneeId/updatedAt` | header 自动注入 `X-Account-Id` 供后端校验 | 处理任务状态，不是编辑任务 |
| 上传中心-文件 | `GET /api/uploads/files`; `POST /api/uploads/files`; `DELETE /api/uploads/files?id={id}` | 登记：`storageConfigId`, `originalName`, `fileExt`, `mimeType`, `fileSize`, `storagePath`; SDK 补 `tenantId`; 删除：`id` | `UploadManageVO.id/fileId/name/type/size/status/storagePath` | 页面要求 `tenantId`；`storageConfigId` 必须由表单选择或输入，提交前校验 | 登记元数据和逻辑删除；无真实 multipart 上传/编辑 |
| 上传中心-附件 | `GET /api/uploads/attachments`; `POST /api/uploads/attachments` | 查询必须显式携带 `bizType`, `bizId`；创建：`fileId`, `bizType`, `bizId`，可选 `fieldCode` | `UploadManageVO.id/fileId/bizType/bizId/status` | 附件列表依赖具体业务对象；上传中心没有当前业务对象，因此不发起附件列表请求，只展示空状态和创建入口 | 创建引用；无编辑 |
| 上传中心-任务 | `POST /api/uploads/jobs` | `jobType`; 可选 `sourceFileId/requestJson`; SDK 补 `tenantId/moduleId` | `UploadManageVO.id/status/failureReason` | 页面要求 `tenantId/moduleId` | 创建任务；无编辑 |
| OpenAPI-客户端 | `GET /api/apps/openapi/clients`; `POST /api/apps/openapi/clients` | `clientCode`, `clientName`; 可选 `rateLimitPerMinute/expiredAt`; SDK 补 `tenantId/systemId` | `AppManageVO.id/clientId/code/name/status/expiredAt` | 页面要求 `tenantId/systemId` | 创建客户端；无编辑 |
| OpenAPI-凭证与授权 | `POST /api/apps/openapi/credentials`; `POST /api/apps/openapi/scopes`; `POST /api/apps/openapi/ip-whitelist`; `POST /api/apps/openapi/idempotents` | 凭证：`clientId/accessKey/secret/signAlgorithm`; scope：`clientId/appId/scopeCode/actions`; IP：`clientId/ipValue`; 幂等：`clientId/idempotentKey/requestHash/expiredAt` | `AppManageVO.id/code/type/value/detail` | 使用已创建的 `clientId/appId/moduleId` | 只创建，不编辑；密钥按创建动作展示 |
| OpenAPI-日志 | `GET /api/apps/openapi/access-logs` | 无 body | `AppManageVO.id/clientId/code/type/value/detail/status` | header 自动注入 | 只读 |

## UI 字段到 API 字段转换

- `permissionIdsText` -> `permissionIds: number[]`，用于 `RolePermissionAssignBO`。
- `roleIdsText` -> `roleIds: number[]`，用于 `AccountRoleAssignBO`。
- `optionsJson` -> `options: ModuleFieldOptionBO[]`，用于 `ModuleFieldSaveBO.options[]`；每项字段必须是 `optionValue/optionLabel/sortOrder`，不再使用 `optionCode/optionName`。
- UI switch -> `0/1`：`flowEnabled/importEnabled/exportEnabled/requiredFlag/uniqueFlag/listVisible/searchable/editable` 提交前显式转换为 Byte 标志位。
- `valuesJson` -> `values: Record<string, unknown>`，用于 `ModuleRecordSaveBO.values`。
- 页面中的 `appId/moduleId/tenantId/systemId` 优先由统一上下文补齐；只有 API 文档明确要求业务表单选择的 `appId/clientId/templateId/recordId` 等资源 ID 才在表单中出现。
