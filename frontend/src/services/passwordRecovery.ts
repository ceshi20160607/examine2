import { apiRequest } from '@/services/api'

export const passwordRecoveryApi = {
  request(account: string) {
    return apiRequest<void>('/api/v1/auth/password-recovery/request', {
      method: 'POST',
      body: { account },
      idempotencyKey: crypto.randomUUID(),
    })
  },
  reset(token: string, newPassword: string) {
    return apiRequest<void>('/api/v1/auth/password-recovery/reset', {
      method: 'POST',
      body: { token, newPassword },
      idempotencyKey: crypto.randomUUID(),
    })
  },
}
