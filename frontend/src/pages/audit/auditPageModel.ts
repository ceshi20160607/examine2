import type { ApiClient, ApiContext } from "../../api/client";
import type { ApiEndpointId } from "../../api/endpoints";
import type { EntityId, IsoDateTimeString, JsonValue, PageQuery, PageResult } from "../../api/types";
import type { AuthStore } from "../../stores/auth";
import type { PermissionDecision, PermissionStore } from "../../stores/permission";
import type { SystemContextStore } from "../../stores/systemContext";

export type AuditLogKind = "operation" | "request" | "error" | "recordChange" | "openapi" | "platformOperation";
export type AuditResult = "SUCCESS" | "FAILED";

export interface AuditLogListItemVO {
  logId: EntityId;
  requestId: string;
  operatorType?: string;
  operatorName?: string;
  systemId?: EntityId;
  tenantId?: EntityId;
  bizType?: string;
  bizId?: EntityId;
  action?: string;
  result?: AuditResult | string;
  errorCode?: string;
  createdAt: IsoDateTimeString;
  summary?: string;
}

export interface BeforeAfterSnapshotVO {
  beforeSnapshot?: JsonValue;
  afterSnapshot?: JsonValue;
  beforeStatus?: string;
  afterStatus?: string;
  changedFields?: string[];
}

export interface AuditLogDetailPageVO extends AuditLogListItemVO, BeforeAfterSnapshotVO {
  traceId: string;
  source: string;
  apiId: string;
  httpMethod: string;
  path: string;
  operatorId?: EntityId;
  durationMs?: number;
}

export interface AuditLogQueryBO extends PageQuery {
  requestId?: string;
  operatorId?: EntityId;
  module?: string;
  bizType?: string;
  bizId?: EntityId;
  action?: string;
  result?: AuditResult;
  startTime?: IsoDateTimeString;
  endTime?: IsoDateTimeString;
}

export interface AuditPermissionState {
  systemOperation: PermissionDecision;
  systemRequest: PermissionDecision;
  systemError: PermissionDecision;
  recordChange: PermissionDecision;
  openApiLog: PermissionDecision;
  platformOperation: PermissionDecision;
}

export interface AuditPageState {
  activeKind: AuditLogKind;
  logs: PageResult<AuditLogListItemVO>;
  selectedDetail?: AuditLogDetailPageVO;
  copiedRequestId?: string;
  lastRequestId?: string;
  emptyReason?: string;
}

export interface AuditPageModel {
  systemRouteName: "audit.system";
  platformRouteName: "audit.platform";
  apiIds: readonly ApiEndpointId[];
  permissions(): AuditPermissionState;
  createInitialState(kind?: AuditLogKind): AuditPageState;
  queryLogs(kind: AuditLogKind, query?: AuditLogQueryBO, requestId?: string): Promise<PageResult<AuditLogListItemVO>>;
  loadDetail(kind: "operation" | "request" | "error" | "recordChange" | "openapi", logId: EntityId, requestId?: string): Promise<AuditLogDetailPageVO>;
  loadPlatformDetail(logId: EntityId, requestId?: string): Promise<AuditLogDetailPageVO>;
  markRequestIdCopied(state: AuditPageState, requestId: string): AuditPageState;
}

const AUDIT_API_IDS = ["AUD-001", "AUD-002", "AUD-003", "AUD-004", "AUD-005", "AUD-006", "AUD-007", "AUD-008"] as const satisfies readonly ApiEndpointId[];

const SYSTEM_AUDIT_API_BY_KIND: Record<Exclude<AuditLogKind, "platformOperation">, ApiEndpointId> = {
  operation: "AUD-001",
  request: "AUD-002",
  error: "AUD-003",
  recordChange: "AUD-004",
  openapi: "AUD-005",
};

