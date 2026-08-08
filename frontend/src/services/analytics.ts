import { apiRequest } from './api'
import type { OperationsAnalyticsSnapshot } from '@/types/analytics'

function analyticsRoot(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/analytics/operations`
}

export const analyticsApi = {
  operations(systemId: string, from: string, to: string) {
    const search = new URLSearchParams({ from, to })
    return apiRequest<OperationsAnalyticsSnapshot>(
      `${analyticsRoot(systemId)}?${search.toString()}`,
    )
  },
}
