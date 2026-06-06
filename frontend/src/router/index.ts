import { API_ENDPOINTS, type ApiEndpointId } from "../api/endpoints";
import type { RequiredContext, SystemContextStore } from "../stores/systemContext";
import type { AuthStore } from "../stores/auth";
import type { PermissionRequirement, PermissionStore } from "../stores/permission";

export type AppLayoutKey = "auth" | "platform" | "system" | "plain";
export type RouteSection =
  | "auth"
  | "platform"
  | "system"
  | "rbac"
  | "app"
  | "runtime"
  | "flow"
  | "file-export"
  | "openapi"
  | "audit-ops";

export interface AppRouteMeta {
  title: string;
  section: RouteSection;
  layout: AppLayoutKey;
  public?: boolean;
  requiresAuth?: boolean;
  requiredContext?: RequiredContext;
  permission?: PermissionRequirement;
  apiIds: ApiEndpointId[];
  placeholderOwner:
    | "FE-003"
    | "FE-004"
    | "FE-005"
    | "FE-006"
    | "FE-008"
    | "FE-009"
    | "FE-010"
    | "FE-011"
    | "FE-002";
}

export interface AppRouteRecord {
  name: string;
  path: string;
  redirect?: string;
  meta: AppRouteMeta;
}

export interface RouteLocationLike {
  name?: string;
  path: string;
  params?: Record<string, string | number | undefined>;
  query?: Record<string, string | number | undefined>;
}

export interface GuardRedirect {
  type: "redirect";
  to: string;
  reason: "AUTH_REQUIRED" | "CONTEXT_REQUIRED";
  missing?: string[];
}

export interface GuardBlock {
  type: "block";
  reason: "PERMISSION_DENIED" | "CONTEXT_DISABLED" | "ROUTE_NOT_FOUND";
  requestIdRequired: boolean;
  message: string;
}

export interface GuardAllow {
  type: "allow";
  route: AppRouteRecord;
}

export type GuardResult = GuardAllow | GuardRedirect | GuardBlock;

export const LOGIN_ROUTE = "/auth/login";
export const DEFAULT_AUTHENTICATED_ROUTE = "/platform/my-systems";

