import type {
  ApiClient,
  ApiEndpointId,
  ApiRequestOptions,
  ApiResponse,
  EffectivePermissionVO,
  EntityId,
  FieldPermission,
  PageQuery,
} from "../../api";
import type { AuthStore, ErrorStore, PermissionRequirement, PermissionStore, SystemContextStore } from "../../stores";
import type {
  DepartmentNodeVO,
  DepartmentSaveBO,
  DictCacheRefreshVO,
  DictItemQuery,
  DictItemSaveBO,
  DictItemUpdateBO,
  DictItemVO,
  DictStatus,
  DictTypeQuery,
  DictTypeSaveBO,
  DictTypeUpdateBO,
  DictTypeVO,
  DictUsageVO,
  DictWriteResult,
  MemberDetailVO,
  MemberInviteBO,
  MemberPageResult,
  MemberQuery,
  MemberRoleAssignBO,
  MemberUpdateBO,
  PageActionState,
  PageContextBlock,
  PageRequestState,
  PermissionCatalogVO,
  RolePermissionDetailVO,
  RolePermissionSaveBO,
  RoleSaveBO,
  RoleVO,
  StatusChangeBO,
  SystemProfileSaveBO,
  SystemProfileVO,
  TenantSaveBO,
  TenantSwitchBO,
  TenantVO,
} from "./types";

export interface SystemPageModelDeps {
  apiClient: ApiClient;
  auth: AuthStore;
  systemContext: SystemContextStore;
  permission: PermissionStore;
  error: ErrorStore;
}

export interface PageDataResult<TData> {
  data?: TData;
  state: PageRequestState;
}

export interface SystemPageRuntime {
  contextBlock(): PageContextBlock;
  action(apiId: ApiEndpointId, label: string, requirement: PermissionRequirement): PageActionState;
  field(fieldCode: string): FieldPermission | undefined;
  call<TData, TBody = unknown, TQuery = Record<string, unknown>>(
    apiId: ApiEndpointId,
    options?: ApiRequestOptions<TBody, TQuery>,
  ): Promise<PageDataResult<ApiResponse<TData>>>;
}

const SYSTEM_MEMBER_CONTEXT = { system: true, member: true };

export function createSystemPageRuntime(deps: SystemPageModelDeps): SystemPageRuntime {
  function contextBlock(): PageContextBlock {
    const missing = deps.systemContext.validate(SYSTEM_MEMBER_CONTEXT);
    const state = deps.systemContext.getState();
    if (missing.length > 0) {
      return {
        blocked: true,
        missing,
        message: "SYS_CONTEXT_REQUIRED",
      };
    }
    if (state.status === "disabled") {
      return {
        blocked: true,
        missing: [],
        message: state.disabledReason ?? "SYS_DISABLED",
      };
    }
    return {
      blocked: false,
      missing: [],
    };
  }

  async function call<TData, TBody = unknown, TQuery = Record<string, unknown>>(
    apiId: ApiEndpointId,
    options: ApiRequestOptions<TBody, TQuery> = {},
  ): Promise<PageDataResult<ApiResponse<TData>>> {
    const block = contextBlock();
    if (block.blocked) {
      return {
        state: {
          loading: false,
          empty: true,
          errorMessage: block.message,
          retryable: false,
        },
      };
    }

    try {
      const requestId = options.context?.requestId;
      const response = await deps.apiClient.call<TData, TBody, TQuery>(apiId, {
        ...options,
        pathParams: deps.systemContext.toPathParams(options.pathParams),
        context: {
          ...deps.auth.toApiContext(requestId),
          tenantId: deps.systemContext.toTenantHeader(),
          ...options.context,
        },
      });
      return {
        data: response,
        state: {
          loading: false,
          empty: isEmptyData(response.data),
          requestId: response.requestId,
          retryable: false,
        },
      };
    } catch (error) {
      const display = deps.error.capture(error);
      return {
        state: {
          loading: false,
          empty: true,
          errorMessage: `${display.code}: ${display.message}`,
          requestId: display.requestId,
          retryable: display.retryable,
        },
      };
    }
  }

  return {
    contextBlock,
    action(apiId, label, requirement) {
      const decision = deps.permission.decide(requirement);
      return {
        actionCode: requirement.anyOperations?.[0] ?? requirement.allOperations?.[0] ?? apiId,
        apiId,
        label,
        ...decision,
      };
    },
    field(fieldCode) {
      return deps.permission.field(fieldCode);
    },
    call,
  };
}

