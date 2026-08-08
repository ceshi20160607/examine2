import { apiRequest } from '@/services/api'
import type { AuditLogPage, AuditLogQuery, OperationsHealthSummary } from '@/types/audit'

function queryString(query: AuditLogQuery) {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  if (!params.has('page')) params.set('page', '1')
  if (!params.has('size')) params.set('size', '20')
  return params.toString()
}

export const auditApi = {
  platformLogs(query: AuditLogQuery = {}) {
    return apiRequest<AuditLogPage>(`/api/v1/platform/admin/audit-logs?${queryString(query)}`)
  },
  systemLogs(systemId: string, query: AuditLogQuery = {}) {
    return apiRequest<AuditLogPage>(
      `/api/v1/systems/${encodeURIComponent(systemId)}/admin/audit-logs?${queryString(query)}`,
    )
  },
  platformHealth() {
    return apiRequest<OperationsHealthSummary>('/api/v1/platform/admin/audit-health')
  },
  systemHealth(systemId: string) {
    return apiRequest<OperationsHealthSummary>(
      `/api/v1/systems/${encodeURIComponent(systemId)}/admin/audit-health`,
    )
  },
}