export function createAuditPageModel(deps: {
  apiClient: ApiClient;
  auth: AuthStore;
  permission: PermissionStore;
  systemContext: SystemContextStore;
}): AuditPageModel {
  const { apiClient, auth, permission, systemContext } = deps;

  return {
    systemRouteName: "audit.system",
    platformRouteName: "audit.platform",
    apiIds: AUDIT_API_IDS,

    permissions() {
      return createAuditPermissionState(auth, permission);
    },

    createInitialState(kind = "operation") {
      return {
        activeKind: kind,
        logs: emptyPage(),
        emptyReason: kind === "platformOperation" ? undefined : systemContext.validate({ system: true, member: true }).join(","),
      };
    },

    async queryLogs(kind, query = {}, requestId) {
      if (kind === "platformOperation") {
        const response = await apiClient.call<PageResult<AuditLogListItemVO>, never, AuditLogQueryBO>("AUD-006", {
          query,
          context: auth.toApiContext(requestId),
        });
        return response.data;
      }

      const response = await apiClient.call<PageResult<AuditLogListItemVO>, never, AuditLogQueryBO>(
        SYSTEM_AUDIT_API_BY_KIND[kind],
        {
          pathParams: systemContext.toPathParams(),
          query,
          context: systemAuditContext(auth, systemContext, requestId),
        },
      );
      return response.data;
    },

    async loadDetail(kind, logId, requestId) {
      const response = await apiClient.call<AuditLogDetailPageVO>("AUD-007", {
        pathParams: systemContext.toPathParams({ logId }),
        context: systemAuditContext(auth, systemContext, requestId),
      });
      return {
        ...response.data,
        summary: response.data.summary ?? `${kind}:${response.data.apiId}`,
      };
    },

    async loadPlatformDetail(logId, requestId) {
      const response = await apiClient.call<AuditLogDetailPageVO>("AUD-008", {
        pathParams: { logId },
        context: auth.toApiContext(requestId),
      });
      return response.data;
    },

    markRequestIdCopied(state, requestId) {
      return {
        ...state,
        copiedRequestId: requestId,
      };
    },
  };
}

function createAuditPermissionState(auth: AuthStore, permission: PermissionStore): AuditPermissionState {
  return {
    systemOperation: permission.decide({ anyOperations: ["AUDIT_OPERATION_VIEW"] }),
    systemRequest: permission.decide({ anyOperations: ["AUDIT_REQUEST_VIEW"] }),
    systemError: permission.decide({ anyOperations: ["AUDIT_ERROR_VIEW"] }),
    recordChange: permission.decide({ anyOperations: ["AUDIT_RECORD_VIEW"] }),
    openApiLog: permission.decide({ anyOperations: ["OPENAPI_LOG_VIEW"] }),
    platformOperation: {
      visible: true,
      enabled: auth.hasPlatformPermission("PLAT_AUDIT_VIEW"),
      disabledReason: auth.hasPlatformPermission("PLAT_AUDIT_VIEW") ? undefined : "PERM_DENIED",
    },
  };
}

function systemAuditContext(auth: AuthStore, systemContext: SystemContextStore, requestId?: string): ApiContext {
  const missing = systemContext.validate({ system: true, member: true });
  if (missing.length > 0) {
    throw new Error(`Audit page requires context: ${missing.join(",")}`);
  }
  return {
    ...auth.toApiContext(requestId),
    tenantId: systemContext.toTenantHeader(),
  };
}

function emptyPage<TRecord>(): PageResult<TRecord> {
  return {
    records: [],
    total: 0,
    pageNo: 1,
    pageSize: 20,
    hasNext: false,
  };
}

export const AUDIT_LIST_COLUMNS = [
  "requestId",
  "operatorType",
  "operatorName",
  "systemId",
  "tenantId",
  "bizType",
  "bizId",
  "action",
  "result",
  "errorCode",
  "createdAt",
  "summary",
] as const;

export const AUDIT_DETAIL_FIELDS = [
  "logId",
  "requestId",
  "traceId",
  "source",
  "apiId",
  "httpMethod",
  "path",
  "operatorId",
  "operatorName",
  "bizType",
  "bizId",
  "beforeStatus",
  "afterStatus",
  "changedFields",
  "beforeSnapshot",
  "afterSnapshot",
  "errorCode",
  "durationMs",
  "createdAt",
] as const;
