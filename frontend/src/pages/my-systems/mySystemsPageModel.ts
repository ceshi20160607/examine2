import type { ApiClient } from "../../api/client";
import type { AvailableAction, EntityId, IsoDateTimeString, SystemStatus, TenantStatus } from "../../api/types";
import { buildPath, DEFAULT_AUTHENTICATED_ROUTE } from "../../router";
import {
  authStore,
  errorStore,
  permissionStore,
  systemContextStore,
  type AuthStore,
  type ErrorStore,
  type PermissionStore,
  type RequestErrorDisplay,
  type SystemContextSnapshot,
  type SystemContextStore,
  type TenantMode,
} from "../../stores";

export interface MySystemCardModel {
  systemId: EntityId;
  systemCode?: string;
  systemName: string;
  status: SystemStatus;
  tenantMode: TenantMode;
  tenantId?: EntityId;
  tenantCode?: string;
  tenantName?: string;
  tenantStatus?: TenantStatus;
  memberId?: EntityId;
  memberDisplayName?: string;
  memberRoleNames: string[];
  availableActions?: AvailableAction[];
}

export interface CreateSystemFormModel {
  name: string;
  code: string;
  tenantMode: TenantMode;
  description?: string;
}

export interface MySystemsPageState {
  loading: boolean;
  creating: boolean;
  enteringSystemId?: EntityId;
  systems: MySystemCardModel[];
  empty: boolean;
  createEnabled: boolean;
  createDisabledReason?: string;
  lastRequestId?: string;
  lastError?: RequestErrorDisplay;
}

export interface MySystemsPageModel {
  readonly state: MySystemsPageState;
  loadSystems(): Promise<MySystemCardModel[]>;
  createSystem(form: CreateSystemFormModel): Promise<{ route: string; requestId?: string }>;
  enterSystem(systemId: EntityId, tenantId?: EntityId): Promise<{ route: string; requestId?: string }>;
  retryLastLoad(): Promise<MySystemCardModel[]>;
}

export interface MySystemsPageModelDependencies {
  apiClient: ApiClient;
  auth?: AuthStore;
  systemContext?: SystemContextStore;
  permission?: PermissionStore;
  errors?: ErrorStore;
  createRequestId?: (scope: string) => string;
  createIdempotencyKey?: (scope: string) => string;
}

interface MySystemsResponseObject {
  systems?: MySystemCardModel[];
  records?: MySystemCardModel[];
  availableActions?: AvailableAction[];
}

type MySystemsResponse = MySystemCardModel[] | MySystemsResponseObject;

interface CreatedSystemResponse {
  systemId?: EntityId;
  systemCode?: string;
  systemName?: string;
  status?: SystemStatus;
  tenantMode?: TenantMode;
  tenantId?: EntityId;
  initializedObjects?: InitializedObject[];
}

interface InitializedObject {
  objectType: string;
  code?: string;
  id?: EntityId;
  status?: string;
}

interface EnterSystemResponse {
  systemId: EntityId;
  systemCode?: string;
  systemName: string;
  status: SystemStatus;
  tenantMode: TenantMode;
  tenantId?: EntityId;
  tenantCode?: string;
  tenantName?: string;
  tenantStatus?: TenantStatus;
  memberId: EntityId;
  memberDisplayName?: string;
  memberRoles?: string[];
  memberRoleNames?: string[];
  memberStatus?: "ENABLED" | "DISABLED";
  runtimeHomePage?: string;
  enteredAt?: IsoDateTimeString;
}

