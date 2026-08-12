import { beforeEach, describe, expect, it, vi } from 'vitest'

import { reportAdminApi, runtimeReportApi } from '@/services/report'

function envelope(data: unknown) {
  return new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('report schedule admin and delivered-run API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('creates, lists, reads, updates, previews and toggles nested report schedules', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope(
      String(input).includes('?page=1&size=100')
        ? { items: [], page: 1, size: 100, total: 0 }
        : [],
    )))
    vi.stubGlobal('fetch', fetchMock)
    const cadence = { kind: 'WEEKLY' as const, localTime: '09:30', daysOfWeek: ['MONDAY', 'FRIDAY'] }

    await reportAdminApi.schedules('10', '30/1')
    await reportAdminApi.createSchedule('10', '30/1', {
      code: 'weekly_orders', name: '每周订单', enabled: false, timeZone: 'Asia/Shanghai',
      cadence, recipientMemberIds: ['101', '102'],
    })
    await reportAdminApi.schedule('10', '30/1', '40/1')
    await reportAdminApi.updateSchedule('10', '30/1', '40/1', {
      expectedVersion: 2, name: '每周经营订单', timeZone: 'Asia/Shanghai', cadence,
      recipientMemberIds: ['101'],
    })
    await reportAdminApi.setScheduleEnabled('10', '30/1', '40/1', true, 3)
    await reportAdminApi.setScheduleEnabled('10', '30/1', '40/1', false, 4)
    await reportAdminApi.previewSchedule('10', '30/1', { timeZone: 'America/New_York', cadence })

    const root = '/api/v1/systems/10/admin/reports/30%2F1/schedules'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      `${root}?page=1&size=100`, root, `${root}/40%2F1`, `${root}/40%2F1`, `${root}/40%2F1:enable`,
      `${root}/40%2F1:disable`, `${root}/next-fire:preview`,
    ])
    const create = fetchMock.mock.calls[1]![1] as RequestInit
    const update = fetchMock.mock.calls[3]![1] as RequestInit
    const enable = fetchMock.mock.calls[4]![1] as RequestInit
    const preview = fetchMock.mock.calls[6]![1] as RequestInit
    expect(JSON.parse(String(create.body))).toMatchObject({ cadence, recipientMemberIds: ['101', '102'] })
    expect((create.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(update.body))).toEqual({
      expectedVersion: 2, name: '每周经营订单', timeZone: 'Asia/Shanghai', cadence,
      recipientMemberIds: ['101'],
    })
    expect(JSON.parse(String(enable.body))).toEqual({ expectedVersion: 3 })
    expect((enable.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(preview.body))).toEqual({ timeZone: 'America/New_York', cadence })
  })

  it('pages, reads and downloads only delivered scheduled runs for the current member', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, _init?: RequestInit) =>
      String(input).endsWith('.xlsx')
        ? Promise.resolve(new Response('xlsx', { status: 200, headers: {
          'Content-Disposition': 'attachment; filename="scheduled-orders.xlsx"',
        } }))
        : Promise.resolve(envelope({ items: [] })))
    vi.stubGlobal('fetch', fetchMock)

    await runtimeReportApi.scheduledRuns('10', 'monthly/orders', 2, 10)
    await runtimeReportApi.scheduledRun('10', 'monthly/orders', 'occurrence/1')
    const result = await runtimeReportApi.downloadScheduledRun('10', 'monthly/orders', 'occurrence/1')

    const root = '/api/v1/systems/10/reports/monthly%2Forders/scheduled-runs'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      `${root}?page=2&size=10`, `${root}/occurrence%2F1`, `${root}/occurrence%2F1/result.xlsx`,
    ])
    expect(result.filename).toBe('scheduled-orders.xlsx')
  })
})
