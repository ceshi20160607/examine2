export interface OperationsRange {
  from: string
  to: string
  days: number
}

export interface WorkDailyMetric {
  date: string
  created: number
  completed: number
}

export interface WorkTopAssigneeMetric {
  memberId: string
  openCount: number
  route: string | null
}

export interface WorkOperationsSection {
  available: boolean
  unavailableReason: string | null
  openCount: number
  overdueOpenCount: number
  dueInRangeOpenCount: number
  completedInRangeCount: number
  route: string | null
  openRoute: string | null
  overdueOpenRoute: string | null
  dueInRangeOpenRoute: string | null
  completedInRangeRoute: string | null
  daily: WorkDailyMetric[]
  topAssignees: WorkTopAssigneeMetric[]
}

export interface FlowDailyMetric {
  date: string
  started: number
  terminal: number
}

export interface FlowTerminalMetric {
  status: 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'TERMINATED'
  count: number
  route: string | null
}

export interface FlowOperationsSection {
  available: boolean
  unavailableReason: string | null
  pendingCount: number
  pendingRoute: string | null
  terminalInRangeCount: number
  terminalInRangeRoute: string | null
  approvedInRangeCount: number
  rejectedInRangeCount: number
  withdrawnInRangeCount: number
  terminatedInRangeCount: number
  daily: FlowDailyMetric[]
  terminalBreakdown: FlowTerminalMetric[]
  route: string | null
}

export interface TodoOperationsSection {
  available: boolean
  unavailableReason: string | null
  openCount: number
  taskCount: number
  approvalCount: number
  todayCount: number
  overdueCount: number
  route: string | null
}

export interface OperationsAnalyticsSnapshot {
  generatedAt: string
  range: OperationsRange
  work: WorkOperationsSection
  flow: FlowOperationsSection
  todo: TodoOperationsSection
}
