import { apiRequest } from './api'
import type { RuntimeQuickCreateModules } from '@/types/quickCreate'

const base = (systemId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/runtime/quick-create-modules`

export const quickCreateApi = {
  list(systemId: string) {
    return apiRequest<RuntimeQuickCreateModules>(base(systemId))
  },
}
