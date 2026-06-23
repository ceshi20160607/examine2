# FE-006 应用模块字段配置页面契约证据

> 本文件只记录 FE-006 页面级证据，FE-012 汇总到 `frontend/docs/api-contract-map.md`。

## 页面基本信息

| 项 | 内容 |
| --- | --- |
| 任务 ID | FE-006 |
| 页面/模块 | 应用、模块、字段、列表/表单/详情 schema、运行菜单、动作、发布配置 |
| 路由 | `/systems/:systemId/apps`、`/systems/:systemId/apps/:appId/modules`、`/systems/:systemId/modules/:moduleId/fields`、`/systems/:systemId/modules/:moduleId/ui` |
| 入口 | FE-002 路由记录：`apps.list`、`modules.list`、`modules.fields`、`modules.ui` |
| 依赖上下文 | `systemId`、`tenantId`、`memberId`；`appId` 与 `moduleId` 来自路由或当前选择项 |
| SDK 引用 | `frontend/src/pages/module-config/moduleConfigPageModel.ts` 只通过 `frontend/src/api` `ApiClient.call()` 与 stores 调用 |

## API 映射证据

| API ID | 方法 | 路径 | 触发动作 | 必填 path 入参 | 必填 query 入参 | 必填 body 入参 | 必填 header | 响应字段 | 枚举/状态 | 错误码 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| APP-001 | GET | `/api/v1/systems/{systemId}/apps` | 加载应用列表 | `systemId` | 可选 `pageNo`、`pageSize`、`keyword` | 无 | Authorization / X-Tenant-Id / X-Request-Id | `records[].appId`、`name`、`code`、`status`、`moduleCount`、`availableActions` | `appStatus` | `MODULE_APP_NOT_FOUND`、`PERM_DENIED`、`SYS_CONTEXT_REQUIRED` | 空列表展示应用空态。 |
| APP-002 | POST | `/api/v1/systems/{systemId}/apps` | 新建应用 | `systemId` | 无 | `name`、`code`；可选 `icon`、`description`、`status` | Authorization / X-Tenant-Id / X-Request-Id / X-Idempotency-Key | `appId`、`name`、`code`、`status`、`version` | `appStatus` | `MODULE_APP_CODE_DUPLICATED`、`PERM_DENIED` | 页面模型生成幂等键，不写系统/租户字段。 |
| APP-003 | GET | `/api/v1/systems/{systemId}/apps/{appId}` | 查看应用详情 | `systemId`、`appId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | `appId`、`systemId`、`tenantId`、`name`、`code`、`status`、`moduleCount`、`publishedVersion`、`version` | `appStatus` | `MODULE_APP_NOT_FOUND`、`PERM_DENIED` | 详情只读系统上下文。 |
| APP-004 | PUT | `/api/v1/systems/{systemId}/apps/{appId}` | 保存应用基础信息 | `systemId`、`appId` | 无 | `name`、`code`；可选 `icon`、`description`、`status`、`version` | Authorization / X-Tenant-Id / X-Request-Id | `AppDetailVO` | `appStatus` | `MODULE_APP_STATUS_INVALID`、`MODULE_CONFIG_VERSION_CONFLICT`、`PERM_DENIED` | 不伪造导入或批量能力。 |
| APP-005 | PATCH | `/api/v1/systems/{systemId}/apps/{appId}/status` | 启停/归档应用 | `systemId`、`appId` | 无 | `targetStatus`；可选 `reason`、`version` | Authorization / X-Tenant-Id / X-Request-Id | `AppDetailVO` | `DRAFT`、`ENABLED`、`DISABLED`、`ARCHIVED` | `MODULE_APP_STATUS_INVALID`、`PERM_DENIED` | 归档后编辑按钮禁用。 |
| APP-006 | POST | `/api/v1/systems/{systemId}/apps/{appId}/copy` | 复制应用 | `systemId`、`appId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | `AppVO` | `appStatus` | `MODULE_APP_NOT_FOUND`、`PERM_DENIED` | 后端 P3 未实现，前端标记为 ENH，不进入 MVP 路由和页面模型调用。 |
| APP-007 | GET | `/api/v1/systems/{systemId}/apps/templates` | 读取应用模板 | `systemId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | 模板数组 | `appStatus` | `PERM_DENIED` | 后端 P3 未实现，前端标记为 ENH，不进入 MVP 路由和页面模型调用。 |
| MOD-001 | GET | `/api/v1/systems/{systemId}/apps/{appId}/modules` | 加载模块列表 | `systemId`、`appId` | 可选 `keyword/status` | 无 | Authorization / X-Tenant-Id / X-Request-Id | `moduleId`、`appId`、`name`、`code`、`status`、`fieldCount`、`pageSchemaCount`、`publishedVersion` | `moduleStatus` | `MODULE_APP_NOT_FOUND`、`PERM_DENIED` | 空列表展示模块空态。 |
| MOD-002 | POST | `/api/v1/systems/{systemId}/apps/{appId}/modules` | 新建模块 | `systemId`、`appId` | 无 | `name`、`code` | Authorization / X-Tenant-Id / X-Request-Id / X-Idempotency-Key | `ModuleDetailVO` | `moduleStatus` | `MODULE_CODE_DUPLICATED`、`PERM_DENIED` | 创建后进入字段配置。 |
| MOD-003 | GET | `/api/v1/systems/{systemId}/modules/{moduleId}` | 查看模块详情 | `systemId`、`moduleId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | `moduleId`、`name`、`code`、`status`、`publishedVersion`、`flowBindingId`、`fieldCount`、`pageSchemaCount`、`version` | `moduleStatus` | `MODULE_NOT_FOUND`、`PERM_DENIED` | 详情用于发布前摘要。 |
| MOD-004 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}` | 保存模块基础信息 | `systemId`、`moduleId` | 无 | `name`、`code`；可选 `description`、`version` | Authorization / X-Tenant-Id / X-Request-Id | `ModuleDetailVO` | `moduleStatus` | `MODULE_CODE_DUPLICATED`、`MODULE_CONFIG_VERSION_CONFLICT`、`PERM_DENIED` | 已归档模块禁用编辑。 |
| MOD-005 | PATCH | `/api/v1/systems/{systemId}/modules/{moduleId}/status` | 停用/归档模块 | `systemId`、`moduleId` | 无 | `targetStatus`、可选 `reason`、`version` | Authorization / X-Tenant-Id / X-Request-Id | `ModuleDetailVO` | `DRAFT`、`PUBLISHED`、`DISABLED`、`ARCHIVED` | `MODULE_NOT_FOUND`、`MODULE_CONFIG_VERSION_CONFLICT`、`PERM_DENIED` | `PUBLISHED` 只能由发布接口生成。 |
| MOD-006 | POST | `/api/v1/systems/{systemId}/modules/{moduleId}/publish-check` | 发布检查 | `systemId`、`moduleId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id / X-Idempotency-Key | `passed`、`nextVersionNo`、`issues[]`、`checkedAt` | 无 | `MODULE_PUBLISH_CHECK_FAILED`、`MODULE_PAGE_FIELD_MISSING` | `issues[].targetType/targetCode/targetId` 用于定位配置项。 |
| MOD-007 | POST | `/api/v1/systems/{systemId}/modules/{moduleId}/publish` | 发布模块 | `systemId`、`moduleId` | 无 | `moduleVersion`，可选 `publishRemark` | Authorization / X-Tenant-Id / X-Request-Id | `publishVersionId`、`versionNo`、`publishStatus`、`publishedAt`、`checkResult` | `PUBLISHED` | `MODULE_PUBLISH_CHECK_FAILED`、`MODULE_CONFIG_VERSION_CONFLICT`、`PERM_DENIED` | 成功后尝试刷新 RUN-001/RUN-002 运行菜单和 schema 状态；刷新失败不伪造发布失败。 |
| FIELD-001 | GET | `/api/v1/systems/{systemId}/modules/{moduleId}/fields` | 加载字段列表 | `systemId`、`moduleId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | `fieldId`、`name`、`code`、`fieldType`、`required`、`unique`、`status`、`version`、`options` | `fieldStatus`、`DynamicFieldType` | `MODULE_NOT_FOUND`、`PERM_DENIED` | 保存字段后刷新字段列表和 UI 预览。 |
| FIELD-002 | POST | `/api/v1/systems/{systemId}/modules/{moduleId}/fields` | 新建字段 | `systemId`、`moduleId` | 无 | `name`、`code`、`fieldType`；可选 `required`、`unique`、`indexed`、`defaultValue`、`options`、`dictTypeId`、`relationConfig`、`subTableConfig`、`serialConfig`、`validation`、`displayConfig`、`status`、`sortOrder` | Authorization / X-Tenant-Id / X-Request-Id / X-Idempotency-Key | `FieldVO` | `fieldStatus`、`DynamicFieldType` | `FIELD_CODE_DUPLICATED`、`FIELD_TYPE_UNSUPPORTED`、`FIELD_RELATION_INVALID`、`FIELD_SERIAL_RULE_INVALID` | 字段编码重复先本地提示，后端仍兜底。 |
| FIELD-003 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/fields/{fieldId}` | 保存字段 | `systemId`、`moduleId`、`fieldId` | 无 | 同 FIELD-002；可选 `version` | Authorization / X-Tenant-Id / X-Request-Id | `FieldVO` | `fieldStatus`、`DynamicFieldType` | `FIELD_TYPE_UNSUPPORTED`、`FIELD_DELETE_HAS_DATA`、`PERM_FIELD_WRITE_DENIED` | 字段类型切换时保留契约字段并重新校验 options/relation/subTable。 |
| FIELD-004 | PATCH | `/api/v1/systems/{systemId}/modules/{moduleId}/fields/{fieldId}/status` | 启停/删除字段 | `systemId`、`moduleId`、`fieldId` | 无 | `targetStatus`、可选 `reason`、`version` | Authorization / X-Tenant-Id / X-Request-Id | `FieldVO` | `DRAFT`、`ENABLED`、`DISABLED`、`DELETED` | `FIELD_DELETE_HAS_DATA`、`MODULE_PAGE_FIELD_MISSING`、`PERM_DENIED` | 被 schema 引用字段禁用删除入口。 |
| FIELD-005 | GET | `/api/v1/systems/{systemId}/field-types` | 加载字段类型 | `systemId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | `code`、`name`、`uniqueSupported`、`optionSupported`、`dictSupported` | `DynamicFieldType` | `FIELD_TYPE_UNSUPPORTED`、`PERM_DENIED` | 页面模型也引用 `DYNAMIC_FIELD_TYPES` 兜底展示。 |
| UI-001 | GET | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/list-views` | 加载列表 schema | `systemId`、`moduleId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | `schemaVersion`、`listSchema`、`permissionHints` | 无 | `MODULE_PAGE_FIELD_MISSING`、`PERM_DENIED` | 无 schema 时展示列表配置空态。 |
| UI-002 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/list-views/default` | 保存列表 schema | `systemId`、`moduleId` | 无 | `schema`，可选 `draftVersion` | Authorization / X-Tenant-Id / X-Request-Id | `PageSchemaVO` | 无 | `MODULE_PAGE_FIELD_MISSING`、`MODULE_CONFIG_VERSION_CONFLICT` | 只提交当前模块字段编码/ID。 |
| UI-003 | GET | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/forms/default` | 加载表单 schema | `systemId`、`moduleId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | `formSchema`、`permissionHints` | 无 | `PERM_DENIED` | 空 schema 展示表单配置空态。 |
| UI-004 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/forms/default` | 保存表单 schema | `systemId`、`moduleId` | 无 | `schema`，可选 `draftVersion` | Authorization / X-Tenant-Id / X-Request-Id | `PageSchemaVO` | 无 | `MODULE_PAGE_FIELD_MISSING`、`PERM_FIELD_WRITE_DENIED` | 表单必填只引用字段定义，不写动态记录值。 |
| UI-005 | GET | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/details/default` | 加载详情 schema | `systemId`、`moduleId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | `detailSchema`、`permissionHints` | 无 | `PERM_DENIED` | 空 schema 展示详情配置空态。 |
| UI-006 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/details/default` | 保存详情 schema | `systemId`、`moduleId` | 无 | `schema`，可选 `draftVersion` | Authorization / X-Tenant-Id / X-Request-Id | `PageSchemaVO` | 无 | `MODULE_PAGE_FIELD_MISSING`、`MODULE_CONFIG_VERSION_CONFLICT` | 无效字段由本地和后端双重校验。 |
| UI-007 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/menu` | 保存运行菜单 | `systemId`、`moduleId` | 无 | `code`、`name`；可选 `menuParentId`、`routePath`、`icon`、`visible`、`enabled`、`sortOrder` | Authorization / X-Tenant-Id / X-Request-Id | `MenuConfigVO` | 无 | `MODULE_MENU_CODE_DUPLICATED`、`PERM_DENIED` | 发布成功后通过 RUN-001 刷新可见菜单。 |
| UI-008 | PUT | `/api/v1/systems/{systemId}/modules/{moduleId}/ui/actions` | 保存动作按钮 | `systemId`、`moduleId` | 无 | `actions[].actionCode`、`actions[].actionName`、`actions[].actionType`、`actions[].enabled` | Authorization / X-Tenant-Id / X-Request-Id / X-Idempotency-Key | `ActionConfigVO[]` | 无 | `PERM_FIELD_WRITE_DENIED`、`MODULE_CONFIG_VERSION_CONFLICT` | 使用 `AvailableAction` 语义，不散落魔法按钮。 |
| RUN-001 | GET | `/api/v1/systems/{systemId}/runtime/menus` | 发布成功后刷新运行菜单状态 | `systemId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | 运行菜单树/菜单编码 | 无 | `PERM_DENIED`、`SYS_CONTEXT_REQUIRED` | 仅用于发布后刷新状态，不替代 FE-008 运行台实现。 |
| RUN-002 | GET | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/schema` | 发布成功后刷新运行 schema 状态 | `systemId`、`moduleId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | `moduleId`、`moduleCode`、`publishedVersionId`、`listSchema`、`formSchema`、`detailSchema`、`fieldDefinitions`、`availableActions` | `moduleStatus`、`fieldStatus` | `MODULE_NOT_FOUND`、`PERM_DENIED` | 只刷新当前模块缓存状态。 |

