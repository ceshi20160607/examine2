import { beforeEach, describe, expect, it, vi } from 'vitest'

import { favoriteApi } from '@/services/favorites'

const envelope = (data: unknown) => JSON.stringify({
  code: 'OK',
  message: '',
  data,
  requestId: 'request-1',
  traceId: 'trace-1',
  errors: [],
})

describe('runtime favorites API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('uses the frozen list, create and CAS delete contracts', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(envelope({ items: [], page: 2, size: 20, total: 21 }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(envelope({
        favoriteId: '91',
        version: 0,
        type: 'RECORD',
        moduleCode: 'work_order',
        recordId: '30',
        displayLabel: 'R-30 · Check power',
        status: 'ACTIVE',
        updatedAt: '2026-07-27T10:00:00',
      }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(envelope({ favoriteId: '91', version: 1, deleted: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))
    vi.stubGlobal('fetch', fetchMock)

    await favoriteApi.list('10', 2, 20)
    await favoriteApi.create('10', { type: 'RECORD', moduleCode: 'work_order', recordId: '30' })
    await favoriteApi.remove('10', '91', 0)

    expect(fetchMock.mock.calls[0]![0]).toBe('/api/v1/systems/10/runtime/favorites?page=2&size=20')
    expect(fetchMock.mock.calls[0]![1]?.method).toBeUndefined()

    const createOptions = fetchMock.mock.calls[1]![1] as RequestInit
    const createHeaders = createOptions.headers as Headers
    expect(fetchMock.mock.calls[1]![0]).toBe('/api/v1/systems/10/runtime/favorites')
    expect(createOptions.method).toBe('POST')
    expect(createHeaders.get('X-CSRF-Token')).toBe('csrf-token')
    expect(createHeaders.get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(createOptions.body))).toEqual({
      type: 'RECORD',
      moduleCode: 'work_order',
      recordId: '30',
    })

    const deleteOptions = fetchMock.mock.calls[2]![1] as RequestInit
    const deleteHeaders = deleteOptions.headers as Headers
    expect(fetchMock.mock.calls[2]![0]).toBe('/api/v1/systems/10/runtime/favorites/91')
    expect(deleteOptions.method).toBe('DELETE')
    expect(deleteHeaders.get('X-CSRF-Token')).toBe('csrf-token')
    expect(deleteHeaders.get('Idempotency-Key')).toBeTruthy()
    expect(deleteHeaders.get('Idempotency-Key')).not.toBe(createHeaders.get('Idempotency-Key'))
    expect(JSON.parse(String(deleteOptions.body))).toEqual({ expectedVersion: 0 })
  })
})