export function createSystemManagementPageModel(deps: SystemPageModelDeps) {
  const runtime = createSystemPageRuntime(deps);

  return {
    routeNames: ["system.profile", "system.tenants"] as const,
    apiIds: ["SYS-002", "SYS-003", "SYS-004", "SYS-005", "SYS-006", "SYS-007"] as const,
    actions: {
      viewProfile: runtime.action("SYS-002", "View system profile", { anyOperations: ["SYS_PROFILE_VIEW"] }),
      editProfile: runtime.action("SYS-003", "Edit system profile", { anyOperations: ["SYS_PROFILE_EDIT"] }),
      viewTenants: runtime.action("SYS-004", "View tenants", { anyOperations: ["SYS_TENANT_VIEW"] }),
      createTenant: runtime.action("SYS-005", "Create tenant", { anyOperations: ["SYS_TENANT_CREATE"] }),
      changeTenantStatus: runtime.action("SYS-006", "Change tenant status", { anyOperations: ["SYS_TENANT_STATUS"] }),
    },
    loadProfile: () => callData<SystemProfileVO>(runtime, "SYS-002"),
    saveProfile: (body: SystemProfileSaveBO) => callData<SystemProfileVO, SystemProfileSaveBO>(runtime, "SYS-003", { body }),
    loadTenants: () => callData<TenantVO[]>(runtime, "SYS-004"),
    createTenant: (body: TenantSaveBO) => callData<TenantVO, TenantSaveBO>(runtime, "SYS-005", { body }),
    changeTenantStatus: (tenantId: EntityId, body: StatusChangeBO) =>
      callData<TenantVO, StatusChangeBO>(runtime, "SYS-006", {
        pathParams: { tenantId },
        body,
      }),
    async switchTenant(body: TenantSwitchBO): Promise<PageDataResult<TenantVO>> {
      const result = await callData<TenantVO, TenantSwitchBO>(runtime, "SYS-007", { body });
      if (result.data) {
        deps.permission.markStale();
      }
      return result;
    },
    readonlyFields: {
      systemId: true,
      code: true,
      tenantMode: true,
    },
  };
}

export function createMemberPageModel(deps: SystemPageModelDeps) {
  const runtime = createSystemPageRuntime(deps);

  return {
    routeName: "system.members" as const,
    apiIds: ["MEM-001", "MEM-002", "MEM-003", "MEM-004", "MEM-005", "MEM-006", "MEM-007"] as const,
    actions: {
      view: runtime.action("MEM-001", "View members", { anyOperations: ["SYS_MEMBER_VIEW"] }),
      invite: runtime.action("MEM-002", "Invite member", { anyOperations: ["SYS_MEMBER_INVITE"] }),
      edit: runtime.action("MEM-004", "Edit member extension", { anyOperations: ["SYS_MEMBER_EDIT"] }),
      changeStatus: runtime.action("MEM-005", "Change member status", { anyOperations: ["SYS_MEMBER_STATUS"] }),
      assignRoles: runtime.action("MEM-006", "Assign member roles", { anyOperations: ["SYS_ROLE_ASSIGN"] }),
    },
    loadMembers: (query: MemberQuery = {}) => callData<MemberPageResult, undefined, MemberQuery>(runtime, "MEM-001", { query }),
    inviteMember: (body: MemberInviteBO) => callData<MemberDetailVO, MemberInviteBO>(runtime, "MEM-002", { body }),
    loadMemberDetail: (memberId: EntityId) =>
      callData<MemberDetailVO>(runtime, "MEM-003", {
        pathParams: { memberId },
      }),
    updateMember: (memberId: EntityId, body: MemberUpdateBO) =>
      callData<MemberDetailVO, MemberUpdateBO>(runtime, "MEM-004", {
        pathParams: { memberId },
        body,
      }),
    changeMemberStatus: (memberId: EntityId, body: StatusChangeBO) =>
      callData<MemberDetailVO, StatusChangeBO>(runtime, "MEM-005", {
        pathParams: { memberId },
        body,
      }),
    assignRoles: (memberId: EntityId, body: MemberRoleAssignBO) =>
      callData<MemberDetailVO, MemberRoleAssignBO>(runtime, "MEM-006", {
        pathParams: { memberId },
        body,
      }),
    loadCurrentMember: () => callData<MemberDetailVO>(runtime, "MEM-007"),
    readonlyFields: {
      accountId: true,
      loginName: true,
      displayName: true,
    },
    writableFields: ["tenantIds", "deptIds", "postName", "roleIds", "status"] as const,
  };
}