## 字段映射

| UI 字段 | API 字段 | 来源 | 是否前端可写 | 提交转换规则 | 回显/空值规则 |
| --- | --- | --- | --- | --- | --- |
| 当前系统 | `systemId` | 路由和 `systemContextStore` | 否 | `systemContext.toPathParams()` 写入 path param | 缺失时阻止请求并展示 `SYS_CONTEXT_REQUIRED` |
| 当前租户 | `tenantId` / `X-Tenant-Id` | `systemContextStore` | 否 | `systemContext.toTenantHeader()` 交给统一请求上下文 | 多租户缺失时阻止请求 |
| 应用基础信息 | `name`、`code`、`icon`、`description`、`status` | 应用表单 | 是 | 创建/更新只提交应用 BO，系统/租户不进 body | `status` 默认由后端或页面状态展示 |
| 模块基础信息 | `name`、`code`、`description` | 模块表单 | 是 | `appId` 只走 path；`moduleId` 只走 path | 无模块时进入模块空态 |
| 字段名称/编码 | `name`、`code` / 回显同名字段 | 字段表单 / FIELD-001 | 是 | 提交 BO 使用 `name/code`；schema 引用使用 `fieldCode` 或 `fieldId`，其中 `fieldCode` 对应后端字段 `code` | 编码重复由 `duplicateCodes()` 本地提示并由后端兜底 |
| 字段类型 | `fieldType` | `DYNAMIC_FIELD_TYPES` / FIELD-005 | 是 | 只允许冻结枚举；切换类型后保留对应 config 字段 | 不支持唯一的类型禁用 `unique` |
| 必填/唯一/默认值 | `required`、`unique`、`defaultValue`、`uniqueConstraints` | 字段表单 | 是 | 唯一类型按 API 唯一规则提交 | 空值按 API 规则跳过唯一校验，必填优先 |
| 字典配置 | `dictTypeId`、`options` | 字段表单 / 字典页选择 | 是 | SELECT/MULTI_SELECT 绑定字典或 options | 字典缺失时发布检查定位字段 |
| 关联/子表配置 | `relationConfig`、`subTableConfig` | 字段表单 | 是 | RELATION/SUB_TABLE 类型才填写 | 非对应字段类型不展示配置入口 |
| 字段权限 | `fieldPermissions`、`fieldVisibility`、`fieldWritable` | 字段配置 / UI schema | 是，权限门控 | 使用 `FieldPermission` 契约字段 | `writable=false` 展示 `readonlyReason` |
| 列表 schema | `columns`、`filters`、`sorters` | 列表配置 | 是 | 只提交当前字段集合中的 `fieldCode/fieldId` | 无 columns 展示配置空态 |
| 表单 schema | `formSections`、`fieldWritable` | 表单配置 | 是 | section 内 fields 引用当前模块字段 | 无 section 展示配置空态 |
| 详情 schema | `detailBlocks`、`fieldVisibility` | 详情配置 | 是 | block 内 fields 引用当前模块字段 | 无 block 展示配置空态 |
| 菜单配置 | `menuCode`、`menuName`、`menuParentId`、`visible`、`sortOrder` | 菜单表单 | 是 | `moduleId` 只走 path | 菜单编码重复显示后端错误 |
| 动作配置 | `actionCodes`、`actions[]` | 动作配置 | 是 | 使用 `AvailableAction` 类字段：`actionCode/label/visible/enabled/requiredPermission` | 无动作展示动作空态 |
| 发布检查定位 | `issues[].targetType/targetCode/targetId` | MOD-006 响应 | 否 | `locatePublishIssue()` 转为配置定位字符串 | `level=ERROR` 的 issue 阻止发布 |
| requestId | `requestId` / `X-Request-Id` | ApiResponse / ApiErrorResponse | 否 | API client context 可传，页面错误态展示 | 错误态必须展示，可复制审计 |

