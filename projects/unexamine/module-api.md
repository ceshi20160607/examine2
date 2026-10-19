# 模块域后端接口契约

本文按当前 `manage/module` 控制器实现整理，供前端直接接入。模块域现有 **70 个路由**：模块配置 27、记录 6、场景 6、辅助配置 31。路径表以 `/api/modules` 为前缀；表内「根路径」即 `/api/modules`，`{moduleId}`、`{recordId}` 等为实际 ID。

## 通用约定

- 先调用 `POST /api/system/session` 登录，后续请求带同一个服务端会话 Cookie。**所有模块接口都要传 `?tenantId={tenantId}`**；分页、排序等参数接在其后。
- 配置读取要求当前租户有效成员；配置写入通常要求租户管理员。个人场景由本人维护；记录写入按记录权限控制。模块还必须属于租户所在系统且已对该租户启用。
- 请求体和返回体均为 JSON。POST、PUT 成功返回相应对象且通常为 HTTP 200；DELETE 成功返回空体。常见失败：401 未登录或会话失效，403 无租户/模块/记录权限，400 参数或字段值无效，404 资源不存在，409 编码重复或版本冲突。
- 表中 `Entity` 表示直接返回对应 `UnModule*` 实体的 JSON；实体含 `id`、业务字段、状态和 `createTime`/`updateTime` 等字段。状态 `1` 为启用、`0` 为停用；记录的 `recordStatus` 另有 `2` 表示软删除。详情和列表的具体包装见下文。
- 编码 `code`、`fieldName`、`dictCode`、`itemCode` 等新建时采用 `[A-Za-z][A-Za-z0-9_]{0,63}`。多数编码、字段类型和关联目标创建后不能直接修改；前端编辑时保留原值。

## 1. 建立模块组和模块

先建立分组，再建立模块。新模块会自动为当前租户建立启用关系；其他租户用 `tenant-status` 自行启用。

| 方法 | 路径 | 请求体 | 返回 | 权限和用途 |
| --- | --- | --- | --- | --- |
| GET | `/groups` | — | `UnModuleGroup[]` | 成员；当前系统全部分组，含停用项 |
| POST | `/groups` | `GroupForm` | `UnModuleGroup` | 管理员；新建分组 |
| PUT | `/groups/{groupId}` | `GroupForm` | `UnModuleGroup` | 管理员；更新名称、上级、图标、排序 |
| DELETE | `/groups/{groupId}` | — | 空 | 管理员；停用无启用子分组和模块的分组 |
| GET | 根路径 | — | `{module: UnModule, tenantEnabled: boolean}[]` | 成员；当前系统全部模块和租户启用状态 |
| POST | 根路径 | `ModuleForm` | `UnModule` | 管理员；新建模块并为当前租户启用 |
| PUT | `/{moduleId}` | `ModuleForm` | `UnModule` | 管理员；更新模块展示信息 |
| PUT | `/{moduleId}/tenant-status` | `TenantStatus` | `UnModuleTenant` | 管理员；启停当前租户的模块、设置排序 |

`GroupForm = {code, name, parentId?, icon?, sortNo?}`，根分组的 `parentId` 可省略或传 `0`。`ModuleForm = {groupId, code, name, icon?, description?, sortNo?}`。`TenantStatus = {enabled: boolean, sortNo?}`。

## 2. 配字段、表单和列表

基础字段属于系统模块，租户字段和基础字段覆盖属于当前租户。前端在新建或编辑记录前读取有效表单结构，不应只使用基础字段表。

