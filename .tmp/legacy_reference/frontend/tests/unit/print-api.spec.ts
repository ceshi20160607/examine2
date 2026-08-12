import { beforeEach, describe, expect, it, vi } from 'vitest'

import { printTemplateApi, runtimePrintApi } from '@/services/print'

function envelope(data: unknown) {
  return new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('print template and runtime API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('owns versioned admin template create, update and publication endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope({ templateId: '70' })))
    vi.stubGlobal('fetch', fetchMock)

    await printTemplateApi.list('10', '20/1')
    await printTemplateApi.create('10', '20/1', { code: 'contract_print' })
    await printTemplateApi.update('10', '20/1', '70/2', { expectedVersion: 1 })
    await printTemplateApi.publish('10', '20/1', '70/2', 2)

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/admin/config/modules/20%2F1/print-templates',
      '/api/v1/systems/10/admin/config/modules/20%2F1/print-templates',
      '/api/v1/systems/10/admin/config/modules/20%2F1/print-templates/70%2F2',
      '/api/v1/systems/10/admin/config/modules/20%2F1/print-templates/70%2F2:publish',
    ])
    const [, createOptions] = fetchMock.mock.calls[1] as unknown as [string, RequestInit]
    const [, publishOptions] = fetchMock.mock.calls[3] as unknown as [string, RequestInit]
    expect((createOptions.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect((publishOptions.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(publishOptions.body))).toEqual({ expectedVersion: 2 })
  })

  it('previews, creates, polls, pages and downloads record-scoped PDF history', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => String(input).endsWith('.pdf')
      ? Promise.resolve(new Response('%PDF-result', {
        status: 200,
        headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="record.pdf"' },
      }))
      : Promise.resolve(envelope({ printId: '800' })))
    vi.stubGlobal('fetch', fetchMock)

    await runtimePrintApi.templates('10', 'work/order', '30/1')
    await runtimePrintApi.preview('10', 'work/order', '30/1', 'contract_print')
    await runtimePrintApi.create('10', 'work/order', '30/1', 'contract_print', 4)
    await runtimePrintApi.history('10', 'work/order', '30/1', 2, 10)
    await runtimePrintApi.task('10', 'work/order', '30/1', '800/2')
    const result = await runtimePrintApi.resultPdf('10', 'work/order', '30/1', '800/2')

    const base = '/api/v1/systems/10/runtime/modules/work%2Forder/records/30%2F1'
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      `${base}/print-templates`, `${base}/print-preview`, `${base}/prints`,
      `${base}/prints?page=2&size=10`, `${base}/prints/800%2F2`, `${base}/prints/800%2F2/result.pdf`,
    ])
    const [, createOptions] = fetchMock.mock.calls[2] as unknown as [string, RequestInit]
    expect(JSON.parse(String(createOptions.body))).toEqual({ templateCode: 'contract_print', expectedRecordVersion: 4 })
    expect((createOptions.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(result.filename).toBe('record.pdf')
    expect(result.mediaType).toBe('application/pdf')
  })
})
