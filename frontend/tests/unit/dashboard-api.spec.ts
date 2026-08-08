import { beforeEach, describe, expect, it, vi } from 'vitest'

import { dashboardAdminApi, runtimeDashboardApi } from '@/services/dashboard'

function envelope(data: unknown) {
  return new Response(JSON.stringify({ code: 'OK', message: '', data, requestId: 'r1', traceId: 't1', errors: [] }), {
    status: 200, headers: { 'Content-Type': 'application/json' },
  })
}

describe('dashboard admin and runtime API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('owns the complete draft check publish and immutable version endpoint set', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope([])))
    vi.stubGlobal('fetch', fetchMock)
    const widget = {
      code: 'amount_1', type: 'STAT_VALUE' as const, title: '订单金额', dataSourceId: '30',
      rowLimit: null,
      statistics: { aggregation: 'SUM' as const, measureFieldCode: 'amount', grouping: null, trend: null },
      grid: { x: 0, y: 0, width: 3, height: 2 },
    }

    await dashboardAdminApi.list('10')
    await dashboardAdminApi.create('10', { code: 'home', placement: 'SYSTEM_HOME', name: '首页', description: null })
    await dashboardAdminApi.detail('10', '20/1')
    await dashboardAdminApi.saveDraft('10', '20/1', { expectedVersion: 1, name: '首页', description: null, widgets: [widget] })
    await dashboardAdminApi.checkDraft('10', '20/1')
    await dashboardAdminApi.publishDraft('10', '20/1', 2)
    await dashboardAdminApi.versions('10', '20/1')

    const root = '/api/v1/systems/10/admin/dashboards'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      root, root, `${root}/20%2F1`, `${root}/20%2F1/draft`, `${root}/20%2F1/draft:check`,
      `${root}/20%2F1/draft:publish`, `${root}/20%2F1/versions`,
    ])
    const create = fetchMock.mock.calls[1]![1] as RequestInit
    const save = fetchMock.mock.calls[3]![1] as RequestInit
    const publish = fetchMock.mock.calls[5]![1] as RequestInit
    expect(create.method).toBe('POST')
    expect((create.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(save.method).toBe('PUT')
    expect(JSON.parse(String(save.body))).toMatchObject({ expectedVersion: 1, widgets: [widget] })
    expect(JSON.parse(String(publish.body))).toEqual({ expectedVersion: 2 })
    expect((publish.headers as Headers).get('Idempotency-Key')).toBeTruthy()
  })

  it('loads the active SYSTEM_HOME dashboard and exact-code preview separately', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope({})))
    vi.stubGlobal('fetch', fetchMock)

    await runtimeDashboardApi.systemHome('10')
    await runtimeDashboardApi.byCode('10', 'home/main')

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/dashboards/system-home',
      '/api/v1/systems/10/dashboards/home%2Fmain',
    ])
  })

  it('owns public and personal scoped dashboard directory, draft, publish and runtime routes', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope([])))
    vi.stubGlobal('fetch', fetchMock)
    const draft = { expectedVersion: 1, name: '销售首页', description: null, widgets: [] }

    await dashboardAdminApi.scopedList('10')
    await dashboardAdminApi.createScoped('10', { code: 'sales', placement: 'APPLICATION_HOME', scopeKey: 'sales:app', name: '销售首页', description: null })
    await dashboardAdminApi.scopedDetail('10', 'APPLICATION_HOME', 'sales:app')
    await dashboardAdminApi.scopedSaveDraft('10', 'APPLICATION_HOME', 'sales:app', draft)
    await dashboardAdminApi.scopedCheckDraft('10', 'APPLICATION_HOME', 'sales:app')
    await dashboardAdminApi.scopedPublishDraft('10', 'APPLICATION_HOME', 'sales:app', 2)
    await dashboardAdminApi.scopedVersions('10', 'APPLICATION_HOME', 'sales:app')
    await dashboardAdminApi.personal('10')
    await dashboardAdminApi.personalDetail('10', '88')
    await dashboardAdminApi.personalSaveDraft('10', '88', draft)
    await dashboardAdminApi.personalCheckDraft('10', '88')
    await dashboardAdminApi.personalPublishDraft('10', '88', 2)
    await dashboardAdminApi.personalVersions('10', '88')
    await runtimeDashboardApi.scoped('10', 'APPLICATION_HOME', 'sales:app')

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/admin/dashboard-scopes',
      '/api/v1/systems/10/admin/dashboard-scopes/APPLICATION_HOME/sales%3Aapp',
      '/api/v1/systems/10/admin/dashboard-scopes/APPLICATION_HOME/sales%3Aapp',
      '/api/v1/systems/10/admin/dashboard-scopes/APPLICATION_HOME/sales%3Aapp/draft',
      '/api/v1/systems/10/admin/dashboard-scopes/APPLICATION_HOME/sales%3Aapp/draft:check',
      '/api/v1/systems/10/admin/dashboard-scopes/APPLICATION_HOME/sales%3Aapp/draft:publish',
      '/api/v1/systems/10/admin/dashboard-scopes/APPLICATION_HOME/sales%3Aapp/versions',
      '/api/v1/systems/10/dashboards/personal',
      '/api/v1/systems/10/dashboards/personal/88',
      '/api/v1/systems/10/dashboards/personal/88/draft',
      '/api/v1/systems/10/dashboards/personal/88/draft:check',
      '/api/v1/systems/10/dashboards/personal/88/draft:publish',
      '/api/v1/systems/10/dashboards/personal/88/versions',
      '/api/v1/systems/10/dashboards/scopes/APPLICATION_HOME/sales%3Aapp',
    ])
  })

  it('submits only the KPI root draft pin and preserves backend decimal strings at runtime', async () => {
    const responses = [
      [],
      {
        id: '70', code: 'home', name: '首页', description: null, placement: 'SYSTEM_HOME', versionId: '71', versionNumber: 1,
        widgets: [{
          code: 'revenue', type: 'KPI_VALUE', title: '收入目标', ordinal: 0, grid: { x: 0, y: 0, width: 6, height: 5 },
          kpiId: '90', kpiVersionId: '91', kpiVersionNumber: 2, kpiCode: 'revenue', kpiName: '收入',
          kpiSubjectType: 'ROLE', kpiPeriodType: 'MONTH', status: 'OK', error: null, total: null, fields: [], rows: [],
          kpiTargets: [{
            id: '100', kpiId: '90', kpiVersionId: '91', kpiVersionNumber: 2, kpiCode: 'revenue', kpiName: '收入',
            subjectType: 'ROLE', subjectId: '80', subjectName: '销售', periodType: 'MONTH', periodStart: '2026-08-01',
            periodEndExclusive: '2026-09-01', targetValue: '99999999999999999999.00001', version: 1,
            createdAt: '', updatedAt: '', latestCalculation: {
              id: '101', targetId: '100', status: 'AT_RISK', errorCode: null,
              targetValue: '99999999999999999999.00001', actualValue: '81234567890123456789.12345',
              attainment: '0.81234567890123456789', calculatedAt: '', calculatedBy: '', explanation: {},
            },
          }],
        }],
      },
    ]
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope(responses.shift())))
    vi.stubGlobal('fetch', fetchMock)
    const widget = { code: 'revenue', type: 'KPI_VALUE' as const, title: '收入目标', kpiId: '90', grid: { x: 0, y: 0, width: 6, height: 5 } }

    await dashboardAdminApi.saveDraft('10', '70', { expectedVersion: 1, name: '首页', description: null, widgets: [widget] })
    const runtime = await runtimeDashboardApi.systemHome('10')

    const body = JSON.parse(String((fetchMock.mock.calls[0]![1] as RequestInit).body))
    expect(body.widgets[0]).toEqual(widget)
    expect(body.widgets[0]).not.toHaveProperty('dataSourceId')
    expect(body.widgets[0]).not.toHaveProperty('statistics')
    expect(body.widgets[0]).not.toHaveProperty('rowLimit')
    expect(runtime.widgets[0]!.kpiTargets[0]!.targetValue).toBe('99999999999999999999.00001')
    expect(runtime.widgets[0]!.kpiTargets[0]!.latestCalculation?.attainment).toBe('0.81234567890123456789')
  })
})
