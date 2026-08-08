import { beforeEach, describe, expect, it, vi } from 'vitest'

import { reportAdminApi, runtimeReportApi } from '@/services/report'

function envelope(data: unknown) {
  return new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('report admin and runtime API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('owns root, draft, check, publish and exact version-number endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope([])))
    vi.stubGlobal('fetch', fetchMock)

    await reportAdminApi.list('10')
    await reportAdminApi.create('10', {
      code: 'orders', name: '订单报表', description: null,
      dataSourceId: '20', outputFieldCodes: ['recordNo', 'amount'],
    })
    await reportAdminApi.detail('10', '30/1')
    await reportAdminApi.saveDraft('10', '30/1', {
      expectedVersion: 2, name: '订单报表', description: null,
      dataSourceId: '20', outputFieldCodes: ['amount', 'recordNo'],
    })
    await reportAdminApi.checkDraft('10', '30/1')
    await reportAdminApi.publishDraft('10', '30/1', 3)
    await reportAdminApi.versions('10', '30/1')
    await reportAdminApi.version('10', '30/1', 4)

    const root = '/api/v1/systems/10/admin/reports'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      root, root, `${root}/30%2F1`, `${root}/30%2F1/draft`,
      `${root}/30%2F1/draft:check`, `${root}/30%2F1/draft:publish`,
      `${root}/30%2F1/versions`, `${root}/30%2F1/versions/4`,
    ])
    const create = fetchMock.mock.calls[1]![1] as RequestInit
    const draft = fetchMock.mock.calls[3]![1] as RequestInit
    const publish = fetchMock.mock.calls[5]![1] as RequestInit
    expect(create.method).toBe('POST')
    expect((create.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(draft.method).toBe('PUT')
    expect(JSON.parse(String(draft.body))).toMatchObject({
      expectedVersion: 2, dataSourceId: '20', outputFieldCodes: ['amount', 'recordNo'],
    })
    expect(JSON.parse(String(publish.body))).toEqual({ expectedVersion: 3 })
    expect((publish.headers as Headers).get('Idempotency-Key')).toBeTruthy()
  })

  it('lists metadata and executes only bounded code-addressed row pages', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope(
      String(input).includes('/rows?')
        ? { items: [{ recordId: '1', values: [] }], page: 2, size: 200, total: 201, queryHash: 'hash-1' }
        : [],
    )))
    vi.stubGlobal('fetch', fetchMock)

    await runtimeReportApi.list('10')
    await runtimeReportApi.metadata('10', 'orders/all')
    const rows = await runtimeReportApi.rows('10', 'orders/all', 2, 200)

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/reports',
      '/api/v1/systems/10/reports/orders%2Fall',
      '/api/v1/systems/10/reports/orders%2Fall/rows?page=2&size=200',
    ])
    expect(rows.rows).toHaveLength(1)
    expect(rows.items).toHaveLength(1)
    expect(rows.queryHash).toBe('hash-1')
  })
})
