import { apiRequest } from '@/services/api'
import type {
  AccessRequest,
  DataScope,
  Department,
  LifecycleCommandInput,
  PageQuery,
  PageResult,
  PermissionDefinition,
  PermissionEvaluation,
  PlatformAccount,
  PlatformSystem,
  Role,
  RoleDraftInput,
  SystemMember,
  SystemSettings,
  SystemDeletePreview,
  SystemDeleteResult,
  SystemTombstone,
  Tenant,
  TenantDomain,
  TenantLifecycleOperation,
  TenantLifecyclePlan,
  TenantQuota,
} from '@/types/admin'

function withQuery(path: string, query: PageQuery | Record<string, string | number | undefined>) {
  const search = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value))
  })
  const suffix = search.toString()
  return suffix ? `${path}?${suffix}` : path
}

function idempotencyKey() {
  return crypto.randomUUID()
}

export const platformAdminApi = {
  listSystems(query: PageQuery = {}) {
    return apiRequest<PageResult<PlatformSystem>>(withQuery('/api/v1/platform/admin/systems', query))
  },
  createSystem(input: Pick<PlatformSystem, 'code' | 'name' | 'description' | 'tenantMode'>) {
    return apiRequest<PlatformSystem>('/api/v1/platform/admin/systems', {
      method: 'POST',
      body: input,
      idempotencyKey: idempotencyKey(),
    })
  },
  updateSystem(systemId: string, input: Pick<PlatformSystem, 'name' | 'description' | 'version'>) {
    return apiRequest<PlatformSystem>(`/api/v1/platform/admin/systems/${systemId}`, {
      method: 'PUT',
      body: input,
    })
  },
  commandSystem(systemId: string, command: 'activate' | 'disable' | 'archive' | 'restore' | 'retry-initialization', input: LifecycleCommandInput) {
    return apiRequest<PlatformSystem>(`/api/v1/platform/admin/systems/${systemId}:${command}`, {
      method: 'POST',
      body: input,
      idempotencyKey: idempotencyKey(),
    })
  },
  previewSystemDeletion(systemId: string) {
    return apiRequest<SystemDeletePreview>(`/api/v1/platform/admin/systems/${systemId}/deletion:preview`, { method: 'POST' })
  },
  confirmSystemDeletion(systemId: string, input: {
    previewId: string; confirmationToken: string; expectedVersion: string; reason: string; impactConfirmed: boolean
  }) {
    return apiRequest<SystemDeleteResult>(`/api/v1/platform/admin/systems/${systemId}/deletion:confirm`, {
      method: 'POST', body: input, idempotencyKey: idempotencyKey(),
    })
  },
  listSystemTombstones(query: PageQuery = {}) {
    return apiRequest<PageResult<SystemTombstone>>(withQuery('/api/v1/platform/admin/system-tombstones', query))
  },
  restoreSystemTombstone(systemId: string, input: { reason: string; version: string }) {
    return apiRequest<PlatformSystem>(`/api/v1/platform/admin/systems/${encodeURIComponent(systemId)}/tombstone:restore`, {
      method: 'POST', body: input, idempotencyKey: idempotencyKey(),
    })
  },
  listAccounts(query: PageQuery = {}) {
    return apiRequest<PageResult<PlatformAccount>>(withQuery('/api/v1/platform/admin/accounts', query))
  },
  updateAccount(accountId: string, input: Partial<PlatformAccount> & { version: string }) {
    return apiRequest<PlatformAccount>(`/api/v1/platform/admin/accounts/${accountId}`, {
      method: 'PUT',
      body: input,
    })
  },
  listDepartments(query: PageQuery = {}) {
    return apiRequest<PageResult<Department>>(withQuery('/api/v1/platform/admin/departments', query))
  },
  createDepartment(input: Pick<Department, 'name' | 'code' | 'parentId'>) {
    return apiRequest<Department>('/api/v1/platform/admin/departments', {
      method: 'POST',
      body: input,
      idempotencyKey: idempotencyKey(),
    })
  },
  updateDepartment(departmentId: string, input: Pick<Department, 'name' | 'parentId' | 'status' | 'version'>) {
    return apiRequest<Department>(`/api/v1/platform/admin/departments/${departmentId}`, {
      method: 'PUT',
      body: input,
    })
  },
  listRoles(query: PageQuery = {}) {
    return apiRequest<PageResult<Role>>(withQuery('/api/v1/platform/admin/roles', query))
  },
  createRole(input: Pick<Role, 'code' | 'name' | 'description'>) {
    return apiRequest<Role>('/api/v1/platform/admin/roles', {
      method: 'POST',
      body: input,
      idempotencyKey: idempotencyKey(),
    })
  },
  listPermissions(query: PageQuery = {}) {
    return apiRequest<PageResult<PermissionDefinition>>(withQuery('/api/v1/platform/admin/permissions', query))
  },
  getPermissionEvaluation(accountId: string) {
    return apiRequest<PermissionEvaluation>(withQuery('/api/v1/platform/admin/permission-evaluations', { accountId }))
  },
  saveRoleDraft(roleId: string, input: RoleDraftInput) {
    return apiRequest<Role>(`/api/v1/platform/admin/roles/${roleId}/draft`, { method: 'PUT', body: input })
  },
  checkRoleDraft(roleId: string, version: string) {
    return apiRequest<Role>(`/api/v1/platform/admin/roles/${roleId}/draft:check`, {
      method: 'POST',
      body: { version },
      idempotencyKey: idempotencyKey(),
    })
  },
  publishRoleDraft(roleId: string, version: string) {
    return apiRequest<Role>(`/api/v1/platform/admin/roles/${roleId}/draft:publish`, {
      method: 'POST',
      body: { version },
      idempotencyKey: idempotencyKey(),
    })
  },
}

