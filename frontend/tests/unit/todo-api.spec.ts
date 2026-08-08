import { beforeEach, describe, expect, it, vi } from 'vitest'

import { todoApi } from '@/services/todo'

function ok(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

describe('todo API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('maps page, counts and detail under the tenant-scoped system root', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      items: [], page: 2, size: 50, total: 0,
    }))
    vi.stubGlobal('fetch', fetchMock)

    await todoApi.list('9007199254740991', {
      category: 'APPROVAL',
      state: 'CLOSED',
      time: 'OVERDUE',
      page: 2,
      size: 50,
    })
    await todoApi.counts('9007199254740991')
    await todoApi.detail('9007199254740991', 'todo/id')

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/9007199254740991/todos'
        + '?category=APPROVAL&state=CLOSED&time=OVERDUE&page=2&size=50',
      '/api/v1/systems/9007199254740991/todos/counts',
      '/api/v1/systems/9007199254740991/todos/todo%2Fid',
    ])
  })

  it('uses frozen default filters and idempotent refresh/action endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      status: 'SUCCESS',
      replayed: false,
      todo: null,
      sourceResult: { code: 'COMPLETED', message: '', sourceVersion: 2 },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await todoApi.list('10')
    await todoApi.refresh('10')
    await todoApi.action('10', '33', {
      action: 'REJECT',
      comment: null,
      reason: 'Missing evidence',
      version: 7,
    })
    await todoApi.action('10', 'event/44', {
      action: 'MARK_READ',
      version: 9,
    })

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/todos?category=ALL&state=OPEN&time=ALL&page=1&size=20',
      '/api/v1/systems/10/todos:refresh',
      '/api/v1/systems/10/todos/33:action',
      '/api/v1/systems/10/todos/event%2F44:action',
    ])
    expect((fetchMock.mock.calls[1]![1] as RequestInit).method).toBe('POST')
    expect((fetchMock.mock.calls[2]![1] as RequestInit).method).toBe('POST')
    expect(JSON.parse(String((fetchMock.mock.calls[2]![1] as RequestInit).body))).toEqual({
      action: 'REJECT',
      comment: null,
      reason: 'Missing evidence',
      version: 7,
    })
    expect(JSON.parse(String((fetchMock.mock.calls[3]![1] as RequestInit).body))).toEqual({
      action: 'MARK_READ',
      version: 9,
    })
    for (const call of fetchMock.mock.calls.slice(1)) {
      const headers = (call[1] as RequestInit).headers as Headers
      expect(headers.get('Idempotency-Key')).toBeTruthy()
      expect(headers.get('X-CSRF-Token')).toBe('csrf-token')
    }
  })
})
