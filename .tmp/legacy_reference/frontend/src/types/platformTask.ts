export type PlatformTaskStatus = 'OPEN' | 'COMPLETED' | 'CANCELLED'

export type PlatformTaskStatusFilter = 'ALL' | PlatformTaskStatus

export type PlatformTaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'

export interface PlatformTask {
  taskId: string
  title: string
  description: string | null
  dueAt: string | null
  priority: PlatformTaskPriority
  status: PlatformTaskStatus
  source: 'AGENT' | 'WORK'
  createdAt: string
  updatedAt: string
  completedAt: string | null
  cancelledAt: string | null
  version: number
}

export interface PlatformTaskPage {
  items: PlatformTask[]
  page: number
  size: number
  total: number
}

export interface PlatformTaskListQuery {
  status?: PlatformTaskStatusFilter
  page?: number
  size?: number
}

export interface PlatformTaskVersionInput {
  version: number
}
