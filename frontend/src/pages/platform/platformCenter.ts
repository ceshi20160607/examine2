import type { ApiClient, ApiContext } from "../../api/client";
import type { ApiEndpointId } from "../../api/endpoints";
import { API_ENDPOINTS } from "../../api/endpoints";
import type {
  AccountStatus,
  AvailableAction,
  EntityId,
  IsoDateTimeString,
  JsonValue,
  PageQuery,
  PageResult,
  SystemStatus,
} from "../../api/types";
import type { AuthStore } from "../../stores/auth";
import { authStore } from "../../stores/auth";
import type { ErrorStore, RequestErrorDisplay } from "../../stores/error";
import { errorStore } from "../../stores/error";

export type TenantMode = "SINGLE" | "MULTI";
export type PlatformRoleStatus = "ENABLED" | "DISABLED";
export type PlatformConfigValue = JsonValue;

export interface PlatformPageDeps {
  api: ApiClient;
  auth?: AuthStore;
  errors?: ErrorStore;
}

export interface PlatformPageState<TRecord> {
  loading: boolean;
  records: TRecord[];
  total: number;
  pageNo: number;
  pageSize: number;
  empty: boolean;
  error?: RequestErrorDisplay;
  lastRequestId?: string;
}

export interface PlatformDetailState<TDetail> {
  loading: boolean;
  detail?: TDetail;
  empty: boolean;
  error?: RequestErrorDisplay;
  lastRequestId?: string;
}

export interface PlatformMutationResult<TData> {
  data: TData;
  requestId: string;
  idempotencyReplay?: boolean;
}

export interface PlatformSystemListVO {
  systemId: EntityId;
  systemCode: string;
  systemName: string;
  tenantMode: TenantMode;
  status: SystemStatus;
  ownerAccountId?: EntityId;
  ownerDisplayName?: string;
  tenantCount?: number;
  memberCount?: number;
  appCount?: number;
  createdAt?: IsoDateTimeString;
  updatedAt?: IsoDateTimeString;
  availableActions?: AvailableAction[];
}

export interface PlatformSystemDetailVO extends PlatformSystemListVO {
  description?: string;
  defaultTenantId?: EntityId;
  initializedObjects?: InitializedObjectVO[];
  platformPermissions?: string[];
  auditSummary?: JsonValue;
}

export interface InitializedObjectVO {
  objectType: string;
  code: string;
  id: EntityId;
  status: string;
}

export interface PlatformSystemCreateBO {
  name: string;
  code: string;
  tenantMode: TenantMode;
  description?: string;
}

export interface PlatformSystemStatusBO {
  status: SystemStatus;
  reason?: string;
}

export interface PlatformAccountListVO {
  accountId: EntityId;
  loginName: string;
  displayName: string;
  mobile?: string;
  email?: string;
  status: AccountStatus;
  roleIds?: EntityId[];
  lastLoginAt?: IsoDateTimeString;
  createdAt?: IsoDateTimeString;
  updatedAt?: IsoDateTimeString;
  availableActions?: AvailableAction[];
}

export interface PlatformAccountDetailVO extends PlatformAccountListVO {
  platformRoles?: PlatformRoleVO[];
  systemMemberships?: PlatformAccountSystemMembershipVO[];
}

export interface PlatformAccountSystemMembershipVO {
  systemId: EntityId;
  systemName: string;
  memberId: EntityId;
  memberStatus?: "ENABLED" | "DISABLED";
  tenantNames?: string[];
  roleNames?: string[];
}

export interface PlatformAccountSaveBO {
  loginName: string;
  displayName?: string;
  mobile?: string;
  email?: string;
  initialPassword?: string;
}

export interface PlatformAccountUpdateBO {
  displayName?: string;
  mobile?: string;
  email?: string;
}

export interface PlatformAccountStatusBO {
  status: AccountStatus;
  reason?: string;
}

