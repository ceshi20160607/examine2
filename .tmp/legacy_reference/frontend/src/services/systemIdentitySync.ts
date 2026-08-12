import { apiRequest } from '@/services/api'
import type {
  ExternalIdentityDepartment,
  ExternalIdentityEmployee,
  IdentitySyncPolicy,
  IdentitySyncPolicyCommand,
  IdentitySyncSnapshot,
  IdentitySyncTask,
  InheritedIdentityProvider,
} from '@/types/systemIdentitySync'

function root(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/admin/identity-sync`
}

function policyRoot(systemId: string, policyId: string) {
  return `${root(systemId)}/policies/${encodeURIComponent(policyId)}`
}

export const systemIdentitySyncApi = {
  providers(systemId: string) {
    return apiRequest<InheritedIdentityProvider[]>(`${root(systemId)}/providers`)
  },
  policies(systemId: string, tenantId?: string) {
    const query = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ''
    return apiRequest<IdentitySyncPolicy[]>(`${root(systemId)}/policies${query}`)
  },
  savePolicy(systemId: string, command: IdentitySyncPolicyCommand) {
    return apiRequest<IdentitySyncPolicy>(`${root(systemId)}/policies`, { method: 'POST', body: command })
  },
  preflight(systemId: string, policyId: string, command: {
    sourceVersion: string
    expectedPolicyVersion: number
    departments: ExternalIdentityDepartment[]
    employees: ExternalIdentityEmployee[]
  }) {
    return apiRequest<IdentitySyncSnapshot>(`${policyRoot(systemId, policyId)}/snapshots:preflight`, {
      method: 'POST', body: command,
    })
  },
  snapshots(systemId: string, policyId: string) {
    return apiRequest<IdentitySyncSnapshot[]>(`${policyRoot(systemId, policyId)}/snapshots`)
  },
  confirm(systemId: string, policyId: string, snapshotId: string,
    expectedPolicyVersion: number, expectedSnapshotVersion: number, approveUnmatched: boolean) {
    return apiRequest<IdentitySyncSnapshot>(
      `${policyRoot(systemId, policyId)}/snapshots/${encodeURIComponent(snapshotId)}:confirm`,
      { method: 'POST', body: { expectedPolicyVersion, expectedSnapshotVersion, approveUnmatched } },
    )
  },
  start(systemId: string, policyId: string, snapshotId: string, expectedSnapshotVersion: number) {
    return apiRequest<IdentitySyncTask>(
      `${policyRoot(systemId, policyId)}/snapshots/${encodeURIComponent(snapshotId)}:start`,
      { method: 'POST', body: { expectedSnapshotVersion } },
    )
  },
  tasks(systemId: string, policyId: string) {
    return apiRequest<IdentitySyncTask[]>(`${policyRoot(systemId, policyId)}/tasks`)
  },
}
