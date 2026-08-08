export interface RuntimeGlobalSearchItem {
  moduleCode: string
  moduleName: string
  recordId: string
  recordNo: string
  displayLabel: string
  status: string
  matchedFieldCodes: string[]
}

export interface RuntimeGlobalSearchPage {
  items: RuntimeGlobalSearchItem[]
  page: number
  size: number
  total: number
}

export interface RuntimeGlobalSearchQuery {
  q: string
  page?: number
  size?: number
}