export interface PlatformAccountResetPasswordBO {
  newPassword?: string;
  forceChangeOnLogin?: boolean;
  reason?: string;
}

export interface PlatformAccountRoleAssignBO {
  roleIds: EntityId[];
}

export interface PlatformRoleVO {
  roleId: EntityId;
  code: string;
  name: string;
  description?: string;
  status: PlatformRoleStatus;
  protectedFlag?: boolean;
  menuIds?: EntityId[];
  operationCodes?: string[];
  accountCount?: number;
  createdAt?: IsoDateTimeString;
  updatedAt?: IsoDateTimeString;
  availableActions?: AvailableAction[];
}

export interface PlatformRoleSaveBO {
  code: string;
  name: string;
  description?: string;
}

export interface PlatformRoleStatusBO {
  status: PlatformRoleStatus;
  reason?: string;
}

export interface PlatformRolePermissionBO {
  menuIds: EntityId[];
  operationCodes: string[];
}

export interface PlatformPermissionCatalogVO {
  menus: PlatformMenuTreeVO[];
  operationCodes: string[];
}

export interface PlatformMenuTreeVO {
  menuId: EntityId;
  parentId?: EntityId;
  code: string;
  name: string;
  path?: string;
  icon?: string;
  status?: string;
  children?: PlatformMenuTreeVO[];
}

export interface PlatformOperationVO {
  operationCode: string;
  operationName: string;
  menuCode?: string;
  description?: string;
}

export interface PlatformConfigVO {
  configKey: string;
  configName: string;
  value?: PlatformConfigValue;
  sensitive: boolean;
  status?: string;
  remark?: string;
  updatedAt?: IsoDateTimeString;
  availableActions?: AvailableAction[];
}

export interface PlatformConfigUpdateBO {
  value: PlatformConfigValue;
  remark?: string;
}

export interface PlatformCenterPageModel {
  readonly apiIds: readonly ApiEndpointId[];
  systems: {
    query(query?: PageQuery): Promise<PlatformPageState<PlatformSystemListVO>>;
    detail(systemId: EntityId): Promise<PlatformDetailState<PlatformSystemDetailVO>>;
    create(body: PlatformSystemCreateBO, idempotencyKey?: string): Promise<PlatformMutationResult<PlatformSystemDetailVO>>;
    changeStatus(systemId: EntityId, body: PlatformSystemStatusBO): Promise<PlatformMutationResult<PlatformSystemDetailVO>>;
    actions(row?: PlatformSystemListVO): Record<"create" | "view" | "status", AvailableAction>;
  };
  accounts: {
    query(query?: PageQuery): Promise<PlatformPageState<PlatformAccountListVO>>;
    detail(accountId: EntityId): Promise<PlatformDetailState<PlatformAccountDetailVO>>;
    create(body: PlatformAccountSaveBO): Promise<PlatformMutationResult<PlatformAccountDetailVO>>;
    update(accountId: EntityId, body: PlatformAccountUpdateBO): Promise<PlatformMutationResult<PlatformAccountDetailVO>>;
    changeStatus(accountId: EntityId, body: PlatformAccountStatusBO): Promise<PlatformMutationResult<PlatformAccountDetailVO>>;
    resetPassword(
      accountId: EntityId,
      body: PlatformAccountResetPasswordBO,
    ): Promise<PlatformMutationResult<{ reset: boolean }>>;
    assignRoles(accountId: EntityId, body: PlatformAccountRoleAssignBO): Promise<PlatformMutationResult<PlatformAccountDetailVO>>;
    actions(row?: PlatformAccountListVO): Record<"create" | "view" | "edit" | "status" | "resetPassword" | "assignRoles", AvailableAction>;
  };
  roles: {
    query(query?: PageQuery): Promise<PlatformPageState<PlatformRoleVO>>;
    create(body: PlatformRoleSaveBO): Promise<PlatformMutationResult<PlatformRoleVO>>;
    update(roleId: EntityId, body: PlatformRoleSaveBO): Promise<PlatformMutationResult<PlatformRoleVO>>;
    changeStatus(roleId: EntityId, body: PlatformRoleStatusBO): Promise<PlatformMutationResult<PlatformRoleVO>>;
    saveMenus(roleId: EntityId, body: PlatformRolePermissionBO): Promise<PlatformMutationResult<PlatformRoleVO>>;
    permissionCatalog(): Promise<PlatformDetailState<PlatformPermissionCatalogVO>>;
    actions(row?: PlatformRoleVO): Record<"create" | "edit" | "status" | "authorize", AvailableAction>;
  };
  configs: {
    list(query?: PageQuery): Promise<PlatformPageState<PlatformConfigVO>>;
    update(configKey: string, body: PlatformConfigUpdateBO): Promise<PlatformMutationResult<PlatformConfigVO>>;
    actions(row?: PlatformConfigVO): Record<"view" | "edit", AvailableAction>;
  };
}

