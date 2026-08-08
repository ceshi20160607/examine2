import { apiRequest } from './api'
import type {
  RuntimeRecentRecordItem,
  RuntimeRecentRecordPage,
  RuntimeRecentRecordTouchInput,
} from '@/types/recentRecords'

const base = (systemId: string) => `/api/v1/systems/${systemId}/runtime/recent-records`

export const recentRecordApi = {
  list(systemId: string, page = 1, size = 20) {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    return apiRequest<RuntimeRecentRecordPage>(`${base(systemId)}?${params}`)
  },
  touch(systemId: string, body: RuntimeRecentRecordTouchInput) {
    return apiRequest<RuntimeRecentRecordItem>(`${base(systemId)}:touch`, {
      method: 'POST',
      body,
    })
  },
}
