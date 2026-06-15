# FE-005 System Member RBAC Dict Page Contract Evidence

> This file records page-level evidence only. FE-012 will summarize it into `frontend/docs/api-contract-map.md`.

## Page Basic Info

| Item | Content |
| --- | --- |
| Task ID | FE-005 |
| Page/module | System profile, tenants, members, departments, system roles, role permissions, dictionaries |
| Routes | `/systems/:systemId/profile`, `/systems/:systemId/tenants`, `/systems/:systemId/members`, `/systems/:systemId/departments`, `/systems/:systemId/roles`, `/systems/:systemId/dict` |
| Entry | FE-002 route records: `system.profile`, `system.tenants`, `system.members`, `system.departments`, `system.roles`, `system.dict` |
| Dependent context | `systemId`, `memberId`; `tenantId` is sent through `X-Tenant-Id` when current system context has a tenant |
| SDK usage | `frontend/src/pages/system/pageModels.ts` uses `frontend/src/api` `ApiClient.call()` and existing stores |

## API Mapping Evidence

| API ID | Method | Path | Trigger | Required path params | Required query params | Required body params | Required header | Response fields | Enum/status | Error codes | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SYS-002 | GET | `/api/v1/systems/{systemId}/profile` | Load system profile | `systemId` | None | None | Authorization / X-Tenant-Id / X-Request-Id | `systemId`, `name/systemName`, `code/systemCode`, `domain`, `tenantMode`, `defaultTenantId`, `status`, `version` | `systemStatus` | `SYS_NOT_FOUND`, `SYS_DISABLED`, `SYS_CONTEXT_REQUIRED`, `PERM_DENIED` | Profile fields `systemId`, `code`, `tenantMode` are read-only in page model. |
| SYS-003 | PUT | `/api/v1/systems/{systemId}/profile` | Save profile | `systemId` | None | `name`; optional `code`, `domain`, `defaultTenantId`, `version` | Authorization / X-Tenant-Id / X-Request-Id | Same as SYS-002 | `systemStatus` | `SYS_ARCHIVED`, `SYS_CONTEXT_REQUIRED`, `PERM_DENIED` | No audit/system fields are writable. |
| SYS-004 | GET | `/api/v1/systems/{systemId}/tenants` | Load tenant list | `systemId` | None | None | Authorization / X-Tenant-Id / X-Request-Id | `tenantId`, `tenantCode`, `tenantName`, `status`, `version`, `updatedAt` | `tenantStatus` | `SYS_CONTEXT_REQUIRED`, `PERM_DENIED` | Multi-tenant empty list is an empty state. |
| SYS-005 | POST | `/api/v1/systems/{systemId}/tenants` | Create tenant | `systemId` | None | `tenantCode`, `tenantName` | Authorization / X-Tenant-Id / X-Request-Id | `TenantVO` | `tenantStatus` | `SYS_TENANT_CODE_DUPLICATED`, `SYS_CONTEXT_REQUIRED`, `PERM_DENIED` | Only multi-tenant systems expose enabled create action. |
| SYS-006 | PATCH | `/api/v1/systems/{systemId}/tenants/{tenantId}/status` | Enable/disable tenant | `systemId`, `tenantId` | None | `targetStatus`, optional `reason`, `version` | Authorization / X-Tenant-Id / X-Request-Id | `TenantVO` | `ENABLED`, `DISABLED` | `SYS_TENANT_DISABLED`, `SYS_CONTEXT_REQUIRED`, `PERM_DENIED` | Current tenant disabled state is blocked by FE-002 context guard. |
| SYS-007 | POST | `/api/v1/systems/{systemId}/tenant-context/switch` | Switch tenant context | `systemId` | None | `tenantId` | Authorization / X-Tenant-Id / X-Request-Id | `tenantId`, `tenantName`, `memberId`, `systemPermissions` | `tenantStatus` | `SYS_TENANT_DISABLED`, `SYS_CONTEXT_REQUIRED` | Caller should refresh permission store after context switch. |
| MEM-001 | GET | `/api/v1/systems/{systemId}/members` | Search member list | `systemId` | `pageNo`, `pageSize`, `keyword`, optional `deptId`, `roleId`, `status` | None | Authorization / X-Tenant-Id / X-Request-Id | `records[].memberId`, `accountId`, `loginName`, `displayName`, `tenantIds`, `deptPath`, `roles`, `dataScopeSummary`, `status`, `total` | member `ENABLED/DISABLED` | `SYS_MEMBER_DISABLED`, `PERM_DENIED` | Platform account fields are read-only display fields. |
| MEM-002 | POST | `/api/v1/systems/{systemId}/members/invitations` | Invite/bind member | `systemId` | None | `accountId` or `loginName`; optional `tenantIds`, `deptIds`, `postName`, `roleIds` | Authorization / X-Tenant-Id / X-Request-Id | `MemberDetailVO` | member `ENABLED/DISABLED` | `SYS_MEMBER_ACCOUNT_REQUIRED`, `SYS_MEMBER_DUPLICATED`, `PERM_DENIED` | Does not create or edit a standalone login account. |
| MEM-003 | GET | `/api/v1/systems/{systemId}/members/{memberId}` | Open member detail | `systemId`, `memberId` | None | None | Authorization / X-Tenant-Id / X-Request-Id | `MemberDetailVO`, `availableActions`, `fieldPermissions` | member `ENABLED/DISABLED` | `SYS_MEMBER_NOT_FOUND`, `PERM_DENIED` | Detail uses server field/action permissions. |
| MEM-004 | PUT | `/api/v1/systems/{systemId}/members/{memberId}` | Save member extension | `systemId`, `memberId` | None | optional `tenantIds`, `deptIds`, `postName` | Authorization / X-Tenant-Id / X-Request-Id | `MemberDetailVO` | member `ENABLED/DISABLED` | `SYS_MEMBER_NOT_FOUND`, `PERM_FIELD_WRITE_DENIED`, `PERM_DENIED` | Writable fields are only system-member extension fields. |
| MEM-005 | PATCH | `/api/v1/systems/{systemId}/members/{memberId}/status` | Enable/disable member | `systemId`, `memberId` | None | `targetStatus`, optional `reason`, `version` | Authorization / X-Tenant-Id / X-Request-Id | `MemberDetailVO` | member `ENABLED/DISABLED` | `SYS_MEMBER_NOT_FOUND`, `SYS_MEMBER_DISABLED`, `PERM_DENIED` | Disabled current member is handled by context disabled state. |
| MEM-006 | PUT | `/api/v1/systems/{systemId}/members/{memberId}/roles` | Assign roles | `systemId`, `memberId` | None | `roleIds` | Authorization / X-Tenant-Id / X-Request-Id | `MemberDetailVO.roles`, `dataScopeSummary` | role `ENABLED/DISABLED` | `SYS_ROLE_DISABLED`, `SYS_MEMBER_NOT_FOUND`, `PERM_DENIED` | Role assignment is disabled when `SYS_ROLE_ASSIGN` is missing. |
| MEM-007 | GET | `/api/v1/systems/{systemId}/members/current` | Load current member | `systemId` | None | None | Authorization / X-Tenant-Id / X-Request-Id | `memberId`, `displayName`, `roles`, `status` | member `ENABLED/DISABLED` | `SYS_MEMBER_DISABLED`, `SYS_CONTEXT_REQUIRED` | Used for current member display and context verification. |
| RBAC-001 | GET | `/api/v1/systems/{systemId}/rbac/departments/tree` | Load department tree | `systemId` | None | None | Authorization / X-Tenant-Id / X-Request-Id | `deptId`, `parentId`, `deptCode`, `deptName`, `sortOrder`, `memberCount`, `children` | None | `PERM_DENIED` | Empty tree displays department empty state. |
| RBAC-002 | POST | `/api/v1/systems/{systemId}/rbac/departments` | Create department | `systemId` | None | `deptCode`, `deptName`; optional `parentId`, `sortOrder` | Authorization / X-Tenant-Id / X-Request-Id | `DepartmentNodeVO` | None | `PERM_DENIED` | Parent can be omitted for root. |
| RBAC-003 | PUT | `/api/v1/systems/{systemId}/rbac/departments/{deptId}` | Edit department | `systemId`, `deptId` | None | `deptCode`, `deptName`; optional `parentId`, `sortOrder` | Authorization / X-Tenant-Id / X-Request-Id | `DepartmentNodeVO` | None | `PERM_DENIED` | Page model keeps department delete constraints separate. |
| RBAC-004 | DELETE | `/api/v1/systems/{systemId}/rbac/departments/{deptId}` | Delete department | `systemId`, `deptId` | None | None | Authorization / X-Tenant-Id / X-Request-Id | `deleted` | None | `SYS_DEPT_HAS_MEMBER`, `PERM_DENIED` | Delete action is locally disabled when child departments or members exist. |
| RBAC-005 | GET | `/api/v1/systems/{systemId}/rbac/roles` | Load roles | `systemId` | optional `pageNo`, `pageSize`, `keyword` | None | Authorization / X-Tenant-Id / X-Request-Id | `roleId`, `code`, `name`, `description`, `status`, `protected`, `memberCount`, `version` | role `ENABLED/DISABLED` | `PERM_DENIED` | Empty roles list displays empty state. |
| RBAC-006 | POST | `/api/v1/systems/{systemId}/rbac/roles` | Create role | `systemId` | None | `code`, `name`; optional `description` | Authorization / X-Tenant-Id / X-Request-Id | `RoleVO` | role `ENABLED/DISABLED` | `SYS_ROLE_CODE_DUPLICATED`, `PERM_DENIED` | No platform role APIs are used. |
| RBAC-007 | PUT | `/api/v1/systems/{systemId}/rbac/roles/{roleId}` | Edit role | `systemId`, `roleId` | None | `code`, `name`; optional `description` | Authorization / X-Tenant-Id / X-Request-Id | `RoleVO` | role `ENABLED/DISABLED` | `SYS_ROLE_PROTECTED`, `PERM_DENIED` | Protected roles disable edit/status actions. |
| RBAC-008 | PATCH | `/api/v1/systems/{systemId}/rbac/roles/{roleId}/status` | Enable/disable role | `systemId`, `roleId` | None | `targetStatus`, optional `reason`, `version` | Authorization / X-Tenant-Id / X-Request-Id | `RoleVO` | role `ENABLED/DISABLED` | `SYS_ROLE_PROTECTED`, `PERM_DENIED` | Disabled roles cannot be assigned to members. |
| RBAC-009 | PUT | `/api/v1/systems/{systemId}/rbac/roles/{roleId}/permissions` | Save authorization | `systemId`, `roleId` | None | optional `menuIds`, `operationCodes`, `fieldPermissions`, `dataScope`, `explicitDeny` | Authorization / X-Tenant-Id / X-Request-Id | `RolePermissionDetailVO` | `DataScopeType` | `PERM_FIELD_WRITE_DENIED`, `PERM_DATA_SCOPE_DENIED`, `PERM_DENIED` | Page model marks permission store stale after success. |
| RBAC-010 | GET | `/api/v1/systems/{systemId}/rbac/effective-permissions` | Refresh effective permission | `systemId` | None | None | Authorization / X-Tenant-Id / X-Request-Id | `memberId`, `roles`, `menus`, `operations`, `fieldPermissions`, `dataScopes`, `availableActions`, `version` | `DataScopeType` | `SYS_MEMBER_DISABLED`, `PERM_DENIED` | Source for button disabled state and field permissions. |
| RBAC-011 | GET | `/api/v1/systems/{systemId}/rbac/runtime-menus` | Refresh runtime menus | `systemId` | None | None | Authorization / X-Tenant-Id / X-Request-Id | runtime menu tree/code | None | `PERM_DENIED` | Flattened into permission store runtime menu codes. |
| RBAC-012 | GET | `/api/v1/systems/{systemId}/rbac/roles/{roleId}/permissions` | Load role authorization detail | `systemId`, `roleId` | None | None | Authorization / X-Tenant-Id / X-Request-Id | `menuIds`, `operationCodes`, `fieldPermissions`, `dataScope`, `explicitDeny`, `permissionHints`, `availableActions` | `DataScopeType` | `PERM_DENIED` | Displays field/data-scope permission summary. |
| RBAC-013 | GET | `/api/v1/systems/{systemId}/rbac/permission-catalog` | Load authorizable catalog | `systemId` | None | None | Authorization / X-Tenant-Id / X-Request-Id | `menus`, `operations`, `moduleFields`, `exports`, `flowActions`, `openApiScopes`, `dataScopeSchema` | `DataScopeType` | `PERM_DENIED` | Supplies menu, operation, field, export, flow, OpenAPI scope and data scope editors. |
| DICT-001 | GET | `/api/v1/systems/{systemId}/dict/types` | Load dictionary types | `systemId` | optional `pageNo`, `pageSize`, `keyword`, `scopeType`, `status`, `referenced` | None | Authorization / X-Tenant-Id / X-Request-Id | `dictTypeId`, `scopeType`, `tenantId`, `code`, `name`, `status`, `systemBuiltIn`, `itemCount`, `enabledItemCount`, `referenced`, `cacheVersion`, `version` | dict `ENABLED/DISABLED/DELETED` | `DICT_TYPE_NOT_FOUND`, `PERM_DENIED` | Default list excludes deleted by backend contract. |
| DICT-002 | POST | `/api/v1/systems/{systemId}/dict/types` | Create dictionary type | `systemId` | None | `scopeType`, `code`, `name`; `tenantId` required for `TENANT` | Authorization / X-Tenant-Id / X-Request-Id | `DictTypeVO`, optional `cacheRefresh` | dict status | `DICT_TYPE_CODE_DUPLICATE`, `DICT_SCOPE_INVALID`, `PERM_DENIED` | Consumes `cacheVersion`; does not build cache keys. |
| DICT-003 | PUT | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}` | Edit dictionary type | `systemId`, `dictTypeId` | None | `name`, `version`; optional `description`, `sortOrder` | Authorization / X-Tenant-Id / X-Request-Id | `DictTypeVO`, optional `cacheRefresh` | dict status | `DICT_BUILTIN_READONLY`, `DICT_STATUS_CONFLICT`, `PERM_DENIED` | Built-in type actions are disabled. |
| DICT-004 | PATCH | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}/status` | Enable/disable dictionary type | `systemId`, `dictTypeId` | None | `targetStatus`, `version`, optional `reason` | Authorization / X-Tenant-Id / X-Request-Id | `DictTypeVO`, optional `cacheRefresh` | `ENABLED/DISABLED` | `DICT_TYPE_IN_USE`, `DICT_STATUS_CONFLICT`, `DICT_CACHE_REFRESH_FAILED` | Page model requires DICT-009 usage check before destructive actions. |
| DICT-005 | GET | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}/items` | Load dictionary item tree/list | `systemId`, `dictTypeId` | optional `treeMode`, `parentId`, `status` | None | Authorization / X-Tenant-Id / X-Request-Id | `dictItemId`, `parentId`, `code`, `label`, `value`, `status`, `depthLevel`, `depthPath`, `leaf`, `systemBuiltIn`, `referenced`, `cacheVersion`, `children` | dict status | `DICT_TYPE_NOT_FOUND`, `PERM_DENIED` | Empty items display empty state for selected type. |
| DICT-006 | POST | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}/items` | Create dictionary item | `systemId`, `dictTypeId` | None | `code`, `label`, `value`; optional `parentId`, `status`, `sortOrder` | Authorization / X-Tenant-Id / X-Request-Id | `DictItemVO`, optional `cacheRefresh` | dict status | `DICT_ITEM_CODE_DUPLICATE`, `DICT_ITEM_VALUE_DUPLICATE`, `DICT_PARENT_DISABLED`, `DICT_DEPTH_EXCEEDED` | Parent-disabled error is displayed with requestId. |
| DICT-007 | PUT | `/api/v1/systems/{systemId}/dict/items/{dictItemId}` | Edit dictionary item | `systemId`, `dictItemId` | None | `label`, `value`, `version`; optional `description`, `sortOrder` | Authorization / X-Tenant-Id / X-Request-Id | `DictItemVO`, optional `cacheRefresh` | dict status | `DICT_BUILTIN_READONLY`, `DICT_ITEM_VALUE_DUPLICATE`, `PERM_DENIED` | Built-in item fields are read-only. |
| DICT-008 | PATCH | `/api/v1/systems/{systemId}/dict/items/{dictItemId}/status` | Enable/disable dictionary item | `systemId`, `dictItemId` | None | `targetStatus`, `version`, optional `reason` | Authorization / X-Tenant-Id / X-Request-Id | `DictItemVO`, optional `cacheRefresh` | `ENABLED/DISABLED` | `DICT_STATUS_CONFLICT`, `DICT_ITEM_IN_USE`, `DICT_CACHE_REFRESH_FAILED` | Disabled items remain visible for history/filter echo only. |
| DICT-009 | GET | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}/usages` | Check usage before disable/delete | `systemId`, `dictTypeId` | None | None | Authorization / X-Tenant-Id / X-Request-Id | `fieldUsages`, `recordUsageCount`, `enabledChildrenCount`, `canDisable`, `canDelete`, `blockingReasons` | None | `DICT_TYPE_NOT_FOUND`, `PERM_DENIED` | Drives delete/status disabled reasons. |
| DICT-010 | DELETE | `/api/v1/systems/{systemId}/dict/types/{dictTypeId}` | Delete dictionary type | `systemId`, `dictTypeId` | None | None | Authorization / X-Tenant-Id / X-Request-Id | `deleted`, optional `cacheRefresh` | `DELETED` terminal | `DICT_TYPE_IN_USE`, `DICT_BUILTIN_READONLY`, `PERM_DENIED` | Disabled unless DICT-009 `canDelete=true`. |
| DICT-011 | DELETE | `/api/v1/systems/{systemId}/dict/items/{dictItemId}` | Delete dictionary item | `systemId`, `dictItemId` | None | None | Authorization / X-Tenant-Id / X-Request-Id | `deleted`, optional `cacheRefresh` | `DELETED` terminal | `DICT_ITEM_IN_USE`, `DICT_HAS_ENABLED_CHILDREN`, `DICT_BUILTIN_READONLY` | Disabled when item has enabled children or record usage. |

## Field Mapping

| UI field | API field | Source | Frontend writable | Submit conversion rule | Echo/empty rule |
| --- | --- | --- | --- | --- | --- |
| System name | `name` / `systemName` | SYS-002/SYS-003 | Yes | Submit `name`; accept `systemName` for echo compatibility | Show `systemName || name`; empty means profile incomplete |
| System code | `code` / `systemCode` | SYS-002 | No | Never submit generated/audit identifiers | Show read-only |
| Tenant mode | `tenantMode` | SYS-002 | No | Never changed from FE-005 page | Controls tenant empty/create state |
| Tenant fields | `tenantCode`, `tenantName`, `status` | SYS-004 to SYS-006 | Code/name on create, status on status action | `tenantId` only path param | Empty list displays tenant empty state |
| Platform account fields | `accountId`, `loginName`, `displayName` | MEM-001/MEM-003 | No | Never submit in MEM-004 | Always read-only to avoid editing login account |
| Member extension fields | `tenantIds`, `deptIds`, `postName`, `roleIds`, `status` | MEM-002/MEM-004/MEM-005/MEM-006 | Yes, permission gated | Submit only member extension BOs | Empty departments/roles display unassigned |
| Department fields | `parentId`, `deptCode`, `deptName`, `sortOrder` | RBAC-001 to RBAC-004 | Yes, permission gated | `deptId` only path param for update/delete | Empty tree displays no departments |
| Role fields | `code`, `name`, `description`, `status` | RBAC-005 to RBAC-008 | Yes, permission gated | `roleId` only path param | Protected role disables edit/status |
| Role authorization | `menuIds`, `operationCodes`, `fieldPermissions`, `dataScope`, `explicitDeny` | RBAC-009/RBAC-012/RBAC-013 | Yes, permission gated | Uses `FieldPermission` and `DataScopeRuleDTO` from typed API | Empty catalog disables save |
| Dictionary type | `scopeType`, `tenantId`, `code`, `name`, `description`, `sortOrder`, `status`, `version` | DICT-001 to DICT-004 | Yes except system built-in/type identifiers | `tenantId` required only for `TENANT` scope | `systemBuiltIn=true` disables edit/delete |
| Dictionary item | `parentId`, `code`, `label`, `value`, `description`, `status`, `sortOrder`, `version` | DICT-005 to DICT-008/DICT-011 | Yes except built-in items | `dictItemId` only path param on update/status/delete | Tree children only when `treeMode=true` |
| Usage blockers | `canDisable`, `canDelete`, `blockingReasons` | DICT-009 | No | Used only to disable status/delete controls | Missing usage check blocks destructive actions |
| requestId | `requestId` / `X-Request-Id` | ApiResponse / ApiErrorResponse | No | `ApiClient` context may send it; page displays it on error | API error state must show requestId |

## Enums, Status And Error Codes

| Type | Contract source | Page usage | Display/disable rule |
| --- | --- | --- | --- |
| Status | `frontend/src/api/enums.ts`, local page model dict/member statuses | system, tenant, member, role, dict type, dict item | Unknown values are rendered as error state; disabled/archived context blocks requests |
| Data scope | `DataScopeRuleDTO.scopeType` / `DATA_SCOPE_TYPES` | Role authorization editor and member data scope summary | `SELF`, `DEPT`, `DEPT_TREE`, `ALL`, `CUSTOM` are shown explicitly |
| Field permission | `FieldPermission` | Role permission detail, permission catalog, member detail | `writable=false` shows `readonlyReason`; backend fallback `PERM_FIELD_WRITE_DENIED` |
| Action permission | `AvailableAction` / `PermissionHint` / `permissionStore.decide()` | Create/edit/delete/status/authorize buttons | `enabled=false` shows disabled reason and does not call API |
| Error code | `frontend/src/api/errorCodes.ts` | All page model errors through `errorStore.capture()` | Display `code`, `message`, and `requestId`; retry only when `retryable=true` |
| Dict cache refresh | `DictCacheRefreshVO` | Dict writes | Show `cacheVersion`; `DICT_CACHE_REFRESH_FAILED` is retryable and must keep requestId |

## Permission Disabled State

| Control/action | Permission source | Disabled field | Disabled display | Backend fallback error code |
| --- | --- | --- | --- | --- |
| Profile save | `SYS_PROFILE_EDIT` | `PageActionState.enabled=false` | `PERM_DENIED` or store disabled reason | `PERM_DENIED`, `SYS_ARCHIVED` |
| Tenant create/status | `SYS_TENANT_CREATE`, `SYS_TENANT_STATUS` | `PageActionState.enabled=false` | Missing permission or disabled context | `PERM_DENIED`, `SYS_TENANT_DISABLED` |
| Member invite/edit/status/role assign | `SYS_MEMBER_INVITE`, `SYS_MEMBER_EDIT`, `SYS_MEMBER_STATUS`, `SYS_ROLE_ASSIGN` | `PageActionState.enabled=false` | Missing operation; platform account fields read-only | `PERM_DENIED`, `SYS_ROLE_DISABLED`, `SYS_MEMBER_DISABLED` |
| Department create/edit/delete | `SYS_DEPT_CREATE`, `SYS_DEPT_EDIT`, `SYS_DEPT_DELETE` | `PageActionState.enabled=false` and `deleteDisabledReason()` | `SYS_DEPT_HAS_MEMBER` or child department reason | `SYS_DEPT_HAS_MEMBER`, `PERM_DENIED` |
| Role create/edit/status/authorize | `SYS_ROLE_CREATE`, `SYS_ROLE_EDIT`, `SYS_ROLE_STATUS`, `SYS_ROLE_PERMISSION_EDIT` | `PageActionState.enabled=false` | Protected role or missing permission | `SYS_ROLE_PROTECTED`, `PERM_FIELD_WRITE_DENIED`, `PERM_DATA_SCOPE_DENIED` |
| Dictionary type/item edit/delete/status | `DICT_*` operation codes and usage model | `PageActionState.enabled=false`, `usageDisabledReason()` | Built-in/read-only or DICT-009 blockers | `DICT_BUILTIN_READONLY`, `DICT_TYPE_IN_USE`, `DICT_ITEM_IN_USE`, `DICT_HAS_ENABLED_CHILDREN` |

## Empty And Error State

| Scenario | Condition | Display content | requestId display | Retry action |
| --- | --- | --- | --- | --- |
| Missing context | `systemContext.validate({system:true, member:true})` returns missing keys | `SYS_CONTEXT_REQUIRED` and no API request | Not required for local block | Re-enter system from platform page |
| Disabled context | system/tenant/member disabled in store | `SYS_DISABLED`, `SYS_TENANT_DISABLED`, or `SYS_MEMBER_DISABLED` | Required after backend fallback error | Switch system/tenant or contact admin |
| Empty members/departments/roles/dict | API returns empty array or empty `PageResult.records` | Page-specific empty state | Not required | Create action if permission allows |
| API error | `ApiClientError` captured by error store | `code + message + requestId` | Required | Retry when `retryable=true` |
| Dict usage blocked | DICT-009 returns `canDisable=false` or `canDelete=false` | `blockingReasons` list | DICT-009 requestId available if API failed | Resolve references before status/delete |
| Permission stale | RBAC-009 save succeeds | Permission cache refresh hint | Save response requestId retained | Call RBAC-010/RBAC-011 refresh |

## requestId And Audit

- All page model calls go through `ApiClient.call()` and preserve `ApiResponse.requestId` in `PageRequestState`.
- All API errors go through `errorStore.capture()` and expose `code`, `message`, `retryable`, and `requestId`.
- Write APIs in this task do not require `X-Idempotency-Key` in `IDEMPOTENCY_REQUIRED_API_IDS`; no page-level idempotency key is fabricated.
- Dictionary writes consume `cacheVersion` and optional `DictCacheRefreshVO`; the frontend does not construct cache keys.

## No Bypass Request Check

| Check item | Conclusion |
| --- | --- |
| Page code does not directly call `axios` / `fetch` / `XMLHttpRequest` | Pass: `frontend/src/pages/system/pageModels.ts` has no direct transport call. |
| All page requests go through `frontend/src/api` typed client | Pass: all API calls use `ApiClient.call(apiId, options)`. |
| API IDs exist in `API_ENDPOINTS` | Pass: page model API IDs are declared in FE-001 `API_ENDPOINTS`. |
| No fake create/edit/delete semantics outside frozen API | Pass: only `SYS-*`, `MEM-*`, `RBAC-*`, `DICT-*` frozen API IDs are called. |
| `systemId` and `tenantId` are provided by route/system context | Pass: `systemContext.toPathParams()` and `toTenantHeader()` are used. |
| Required context missing blocks requests | Pass: `createSystemPageRuntime().contextBlock()` returns local empty/error state before calling API. |
