import { apiRequest } from '@/services/api'
import type { PlatformGlobalSettings, PlatformGlobalSettingsUpdate } from '@/types/platformSettings'

const path = '/api/v1/platform/admin/global-settings'

export const platformSettingsApi = {
  get() { return apiRequest<PlatformGlobalSettings>(path) },
  update(input: PlatformGlobalSettingsUpdate) {
    return apiRequest<PlatformGlobalSettings>(path, { method: 'PUT', body: input })
  },
}
