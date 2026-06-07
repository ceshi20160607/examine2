# 前端 API 契约映射汇总

## 结论

- 汇总范围：FE-002 至 FE-011 页面级证据、`frontend/src/api/` typed SDK、`frontend/src/router/index.ts` 路由 API 映射。
- 冻结端点总数：174；MVP 端点：163；增强/占位端点：11。
- 路由引用端点：140；页面证据引用端点：161。
- 路由引用不存在端点：无。
- 页面证据引用不存在端点：无。

## 页面证据汇总

| 任务 | 路由 API 数 | 证据 API 数 | 证据文件 | requestId | 无旁路证据 |
| --- | --- | --- | --- | --- | --- |
| FE-002 | 0 | 22 | FE-002-routing-layout-auth-context.md | true | true |
| FE-003 | 4 | 8 | FE-003-login-my-systems.md | true | true |
| FE-004 | 3 | 19 | FE-004-platform-center-pages.md | true | true |
| FE-005 | 38 | 37 | FE-005-system-member-rbac-dict-pages.md | true | true |
| FE-006 | 26 | 30 | FE-006-app-module-field-config-pages.md | true | true |
| FE-007 | 0 | 8 | FE-007-dynamic-schema-renderer.md | true | true |
| FE-008 | 10 | 10 | FE-008-runtime-workbench-pages.md | true | false |
| FE-009 | 21 | 21 | FE-009-flow-workbench-pages.md | true | true |
| FE-010 | 15 | 15 | FE-010-file-export-pages.md | true | true |
| FE-011 | 23 | 23 | FE-011-openapi-audit-ops-pages.md | true | true |

## 路由到 API 映射

| route | path | task | apiIds |
| --- | --- | --- | --- |
| auth.login | /auth/login | FE-003 | AUTH-002 |
| auth.register | /auth/register | FE-003 | AUTH-001 |
| auth.resetPassword | /auth/password-reset | FE-003 | AUTH-006 |
| platform.mySystems | /platform/my-systems | FE-003 | PLAT-001 |
| platform.systems | /platform/systems | FE-004 | PLAT-003 |
| platform.configs | /platform/configs | FE-004 | PLAT-011, PLAT-012 |
| system.profile | /systems/:systemId/profile | FE-005 | SYS-001, SYS-002, SYS-003 |
| system.tenants | /systems/:systemId/tenants | FE-005 | SYS-004, SYS-005, SYS-006, SYS-007 |
| system.members | /systems/:systemId/members | FE-005 | MEM-001, MEM-002, MEM-003, MEM-004, MEM-005, MEM-006, MEM-007 |
| system.departments | /systems/:systemId/departments | FE-005 | RBAC-001, RBAC-002, RBAC-003, RBAC-004 |
| system.roles | /systems/:systemId/roles | FE-005 | RBAC-005, RBAC-006, RBAC-007, RBAC-008, RBAC-009, RBAC-010, RBAC-011, RBAC-012, RBAC-013 |
| system.dict | /systems/:systemId/dict | FE-005 | DICT-001, DICT-002, DICT-003, DICT-004, DICT-005, DICT-006, DICT-007, DICT-008, DICT-009, DICT-010, DICT-011 |
| apps.list | /systems/:systemId/apps | FE-006 | APP-001, APP-002, APP-003, APP-004, APP-005 |
| modules.list | /systems/:systemId/apps/:appId/modules | FE-006 | MOD-001, MOD-002, MOD-003, MOD-004, MOD-005, MOD-006, MOD-007 |
| modules.fields | /systems/:systemId/modules/:moduleId/fields | FE-006 | FIELD-001, FIELD-002, FIELD-003, FIELD-004, FIELD-005 |
| modules.ui | /systems/:systemId/modules/:moduleId/ui | FE-006 | UI-001, UI-002, UI-003, UI-004, UI-005, UI-006, UI-007, UI-008, UI-009 |
| runtime.home | /systems/:systemId/runtime | FE-008 | RUN-001 |
| runtime.module | /systems/:systemId/runtime/modules/:moduleId | FE-008 | RUN-002, RUN-003, RUN-004, RUN-005, RUN-006, RUN-007, RUN-008, RUN-009, RUN-010 |
| flow.templates | /systems/:systemId/flow/templates | FE-009 | FLOW-001, FLOW-002, FLOW-003, FLOW-004, FLOW-005, FLOW-006, FLOW-019, FLOW-020, FLOW-021 |
| flow.workbench | /systems/:systemId/flow/workbench | FE-009 | FLOW-007, FLOW-008, FLOW-009, FLOW-010, FLOW-011, FLOW-012, FLOW-013, FLOW-014, FLOW-015, FLOW-016, FLOW-017, FLOW-018 |
| files.center | /systems/:systemId/files | FE-010 | FILE-001, FILE-002, FILE-003, FILE-004, FILE-005, FILE-006, FILE-007 |
| exports.jobs | /systems/:systemId/exports | FE-010 | EXP-001, EXP-002, EXP-003, EXP-004, EXP-005, EXP-006, EXP-007, EXP-008 |
| openapi.clients | /systems/:systemId/openapi | FE-011 | OPM-001, OPM-002, OPM-003, OPM-004, OPM-005, OPM-006, OPM-007, OPM-008, OPM-009 |
| audit.system | /systems/:systemId/audit | FE-011 | AUD-001, AUD-002, AUD-003, AUD-004, AUD-005, AUD-007 |
| audit.platform | /platform/audit | FE-011 | AUD-006, AUD-008 |
| ops.health | /ops | FE-011 | OPS-001, OPS-002, OPS-003, OPS-004, OPS-005, OPS-006 |

