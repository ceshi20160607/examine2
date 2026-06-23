# FE-007 动态 schema 渲染组件契约证据

> 本文件只记录 FE-007 组件级证据，FE-012 统一汇总到 `frontend/docs/api-contract-map.md`。

## 页面基本信息

| 项 | 内容 |
| --- | --- |
| 任务 ID | FE-007 |
| 页面/模块 | 动态 schema 渲染组件模型 |
| 路由 | 无独立路由；供运行台、流程工作台、文件导出等页面复用 |
| 入口 | `frontend/src/components/dynamic-schema/index.ts` |
| 依赖上下文 | `systemId` / `tenantId` / `moduleId` / `recordId` 由调用页面和统一请求层补齐 |
| SDK 引用 | 组件只引用 `frontend/src/api` 类型、枚举和错误结构，不调用 API |

## API 映射证据

| API ID | 方法 | 路径 | 触发动作 | 必填 path 入参 | 必填 query 入参 | 必填 body 入参 | 必填 header | 响应字段 | 枚举/状态 | 错误码 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RUN-002 | GET | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/schema` | 调用页面加载运行 schema 后传入组件 | `systemId`、`moduleId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | `moduleId`、`moduleCode`、`publishedVersionId`、`listSchema`、`formSchema`、`detailSchema`、`fieldDefinitions`、`availableActions`、`permissionHints`、`statusRules` | `DynamicFieldType`、`fieldStatus` | `MODULE_NOT_FOUND`、`PERM_DENIED` | FE-007 不发请求，只消费返回结构。 |
| RUN-003 | POST | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/query` | 调用页面提交列表筛选/排序 | `systemId`、`moduleId` | 无 | `filters`、`sorter`、分页参数 | Authorization / X-Tenant-Id / X-Request-Id | `records[].values`、`records[].availableActions`、`records[].recordVersion` | `recordStatus`、`flowStatus` | `PERM_DATA_SCOPE_DENIED`、`FIELD_VALUE_TYPE_INVALID` | `buildDynamicListQuery()` 只保留 schema 允许的筛选和排序字段。 |
| RUN-004 | POST | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records` | 调用页面创建记录 | `systemId`、`moduleId` | 无 | `values[]`、`idempotencyKey` | Authorization / X-Tenant-Id / X-Request-Id / X-Idempotency-Key | `recordId`、`recordStatus`、`recordVersion`、`availableActions` | `recordStatus` | `FIELD_REQUIRED_MISSING`、`FIELD_VALUE_TYPE_INVALID`、`FIELD_UNIQUE_CONFLICT`、`PERM_FIELD_WRITE_DENIED` | `validateDynamicForm()` 生成提交 `values[]` 并定位字段错误。 |
| RUN-005 | GET | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}` | 调用页面加载详情 | `systemId`、`moduleId`、`recordId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | `values`、`fileRefs`、`flowSummary`、`historySummary`、`availableActions`、`fieldPermissions` | `recordStatus` | `MODULE_RECORD_NOT_FOUND`、`PERM_DATA_SCOPE_DENIED` | `createDynamicDetailModel()` 合并详情字段权限和展示值。 |
| RUN-006 | PUT | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}` | 调用页面编辑保存 | `systemId`、`moduleId`、`recordId` | 无 | `values[]`、`recordVersion`、`idempotencyKey` | Authorization / X-Tenant-Id / X-Request-Id / X-Idempotency-Key | `recordId`、`recordStatus`、`recordVersion`、`changedFields` | `recordStatus` | `MODULE_RECORD_STATUS_CONFLICT`、`PERM_FIELD_WRITE_DENIED` | 只提交 `FieldPermission.writable=true` 且非 `AUTO_NO` 的字段。 |
| RUN-009 | GET | `/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/history` | 调用页面加载历史快照 | `systemId`、`moduleId`、`recordId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | 历史 `values`、`valueSnapshot`、`requestId` | `recordStatus` | `MODULE_RECORD_NOT_FOUND`、`PERM_DATA_SCOPE_DENIED` | `createDynamicHistoryModel()` 优先展示 `displayValue/valueSnapshot`。 |
| FLOW-008 | GET | `/api/v1/systems/{systemId}/flow/tasks/{taskId}` | 流程任务详情复用表单/详情渲染 | `systemId`、`taskId` | 无 | 无 | Authorization / X-Tenant-Id / X-Request-Id | `formSchema`、`values`、`history`、`availableActions` | `flowTaskStatus` | `FLOW_TASK_NOT_FOUND`、`PERM_DENIED` | 流程页把任务 schema 适配为 `DynamicSchemaInput` 后复用组件。 |

## 字段映射