const FE004_API_IDS = [
  "PLAT-002",
  "PLAT-003",
  "PLAT-004",
  "PLAT-005",
  "PLAT-006",
  "PLAT-007",
  "PLAT-008",
  "PLAT-009",
  "PLAT-010",
  "PLAT-011",
  "PLAT-012",
  "PLAT-013",
  "PLAT-014",
  "PLAT-015",
  "PLAT-016",
  "PLAT-017",
  "PLAT-018",
  "PLAT-019",
  "PLAT-020",
] as const satisfies readonly ApiEndpointId[];

export function createPlatformCenterPageModel(deps: PlatformPageDeps): PlatformCenterPageModel {
  const auth = deps.auth ?? authStore;
  const errors = deps.errors ?? errorStore;

  return {
    apiIds: FE004_API_IDS,
    systems: {
      query(query = defaultPageQuery()) {
        return queryPage<PlatformSystemListVO>(deps.api, errors, auth, "PLAT-003", query);
      },
      detail(systemId) {
        return getDetail<PlatformSystemDetailVO>(deps.api, errors, auth, "PLAT-004", { systemId });
      },
      create(body, idempotencyKey = createIdempotencyKey("PLAT-002")) {
        return mutate<PlatformSystemDetailVO, PlatformSystemCreateBO>(deps.api, auth, "PLAT-002", {
          body,
          idempotencyKey,
        });
      },
      changeStatus(systemId, body) {
        return mutate<PlatformSystemDetailVO, PlatformSystemStatusBO>(deps.api, auth, "PLAT-005", {
          pathParams: { systemId },
          body,
        });
      },
      actions(row) {
        return {
          create: action("create", "创建系统", "PLAT_SYSTEM_CREATE", auth),
          view: action("view", "查看详情", "PLAT_SYSTEM_VIEW", auth),
          status: action("status", "变更状态", "PLAT_SYSTEM_STATUS", auth, row?.status === "ARCHIVED" ? "SYS_ARCHIVED" : undefined),
        };
      },
    },
    accounts: {
      query(query = defaultPageQuery()) {
        return queryPage<PlatformAccountListVO>(deps.api, errors, auth, "PLAT-006", query);
      },
      detail(accountId) {
        return getDetail<PlatformAccountDetailVO>(deps.api, errors, auth, "PLAT-013", { accountId });
      },
      create(body) {
        return mutate<PlatformAccountDetailVO, PlatformAccountSaveBO>(deps.api, auth, "PLAT-007", { body });
      },
      update(accountId, body) {
        return mutate<PlatformAccountDetailVO, PlatformAccountUpdateBO>(deps.api, auth, "PLAT-014", {
          pathParams: { accountId },
          body,
        });
      },
      changeStatus(accountId, body) {
        return mutate<PlatformAccountDetailVO, PlatformAccountStatusBO>(deps.api, auth, "PLAT-008", {
          pathParams: { accountId },
          body,
        });
      },
      resetPassword(accountId, body) {
        return mutate<{ reset: boolean }, PlatformAccountResetPasswordBO>(deps.api, auth, "PLAT-015", {
          pathParams: { accountId },
          body,
        });
      },
      assignRoles(accountId, body) {
        return mutate<PlatformAccountDetailVO, PlatformAccountRoleAssignBO>(deps.api, auth, "PLAT-016", {
          pathParams: { accountId },
          body,
        });
      },
      actions(row) {
        return {
          create: action("create", "创建账号", "PLAT_ACCOUNT_CREATE", auth),
          view: action("view", "查看账号", "PLAT_ACCOUNT_VIEW", auth),
          edit: action("edit", "编辑账号", "PLAT_ACCOUNT_CREATE", auth, row?.status === "LOCKED" ? "AUTH_ACCOUNT_LOCKED" : undefined),
          status: action("status", "变更状态", "PLAT_ACCOUNT_STATUS", auth),
          resetPassword: action("resetPassword", "重置密码", "PLAT_ACCOUNT_STATUS", auth),
          assignRoles: action("assignRoles", "分配角色", "PLAT_ROLE_AUTH", auth),
        };
      },
    },
    roles: {
      query(query = defaultPageQuery()) {
        return queryPage<PlatformRoleVO>(deps.api, errors, auth, "PLAT-009", query);
      },
      create(body) {
        return mutate<PlatformRoleVO, PlatformRoleSaveBO>(deps.api, auth, "PLAT-017", { body });
      },
      update(roleId, body) {
        return mutate<PlatformRoleVO, PlatformRoleSaveBO>(deps.api, auth, "PLAT-018", {
          pathParams: { roleId },
          body,
        });
      },
      changeStatus(roleId, body) {
        return mutate<PlatformRoleVO, PlatformRoleStatusBO>(deps.api, auth, "PLAT-019", {
          pathParams: { roleId },
          body,
        });
      },
      saveMenus(roleId, body) {
        return mutate<PlatformRoleVO, PlatformRolePermissionBO>(deps.api, auth, "PLAT-010", {
          pathParams: { roleId },
          body,
        });
      },
      permissionCatalog() {
        return getDetail<PlatformPermissionCatalogVO>(deps.api, errors, auth, "PLAT-020");
      },
      actions(row) {
        return {
          create: action("create", "创建角色", "PLAT_ROLE_AUTH", auth),
          edit: action("edit", "编辑角色", "PLAT_ROLE_AUTH", auth, row?.protectedFlag ? "SYS_ROLE_PROTECTED" : undefined),
          status: action("status", "变更状态", "PLAT_ROLE_AUTH", auth, row?.protectedFlag ? "SYS_ROLE_PROTECTED" : undefined),
          authorize: action("authorize", "授权", "PLAT_ROLE_AUTH", auth, row?.status === "DISABLED" ? "SYS_ROLE_DISABLED" : undefined),
        };
      },
    },
    configs: {
      list(query = defaultPageQuery()) {
        return queryPage<PlatformConfigVO>(deps.api, errors, auth, "PLAT-011", query);
      },
      update(configKey, body) {
        return mutate<PlatformConfigVO, PlatformConfigUpdateBO>(deps.api, auth, "PLAT-012", {
          pathParams: { configKey },
          body,
        });
      },
      actions(row) {
        return {
          view: action("view", "查看配置", "PLAT_CONFIG_VIEW", auth),
          edit: action("edit", "编辑配置", "PLAT_CONFIG_EDIT", auth),
        };
      },
    },
  };
}