## SDK 端点分组

| API | group | method | path | auth | permission | stage |
| --- | --- | --- | --- | --- | --- | --- |
| AUTH-001 | auth | POST | /api/v1/auth/register | None |  | MVP |
| AUTH-002 | auth | POST | /api/v1/auth/login | None |  | MVP |
| AUTH-003 | auth | POST | /api/v1/auth/refresh | None |  | MVP |
| AUTH-004 | auth | POST | /api/v1/auth/logout | None |  | MVP |
| AUTH-005 | auth | GET | /api/v1/auth/me | None |  | MVP |
| AUTH-006 | auth | POST | /api/v1/auth/password/reset | None |  | MVP |
| PLAT-001 | platform | GET | /api/v1/platform/my-systems | Bearer | LOGIN_USER | MVP |
| PLAT-002 | platform | POST | /api/v1/platform/systems | Bearer | PLAT_SYSTEM_CREATE | MVP |
| PLAT-003 | platform | GET | /api/v1/platform/systems | Bearer | PLAT_SYSTEM_VIEW | MVP |
| PLAT-004 | platform | GET | /api/v1/platform/systems/{systemId} | Bearer | PLAT_SYSTEM_VIEW_OR_SYSTEM_MEMBER | MVP |
| PLAT-005 | platform | PATCH | /api/v1/platform/systems/{systemId}/status | Bearer | PLAT_SYSTEM_STATUS | MVP |
| PLAT-006 | platform | GET | /api/v1/platform/accounts | Bearer | PLAT_ACCOUNT_VIEW | MVP |
| PLAT-007 | platform | POST | /api/v1/platform/accounts | Bearer | PLAT_ACCOUNT_CREATE | MVP |
| PLAT-008 | platform | PATCH | /api/v1/platform/accounts/{accountId}/status | Bearer | PLAT_ACCOUNT_STATUS | MVP |
| PLAT-009 | platform | GET | /api/v1/platform/roles | Bearer | PLAT_ROLE_VIEW | MVP |
| PLAT-010 | platform | PUT | /api/v1/platform/roles/{roleId}/menus | Bearer | PLAT_ROLE_AUTH | MVP |
| PLAT-011 | platform | GET | /api/v1/platform/configs | Bearer | PLAT_CONFIG_VIEW | MVP |
| PLAT-012 | platform | PUT | /api/v1/platform/configs/{configKey} | Bearer | PLAT_CONFIG_EDIT | MVP |
| PLAT-013 | platform | GET | /api/v1/platform/accounts/{accountId} | Bearer | PLAT_ACCOUNT_VIEW | MVP |
| PLAT-014 | platform | PUT | /api/v1/platform/accounts/{accountId} | Bearer | PLAT_ACCOUNT_CREATE | MVP |
| PLAT-015 | platform | POST | /api/v1/platform/accounts/{accountId}/password/reset | Bearer | PLAT_ACCOUNT_STATUS | MVP |
| PLAT-016 | platform | PUT | /api/v1/platform/accounts/{accountId}/roles | Bearer | PLAT_ROLE_AUTH | MVP |
| PLAT-017 | platform | POST | /api/v1/platform/roles | Bearer | PLAT_ROLE_AUTH | MVP |
| PLAT-018 | platform | PUT | /api/v1/platform/roles/{roleId} | Bearer | PLAT_ROLE_AUTH | MVP |
| PLAT-019 | platform | PATCH | /api/v1/platform/roles/{roleId}/status | Bearer | PLAT_ROLE_AUTH | MVP |
| PLAT-020 | platform | GET | /api/v1/platform/permission-catalog | Bearer | PLAT_ROLE_AUTH | MVP |
| SYS-001 | system | POST | /api/v1/systems/{systemId}/enter | Bearer | SYSTEM_MEMBER | MVP |
| SYS-002 | system | GET | /api/v1/systems/{systemId}/profile | Bearer | SYS_PROFILE_VIEW | MVP |
| SYS-003 | system | PUT | /api/v1/systems/{systemId}/profile | Bearer | SYS_PROFILE_EDIT | MVP |
| SYS-004 | system | GET | /api/v1/systems/{systemId}/tenants | Bearer | SYS_TENANT_VIEW | MVP |
| SYS-005 | system | POST | /api/v1/systems/{systemId}/tenants | Bearer | SYS_TENANT_CREATE | MVP |
| SYS-006 | system | PATCH | /api/v1/systems/{systemId}/tenants/{tenantId}/status | Bearer | SYS_TENANT_STATUS | MVP |
| SYS-007 | system | POST | /api/v1/systems/{systemId}/tenant-context/switch | Bearer | SYSTEM_MEMBER | MVP |
| MEM-001 | member | GET | /api/v1/systems/{systemId}/members | Bearer | SYS_MEMBER_VIEW | MVP |
| MEM-002 | member | POST | /api/v1/systems/{systemId}/members/invitations | Bearer | SYS_MEMBER_INVITE | MVP |
| MEM-003 | member | GET | /api/v1/systems/{systemId}/members/{memberId} | Bearer | SYS_MEMBER_VIEW | MVP |
| MEM-004 | member | PUT | /api/v1/systems/{systemId}/members/{memberId} | Bearer | SYS_MEMBER_EDIT | MVP |
| MEM-005 | member | PATCH | /api/v1/systems/{systemId}/members/{memberId}/status | Bearer | SYS_MEMBER_STATUS | MVP |
| MEM-006 | member | PUT | /api/v1/systems/{systemId}/members/{memberId}/roles | Bearer | SYS_ROLE_ASSIGN | MVP |
| MEM-007 | member | GET | /api/v1/systems/{systemId}/members/current | Bearer | SYSTEM_MEMBER | MVP |
| RBAC-001 | rbac | GET | /api/v1/systems/{systemId}/rbac/departments/tree | Bearer | SYS_DEPT_VIEW | MVP |
| RBAC-002 | rbac | POST | /api/v1/systems/{systemId}/rbac/departments | Bearer | SYS_DEPT_CREATE | MVP |
| RBAC-003 | rbac | PUT | /api/v1/systems/{systemId}/rbac/departments/{deptId} | Bearer | SYS_DEPT_EDIT | MVP |
| RBAC-004 | rbac | DELETE | /api/v1/systems/{systemId}/rbac/departments/{deptId} | Bearer | SYS_DEPT_DELETE | MVP |
| RBAC-005 | rbac | GET | /api/v1/systems/{systemId}/rbac/roles | Bearer | SYS_ROLE_VIEW | MVP |
| RBAC-006 | rbac | POST | /api/v1/systems/{systemId}/rbac/roles | Bearer | SYS_ROLE_CREATE | MVP |
| RBAC-007 | rbac | PUT | /api/v1/systems/{systemId}/rbac/roles/{roleId} | Bearer | SYS_ROLE_EDIT | MVP |
| RBAC-008 | rbac | PATCH | /api/v1/systems/{systemId}/rbac/roles/{roleId}/status | Bearer | SYS_ROLE_STATUS | MVP |
| RBAC-009 | rbac | PUT | /api/v1/systems/{systemId}/rbac/roles/{roleId}/permissions | Bearer | SYS_ROLE_PERMISSION_EDIT | MVP |
| RBAC-010 | rbac | GET | /api/v1/systems/{systemId}/rbac/effective-permissions | Bearer | SYSTEM_MEMBER | MVP |
| RBAC-011 | rbac | GET | /api/v1/systems/{systemId}/rbac/runtime-menus | Bearer | SYSTEM_MEMBER | MVP |
| RBAC-012 | rbac | GET | /api/v1/systems/{systemId}/rbac/roles/{roleId}/permissions | Bearer | SYS_ROLE_VIEW | MVP |
| RBAC-013 | rbac | GET | /api/v1/systems/{systemId}/rbac/permission-catalog | Bearer | SYS_ROLE_PERMISSION_EDIT | MVP |
| DICT-001 | dict | GET | /api/v1/systems/{systemId}/dict/types | Bearer | DICT_VIEW | MVP |
| DICT-002 | dict | POST | /api/v1/systems/{systemId}/dict/types | Bearer | DICT_CREATE | MVP |
| DICT-003 | dict | PUT | /api/v1/systems/{systemId}/dict/types/{dictTypeId} | Bearer | DICT_EDIT | MVP |
| DICT-004 | dict | PATCH | /api/v1/systems/{systemId}/dict/types/{dictTypeId}/status | Bearer | DICT_STATUS | MVP |
| DICT-005 | dict | GET | /api/v1/systems/{systemId}/dict/types/{dictTypeId}/items | Bearer | DICT_VIEW | MVP |
| DICT-006 | dict | POST | /api/v1/systems/{systemId}/dict/types/{dictTypeId}/items | Bearer | DICT_ITEM_CREATE | MVP |
| DICT-007 | dict | PUT | /api/v1/systems/{systemId}/dict/items/{dictItemId} | Bearer | DICT_ITEM_EDIT | MVP |
| DICT-008 | dict | PATCH | /api/v1/systems/{systemId}/dict/items/{dictItemId}/status | Bearer | DICT_ITEM_STATUS | MVP |
| DICT-009 | dict | GET | /api/v1/systems/{systemId}/dict/types/{dictTypeId}/usages | Bearer | DICT_VIEW | MVP |
| DICT-010 | dict | DELETE | /api/v1/systems/{systemId}/dict/types/{dictTypeId} | Bearer | DICT_DELETE | MVP |
| DICT-011 | dict | DELETE | /api/v1/systems/{systemId}/dict/items/{dictItemId} | Bearer | DICT_ITEM_DELETE | MVP |
| APP-001 | app | GET | /api/v1/systems/{systemId}/apps | Bearer | APP_VIEW | MVP |
| APP-002 | app | POST | /api/v1/systems/{systemId}/apps | Bearer | APP_CREATE | MVP |
| APP-003 | app | GET | /api/v1/systems/{systemId}/apps/{appId} | Bearer | APP_VIEW | MVP |
| APP-004 | app | PUT | /api/v1/systems/{systemId}/apps/{appId} | Bearer | APP_EDIT | MVP |
| APP-005 | app | PATCH | /api/v1/systems/{systemId}/apps/{appId}/status | Bearer | APP_STATUS | MVP |
| APP-006 | app | POST | /api/v1/systems/{systemId}/apps/{appId}/copy | Bearer | APP_COPY | ENH |
| APP-007 | app | GET | /api/v1/systems/{systemId}/apps/templates | Bearer | APP_TEMPLATE_VIEW | ENH |
| MOD-001 | module | GET | /api/v1/systems/{systemId}/apps/{appId}/modules | Bearer | MODULE_VIEW | MVP |
| MOD-002 | module | POST | /api/v1/systems/{systemId}/apps/{appId}/modules | Bearer | MODULE_CREATE | MVP |
| MOD-003 | module | GET | /api/v1/systems/{systemId}/modules/{moduleId} | Bearer | MODULE_VIEW | MVP |
| MOD-004 | module | PUT | /api/v1/systems/{systemId}/modules/{moduleId} | Bearer | MODULE_EDIT | MVP |
| MOD-005 | module | PATCH | /api/v1/systems/{systemId}/modules/{moduleId}/status | Bearer | MODULE_STATUS | MVP |
| MOD-006 | module | POST | /api/v1/systems/{systemId}/modules/{moduleId}/publish-check | Bearer | MODULE_PUBLISH | MVP |
| MOD-007 | module | POST | /api/v1/systems/{systemId}/modules/{moduleId}/publish | Bearer | MODULE_PUBLISH | MVP |
| FIELD-001 | field | GET | /api/v1/systems/{systemId}/modules/{moduleId}/fields | Bearer | FIELD_VIEW | MVP |
| FIELD-002 | field | POST | /api/v1/systems/{systemId}/modules/{moduleId}/fields | Bearer | FIELD_CREATE | MVP |
| FIELD-003 | field | PUT | /api/v1/systems/{systemId}/modules/{moduleId}/fields/{fieldId} | Bearer | FIELD_EDIT | MVP |
| FIELD-004 | field | PATCH | /api/v1/systems/{systemId}/modules/{moduleId}/fields/{fieldId}/status | Bearer | FIELD_STATUS | MVP |
| FIELD-005 | field | GET | /api/v1/systems/{systemId}/field-types | Bearer | FIELD_TYPE_VIEW | MVP |
| UI-001 | ui | GET | /api/v1/systems/{systemId}/modules/{moduleId}/ui/list-views | Bearer | PAGE_VIEW | MVP |
| UI-002 | ui | PUT | /api/v1/systems/{systemId}/modules/{moduleId}/ui/list-views/default | Bearer | PAGE_EDIT | MVP |
| UI-003 | ui | GET | /api/v1/systems/{systemId}/modules/{moduleId}/ui/forms/default | Bearer | PAGE_VIEW | MVP |
| UI-004 | ui | PUT | /api/v1/systems/{systemId}/modules/{moduleId}/ui/forms/default | Bearer | PAGE_EDIT | MVP |
| UI-005 | ui | GET | /api/v1/systems/{systemId}/modules/{moduleId}/ui/details/default | Bearer | PAGE_VIEW | MVP |
| UI-006 | ui | PUT | /api/v1/systems/{systemId}/modules/{moduleId}/ui/details/default | Bearer | PAGE_EDIT | MVP |
| UI-007 | ui | PUT | /api/v1/systems/{systemId}/modules/{moduleId}/ui/menu | Bearer | MENU_EDIT | MVP |
| UI-008 | ui | PUT | /api/v1/systems/{systemId}/modules/{moduleId}/ui/actions | Bearer | ACTION_EDIT | MVP |
| UI-009 | ui | POST | /api/v1/systems/{systemId}/modules/{moduleId}/ui/import | Bearer | PAGE_IMPORT | ENH |
| RUN-001 | runtime | GET | /api/v1/systems/{systemId}/runtime/menus | Bearer | SYSTEM_MEMBER | MVP |
| RUN-002 | runtime | GET | /api/v1/systems/{systemId}/runtime/modules/{moduleId}/schema | Bearer | MENU_VISIBLE | MVP |
| RUN-003 | runtime | POST | /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/query | Bearer | RECORD_VIEW | MVP |
| RUN-004 | runtime | POST | /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records | Bearer | RECORD_CREATE | MVP |
| RUN-005 | runtime | GET | /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId} | Bearer | RECORD_VIEW_DATA_SCOPE | MVP |
| RUN-006 | runtime | PUT | /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId} | Bearer | RECORD_EDIT_FIELD_WRITE | MVP |
| RUN-007 | runtime | DELETE | /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId} | Bearer | RECORD_DELETE | MVP |
| RUN-008 | runtime | POST | /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/submit | Bearer | RECORD_SUBMIT | MVP |
| RUN-009 | runtime | GET | /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/history | Bearer | RECORD_HISTORY_VIEW | MVP |
| RUN-010 | runtime | GET | /api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/relations | Bearer | RECORD_VIEW | MVP |
| FLOW-001 | flow | GET | /api/v1/systems/{systemId}/flow/templates | Bearer | FLOW_TEMPLATE_VIEW | MVP |
| FLOW-002 | flow | POST | /api/v1/systems/{systemId}/flow/templates | Bearer | FLOW_TEMPLATE_CREATE | MVP |
| FLOW-003 | flow | PUT | /api/v1/systems/{systemId}/flow/templates/{templateId}/graph | Bearer | FLOW_TEMPLATE_EDIT | MVP |
| FLOW-004 | flow | POST | /api/v1/systems/{systemId}/flow/templates/{templateId}/publish-check | Bearer | FLOW_TEMPLATE_PUBLISH | MVP |
| FLOW-005 | flow | POST | /api/v1/systems/{systemId}/flow/templates/{templateId}/publish | Bearer | FLOW_TEMPLATE_PUBLISH | MVP |
| FLOW-006 | flow | PUT | /api/v1/systems/{systemId}/flow/bindings/modules/{moduleId} | Bearer | FLOW_BINDING_EDIT | MVP |
| FLOW-007 | flow | GET | /api/v1/systems/{systemId}/flow/tasks/todo | Bearer | APPROVER | MVP |
| FLOW-008 | flow | GET | /api/v1/systems/{systemId}/flow/tasks/{taskId} | Bearer | TASK_ACTOR | MVP |
| FLOW-009 | flow | POST | /api/v1/systems/{systemId}/flow/tasks/{taskId}/actions | Bearer | FLOW_TASK_HANDLE | MVP |
| FLOW-010 | flow | POST | /api/v1/systems/{systemId}/flow/instances/{instanceId}/withdraw | Bearer | STARTER_OR_AUTHORIZED | MVP |
| FLOW-011 | flow | GET | /api/v1/systems/{systemId}/flow/instances/{instanceId} | Bearer | FLOW_INSTANCE_VIEW_DATA_SCOPE | MVP |
| FLOW-012 | flow | GET | /api/v1/systems/{systemId}/flow/instances/{instanceId}/diagram | Bearer | FLOW_INSTANCE_VIEW | MVP |
| FLOW-013 | flow | GET | /api/v1/systems/{systemId}/flow/workbench/cc | Bearer |  | MVP |
| FLOW-014 | flow | GET | /api/v1/systems/{systemId}/flow/workbench/started | Bearer |  | MVP |
| FLOW-015 | flow | POST | /api/v1/systems/{systemId}/flow/tasks/{taskId}/claim | Bearer |  | MVP |
| FLOW-016 | flow | POST | /api/v1/systems/{systemId}/flow/tasks/{taskId}/unclaim | Bearer |  | MVP |
| FLOW-017 | flow | GET | /api/v1/systems/{systemId}/flow/instances | Bearer |  | MVP |
| FLOW-018 | flow | GET | /api/v1/systems/{systemId}/flow/instances/{instanceId}/history | Bearer |  | MVP |
| FLOW-019 | flow | GET | /api/v1/systems/{systemId}/flow/templates/{templateId} | Bearer |  | MVP |
| FLOW-020 | flow | GET | /api/v1/systems/{systemId}/flow/templates/{templateId}/graph | Bearer |  | MVP |
| FLOW-021 | flow | PATCH | /api/v1/systems/{systemId}/flow/templates/{templateId}/status | Bearer |  | MVP |
| FILE-001 | file | POST | /api/v1/systems/{systemId}/files/upload | Bearer | FILE_UPLOAD | MVP |
| FILE-002 | file | GET | /api/v1/systems/{systemId}/files | Bearer | FILE_VIEW | MVP |
| FILE-003 | file | GET | /api/v1/systems/{systemId}/files/{fileId} | Bearer | FILE_REFERENCE_PERMISSION | MVP |
| FILE-004 | file | GET | /api/v1/systems/{systemId}/files/{fileId}/preview | Bearer | FILE_REFERENCE_PERMISSION | MVP |
| FILE-005 | file | GET | /api/v1/systems/{systemId}/files/{fileId}/download | Bearer | FILE_REFERENCE_PERMISSION | MVP |
| FILE-006 | file | DELETE | /api/v1/systems/{systemId}/files/{fileId} | Bearer | FILE_DELETE | MVP |
| FILE-007 | file | POST | /api/v1/systems/{systemId}/files/chunks | Bearer | FILE_UPLOAD | ENH |
| EXP-001 | export | GET | /api/v1/systems/{systemId}/exports/templates | Bearer | EXPORT_TEMPLATE_VIEW | MVP |
| EXP-002 | export | POST | /api/v1/systems/{systemId}/exports/templates | Bearer | EXPORT_TEMPLATE_CREATE | MVP |
| EXP-003 | export | PUT | /api/v1/systems/{systemId}/exports/templates/{templateId} | Bearer | EXPORT_TEMPLATE_EDIT | MVP |
| EXP-004 | export | POST | /api/v1/systems/{systemId}/exports/jobs | Bearer | RECORD_EXPORT | MVP |
| EXP-005 | export | GET | /api/v1/systems/{systemId}/exports/jobs | Bearer | EXPORT_JOB_VIEW | MVP |
| EXP-006 | export | GET | /api/v1/systems/{systemId}/exports/jobs/{jobId} | Bearer | EXPORT_JOB_VIEW | MVP |
| EXP-007 | export | POST | /api/v1/systems/{systemId}/exports/jobs/{jobId}/retry | Bearer | EXPORT_JOB_RETRY | MVP |
| EXP-008 | export | POST | /api/v1/systems/{systemId}/exports/jobs/{jobId}/cancel | Bearer | EXPORT_JOB_CANCEL | MVP |
| IMP-001 | import | POST | /api/v1/systems/{systemId}/imports/preview | Bearer | IMPORT_PREVIEW | PLACEHOLDER |
| IMP-002 | import | POST | /api/v1/systems/{systemId}/imports/jobs | Bearer | IMPORT_EXECUTE | PLACEHOLDER |
| OPM-001 | openapi | GET | /api/v1/systems/{systemId}/openapi/clients | Bearer | OPENAPI_CLIENT_VIEW | MVP |
| OPM-002 | openapi | POST | /api/v1/systems/{systemId}/openapi/clients | Bearer | OPENAPI_CLIENT_CREATE | MVP |
| OPM-003 | openapi | PUT | /api/v1/systems/{systemId}/openapi/clients/{clientId} | Bearer | OPENAPI_CLIENT_EDIT | MVP |
| OPM-004 | openapi | PATCH | /api/v1/systems/{systemId}/openapi/clients/{clientId}/status | Bearer | OPENAPI_CLIENT_STATUS | MVP |
| OPM-005 | openapi | POST | /api/v1/systems/{systemId}/openapi/clients/{clientId}/credentials/rotate | Bearer | OPENAPI_CREDENTIAL_ROTATE | MVP |
| OPM-006 | openapi | PUT | /api/v1/systems/{systemId}/openapi/clients/{clientId}/scopes | Bearer | OPENAPI_SCOPE_EDIT | MVP |
| OPM-007 | openapi | PUT | /api/v1/systems/{systemId}/openapi/clients/{clientId}/ip-whitelist | Bearer | OPENAPI_IP_EDIT | MVP |
| OPM-008 | openapi | GET | /api/v1/systems/{systemId}/openapi/access-logs | Bearer | OPENAPI_LOG_VIEW | MVP |
| OPM-009 | openapi | GET | /api/v1/systems/{systemId}/openapi/scope-catalog | Bearer |  | MVP |
| OPN-001 | openapi | POST | /openapi/v1/records/query | AK/SK | record:read | MVP |
| OPN-002 | openapi | GET | /openapi/v1/records/{recordId} | AK/SK | record:read | MVP |
| OPN-003 | openapi | POST | /openapi/v1/records | AK/SK | record:create | MVP |
| OPN-004 | openapi | PUT | /openapi/v1/records/{recordId} | AK/SK | record:update | MVP |
| OPN-005 | openapi | POST | /openapi/v1/records/{recordId}/submit | AK/SK | record:submit | MVP |
| OPN-006 | openapi | POST | /openapi/v1/flow/tasks/{taskId}/actions | AK/SK | flow:task:handle | MVP |
| OPN-007 | openapi | GET | /openapi/v1/files/{fileId}/download | AK/SK | file:download | MVP |
| AUD-001 | audit | GET | /api/v1/systems/{systemId}/audit/operation-logs | Bearer | AUDIT_OPERATION_VIEW | MVP |
| AUD-002 | audit | GET | /api/v1/systems/{systemId}/audit/request-logs | Bearer | AUDIT_REQUEST_VIEW | MVP |
| AUD-003 | audit | GET | /api/v1/systems/{systemId}/audit/error-logs | Bearer | AUDIT_ERROR_VIEW | MVP |
| AUD-004 | audit | GET | /api/v1/systems/{systemId}/audit/record-changes | Bearer | AUDIT_RECORD_VIEW | MVP |
| AUD-005 | audit | GET | /api/v1/systems/{systemId}/audit/openapi-logs | Bearer | OPENAPI_LOG_VIEW | MVP |
| AUD-006 | audit | GET | /api/v1/platform/audit/operation-logs | Bearer | PLAT_AUDIT_VIEW | MVP |
| AUD-007 | audit | GET | /api/v1/systems/{systemId}/audit/logs/{logId} | Bearer |  | MVP |
| AUD-008 | audit | GET | /api/v1/platform/audit/logs/{logId} | Bearer |  | MVP |
| OPS-001 | ops | GET | /api/v1/ops/health | Bearer | OPS_HEALTH_VIEW | MVP |
| OPS-002 | ops | GET | /api/v1/ops/config-check | Bearer | OPS_CONFIG_VIEW | MVP |
| OPS-003 | ops | GET | /api/v1/ops/version | Bearer | OPS_VERSION_VIEW | MVP |
| OPS-004 | ops | GET | /api/v1/ops/migration/status | Bearer | OPS_MIGRATION_VIEW | MVP |
| OPS-005 | ops | PUT | /api/v1/ops/runtime-configs/{configKey} | Bearer | OPS_CONFIG_EDIT | ENH |
| OPS-006 | ops | GET | /api/v1/ops/health/components | Bearer |  | MVP |
| GEN-001 | generator | GET | /api/v1/ops/generator/table-mappings | Bearer | GENERATOR_VIEW | PLACEHOLDER/INTERNAL |
| GEN-002 | generator | POST | /api/v1/ops/generator/tasks | Bearer | GENERATOR_RUN | PLACEHOLDER/INTERNAL |
| GEN-003 | generator | GET | /api/v1/ops/generator/tasks/{taskId} | Bearer | GENERATOR_VIEW | PLACEHOLDER/INTERNAL |
| GEN-004 | generator | GET | /api/v1/ops/generator/tasks/{taskId}/report | Bearer | GENERATOR_VIEW | PLACEHOLDER/INTERNAL |