| 方法 | 路径 | 请求体/参数 | 返回 | 用途 |
| --- | --- | --- | --- | --- |
| GET | `/{moduleId}/fields` | — | `UnModuleField[]` | 基础字段原始配置，含停用项 |
| POST | `/{moduleId}/fields` | `FieldForm` | `UnModuleField` | 新增基础字段 |
| PUT | `/{moduleId}/fields/{fieldId}` | `FieldForm` | `UnModuleField` | 更新基础字段，编码和类型保持不变 |
| DELETE | `/{moduleId}/fields/{fieldId}` | — | 空 | 停用基础字段，历史记录数据保留 |
| GET | `/{moduleId}/tenant-fields` | — | `UnModuleTenantField[]` | 当前租户扩展和覆盖的原始配置 |
| POST | `/{moduleId}/tenant-fields` | `FieldForm` | `UnModuleTenantField` | 新增租户独有字段 |
| PUT | `/{moduleId}/tenant-fields/{fieldId}` | `FieldForm` | `UnModuleTenantField` | 更新租户独有字段 |
| PUT | `/{moduleId}/tenant-fields/base/{baseFieldId}` | `FieldForm` | `UnModuleTenantField` | 覆盖基础字段的展示和表单属性；存储类型、唯一性等基础约束不放宽 |
| DELETE | `/{moduleId}/tenant-fields/{fieldId}` | — | 空 | 停用租户字段或覆盖 |
| GET | `/{moduleId}/form-schema` | `purpose=create\|edit\|detail`，默认 `create` | `FieldSpec[]` | 有效字段结构，按当前租户表单/详情顺序返回 |
| PUT | `/{moduleId}/fields/order` | `viewType=form\|detail`；请求体 `string[]` | `FieldSpec[]` | 保存当前租户的表单或详情字段顺序 |
| GET | `/{moduleId}/query-fields` | — | `UnModuleQueryField[]` | 可查询字段及操作符配置 |
| PUT | `/{moduleId}/query-fields` | `QueryFieldForm[]` | `UnModuleQueryField[]` | 替换模块查询字段配置 |
| GET | `/{moduleId}/columns` | — | `ColumnView[]` | 当前租户有效列表表头 |
| PUT | `/{moduleId}/columns` | `string[]` 字段编码 | `ColumnView[]` | 保存当前租户表头顺序 |
| GET | `/{moduleId}/dicts` | — | `UnModuleDict[]` | 读取系统通用及当前模块启用字典项 |
| POST | `/{moduleId}/dicts` | `DictForm` | `UnModuleDict` | 新增模块字典项 |
| PUT | `/{moduleId}/dicts/{dictId}` | `DictForm` | `UnModuleDict` | 修改字典项名称/排序，编码不变 |
| DELETE | `/{moduleId}/dicts/{dictId}` | — | 空 | 停用字典项 |

上表 GET 要求成员，写入要求管理员。`FieldForm` 字段如下；问号表示可省略：

```json
{
  "fieldName": "amount", "name": "金额", "type": "number",
  "remark": null, "inputTips": null, "maxLength": null, "defaultValue": null,
  "required": true, "unique": false, "hidden": false,
  "add": true, "index": true, "detail": true, "sortable": true,
  "dictCode": null, "optionData": null,
  "precisionScale": 2, "minNumber": 0, "maxNumber": null
}
```

`fieldName/name/type` 必填；其他字段可省略。类型为 `text | textarea | number | integer | date | datetime | select | multiselect | boolean`。`select/multiselect` 必须有 `dictCode` 或非空 `optionData` 数组。`number/integer` 可设 `precisionScale`（0–8）、`minNumber`、`maxNumber`。`multiselect` 不支持唯一和排序。`FieldSpec` 返回 `{fieldName,name,type,required,unique,hidden,add,index,detail,sortable,maxLength,precisionScale,minNumber,maxNumber,dictCode,optionData}`；其中 `optionData` 是存储的 JSON **字符串**。`FieldForm` 中的 `optionData` 是 JSON 值。

`QueryFieldForm = {fieldName, operators: string[]}`，保存接口整体替换旧配置。可配置操作符 `eq,ne,contains,gt,gte,lt,lte,in`；**当前记录列表实际支持前七种，不支持执行 `in`**，前端暂不提供 `in`。`UnModuleQueryField.operatorsJson` 为 JSON 字符串。`ColumnView = {fieldName,title,type,sortable,sortNo}`。`PUT /columns` 只调整顺序；字段是否进入默认表头由 `FieldForm.index` 控制，场景可另行控制本次显示列。`DictForm = {dictCode,itemCode,itemName,sortNo?}`。

## 3. 新建、编辑、查看记录

页面先调用 `form-schema` 获取字段，再把用户输入按字段编码放进 `values`。记录的 `name/num/remark/status/ownerMemberId/ownerDepartmentId` 是主记录属性，不在 `values` 内。

| 方法 | 路径 | 请求体/参数 | 返回 | 用途 |
| --- | --- | --- | --- | --- |
| POST | `/{moduleId}/records` | `RecordForm` | `RecordView` | 新建记录及字段值，写入日志 |
| GET | `/{moduleId}/records/{recordId}` | — | `RecordView` | 详情；按记录可读范围校验 |
| PUT | `/{moduleId}/records/{recordId}` | `RecordForm`，必须传当前 `version` | `RecordView` | 全量提交当前可填写字段，版本加一 |
| DELETE | `/{moduleId}/records/{recordId}` | 查询参数 `version` | 空 | 软删除，版本加一 |
| GET | `/{moduleId}/records/{recordId}/logs` | — | `UnModuleLog[]` | 查看该记录操作日志 |

