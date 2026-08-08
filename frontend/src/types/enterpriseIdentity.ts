export type IdentityProtocol = 'OIDC' | 'OAUTH2' | 'SAML2' | 'LDAP' | 'AD' | 'WECOM' | 'DINGTALK'
export type IdentityProviderStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'
export type IdentityMfaPolicy = 'DISABLED' | 'OPTIONAL' | 'REQUIRED'

export interface IdentityProviderCommand {
  providerCode: string
  name: string
  protocol: IdentityProtocol
  issuerUri: string | null
  authorizationEndpoint: string | null
  tokenEndpoint: string | null
  jwksUri: string | null
  directoryEndpoint: string | null
  clientId: string | null
  secretRef: string
  secretVersion: string
  callbackUri: string
  scopes: string
  allowedDomains: string[]
  attributeMapping: Record<string, string>
  jitAccount: boolean
  jitSystemMember: boolean
  systemId: string | null
  tenantId: string | null
  mfaPolicy: IdentityMfaPolicy
  expectedVersion: number | null
}

export interface IdentityProvider extends Omit<IdentityProviderCommand, 'secretRef' | 'expectedVersion'> {
  id: string
  secretRefMasked: string
  status: IdentityProviderStatus
  preflightStatus: 'NOT_RUN' | 'PASSED' | 'FAILED'
  preflightVersion: number | null
  preflightFailureCode: string | null
  preflightAt: string | null
  publishedAt: string | null
  version: number
}

export interface IdentityPreflightResult {
  providerId: string
  protocol: IdentityProtocol
  successful: boolean
  failureCode: string | null
  checks: string[]
  checkedAt: string
  providerVersion: number
}