## 枚举与幂等同步

- 状态枚举：accountStatus=NORMAL/DISABLED/LOCKED；systemStatus=DRAFT/ENABLED/DISABLED/ARCHIVED；tenantStatus=ENABLED/DISABLED；appStatus=DRAFT/ENABLED/DISABLED/ARCHIVED；moduleStatus=DRAFT/PUBLISHED/DISABLED/ARCHIVED；fieldStatus=DRAFT/ENABLED/DISABLED/DELETED；versionStatus=DRAFT/PUBLISHED/DISCARDED；recordStatus=DRAFT/SUBMITTED/IN_APPROVAL/APPROVED/REJECTED/WITHDRAWN/ARCHIVED/DELETED；flowTemplateStatus=DRAFT/PUBLISHED/DISABLED；flowInstanceStatus=IN_APPROVAL/APPROVED/REJECTED/WITHDRAWN/TERMINATED；flowTaskStatus=PENDING/DONE/CANCELED/TRANSFERRED/RETURNED；fileStatus=TEMP/REFERENCED/DELETED/EXPIRED；exportJobStatus=QUEUED/PROCESSING/SUCCESS/FAILED/CANCELED；openApiClientStatus=DRAFT/ENABLED/DISABLED/EXPIRED。
- 类型状态定义：AccountStatus, SystemStatus, TenantStatus, AppStatus, ModuleStatus, FieldStatus, VersionStatus, RecordStatus, FlowTemplateStatus, FlowInstanceStatus, FlowTaskStatus, FileStatus, ExportJobStatus, OpenApiClientStatus。
- 幂等必填接口：PLAT-002, APP-002, MOD-002, FIELD-002, MOD-006, UI-008, RUN-004, RUN-006, RUN-008, FLOW-005, FLOW-009, FLOW-010, FLOW-013, FLOW-015, FLOW-016, FILE-001, EXP-004, EXP-007, EXP-008, OPM-002, OPM-005, OPN-003, OPN-004, OPN-005, OPN-006。
- 错误码枚举数量：139，来源：`frontend/src/api/errorCodes.ts`，命名空间来源：`frontend/src/api/enums.ts`。

