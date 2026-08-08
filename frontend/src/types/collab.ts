export type RecordTeamRole = 'OWNER' | 'COLLABORATOR' | 'VIEWER' | 'FOLLOWER'
export type AssignableRecordTeamRole = Exclude<RecordTeamRole, 'OWNER'>

export interface RecordTeamMember {
  memberId: string
  role: RecordTeamRole
}

export interface RecordTeam {
  systemId: string
  tenantId: string
  recordId: string
  version: number
  ownerMemberId: string
  members: RecordTeamMember[]
}

export interface InitializeRecordTeamResult {
  created: boolean
  team: RecordTeam
}

export interface AddRecordTeamMemberInput {
  memberId: string
  role: AssignableRecordTeamRole
}

export interface ChangeRecordTeamRoleInput {
  role: AssignableRecordTeamRole
}

export interface TransferRecordTeamOwnershipInput {
  targetMemberId: string
}
