# 后端 API 文档

## 接口清单

本次后端只暴露各业务模块 `manage.controller` 中已实现接口，未暴露 MyBatis-Plus `base` CRUD Controller。

| 模块 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 平台 | POST | `/api/plat/auth/login` | 账号登录，MVP 校验账号存在且启用 |
| 平台 | GET/POST | `/api/plat/systems` | 查询/创建系统 |
| 平台 | PATCH | `/api/plat/systems/status?id={id}` | 变更系统状态 |
| 平台 | GET/POST | `/api/plat/tenants` | 查询/创建租户 |
| 平台 | PATCH | `/api/plat/tenants/status?id={id}` | 变更租户状态 |
| 平台 | GET/POST | `/api/plat/accounts` | 查询/创建账号 |
| 平台 | PATCH | `/api/plat/accounts/status?id={id}` | 变更账号状态 |
| 平台 | GET/POST | `/api/plat/roles` | 查询/创建角色 |
| 平台 | GET/POST | `/api/plat/permissions` | 查询/创建权限点 |
| 平台 | POST | `/api/plat/roles/permissions` | 替换角色权限 |
| 平台 | POST | `/api/plat/accounts/roles` | 替换账号角色 |
| 动态模块 | GET/POST | `/api/modules/models` | 查询/创建模块模型 |
| 动态模块 | PATCH | `/api/modules/models/status?id={id}` | 变更模块状态；前端 `setModelStatus` 只能提交 `DRAFT`、`PUBLISHED`、`DISABLED` |
| 动态模块 | GET/POST | `/api/modules/fields` | 查询/创建模块字段 |
| 动态模块 | GET/POST | `/api/modules/pages` | 查询/创建模块页面 |
| 动态模块 | GET/POST | `/api/modules/records` | 查询/创建运行态记录 |
| 动态模块 | GET | `/api/modules/records/detail?id={id}` | 查询记录详情 |
| 动态模块 | PUT | `/api/modules/records?id={id}` | 更新运行态记录 |
| 动态模块 | DELETE | `/api/modules/records?id={id}` | 删除运行态记录 |
| 动态模块 | POST | `/api/modules/export-jobs` | 创建模块导出任务 |
| 流程 | GET/POST | `/api/flows/templates` | 查询/创建流程模板 |
| 流程 | POST | `/api/flows/templates/publish` | 发布流程模板版本 |
| 流程 | POST | `/api/flows/instances` | 发起流程实例并创建首个待办 |
| 流程 | GET | `/api/flows/tasks` | 查询流程任务 |
| 流程 | PATCH | `/api/flows/tasks/handle?taskId={taskId}` | 处理流程任务 |
| 上传 | GET/POST | `/api/uploads/files` | 查询/登记文件元数据 |
| 上传 | DELETE | `/api/uploads/files?id={id}` | 逻辑删除文件 |
| 上传 | GET/POST | `/api/uploads/attachments` | 查询/创建附件引用 |
| 上传 | POST | `/api/uploads/jobs` | 创建导入导出任务 |
| 应用/OpenAPI | GET/POST | `/api/apps` | 查询/创建应用 |
| 应用/OpenAPI | POST | `/api/apps/publish` | 发布应用版本 |
| 应用/OpenAPI | GET/POST | `/api/apps/openapi/clients` | 查询/创建 OpenAPI 客户端 |
| 应用/OpenAPI | POST | `/api/apps/openapi/credentials` | 创建 OpenAPI 凭证 |
| 应用/OpenAPI | POST | `/api/apps/openapi/scopes` | 创建 OpenAPI scope |
| 应用/OpenAPI | POST | `/api/apps/openapi/ip-whitelist` | 创建 OpenAPI IP 白名单 |
| 应用/OpenAPI | POST | `/api/apps/openapi/idempotents` | 创建 OpenAPI 幂等记录 |
| 应用/OpenAPI | GET | `/api/apps/openapi/access-logs` | 查询 OpenAPI 访问日志 |

## 请求方法与路径

