import { beforeEach, describe, expect, it, vi } from 'vitest'

import { configRecoveryApi } from '@/services/configRecovery'

function envelope() {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data: {
      owner: 'REPORT', resourceId: '50', sourceVersionNumber: 2,
      draftVersion: '4', activeVersionReference: '3', state: 'DRAFT_RESTORED',
    }, requestId: 'r1', traceId: 't1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

describe('configuration recovery API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('uses one audited draft-only namespace for every configuration owner', async () => {
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>(
      (_input, _init) => envelope(),
    )
    vi.stubGlobal('fetch', fetchMock)
    const input = { expectedVersion: 3, reason: 'restore for review' }

    await configRecoveryApi.moduleConfig('10', '101', 1, input)
    await configRecoveryApi.dataSource('10', '20/1', 2, input)
    await configRecoveryApi.dashboard('10', '30', 3, input)
    await configRecoveryApi.kpi('10', '40', 4, input)
    await configRecoveryApi.report('10', '50', 5, input)

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/admin/config-recovery/module-config/versions/101/1:restore',
      '/api/v1/systems/10/admin/config-recovery/data-sources/20%2F1/versions/2:restore',
      '/api/v1/systems/10/admin/config-recovery/dashboards/30/versions/3:restore',
      '/api/v1/systems/10/admin/config-recovery/kpis/40/versions/4:restore',
      '/api/v1/systems/10/admin/config-recovery/reports/50/versions/5:restore',
    ])
    for (const call of fetchMock.mock.calls) {
      const options = call[1] as RequestInit
      expect(options.method).toBe('POST')
      expect((options.headers as Headers).get('Idempotency-Key')).toBeTruthy()
      expect((options.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
      expect(JSON.parse(String(options.body))).toEqual({
        expectedVersion: '3', reason: 'restore for review',
      })
    }
  })
})
