export type OpenApiApplicationStatus = 'ACTIVE' | 'DISABLED'

export interface OpenApiApplication {
  id: string
  appKey: string
  name: string
  status: OpenApiApplicationStatus
  systemId: string
  tenantId: string
  serviceMemberId: string
  secretRef: string
  scopes: string[]
  ipAllowlist: string[]
  rateLimitPerMinute: number
  credentialVersion: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface OpenApiApplicationPage {
  items: OpenApiApplication[]
  page: number
  size: number
  total: number
}

export type OpenApiCallLogResultCategory =
  | 'SUCCESS'
  | 'AUTH_REJECTED'
  | 'SIGNATURE_REJECTED'
  | 'REPLAY_REJECTED'
  | 'SCOPE_REJECTED'
  | 'PERMISSION_REJECTED'
  | 'IP_REJECTED'
  | 'RATE_REJECTED'
  | 'FAILED'

export type OpenApiCallLogResultFilter = 'ALL' | OpenApiCallLogResultCategory

export type OpenApiCallLogRequestMethod =
  | 'GET'
  | 'HEAD'
  | 'POST'
  | 'PUT'
  | 'PATCH'
  | 'DELETE'
  | 'OPTIONS'

export type OpenApiCallLogMethodFilter = 'ALL' | OpenApiCallLogRequestMethod

export interface OpenApiCallLog {
  id: string
  credentialVersion: number | null
  routeTemplate: string
  requestMethod: OpenApiCallLogRequestMethod
  resultCategory: OpenApiCallLogResultCategory
  httpStatus: number
  latencyMs: number
  requestId: string
  traceId: string
  observedIp: string
  createdAt: string
}

export interface OpenApiCallLogPage {
  items: OpenApiCallLog[]
  page: number
  size: number
  total: number
}

export interface OpenApiCallLogQuery {
  resultCategory?: OpenApiCallLogResultFilter
  requestMethod?: OpenApiCallLogMethodFilter
  page?: number
  size?: number
}

export interface CreateOpenApiApplicationInput {
  name: string
  tenantId: string
  serviceMemberId: string
  secretRef: string
  scopes: string[]
  ipAllowlist: string[]
  rateLimitPerMinute: number
}

export interface UpdateOpenApiApplicationPolicyInput {
  scopes: string[]
  ipAllowlist: string[]
  rateLimitPerMinute: number
  version: number
}

export interface RotateOpenApiSecretRefInput {
  secretRef: string
  version: number
}

export interface ChangeOpenApiApplicationStatusInput {
  version: number
  reason: string
}