function systemAdminBase(systemId: string) {
  return `/api/v1/systems/${systemId}/admin`
}

export const systemAdminApi = {
  getSettings(systemId: string) {
    return apiRequest<SystemSettings>(`${systemAdminBase(systemId)}/settings`)
  },
  updateSettings(systemId: string, input: Pick<SystemSettings, 'name' | 'description' | 'tenantMode' | 'version'>) {
    return apiRequest<SystemSettings>(`${systemAdminBase(systemId)}/settings`, { method: 'PUT', body: input })
  },
  listTenants(systemId: string, query: PageQuery = {}) {
    return apiRequest<PageResult<Tenant>>(withQuery(`${systemAdminBase(systemId)}/tenants`, query))
  },
  createTenant(systemId: string, input: Pick<Tenant, 'code' | 'name'>) {
    return apiRequest<Tenant>(`${systemAdminBase(systemId)}/tenants`, {
      method: 'POST',
      body: input,
      idempotencyKey: idempotencyKey(),
    })
  },
  updateTenant(systemId: string, tenantId: string, input: Pick<Tenant, 'name' | 'version'>) {
    return apiRequest<Tenant>(`${systemAdminBase(systemId)}/tenants/${tenantId}`, { method: 'PUT', body: input })
  },
  commandTenant(systemId: string, tenantId: string, command: 'activate' | 'disable' | 'archive' | 'restore', input: LifecycleCommandInput) {
    return apiRequest<Tenant>(`${systemAdminBase(systemId)}/tenants/${tenantId}:${command}`, {
      method: 'POST',
      body: input,
      idempotencyKey: idempotencyKey(),
    })
  },
  listTenantDomains(systemId: string, tenantId: string) {
    return apiRequest<TenantDomain[]>(`${systemAdminBase(systemId)}/tenants/${tenantId}/domains`)
  },
  addTenantDomain(systemId: string, tenantId: string, domainName: string) {
    return apiRequest<TenantDomain>(`${systemAdminBase(systemId)}/tenants/${tenantId}/domains`, {
      method: 'POST', body: { domainName }, idempotencyKey: idempotencyKey(),
    })
  },
  verifyTenantDomain(systemId: string, tenantId: string, domainId: string, verificationToken: string, expectedVersion: string) {
    return apiRequest<TenantDomain>(`${systemAdminBase(systemId)}/tenants/${tenantId}/domains/${domainId}:verify`, {
      method: 'POST', body: { verificationToken, expectedVersion },
    })
  },
  makePrimaryTenantDomain(systemId: string, tenantId: string, domainId: string, expectedVersion: string) {
    return apiRequest<TenantDomain>(`${systemAdminBase(systemId)}/tenants/${tenantId}/domains/${domainId}:primary`, {
      method: 'PUT', body: { expectedVersion, reason: '设为主域名', impactConfirmed: true },
    })
  },
  disableTenantDomain(systemId: string, tenantId: string, domainId: string, expectedVersion: string) {
    return apiRequest<TenantDomain>(`${systemAdminBase(systemId)}/tenants/${tenantId}/domains/${domainId}:disable`, {
      method: 'POST', body: { expectedVersion, reason: '停用租户域名', impactConfirmed: true }, idempotencyKey: idempotencyKey(),
    })
  },
  listTenantQuotas(systemId: string, tenantId: string) {
    return apiRequest<TenantQuota[]>(`${systemAdminBase(systemId)}/tenants/${tenantId}/quotas`)
  },
  setTenantQuota(systemId: string, tenantId: string, input: { quotaKey: string; softLimit: number | null; hardLimit: number; expectedVersion: string | null }) {
    return apiRequest<TenantQuota>(`${systemAdminBase(systemId)}/tenants/${tenantId}/quotas`, {
      method: 'PUT', body: input, idempotencyKey: idempotencyKey(),
    })
  },
  backupTenant(systemId: string, tenantId: string, reason: string, expectedTenantVersion: string) {
    return apiRequest<TenantLifecycleOperation>(`${systemAdminBase(systemId)}/tenants/${tenantId}/lifecycle:backup`, {
      method: 'POST', body: { reason, expectedTenantVersion }, idempotencyKey: idempotencyKey(),
    })
  },
  previewTenantRecovery(systemId: string, tenantId: string, input: { backupOperationId: string; expectedTenantVersion: string }) {
    return apiRequest<TenantLifecyclePlan>(`${systemAdminBase(systemId)}/tenants/${tenantId}/lifecycle:recovery-preview`, {
      method: 'POST', body: input,
    })
  },
  recoverTenant(systemId: string, tenantId: string, input: { planOperationId: string; confirmationToken: string; reason: string; expectedTenantVersion: string; impactConfirmed: boolean }) {
    return apiRequest<TenantLifecycleOperation>(`${systemAdminBase(systemId)}/tenants/${tenantId}/lifecycle:recover`, {
      method: 'POST', body: input, idempotencyKey: idempotencyKey(),
    })
  },
  previewTenantMigration(systemId: string, tenantId: string, input: { targetTenantId: string; expectedSourceVersion: string; expectedTargetVersion: string }) {
    return apiRequest<TenantLifecyclePlan>(`${systemAdminBase(systemId)}/tenants/${tenantId}/lifecycle:migration-preview`, {
      method: 'POST', body: input,
    })
  },
  migrateTenant(systemId: string, tenantId: string, input: { planOperationId: string; confirmationToken: string; targetTenantId: string; reason: string; expectedSourceVersion: string; expectedTargetVersion: string; impactConfirmed: boolean }) {
    return apiRequest<TenantLifecycleOperation>(`${systemAdminBase(systemId)}/tenants/${tenantId}/lifecycle:migrate`, {
      method: 'POST', body: input, idempotencyKey: idempotencyKey(),
    })
  },
  listDepartments(systemId: string, query: PageQuery = {}) {
    return apiRequest<PageResult<Department>>(withQuery(`${systemAdminBase(systemId)}/departments`, query))
  },
  createDepartment(systemId: string, input: Pick<Department, 'name' | 'code' | 'parentId'>) {
    return apiRequest<Department>(`${systemAdminBase(systemId)}/departments`, {
      method: 'POST',
      body: input,
      idempotencyKey: idempotencyKey(),
    })
  },
  updateDepartment(systemId: string, departmentId: string, input: Pick<Department, 'name' | 'parentId' | 'status' | 'version'>) {
    return apiRequest<Department>(`${systemAdminBase(systemId)}/departments/${departmentId}`, { method: 'PUT', body: input })
  },
  updateDepartmentLeader(systemId: string, departmentId: string, input: {
    leaderMemberId: string | null
    version: string
  }) {
    return apiRequest<Department>(`${systemAdminBase(systemId)}/departments/${departmentId}/leader`, {
      method: 'PUT',
      body: input,
    })
  },
  listMembers(systemId: string, query: PageQuery = {}) {
    return apiRequest<PageResult<SystemMember>>(withQuery(`${systemAdminBase(systemId)}/members`, query))
  },
  updateMember(systemId: string, memberId: string, input: Partial<SystemMember> & { version: string }) {
    return apiRequest<SystemMember>(`${systemAdminBase(systemId)}/members/${memberId}`, { method: 'PUT', body: input })
  },
  updateMemberManager(systemId: string, memberId: string, input: {
    managerMemberId: string | null
    version: string
  }) {
    return apiRequest<SystemMember>(`${systemAdminBase(systemId)}/members/${memberId}/manager`, {
      method: 'PUT',
      body: input,
    })
  },
  listRoles(systemId: string, query: PageQuery = {}) {
    return apiRequest<PageResult<Role>>(withQuery(`${systemAdminBase(systemId)}/roles`, query))
  },
  createRole(systemId: string, input: Pick<Role, 'code' | 'name' | 'description'>) {
    return apiRequest<Role>(`${systemAdminBase(systemId)}/roles`, {
      method: 'POST',
      body: input,
      idempotencyKey: idempotencyKey(),
    })
  },
  listPermissions(systemId: string, query: PageQuery = {}) {
    return apiRequest<PageResult<PermissionDefinition>>(withQuery(`${systemAdminBase(systemId)}/permissions`, query))
  },
  listDataScopes(systemId: string, query: PageQuery = {}) {
    return apiRequest<PageResult<DataScope>>(withQuery(`${systemAdminBase(systemId)}/data-scopes`, query))
  },
  getPermissionEvaluation(systemId: string, memberId: string, tenantId: string) {
    return apiRequest<PermissionEvaluation>(withQuery(`${systemAdminBase(systemId)}/permission-evaluations`, { memberId, tenantId }))
  },
  saveRoleDraft(systemId: string, roleId: string, input: RoleDraftInput) {
    return apiRequest<Role>(`${systemAdminBase(systemId)}/roles/${roleId}/draft`, { method: 'PUT', body: input })
  },
  checkRoleDraft(systemId: string, roleId: string, version: string) {
    return apiRequest<Role>(`${systemAdminBase(systemId)}/roles/${roleId}/draft:check`, {
      method: 'POST',
      body: { version },
      idempotencyKey: idempotencyKey(),
    })
  },
  publishRoleDraft(systemId: string, roleId: string, version: string) {
    return apiRequest<Role>(`${systemAdminBase(systemId)}/roles/${roleId}/draft:publish`, {
      method: 'POST',
      body: { version },
      idempotencyKey: idempotencyKey(),
    })
  },
  listAccessRequests(systemId: string, query: PageQuery = {}) {
    return apiRequest<PageResult<AccessRequest>>(withQuery(`${systemAdminBase(systemId)}/access-requests`, query))
  },
  reviewAccessRequest(systemId: string, requestId: string, decision: 'approve' | 'reject', input: { reason: string; version: string; tenantIds?: string[]; roleIds?: string[] }) {
    return apiRequest<AccessRequest>(`${systemAdminBase(systemId)}/access-requests/${requestId}:${decision}`, {
      method: 'POST',
      body: input,
      idempotencyKey: idempotencyKey(),
    })
  },
}