`RecordForm = {name?, num?, remark?, status?, ownerMemberId?, ownerDepartmentId?, version?, values}`。`values` 是 `{[fieldName]: JSON 值}`，必填，不能提交隐藏/停用/不可填写字段；有效必填字段必须有值。`status=0` 草稿、`1` 正常；新建省略时为正常，编辑时也应显式传当前状态，避免默认回正常。新建省略负责人/负责部门时用当前成员及其部门；编辑省略时保留原值。普通成员不能在新建时指派别人为负责人；已有记录负责人变更只允许管理员。更新应把当前可填写字段完整提交；隐藏或停用字段的历史值由后端保留。

数值传 JSON number（或可解析数值），日期传 `YYYY-MM-DD`，日期时间传 ISO 本地时间如 `2026-10-19T10:30:00`，布尔传 JSON boolean，多选传选项字符串数组。返回 `RecordView = {record: UnModuleRecord, values: {[fieldName]: JSON 值}}`；`record.version` 用于后续修改、删除和状态动作。日期存储为时间值，详情中按时间类型返回。

记录可读范围为管理员、角色数据范围、负责人、创建人或团队成员；可修改/删除者为管理员、负责人、创建人或 `editor/manager` 团队成员。版本落后返回 409；无权访问返回 403。

## 4. 列表、排序与场景

`GET /{moduleId}/records` 返回 `{columns: ColumnView[], records: RecordView[], total, page, size}`，只统计当前成员可读记录。

| 参数 | 含义 |
| --- | --- |
| `page`、`size` | 默认 1、20；`page>=1`，`1<=size<=100` |
| `sortField`、`sortOrder` | 方向 `asc/desc`；字段仅能为 `id`、`createTime` 或有效且可排序的查询字段；字段相同时按 `id` 稳定补序。默认 `id desc` |
| `filterField`、`filterValue` | 两者同时传；当前接口做单个 `eq` 筛选，字段必须在查询字段白名单内。此处 `filterValue` 是字符串参数，布尔字段请使用场景筛选 |
| `sceneId` | 应用该场景的列、筛选和默认排序；显式 `sortField` 覆盖场景排序。若同时传 `filterField`，两种筛选条件相与 |

场景配置把列表列、筛选、排序保存在一起。前端推荐先读场景列表，选中场景后把 `sceneId` 传给记录列表；若要遵循场景的每页条数，还需显式传 `size=SceneView.pageSize`。

| 方法 | 路径 | 请求体 | 返回 | 用途 |
| --- | --- | --- | --- | --- |
| GET | `/{moduleId}/scenes` | — | `SceneView[]` | 读取当前租户共享场景和本人个人场景 |
| GET | `/{moduleId}/scenes/{sceneId}` | — | `SceneView` | 场景详情 |
| POST | `/{moduleId}/scenes` | `SceneForm` | `SceneView` | 创建场景及唯一配置行 |
| PUT | `/{moduleId}/scenes/{sceneId}` | `SceneForm` | `SceneView` | 修改场景；不可直接改变可见范围 |
| PUT | `/{moduleId}/scenes/{sceneId}/default` | — | `SceneView` | 设为所属范围的默认场景 |
| DELETE | `/{moduleId}/scenes/{sceneId}` | — | 空 | 停用非默认场景 |

`SceneForm = {name, visibility, defaultFlag?, columns, filters?, sort?, pageSize?}`。`visibility` 可为 `personal | tenant | public`；当前 `tenant/public` 都只在当前租户内共享，创建/修改要求管理员；个人场景由本人管理。`columns` 至少一项，元素 `{fieldName,visible?,width?}`，宽度 40–1000；`filters` 最多 10 项，元素 `{fieldName,operator,value}`；`sort={fieldName,order:"asc"|"desc"}`；`pageSize` 范围 1–100。字段须仍有效，筛选/排序须通过查询字段白名单。`SceneView = {scene: UnModuleScence, columns: JSON数组, filters: JSON数组, sort: JSON对象或null, pageSize}`；`scene.defaultFlag` 是 0/1。同一租户、模块、可见范围（个人场景还区分本人）仅有一个默认场景。

## 5. 菜单、动作和保存规则

