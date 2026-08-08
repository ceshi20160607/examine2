import { beforeEach, describe, expect, it, vi } from 'vitest'

import { runtimeExportApi } from '@/services/runtimeExport'
import type { RuntimeRecordQuery } from '@/types/config'

function envelope(data: unknown) {
  return new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

const query: RuntimeRecordQuery = {
  schemaVersionId: '40', page: 1, size: 200, recordScope: 'active', q: null,
  filter: null, sort: [], columns: ['name'], viewId: null,
}

describe('runtime export API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('creates, reads, pages and downloads member-scoped export tasks', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => String(input).endsWith('.xlsx')
      ? Promise.resolve(new Response('xlsx', {
        status: 200,
        headers: {
          'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          'Content-Disposition': 'attachment; filename="work_order-export.xlsx"',
        },
      }))
      : Promise.resolve(envelope({ exportId: '700' })))
    vi.stubGlobal('fetch', fetchMock)

    await runtimeExportApi.create('10', 'work/order', { query, fieldCodes: ['name'] })
    await runtimeExportApi.history('10', 'work/order', 2, 10)
    await runtimeExportApi.task('10', 'work/order', '700/1')
    const result = await runtimeExportApi.resultWorkbook('10', 'work/order', '700/1')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/runtime/modules/work%2Forder/exports',
      '/api/v1/systems/10/runtime/modules/work%2Forder/exports?page=2&size=10',
      '/api/v1/systems/10/runtime/modules/work%2Forder/exports/700%2F1',
      '/api/v1/systems/10/runtime/modules/work%2Forder/exports/700%2F1/result.xlsx',
    ])
    const [, createOptions] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(createOptions.method).toBe('POST')
    expect((createOptions.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    expect(JSON.parse(String(createOptions.body))).toEqual({ query, fieldCodes: ['name'] })
    expect(result.filename).toBe('work_order-export.xlsx')
  })
})
