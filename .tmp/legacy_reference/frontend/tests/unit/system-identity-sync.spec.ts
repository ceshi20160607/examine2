import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { systemIdentitySyncApi } from '@/services/systemIdentitySync'
import type { IdentitySyncPolicy, IdentitySyncSnapshot, IdentitySyncTask } from '@/types/systemIdentitySync'
import SystemIdentitySyncView from '@/views/system/admin/SystemIdentitySyncView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
vi.mock('vue-router', async importOriginal => {
  const original = await importOriginal<typeof import('vue-router')>()
  return { ...original, useRoute: () => route }
})
vi.mock('@/stores/session', () => ({ useSessionStore: () => ({
  context: { tenantId: '20' }, tenants: [{ id: '20', name: '主租户' }],
}) }))
vi.mock('@/services/systemIdentitySync', () => ({ systemIdentitySyncApi: {
  providers: vi.fn(), policies: vi.fn(), savePolicy: vi.fn(), preflight: vi.fn(),
  snapshots: vi.fn(), confirm: vi.fn(), start: vi.fn(), tasks: vi.fn(),
} }))

const policy: IdentitySyncPolicy = {
  id: '31', systemId: '10', tenantId: '20', providerId: '41', providerCode: 'corp-oidc',
  providerName: '集团 OIDC', allowedDomains: ['example.com'], jitSystemMember: true,
  unmatchedAction: 'REQUIRE_REVIEW', scheduleEnabled: true, scheduleIntervalMinutes: 60,
  nextSyncAt: '2026-08-07T10:00:00Z', status: 'ACTIVE', lastConfirmedSnapshotId: null,
  lastSyncAt: null, version: 2,
}
const draft: IdentitySyncSnapshot = {
  id: '51', policyId: '31', sourceVersion: 'directory-v3', status: 'DRAFT',
  departmentCount: 1, employeeCount: 2, matchedCount: 1, createCount: 1, unmatchedCount: 1,
  confirmedAt: null, confirmedBy: null, appliedAt: null, version: 0,
  items: [
    { id: '61', kind: 'DEPARTMENT', externalId: 'OPS', displayName: '运营中心', parentExternalId: null,
      email: null, departmentExternalId: null, targetDepartmentId: null, targetAccountId: null,
      targetMemberId: null, proposedAction: 'CREATE', issueCode: null, attributes: {} },
    { id: '62', kind: 'EMPLOYEE', externalId: 'blocked', displayName: '未匹配员工', parentExternalId: null,
      email: 'blocked@invalid.test', departmentExternalId: 'OPS', targetDepartmentId: null,
      targetAccountId: null, targetMemberId: null, proposedAction: 'UNMATCHED',
      issueCode: 'EMPLOYEE_DOMAIN_DENIED', attributes: {} },
  ],
}
const confirmed: IdentitySyncSnapshot = { ...draft, status: 'CONFIRMED', confirmedAt: '2026-08-07T09:00:00Z', confirmedBy: '7', version: 1 }
const task: IdentitySyncTask = {
  id: '71', snapshotId: '51', trigger: 'MANUAL', status: 'QUEUED', progressPercent: 0,
  attemptCount: 0, maxAttempts: 3, lastError: null, result: {}, availableAt: '2026-08-07T09:01:00Z',
  startedAt: null, finishedAt: null, createdAt: '2026-08-07T09:01:00Z', failures: [],
}

