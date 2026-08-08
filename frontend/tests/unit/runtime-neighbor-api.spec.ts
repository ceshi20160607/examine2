import { beforeEach, describe, expect, it, vi } from 'vitest'

import { runtimeApi } from '@/services/config'

describe('runtime neighbor API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('posts only the frozen query token, direction and current sort anchor', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify({
      code: 'OK',
      message: '',
      data: { neighbor: null, boundary: true, querySnapshotToken: 'next-token', correlationId: 'trace-1' },
      requestId: 'request-1',
      traceId: 'trace-1',
      errors: [],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))
    vi.stubGlobal('fetch', fetchMock)

    await runtimeApi.recordNeighbor('10', 'work_order', '30', {
      direction: 'NEXT',
      querySnapshotToken: 'snapshot-token',
      sortAnchor: { values: ['2026-07-27T01:00:00Z'], recordId: '30' },
    })

    const [path, options] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/api/v1/systems/10/runtime/modules/work_order/records/30:neighbors')
    expect(options.method).toBe('POST')
    expect(JSON.parse(String(options.body))).toEqual({
      direction: 'NEXT',
      querySnapshotToken: 'snapshot-token',
      sortAnchor: { values: ['2026-07-27T01:00:00Z'], recordId: '30' },
    })
  })
})
