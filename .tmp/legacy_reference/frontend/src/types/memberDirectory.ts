export interface RuntimeMemberOption {
  memberId: string
  memberCode: string
  displayName: string
}

export interface RuntimeMemberPage {
  items: RuntimeMemberOption[]
  page: number
  size: number
  total: number
}