## 范围说明

- 以下 MVP 端点未直接出现在路由：AUTH-003, AUTH-004, AUTH-005, PLAT-002, PLAT-004, PLAT-005, PLAT-006, PLAT-007, PLAT-008, PLAT-009, PLAT-010, PLAT-013, PLAT-014, PLAT-015, PLAT-016, PLAT-017, PLAT-018, PLAT-019, PLAT-020, OPN-001, OPN-002, OPN-003, OPN-004, OPN-005, OPN-006, OPN-007。其中认证刷新/退出/当前用户、平台页面内动作等由页面模型内部调用；OPN 外部接口是 AK/SK 客户端调用入口，不作为浏览器后台管理页面路由。
- 除 OpenAPI 外部调用入口外，MVP 页面证据均已覆盖。
- OPN 外部接口未作为浏览器页面直连能力纳入页面证据：OPN-001, OPN-002, OPN-003, OPN-004, OPN-005, OPN-006, OPN-007；FE-011 覆盖 OPM 客户端、安全策略、scope 和调用日志管理，外部调用由第三方客户端通过 AK/SK 访问。
- `FILE-007`、`UI-009`、`OPS-005` 等 ENH 接口不作为 MVP 闭环通过条件；如页面证据提及，均以“不实现增强闭环”记录。
