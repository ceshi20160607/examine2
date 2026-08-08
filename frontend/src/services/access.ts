import { apiRequest } from '@/services/api'
import type { AccessRequest, PageResult } from '@/types/admin'
import type { AuthResult, TenantSummary } from '@/types/session'

export const contextAccessApi = {
  listTenants() {
    return apiRequest<TenantSummary[]>('/api/v1/context/tenants')
  },
  switchTenant(tenantId: string) {
    return apiRequest<AuthResult>(`/api/v1/context/tenants/${tenantId}:switch`, { method: 'POST' })
  },
  listOwnRequests(systemId?: string) {
    const query = new URLSearchParams({ page: '1', size: '20' })
    if (systemId) query.set('systemId', systemId)
    return apiRequest<PageResult<AccessRequest>>(`/api/v1/context/access-requests?${query.toString()}`)
  },
  submitRequest(systemId: string, input: { targetTenantId?: string; reason: string }) {
    return apiRequest<AccessRequest>(`/api/v1/context/systems/${systemId}/access-requests`, {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  cancelRequest(requestId: string, version: string) {
    return apiRequest<AccessRequest>(`/api/v1/context/access-requests/${requestId}:cancel`, {
      method: 'POST',
      body: { version },
      idempotencyKey: crypto.randomUUID(),
    })
  },
}
