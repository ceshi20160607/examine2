import { beforeEach, describe, expect, it, vi } from 'vitest'

import { runtimeApi } from '@/services/config'

describe('runtime batch archive API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('posts the explicit record versions with one idempotency key', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify({
      code: 'OK',
      message: '',
      data: {
        allApplied: true,
        items: [
          { recordId: '30', resultCode: 'APPLIED', newVersion: 4, status: 'ARCHIVED' },
          { recordId: '40', resultCode: 'APPLIED', newVersion: 8, status: 'ARCHIVED' },
        ],
      },
      requestId: 'request-1',
      traceId: 'trace-1',
      errors: [],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))
    vi.stubGlobal('fetch', fetchMock)

    await runtimeApi.batchArchiveRecords('10', 'work_order', {
      items: [
        { recordId: '30', expectedVersion: 3 },
        { recordId: '40', expectedVersion: 7 },
      ],
    })

    const [path, options] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    const headers = options.headers as Headers
    expect(path).toBe('/api/v1/systems/10/runtime/modules/work_order/records:batch-archive')
    expect(options.method).toBe('POST')
    expect(headers.get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(options.body))).toEqual({
      items: [
        { recordId: '30', expectedVersion: 3 },
        { recordId: '40', expectedVersion: 7 },
      ],
    })
  })
})