export function redactPlatformConfigValue(config: PlatformConfigVO): string {
  if (config.sensitive) {
    return config.value === undefined || config.value === null ? "******" : String(config.value);
  }
  return config.value === undefined || config.value === null ? "" : String(config.value);
}

async function queryPage<TRecord>(
  api: ApiClient,
  errors: ErrorStore,
  auth: AuthStore,
  apiId: ApiEndpointId,
  query: PageQuery,
): Promise<PlatformPageState<TRecord>> {
  try {
    const response = await api.call<PageResult<TRecord> | TRecord[], never, PageQuery>(apiId, {
      query,
      context: platformContext(auth),
    });
    return pageState(toPageResult(response.data, query), response.requestId);
  } catch (error) {
    return emptyPageState<TRecord>(errors.capture(error));
  }
}

async function getDetail<TDetail>(
  api: ApiClient,
  errors: ErrorStore,
  auth: AuthStore,
  apiId: ApiEndpointId,
  pathParams?: Record<string, string>,
): Promise<PlatformDetailState<TDetail>> {
  try {
    const response = await api.call<TDetail>(apiId, {
      pathParams,
      context: platformContext(auth),
    });
    return {
      loading: false,
      detail: response.data,
      empty: response.data === undefined || response.data === null,
      lastRequestId: response.requestId,
    };
  } catch (error) {
    const display = errors.capture(error);
    return {
      loading: false,
      empty: true,
      error: display,
      lastRequestId: display.requestId,
    };
  }
}