所有接口返回 `ApiResult<T>`：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {}
}
```

分页能力本次未实现，列表接口返回当前条件下的数组；前端需要分页时应等待后端补分页语义后再展示分页控件。

## 入参

公共请求头：

| Header | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Account-Id` | Long | 否 | 当前账号 ID，用于审计字段和任务处理人校验 |
| `X-Tenant-Id` | Long | 否 | 当前租户上下文 |
| `X-System-Id` | Long | 否 | 当前系统上下文 |

主要 Body 入参：

| DTO/BO | 字段 |
| --- | --- |
| `PlatformLoginDTO` | `username`, `password` |
| `PlatformSystemSaveBO` | `systemCode`, `systemName`, `description` |
| `PlatformTenantSaveBO` | `tenantCode`, `tenantName`, `adminAccountId`, `expireAt`, `configJson` |
| `PlatformAccountSaveBO` | `username`, `displayName`, `mobile`, `email`, `password` |
| `PlatformRoleSaveBO` | `tenantId`, `systemId`, `appId`, `roleCode`, `roleName`, `roleType` |
| `PlatformPermissionSaveBO` | `tenantId`, `systemId`, `appId`, `moduleId`, `permissionCode`, `permissionName`, `permissionType`, `resourcePath` |
| `RolePermissionAssignBO` | `roleId`, `permissionIds` |
| `AccountRoleAssignBO` | `accountId`, `tenantId`, `systemId`, `roleIds` |
| `ModuleModelSaveBO` | `tenantId`, `systemId`, `appId`, `moduleCode`, `moduleName`, `dataScopeType`, `flowEnabled`, `importEnabled`, `exportEnabled` |
| `ModuleStatusBO` | `status`；必填，合法值为 `DRAFT`、`PUBLISHED`、`DISABLED` |
| `ModuleFieldSaveBO` | `moduleId`, `fieldCode`, `fieldName`, `fieldType`, `requiredFlag`, `uniqueFlag`, `listVisible`, `searchable`, `editable`, `defaultValue`, `validationJson`, `sortOrder`, `options[]` |
| `ModulePageSaveBO` | `moduleId`, `pageCode`, `pageName`, `pageType`, `layoutJson`, `buttonJson` |
| `ModuleRecordSaveBO` | `moduleId`, `recordNo`, `ownerAccountId`, `deptId`, `values`；`recordNo` 创建时可为空由后端自动生成，编辑时可写，传入后不能为空、长度不超过 64，并按同租户同系统同模型唯一校验后更新 |
| `FlowTemplateSaveBO` | `tenantId`, `appId`, `moduleId`, `templateCode`, `templateName` |
| `FlowTemplatePublishBO` | `templateId`, `versionNo`, `graphJson` |
| `FlowStartBO` | `tenantId`, `moduleId`, `recordId`, `templateId`, `templateVersionId`, `assigneeId`, `taskName` |
| `FlowTaskHandleBO` | `actionType`, `commentText`, `transferTo`；`actionType` 合法值为 `APPROVE`、`REJECT`、`TRANSFER`、`CANCEL` |
| `UploadFileCreateBO` | `tenantId`, `storageConfigId`, `originalName`, `fileExt`, `mimeType`, `fileSize`, `storagePath`, `sha256`；`storageConfigId` 必填，后端不按默认存储配置兜底 |
| `AttachmentCreateBO` | `fileId`, `bizType`, `bizId`, `fieldCode` |
| `UploadImportExportJobBO` | `tenantId`, `moduleId`, `jobType`, `sourceFileId`, `requestJson` |
| `AppApplicationSaveBO` | `tenantId`, `systemId`, `appCode`, `appName`, `visibleScope`, `description` |
| `AppPublishBO` | `appId`, `versionNo`, `versionName`, `snapshotJson` |
| `OpenApiClientSaveBO` | `tenantId`, `systemId`, `clientCode`, `clientName`, `rateLimitPerMinute`, `expiredAt` |
| `OpenApiCredentialCreateBO` | `clientId`, `accessKey`, `secret`, `signAlgorithm` |
| `OpenApiScopeSaveBO` | `clientId`, `appId`, `moduleId`, `scopeCode`, `actions` |
| `OpenApiIpWhitelistSaveBO` | `clientId`, `ipValue` |
| `OpenApiIdempotentSaveBO` | `clientId`, `idempotentKey`, `requestHash`, `responseHash`, `expiredAt` |