export const APP_ROUTES: AppRouteRecord[] = [
  route("auth.login", "/auth/login", "Login", "auth", "auth", ["AUTH-002"], "FE-003", { public: true }),
  route("auth.register", "/auth/register", "Register", "auth", "auth", ["AUTH-001"], "FE-003", { public: true }),
  route("auth.resetPassword", "/auth/password-reset", "Reset password", "auth", "auth", ["AUTH-006"], "FE-003", {
    public: true,
  }),
  route("platform.mySystems", "/platform/my-systems", "My systems", "platform", "platform", ["PLAT-001"], "FE-003", {
    requiresAuth: true,
  }),
  route("platform.systems", "/platform/systems", "Platform systems", "platform", "platform", ["PLAT-003"], "FE-004", {
    requiresAuth: true,
    permission: { anyOperations: ["PLAT_SYSTEM_VIEW"] },
  }),
  route(
    "platform.accounts",
    "/platform/accounts",
    "Platform accounts",
    "platform",
    "platform",
    ["PLAT-006", "PLAT-007", "PLAT-008", "PLAT-013", "PLAT-014", "PLAT-015", "PLAT-016"],
    "FE-004",
    {
      requiresAuth: true,
      permission: { anyOperations: ["PLAT_ACCOUNT_VIEW"] },
    },
  ),
  route(
    "platform.roles",
    "/platform/roles",
    "Platform roles",
    "platform",
    "platform",
    ["PLAT-009", "PLAT-010", "PLAT-017", "PLAT-018", "PLAT-019", "PLAT-020"],
    "FE-004",
    {
      requiresAuth: true,
      permission: { anyOperations: ["PLAT_ROLE_VIEW"] },
    },
  ),
  route("platform.configs", "/platform/configs", "Platform configs", "platform", "platform", ["PLAT-011", "PLAT-012"], "FE-004", {
    requiresAuth: true,
    permission: { anyOperations: ["PLAT_CONFIG_VIEW"] },
  }),
  route("system.profile", "/systems/:systemId/profile", "System profile", "system", "system", ["SYS-001", "SYS-002", "SYS-003"], "FE-005", {
    requiresAuth: true,
    requiredContext: { system: true, member: true },
    permission: { anyOperations: ["SYS_PROFILE_VIEW"] },
  }),
  route("system.tenants", "/systems/:systemId/tenants", "Tenants", "system", "system", ["SYS-004", "SYS-005", "SYS-006", "SYS-007"], "FE-005", {
    requiresAuth: true,
    requiredContext: { system: true, member: true },
    permission: { anyOperations: ["SYS_TENANT_VIEW"] },
  }),
  route("system.members", "/systems/:systemId/members", "Members", "rbac", "system", ["MEM-001", "MEM-002", "MEM-003", "MEM-004", "MEM-005", "MEM-006", "MEM-007"], "FE-005", {
    requiresAuth: true,
    requiredContext: { system: true, member: true },
    permission: { anyOperations: ["SYS_MEMBER_VIEW"] },
  }),
  route("system.departments", "/systems/:systemId/departments", "Departments", "rbac", "system", ["RBAC-001", "RBAC-002", "RBAC-003", "RBAC-004"], "FE-005", {
    requiresAuth: true,
    requiredContext: { system: true, member: true },
    permission: { anyOperations: ["SYS_DEPT_VIEW"] },
  }),
  route("system.roles", "/systems/:systemId/roles", "System roles", "rbac", "system", ["RBAC-005", "RBAC-006", "RBAC-007", "RBAC-008", "RBAC-009", "RBAC-010", "RBAC-011", "RBAC-012", "RBAC-013"], "FE-005", {
    requiresAuth: true,
    requiredContext: { system: true, member: true },
    permission: { anyOperations: ["SYS_ROLE_VIEW"] },
  }),
  route("system.dict", "/systems/:systemId/dict", "Dictionaries", "rbac", "system", ["DICT-001", "DICT-002", "DICT-003", "DICT-004", "DICT-005", "DICT-006", "DICT-007", "DICT-008", "DICT-009", "DICT-010", "DICT-011"], "FE-005", {
    requiresAuth: true,
    requiredContext: { system: true, member: true },
    permission: { anyOperations: ["DICT_VIEW"] },
  }),
  route("apps.list", "/systems/:systemId/apps", "Applications", "app", "system", ["APP-001", "APP-002", "APP-003", "APP-004", "APP-005", "APP-006", "APP-007"], "FE-006", {
    requiresAuth: true,
    requiredContext: { system: true, tenant: true, member: true },
    permission: { anyOperations: ["APP_VIEW"] },
  }),
  route("modules.list", "/systems/:systemId/apps/:appId/modules", "Modules", "app", "system", ["MOD-001", "MOD-002", "MOD-003", "MOD-004", "MOD-005", "MOD-006", "MOD-007"], "FE-006", {
    requiresAuth: true,
    requiredContext: { system: true, tenant: true, member: true },
    permission: { anyOperations: ["MODULE_VIEW"] },
  }),
  route("modules.fields", "/systems/:systemId/modules/:moduleId/fields", "Fields", "app", "system", ["FIELD-001", "FIELD-002", "FIELD-003", "FIELD-004", "FIELD-005"], "FE-006", {
    requiresAuth: true,
    requiredContext: { system: true, tenant: true, member: true },
    permission: { anyOperations: ["FIELD_VIEW"] },
  }),
  route("modules.ui", "/systems/:systemId/modules/:moduleId/ui", "Page config", "app", "system", ["UI-001", "UI-002", "UI-003", "UI-004", "UI-005", "UI-006", "UI-007", "UI-008", "UI-009"], "FE-006", {
    requiresAuth: true,
    requiredContext: { system: true, tenant: true, member: true },
    permission: { anyOperations: ["PAGE_VIEW"] },
  }),
  route("runtime.home", "/systems/:systemId/runtime", "Runtime", "runtime", "system", ["RUN-001"], "FE-008", {
    requiresAuth: true,
    requiredContext: { system: true, tenant: true, member: true },
  }),
  route("runtime.module", "/systems/:systemId/runtime/modules/:moduleId", "Runtime module", "runtime", "system", ["RUN-002", "RUN-003", "RUN-004", "RUN-005", "RUN-006", "RUN-007", "RUN-008", "RUN-009", "RUN-010"], "FE-008", {
    requiresAuth: true,
    requiredContext: { system: true, tenant: true, member: true },
    permission: { anyOperations: ["RECORD_VIEW"] },
  }),
  route("flow.templates", "/systems/:systemId/flow/templates", "Flow templates", "flow", "system", ["FLOW-001", "FLOW-002", "FLOW-003", "FLOW-004", "FLOW-005", "FLOW-006", "FLOW-019", "FLOW-020", "FLOW-021"], "FE-009", {
    requiresAuth: true,
    requiredContext: { system: true, tenant: true, member: true },
    permission: { anyOperations: ["FLOW_TEMPLATE_VIEW"] },
  }),
  route("flow.workbench", "/systems/:systemId/flow/workbench", "Flow workbench", "flow", "system", ["FLOW-007", "FLOW-008", "FLOW-009", "FLOW-010", "FLOW-011", "FLOW-012", "FLOW-013", "FLOW-014", "FLOW-015", "FLOW-016", "FLOW-017", "FLOW-018"], "FE-009", {
    requiresAuth: true,
    requiredContext: { system: true, tenant: true, member: true },
  }),
  route("files.center", "/systems/:systemId/files", "Files", "file-export", "system", ["FILE-001", "FILE-002", "FILE-003", "FILE-004", "FILE-005", "FILE-006", "FILE-007"], "FE-010", {
    requiresAuth: true,
    requiredContext: { system: true, tenant: true, member: true },
    permission: { anyOperations: ["FILE_VIEW"] },
  }),
  route("exports.jobs", "/systems/:systemId/exports", "Exports", "file-export", "system", ["EXP-001", "EXP-002", "EXP-003", "EXP-004", "EXP-005", "EXP-006", "EXP-007", "EXP-008"], "FE-010", {
    requiresAuth: true,
    requiredContext: { system: true, tenant: true, member: true },
    permission: { anyOperations: ["EXPORT_JOB_VIEW"] },
  }),
  route("openapi.clients", "/systems/:systemId/openapi", "OpenAPI", "openapi", "system", ["OPM-001", "OPM-002", "OPM-003", "OPM-004", "OPM-005", "OPM-006", "OPM-007", "OPM-008", "OPM-009"], "FE-011", {
    requiresAuth: true,
    requiredContext: { system: true, tenant: true, member: true },
    permission: { anyOperations: ["OPENAPI_CLIENT_VIEW"] },
  }),
  route("audit.system", "/systems/:systemId/audit", "System audit", "audit-ops", "system", ["AUD-001", "AUD-002", "AUD-003", "AUD-004", "AUD-005", "AUD-007"], "FE-011", {
    requiresAuth: true,
    requiredContext: { system: true, member: true },
    permission: { anyOperations: ["AUDIT_OPERATION_VIEW", "AUDIT_REQUEST_VIEW", "AUDIT_ERROR_VIEW"] },
  }),
  route("audit.platform", "/platform/audit", "Platform audit", "audit-ops", "platform", ["AUD-006", "AUD-008"], "FE-011", {
    requiresAuth: true,
    permission: { anyOperations: ["PLAT_AUDIT_VIEW"] },
  }),
  route("ops.health", "/ops", "Operations", "audit-ops", "platform", ["OPS-001", "OPS-002", "OPS-003", "OPS-004", "OPS-005", "OPS-006"], "FE-011", {
    requiresAuth: true,
    permission: { anyOperations: ["OPS_HEALTH_VIEW", "OPS_CONFIG_VIEW", "OPS_VERSION_VIEW", "OPS_MIGRATION_VIEW"] },
  }),
];