| UI 字段 | API 字段 | 来源 | 是否前端可写 | 提交转换规则 | 回显/空值规则 |
| --- | --- | --- | --- | --- | --- |
| 字段定义 | `fieldDefinitions[]` | RUN-002 / FLOW-008 | 否 | 只作为渲染和校验元数据 | `status=DELETED` 或 `visible=false` 不渲染 |
| 字段类型 | `fieldType` | `DynamicFieldType` | 否 | 映射到 `DYNAMIC_FIELD_RENDERERS` | 未声明类型回退为只读文本兜底 |
| 列表字段 | `listSchema.columns` | RUN-002 | 否 | `createDynamicListModel()` 转为列渲染模型 | 空列显示配置空态 |
| 筛选字段 | `listSchema.filters` | RUN-002 | 否 | `buildDynamicListQuery()` 只保留允许字段和操作符 | 不支持筛选的类型被过滤 |
| 排序字段 | `listSchema.sorters` | RUN-002 | 否 | 只保留可排序字段，输出 `SortRule[]` | 不支持排序的类型被过滤 |
| 表单字段 | `formSchema.formSections[].fields` | RUN-002 / FLOW-008 | 是，受权限控制 | `validateDynamicForm()` 输出 `DynamicFieldValue[]` | 必填空值返回本地 `FIELD_REQUIRED_MISSING` |
| 详情字段 | `detailSchema.detailBlocks[].fields` | RUN-002 / RUN-005 | 否 | 不提交 | 优先 `displayValue`，其次按字段类型格式化 `value` |
| 历史字段 | `values[].valueSnapshot/displayValue` | RUN-009 / FLOW-008 | 否 | 不提交 | 优先快照展示值，缺失时显示 `-` |
| 字段权限 | `FieldPermission` / `fieldWritable` / `fieldVisibility` | RUN-002 / RUN-005 / RBAC-010 | 否 | 合并成 `FieldPermissionState` | `readonlyReason` 必须进入模型 |
| 字段错误 | `errors[].fieldCode` | API 错误响应 | 否 | `mapApiFieldErrors()` 转为字段级错误 | 展示 `message` 与 `requestId` |
| 动作权限 | `AvailableAction` | RUN/FLOW/EXP 返回 | 否 | `resolveAvailableAction()`、`actionDisabledReason()` | `enabled=false` 展示禁用原因 |

## 枚举、状态与错误码

| 类型 | 契约来源 | 组件使用位置 | 展示/禁用规则 |
| --- | --- | --- | --- |
| 字段类型 | `frontend/src/api/enums.ts` `DYNAMIC_FIELD_TYPES` | `DYNAMIC_FIELD_RENDERERS` | 覆盖 `TEXT`、`TEXTAREA`、`NUMBER`、`MONEY`、`DATE`、`DATETIME`、`SELECT`、`MULTI_SELECT`、`SWITCH`、`MEMBER`、`DEPT`、`ATTACHMENT`、`IMAGE`、`AUTO_NO`、`RELATION`、`SUB_TABLE`、`ADDRESS`、`TAG`、`JSON` |
| 字段状态 | `fieldStatus` | 权限解析 | `DELETED` 不可见；非 `ENABLED` 只读 |
| 记录状态 | `recordStatus` | 调用页面动作禁用 | 组件只消费 `AvailableAction`，不绕过后端状态规则 |
| 流程状态 | `flowTaskStatus` / `flowInstanceStatus` | 流程页面复用 | 组件只展示和校验字段，不伪造流程动作 |
| 错误码 | `frontend/src/api/errorCodes.ts` | 字段错误和错误态 | 必须展示 `message`；有 `requestId` 时必须展示 |

## 字段类型覆盖

