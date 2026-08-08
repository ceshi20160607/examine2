import { beforeEach, describe, expect, it, vi } from 'vitest'

import { recentRecordApi } from '@/services/recentRecords'

const recent = {
  recentId: '81',
  moduleCode: 'work_order',
  recordId: '30',
  displayLabel: 'R-30 · Check power',
  status: 'ACTIVE',
  accessCount: 4,
  lastAccessedAt: '2026-07-27T12:00:00',
}

const envelope = (data: unknown) => JSON.stringify({
  code: 'OK',
  message: '',
  data,
  requestId: 'request-1',
  traceId: 'trace-1',
  errors: [],
})

describe('runtime recent records API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('lists a frozen page and touches visible detail without an idempotency key', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(envelope({
        items: [recent],
        page: 2,
        size: 20,
        total: 21,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(envelope(recent), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))
    vi.stubGlobal('fetch', fetchMock)

    await recentRecordApi.list('10', 2, 20)
    await recentRecordApi.touch('10', { moduleCode: 'work_order', recordId: '30' })

    expect(fetchMock.mock.calls[0]![0]).toBe('/api/v1/systems/10/runtime/recent-records?page=2&size=20')
    expect(fetchMock.mock.calls[0]![1]?.method).toBeUndefined()

    const touchOptions = fetchMock.mock.calls[1]![1] as RequestInit
    const headers = touchOptions.headers as Headers
    expect(fetchMock.mock.calls[1]![0]).toBe('/api/v1/systems/10/runtime/recent-records:touch')
    expect(touchOptions.method).toBe('POST')
    expect(headers.get('X-CSRF-Token')).toBe('csrf-token')
    expect(headers.get('Idempotency-Key')).toBeNull()
    expect(JSON.parse(String(touchOptions.body))).toEqual({
      moduleCode: 'work_order',
      recordId: '30',
    })
  })
})
