import { beforeEach, describe, expect, it, vi } from 'vitest'

import { dataSourceAdminApi, runtimeDataSourceApi } from '@/services/dataSource'

function envelope(data: unknown) {
  return new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('data source admin and runtime API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('owns the catalog, root, draft, check, publish and immutable-version endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope([])))
    vi.stubGlobal('fetch', fetchMock)

    await dataSourceAdminApi.catalog('10')
    await dataSourceAdminApi.list('10')
    await dataSourceAdminApi.create('10', { code: 'orders', moduleId: '20', name: '订单', description: null })
    await dataSourceAdminApi.detail('10', '30/1')
    await dataSourceAdminApi.saveDraft('10', '30/1', {
      expectedVersion: 2,
      name: '订单',
      description: null,
      outputFields: [{ fieldCode: 'title' }],
      fixedFilters: [{ fieldCode: 'amount', operator: 'GTE', canonicalValue: 100 }],
      defaultSort: { fieldCode: 'title', direction: 'ASC' },
      defaultTimeFieldCode: null,
    })
    await dataSourceAdminApi.checkDraft('10', '30/1')
    await dataSourceAdminApi.publishDraft('10', '30/1', { expectedVersion: 3 })
    await dataSourceAdminApi.versions('10', '30/1')

    const root = '/api/v1/systems/10/admin/data-sources'
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      `${root}/catalog`, root, root, `${root}/30%2F1`, `${root}/30%2F1/draft`,
      `${root}/30%2F1/draft:check`, `${root}/30%2F1/draft:publish`, `${root}/30%2F1/versions`,
    ])
    const createOptions = fetchMock.mock.calls[2]![1] as RequestInit
    const saveOptions = fetchMock.mock.calls[4]![1] as RequestInit
    const checkOptions = fetchMock.mock.calls[5]![1] as RequestInit
    const publishOptions = fetchMock.mock.calls[6]![1] as RequestInit
    expect(createOptions.method).toBe('POST')
    expect((createOptions.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(saveOptions.method).toBe('PUT')
    expect(JSON.parse(String(saveOptions.body))).toMatchObject({ expectedVersion: 2 })
    expect(checkOptions.method).toBe('POST')
    expect(checkOptions.body).toBeUndefined()
    expect(publishOptions.method).toBe('POST')
    expect((publishOptions.headers as Headers).get('Idempotency-Key')).toBeTruthy()
  })

  it('queries only the published runtime metadata, bounded Native row page and explicit HTTP page', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope({})))
    vi.stubGlobal('fetch', fetchMock)

    await runtimeDataSourceApi.metadata('10', 'orders/all')
    await runtimeDataSourceApi.rows('10', 'orders/all', 2, 25)
    await runtimeDataSourceApi.httpRows('10', 'orders/all')
    await runtimeDataSourceApi.jdbcRows('10', 'orders/all')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/data-sources/orders%2Fall',
      '/api/v1/systems/10/data-sources/orders%2Fall/rows?page=2&size=25',
      '/api/v1/systems/10/data-sources/orders%2Fall/http-rows',
      '/api/v1/systems/10/data-sources/orders%2Fall/jdbc-rows',
    ])
    expect(fetchMock.mock.calls.every((call) => (call[1] as RequestInit).method === undefined)).toBe(true)
  })

  it('sends only frozen HTTP JSON draft settings and checks the persisted draft explicitly', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope({})))
    vi.stubGlobal('fetch', fetchMock)
    const httpJsonConnection = {
      endpoint: 'https://api.example.com/v1/orders',
      authSecretRef: 'env://EXAMINE_DS_S10_T20_ORDERS_V1',
      timeoutSeconds: 5,
    }

    await dataSourceAdminApi.create('10', {
      code: 'external_orders', moduleId: '20', name: '外部订单', description: null,
      sourceKind: 'HTTP_JSON', httpJsonConnection,
    })
    await dataSourceAdminApi.saveDraft('10', '30/1', {
      expectedVersion: 4, name: '外部订单', description: null,
      outputFields: [], fixedFilters: [],
      defaultSort: null, defaultTimeFieldCode: null,
      sourceKind: 'HTTP_JSON', httpJsonConnection,
      httpFieldProjections: [{ sourceField: 'display_name', fieldCode: 'title', sourceType: 'STRING' }],
    })
    await dataSourceAdminApi.checkDraftConnection('10', '30/1', { expectedVersion: 5 })
    await dataSourceAdminApi.discoverDraftSchema('10', '30/1', { expectedVersion: 5 })
    await dataSourceAdminApi.previewDraftRows('10', '30/1', { expectedVersion: 5 })

    const root = '/api/v1/systems/10/admin/data-sources'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      root,
      `${root}/30%2F1/draft`,
      `${root}/30%2F1/draft:connection-check`,
      `${root}/30%2F1/draft:schema-discovery`,
      `${root}/30%2F1/draft:rows-preview`,
    ])
    const createBody = JSON.parse(String((fetchMock.mock.calls[0]![1] as RequestInit).body))
    const saveBody = JSON.parse(String((fetchMock.mock.calls[1]![1] as RequestInit).body))
    const connectionCheck = fetchMock.mock.calls[2]![1] as RequestInit
    const schemaDiscovery = fetchMock.mock.calls[3]![1] as RequestInit
    const rowsPreview = fetchMock.mock.calls[4]![1] as RequestInit
    expect(createBody).toEqual({
      code: 'external_orders', moduleId: '20', name: '外部订单', description: null,
      sourceKind: 'HTTP_JSON', httpJsonConnection,
    })
    expect(saveBody).toMatchObject({
      expectedVersion: 4, sourceKind: 'HTTP_JSON', httpJsonConnection,
    })
    expect(JSON.stringify([createBody, saveBody])).not.toMatch(/password|bearer|credential|token/iu)
    expect(connectionCheck.method).toBe('POST')
    expect(JSON.parse(String(connectionCheck.body))).toEqual({ expectedVersion: 5 })
    expect(schemaDiscovery.method).toBe('POST')
    expect(JSON.parse(String(schemaDiscovery.body))).toEqual({ expectedVersion: 5 })
    expect(JSON.stringify(JSON.parse(String(schemaDiscovery.body))))
      .not.toMatch(/sample|endpoint|secret|credential|token/iu)
    expect(rowsPreview.method).toBe('POST')
    expect(JSON.parse(String(rowsPreview.body))).toEqual({ expectedVersion: 5 })
    expect(JSON.stringify(JSON.parse(String(rowsPreview.body))))
      .not.toMatch(/page|size|sample|endpoint|secret|credential|token/iu)
  })

  it('posts the frozen statistics wire DTO to the exact published-source endpoint', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope({})))
    vi.stubGlobal('fetch', fetchMock)

    await runtimeDataSourceApi.statisticsCapabilities('10', 'orders/all')
    await runtimeDataSourceApi.statistics('10', 'orders/all', {
      aggregation: 'AVG', measureFieldCode: 'amount', grouping: null,
      trend: { fieldCode: 'createdAt', grain: 'MONTH', startInclusive: '2026-01-01', endExclusive: '2026-07-01' },
    })

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/data-sources/orders%2Fall/statistics-capabilities',
      '/api/v1/systems/10/data-sources/orders%2Fall:statistics',
    ])
    const options = fetchMock.mock.calls[1]![1] as RequestInit
    expect(options.method).toBe('POST')
    expect(JSON.parse(String(options.body))).toEqual({
      aggregation: 'AVG', measureFieldCode: 'amount', grouping: null,
      trend: { fieldCode: 'createdAt', grain: 'MONTH', startInclusive: '2026-01-01', endExclusive: '2026-07-01' },
    })
  })
})