| 字段类型 | 表单组件键 | 筛选组件键 | 列表/详情/历史展示 | 校验规则 |
| --- | --- | --- | --- | --- |
| `TEXT` | `text-input` | `text-input` | 文本 | string |
| `TEXTAREA` | `text-input` | `text-input` | 文本 | string |
| `NUMBER` | `number-input` | `number-input` | 数字 | number 或数值字符串 |
| `MONEY` | `money-input` | `money-input` | 两位小数 | number 或数值字符串 |
| `DATE` | `date-picker` | `date-picker` | 日期 | `YYYY-MM-DD` |
| `DATETIME` | `datetime-picker` | `datetime-picker` | 时间 | 可解析 ISO 日期时间 |
| `SELECT` | `select` | `select` | 选项展示值 | string/number/boolean |
| `MULTI_SELECT` | `multi-select` | `multi-select` | 多选展示值 | primitive array |
| `SWITCH` | `switch` | `switch` | Yes/No | boolean |
| `MEMBER` | `member-picker` | `member-picker` | 成员展示值 | primitive |
| `DEPT` | `dept-picker` | `dept-picker` | 部门展示值 | primitive |
| `ATTACHMENT` | `file-uploader` | `readonly-text` | 文件名列表 | `FileBindDTO[]` |
| `IMAGE` | `image-uploader` | `readonly-text` | 图片文件名列表 | `FileBindDTO[]` |
| `AUTO_NO` | `auto-number` | `readonly-text` | 自动编号 | 只读 string |
| `RELATION` | `relation-picker` | `relation-picker` | 关联展示值 | id/object/array |
| `SUB_TABLE` | `sub-table` | `readonly-text` | 子表摘要 | array |
| `ADDRESS` | `address-picker` | `text-input` | 地址文本或对象展示值 | string/object |
| `TAG` | `tag-input` | `tag-input` | 标签列表 | primitive array |
| `JSON` | `json-editor` | `readonly-text` | JSON 文本 | `JsonValue` |

## 权限禁用态

| 控件/动作 | 权限来源 | 禁用字段 | 禁用展示 | 后端兜底错误码 |
| --- | --- | --- | --- | --- |
| 字段可见 | `FieldPermission.visible`、`fieldVisibility`、`fieldStatus` | `visible=false` | 不渲染字段 | `PERM_DENIED` / `PERM_DATA_SCOPE_DENIED` |
| 字段可写 | `FieldPermission.writable`、`fieldWritable`、字段类型、字段状态 | `writable=false` | `readonlyReason` | `PERM_FIELD_WRITE_DENIED` |
| 自动编号 | `DynamicFieldType.AUTO_NO` | `writable=false` | 字段类型只读原因 | `FIELD_SERIAL_RULE_INVALID` |
| 页面动作 | `AvailableAction.visible/enabled` | `enabled=false` | `disabledReason` / `stateReason` | `PERM_DENIED` / `MODULE_RECORD_STATUS_CONFLICT` |

## 空态与错误态

| 场景 | 判定条件 | 展示内容 | requestId 展示 | 重试动作 |
| --- | --- | --- | --- | --- |
| 列表空态 | `listSchema.columns=[]` 或字段均不可见 | schema 配置空态 | 不需要 | 调用页面重新加载 RUN-002 |
| 表单空态 | `formSections=[]` 或字段均不可见 | 表单配置空态 | 不需要 | 调用页面重新加载 RUN-002 |
| 详情空态 | `detailBlocks=[]` 或字段均不可见 | 详情配置空态 | 不需要 | 调用页面重新加载 RUN-005 |
| 字段必填错误 | 必填字段空值 | 字段名 + `FIELD_REQUIRED_MISSING` | 本地错误不需要 | 修改字段后重新校验 |
| 字段类型错误 | 值与 `DynamicFieldType` 不匹配 | 字段名 + `FIELD_VALUE_TYPE_INVALID` | 本地错误不需要 | 修改字段后重新校验 |
| API 字段错误 | `ApiErrorResponse.errors[].fieldCode` | `userMessage` + 字段定位 | 必须展示 | `retryable=true` 时由调用页面提供重试 |
| API 全局错误 | `ApiClientError` | `code + message + requestId` | 必须展示 | 由调用页面决定 |

## requestId 与审计

- FE-007 不产生请求，因此不生成新的 `requestId`。
- `mapApiFieldErrors(errors, requestId)` 保留调用页面传入的 `requestId`，字段错误模型必须携带该值。
- 历史快照模型允许记录 `requestId`，供运行台历史、流程历史和审计链路展示。

## 无旁路请求检查

| 检查项 | 结论 |
| --- | --- |
| 组件没有直接调用 `axios` / `fetch` / `XMLHttpRequest` | 通过，`frontend/src/components/dynamic-schema/` 未出现旁路请求关键字。 |
| 组件所有 API 结构均来自 `frontend/src/api` typed SDK | 通过，只 import API 类型、枚举和错误结构。 |
| API ID 均存在于 `API_ENDPOINTS` | 通过，本文引用的 RUN/FLOW API 已存在于冻结 `API_ENDPOINTS`，组件代码不硬编码调用 API ID。 |
| 未伪造冻结 API 未声明的创建/编辑/删除/提交语义 | 通过，组件只生成渲染模型、校验结果和查询参数，不发起业务动作。 |
| `systemId` / `tenantId` 等上下文字段由统一请求层或调用页面补齐 | 通过，组件不接收也不补写请求上下文。 |
| 无法补齐必填上下文时页面阻止请求并展示空态或提示 | 由调用页面负责；FE-007 不绕过调用页面上下文校验。 |
