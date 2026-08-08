import { beforeEach, describe, expect, it, vi } from 'vitest'

import { globalSearchApi } from '@/services/globalSearch'

const envelope = (data: unknown) => JSON.stringify({
  code: 'OK',
  message: '',
  data,
  requestId: 'request-1',
  traceId: 'trace-1',
  errors: [],
})

describe('runtime global search API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('maps the frozen trimmed query and page without mutation headers', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(envelope({
      items: [],
      page: 2,
      size: 25,
      total: 0,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await globalSearchApi.search('system / 10', {
      q: '  pump station  ',
      page: 2,
      size: 25,
    })

    expect(fetchMock.mock.calls[0]![0]).toBe(
      '/api/v1/systems/system%20%2F%2010/runtime/global-search?q=pump+station&page=2&size=25',
    )
    const options = fetchMock.mock.calls[0]![1] as RequestInit
    const headers = options.headers as Headers
    expect(options.method).toBeUndefined()
    expect(headers.get('X-CSRF-Token')).toBeNull()
    expect(headers.get('Idempotency-Key')).toBeNull()
  })
})