以下列表接口成员可读，配置写入要求管理员；删除均将配置停用。返回实体的 `*Json` 字段为 JSON 字符串，前端需解析。

| 方法 | 路径 | 请求体 | 返回 | 用途 |
| --- | --- | --- | --- | --- |
| GET | `/{moduleId}/menus` | — | `UnModuleMenu[]` | 有效菜单 |
| POST | `/{moduleId}/menus` | `MenuForm` | `UnModuleMenu` | 新增菜单 |
| PUT | `/{moduleId}/menus/{id}` | `MenuForm` | `UnModuleMenu` | 修改菜单，编码不变 |
| DELETE | `/{moduleId}/menus/{id}` | — | 空 | 停用菜单 |
| GET | `/{moduleId}/actions` | — | `UnModuleAction[]` | 有效动作 |
| POST | `/{moduleId}/actions` | `ActionForm` | `UnModuleAction` | 新增动作 |
| PUT | `/{moduleId}/actions/{id}` | `ActionForm` | `UnModuleAction` | 修改动作，编码不变 |
| DELETE | `/{moduleId}/actions/{id}` | — | 空 | 停用动作 |
| POST | `/{moduleId}/records/{recordId}/actions/{actionCode}` | `{version: number}` | `UnModuleRecord` | 管理员执行 `status` 动作并写日志；`examine/flow` 类型返回 409，须调用对应运行接口 |
| GET | `/{moduleId}/rules` | — | `UnModuleRule[]` | 有效保存规则 |
| POST | `/{moduleId}/rules` | `RuleForm` | `UnModuleRule` | 新增规则 |
| PUT | `/{moduleId}/rules/{id}` | `RuleForm` | `UnModuleRule` | 修改规则，编码不变 |
| DELETE | `/{moduleId}/rules/{id}` | — | 空 | 停用规则 |

`MenuForm = {code,name,type,permissionCode,sortNo?}`。`ActionForm = {code,name,type,permissionCode,config?,sortNo?}`，`type=status|examine|flow`；状态动作的 `config` 至少为 `{recordStatus:0|1}`。当前执行入口仅执行状态动作，使用管理员身份和记录版本校验；`permissionCode` 目前作为配置保存。

`RuleForm = {code,name,triggerType:"save",condition?,effect,sortNo?}`；`condition/effect` 为 `{fieldName,operator,value,message?}`。操作符可为 `eq,ne,gt,gte,lt,lte,contains,empty,notEmpty`，即使 `empty/notEmpty` 也需带 `value` 键。保存记录时，若条件成立而 `effect` 比较不成立，整个保存请求返回 400 且事务回滚；当前无其他规则触发类型。

## 6. 关联和记录团队

先建关联配置，再关联两条当前租户内的有效记录。团队角色决定记录访问权限。

| 方法 | 路径 | 请求体 | 返回 | 用途 |
| --- | --- | --- | --- | --- |
| GET | `/{moduleId}/relation-configs` | — | `UnModuleRelationConfig[]` | 有效关联配置 |
| POST | `/{moduleId}/relation-configs` | `RelationConfigForm` | `UnModuleRelationConfig` | 管理员新增配置 |
| PUT | `/{moduleId}/relation-configs/{id}` | `RelationConfigForm` | `UnModuleRelationConfig` | 管理员修改名称、方向、映射；编码和目标模块不变 |
| DELETE | `/{moduleId}/relation-configs/{id}` | — | 空 | 管理员停用配置 |
| GET | `/{moduleId}/records/{recordId}/relations` | — | `UnModuleRelation[]` | 查看本人可读的记录关联，仅返回另一端也可读的关联 |
| POST | `/{moduleId}/records/{recordId}/relations` | `RelationForm` | `UnModuleRelation` | 源记录可写、目标记录可读时建立关联 |
| DELETE | `/{moduleId}/records/{recordId}/relations/{id}` | — | 空 | 源记录可写时删除关联 |
| GET | `/{moduleId}/records/{recordId}/teamers` | — | `UnModuleTeamer[]` | 可读记录的团队成员 |
| POST | `/{moduleId}/records/{recordId}/teamers` | `TeamerForm` | `UnModuleTeamer` | 管理员添加当前租户有效成员 |
| DELETE | `/{moduleId}/records/{recordId}/teamers/{id}` | — | 空 | 管理员移除团队成员 |

