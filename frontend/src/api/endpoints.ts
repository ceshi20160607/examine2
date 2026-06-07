import { IDEMPOTENCY_REQUIRED_API_IDS } from "./enums";
import type { HttpMethod } from "./types";

export type ApiGroup =
  | "auth"
  | "platform"
  | "system"
  | "member"
  | "rbac"
  | "dict"
  | "app"
  | "module"
  | "field"
  | "ui"
  | "runtime"
  | "flow"
  | "file"
  | "export"
  | "import"
  | "openapi"
  | "audit"
  | "ops"
  | "generator";

export interface ApiEndpointDefinition {
  id: string;
  group: ApiGroup;
  method: HttpMethod;
  path: string;
  auth: "Bearer" | "AK/SK" | "None";
  permission?: string;
  stage?: "MVP" | "ENH" | "PLACEHOLDER" | "INTERNAL" | "PLACEHOLDER/INTERNAL";
  idempotencyRequired: boolean;
}

const idempotencyIds = new Set<string>(IDEMPOTENCY_REQUIRED_API_IDS);

function defineEndpoint(
  id: string,
  group: ApiGroup,
  method: HttpMethod,
  path: string,
  auth: ApiEndpointDefinition["auth"] = "Bearer",
  permission?: string,
  stage: ApiEndpointDefinition["stage"] = "MVP",
): ApiEndpointDefinition {
  return {
    id,
    group,
    method,
    path,
    auth,
    permission,
    stage,
    idempotencyRequired: idempotencyIds.has(id),
  };
}