## 枚举、状态与错误码

| 类型 | 契约来源 | 页面使用位置 | 展示/禁用规则 |
| --- | --- | --- | --- |
| 字段类型 | `frontend/src/api/enums.ts` `DYNAMIC_FIELD_TYPES` | 字段设计器、字段类型切换、schema 预览 | 未声明类型不展示；`TEXTAREA/MULTI_SELECT/ATTACHMENT/IMAGE/SUB_TABLE/ADDRESS/TAG/JSON` 禁用唯一开关 |
| 应用状态 | `appStatus` | 应用列表、应用详情、状态按钮 | `ARCHIVED` 禁用编辑和新增模块 |
| 模块状态 | `moduleStatus` | 模块列表、发布结果、运行 schema 刷新 | `PUBLISHED` 只能由 MOD-007 产生；`ARCHIVED` 禁用编辑 |
| 字段状态 | `fieldStatus` | 字段列表、字段状态按钮、schema 校验 | `DELETED` 不允许被 schema 引用 |
| 发布状态 | `publishStatus` | 发布结果 | `PUBLISHED` 后刷新 RUN-001/RUN-002 |
| 错误码 | `frontend/src/api/errorCodes.ts` | 所有模型错误经 `errorStore.capture()` | 展示 `code`、`message`、`requestId`；`retryable=true` 才允许重试 |

