import { apiRequest } from './api'
import type { RecordHistoryPage } from '@/types/history'

function root(systemId: string, moduleCode: string, recordId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}`
    + `/runtime/modules/${encodeURIComponent(moduleCode)}`
    + `/records/${encodeURIComponent(recordId)}/history`
}

export const recordHistoryApi = {
  list(systemId: string, moduleCode: string, recordId: string, page = 1, size = 20) {
    return apiRequest<RecordHistoryPage>(
      `${root(systemId, moduleCode, recordId)}?page=${page}&size=${size}`,
    )
  },
}