export function createDepartmentPageModel(deps: SystemPageModelDeps) {
  const runtime = createSystemPageRuntime(deps);

  return {
    routeName: "system.departments" as const,
    apiIds: ["RBAC-001", "RBAC-002", "RBAC-003", "RBAC-004"] as const,
    actions: {
      view: runtime.action("RBAC-001", "View department tree", { anyOperations: ["SYS_DEPT_VIEW"] }),
      create: runtime.action("RBAC-002", "Create department", { anyOperations: ["SYS_DEPT_CREATE"] }),
      edit: runtime.action("RBAC-003", "Edit department", { anyOperations: ["SYS_DEPT_EDIT"] }),
      delete: runtime.action("RBAC-004", "Delete department", { anyOperations: ["SYS_DEPT_DELETE"] }),
    },
    loadTree: () => callData<DepartmentNodeVO[]>(runtime, "RBAC-001"),
    createDepartment: (body: DepartmentSaveBO) => callData<DepartmentNodeVO, DepartmentSaveBO>(runtime, "RBAC-002", { body }),
    updateDepartment: (deptId: EntityId, body: DepartmentSaveBO) =>
      callData<DepartmentNodeVO, DepartmentSaveBO>(runtime, "RBAC-003", {
        pathParams: { deptId },
        body,
      }),
    deleteDepartment: (deptId: EntityId) =>
      callData<{ deleted: boolean }>(runtime, "RBAC-004", {
        pathParams: { deptId },
      }),
    deleteDisabledReason(node: DepartmentNodeVO): string | undefined {
      if ((node.children?.length ?? 0) > 0) {
        return "Department has child departments.";
      }
      if ((node.memberCount ?? 0) > 0) {
        return "SYS_DEPT_HAS_MEMBER";
      }
      return undefined;
    },
  };
}

export function createRolePermissionPageModel(deps: SystemPageModelDeps) {
  const runtime = createSystemPageRuntime(deps);

  return {
    routeName: "system.roles" as const,
    apiIds: ["RBAC-005", "RBAC-006", "RBAC-007", "RBAC-008", "RBAC-009", "RBAC-010", "RBAC-011", "RBAC-012", "RBAC-013"] as const,
    actions: {
      viewRoles: runtime.action("RBAC-005", "View roles", { anyOperations: ["SYS_ROLE_VIEW"] }),
      createRole: runtime.action("RBAC-006", "Create role", { anyOperations: ["SYS_ROLE_CREATE"] }),
      editRole: runtime.action("RBAC-007", "Edit role", { anyOperations: ["SYS_ROLE_EDIT"] }),
      changeRoleStatus: runtime.action("RBAC-008", "Change role status", { anyOperations: ["SYS_ROLE_STATUS"] }),
      savePermissions: runtime.action("RBAC-009", "Save role permissions", { anyOperations: ["SYS_ROLE_PERMISSION_EDIT"] }),
    },
    loadRoles: (query: PageQuery = {}) => callData<RoleVO[], undefined, PageQuery>(runtime, "RBAC-005", { query }),
    createRole: (body: RoleSaveBO) => callData<RoleVO, RoleSaveBO>(runtime, "RBAC-006", { body }),
    updateRole: (roleId: EntityId, body: RoleSaveBO) =>
      callData<RoleVO, RoleSaveBO>(runtime, "RBAC-007", {
        pathParams: { roleId },
        body,
      }),
    changeRoleStatus: (roleId: EntityId, body: StatusChangeBO) =>
      callData<RoleVO, StatusChangeBO>(runtime, "RBAC-008", {
        pathParams: { roleId },
        body,
      }),
    async savePermissions(roleId: EntityId, body: RolePermissionSaveBO): Promise<PageDataResult<RolePermissionDetailVO>> {
      const result = await callData<RolePermissionDetailVO, RolePermissionSaveBO>(runtime, "RBAC-009", {
        pathParams: { roleId },
        body,
      });
      if (result.data) {
        deps.permission.markStale();
      }
      return result;
    },
    refreshEffectivePermissions: async () => {
      const effective = await callData<EffectivePermissionVO>(runtime, "RBAC-010");
      const menus = await callData<RuntimeMenuNode[]>(runtime, "RBAC-011");
      if (effective.data) {
        deps.permission.setEffectivePermission(effective.data, flattenRuntimeMenuCodes(menus.data ?? []));
      }
      return {
        effective,
        menus,
      };
    },
    loadRolePermissions: (roleId: EntityId) =>
      callData<RolePermissionDetailVO>(runtime, "RBAC-012", {
        pathParams: { roleId },
      }),
    loadPermissionCatalog: () => callData<PermissionCatalogVO>(runtime, "RBAC-013"),
    fieldPermissionSummary(fieldCode: string): FieldPermission | undefined {
      return runtime.field(fieldCode);
    },
  };
}