export const API_ENDPOINTS = {
  "AUTH-001": defineEndpoint("AUTH-001", "auth", "POST", "/api/v1/auth/register", "None"),
  "AUTH-002": defineEndpoint("AUTH-002", "auth", "POST", "/api/v1/auth/login", "None"),
  "AUTH-003": defineEndpoint("AUTH-003", "auth", "POST", "/api/v1/auth/refresh", "None"),
  "AUTH-004": defineEndpoint("AUTH-004", "auth", "POST", "/api/v1/auth/logout", "None"),
  "AUTH-005": defineEndpoint("AUTH-005", "auth", "GET", "/api/v1/auth/me", "None"),
  "AUTH-006": defineEndpoint("AUTH-006", "auth", "POST", "/api/v1/auth/password/reset", "None"),

  "PLAT-001": defineEndpoint("PLAT-001", "platform", "GET", "/api/v1/platform/my-systems", "Bearer", "LOGIN_USER"),
  "PLAT-002": defineEndpoint("PLAT-002", "platform", "POST", "/api/v1/platform/systems", "Bearer", "PLAT_SYSTEM_CREATE"),
  "PLAT-003": defineEndpoint("PLAT-003", "platform", "GET", "/api/v1/platform/systems", "Bearer", "PLAT_SYSTEM_VIEW"),
  "PLAT-004": defineEndpoint("PLAT-004", "platform", "GET", "/api/v1/platform/systems/{systemId}", "Bearer", "PLAT_SYSTEM_VIEW_OR_SYSTEM_MEMBER"),
  "PLAT-005": defineEndpoint("PLAT-005", "platform", "PATCH", "/api/v1/platform/systems/{systemId}/status", "Bearer", "PLAT_SYSTEM_STATUS"),
  "PLAT-006": defineEndpoint("PLAT-006", "platform", "GET", "/api/v1/platform/accounts", "Bearer", "PLAT_ACCOUNT_VIEW"),
  "PLAT-007": defineEndpoint("PLAT-007", "platform", "POST", "/api/v1/platform/accounts", "Bearer", "PLAT_ACCOUNT_CREATE"),
  "PLAT-008": defineEndpoint("PLAT-008", "platform", "PATCH", "/api/v1/platform/accounts/{accountId}/status", "Bearer", "PLAT_ACCOUNT_STATUS"),
  "PLAT-009": defineEndpoint("PLAT-009", "platform", "GET", "/api/v1/platform/roles", "Bearer", "PLAT_ROLE_VIEW"),
  "PLAT-010": defineEndpoint("PLAT-010", "platform", "PUT", "/api/v1/platform/roles/{roleId}/menus", "Bearer", "PLAT_ROLE_AUTH"),
  "PLAT-011": defineEndpoint("PLAT-011", "platform", "GET", "/api/v1/platform/configs", "Bearer", "PLAT_CONFIG_VIEW"),
  "PLAT-012": defineEndpoint("PLAT-012", "platform", "PUT", "/api/v1/platform/configs/{configKey}", "Bearer", "PLAT_CONFIG_EDIT"),
  "PLAT-013": defineEndpoint("PLAT-013", "platform", "GET", "/api/v1/platform/accounts/{accountId}", "Bearer", "PLAT_ACCOUNT_VIEW"),
  "PLAT-014": defineEndpoint("PLAT-014", "platform", "PUT", "/api/v1/platform/accounts/{accountId}", "Bearer", "PLAT_ACCOUNT_CREATE"),
  "PLAT-015": defineEndpoint("PLAT-015", "platform", "POST", "/api/v1/platform/accounts/{accountId}/password/reset", "Bearer", "PLAT_ACCOUNT_STATUS"),
  "PLAT-016": defineEndpoint("PLAT-016", "platform", "PUT", "/api/v1/platform/accounts/{accountId}/roles", "Bearer", "PLAT_ROLE_AUTH"),
  "PLAT-017": defineEndpoint("PLAT-017", "platform", "POST", "/api/v1/platform/roles", "Bearer", "PLAT_ROLE_AUTH"),
  "PLAT-018": defineEndpoint("PLAT-018", "platform", "PUT", "/api/v1/platform/roles/{roleId}", "Bearer", "PLAT_ROLE_AUTH"),
  "PLAT-019": defineEndpoint("PLAT-019", "platform", "PATCH", "/api/v1/platform/roles/{roleId}/status", "Bearer", "PLAT_ROLE_AUTH"),
  "PLAT-020": defineEndpoint("PLAT-020", "platform", "GET", "/api/v1/platform/permission-catalog", "Bearer", "PLAT_ROLE_AUTH"),

  "SYS-001": defineEndpoint("SYS-001", "system", "POST", "/api/v1/systems/{systemId}/enter", "Bearer", "SYSTEM_MEMBER"),
  "SYS-002": defineEndpoint("SYS-002", "system", "GET", "/api/v1/systems/{systemId}/profile", "Bearer", "SYS_PROFILE_VIEW"),
  "SYS-003": defineEndpoint("SYS-003", "system", "PUT", "/api/v1/systems/{systemId}/profile", "Bearer", "SYS_PROFILE_EDIT"),
  "SYS-004": defineEndpoint("SYS-004", "system", "GET", "/api/v1/systems/{systemId}/tenants", "Bearer", "SYS_TENANT_VIEW"),
  "SYS-005": defineEndpoint("SYS-005", "system", "POST", "/api/v1/systems/{systemId}/tenants", "Bearer", "SYS_TENANT_CREATE"),
  "SYS-006": defineEndpoint("SYS-006", "system", "PATCH", "/api/v1/systems/{systemId}/tenants/{tenantId}/status", "Bearer", "SYS_TENANT_STATUS"),
  "SYS-007": defineEndpoint("SYS-007", "system", "POST", "/api/v1/systems/{systemId}/tenant-context/switch", "Bearer", "SYSTEM_MEMBER"),

  "MEM-001": defineEndpoint("MEM-001", "member", "GET", "/api/v1/systems/{systemId}/members", "Bearer", "SYS_MEMBER_VIEW"),
  "MEM-002": defineEndpoint("MEM-002", "member", "POST", "/api/v1/systems/{systemId}/members/invitations", "Bearer", "SYS_MEMBER_INVITE"),
  "MEM-003": defineEndpoint("MEM-003", "member", "GET", "/api/v1/systems/{systemId}/members/{memberId}", "Bearer", "SYS_MEMBER_VIEW"),
  "MEM-004": defineEndpoint("MEM-004", "member", "PUT", "/api/v1/systems/{systemId}/members/{memberId}", "Bearer", "SYS_MEMBER_EDIT"),
  "MEM-005": defineEndpoint("MEM-005", "member", "PATCH", "/api/v1/systems/{systemId}/members/{memberId}/status", "Bearer", "SYS_MEMBER_STATUS"),
  "MEM-006": defineEndpoint("MEM-006", "member", "PUT", "/api/v1/systems/{systemId}/members/{memberId}/roles", "Bearer", "SYS_ROLE_ASSIGN"),
  "MEM-007": defineEndpoint("MEM-007", "member", "GET", "/api/v1/systems/{systemId}/members/current", "Bearer", "SYSTEM_MEMBER"),

  "RBAC-001": defineEndpoint("RBAC-001", "rbac", "GET", "/api/v1/systems/{systemId}/rbac/departments/tree", "Bearer", "SYS_DEPT_VIEW"),
  "RBAC-002": defineEndpoint("RBAC-002", "rbac", "POST", "/api/v1/systems/{systemId}/rbac/departments", "Bearer", "SYS_DEPT_CREATE"),
  "RBAC-003": defineEndpoint("RBAC-003", "rbac", "PUT", "/api/v1/systems/{systemId}/rbac/departments/{deptId}", "Bearer", "SYS_DEPT_EDIT"),
  "RBAC-004": defineEndpoint("RBAC-004", "rbac", "DELETE", "/api/v1/systems/{systemId}/rbac/departments/{deptId}", "Bearer", "SYS_DEPT_DELETE"),
  "RBAC-005": defineEndpoint("RBAC-005", "rbac", "GET", "/api/v1/systems/{systemId}/rbac/roles", "Bearer", "SYS_ROLE_VIEW"),
  "RBAC-006": defineEndpoint("RBAC-006", "rbac", "POST", "/api/v1/systems/{systemId}/rbac/roles", "Bearer", "SYS_ROLE_CREATE"),
  "RBAC-007": defineEndpoint("RBAC-007", "rbac", "PUT", "/api/v1/systems/{systemId}/rbac/roles/{roleId}", "Bearer", "SYS_ROLE_EDIT"),
  "RBAC-008": defineEndpoint("RBAC-008", "rbac", "PATCH", "/api/v1/systems/{systemId}/rbac/roles/{roleId}/status", "Bearer", "SYS_ROLE_STATUS"),
  "RBAC-009": defineEndpoint("RBAC-009", "rbac", "PUT", "/api/v1/systems/{systemId}/rbac/roles/{roleId}/permissions", "Bearer", "SYS_ROLE_PERMISSION_EDIT"),
  "RBAC-010": defineEndpoint("RBAC-010", "rbac", "GET", "/api/v1/systems/{systemId}/rbac/effective-permissions", "Bearer", "SYSTEM_MEMBER"),
  "RBAC-011": defineEndpoint("RBAC-011", "rbac", "GET", "/api/v1/systems/{systemId}/rbac/runtime-menus", "Bearer", "SYSTEM_MEMBER"),
  "RBAC-012": defineEndpoint("RBAC-012", "rbac", "GET", "/api/v1/systems/{systemId}/rbac/roles/{roleId}/permissions", "Bearer", "SYS_ROLE_VIEW"),
  "RBAC-013": defineEndpoint("RBAC-013", "rbac", "GET", "/api/v1/systems/{systemId}/rbac/permission-catalog", "Bearer", "SYS_ROLE_PERMISSION_EDIT"),

  "DICT-001": defineEndpoint("DICT-001", "dict", "GET", "/api/v1/systems/{systemId}/dict/types", "Bearer", "DICT_VIEW"),
  "DICT-002": defineEndpoint("DICT-002", "dict", "POST", "/api/v1/systems/{systemId}/dict/types", "Bearer", "DICT_CREATE"),
  "DICT-003": defineEndpoint("DICT-003", "dict", "PUT", "/api/v1/systems/{systemId}/dict/types/{dictTypeId}", "Bearer", "DICT_EDIT"),
  "DICT-004": defineEndpoint("DICT-004", "dict", "PATCH", "/api/v1/systems/{systemId}/dict/types/{dictTypeId}/status", "Bearer", "DICT_STATUS"),
  "DICT-005": defineEndpoint("DICT-005", "dict", "GET", "/api/v1/systems/{systemId}/dict/types/{dictTypeId}/items", "Bearer", "DICT_VIEW"),
  "DICT-006": defineEndpoint("DICT-006", "dict", "POST", "/api/v1/systems/{systemId}/dict/types/{dictTypeId}/items", "Bearer", "DICT_ITEM_CREATE"),
  "DICT-007": defineEndpoint("DICT-007", "dict", "PUT", "/api/v1/systems/{systemId}/dict/items/{dictItemId}", "Bearer", "DICT_ITEM_EDIT"),
  "DICT-008": defineEndpoint("DICT-008", "dict", "PATCH", "/api/v1/systems/{systemId}/dict/items/{dictItemId}/status", "Bearer", "DICT_ITEM_STATUS"),
  "DICT-009": defineEndpoint("DICT-009", "dict", "GET", "/api/v1/systems/{systemId}/dict/types/{dictTypeId}/usages", "Bearer", "DICT_VIEW"),
  "DICT-010": defineEndpoint("DICT-010", "dict", "DELETE", "/api/v1/systems/{systemId}/dict/types/{dictTypeId}", "Bearer", "DICT_DELETE"),
  "DICT-011": defineEndpoint("DICT-011", "dict", "DELETE", "/api/v1/systems/{systemId}/dict/items/{dictItemId}", "Bearer", "DICT_ITEM_DELETE"),

  "APP-001": defineEndpoint("APP-001", "app", "GET", "/api/v1/systems/{systemId}/apps", "Bearer", "APP_VIEW"),
  "APP-002": defineEndpoint("APP-002", "app", "POST", "/api/v1/systems/{systemId}/apps", "Bearer", "APP_CREATE"),
  "APP-003": defineEndpoint("APP-003", "app", "GET", "/api/v1/systems/{systemId}/apps/{appId}", "Bearer", "APP_VIEW"),
  "APP-004": defineEndpoint("APP-004", "app", "PUT", "/api/v1/systems/{systemId}/apps/{appId}", "Bearer", "APP_EDIT"),
  "APP-005": defineEndpoint("APP-005", "app", "PATCH", "/api/v1/systems/{systemId}/apps/{appId}/status", "Bearer", "APP_STATUS"),
  "APP-006": defineEndpoint("APP-006", "app", "POST", "/api/v1/systems/{systemId}/apps/{appId}/copy", "Bearer", "APP_COPY", "ENH"),
  "APP-007": defineEndpoint("APP-007", "app", "GET", "/api/v1/systems/{systemId}/apps/templates", "Bearer", "APP_TEMPLATE_VIEW", "ENH"),

  "MOD-001": defineEndpoint("MOD-001", "module", "GET", "/api/v1/systems/{systemId}/apps/{appId}/modules", "Bearer", "MODULE_VIEW"),
  "MOD-002": defineEndpoint("MOD-002", "module", "POST", "/api/v1/systems/{systemId}/apps/{appId}/modules", "Bearer", "MODULE_CREATE"),
  "MOD-003": defineEndpoint("MOD-003", "module", "GET", "/api/v1/systems/{systemId}/modules/{moduleId}", "Bearer", "MODULE_VIEW"),
  "MOD-004": defineEndpoint("MOD-004", "module", "PUT", "/api/v1/systems/{systemId}/modules/{moduleId}", "Bearer", "MODULE_EDIT"),
  "MOD-005": defineEndpoint("MOD-005", "module", "PATCH", "/api/v1/systems/{systemId}/modules/{moduleId}/status", "Bearer", "MODULE_STATUS"),
  "MOD-006": defineEndpoint("MOD-006", "module", "POST", "/api/v1/systems/{systemId}/modules/{moduleId}/publish-check", "Bearer", "MODULE_PUBLISH"),
  "MOD-007": defineEndpoint("MOD-007", "module", "POST", "/api/v1/systems/{systemId}/modules/{moduleId}/publish", "Bearer", "MODULE_PUBLISH"),

  "FIELD-001": defineEndpoint("FIELD-001", "field", "GET", "/api/v1/systems/{systemId}/modules/{moduleId}/fields", "Bearer", "FIELD_VIEW"),
  "FIELD-002": defineEndpoint("FIELD-002", "field", "POST", "/api/v1/systems/{systemId}/modules/{moduleId}/fields", "Bearer", "FIELD_CREATE"),
  "FIELD-003": defineEndpoint("FIELD-003", "field", "PUT", "/api/v1/systems/{systemId}/modules/{moduleId}/fields/{fieldId}", "Bearer", "FIELD_EDIT"),
  "FIELD-004": defineEndpoint("FIELD-004", "field", "PATCH", "/api/v1/systems/{systemId}/modules/{moduleId}/fields/{fieldId}/status", "Bearer", "FIELD_STATUS"),
  "FIELD-005": defineEndpoint("FIELD-005", "field", "GET", "/api/v1/systems/{systemId}/field-types", "Bearer", "FIELD_TYPE_VIEW"),

  "UI-001": defineEndpoint("UI-001", "ui", "GET", "/api/v1/systems/{systemId}/modules/{moduleId}/ui/list-views", "Bearer", "PAGE_VIEW"),
  "UI-002": defineEndpoint("UI-002", "ui", "PUT", "/api/v1/systems/{systemId}/modules/{moduleId}/ui/list-views/default", "Bearer", "PAGE_EDIT"),
  "UI-003": defineEndpoint("UI-003", "ui", "GET", "/api/v1/systems/{systemId}/modules/{moduleId}/ui/forms/default", "Bearer", "PAGE_VIEW"),
  "UI-004": defineEndpoint("UI-004", "ui", "PUT", "/api/v1/systems/{systemId}/modules/{moduleId}/ui/forms/default", "Bearer", "PAGE_EDIT"),
  "UI-005": defineEndpoint("UI-005", "ui", "GET", "/api/v1/systems/{systemId}/modules/{moduleId}/ui/details/default", "Bearer", "PAGE_VIEW"),
  "UI-006": defineEndpoint("UI-006", "ui", "PUT", "/api/v1/systems/{systemId}/modules/{moduleId}/ui/details/default", "Bearer", "PAGE_EDIT"),
  "UI-007": defineEndpoint("UI-007", "ui", "PUT", "/api/v1/systems/{systemId}/modules/{moduleId}/ui/menu", "Bearer", "MENU_EDIT"),
  "UI-008": defineEndpoint("UI-008", "ui", "PUT", "/api/v1/systems/{systemId}/modules/{moduleId}/ui/actions", "Bearer", "ACTION_EDIT"),
  "UI-009": defineEndpoint("UI-009", "ui", "POST", "/api/v1/systems/{systemId}/modules/{moduleId}/ui/import", "Bearer", "PAGE_IMPORT", "ENH"),

  "RUN-001": defineEndpoint("RUN-001", "runtime", "GET", "/api/v1/systems/{systemId}/runtime/menus", "Bearer", "SYSTEM_MEMBER"),
  "RUN-002": defineEndpoint("RUN-002", "runtime", "GET", "/api/v1/systems/{systemId}/runtime/modules/{moduleId}/schema", "Bearer", "MENU_VISIBLE"),
  "RUN-003": defineEndpoint("RUN-003", "runtime", "POST", "/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/query", "Bearer", "RECORD_VIEW"),
  "RUN-004": defineEndpoint("RUN-004", "runtime", "POST", "/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records", "Bearer", "RECORD_CREATE"),
  "RUN-005": defineEndpoint("RUN-005", "runtime", "GET", "/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}", "Bearer", "RECORD_VIEW_DATA_SCOPE"),
  "RUN-006": defineEndpoint("RUN-006", "runtime", "PUT", "/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}", "Bearer", "RECORD_EDIT_FIELD_WRITE"),
  "RUN-007": defineEndpoint("RUN-007", "runtime", "DELETE", "/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}", "Bearer", "RECORD_DELETE"),
  "RUN-008": defineEndpoint("RUN-008", "runtime", "POST", "/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/submit", "Bearer", "RECORD_SUBMIT"),
  "RUN-009": defineEndpoint("RUN-009", "runtime", "GET", "/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/history", "Bearer", "RECORD_HISTORY_VIEW"),
  "RUN-010": defineEndpoint("RUN-010", "runtime", "GET", "/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/relations", "Bearer", "RECORD_VIEW"),

  "FLOW-001": defineEndpoint("FLOW-001", "flow", "GET", "/api/v1/systems/{systemId}/flow/templates", "Bearer", "FLOW_TEMPLATE_VIEW"),
  "FLOW-002": defineEndpoint("FLOW-002", "flow", "POST", "/api/v1/systems/{systemId}/flow/templates", "Bearer", "FLOW_TEMPLATE_CREATE"),
  "FLOW-003": defineEndpoint("FLOW-003", "flow", "PUT", "/api/v1/systems/{systemId}/flow/templates/{templateId}/graph", "Bearer", "FLOW_TEMPLATE_EDIT"),
  "FLOW-004": defineEndpoint("FLOW-004", "flow", "POST", "/api/v1/systems/{systemId}/flow/templates/{templateId}/publish-check", "Bearer", "FLOW_TEMPLATE_PUBLISH"),
  "FLOW-005": defineEndpoint("FLOW-005", "flow", "POST", "/api/v1/systems/{systemId}/flow/templates/{templateId}/publish", "Bearer", "FLOW_TEMPLATE_PUBLISH"),
  "FLOW-006": defineEndpoint("FLOW-006", "flow", "PUT", "/api/v1/systems/{systemId}/flow/bindings/modules/{moduleId}", "Bearer", "FLOW_BINDING_EDIT"),
  "FLOW-007": defineEndpoint("FLOW-007", "flow", "GET", "/api/v1/systems/{systemId}/flow/tasks/todo", "Bearer", "APPROVER"),
  "FLOW-008": defineEndpoint("FLOW-008", "flow", "GET", "/api/v1/systems/{systemId}/flow/tasks/{taskId}", "Bearer", "TASK_ACTOR"),
  "FLOW-009": defineEndpoint("FLOW-009", "flow", "POST", "/api/v1/systems/{systemId}/flow/tasks/{taskId}/actions", "Bearer", "FLOW_TASK_HANDLE"),
  "FLOW-010": defineEndpoint("FLOW-010", "flow", "POST", "/api/v1/systems/{systemId}/flow/instances/{instanceId}/withdraw", "Bearer", "STARTER_OR_AUTHORIZED"),
  "FLOW-011": defineEndpoint("FLOW-011", "flow", "GET", "/api/v1/systems/{systemId}/flow/instances/{instanceId}", "Bearer", "FLOW_INSTANCE_VIEW_DATA_SCOPE"),
  "FLOW-012": defineEndpoint("FLOW-012", "flow", "GET", "/api/v1/systems/{systemId}/flow/instances/{instanceId}/diagram", "Bearer", "FLOW_INSTANCE_VIEW"),
  "FLOW-013": defineEndpoint("FLOW-013", "flow", "GET", "/api/v1/systems/{systemId}/flow/workbench/cc"),
  "FLOW-014": defineEndpoint("FLOW-014", "flow", "GET", "/api/v1/systems/{systemId}/flow/workbench/started"),
  "FLOW-015": defineEndpoint("FLOW-015", "flow", "POST", "/api/v1/systems/{systemId}/flow/tasks/{taskId}/claim"),
  "FLOW-016": defineEndpoint("FLOW-016", "flow", "POST", "/api/v1/systems/{systemId}/flow/tasks/{taskId}/unclaim"),
  "FLOW-017": defineEndpoint("FLOW-017", "flow", "GET", "/api/v1/systems/{systemId}/flow/instances"),
  "FLOW-018": defineEndpoint("FLOW-018", "flow", "GET", "/api/v1/systems/{systemId}/flow/instances/{instanceId}/history"),
  "FLOW-019": defineEndpoint("FLOW-019", "flow", "GET", "/api/v1/systems/{systemId}/flow/templates/{templateId}"),
  "FLOW-020": defineEndpoint("FLOW-020", "flow", "GET", "/api/v1/systems/{systemId}/flow/templates/{templateId}/graph"),
  "FLOW-021": defineEndpoint("FLOW-021", "flow", "PATCH", "/api/v1/systems/{systemId}/flow/templates/{templateId}/status"),

  "FILE-001": defineEndpoint("FILE-001", "file", "POST", "/api/v1/systems/{systemId}/files/upload", "Bearer", "FILE_UPLOAD"),
  "FILE-002": defineEndpoint("FILE-002", "file", "GET", "/api/v1/systems/{systemId}/files", "Bearer", "FILE_VIEW"),
  "FILE-003": defineEndpoint("FILE-003", "file", "GET", "/api/v1/systems/{systemId}/files/{fileId}", "Bearer", "FILE_REFERENCE_PERMISSION"),
  "FILE-004": defineEndpoint("FILE-004", "file", "GET", "/api/v1/systems/{systemId}/files/{fileId}/preview", "Bearer", "FILE_REFERENCE_PERMISSION"),
  "FILE-005": defineEndpoint("FILE-005", "file", "GET", "/api/v1/systems/{systemId}/files/{fileId}/download", "Bearer", "FILE_REFERENCE_PERMISSION"),
  "FILE-006": defineEndpoint("FILE-006", "file", "DELETE", "/api/v1/systems/{systemId}/files/{fileId}", "Bearer", "FILE_DELETE"),
  "FILE-007": defineEndpoint("FILE-007", "file", "POST", "/api/v1/systems/{systemId}/files/chunks", "Bearer", "FILE_UPLOAD", "ENH"),

  "EXP-001": defineEndpoint("EXP-001", "export", "GET", "/api/v1/systems/{systemId}/exports/templates", "Bearer", "EXPORT_TEMPLATE_VIEW"),
  "EXP-002": defineEndpoint("EXP-002", "export", "POST", "/api/v1/systems/{systemId}/exports/templates", "Bearer", "EXPORT_TEMPLATE_CREATE"),
  "EXP-003": defineEndpoint("EXP-003", "export", "PUT", "/api/v1/systems/{systemId}/exports/templates/{templateId}", "Bearer", "EXPORT_TEMPLATE_EDIT"),
  "EXP-004": defineEndpoint("EXP-004", "export", "POST", "/api/v1/systems/{systemId}/exports/jobs", "Bearer", "RECORD_EXPORT"),
  "EXP-005": defineEndpoint("EXP-005", "export", "GET", "/api/v1/systems/{systemId}/exports/jobs", "Bearer", "EXPORT_JOB_VIEW"),
  "EXP-006": defineEndpoint("EXP-006", "export", "GET", "/api/v1/systems/{systemId}/exports/jobs/{jobId}", "Bearer", "EXPORT_JOB_VIEW"),
  "EXP-007": defineEndpoint("EXP-007", "export", "POST", "/api/v1/systems/{systemId}/exports/jobs/{jobId}/retry", "Bearer", "EXPORT_JOB_RETRY"),
  "EXP-008": defineEndpoint("EXP-008", "export", "POST", "/api/v1/systems/{systemId}/exports/jobs/{jobId}/cancel", "Bearer", "EXPORT_JOB_CANCEL"),
  "IMP-001": defineEndpoint("IMP-001", "import", "POST", "/api/v1/systems/{systemId}/imports/preview", "Bearer", "IMPORT_PREVIEW", "PLACEHOLDER"),
  "IMP-002": defineEndpoint("IMP-002", "import", "POST", "/api/v1/systems/{systemId}/imports/jobs", "Bearer", "IMPORT_EXECUTE", "PLACEHOLDER"),

  "OPM-001": defineEndpoint("OPM-001", "openapi", "GET", "/api/v1/systems/{systemId}/openapi/clients", "Bearer", "OPENAPI_CLIENT_VIEW"),
  "OPM-002": defineEndpoint("OPM-002", "openapi", "POST", "/api/v1/systems/{systemId}/openapi/clients", "Bearer", "OPENAPI_CLIENT_CREATE"),
  "OPM-003": defineEndpoint("OPM-003", "openapi", "PUT", "/api/v1/systems/{systemId}/openapi/clients/{clientId}", "Bearer", "OPENAPI_CLIENT_EDIT"),
  "OPM-004": defineEndpoint("OPM-004", "openapi", "PATCH", "/api/v1/systems/{systemId}/openapi/clients/{clientId}/status", "Bearer", "OPENAPI_CLIENT_STATUS"),
  "OPM-005": defineEndpoint("OPM-005", "openapi", "POST", "/api/v1/systems/{systemId}/openapi/clients/{clientId}/credentials/rotate", "Bearer", "OPENAPI_CREDENTIAL_ROTATE"),
  "OPM-006": defineEndpoint("OPM-006", "openapi", "PUT", "/api/v1/systems/{systemId}/openapi/clients/{clientId}/scopes", "Bearer", "OPENAPI_SCOPE_EDIT"),
  "OPM-007": defineEndpoint("OPM-007", "openapi", "PUT", "/api/v1/systems/{systemId}/openapi/clients/{clientId}/ip-whitelist", "Bearer", "OPENAPI_IP_EDIT"),
  "OPM-008": defineEndpoint("OPM-008", "openapi", "GET", "/api/v1/systems/{systemId}/openapi/access-logs", "Bearer", "OPENAPI_LOG_VIEW"),
  "OPM-009": defineEndpoint("OPM-009", "openapi", "GET", "/api/v1/systems/{systemId}/openapi/scope-catalog"),
  "OPN-001": defineEndpoint("OPN-001", "openapi", "POST", "/openapi/v1/records/query", "AK/SK", "record:read"),
  "OPN-002": defineEndpoint("OPN-002", "openapi", "GET", "/openapi/v1/records/{recordId}", "AK/SK", "record:read"),
  "OPN-003": defineEndpoint("OPN-003", "openapi", "POST", "/openapi/v1/records", "AK/SK", "record:create"),
  "OPN-004": defineEndpoint("OPN-004", "openapi", "PUT", "/openapi/v1/records/{recordId}", "AK/SK", "record:update"),
  "OPN-005": defineEndpoint("OPN-005", "openapi", "POST", "/openapi/v1/records/{recordId}/submit", "AK/SK", "record:submit"),
  "OPN-006": defineEndpoint("OPN-006", "openapi", "POST", "/openapi/v1/flow/tasks/{taskId}/actions", "AK/SK", "flow:task:handle"),
  "OPN-007": defineEndpoint("OPN-007", "openapi", "GET", "/openapi/v1/files/{fileId}/download", "AK/SK", "file:download"),

  "AUD-001": defineEndpoint("AUD-001", "audit", "GET", "/api/v1/systems/{systemId}/audit/operation-logs", "Bearer", "AUDIT_OPERATION_VIEW"),
  "AUD-002": defineEndpoint("AUD-002", "audit", "GET", "/api/v1/systems/{systemId}/audit/request-logs", "Bearer", "AUDIT_REQUEST_VIEW"),
  "AUD-003": defineEndpoint("AUD-003", "audit", "GET", "/api/v1/systems/{systemId}/audit/error-logs", "Bearer", "AUDIT_ERROR_VIEW"),
  "AUD-004": defineEndpoint("AUD-004", "audit", "GET", "/api/v1/systems/{systemId}/audit/record-changes", "Bearer", "AUDIT_RECORD_VIEW"),
  "AUD-005": defineEndpoint("AUD-005", "audit", "GET", "/api/v1/systems/{systemId}/audit/openapi-logs", "Bearer", "OPENAPI_LOG_VIEW"),
  "AUD-006": defineEndpoint("AUD-006", "audit", "GET", "/api/v1/platform/audit/operation-logs", "Bearer", "PLAT_AUDIT_VIEW"),
  "AUD-007": defineEndpoint("AUD-007", "audit", "GET", "/api/v1/systems/{systemId}/audit/logs/{logId}"),
  "AUD-008": defineEndpoint("AUD-008", "audit", "GET", "/api/v1/platform/audit/logs/{logId}"),

  "OPS-001": defineEndpoint("OPS-001", "ops", "GET", "/api/v1/ops/health", "Bearer", "OPS_HEALTH_VIEW"),
  "OPS-002": defineEndpoint("OPS-002", "ops", "GET", "/api/v1/ops/config-check", "Bearer", "OPS_CONFIG_VIEW"),
  "OPS-003": defineEndpoint("OPS-003", "ops", "GET", "/api/v1/ops/version", "Bearer", "OPS_VERSION_VIEW"),
  "OPS-004": defineEndpoint("OPS-004", "ops", "GET", "/api/v1/ops/migration/status", "Bearer", "OPS_MIGRATION_VIEW"),
  "OPS-005": defineEndpoint("OPS-005", "ops", "PUT", "/api/v1/ops/runtime-configs/{configKey}", "Bearer", "OPS_CONFIG_EDIT", "ENH"),
  "OPS-006": defineEndpoint("OPS-006", "ops", "GET", "/api/v1/ops/health/components"),

  "GEN-001": defineEndpoint("GEN-001", "generator", "GET", "/api/v1/ops/generator/table-mappings", "Bearer", "GENERATOR_VIEW", "PLACEHOLDER/INTERNAL"),
  "GEN-002": defineEndpoint("GEN-002", "generator", "POST", "/api/v1/ops/generator/tasks", "Bearer", "GENERATOR_RUN", "PLACEHOLDER/INTERNAL"),
  "GEN-003": defineEndpoint("GEN-003", "generator", "GET", "/api/v1/ops/generator/tasks/{taskId}", "Bearer", "GENERATOR_VIEW", "PLACEHOLDER/INTERNAL"),
  "GEN-004": defineEndpoint("GEN-004", "generator", "GET", "/api/v1/ops/generator/tasks/{taskId}/report", "Bearer", "GENERATOR_VIEW", "PLACEHOLDER/INTERNAL"),
} as const;

export type ApiEndpointId = keyof typeof API_ENDPOINTS;
export type EndpointGroup<TId extends ApiEndpointId> = (typeof API_ENDPOINTS)[TId]["group"];

export const API_GROUPS: Record<ApiGroup, ApiEndpointId[]> = Object.values(API_ENDPOINTS).reduce(
  (groups, endpoint) => {
    groups[endpoint.group].push(endpoint.id as ApiEndpointId);
    return groups;
  },
  {
    auth: [],
    platform: [],
    system: [],
    member: [],
    rbac: [],
    dict: [],
    app: [],
    module: [],
    field: [],
    ui: [],
    runtime: [],
    flow: [],
    file: [],
    export: [],
    import: [],
    openapi: [],
    audit: [],
    ops: [],
    generator: [],
  } as Record<ApiGroup, ApiEndpointId[]>,
);

export function getEndpointDefinition<TId extends ApiEndpointId>(apiId: TId): (typeof API_ENDPOINTS)[TId] {
  return API_ENDPOINTS[apiId];
}
