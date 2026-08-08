import { beforeEach, describe, expect, it, vi } from 'vitest'

import { platformAdminApi, systemAdminApi } from '@/services/admin'

function envelope(data: unknown = {}) {
  return new Response(JSON.stringify({ code: 'OK', message: '', data, requestId: 'r1', traceId: 't1', errors: [] }), {
    status: 200, headers: { 'Content-Type': 'application/json' },
  })
}

describe('platform system and tenant lifecycle API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('performs preview-bound system deletion with an idempotency key', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope()))
    vi.stubGlobal('fetch', fetchMock)
    await platformAdminApi.previewSystemDeletion('system/1')
    await platformAdminApi.confirmSystemDeletion('system/1', {
      previewId: 'preview-1', confirmationToken: 'one-time', expectedVersion: '7',
      reason: 'retired', impactConfirmed: true,
    })
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/platform/admin/systems/system/1/deletion:preview',
      '/api/v1/platform/admin/systems/system/1/deletion:confirm',
    ])
    const confirm = fetchMock.mock.calls[1]![1] as RequestInit
    expect(JSON.parse(String(confirm.body))).toEqual({
      previewId: 'preview-1', confirmationToken: 'one-time', expectedVersion: '7',
      reason: 'retired', impactConfirmed: true,
    })
    expect((confirm.headers as Headers).get('Idempotency-Key')).toBeTruthy()
  })

  it('uses the tenant domain, quota, backup, recovery and migration control plane', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope([])))
    vi.stubGlobal('fetch', fetchMock)
    await systemAdminApi.listTenantDomains('10', '20')
    await systemAdminApi.addTenantDomain('10', '20', 'tenant.example.com')
    await systemAdminApi.verifyTenantDomain('10', '20', '30', 'token', '0')
    await systemAdminApi.makePrimaryTenantDomain('10', '20', '30', '1')
    await systemAdminApi.disableTenantDomain('10', '20', '30', '2')
    await systemAdminApi.listTenantQuotas('10', '20')
    await systemAdminApi.setTenantQuota('10', '20', { quotaKey: 'MEMBERS', softLimit: 90, hardLimit: 100, expectedVersion: null })
    await systemAdminApi.backupTenant('10', '20', 'before migration', '3')
    await systemAdminApi.previewTenantRecovery('10', '20', { backupOperationId: '40', expectedTenantVersion: '4' })
    await systemAdminApi.recoverTenant('10', '20', { planOperationId: '41', confirmationToken: 'recover-once', reason: 'rollback', expectedTenantVersion: '4', impactConfirmed: true })
    await systemAdminApi.previewTenantMigration('10', '20', { targetTenantId: '21', expectedSourceVersion: '5', expectedTargetVersion: '6' })
    await systemAdminApi.migrateTenant('10', '20', { planOperationId: '42', confirmationToken: 'migrate-once', targetTenantId: '21', reason: 'merge', expectedSourceVersion: '5', expectedTargetVersion: '6', impactConfirmed: true })

    const root = '/api/v1/systems/10/admin/tenants/20'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      `${root}/domains`, `${root}/domains`, `${root}/domains/30:verify`, `${root}/domains/30:primary`,
      `${root}/domains/30:disable`, `${root}/quotas`, `${root}/quotas`, `${root}/lifecycle:backup`,
      `${root}/lifecycle:recovery-preview`, `${root}/lifecycle:recover`,
      `${root}/lifecycle:migration-preview`, `${root}/lifecycle:migrate`,
    ])
    expect(JSON.parse(String((fetchMock.mock.calls[6]![1] as RequestInit).body))).toEqual({
      quotaKey: 'MEMBERS', softLimit: 90, hardLimit: 100, expectedVersion: null,
    })
    expect([1, 4, 6, 7, 9, 11].every(index =>
      ((fetchMock.mock.calls[index]![1] as RequestInit).headers as Headers).has('Idempotency-Key'))).toBe(true)
    expect(JSON.parse(String((fetchMock.mock.calls[9]![1] as RequestInit).body))).toEqual({
      planOperationId: '41', confirmationToken: 'recover-once', reason: 'rollback',
      expectedTenantVersion: '4', impactConfirmed: true,
    })
    expect(JSON.parse(String((fetchMock.mock.calls[11]![1] as RequestInit).body))).toEqual({
      planOperationId: '42', confirmationToken: 'migrate-once', targetTenantId: '21', reason: 'merge',
      expectedSourceVersion: '5', expectedTargetVersion: '6', impactConfirmed: true,
    })
  })
})
