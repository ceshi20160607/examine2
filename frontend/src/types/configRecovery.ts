export type ConfigRecoveryOwner =
  | 'MODULE_CONFIG'
  | 'DATA_SOURCE'
  | 'DASHBOARD'
  | 'KPI'
  | 'REPORT'

export interface RestoreConfigInput {
  expectedVersion: string | number
  reason: string
}

export interface ConfigRestoreResult {
  owner: ConfigRecoveryOwner
  resourceId: string
  sourceVersionNumber: number
  draftVersion: string
  activeVersionReference: string | null
  state: 'DRAFT_RESTORED'
}