export function createMySystemsPageModel(dependencies: MySystemsPageModelDependencies): MySystemsPageModel {
  const auth = dependencies.auth ?? authStore;
  const systemContext = dependencies.systemContext ?? systemContextStore;
  const permission = dependencies.permission ?? permissionStore;
  const errors = dependencies.errors ?? errorStore;
  const createRequestId = dependencies.createRequestId ?? defaultRequestId;
  const createIdempotencyKey = dependencies.createIdempotencyKey ?? defaultIdempotencyKey;
  const state: MySystemsPageState = {
    loading: false,
    creating: false,
    systems: [],
    empty: true,
    createEnabled: auth.hasPlatformPermission("PLAT_SYSTEM_CREATE"),
  };

  function begin(scope: string): string {
    const requestId = createRequestId(scope);
    state.lastRequestId = requestId;
    state.lastError = undefined;
    return requestId;
  }

  function refreshCreatePermission(actions?: AvailableAction[]): void {
    const action = actions?.find((item) => item.actionCode === "PLAT_SYSTEM_CREATE" || item.requiredPermission === "PLAT_SYSTEM_CREATE");
    const enabled = action ? action.visible && action.enabled : auth.hasPlatformPermission("PLAT_SYSTEM_CREATE");
    state.createEnabled = enabled;
    state.createDisabledReason = enabled ? undefined : action?.disabledReason ?? action?.stateReason ?? "PERM_DENIED";
  }

  function fail(error: unknown): never {
    const display = errors.capture(error);
    state.lastError = display;
    throw error;
  }

  async function loadSystems(): Promise<MySystemCardModel[]> {
    const requestId = begin("PLAT-001");
    state.loading = true;
    try {
      const response = await dependencies.apiClient.call<MySystemsResponse>("PLAT-001", {
        context: auth.toApiContext(requestId),
      });
      const systems = normalizeSystems(response.data);
      state.systems = systems;
      state.empty = systems.length === 0;
      refreshCreatePermission(getResponseActions(response.data));
      return systems;
    } catch (error) {
      fail(error);
    } finally {
      state.loading = false;
    }
  }

  async function enterSystem(systemId: EntityId, tenantId?: EntityId): Promise<{ route: string; requestId?: string }> {
    const system = state.systems.find((item) => item.systemId === systemId);
    const selectedTenantId = tenantId ?? system?.tenantId;
    const requestId = begin("SYS-001");
    state.enteringSystemId = systemId;
    systemContext.beginEnter(systemId);
    try {
      const response = await dependencies.apiClient.call<EnterSystemResponse, { tenantId?: EntityId }>("SYS-001", {
        pathParams: { systemId },
        body: selectedTenantId ? { tenantId: selectedTenantId } : {},
        context: {
          ...auth.toApiContext(requestId),
          tenantId: selectedTenantId,
        },
      });
      const snapshot = toSystemContextSnapshot(response.data, system);
      systemContext.setContext(snapshot);
      permission.clear();
      permission.markStale();
      return {
        route: response.data.runtimeHomePage ?? buildPath("runtime.home", { systemId: snapshot.system.systemId }),
        requestId: response.requestId,
      };
    } catch (error) {
      systemContext.clear();
      fail(error);
    } finally {
      state.enteringSystemId = undefined;
    }
  }

  return {
    state,

    loadSystems,

    async createSystem(form) {
      validateCreateSystem(form);
      if (!state.createEnabled) {
        const blocked = localDisplayError("PERM_DENIED", state.createDisabledReason ?? "PLAT_SYSTEM_CREATE is not granted.");
        errors.push(blocked);
        state.lastError = blocked;
        throw new Error(blocked.message);
      }

      const requestId = begin("PLAT-002");
      state.creating = true;
      try {
        const response = await dependencies.apiClient.call<CreatedSystemResponse, CreateSystemFormModel>("PLAT-002", {
          body: form,
          context: auth.toApiContext(requestId),
          idempotencyKey: createIdempotencyKey("PLAT-002"),
        });
        const systems = await loadSystems();
        const createdSystemId = resolveCreatedSystemId(response.data, systems, form.code);
        if (!createdSystemId) {
          const blocked = localDisplayError("SYS_CONTEXT_REQUIRED", "Created system response did not include a resolvable systemId.");
          errors.push(blocked);
          state.lastError = blocked;
          return { route: DEFAULT_AUTHENTICATED_ROUTE, requestId: response.requestId };
        }
        return enterSystem(createdSystemId, response.data.tenantId);
      } catch (error) {
        fail(error);
      } finally {
        state.creating = false;
      }
    },

    enterSystem,

    async retryLastLoad() {
      return loadSystems();
    },
  };
}

function normalizeSystems(response: MySystemsResponse): MySystemCardModel[] {
  const items = Array.isArray(response) ? response : response.systems ?? response.records ?? [];
  return items.map((item) => ({
    ...item,
    memberRoleNames: item.memberRoleNames ?? [],
  }));
}

function getResponseActions(response: MySystemsResponse): AvailableAction[] | undefined {
  return Array.isArray(response) ? undefined : response.availableActions;
}

function validateCreateSystem(form: CreateSystemFormModel): void {
  if (!form.name || !form.code || !form.tenantMode) {
    throw new Error("name, code and tenantMode are required by PLAT-002.");
  }
}

function resolveCreatedSystemId(response: CreatedSystemResponse, systems: MySystemCardModel[], code: string): EntityId | undefined {
  return (
    response.systemId ??
    response.initializedObjects?.find((item) => item.objectType === "SYSTEM")?.id ??
    systems.find((item) => item.systemCode === response.systemCode || item.systemCode === code)?.systemId
  );
}

function toSystemContextSnapshot(response: EnterSystemResponse, fallback?: MySystemCardModel): SystemContextSnapshot {
  return {
    system: {
      systemId: response.systemId,
      systemCode: response.systemCode ?? fallback?.systemCode,
      systemName: response.systemName ?? fallback?.systemName ?? "",
      tenantMode: response.tenantMode ?? fallback?.tenantMode ?? "SINGLE",
      status: response.status ?? fallback?.status ?? "ENABLED",
    },
    tenant:
      response.tenantId || fallback?.tenantId
        ? {
            tenantId: response.tenantId ?? fallback?.tenantId ?? "",
            tenantCode: response.tenantCode ?? fallback?.tenantCode,
            tenantName: response.tenantName ?? fallback?.tenantName ?? "default",
            status: response.tenantStatus ?? fallback?.tenantStatus ?? "ENABLED",
          }
        : undefined,
    member: {
      memberId: response.memberId,
      displayName: response.memberDisplayName ?? fallback?.memberDisplayName ?? "",
      roles: response.memberRoles ?? response.memberRoleNames ?? fallback?.memberRoleNames ?? [],
      status: response.memberStatus,
    },
    runtimeHomePage: response.runtimeHomePage,
    enteredAt: response.enteredAt,
  };
}

function localDisplayError(code: string, message: string): RequestErrorDisplay {
  return {
    level: "error",
    code,
    message,
    retryable: false,
    details: [],
  };
}

function defaultRequestId(scope: string): string {
  return `${scope}_${Date.now().toString(36)}`;
}

function defaultIdempotencyKey(scope: string): string {
  return `${scope}_${Date.now().toString(36)}`;
}
