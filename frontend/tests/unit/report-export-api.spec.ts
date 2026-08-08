import { beforeEach, describe, expect, it, vi } from 'vitest'

import { runtimeReportApi } from '@/services/report'

function envelope(data: unknown) {
  return new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('report XLSX export API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('starts with one stable request identity, pages, reads and downloads tenant-safe runs', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, _init?: RequestInit) =>
      String(input).endsWith('.xlsx')
        ? Promise.resolve(new Response('xlsx', { status: 200, headers: {
          'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          'Content-Disposition': "attachment; filename*=UTF-8''monthly-orders.xlsx",
        } }))
        : Promise.resolve(envelope({ exportId: '70' })))
    vi.stubGlobal('fetch', fetchMock)

    await runtimeReportApi.startExport('10', 'monthly/orders', 'stable-request-1')
    await runtimeReportApi.exports('10', 'monthly/orders', 2, 10)
    await runtimeReportApi.exportTask('10', 'monthly/orders', '70/1')
    const download = await runtimeReportApi.downloadExport('10', 'monthly/orders', '70/1')

    const root = '/api/v1/systems/10/reports/monthly%2Forders/exports'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      root, `${root}?page=2&size=10`, `${root}/70%2F1`, `${root}/70%2F1/result.xlsx`,
    ])
    const start = fetchMock.mock.calls[0]![1] as RequestInit
    expect(start.method).toBe('POST')
    expect((start.headers as Headers).get('Idempotency-Key')).toBe('stable-request-1')
    expect((start.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    expect(JSON.parse(String(start.body))).toEqual({ requestKey: 'stable-request-1' })
    expect(download.filename).toBe('monthly-orders.xlsx')
  })
})
