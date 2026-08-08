import { beforeEach, describe, expect, it, vi } from 'vitest'

import { platformTaskApi } from '@/services/platformTask'
import type { PlatformTask } from '@/types/platformTask'

const task: PlatformTask = {
  taskId: 'task/1',
  title: '核对平台授权',
  description: '确认当前账号权限',
  dueAt: '2099-08-06T02:00:00Z',
  priority: 'HIGH',
  status: 'OPEN',
  source: 'AGENT',
  createdAt: '2026-08-05T00:00:00Z',
  updatedAt: '2026-08-05T00:00:00Z',
  completedAt: null,
  cancelledAt: null,
  version: 0,
}

function envelope(data: unknown) {
  return new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('platform task API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('encodes the frozen status and page query without owner identity', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope({ items: [task], page: 1, size: 20, total: 1 })))
    vi.stubGlobal('fetch', fetchMock)

    await platformTaskApi.list()
    await platformTaskApi.list({ status: 'CANCELLED', page: 7, size: 50 })

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/platform/tasks?status=ALL&page=1&size=20',
      '/api/v1/platform/tasks?status=CANCELLED&page=7&size=50',
    ])
    expect(fetchMock.mock.calls.every(call => !String(call[0]).includes('account'))).toBe(true)
  })

  it('uses exact lifecycle endpoints and preserves a non-negative version including zero', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope(task)))
    vi.stubGlobal('fetch', fetchMock)

    await platformTaskApi.complete('task/1', { version: 0 })
    await platformTaskApi.reopen('task/1', { version: 4 })
    await platformTaskApi.cancel('task/1', { version: 5 })

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/platform/tasks/task%2F1:complete',
      '/api/v1/platform/tasks/task%2F1:reopen',
      '/api/v1/platform/tasks/task%2F1:cancel',
    ])
    for (const [index, expectedVersion] of [0, 4, 5].entries()) {
      const request = fetchMock.mock.calls[index]![1] as RequestInit
      expect(request.method).toBe('POST')
      expect(JSON.parse(String(request.body))).toEqual({ version: expectedVersion })
      expect(Object.keys(JSON.parse(String(request.body)))).toEqual(['version'])
      expect((request.headers as Headers).has('Idempotency-Key')).toBe(false)
    }
  })
})
