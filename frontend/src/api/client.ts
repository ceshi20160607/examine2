import { API_ENDPOINTS, type ApiEndpointId, type ApiGroup, getEndpointDefinition } from "./endpoints";
import type { ApiErrorResponse, ApiResult, ApiResponse, HttpMethod, JsonValue } from "./types";

export interface ApiContext {
  accessToken?: string;
  tenantId?: string;
  requestId?: string;
}

export interface ApiRequestOptions<TBody = unknown, TQuery = Record<string, unknown>> {
  pathParams?: Record<string, string | number>;
  query?: TQuery;
  body?: TBody;
  headers?: Record<string, string>;
  idempotencyKey?: string;
  context?: ApiContext;
}

export interface TransportRequestConfig {
  method: HttpMethod;
  url: string;
  params?: unknown;
  data?: unknown;
  headers: Record<string, string>;
}

export interface HttpTransport {
  request<TData>(config: TransportRequestConfig): Promise<{ data: ApiResult<TData> }>;
}

export interface ApiClient {
  call<TData, TBody = unknown, TQuery = Record<string, unknown>>(
    apiId: ApiEndpointId,
    options?: ApiRequestOptions<TBody, TQuery>,
  ): Promise<ApiResponse<TData>>;
  endpoint(apiId: ApiEndpointId): (typeof API_ENDPOINTS)[ApiEndpointId];
  group(group: ApiGroup): ApiEndpointId[];
}

export interface CreateApiClientOptions {
  transport: HttpTransport;
  getContext?: () => ApiContext;
}

export function createApiClient(options: CreateApiClientOptions): ApiClient {
  const { transport, getContext } = options;

  return {
    async call<TData, TBody = unknown, TQuery = Record<string, unknown>>(
      apiId: ApiEndpointId,
      requestOptions: ApiRequestOptions<TBody, TQuery> = {},
    ): Promise<ApiResponse<TData>> {
      const endpoint = getEndpointDefinition(apiId);
      const context = { ...getContext?.(), ...requestOptions.context };
      const url = interpolatePath(endpoint.path, requestOptions.pathParams);
      const headers = buildHeaders(endpoint.idempotencyRequired, context, requestOptions);
      const response = await transport.request<TData>({
        method: endpoint.method,
        url,
        params: requestOptions.query,
        data: requestOptions.body,
        headers,
      });

      if (isApiErrorResponse(response.data)) {
        throw createApiError(response.data);
      }
      return response.data;
    },

    endpoint(apiId) {
      return API_ENDPOINTS[apiId];
    },

    group(group) {
      return Object.values(API_ENDPOINTS)
        .filter((endpoint) => endpoint.group === group)
        .map((endpoint) => endpoint.id as ApiEndpointId);
    },
  };
}

function buildHeaders<TBody, TQuery>(
  idempotencyRequired: boolean,
  context: ApiContext,
  options: ApiRequestOptions<TBody, TQuery>,
): Record<string, string> {
  const headers: Record<string, string> = {
    ...(options.headers ?? {}),
  };

  if (context.accessToken) {
    headers.Authorization = `Bearer ${context.accessToken}`;
  }
  if (context.tenantId) {
    headers["X-Tenant-Id"] = context.tenantId;
  }
  if (context.requestId) {
    headers["X-Request-Id"] = context.requestId;
  }
  if (options.idempotencyKey) {
    headers["X-Idempotency-Key"] = options.idempotencyKey;
  }
  if (idempotencyRequired && !headers["X-Idempotency-Key"] && !hasBodyIdempotencyKey(options.body)) {
    throw new Error("Idempotency key is required by the frozen API contract.");
  }

  return headers;
}

function interpolatePath(path: string, params: ApiRequestOptions["pathParams"] = {}): string {
  return path.replace(/\{([^}]+)\}/g, (_, key: string) => {
    const value = params[key];
    if (value === undefined || value === null || value === "") {
      throw new Error(`Missing path parameter: ${key}`);
    }
    return encodeURIComponent(String(value));
  });
}

function hasBodyIdempotencyKey(body: unknown): boolean {
  if (!body || typeof body !== "object") {
    return false;
  }
  const candidate = body as { idempotencyKey?: unknown };
  return typeof candidate.idempotencyKey === "string" && candidate.idempotencyKey.length > 0;
}

function isApiErrorResponse<TData>(result: ApiResult<TData>): result is ApiErrorResponse {
  return result.success === false;
}

export class ApiClientError extends Error {
  readonly requestId: string;
  readonly code: string;
  readonly retryable: boolean;
  readonly details: JsonValue;

  constructor(error: ApiErrorResponse) {
    super(error.message);
    this.name = "ApiClientError";
    this.requestId = error.requestId;
    this.code = error.code;
    this.retryable = error.errors.some((item) => item.retryable);
    this.details = error as unknown as JsonValue;
  }
}

function createApiError(error: ApiErrorResponse): ApiClientError {
  return new ApiClientError(error);
}
