import { beforeEach, describe, expect, it, vi } from 'vitest'

import { platformTodoApi } from '@/services/platformTodo'

function envelope(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

describe('platform Todo API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('maps list, counts, detail and the versioned idempotent action exactly', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => envelope({ items: [] }))
    vi.stubGlobal('fetch', fetchMock)

    await platformTodoApi.list({ state: 'CLOSED', page: 3, size: 50 })
    await platformTodoApi.counts()
    await platformTodoApi.detail('todo/9')
    await platformTodoApi.action('todo/9', 'REOPEN', 0)

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/platform/todos?state=CLOSED&type=ALL&page=3&size=50',
      '/api/v1/platform/todos/counts',
      '/api/v1/platform/todos/todo%2F9',
      '/api/v1/platform/todos/todo%2F9:action',
    ])
    const actionRequest = fetchMock.mock.calls[3]![1] as RequestInit
    expect(actionRequest.method).toBe('POST')
    expect(JSON.parse(String(actionRequest.body))).toEqual({ action: 'REOPEN', version: 0 })
    expect((actionRequest.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect((actionRequest.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
  })

  it('uses the frozen open first-page defaults', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => envelope({ items: [] }))
    vi.stubGlobal('fetch', fetchMock)
    await platformTodoApi.list()
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/platform/todos?state=OPEN&type=ALL&page=1&size=20')
  })
})
