import { beforeEach, describe, expect, it, vi } from 'vitest'

import { configApi } from '@/services/config'

function ok(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

describe('config page API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('creates a page on the existing collection with the exact body and idempotency metadata', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ id: 'page-2' }))
    vi.stubGlobal('fetch', fetchMock)
    const body = {
      code: 'detail',
      name: '详情页',
      type: 'DETAIL',
      isDefault: true,
      status: 'ENABLED',
      layout: {
        columns: 12,
        gap: 12,
        labelPosition: 'TOP',
        density: 'DEFAULT',
        stickyActions: true,
        pageSize: 20,
        showSearch: true,
        showFilters: true,
      },
      draftRevision: '7',
    }

    await configApi.createPage('10', 'module-1', body)

    const [path, options] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    const headers = options.headers as Headers
    expect(path).toBe('/api/v1/systems/10/admin/config/modules/module-1/pages')
    expect(options.method).toBe('POST')
    expect(headers.get('Idempotency-Key')).toBeTruthy()
    expect(headers.get('X-CSRF-Token')).toBe('csrf-token')
    expect(JSON.parse(String(options.body))).toEqual(body)
  })
})