## 权限禁用态

| 控件/动作 | 权限来源 | 禁用字段 | 禁用展示 | 后端兜底错误码 |
| --- | --- | --- | --- | --- |
| 应用查看/新建/编辑/状态 | `APP_VIEW`、`APP_CREATE`、`APP_EDIT`、`APP_STATUS` | `PageActionState.enabled=false` | `PERM_DENIED` 或状态原因 | `PERM_DENIED` |
| 模块查看/新建/编辑/发布 | `MODULE_VIEW`、`MODULE_CREATE`、`MODULE_EDIT`、`MODULE_PUBLISH` | `PageActionState.enabled=false` | 缺权限、已归档、发布检查未通过 | `MODULE_PUBLISH_CHECK_FAILED`、`PERM_DENIED` |
| 字段查看/新建/编辑/状态 | `FIELD_VIEW`、`FIELD_CREATE`、`FIELD_EDIT`、`FIELD_STATUS` | `PageActionState.enabled=false`、`FieldPermission.writable=false` | `readonlyReason` / `PERM_FIELD_WRITE_DENIED` | `FIELD_DELETE_HAS_DATA`、`PERM_FIELD_WRITE_DENIED` |
| 页面 schema 查看/保存 | `PAGE_VIEW`、`PAGE_EDIT` | `PageActionState.enabled=false` | 缺权限或无效字段引用 | `MODULE_PAGE_FIELD_MISSING`、`PERM_DENIED` |
| 菜单保存 | `MENU_EDIT` | `PageActionState.enabled=false` | 缺权限或编码冲突 | `MODULE_MENU_CODE_DUPLICATED`、`PERM_DENIED` |
| 动作保存 | `ACTION_EDIT` | `PageActionState.enabled=false` | 缺权限或动作配置冲突 | `PERM_FIELD_WRITE_DENIED`、`MODULE_CONFIG_VERSION_CONFLICT` |

