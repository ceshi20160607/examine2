export type LifecycleStatus =
  | 'INITIALIZING'
  | 'INIT_FAILED'
  | 'ACTIVE'
  | 'DISABLED'
  | 'ARCHIVED'

export type AccessRequestStatus =
  | 'SUBMITTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'EXPIRED'

export type DraftStatus = 'DRAFT' | 'CHECKED' | 'PUBLISHED'

export type DataScopeKind =
  | 'ALL'
  | 'SELF'
  | 'PRIMARY_DEPARTMENT'
  | 'DEPARTMENT_TREE'
  | 'SELECTED_DEPARTMENTS'
  | 'SELECTED_MEMBERS'
  | 'FIELD_RULE'

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface PageQuery {
  page?: number
  size?: number
  keyword?: string
  status?: string
}

export interface PlatformSystem {
  id: string
  code: string
  name: string
  description: string
  status: LifecycleStatus
  tenantMode: 'SINGLE' | 'MULTI'
  ownerAccountId: string
  version: string
  createdAt?: string
  initFailureCode?: string
  initFailedAt?: string
}

export interface SystemTombstone {
  id: string
  code: string
  name: string
  description: string
  tenantMode: 'SINGLE' | 'MULTI'
  ownerAccountId: string
  version: string
  deletedAt: string
  reason: string
}

export interface PlatformAccount {
  id: string
  username: string
  displayName: string
  email?: string
  mobile?: string
  status: 'ACTIVE' | 'DISABLED' | 'LOCKED'
  departmentIds: string[]
  roleIds: string[]
  version: string
}

export interface Department {
  id: string
  parentId?: string
  name: string
  code: string
  status: 'ACTIVE' | 'DISABLED'
  leaderMemberId?: string | null
  memberCount: number
  version: string
}

export interface PermissionDefinition {
  id: string
  code: string
  name: string
  type: 'SHELL' | 'MENU' | 'ACTION' | 'FIELD' | 'DATA'
  description?: string
}

export interface PermissionEvaluationSourceRole {
  id: string
  code: string
  name: string
  publishedVersion: string
}

export interface PermissionEvaluationDataScope {
  roleId: string
  id: string
  code: string
  kind: DataScopeKind
}

export interface PermissionEvaluationDecision {
  permissionCode: string
  result: 'ALLOW' | 'DENY'
  reason: string
  sourceRoleIds: string[]
}

export interface PermissionEvaluation {
  principalType: string
  principalId: string
  systemId: string | null
  tenantId: string | null
  permissionVersion: string
  sourceRoles: PermissionEvaluationSourceRole[]
  dataScopes: PermissionEvaluationDataScope[]
  decisions: PermissionEvaluationDecision[]
}

export interface Role {
  id: string
  code: string
  name: string
  description?: string
  status: 'ACTIVE' | 'DISABLED'
  builtin: boolean
  memberCount: number
  permissionCodes: string[]
  deniedPermissionCodes: string[]
  dataScopeId?: string
  draftStatus: DraftStatus
  publishedVersion?: string
  version: string
}

export interface DataScope {
  id: string
  name: string
  kind: DataScopeKind
  description?: string
  targetIds: string[]
  version: string
}

export interface SystemSettings {
  id: string
  systemId: string
  name: string
  code: string
  description: string
  tenantMode: 'SINGLE' | 'MULTI'
  defaultTenantId: string
  status: LifecycleStatus
  version: string
}

export interface Tenant {
  id: string
  systemId: string
  code: string
  name: string
  status: 'ACTIVE' | 'DISABLED' | 'ARCHIVED'
  isDefault: boolean
  memberCount: number
  version: string
}

export interface SystemDeletePreview {
  previewId: string
  systemId: string
  systemVersion: string
  eligible: boolean
  dependencies: Record<string, number>
  blockers: string[]
  impactFingerprint: string
  confirmationToken: string
  expiresAt: string
}

export interface SystemDeleteResult {
  systemId: string
  status: 'TOMBSTONED'
  revokedSessions: number
  disabledTenants: number
  previewId: string
  deletedAt: string
}

export interface TenantDomain {
  id: string
  systemId: string
  tenantId: string
  domainName: string
  status: 'PENDING' | 'VERIFIED' | 'DISABLED'
  primary: boolean
  verificationToken: string | null
  verifiedAt: string | null
  version: string
}

export interface TenantQuota {
  id: string
  systemId: string
  tenantId: string
  quotaKey: string
  softLimit: number | null
  hardLimit: number
  usedValue: number
  softLimitExceeded: boolean
  status: 'ACTIVE' | 'DISABLED'
  version: string
}

export interface TenantLifecycleOperation {
  id: string
  systemId: string
  sourceTenantId: string
  targetTenantId: string | null
  operationType: 'BACKUP' | 'MIGRATION' | 'RECOVERY'
  status: 'SUCCEEDED' | 'FAILED'
  reason: string
  snapshotChecksum: string
  result: Record<string, unknown>
  requestedAt: string
  finishedAt: string
}

export interface TenantLifecyclePlan {
  id: string
  systemId: string
  sourceTenantId: string
  targetTenantId: string | null
  operationType: 'RECOVERY_PREVIEW' | 'MIGRATION_PREVIEW'
  status: string
  eligible: boolean
  blockers: string[]
  tableImpacts: Record<string, unknown>
  quotaProjection: Record<string, unknown>
  planFingerprint: string
  confirmationToken: string
  expiresAt: string
  rowCount: number
  estimatedBytes: number
  databaseMigrationVersion: string
}

export interface SystemMember {
  id: string
  accountId: string
  username?: string
  displayName: string
  email?: string
  status: 'ACTIVE' | 'DISABLED' | 'PENDING'
  managerMemberId?: string | null
  primaryDepartmentId?: string
  departmentIds: string[]
  tenantIds: string[]
  roleIds: string[]
  version: string
}

export interface AccessRequest {
  id: string
  systemId: string
  systemName?: string
  accountId: string
  accountName: string
  targetTenantId?: string
  targetTenantName?: string
  reason: string
  status: AccessRequestStatus
  reviewReason?: string
  submittedAt: string
  reviewedAt?: string
  version: string
}

export interface RoleDraftInput {
  name: string
  description?: string
  permissionCodes: string[]
  deniedPermissionCodes?: string[]
  dataScopeId?: string
  version: string
}

export interface LifecycleCommandInput {
  reason: string
  version: string
  impactConfirmed: boolean
}
