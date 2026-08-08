import { beforeEach, describe, expect, it, vi } from 'vitest'

import { runtimeImportApi } from '@/services/runtimeImport'

function envelope(data: unknown) {
  return new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('runtime import API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('uses the module runtime endpoints for template, preview, result, commit and rollback', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope({ batchId: '300' })))
    vi.stubGlobal('fetch', fetchMock)

    await runtimeImportApi.template('10', 'work/order')
    await runtimeImportApi.preview('10', 'work/order', {
      mode: 'UPSERT',
      matchFieldCode: 'external_key',
      rows: [{ external_key: 'WO-1' }],
    })
    await runtimeImportApi.batch('10', 'work/order', '300/1')
    await runtimeImportApi.commit('10', 'work/order', '300/1')
    await runtimeImportApi.rollback('10', 'work/order', '300/1')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/runtime/modules/work%2Forder/imports/template',
      '/api/v1/systems/10/runtime/modules/work%2Forder/imports:preview',
      '/api/v1/systems/10/runtime/modules/work%2Forder/imports/300%2F1',
      '/api/v1/systems/10/runtime/modules/work%2Forder/imports/300%2F1:commit',
      '/api/v1/systems/10/runtime/modules/work%2Forder/imports/300%2F1:rollback',
    ])
    const [, previewOptions] = fetchMock.mock.calls[1] as unknown as [string, RequestInit]
    expect(previewOptions.method).toBe('POST')
    expect((previewOptions.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    expect(JSON.parse(String(previewOptions.body))).toEqual({
      mode: 'UPSERT',
      matchFieldCode: 'external_key',
      rows: [{ external_key: 'WO-1' }],
    })
    const [, commitOptions] = fetchMock.mock.calls[3] as unknown as [string, RequestInit]
    const [, rollbackOptions] = fetchMock.mock.calls[4] as unknown as [string, RequestInit]
    expect(commitOptions.method).toBe('POST')
    expect(rollbackOptions.method).toBe('POST')
  })

  it('downloads workbooks, uploads multipart preview and pages member history', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path.endsWith('.xlsx')) {
        return Promise.resolve(new Response('xlsx', {
          status: 200, headers: { 'Content-Type': 'application/octet-stream' },
        }))
      }
      if (init?.method === 'POST') return Promise.resolve(envelope({ batchId: '300' }))
      return Promise.resolve(envelope({ items: [], page: 1, size: 20, total: 0 }))
    })
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['xlsx'], 'items.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })

    await runtimeImportApi.templateWorkbook('10', 'work_order')
    await runtimeImportApi.previewWorkbook('10', 'work_order', file, 'UPSERT', 'external_key')
    await runtimeImportApi.history('10', 'work_order', 2, 10)
    await runtimeImportApi.errorWorkbook('10', 'work_order', '300')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/runtime/modules/work_order/imports/template.xlsx',
      '/api/v1/systems/10/runtime/modules/work_order/imports:preview-xlsx',
      '/api/v1/systems/10/runtime/modules/work_order/imports?page=2&size=10',
      '/api/v1/systems/10/runtime/modules/work_order/imports/300/errors.xlsx',
    ])
    const [, uploadOptions] = fetchMock.mock.calls[1] as unknown as [string, RequestInit]
    expect(uploadOptions.method).toBe('POST')
    expect((uploadOptions.headers as Headers).get('Content-Type')).toBeNull()
    expect((uploadOptions.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    expect(uploadOptions.body).toBeInstanceOf(FormData)
    const form = uploadOptions.body as FormData
    expect(form.get('file')).toBe(file)
    expect(form.get('mode')).toBe('UPSERT')
    expect(form.get('matchFieldCode')).toBe('external_key')
  })
})