## 空态与错误态

| 场景 | 判定条件 | 展示内容 | requestId 展示 | 重试动作 |
| --- | --- | --- | --- | --- |
| 缺少系统/租户/成员上下文 | `systemContext.validate({system:true, tenant:true, member:true})` 返回缺失项 | `SYS_CONTEXT_REQUIRED` 和缺失键 | 本地阻断不需要 | 重新进入系统或切换租户 |
| 上下文停用 | `systemContext.status === disabled` | `SYS_DISABLED`、`SYS_TENANT_DISABLED` 或 `SYS_MEMBER_DISABLED` | 后端兜底错误必须展示 | 切换系统/租户或联系管理员 |
| 应用/模块/字段/schema 空列表 | API 返回空数组或 `PageResult.records=[]` | 页面对应空态和创建入口 | 不需要 | 有权限时创建 |
| 字段编码重复 | 本地 `duplicateCodes()` 命中，或后端返回 `FIELD_CODE_DUPLICATED` | 重复编码定位到字段行 | 后端错误必须展示 | 修改编码后重试 |
| 字段类型切换不合法 | 类型不在 `DYNAMIC_FIELD_TYPES` 或唯一规则不支持 | 禁用不兼容配置项 | 不需要 | 切换到支持类型 |
| 页面引用无效字段 | `validate*Schema()` 返回 `missingFieldCodes`，或后端 `MODULE_PAGE_FIELD_MISSING` | 定位到 list/form/detail schema 字段 | 后端错误必须展示 | 删除无效字段引用 |
| 发布检查失败 | MOD-006 `passed=false` 或错误码 `MODULE_PUBLISH_CHECK_FAILED` | `issues[]`，用 `targetType/targetCode/targetId` 定位 | 必须展示 MOD-006 requestId | 修复配置后重新检查 |
| 发布成功刷新失败 | MOD-007 成功但 RUN-001/RUN-002 刷新失败 | 展示发布成功和刷新失败 requestId | 必须展示失败请求 requestId | 允许单独刷新运行菜单/schema |
| API 错误 | `ApiClientError` | `code + message + requestId` | 必须展示 | `retryable=true` 时允许 |