export function createDictPageModel(deps: SystemPageModelDeps) {
  const runtime = createSystemPageRuntime(deps);

  return {
    routeName: "system.dict" as const,
    apiIds: ["DICT-001", "DICT-002", "DICT-003", "DICT-004", "DICT-005", "DICT-006", "DICT-007", "DICT-008", "DICT-009", "DICT-010", "DICT-011"] as const,
    actions: {
      view: runtime.action("DICT-001", "View dictionaries", { anyOperations: ["DICT_VIEW"] }),
      createType: runtime.action("DICT-002", "Create dictionary type", { anyOperations: ["DICT_CREATE"] }),
      editType: runtime.action("DICT-003", "Edit dictionary type", { anyOperations: ["DICT_EDIT"] }),
      changeTypeStatus: runtime.action("DICT-004", "Change dictionary type status", { anyOperations: ["DICT_STATUS"] }),
      createItem: runtime.action("DICT-006", "Create dictionary item", { anyOperations: ["DICT_ITEM_CREATE"] }),
      editItem: runtime.action("DICT-007", "Edit dictionary item", { anyOperations: ["DICT_ITEM_EDIT"] }),
      changeItemStatus: runtime.action("DICT-008", "Change dictionary item status", { anyOperations: ["DICT_ITEM_STATUS"] }),
      deleteType: runtime.action("DICT-010", "Delete dictionary type", { anyOperations: ["DICT_DELETE"] }),
      deleteItem: runtime.action("DICT-011", "Delete dictionary item", { anyOperations: ["DICT_ITEM_DELETE"] }),
    },
    loadTypes: (query: DictTypeQuery = {}) => callData<DictTypeVO[], undefined, DictTypeQuery>(runtime, "DICT-001", { query }),
    async createType(body: DictTypeSaveBO): Promise<PageDataResult<DictWriteResult<DictTypeVO>>> {
      return callDictWrite<DictTypeVO, DictTypeSaveBO>(runtime, "DICT-002", { body });
    },
    async updateType(dictTypeId: EntityId, body: DictTypeUpdateBO): Promise<PageDataResult<DictWriteResult<DictTypeVO>>> {
      return callDictWrite<DictTypeVO, DictTypeUpdateBO>(runtime, "DICT-003", {
        pathParams: { dictTypeId },
        body,
      });
    },
    async changeTypeStatus(
      dictTypeId: EntityId,
      body: StatusChangeBO<Exclude<DictStatus, "DELETED">>,
    ): Promise<PageDataResult<DictWriteResult<DictTypeVO>>> {
      return callDictWrite<DictTypeVO, StatusChangeBO<Exclude<DictStatus, "DELETED">>>(runtime, "DICT-004", {
        pathParams: { dictTypeId },
        body,
      });
    },
    loadItems: (dictTypeId: EntityId, query: DictItemQuery = { treeMode: true }) =>
      callData<DictItemVO[], undefined, DictItemQuery>(runtime, "DICT-005", {
        pathParams: { dictTypeId },
        query,
      }),
    async createItem(dictTypeId: EntityId, body: DictItemSaveBO): Promise<PageDataResult<DictWriteResult<DictItemVO>>> {
      return callDictWrite<DictItemVO, DictItemSaveBO>(runtime, "DICT-006", {
        pathParams: { dictTypeId },
        body,
      });
    },
    async updateItem(dictItemId: EntityId, body: DictItemUpdateBO): Promise<PageDataResult<DictWriteResult<DictItemVO>>> {
      return callDictWrite<DictItemVO, DictItemUpdateBO>(runtime, "DICT-007", {
        pathParams: { dictItemId },
        body,
      });
    },
    async changeItemStatus(
      dictItemId: EntityId,
      body: StatusChangeBO<Exclude<DictStatus, "DELETED">>,
    ): Promise<PageDataResult<DictWriteResult<DictItemVO>>> {
      return callDictWrite<DictItemVO, StatusChangeBO<Exclude<DictStatus, "DELETED">>>(runtime, "DICT-008", {
        pathParams: { dictItemId },
        body,
      });
    },
    loadUsage: (dictTypeId: EntityId) =>
      callData<DictUsageVO>(runtime, "DICT-009", {
        pathParams: { dictTypeId },
      }),
    async deleteType(dictTypeId: EntityId): Promise<PageDataResult<DictWriteResult<{ deleted: boolean }>>> {
      return callDictWrite<{ deleted: boolean }>(runtime, "DICT-010", {
        pathParams: { dictTypeId },
      });
    },
    async deleteItem(dictItemId: EntityId): Promise<PageDataResult<DictWriteResult<{ deleted: boolean }>>> {
      return callDictWrite<{ deleted: boolean }>(runtime, "DICT-011", {
        pathParams: { dictItemId },
      });
    },
    usageDisabledReason(usage?: DictUsageVO, mode: "disable" | "delete" = "delete"): string | undefined {
      if (!usage) {
        return "DICT-009 usage check required before destructive dictionary operation.";
      }
      if (mode === "disable" && !usage.canDisable) {
        return usage.blockingReasons.join("; ") || "DICT_TYPE_IN_USE";
      }
      if (mode === "delete" && !usage.canDelete) {
        return usage.blockingReasons.join("; ") || "DICT_TYPE_IN_USE";
      }
      return undefined;
    },
  };
}

