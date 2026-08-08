import { beforeEach, describe, expect, it, vi } from 'vitest'

import { analyticsApi } from '@/services/analytics'

function ok(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

describe('operations analytics API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('maps the inclusive/exclusive UTC range under the system analytics root', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({}))
    vi.stubGlobal('fetch', fetchMock)

    await analyticsApi.operations('9007199254740991', '2026-07-26', '2026-08-02')

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/v1/systems/9007199254740991/analytics/operations'
      + '?from=2026-07-26&to=2026-08-02',
    )
    expect((fetchMock.mock.calls[0]?.[1] as RequestInit).method).toBeUndefined()
  })
})
