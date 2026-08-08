import { beforeEach, describe, expect, it, vi } from 'vitest'

import { eventAdministrationApi, eventApi } from '@/services/event'

function ok(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

describe('event inbox API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('loads filtered server pages through the frozen message endpoint', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      items: [], page: 1, size: 20, total: 0,
    }))
    vi.stubGlobal('fetch', fetchMock)

    await eventApi.list('10')
    await eventApi.list('10', { status: 'ARCHIVED', page: 3, size: 50 })
    await eventApi.unreadCount('10')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/event/messages?status=ALL&page=1&size=20',
      '/api/v1/systems/10/event/messages?status=ARCHIVED&page=3&size=50',
      '/api/v1/systems/10/event/messages/unread-count',
    ])
  })

  it('maps read, read-all and archive mutations and includes CSRF metadata', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ changedCount: 1 }))
    vi.stubGlobal('fetch', fetchMock)

    await eventApi.read('10', '20')
    await eventApi.readAll('10')
    await eventApi.archive('10', '20')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/event/messages/20:read',
      '/api/v1/systems/10/event/messages/read-all',
      '/api/v1/systems/10/event/messages/20:archive',
    ])
    for (const call of fetchMock.mock.calls) {
      const headers = (call[1] as RequestInit).headers as Headers
      expect(headers.get('X-CSRF-Token')).toBe('csrf-token')
      expect(headers.get('Idempotency-Key')).toBeTruthy()
    }
  })

  it('maps current-member delivery preference reads and exact controlled updates', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok([]))
    vi.stubGlobal('fetch', fetchMock)

    await eventApi.deliveryPreferences('system 10')
    await eventApi.updateDeliveryPreference('system 10', 'MODULE_EXPORT_SUCCEEDED', 'EMAIL', {
      enabled: false,
      expectedVersion: 0,
    })

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/system%2010/event/delivery-preferences',
      '/api/v1/systems/system%2010/event/delivery-preferences/MODULE_EXPORT_SUCCEEDED/EMAIL',
    ])
    const readRequest = fetchMock.mock.calls[0]![1] as RequestInit
    const updateRequest = fetchMock.mock.calls[1]![1] as RequestInit
    expect(readRequest.method).toBeUndefined()
    expect(readRequest.body).toBeUndefined()
    expect(updateRequest.method).toBe('PUT')
    expect(JSON.parse(String(updateRequest.body))).toEqual({ enabled: false, expectedVersion: 0 })
    expect(Object.keys(JSON.parse(String(updateRequest.body)))).toEqual(['enabled', 'expectedVersion'])
    expect((updateRequest.headers as Headers).has('Idempotency-Key')).toBe(false)
  })

  it('maps safe delivery-log filters and detail reads without inventing retry mutations', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      items: [], total: 0, page: 1, size: 20,
    }))
    vi.stubGlobal('fetch', fetchMock)

    await eventApi.deliveryLogs('system 10')
    await eventApi.deliveryLogs('system 10', {
      page: 2,
      size: 50,
      channel: 'WEBHOOK',
      status: 'FAILED',
      templateCode: ' MODULE_EXPORT_SUCCEEDED ',
    })
    await eventApi.deliveryLog('system 10', 'delivery/20')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/system%2010/event/delivery-logs?page=0&size=20',
      '/api/v1/systems/system%2010/event/delivery-logs?page=2&size=50&channel=WEBHOOK&status=FAILED&templateCode=MODULE_EXPORT_SUCCEEDED',
      '/api/v1/systems/system%2010/event/delivery-logs/delivery%2F20',
    ])
    expect(fetchMock.mock.calls.every((call) => (call[1] as RequestInit).method === undefined)).toBe(true)
  })

  it('maps channel administration with only non-secret update fields', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok([]))
    vi.stubGlobal('fetch', fetchMock)

    await eventAdministrationApi.channels('system 10')
    await eventAdministrationApi.updateChannel('system 10', 'WEBHOOK', {
      enabled: true,
      endpoint: 'https://hooks.example.test/notify',
      secretRef: 'env://EVENT_WEBHOOK_SECRET_V2',
      timeoutMs: 3000,
      expectedVersion: 4,
    })
    await eventAdministrationApi.checkChannel('system 10', 'EMAIL')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/system%2010/event/channels',
      '/api/v1/systems/system%2010/event/channels/WEBHOOK',
      '/api/v1/systems/system%2010/event/channels/EMAIL:check',
    ])
    expect(JSON.parse(String((fetchMock.mock.calls[1]![1] as RequestInit).body))).toEqual({
      enabled: true,
      endpoint: 'https://hooks.example.test/notify',
      secretRef: 'env://EVENT_WEBHOOK_SECRET_V2',
      timeoutMs: 3000,
      expectedVersion: 4,
    })
    const check = fetchMock.mock.calls[2]![1] as RequestInit
    expect(check.method).toBe('POST')
    expect(((check.headers as Headers).get('Idempotency-Key'))).toBeTruthy()
  })
})
