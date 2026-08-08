import { apiRequest } from '@/services/api'
import type {
  PlatformTask,
  PlatformTaskListQuery,
  PlatformTaskPage,
  PlatformTaskVersionInput,
} from '@/types/platformTask'

const taskRoot = '/api/v1/platform/tasks'

function transition(taskId: string, action: 'complete' | 'reopen' | 'cancel', input: PlatformTaskVersionInput) {
  return apiRequest<PlatformTask>(`${taskRoot}/${encodeURIComponent(taskId)}:${action}`, {
    method: 'POST',
    body: input,
  })
}

export const platformTaskApi = {
  list(query: PlatformTaskListQuery = {}) {
    const params = new URLSearchParams({
      status: query.status ?? 'ALL',
      page: String(query.page ?? 1),
      size: String(query.size ?? 20),
    })
    return apiRequest<PlatformTaskPage>(`${taskRoot}?${params.toString()}`)
  },
  complete(taskId: string, input: PlatformTaskVersionInput) {
    return transition(taskId, 'complete', input)
  },
  reopen(taskId: string, input: PlatformTaskVersionInput) {
    return transition(taskId, 'reopen', input)
  },
  cancel(taskId: string, input: PlatformTaskVersionInput) {
    return transition(taskId, 'cancel', input)
  },
}