关键枚举与字段约束：

| 契约点 | 合法值或规则 | 适用接口/字段 |
| --- | --- | --- |
| 模块状态 | `DRAFT`、`PUBLISHED`、`DISABLED` | `PATCH /api/modules/models/status?id={id}` 的 `ModuleStatusBO.status`；前端 `setModelStatus` 不接受 `ENABLED` |
| 权限类型 `PermissionType` | `MENU`、`BUTTON`、`API`、`FIELD`、`DATA_SCOPE`；空值默认 `MENU`，未知非空值返回 `PLAT_PERMISSION_TYPE_INVALID` | `PlatformPermissionSaveBO.permissionType`、`un_platt_permission.permission_type` |
| 流程动作 `FlowActionType` | `APPROVE`、`REJECT`、`TRANSFER`、`CANCEL`；空值返回 `FLOW_PARAM_REQUIRED`，未知值返回 `FLOW_ACTION_TYPE_INVALID`，`CANCEL` 会将任务与实例状态置为 `CANCELED` | `FlowTaskHandleBO.actionType` |
| 文件存储配置 | `storageConfigId` 必填，必须指向已存在的存储配置 | `UploadFileCreateBO.storageConfigId`、`un_upload_file.storage_config_id`；当前后端不自动选择默认配置 |

## 出参

| VO | 适用接口 | 主要字段 |
| --- | --- | --- |
| `PlatformManageVO` | 平台接口 | `id`, `code`, `name`, `status`, `tenantId`, `systemId`, `appId`, `moduleId`, `type`, `resourcePath`, `mobile`, `email`, `roleIds`, `permissionIds`, `createdAt`, `updatedAt` |
| `ModuleManageVO` | 动态模块接口 | `id`, `tenantId`, `systemId`, `appId`, `moduleId`, `code`, `name`, `type`, `status`, `configJson`, `values`, `createdAt`, `updatedAt` |
| `FlowManageVO` | 流程接口 | `id`, `tenantId`, `appId`, `moduleId`, `recordId`, `code`, `name`, `status`, `templateId`, `templateVersionId`, `assigneeId`, `graphJson`, `createdAt`, `updatedAt` |
| `UploadManageVO` | 上传接口 | `id`, `tenantId`, `fileId`, `moduleId`, `name`, `type`, `size`, `storagePath`, `status`, `bizType`, `bizId`, `failureReason`, `createdAt`, `updatedAt` |
| `AppManageVO` | 应用/OpenAPI 接口 | `id`, `tenantId`, `systemId`, `clientId`, `appId`, `moduleId`, `code`, `name`, `status`, `type`, `versionNo`, `value`, `detail`, `expiredAt`, `createdAt`, `updatedAt` |

## 错误码

| 错误码 | 说明 |
| --- | --- |
| `SUCCESS` | 成功 |
| `SYSTEM_ERROR` | 未预期异常 |
| `PLAT_PARAM_REQUIRED` | 平台参数缺失 |
| `PLAT_DATA_NOT_FOUND` | 平台数据不存在 |
| `PLAT_STATUS_INVALID` | 平台状态不合法 |
| `PLAT_PERMISSION_TYPE_INVALID` | 平台权限类型不合法 |
| `PLAT_LOGIN_FAILED` | 账号不存在或未启用 |
| `MODULE_PARAM_REQUIRED` | 模块参数缺失 |
| `MODULE_DATA_NOT_FOUND` | 模块数据不存在 |
| `MODULE_FIELD_INVALID` | 字段配置或字段值不合法 |
| `MODULE_RECORD_NO_DUPLICATE` | 记录编号在同租户同系统同模型下已存在 |
| `MODULE_STATUS_INVALID` | 模块状态不合法 |
| `FLOW_PARAM_REQUIRED` | 流程参数缺失 |
| `FLOW_DATA_NOT_FOUND` | 流程数据不存在 |
| `FLOW_STATUS_INVALID` | 流程状态不允许当前操作 |
| `FLOW_ACTION_TYPE_INVALID` | 流程任务处理动作不合法 |
| `FLOW_TASK_ASSIGNEE_INVALID` | 当前账号无权处理该任务 |
| `UPLOAD_PARAM_REQUIRED` | 上传参数缺失 |
| `UPLOAD_DATA_NOT_FOUND` | 文件或附件不存在 |
| `UPLOAD_STATUS_INVALID` | 文件状态不允许当前操作 |
| `APP_PARAM_REQUIRED` | 应用或 OpenAPI 参数缺失 |
| `APP_DATA_NOT_FOUND` | 应用或 OpenAPI 数据不存在 |
| `APP_STATUS_INVALID` | 应用或 OpenAPI 状态不允许当前操作 |

