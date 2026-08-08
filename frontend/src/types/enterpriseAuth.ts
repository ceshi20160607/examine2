import type { AuthResult } from './session'

export interface EnterpriseAuthCompletion {
  status: 'AUTHENTICATED' | 'MFA_REQUIRED'
  session?: AuthResult
  redirectUri?: string
  challenge?: string
  enrollmentRequired?: boolean
  enrollment?: MfaEnrollment
}

export interface MfaEnrollment {
  status: string
  secretRefMasked: string
  secretVersion: string
  recoveryCodes: string[]
  verifiedAt?: string
}

export interface AccountProfile {
  id: string
  username: string
  displayName: string
  email?: string
  phone?: string
  locale?: string
  timeZone?: string
  version: number
}

export interface OwnedSession {
  id: string
  contextType: 'PLATFORM' | 'SYSTEM'
  systemId?: string
  tenantId?: string
  status: 'ACTIVE' | 'REVOKED'
  current: boolean
  issuedAt: string
  lastSeenAt?: string
  expiresAt: string
  revokedAt?: string
}
