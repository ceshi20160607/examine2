import { beforeEach, describe, expect, it, vi } from 'vitest'

import { runtimeApi } from '@/services/config'

describe('runtime batch trash API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('posts explicit record versions and creates a fresh idempotency key per command', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify({
      code: 'OK',
      message: '',
      data: {
        allApplied: true,
        items: [
          { recordId: '30', resultCode: 'APPLIED', newVersion: 4, status: 'TRASHED' },
        ],
      },
      requestId: 'request-1',
      traceId: 'trace-1',
      errors: [],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))
    vi.stubGlobal('fetch', fetchMock)

    const body = { items: [{ recordId: '30', expectedVersion: 3 }] }
    await runtimeApi.batchTrashRecords('10', 'work_order', body)
    await runtimeApi.batchTrashRecords('10', 'work_order', body)

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const [firstPath, firstOptions] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    const [secondPath, secondOptions] = fetchMock.mock.calls[1] as unknown as [string, RequestInit]
    const firstHeaders = firstOptions.headers as Headers
    const secondHeaders = secondOptions.headers as Headers

    expect(firstPath).toBe('/api/v1/systems/10/runtime/modules/work_order/records:batch-trash')
    expect(secondPath).toBe(firstPath)
    expect(firstOptions.method).toBe('POST')
    expect(firstHeaders.get('X-CSRF-Token')).toBe('csrf-token')
    expect(firstHeaders.get('Idempotency-Key')).toBeTruthy()
    expect(secondHeaders.get('Idempotency-Key')).toBeTruthy()
    expect(secondHeaders.get('Idempotency-Key')).not.toBe(firstHeaders.get('Idempotency-Key'))
    expect(JSON.parse(String(firstOptions.body))).toEqual(body)
  })
})
