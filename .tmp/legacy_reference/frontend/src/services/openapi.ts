import { apiRequest } from '@/services/api'
import type {
  ChangeOpenApiApplicationStatusInput,
  CreateOpenApiApplicationInput,
  OpenApiCallLogPage,
  OpenApiCallLogQuery,
  OpenApiApplication,
  OpenApiApplicationPage,
  RotateOpenApiSecretRefInput,
  UpdateOpenApiApplicationPolicyInput,
} from '@/types/openapi'

function base(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/admin/openapi/applications`
}

export const openApiApplicationApi = {
  list(systemId: string, page = 1, size = 100) {
    return apiRequest<OpenApiApplicationPage>(
      `${base(systemId)}?page=${page}&size=${size}`,
    )
  },
  detail(systemId: string, applicationId: string) {
    return apiRequest<OpenApiApplication>(
      `${base(systemId)}/${encodeURIComponent(applicationId)}`,
    )
  },
  callLogs(systemId: string, applicationId: string, query: OpenApiCallLogQuery = {}) {
    const params = new URLSearchParams({
      resultCategory: query.resultCategory ?? 'ALL',
      requestMethod: query.requestMethod ?? 'ALL',
      page: String(query.page ?? 1),
      size: String(query.size ?? 20),
    })
    return apiRequest<OpenApiCallLogPage>(
      `${base(systemId)}/${encodeURIComponent(applicationId)}/call-logs?${params.toString()}`,
    )
  },
  create(
    systemId: string,
    input: CreateOpenApiApplicationInput,
    idempotencyKey: string,
  ) {
    return apiRequest<OpenApiApplication>(base(systemId), {
      method: 'POST',
      body: input,
      idempotencyKey,
    })
  },
  updatePolicy(
    systemId: string,
    applicationId: string,
    input: UpdateOpenApiApplicationPolicyInput,
    idempotencyKey: string,
  ) {
    return apiRequest<OpenApiApplication>(
      `${base(systemId)}/${encodeURIComponent(applicationId)}/policy`,
      {
        method: 'PUT',
        body: input,
        idempotencyKey,
      },
    )
  },
  rotateSecretRef(
    systemId: string,
    applicationId: string,
    input: RotateOpenApiSecretRefInput,
    idempotencyKey: string,
  ) {
    return apiRequest<OpenApiApplication>(
      `${base(systemId)}/${encodeURIComponent(applicationId)}:rotate-secret-ref`,
      {
        method: 'POST',
        body: input,
        idempotencyKey,
      },
    )
  },
  changeStatus(
    systemId: string,
    applicationId: string,
    command: 'enable' | 'disable',
    input: ChangeOpenApiApplicationStatusInput,
    idempotencyKey: string,
  ) {
    return apiRequest<OpenApiApplication>(
      `${base(systemId)}/${encodeURIComponent(applicationId)}:${command}`,
      {
        method: 'POST',
        body: input,
        idempotencyKey,
      },
    )
  },
}
