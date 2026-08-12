export type PlatformTodoStateFilter = 'ALL' | 'OPEN' | 'CLOSED'
export type PlatformTodoAction = 'COMPLETE' | 'REOPEN' | 'CANCEL'

export type PlatformTodoTypeFilter = 'ALL' | 'TASK' | 'TODAY' | 'REMINDER' | 'APPROVAL' | 'FAILED'

export interface PlatformTodo {
  id: string
  context: 'PLATFORM'
  source: 'PLATFORM_TASK'
  category: 'TASK' | 'TODAY' | 'REMINDER' | 'APPROVAL' | 'FAILED'
  title: string
  description: string | null
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
  state: 'OPEN' | 'CLOSED'
  sourceStatus: 'OPEN' | 'COMPLETED' | 'CANCELLED'
  dueAt: string | null
  routeHint: string
  availableActions: PlatformTodoAction[]
  createdAt: string
  updatedAt: string
  version: number
}

export interface PlatformTodoPage {
  items: PlatformTodo[]
  page: number
  size: number
  total: number
}

export interface PlatformTodoCounts { open: number; closed: number; total: number; today: number; reminders: number; approvals: number; failures: number }
export interface PlatformTodoActionResult { todo: PlatformTodo; replayed: boolean }
