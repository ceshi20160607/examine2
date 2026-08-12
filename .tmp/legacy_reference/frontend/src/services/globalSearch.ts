import { apiRequest } from './api'
import type {
  RuntimeGlobalSearchPage,
  RuntimeGlobalSearchQuery,
} from '@/types/globalSearch'

const base = (systemId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/runtime/global-search`

export const globalSearchApi = {
  search(systemId: string, query: RuntimeGlobalSearchQuery) {
    const params = new URLSearchParams({
      q: query.q.trim(),
      page: String(query.page ?? 1),
      size: String(query.size ?? 20),
    })
    return apiRequest<RuntimeGlobalSearchPage>(`${base(systemId)}?${params}`)
  },
}
