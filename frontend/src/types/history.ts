export interface RecordHistoryDiff {
  fieldCode: string
  beforeValue: unknown
  afterValue: unknown
  masked: boolean
}

export interface RecordHistoryItem {
  historyId: string
  recordVersion: number
  action: string
  actorMemberId: string | null
  occurredAt: string
  diff: RecordHistoryDiff[]
}

export interface RecordHistoryPage {
  items: RecordHistoryItem[]
  page: number
  size: number
  total: number
}
