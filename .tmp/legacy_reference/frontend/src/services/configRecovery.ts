import { apiRequest } from './api'
import type { ConfigRestoreResult, RestoreConfigInput } from '@/types/configRecovery'

const base = (systemId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/admin/config-recovery`

function restore(path: string, input: RestoreConfigInput) {
  return apiRequest<ConfigRestoreResult>(path, {
    method: 'POST',
    body: { ...input, expectedVersion: String(input.expectedVersion) },
    idempotencyKey: crypto.randomUUID(),
  })
}

export const configRecoveryApi = {
  moduleConfig(
    systemId: string,
    versionId: string,
    versionNumber: number,
    input: RestoreConfigInput,
  ) {
    return restore(
      `${base(systemId)}/module-config/versions/${encodeURIComponent(versionId)}/${versionNumber}:restore`,
      input,
    )
  },
  dataSource(systemId: string, resourceId: string, versionNumber: number, input: RestoreConfigInput) {
    return restore(`${base(systemId)}/data-sources/${encodeURIComponent(resourceId)}/versions/${versionNumber}:restore`, input)
  },
  dashboard(systemId: string, resourceId: string, versionNumber: number, input: RestoreConfigInput) {
    return restore(`${base(systemId)}/dashboards/${encodeURIComponent(resourceId)}/versions/${versionNumber}:restore`, input)
  },
  kpi(systemId: string, resourceId: string, versionNumber: number, input: RestoreConfigInput) {
    return restore(`${base(systemId)}/kpis/${encodeURIComponent(resourceId)}/versions/${versionNumber}:restore`, input)
  },
  report(systemId: string, resourceId: string, versionNumber: number, input: RestoreConfigInput) {
    return restore(`${base(systemId)}/reports/${encodeURIComponent(resourceId)}/versions/${versionNumber}:restore`, input)
  },
}