## requestId 与审计

- 所有 API 调用均通过 `ApiClient.call()`，成功响应写入 `PageRequestState.requestId`。
- 所有 API 错误通过 `errorStore.capture()` 归一化，页面必须展示 `code`、`message`、`requestId`。
- 写接口幂等：`APP-002`、`MOD-002`、`MOD-006`、`FIELD-002`、`UI-008` 显式传入 `X-Idempotency-Key`；`MOD-007` 不在冻结幂等清单内，页面不额外伪造幂等键。
- 发布成功后读取 RUN-001/RUN-002 的 requestId，作为运行菜单和 schema 缓存刷新证据。

## 无旁路请求检查

| 检查项 | 结论 |
| --- | --- |
| 页面没有直接调用 `axios` / `fetch` / `XMLHttpRequest` | 通过：`frontend/src/pages/module-config/` 未出现旁路请求关键字。 |
| 页面所有请求均通过 `frontend/src/api` typed client | 通过：`moduleConfigPageModel.ts` 统一调用 `ApiClient.call(apiId, options)`。 |
| API ID 均存在于 `API_ENDPOINTS` | 通过：使用 `ApiEndpointId` 类型约束，并只引用冻结 APP/MOD/FIELD/UI/RUN API ID。 |
| 未伪造冻结 API 未声明的创建/编辑/删除/提交语义 | 通过：未实现 UI-009 配置导入执行，未新增冻结契约外接口。 |
| `systemId` / `tenantId` 等上下文字段由统一请求层或路由上下文补齐 | 通过：`systemContext.toPathParams()` 与 `toTenantHeader()` 统一补齐。 |
| 无法补齐必填上下文时，页面阻止请求并展示空态或明确提示 | 通过：缺 `systemId/tenantId/memberId` 时返回本地 `SYS_CONTEXT_REQUIRED`，不发 API 请求。 |