const stubs = {
  AdminPageHeader: { props: ['title', 'description'], template: '<header><h1>{{ title }}</h1><p>{{ description }}</p><slot name="actions" /></header>' },
  'a-alert': { props: ['message'], template: '<div>{{ message }}</div>' },
  'a-button': { inheritAttrs: false, props: ['disabled', 'loading'], emits: ['click'], template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
  'a-tabs': { template: '<div><slot /></div>' },
  'a-tab-pane': { props: ['tab', 'disabled'], template: '<section v-if="!disabled"><h2>{{ tab }}</h2><slot /></section>' },
  'a-select': true, 'a-input': true, 'a-input-number': true, 'a-textarea': true, 'a-switch': true,
  'a-tag': { template: '<span><slot /></span>' },
  'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
  'a-table': { template: '<div class="table-stub" />' },
  'a-checkbox': { props: ['checked'], emits: ['update:checked'], template: '<label><input class="approve-checkbox" type="checkbox" :checked="checked" @change="$emit(\'update:checked\', $event.target.checked)" /><slot /></label>' },
}

describe('system identity synchronization', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(systemIdentitySyncApi.providers).mockResolvedValue([{
      id: '41', providerCode: 'corp-oidc', name: '集团 OIDC', protocol: 'OIDC',
      providerDomains: ['example.com'], jitAccount: true, mfaPolicy: 'REQUIRED', version: 4,
    }])
    vi.mocked(systemIdentitySyncApi.policies).mockResolvedValue([policy])
    vi.mocked(systemIdentitySyncApi.snapshots).mockResolvedValue([draft])
    vi.mocked(systemIdentitySyncApi.tasks).mockResolvedValue([])
    vi.mocked(systemIdentitySyncApi.confirm).mockResolvedValue(confirmed)
    vi.mocked(systemIdentitySyncApi.start).mockResolvedValue(task)
  })

  it('makes unmatched differences visible and requires explicit confirmation before queuing', async () => {
    const wrapper = mount(SystemIdentitySyncView, { global: { stubs } })
    await flushPromises()

    expect(systemIdentitySyncApi.providers).toHaveBeenCalledWith('10')
    expect(systemIdentitySyncApi.policies).toHaveBeenCalledWith('10', '20')
    const applicationRouter = (await import('@/router')).default
    const routeRecord = applicationRouter.getRoutes().find(item => item.name === 'system-admin-identity-sync')
    expect(routeRecord?.path).toBe('/systems/:systemId/admin/identity-sync')
    expect(routeRecord?.meta.requiredPermissions).toEqual(['system.organization.manage'])
    expect(wrapper.text()).toContain('当前未匹配1')
    expect(wrapper.get('.identity-confirm').attributes('disabled')).toBeDefined()

    vi.mocked(systemIdentitySyncApi.snapshots).mockResolvedValue([confirmed])
    await wrapper.get('.approve-checkbox').setValue(true)
    await wrapper.get('.identity-confirm').trigger('click')
    await flushPromises()
    expect(systemIdentitySyncApi.confirm).toHaveBeenCalledWith('10', '31', '51', 2, 0, true)

    await wrapper.get('.identity-start').trigger('click')
    await flushPromises()
    expect(systemIdentitySyncApi.start).toHaveBeenCalledWith('10', '31', '51', 1)
  })

  it('uses scoped and encoded policy, snapshot and task endpoints', async () => {
    vi.unstubAllGlobals()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
    const response = (data: unknown) => Promise.resolve(new Response(JSON.stringify({
      code: 'OK', message: '', data, requestId: 'r', traceId: 't', errors: [],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => response([]))
    vi.stubGlobal('fetch', fetchMock)

    // Import the real module after resetting the service mock for this isolated transport assertion.
    const real = await vi.importActual<typeof import('@/services/systemIdentitySync')>('@/services/systemIdentitySync')
    await real.systemIdentitySyncApi.providers('system/10')
    await real.systemIdentitySyncApi.policies('system/10', 'tenant/20')
    await real.systemIdentitySyncApi.tasks('system/10', 'policy/31')

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/system%2F10/admin/identity-sync/providers',
      '/api/v1/systems/system%2F10/admin/identity-sync/policies?tenantId=tenant%2F20',
      '/api/v1/systems/system%2F10/admin/identity-sync/policies/policy%2F31/tasks',
    ])
  })
})
