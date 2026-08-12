import { beforeEach, describe, expect, it, vi } from 'vitest'

import { platformSettingsApi } from '@/services/platformSettings'

function ok(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({ code: 'OK', message: '', data,
    requestId: 'r', traceId: 't', errors: [] }), { status: 200 }))
}

describe('platform global settings API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('uses the permissioned singleton read and versioned update endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ version: '1' }))
    vi.stubGlobal('fetch', fetchMock)
    const input = {
      profile: { name: 'Examine', description: '' },
      storage: { defaultMode: 'LOCAL' as const, maximumUploadBytes: 20_971_520, retentionDays: 365 },
      security: { sessionIdleMinutes: 30, passwordMinimumLength: 12, requireMfaForAdmins: true },
      quota: { defaultMemberLimit: 1000, defaultModuleLimit: 200, defaultStorageBytes: 107_374_182_400 },
      backup: { enabled: true, retentionDays: 30, intervalHours: 24 },
      release: { maintenanceMode: false, channel: 'STABLE' as const, approvalRequired: true },
      expectedVersion: '0',
    }
    await platformSettingsApi.get()
    await platformSettingsApi.update(input)

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/platform/admin/global-settings', '/api/v1/platform/admin/global-settings',
    ])
    expect((fetchMock.mock.calls[1]![1] as RequestInit).method).toBe('PUT')
    expect(JSON.parse(String((fetchMock.mock.calls[1]![1] as RequestInit).body))).toEqual(input)
  })
})
