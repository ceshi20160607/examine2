import { beforeEach, describe, expect, it, vi } from 'vitest'

import { messageTemplateApi } from '@/services/event'

function envelope(data: unknown) {
  return new Response(JSON.stringify({ code: 'OK', message: '', data, requestId: 'r', traceId: 't', errors: [] }),
    { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('message template API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('owns scoped list, optimistic update and idempotent publication endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope({ templateCode: 'MODULE_EXPORT_SUCCEEDED' })))
    vi.stubGlobal('fetch', fetchMock)

    await messageTemplateApi.list('10/1')
    await messageTemplateApi.update('10/1', 'MODULE_EXPORT_SUCCEEDED', {
      expectedVersion: 2, name: 'Export', enabled: true, titleTemplate: 'Ready',
      bodyTemplate: '{moduleCode} {rows}', channels: ['INBOX'],
    })
    await messageTemplateApi.publish('10/1', 'MODULE_EXPORT_SUCCEEDED', 3)

    const root = '/api/v1/systems/10%2F1/admin/event/message-templates'
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      root, `${root}/MODULE_EXPORT_SUCCEEDED`, `${root}/MODULE_EXPORT_SUCCEEDED:publish`,
    ])
    const [, publishOptions] = fetchMock.mock.calls[2] as unknown as [string, RequestInit]
    expect((publishOptions.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(publishOptions.body))).toEqual({ expectedVersion: 3 })
  })
})