## 鉴权与权限说明

本次实现按 `docs/service_info.md` 中公共服务配置启动，认证上下文由 `examine-web` 从 `X-Account-Id`、`X-Tenant-Id`、`X-System-Id` 请求头装配到 `AuthContextHolder`。MVP 阶段已用于审计字段补齐和流程任务处理人校验。

权限边界：

- 平台角色、权限点、账号角色关系已落库，接口支持配置授权关系。
- 动态模块运行态记录已按模块字段定义写入，复杂字段权限、数据范围过滤和菜单按钮级拦截尚未展开。
- 流程任务处理会校验当前账号与任务 `assigneeId`；复杂候选人、代理、会签和流程图解析尚未展开。
- OpenAPI 已支持客户端、凭证、scope、白名单、幂等记录和访问日志管理；真实签名验签、限流拦截和开放业务网关不在本次接口内。

## 前后端字段映射说明

| 前端概念 | API 字段 | 数据表字段 |
| --- | --- | --- |
| 系统编码/名称 | `code`/`name` | `un_platt_system.system_code/system_name` |
| 租户编码/名称 | `code`/`name` | `un_platt_tenant.tenant_code/tenant_name` |
| 账号/显示名 | `code`/`name` | `un_platt_account.username/display_name` |
| 角色编码/名称 | `code`/`name` | `un_platt_role.role_code/role_name` |
| 权限编码/资源 | `code`/`resourcePath` | `un_platt_permission.permission_code/resource_path` |
| 应用编码/名称 | `code`/`name` | `un_app_application.app_code/app_name` |
| 模块编码/名称 | `code`/`name` | `un_module_model.module_code/module_name` |
| 字段编码/名称/类型 | `code`/`name`/`type` | `un_module_field.field_code/field_name/field_type` |
| 页面编码/布局 | `code`/`configJson` | `un_module_page.page_code/layout_json` |
| 记录编号 | `recordNo` 入参 / `code` 出参 | `un_module_record.record_no`，创建和编辑均可写；编辑时后端按同租户同系统同模型唯一校验并更新，刷新后以 `code` 回显最新编号 |
| 记录字段值 | `values[fieldCode]` | `un_module_record_value.field_code + typed value` |
| 流程模板编码/名称 | `code`/`name` | `un_flow_template.template_code/template_name` |
| 流程版本快照 | `graphJson` | `un_flow_template_version.graph_json` |
| 流程任务处理人 | `assigneeId` | `un_flow_task.assignee_id` |
| 文件名称/路径 | `name`/`storagePath` | `un_upload_file.original_name/storage_path` |
| 附件业务对象 | `bizType`/`bizId` | `un_upload_attachment.biz_type/biz_id` |
| OpenAPI 客户端 | `code`/`name` | `un_openapi_client.client_code/client_name` |
| OpenAPI 凭证 | `code`/`type` | `un_openapi_credential.access_key/sign_algorithm` |
| OpenAPI scope | `code`/`type` | `un_openapi_scope.scope_code/actions` |
| OpenAPI 白名单 | `value` | `un_openapi_ip_whitelist.ip_value` |
| OpenAPI 幂等 | `code`/`value` | `un_openapi_idempotent.idempotent_key/request_hash` |

## MVP 边界

- 登录接口当前只校验账号存在且状态为 `ENABLED`，未签发 token，密码摘要和正式认证链路需后续接入。
- 上传接口当前登记文件元数据和附件引用，不接收 multipart 二进制内容，不提供真实预览/下载流。
- 应用和流程发布要求调用方显式传入 `versionNo`，由数据库唯一索引兜底避免重复版本。
- 列表接口当前未实现分页，前端不得伪造分页或编辑能力。
