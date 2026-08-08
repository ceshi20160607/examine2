export interface InheritedIdentityProvider {
  id: string
  providerCode: string
  name: string
  protocol: string
  providerDomains: string[]
  jitAccount: boolean
  mfaPolicy: string
  version: number
}

export interface IdentitySyncPolicyCommand {
  tenantId: string
  providerId: string
  allowedDomains: string[]
  jitSystemMember: boolean
  unmatchedAction: 'REQUIRE_REVIEW' | 'SKIP'
  scheduleEnabled: boolean
  scheduleIntervalMinutes: number | null
  expectedVersion: number | null
}

export interface IdentitySyncPolicy extends Omit<IdentitySyncPolicyCommand, 'expectedVersion'> {
  id: string
  systemId: string
  providerCode: string
  providerName: string
  nextSyncAt: string | null
  status: 'DRAFT' | 'ACTIVE' | 'DISABLED'
  lastConfirmedSnapshotId: string | null
  lastSyncAt: string | null
  version: number
}

export interface ExternalIdentityDepartment {
  externalId: string
  name: string
  parentExternalId?: string | null
  targetDepartmentId?: string | null
  attributes?: Record<string, unknown>
}

export interface ExternalIdentityEmployee {
  externalUserId: string
  email: string
  displayName: string
  employeeNo?: string | null
  departmentExternalId?: string | null
  targetMemberId?: string | null
  attributes?: Record<string, unknown>
}

export interface IdentitySyncItem {
  id: string
  kind: 'DEPARTMENT' | 'EMPLOYEE'
  externalId: string
  displayName: string
  parentExternalId: string | null
  email: string | null
  departmentExternalId: string | null
  targetDepartmentId: string | null
  targetAccountId: string | null
  targetMemberId: string | null
  proposedAction: 'BIND' | 'CREATE' | 'UPDATE' | 'SKIP' | 'UNMATCHED'
  issueCode: string | null
  attributes: Record<string, unknown>
}

export interface IdentitySyncSnapshot {
  id: string
  policyId: string
  sourceVersion: string
  status: 'DRAFT' | 'CONFIRMED' | 'QUEUED' | 'APPLIED' | 'PARTIAL_FAILED'
  departmentCount: number
  employeeCount: number
  matchedCount: number
  createCount: number
  unmatchedCount: number
  confirmedAt: string | null
  confirmedBy: string | null
  appliedAt: string | null
  version: number
  items: IdentitySyncItem[]
}

export interface IdentitySyncFailure {
  itemId: string
  kind: string
  externalId: string
  failureCode: string
  failureMessage: string
  createdAt: string
}

export interface IdentitySyncTask {
  id: string
  snapshotId: string
  trigger: 'MANUAL' | 'SCHEDULED'
  status: string
  progressPercent: number
  attemptCount: number
  maxAttempts: number
  lastError: string | null
  result: Record<string, unknown>
  availableAt: string
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  failures: IdentitySyncFailure[]
}
