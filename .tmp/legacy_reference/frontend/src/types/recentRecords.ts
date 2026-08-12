export interface RuntimeRecentRecordItem {
  recentId: string
  moduleCode: string
  recordId: string
  displayLabel: string
  status: string
  accessCount: number
  lastAccessedAt: string
}

export interface RuntimeRecentRecordPage {
  items: RuntimeRecentRecordItem[]
  page: number
  size: number
  total: number
}

export interface RuntimeRecentRecordTouchInput {
  moduleCode: string
  recordId: string
}
