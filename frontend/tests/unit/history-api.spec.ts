import { afterEach, describe, expect, it, vi } from 'vitest'

import { recordHistoryApi } from '@/services/history'

describe('recordHistoryApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('requests the scoped record history page', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'OK',
      message: '',
      data: { items: [], page: 2, size: 20, total: 0 },
      requestId: 'request-1',
      traceId: 'trace-1',
      errors: [],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await recordHistoryApi.list('10', 'work order', '30', 2, 20)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/systems/10/runtime/modules/work%20order/records/30/history?page=2&size=20',
      expect.objectContaining({ credentials: 'include' }),
    )
  })
})
