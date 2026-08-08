import { apiRequest } from '@/services/api'
import type { IdentityPreflightResult, IdentityProvider, IdentityProviderCommand } from '@/types/enterpriseIdentity'

const root = '/api/v1/platform/admin/identity-providers'

function providerPath(providerId: string) {
  return `${root}/${encodeURIComponent(providerId)}`
}

export const enterpriseIdentityAdminApi = {
  list() {
    return apiRequest<IdentityProvider[]>(root)
  },
  create(command: IdentityProviderCommand) {
    return apiRequest<IdentityProvider>(root, { method: 'POST', body: command })
  },
  update(providerId: string, command: IdentityProviderCommand) {
    return apiRequest<IdentityProvider>(providerPath(providerId), { method: 'PUT', body: command })
  },
  preflight(providerId: string, expectedVersion: number) {
    return apiRequest<IdentityPreflightResult>(`${providerPath(providerId)}:preflight`, {
      method: 'POST', body: { expectedVersion },
    })
  },
  publish(providerId: string, expectedVersion: number) {
    return apiRequest<IdentityProvider>(`${providerPath(providerId)}:publish`, {
      method: 'POST', body: { expectedVersion },
    })
  },
  disable(providerId: string, expectedVersion: number) {
    return apiRequest<IdentityProvider>(`${providerPath(providerId)}:disable`, {
      method: 'POST', body: { expectedVersion },
    })
  },
}
