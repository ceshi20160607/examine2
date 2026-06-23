import type { ApiClient, ApiContext } from "../../api/client";
import type { ApiEndpointId } from "../../api/endpoints";
import type {
  DataScopeRuleDTO,
  EntityId,
  IsoDateTimeString,
  JsonValue,
  OpenApiAccessLogVO,
  OpenApiClientStatus,
  OpenApiScope,
  PageQuery,
  PageResult,
} from "../../api/types";
import type { AuthStore } from "../../stores/auth";
import type { PermissionDecision, PermissionStore } from "../../stores/permission";
import type { SystemContextStore } from "../../stores/systemContext";

export interface OpenApiClientListItemVO {
  clientId: EntityId;
  name: string;
  code: string;
  tenantId?: EntityId;
  accessKey: string;
  maskedSecret?: string;
  secretVisibleOnce: boolean;
  status: OpenApiClientStatus;
  scopes: OpenApiScope[];
  ipWhitelist: string[];
  rateLimitPolicy?: OpenApiRateLimitPolicyDTO[];
  expiresAt?: IsoDateTimeString;
  createdAt: IsoDateTimeString;
  lastUsedAt?: IsoDateTimeString;
}

export interface OpenApiClientDetailVO extends OpenApiClientListItemVO {
  updatedAt?: IsoDateTimeString;
  version?: number;
}

export interface OpenApiCredentialOnceVO {
  clientId: EntityId;
  accessKey: string;
  secretOnce?: string;
  maskedSecret?: string;
  secretVisibleOnce: boolean;
  rotatedAt?: IsoDateTimeString;
}

export interface OpenApiScopeCatalogVO {
  modules: OpenApiScopeCatalogModule[];
  actions: string[];
  dataScopes: DataScopeRuleDTO[];
  filePermissions: string[];
  flowPermissions: string[];
}

export interface OpenApiScopeCatalogModule {
  moduleCode: string;
  moduleName: string;
  readableFieldCodes: string[];
  writableFieldCodes: string[];
  actions: string[];
}

export interface OpenApiRateLimitPolicyDTO {
  windowSeconds: number;
  maxRequests: number;
  burst?: number;
  effectiveFrom?: IsoDateTimeString;
  status?: "ENABLED" | "DISABLED";
}

export interface OpenApiClientSaveBO {
  name: string;
  code: string;
  tenantId?: EntityId;
  scopes: OpenApiScope[];
  ipWhitelist: OpenApiIpWhitelistBO[];
  rateLimitPolicy?: OpenApiRateLimitPolicyDTO[];
  expiresAt?: IsoDateTimeString;
  status: OpenApiClientStatus;
  idempotencyKey: string;
}

export interface OpenApiClientUpdateBO {
  name: string;
  code?: string;
  status?: OpenApiClientStatus;
  tenantId?: EntityId;
  scopes?: OpenApiScope[];
  ipWhitelist?: OpenApiIpWhitelistBO[];
  rateLimitPolicy?: OpenApiRateLimitPolicyDTO[];
  expiresAt?: IsoDateTimeString;
  version?: number;
}

export interface OpenApiClientStatusBO {
  status: Exclude<OpenApiClientStatus, "DRAFT">;
  version?: number;
}

export interface OpenApiScopeSaveBO {
  scopes: OpenApiScope[];
  version?: number;
}

export interface OpenApiIpWhitelistBO {
  ipRule: string;
  ruleType?: "IP" | "CIDR";
  status?: "ENABLED" | "DISABLED";
  description?: string;
  version?: number;
}

export interface OpenApiClientQuery extends PageQuery {
  status?: OpenApiClientStatus;
  tenantId?: EntityId;
}

export interface OpenApiAccessLogQuery extends PageQuery {
  requestId?: string;
  clientId?: EntityId;
  apiId?: string;
  statusCode?: number;
  errorCode?: string;
  startTime?: IsoDateTimeString;
  endTime?: IsoDateTimeString;
}

export interface SecretOnceDisplayState {
  visible: boolean;
  source: "create" | "rotate";
  clientId: EntityId;
  accessKey: string;
  secretOnce?: string;
  maskedSecret?: string;
  requestId: string;
  consumed: boolean;
}

export interface OpenApiPermissionState {
  viewClients: PermissionDecision;
  createClient: PermissionDecision;
  editClient: PermissionDecision;
  changeStatus: PermissionDecision;
  rotateCredential: PermissionDecision;
  editScope: PermissionDecision;
  editIp: PermissionDecision;
  viewLogs: PermissionDecision;
}

export interface OpenApiPageState {
  clients: PageResult<OpenApiClientListItemVO>;
  scopeCatalog?: OpenApiScopeCatalogVO;
  accessLogs: PageResult<OpenApiAccessLogVO>;
  selectedClient?: OpenApiClientDetailVO;
  secretOnce?: SecretOnceDisplayState;
  lastRequestId?: string;
  emptyReason?: string;
}