async function mutate<TData, TBody>(
  api: ApiClient,
  auth: AuthStore,
  apiId: ApiEndpointId,
  options: {
    pathParams?: Record<string, string>;
    body?: TBody;
    idempotencyKey?: string;
  },
): Promise<PlatformMutationResult<TData>> {
  const response = await api.call<TData, TBody>(apiId, {
    pathParams: options.pathParams,
    body: options.body,
    idempotencyKey: options.idempotencyKey,
    context: platformContext(auth),
  });
  return {
    data: response.data,
    requestId: response.requestId,
    idempotencyReplay: response.meta.idempotencyReplay,
  };
}

function pageState<TRecord>(page: PageResult<TRecord>, requestId: string): PlatformPageState<TRecord> {
  return {
    loading: false,
    records: page.records,
    total: page.total,
    pageNo: page.pageNo,
    pageSize: page.pageSize,
    empty: page.records.length === 0,
    lastRequestId: requestId,
  };
}

function toPageResult<TRecord>(data: PageResult<TRecord> | TRecord[], query: PageQuery): PageResult<TRecord> {
  if (Array.isArray(data)) {
    return {
      records: data,
      total: data.length,
      pageNo: query.pageNo ?? 1,
      pageSize: query.pageSize ?? data.length,
      hasNext: false,
    };
  }
  return data;
}

function emptyPageState<TRecord>(error: RequestErrorDisplay): PlatformPageState<TRecord> {
  return {
    loading: false,
    records: [],
    total: 0,
    pageNo: 1,
    pageSize: 20,
    empty: true,
    error,
    lastRequestId: error.requestId,
  };
}

function action(
  actionCode: string,
  label: string,
  requiredPermission: string,
  auth: AuthStore,
  stateReason?: string,
): AvailableAction {
  const permitted = auth.hasPlatformPermission(requiredPermission);
  const enabled = permitted && !stateReason;
  return {
    actionCode,
    label,
    visible: permitted,
    enabled,
    requiredPermission,
    disabledReason: permitted ? undefined : "PERM_DENIED",
    stateReason,
    confirmRequired: actionCode === "status" || actionCode === "resetPassword",
    danger: actionCode === "status" || actionCode === "resetPassword",
  };
}

function platformContext(auth: AuthStore): ApiContext {
  return auth.toApiContext(createRequestId("fe004"));
}

function defaultPageQuery(): PageQuery {
  return {
    pageNo: 1,
    pageSize: 20,
  };
}

function createRequestId(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

function createIdempotencyKey(apiId: ApiEndpointId): string {
  const endpoint = API_ENDPOINTS[apiId];
  const cryptoApi = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto;
  const random =
    typeof cryptoApi?.randomUUID === "function"
      ? cryptoApi.randomUUID()
      : Math.random().toString(36).slice(2);
  return `${endpoint.id}:${Date.now()}:${random}`;
}
