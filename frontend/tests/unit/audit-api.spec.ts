import { beforeEach, describe, expect, it, vi } from 'vitest'

import { auditApi } from '@/services/audit'

function ok(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

describe('unified audit API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('encodes all platform log filters and paging', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      ok({ items: [], page: 3, size: 50, total: 0 }))
    vi.stubGlobal('fetch', fetchMock)

    await auditApi.platformLogs({
      requestId: 'request / 1', traceId: 'trace:1', actor: 'Alice Chen', object: 'module/a',
      result: 'FAILED', category: 'CONFIG', from: '2026-08-01T00:00:00Z',
      to: '2026-08-07T00:00:00Z', page: 3, size: 50,
    })

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/platform/admin/audit-logs?requestId=request+%2F+1&traceId=trace%3A1&actor=Alice+Chen&object=module%2Fa&result=FAILED&category=CONFIG&from=2026-08-01T00%3A00%3A00Z&to=2026-08-07T00%3A00%3A00Z&page=3&size=50')
  })

  it('uses exact encoded system log and health endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      ok({ items: [], page: 1, size: 20, total: 0 }))
    vi.stubGlobal('fetch', fetchMock)

    await auditApi.systemLogs('system / 7')
    await auditApi.systemHealth('system / 7')
    await auditApi.platformHealth()

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/system%20%2F%207/admin/audit-logs?page=1&size=20',
      '/api/v1/systems/system%20%2F%207/admin/audit-health',
      '/api/v1/platform/admin/audit-health',
    ])
  })
})