export interface OpenApiPageModel {
  routeName: "openapi.clients";
  routePath: "/systems/:systemId/openapi";
  apiIds: readonly ApiEndpointId[];
  permissions(): OpenApiPermissionState;
  createInitialState(): OpenApiPageState;
  loadClients(query?: OpenApiClientQuery, requestId?: string): Promise<PageResult<OpenApiClientListItemVO>>;
  loadScopeCatalog(requestId?: string): Promise<OpenApiScopeCatalogVO>;
  createClient(body: OpenApiClientSaveBO, requestId?: string): Promise<OpenApiClientDetailVO>;
  updateClient(clientId: EntityId, body: OpenApiClientUpdateBO, requestId?: string): Promise<OpenApiClientDetailVO>;
  changeClientStatus(clientId: EntityId, body: OpenApiClientStatusBO, requestId?: string): Promise<OpenApiClientDetailVO>;
  rotateCredential(clientId: EntityId, idempotencyKey: string, requestId?: string): Promise<SecretOnceDisplayState>;
  updateScopes(clientId: EntityId, body: OpenApiScopeSaveBO, requestId?: string): Promise<OpenApiClientDetailVO>;
  updateIpWhitelist(clientId: EntityId, body: { ipWhitelist: OpenApiIpWhitelistBO[] }, requestId?: string): Promise<OpenApiClientDetailVO>;
  loadAccessLogs(query?: OpenApiAccessLogQuery, requestId?: string): Promise<PageResult<OpenApiAccessLogVO>>;
  consumeSecretOnce(state: OpenApiPageState): OpenApiPageState;
}

const OPENAPI_API_IDS = [
  "OPM-001",
  "OPM-002",
  "OPM-003",
  "OPM-004",
  "OPM-005",
  "OPM-006",
  "OPM-007",
  "OPM-008",
  "OPM-009",
] as const satisfies readonly ApiEndpointId[];

export function createOpenApiPageModel(deps: {
  apiClient: ApiClient;
  auth: AuthStore;
  permission: PermissionStore;
  systemContext: SystemContextStore;
}): OpenApiPageModel {
  const { apiClient, auth, permission, systemContext } = deps;

  return {
    routeName: "openapi.clients",
    routePath: "/systems/:systemId/openapi",
    apiIds: OPENAPI_API_IDS,

    permissions() {
      return createOpenApiPermissionState(permission);
    },

    createInitialState() {
      return {
        clients: emptyPage(),
        accessLogs: emptyPage(),
        emptyReason: systemContext.validate({ system: true, tenant: true, member: true }).join(","),
      };
    },

    async loadClients(query = {}, requestId) {
      const response = await apiClient.call<PageResult<OpenApiClientListItemVO>, never, OpenApiClientQuery>(
        "OPM-001",
        {
          pathParams: systemContext.toPathParams(),
          query,
          context: apiContext(auth, systemContext, requestId),
        },
      );
      return response.data;
    },

    async loadScopeCatalog(requestId) {
      const response = await apiClient.call<OpenApiScopeCatalogVO>("OPM-009", {
        pathParams: systemContext.toPathParams(),
        context: apiContext(auth, systemContext, requestId),
      });
      return response.data;
    },

    async createClient(body, requestId) {
      const response = await apiClient.call<OpenApiClientDetailVO, OpenApiClientSaveBO>("OPM-002", {
        pathParams: systemContext.toPathParams(),
        body,
        idempotencyKey: body.idempotencyKey,
        context: apiContext(auth, systemContext, requestId),
      });
      return response.data;
    },

    async updateClient(clientId, body, requestId) {
      const response = await apiClient.call<OpenApiClientDetailVO, OpenApiClientUpdateBO>("OPM-003", {
        pathParams: systemContext.toPathParams({ clientId }),
        body,
        context: apiContext(auth, systemContext, requestId),
      });
      return response.data;
    },

    async changeClientStatus(clientId, body, requestId) {
      const response = await apiClient.call<OpenApiClientDetailVO, OpenApiClientStatusBO>("OPM-004", {
        pathParams: systemContext.toPathParams({ clientId }),
        body,
        context: apiContext(auth, systemContext, requestId),
      });
      return response.data;
    },

    async rotateCredential(clientId, idempotencyKey, requestId) {
      const response = await apiClient.call<OpenApiCredentialOnceVO, { idempotencyKey: string }>("OPM-005", {
        pathParams: systemContext.toPathParams({ clientId }),
        body: { idempotencyKey },
        idempotencyKey,
        context: apiContext(auth, systemContext, requestId),
      });
      return toSecretDisplay(response.data, "rotate", response.requestId);
    },

    async updateScopes(clientId, body, requestId) {
      const response = await apiClient.call<OpenApiClientDetailVO, OpenApiScope[]>("OPM-006", {
        pathParams: systemContext.toPathParams({ clientId }),
        body: body.scopes,
        context: apiContext(auth, systemContext, requestId),
      });
      return response.data;
    },

    async updateIpWhitelist(clientId, body, requestId) {
      const response = await apiClient.call<OpenApiClientDetailVO, OpenApiIpWhitelistBO[]>("OPM-007", {
        pathParams: systemContext.toPathParams({ clientId }),
        body: body.ipWhitelist,
        context: apiContext(auth, systemContext, requestId),
      });
      return response.data;
    },

    async loadAccessLogs(query = {}, requestId) {
      const response = await apiClient.call<PageResult<OpenApiAccessLogVO>, never, OpenApiAccessLogQuery>("OPM-008", {
        pathParams: systemContext.toPathParams(),
        query,
        context: apiContext(auth, systemContext, requestId),
      });
      return response.data;
    },

    consumeSecretOnce(state) {
      if (!state.secretOnce) {
        return state;
      }
      return {
        ...state,
        secretOnce: {
          ...state.secretOnce,
          visible: false,
          secretOnce: undefined,
          consumed: true,
        },
      };
    },
  };
}

