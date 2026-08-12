import { apiRequest } from './api'
import type { AccountProfile, EnterpriseAuthCompletion, MfaEnrollment, OwnedSession } from '@/types/enterpriseAuth'

export const enterpriseAuthApi = {
  start(providerCode: string, redirectUri: string) {
    const query = new URLSearchParams({ providerCode, redirectUri })
    return apiRequest<{ authorizationUrl: string; state: string; expiresAt: string }>(`/api/v1/auth/sso/start?${query}`)
  },
  callback(state: string, code: string) {
    return apiRequest<EnterpriseAuthCompletion>('/api/v1/auth/sso/callback', {
      method: 'POST', body: { state, code },
    })
  },
  directory(providerCode: string, username: string, password: string) {
    return apiRequest<EnterpriseAuthCompletion>('/api/v1/auth/sso/directory', {
      method: 'POST', body: { providerCode, username, password },
    })
  },
  verifyMfa(challenge: string, totpCode?: string, recoveryCode?: string) {
    return apiRequest<EnterpriseAuthCompletion>('/api/v1/auth/sso/mfa:verify', {
      method: 'POST', body: { challenge, totpCode, recoveryCode },
    })
  },
  enrollMfa(challenge: string, secretRef: string, secretVersion: string, totpCode: string) {
    return apiRequest<EnterpriseAuthCompletion>('/api/v1/auth/sso/mfa:enroll', {
      method: 'POST', body: { challenge, secretRef, secretVersion, totpCode },
    })
  },
  enrollAuthenticated(secretRef: string, secretVersion: string, totpCode: string) {
    return apiRequest<MfaEnrollment>('/api/v1/auth/sso/mfa:enroll-authenticated', {
      method: 'POST', body: { secretRef, secretVersion, totpCode },
    })
  },
}

export const accountApi = {
  profile: () => apiRequest<AccountProfile>('/api/v1/account/profile'),
  updateProfile: (profile: Omit<AccountProfile, 'id' | 'username'>) => apiRequest<AccountProfile>('/api/v1/account/profile', {
    method: 'PUT', body: profile,
  }),
  sessions: () => apiRequest<OwnedSession[]>('/api/v1/account/sessions'),
  revokeSession: (id: string) => apiRequest<void>(`/api/v1/account/sessions/${id}:revoke`, { method: 'POST' }),
  revokeOthers: () => apiRequest<void>('/api/v1/account/sessions:revoke-others', { method: 'POST' }),
}
