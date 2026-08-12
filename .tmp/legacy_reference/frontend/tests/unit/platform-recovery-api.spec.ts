import { beforeEach, describe, expect, it, vi } from 'vitest'

import { platformAdminApi } from '@/services/admin'

function ok(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({ code: 'OK', message: '', data,
    requestId: 'r', traceId: 't', errors: [] }), { status: 200 }))
}

describe('platform lifecycle recovery API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('uses explicit retry and recoverable tombstone operations', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ items: [] }))
    vi.stubGlobal('fetch', fetchMock)
    await platformAdminApi.commandSystem('41', 'retry-initialization', {
      reason: 'retry after provider recovery', version: '2', impactConfirmed: true,
    })
    await platformAdminApi.listSystemTombstones({ page: 1, size: 20 })
    await platformAdminApi.restoreSystemTombstone('system / 41', { reason: 'operator recovery', version: '7' })

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/platform/admin/systems/41:retry-initialization',
      '/api/v1/platform/admin/system-tombstones?page=1&size=20',
      '/api/v1/platform/admin/systems/system%20%2F%2041/tombstone:restore',
    ])
    expect(fetchMock.mock.calls.map(call => (call[1] as RequestInit).method)).toEqual(['POST', undefined, 'POST'])
    expect(JSON.parse(String((fetchMock.mock.calls[2]![1] as RequestInit).body))).toEqual({
      reason: 'operator recovery', version: '7',
    })
  })
})