export function createSecretDisplayFromCreatedClient(
  client: OpenApiClientDetailVO,
  requestId: string,
): SecretOnceDisplayState | undefined {
  if (!client.secretVisibleOnce || !("secretOnce" in client)) {
    return undefined;
  }
  const credential = client as OpenApiCredentialOnceVO;
  return toSecretDisplay(credential, "create", requestId);
}

function createOpenApiPermissionState(permission: PermissionStore): OpenApiPermissionState {
  return {
    viewClients: permission.decide({ anyOperations: ["OPENAPI_CLIENT_VIEW"] }),
    createClient: permission.decide({ anyOperations: ["OPENAPI_CLIENT_CREATE"] }),
    editClient: permission.decide({ anyOperations: ["OPENAPI_CLIENT_EDIT"] }),
    changeStatus: permission.decide({ anyOperations: ["OPENAPI_CLIENT_STATUS"] }),
    rotateCredential: permission.decide({ anyOperations: ["OPENAPI_CREDENTIAL_ROTATE"] }),
    editScope: permission.decide({ anyOperations: ["OPENAPI_SCOPE_EDIT"] }),
    editIp: permission.decide({ anyOperations: ["OPENAPI_IP_EDIT"] }),
    viewLogs: permission.decide({ anyOperations: ["OPENAPI_LOG_VIEW"] }),
  };
}

function apiContext(auth: AuthStore, systemContext: SystemContextStore, requestId?: string): ApiContext {
  const missing = systemContext.validate({ system: true, tenant: true, member: true });
  if (missing.length > 0) {
    throw new Error(`OpenAPI page requires context: ${missing.join(",")}`);
  }
  return {
    ...auth.toApiContext(requestId),
    tenantId: systemContext.toTenantHeader(),
  };
}

function toSecretDisplay(
  credential: OpenApiCredentialOnceVO,
  source: SecretOnceDisplayState["source"],
  requestId: string,
): SecretOnceDisplayState {
  return {
    visible: Boolean(credential.secretOnce && credential.secretVisibleOnce),
    source,
    clientId: credential.clientId,
    accessKey: credential.accessKey,
    secretOnce: credential.secretVisibleOnce ? credential.secretOnce : undefined,
    maskedSecret: credential.maskedSecret,
    requestId,
    consumed: false,
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

export type OpenApiClientWritableField =
  | "name"
  | "code"
  | "tenantId"
  | "scopes"
  | "ipWhitelist"
  | "rateLimitPolicy"
  | "expiresAt"
  | "status";

export const OPENAPI_CLIENT_RESPONSE_FIELDS = [
  "clientId",
  "accessKey",
  "maskedSecret",
  "secretVisibleOnce",
  "status",
  "scopes",
  "ipWhitelist",
  "rateLimitPolicy",
  "createdAt",
  "lastUsedAt",
] as const;

export const OPENAPI_ACCESS_LOG_COLUMNS = [
  "requestId",
  "clientId",
  "accessKey",
  "apiId",
  "method",
  "path",
  "statusCode",
  "errorCode",
  "signatureResult",
  "nonceResult",
  "idempotencyResult",
  "rateLimitResult",
  "scopeResult",
  "durationMs",
  "createdAt",
] as const;

export type OpenApiScopeDraft = Pick<
  OpenApiScope,
  "scopeCode" | "moduleCode" | "actions" | "readableFieldCodes" | "writableFieldCodes"
> & {
  dataScope?: DataScopeRuleDTO;
  metadata?: Record<string, JsonValue>;
};
