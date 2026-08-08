import { apiRequest } from './api'
import type { RuntimeMemberPage } from '@/types/memberDirectory'

export const memberDirectoryApi = {
  list(systemId: string, keyword = '', page = 1, size = 20) {
    const query = new URLSearchParams({
      page: String(page),
      size: String(size),
    })
    const normalized = keyword.trim()
    if (normalized) query.set('keyword', normalized)
    return apiRequest<RuntimeMemberPage>(
      `/api/v1/systems/${encodeURIComponent(systemId)}/directory/members?${query}`,
    )
  },
}