async function callData<TData, TBody = unknown, TQuery = Record<string, unknown>>(
  runtime: SystemPageRuntime,
  apiId: ApiEndpointId,
  options: ApiRequestOptions<TBody, TQuery> = {},
): Promise<PageDataResult<TData>> {
  const result = await runtime.call<TData, TBody, TQuery>(apiId, options);
  return {
    data: result.data?.data,
    state: result.state,
  };
}

async function callDictWrite<TData, TBody = unknown>(
  runtime: SystemPageRuntime,
  apiId: ApiEndpointId,
  options: ApiRequestOptions<TBody> = {},
): Promise<PageDataResult<DictWriteResult<TData>>> {
  const result = await runtime.call<TData | DictWriteResult<TData>, TBody>(apiId, options);
  if (!result.data) {
    return {
      state: result.state,
    };
  }
  return {
    data: normalizeDictWriteResult<TData>(result.data),
    state: result.state,
  };
}

function normalizeDictWriteResult<TData>(response: ApiResponse<TData | DictWriteResult<TData>>): DictWriteResult<TData> {
  const payload = response.data;
  if (isDictWriteResult<TData>(payload)) {
    return payload;
  }

  const cacheRefresh = readDictCacheRefresh(response.meta.dictCache);
  return {
    entity: payload as TData,
    cacheRefresh,
  };
}

function isDictWriteResult<TData>(payload: TData | DictWriteResult<TData>): payload is DictWriteResult<TData> {
  return isObject(payload) && "entity" in payload;
}

function readDictCacheRefresh(value: unknown): DictCacheRefreshVO | undefined {
  return isObject(value) && typeof value.dictTypeId === "string" && typeof value.cacheVersion === "number"
    ? (value as unknown as DictCacheRefreshVO)
    : undefined;
}

function isEmptyData(data: unknown): boolean {
  if (Array.isArray(data)) {
    return data.length === 0;
  }
  if (isObject(data) && Array.isArray(data.records)) {
    return data.records.length === 0;
  }
  return data === undefined || data === null;
}

interface RuntimeMenuNode {
  code?: string;
  menuCode?: string;
  children?: RuntimeMenuNode[];
}

function flattenRuntimeMenuCodes(nodes: RuntimeMenuNode[]): string[] {
  return nodes.flatMap((node) => {
    const current = node.menuCode ?? node.code;
    const children = flattenRuntimeMenuCodes(node.children ?? []);
    return current ? [current, ...children] : children;
  });
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
