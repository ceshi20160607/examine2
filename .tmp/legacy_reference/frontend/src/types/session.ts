export type ContextType = 'PLATFORM' | 'SYSTEM'

export interface AccountSummary {
  id: string
  username: string
  displayName: string
}

export interface LoginInput {
  account: string
  password: string
}

export interface RegisterFirstSystemInput {
  username: string
  displayName: string
  password: string
  systemName: string
  systemCode: string
}

export interface SystemSummary {
  id: string
  code: string
  name: string
  status: 'INITIALIZING' | 'INIT_FAILED' | 'ACTIVE' | 'DISABLED' | 'ARCHIVED'
  memberStatus: 'ACTIVE' | 'DISABLED' | 'PENDING'
  defaultTenantId: string | null
  roleNames: string[]
  recentEnteredAt: string | null
}

export interface TenantSummary {
  id: string
  code: string
  name: string
  status: 'ACTIVE' | 'DISABLED' | 'ARCHIVED'
  isDefault: boolean
}

export interface DataScopeContext {
  id: string
  code: string
  kind: string
}

export type RestrictedMode = 'NONE' | 'ADMIN_SETTINGS_ONLY'

interface SessionContextBase {
  type: ContextType
  account: AccountSummary
  systemId?: string
  systemName?: string
  tenantId?: string
  tenantName?: string
  memberId?: string
  permissionVersion: string
  permissions: string[]
  shells: Array<'PLATFORM_RUNTIME' | 'PLATFORM_ADMIN' | 'SYSTEM_RUNTIME' | 'SYSTEM_ADMIN'>
}

interface P1SessionSnapshot {
  roleIds: string[]
  dataScope: DataScopeContext | null
  restrictedMode: RestrictedMode
}

// Frozen legacy shells only consume the pre-P1 context view. They cannot be
// supplied to AuthResult/applyAuth or any vNext API boundary.
export type SessionContext = SessionContextBase & Partial<P1SessionSnapshot>

export type P1SessionContext = SessionContextBase & P1SessionSnapshot

export interface AuthResult {
  account: AccountSummary
  context: P1SessionContext
  systems: SystemSummary[]
  tenants: TenantSummary[]
  firstSystemId: string | null
}

export type SystemsState = 'loading' | 'ready' | 'empty_create_first_system_guidance' | 'error'