export const ROUTE_BY_NAME = APP_ROUTES.reduce<Record<string, AppRouteRecord>>((mapping, item) => {
  mapping[item.name] = item;
  return mapping;
}, {});

export const ROUTES_BY_SECTION = APP_ROUTES.reduce<Record<RouteSection, AppRouteRecord[]>>(
  (mapping, item) => {
    mapping[item.meta.section].push(item);
    return mapping;
  },
  {
    auth: [],
    platform: [],
    system: [],
    rbac: [],
    app: [],
    runtime: [],
    flow: [],
    "file-export": [],
    openapi: [],
    "audit-ops": [],
  },
);

export interface CreateRouteGuardOptions {
  auth: AuthStore;
  systemContext: SystemContextStore;
  permission: PermissionStore;
}

export function createRouteGuard(options: CreateRouteGuardOptions) {
  return function guard(location: RouteLocationLike): GuardResult {
    const routeRecord = resolveRoute(location);
    if (!routeRecord) {
      return {
        type: "block",
        reason: "ROUTE_NOT_FOUND",
        requestIdRequired: false,
        message: "Route is not declared in FE-002 routing contract.",
      };
    }

    const meta = routeRecord.meta;
    const authState = options.auth.getState();
    if (meta.requiresAuth && authState.status !== "authenticated") {
      return {
        type: "redirect",
        to: `${LOGIN_ROUTE}?redirect=${encodeURIComponent(location.path)}`,
        reason: "AUTH_REQUIRED",
      };
    }

    const missing = meta.requiredContext ? options.systemContext.validate(meta.requiredContext) : [];
    if (missing.length > 0) {
      return {
        type: "redirect",
        to: DEFAULT_AUTHENTICATED_ROUTE,
        reason: "CONTEXT_REQUIRED",
        missing,
      };
    }

    const contextState = options.systemContext.getState();
    if (meta.requiredContext && contextState.status === "disabled") {
      return {
        type: "block",
        reason: "CONTEXT_DISABLED",
        requestIdRequired: true,
        message: contextState.disabledReason ?? "SYS_DISABLED",
      };
    }

    const decision =
      meta.layout === "platform"
        ? decidePlatformPermission(options.auth, meta.permission)
        : options.permission.decide(meta.permission);
    if (!decision.visible || !decision.enabled) {
      return {
        type: "block",
        reason: "PERMISSION_DENIED",
        requestIdRequired: true,
        message: decision.disabledReason ?? "PERM_DENIED",
      };
    }

    return {
      type: "allow",
      route: routeRecord,
    };
  };
}

