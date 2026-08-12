import { apiRequest } from './api'
import type {
  TodoActionInput,
  TodoActionResult,
  TodoCounts,
  TodoDetailResult,
  TodoPage,
  TodoPageQuery,
  TodoRefreshResult,
} from '@/types/todo'

function todoRoot(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/todos`
}

export const TODO_COUNTS_CHANGED_EVENT = 'examine:todo-counts-changed'

export const todoApi = {
  list(systemId: string, query: TodoPageQuery = {}) {
    const search = new URLSearchParams({
      category: query.category ?? 'ALL',
      state: query.state ?? 'OPEN',
      time: query.time ?? 'ALL',
      page: String(query.page ?? 1),
      size: String(query.size ?? 20),
    })
    return apiRequest<TodoPage>(`${todoRoot(systemId)}?${search.toString()}`)
  },
  counts(systemId: string) {
    return apiRequest<TodoCounts>(`${todoRoot(systemId)}/counts`)
  },
  detail(systemId: string, todoId: string) {
    return apiRequest<TodoDetailResult>(
      `${todoRoot(systemId)}/${encodeURIComponent(todoId)}`,
    )
  },
  refresh(systemId: string) {
    return apiRequest<TodoRefreshResult>(`${todoRoot(systemId)}:refresh`, {
      method: 'POST',
      idempotencyKey: crypto.randomUUID(),
    })
  },
  action(systemId: string, todoId: string, input: TodoActionInput) {
    return apiRequest<TodoActionResult>(
      `${todoRoot(systemId)}/${encodeURIComponent(todoId)}:action`,
      {
        method: 'POST',
        body: input,
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
}
