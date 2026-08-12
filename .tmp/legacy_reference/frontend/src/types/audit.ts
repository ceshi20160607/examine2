export type AuditCategory = 'ALL' | 'AUTH' | 'CONFIG' | 'DATA' | 'FLOW' | 'FILE' | 'TASK' | 'OPENAPI' | 'MESSAGE' | 'AI' | 'OPERATION'
export type AuditResult = 'ALL' | 'SUCCESS' | 'DENIED' | 'FAILED' | 'PENDING'
export type OperationsHealthStatus = 'AVAILABLE' | 'DEGRADED' | 'UNCONFIGURED'

export interface AuditLogQuery {
  requestId?: string
  traceId?: string
  actor?: string
  object?: string
  result?: AuditResult
  category?: AuditCategory
  from?: string
  to?: string
  page?: number
  size?: number
}

export interface AuditLogItem {
  id: string
  category: Exclude<AuditCategory, 'ALL'>
  source: string
  event: string
  actorId?: string
  actorName?: string
  objectType?: string
  objectId?: string
  result: Exclude<AuditResult, 'ALL'>
  failureCode?: string
  requestId?: string
  traceId?: string
  systemId?: string
  tenantId?: string
  occurredAt: string
  details: Record<string, string>
}

export interface AuditLogPage {
  items: AuditLogItem[]
  page: number
  size: number
  total: number
}

export interface OperationsHealthComponent {
  code: string
  label: string
  status: OperationsHealthStatus
  summary: string
  hint: string
}

export interface OperationsHealthSummary {
  generatedAt: string
  overallStatus: Exclude<OperationsHealthStatus, 'UNCONFIGURED'>
  version: string
  flywayVersion?: string
  components: OperationsHealthComponent[]
}
