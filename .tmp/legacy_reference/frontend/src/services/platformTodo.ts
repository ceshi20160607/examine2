import { apiRequest } from '@/services/api'
import type {
  PlatformTodo,
  PlatformTodoAction,
  PlatformTodoActionResult,
  PlatformTodoCounts,
  PlatformTodoPage,
  PlatformTodoStateFilter,
  PlatformTodoTypeFilter,
} from '@/types/platformTodo'

const root = '/api/v1/platform/todos'

export const platformTodoApi = {
  list(query: { state?: PlatformTodoStateFilter; type?: PlatformTodoTypeFilter; page?: number; size?: number } = {}) {
    const params = new URLSearchParams({
      state: query.state ?? 'OPEN',
      type: query.type ?? 'ALL',
      page: String(query.page ?? 1),
      size: String(query.size ?? 20),
    })
    return apiRequest<PlatformTodoPage>(`${root}?${params.toString()}`)
  },
  counts() {
    return apiRequest<PlatformTodoCounts>(`${root}/counts`)
  },
  detail(todoId: string) {
    return apiRequest<PlatformTodo>(`${root}/${encodeURIComponent(todoId)}`)
  },
  action(todoId: string, action: PlatformTodoAction, version: number) {
    return apiRequest<PlatformTodoActionResult>(
      `${root}/${encodeURIComponent(todoId)}:action`,
      {
        method: 'POST',
        body: { action, version },
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
}
