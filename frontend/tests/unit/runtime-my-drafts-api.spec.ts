import { beforeEach, describe, expect, it, vi } from 'vitest'

import { runtimeApi } from '@/services/config'

describe('runtime my drafts API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('posts only the frozen page, size and q request without an idempotency key', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify({
      code: 'OK',
      message: '',
      data: {
        rows: [],
        page: 2,
        size: 50,
        total: 0,
        invalidNodes: [],
      },
      requestId: 'request-1',
      traceId: 'trace-1',
      errors: [],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))
    vi.stubGlobal('fetch', fetchMock)

    await runtimeApi.myDrafts('10', 'work_order', {
      page: 2,
      size: 50,
      q: '现场草稿',
    })

    const [path, options] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    const headers = options.headers as Headers
    expect(path).toBe('/api/v1/systems/10/runtime/modules/work_order/records:my-drafts-query')
    expect(options.method).toBe('POST')
    expect(headers.get('Idempotency-Key')).toBeNull()
    expect(JSON.parse(String(options.body))).toEqual({
      page: 2,
      size: 50,
      q: '现场草稿',
    })
  })
})