`RelationConfigForm = {code,name,targetModuleId,direction?,mapping?}`；`direction=forward|both`，默认 `forward`。`mapping` 当前作为 JSON 配置存储，建立关联时不执行自动字段映射。`RelationForm = {configId,targetRecordId}`。`TeamerForm = {memberId,roleCode}`，`roleCode=viewer|editor|manager`；三者均可读记录，`editor/manager` 可修改记录，团队配置本身仅管理员可写。

## 7. 审批和流程绑定

先配置 `examine` 或 `flow` 类型动作，再绑定同租户已启用的定义。绑定接口只管理模块与定义的关系，实际运行由审批/流程接口完成。

| 方法 | 路径 | 请求体 | 返回 | 用途 |
| --- | --- | --- | --- | --- |
| GET | `/{moduleId}/examine-bindings` | — | `UnModuleExamine[]` | 有效审批绑定 |
| POST | `/{moduleId}/examine-bindings` | `BindingForm` | `UnModuleExamine` | 管理员绑定审批定义 |
| PUT | `/{moduleId}/examine-bindings/{id}` | `BindingForm` | `UnModuleExamine` | 管理员更新定义或触发动作，编码不变 |
| DELETE | `/{moduleId}/examine-bindings/{id}` | — | 空 | 管理员停用绑定 |
| GET | `/{moduleId}/flow-bindings` | — | `UnModuleFlow[]` | 有效流程绑定 |
| POST | `/{moduleId}/flow-bindings` | `BindingForm` | `UnModuleFlow` | 管理员绑定已发布启用的流程定义 |
| PUT | `/{moduleId}/flow-bindings/{id}` | `BindingForm` | `UnModuleFlow` | 管理员更新定义或触发动作，编码不变 |
| DELETE | `/{moduleId}/flow-bindings/{id}` | — | 空 | 管理员停用绑定 |

`BindingForm = {code,definitionId,actionCode}`。审批绑定返回实体中的定义 ID 字段为 `examineId`，流程绑定为 `flowId`；两者都有 `bindingCode/startActionCode/status`。流程定义还必须有已发布的 `currentVersionId`。

## 返回实体的常用字段

| 实体 | 前端常用字段（除 `id/status/createTime/updateTime`） |
| --- | --- |
| `UnModuleGroup` | `systemId,parentId,code,name,icon,sortNo` |
| `UnModule` / `UnModuleTenant` | `systemId,groupId,code,name,icon,description,sortNo` / `tenantId,moduleId,sortNo` |
| `UnModuleField` / `UnModuleTenantField` | `moduleId,tenantId?,baseFieldId?,fieldName,name,type,remark,inputTips,maxLength,defaultValue,mustFlag,unionFlag,hiddenFlag,addFlag,indexFlag,detailFlag,sortableFlag,dictCode,optionData,precisionScale,minNumRestrict,maxNumRestrict`；标志值为 0/1 |
| `UnModuleDict` / `UnModuleQueryField` | `dictCode,itemCode,itemName,sortNo` / `fieldName,operatorsJson,sortNo` |
| `UnModuleRecord` / `UnModuleLog` | `tenantId,moduleId,name,num,remark,recordStatus,ownerMemberId,ownerDepartmentId,version,createMemberId,updateMemberId,deleteTime` / `recordId,actorMemberId,operationCode,beforeJson,afterJson,createTime` |
| `UnModuleScence` | `tenantId,moduleId,ownerMemberId,name,visibility,defaultFlag` |
| `UnModuleMenu` / `UnModuleAction` / `UnModuleRule` | `menuCode,menuName,menuType,permissionCode,sortNo` / `actionCode,name,actionType,permissionCode,configJson,sortNo` / `ruleCode,name,triggerType,conditionJson,effectJson,sortNo` |
| `UnModuleRelationConfig` / `UnModuleRelation` / `UnModuleTeamer` | `sourceModuleId,targetModuleId,relationCode,name,direction,mappingJson` / `relationConfigId,sourceRecordId,targetRecordId,createMemberId` / `recordId,memberId,roleCode` |
| `UnModuleExamine` / `UnModuleFlow` | `tenantId,moduleId,examineId/flowId,bindingCode,startActionCode` |

## 已验证和当前限制

真实请求已验证登录与租户隔离、字段值校验、数值按 `2 < 10` 排序、无效排序/分页拒绝、场景列与筛选、隐藏字段历史值保留、版本冲突、保存规则拒绝、状态动作、普通成员与团队 viewer/editor 记录权限。当前实现的查询字段配置可保存 `in`，但记录列表执行不支持；场景 `pageSize` 需前端传为列表 `size`；审批/流程类型动作须由相应运行接口启动。