function decidePlatformPermission(auth: AuthStore, permission?: PermissionRequirement) {
  if (!permission) {
    return { visible: true, enabled: true };
  }
  if (permission.allOperations?.some((item) => !auth.hasPlatformPermission(item))) {
    return { visible: true, enabled: false, disabledReason: "PERM_DENIED" };
  }
  if (permission.anyOperations && !permission.anyOperations.some((item) => auth.hasPlatformPermission(item))) {
    return { visible: true, enabled: false, disabledReason: "PERM_DENIED" };
  }
  return { visible: true, enabled: true };
}

export function resolveRoute(location: RouteLocationLike): AppRouteRecord | undefined {
  if (location.name && ROUTE_BY_NAME[location.name]) {
    return ROUTE_BY_NAME[location.name];
  }
  return APP_ROUTES.find((item) => matchPath(item.path, location.path));
}

export function buildPath(routeName: string, params: Record<string, string | number | undefined> = {}): string {
  const routeRecord = ROUTE_BY_NAME[routeName];
  if (!routeRecord) {
    throw new Error(`Unknown route name: ${routeName}`);
  }
  return routeRecord.path.replace(/:([A-Za-z0-9_]+)/g, (_, key: string) => {
    const value = params[key];
    if (value === undefined || value === "") {
      throw new Error(`Missing route param: ${key}`);
    }
    return encodeURIComponent(String(value));
  });
}

export function assertRouteApiIds(): void {
  APP_ROUTES.forEach((routeRecord) => {
    routeRecord.meta.apiIds.forEach((apiId) => {
      if (!API_ENDPOINTS[apiId]) {
        throw new Error(`Route ${routeRecord.name} references unknown API ID ${apiId}`);
      }
    });
  });
}

function route(
  name: string,
  path: string,
  title: string,
  section: RouteSection,
  layout: AppLayoutKey,
  apiIds: ApiEndpointId[],
  placeholderOwner: AppRouteMeta["placeholderOwner"],
  extra: Partial<Omit<AppRouteMeta, "title" | "section" | "layout" | "apiIds" | "placeholderOwner">> = {},
): AppRouteRecord {
  return {
    name,
    path,
    meta: {
      title,
      section,
      layout,
      apiIds,
      placeholderOwner,
      ...extra,
    },
  };
}

function matchPath(pattern: string, actualPath: string): boolean {
  const patternParts = pattern.split("/").filter(Boolean);
  const actualParts = actualPath.split("?")[0].split("/").filter(Boolean);
  if (patternParts.length !== actualParts.length) {
    return false;
  }
  return patternParts.every((part, index) => part.startsWith(":") || part === actualParts[index]);
}
