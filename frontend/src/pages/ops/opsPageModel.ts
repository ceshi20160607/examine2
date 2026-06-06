import type { ApiClient } from "../../api/client";
import type { ApiEndpointId } from "../../api/endpoints";
import type { IsoDateTimeString, JsonValue } from "../../api/types";
import type { AuthStore } from "../../stores/auth";
import type { PermissionDecision } from "../../stores/permission";

export type OpsCheckResult = "UP" | "DOWN" | "WARN" | "UNKNOWN";

export interface HealthCheckResultVO {
  status: OpsCheckResult;
  checks: OpsComponentStatusVO[];
  requestId: string;
}

export interface OpsComponentStatusVO {
  component: string;
  result: OpsCheckResult;
  message?: string;
  suggestion?: string;
  checkedAt?: IsoDateTimeString;
  metadata?: Record<string, JsonValue>;
}

export interface OpsConfigCheckVO {
  status: OpsCheckResult;
  checks: OpsComponentStatusVO[];
  requestId: string;
}

export interface OpsVersionVO {
  version: string;
  buildTime: IsoDateTimeString;
  requestId: string;
  commitId?: string;
  profile?: string;
}

export interface OpsMigrationStatusVO {
  status: OpsCheckResult;
  migrationVersion: string;
  checks: OpsComponentStatusVO[];
  requestId: string;
}

export interface OpsPermissionState {
  viewHealth: PermissionDecision;
  viewConfig: PermissionDecision;
  viewVersion: PermissionDecision;
  viewMigration: PermissionDecision;
  editRuntimeConfig: PermissionDecision;
}

export interface OpsPageState {
  health?: HealthCheckResultVO;
  configCheck?: OpsConfigCheckVO;
  version?: OpsVersionVO;
  migration?: OpsMigrationStatusVO;
  components: OpsComponentStatusVO[];
  lastRequestId?: string;
  healthAbnormal: boolean;
  emptyReason?: string;
}

export interface OpsPageModel {
  routeName: "ops.health";
  routePath: "/ops";
  apiIds: readonly ApiEndpointId[];
  permissions(): OpsPermissionState;
  createInitialState(): OpsPageState;
  loadHealth(requestId?: string): Promise<HealthCheckResultVO>;
  loadConfigCheck(requestId?: string): Promise<OpsConfigCheckVO>;
  loadVersion(requestId?: string): Promise<OpsVersionVO>;
  loadMigrationStatus(requestId?: string): Promise<OpsMigrationStatusVO>;
  loadComponents(requestId?: string): Promise<OpsComponentStatusVO[]>;
  summarize(state: OpsPageState): OpsPageState;
}

const OPS_API_IDS = ["OPS-001", "OPS-002", "OPS-003", "OPS-004", "OPS-005", "OPS-006"] as const satisfies readonly ApiEndpointId[];

export function createOpsPageModel(deps: { apiClient: ApiClient; auth: AuthStore }): OpsPageModel {
  const { apiClient, auth } = deps;

  return {
    routeName: "ops.health",
    routePath: "/ops",
    apiIds: OPS_API_IDS,

    permissions() {
      return createOpsPermissionState(auth);
    },

    createInitialState() {
      const permissions = createOpsPermissionState(auth);
      return {
        components: [],
        healthAbnormal: false,
        emptyReason:
          permissions.viewHealth.enabled ||
          permissions.viewConfig.enabled ||
          permissions.viewVersion.enabled ||
          permissions.viewMigration.enabled
            ? undefined
            : "PERM_DENIED",
      };
    },

    async loadHealth(requestId) {
      const response = await apiClient.call<HealthCheckResultVO>("OPS-001", {
        context: auth.toApiContext(requestId),
      });
      return response.data;
    },

    async loadConfigCheck(requestId) {
      const response = await apiClient.call<OpsConfigCheckVO>("OPS-002", {
        context: auth.toApiContext(requestId),
      });
      return response.data;
    },

    async loadVersion(requestId) {
      const response = await apiClient.call<OpsVersionVO>("OPS-003", {
        context: auth.toApiContext(requestId),
      });
      return response.data;
    },

    async loadMigrationStatus(requestId) {
      const response = await apiClient.call<OpsMigrationStatusVO>("OPS-004", {
        context: auth.toApiContext(requestId),
      });
      return response.data;
    },

    async loadComponents(requestId) {
      const response = await apiClient.call<OpsComponentStatusVO[]>("OPS-006", {
        context: auth.toApiContext(requestId),
      });
      return response.data;
    },

    summarize(state) {
      const components = [
        ...(state.health?.checks ?? []),
        ...(state.configCheck?.checks ?? []),
        ...(state.migration?.checks ?? []),
        ...state.components,
      ];
      return {
        ...state,
        components,
        healthAbnormal: components.some((item) => item.result === "DOWN" || item.result === "WARN"),
        lastRequestId:
          state.health?.requestId ?? state.configCheck?.requestId ?? state.version?.requestId ?? state.migration?.requestId,
      };
    },
  };
}

function createOpsPermissionState(auth: AuthStore): OpsPermissionState {
  return {
    viewHealth: platformPermission(auth, "OPS_HEALTH_VIEW"),
    viewConfig: platformPermission(auth, "OPS_CONFIG_VIEW"),
    viewVersion: platformPermission(auth, "OPS_VERSION_VIEW"),
    viewMigration: platformPermission(auth, "OPS_MIGRATION_VIEW"),
    editRuntimeConfig: platformPermission(auth, "OPS_CONFIG_EDIT"),
  };
}

function platformPermission(auth: AuthStore, permissionCode: string): PermissionDecision {
  const enabled = auth.hasPlatformPermission(permissionCode);
  return {
    visible: true,
    enabled,
    disabledReason: enabled ? undefined : "PERM_DENIED",
    matchedPermission: permissionCode,
  };
}

export const OPS_COMPONENT_COLUMNS = ["component", "result", "message", "suggestion", "checkedAt"] as const;

export const OPS_READONLY_API_IDS = ["OPS-001", "OPS-002", "OPS-003", "OPS-004", "OPS-006"] as const satisfies readonly ApiEndpointId[];
