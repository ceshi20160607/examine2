export type TodoCategory = 'APPROVAL' | 'TASK' | 'REMINDER' | 'CC'
export type TodoCategoryFilter = 'ALL' | TodoCategory
export type TodoState = 'OPEN' | 'CLOSED'
export type TodoStateFilter = 'ALL' | TodoState
export type TodoTimeFilter = 'ALL' | 'TODAY' | 'OVERDUE'
export type TodoActionCode = 'COMPLETE' | 'APPROVE' | 'REJECT' | 'MARK_READ'
export type TodoSourceType = 'WORK_TASK' | 'FLOW_APPROVAL' | 'EVENT_MESSAGE'

export interface TodoItem {
  id: string
  systemId: string
  tenantId: string
  recipientMemberId: string
  sourceType: TodoSourceType
  sourceId: string
  sourceVersion: number
  actionScope: string
  category: TodoCategory
  title: string
  dueAt: string | null
  priority: number
  routeHint: string | null
  representedMemberId: string | null
  availableActions: TodoActionCode[]
  status: TodoState
  closeReason: string | null
  createdAt: string
  updatedAt: string
  closedAt: string | null
  version: number
}

export interface TodoPage {
  items: TodoItem[]
  page: number
  size: number
  total: number
}

export interface TodoPageQuery {
  category?: TodoCategoryFilter
  state?: TodoStateFilter
  time?: TodoTimeFilter
  page?: number
  size?: number
}

export interface TodoCounts {
  openCount: number
  taskCount: number
  approvalCount: number
  todayCount: number
  overdueCount: number
}

export interface TodoRefreshResult {
  discovered: number
  created: number
  updated: number
  closed: number
}

export interface TodoActionInput {
  version: number
  action: TodoActionCode
  comment?: string | null
  reason?: string | null
}

export type TodoDetailStatus = 'LIVE' | 'STALE' | 'DENIED'

export interface TodoDetailResult {
  status: TodoDetailStatus
  todo: TodoItem
}

export type TodoActionStatus =
  | 'SUCCESS'
  | 'STALE'
  | 'DENIED'
  | 'CONFLICT'
  | 'FAILED'
  | 'IN_PROGRESS'

export interface TodoSourceActionResult {
  code: string
  message: string
  sourceVersion: number
}

export interface TodoActionResult {
  status: TodoActionStatus
  replayed: boolean
  todo: TodoItem
  sourceResult: TodoSourceActionResult
}
