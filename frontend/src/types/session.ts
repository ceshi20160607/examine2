export type ContextType = 'PLATFORM' | 'SYSTEM'

export interface AccountSummary {
  id: string
  username: string
  displayName: string
}

export interface SystemSummary {
  id: string
  code: string
  name: string
  status: 'INITIALIZING' | 'INIT_FAILED' | 'ACTIVE' | 'DISABLED' | 'ARCHIVED'
  memberStatus: 'ACTIVE' | 'DISABLED' | 'PENDING'
  defaultTenantId: string
}

export interface TenantSummary {
  id: string
  code: string
  name: string
  status: 'ACTIVE' | 'DISABLED' | 'ARCHIVED'
  isDefault: boolean
}

export interface SessionContext {
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

export interface AuthResult {
  account: AccountSummary
  context: SessionContext
  systems: SystemSummary[]
  tenants?: TenantSummary[]
  firstSystemId?: string
}
