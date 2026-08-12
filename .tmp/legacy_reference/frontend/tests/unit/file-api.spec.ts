import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fileApi } from '@/services/file'

function ok(data: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status, headers: { 'Content-Type': 'application/json' } }))
}

describe('file API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('uploads multipart content and keeps the browser boundary header', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ id: '20' }, 201))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['hello'], 'hello.txt', { type: 'text/plain' })

    await fileApi.upload('10', file)

    const [path, options] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/api/v1/systems/10/files')
    expect(options.body).toBeInstanceOf(FormData)
    expect((options.headers as Headers).has('Content-Type')).toBe(false)
  })

  it('maps metadata, references and delete to stable endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ id: '20', references: [] }))
    vi.stubGlobal('fetch', fetchMock)

    await fileApi.get('10', '20')
    await fileApi.addReference('10', '20', { targetType: 'MODULE_RECORD', targetId: '30' })
    await fileApi.removeReference('10', '20', { targetType: 'MODULE_RECORD', targetId: '30' })
    await fileApi.delete('10', '20')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/files/20',
      '/api/v1/systems/10/files/20/references',
      '/api/v1/systems/10/files/20/references',
      '/api/v1/systems/10/files/20',
    ])
    expect(fetchMock.mock.calls.map((call) => (call[1] as RequestInit | undefined)?.method)).toEqual([
      undefined, 'POST', 'DELETE', 'DELETE',
    ])
  })

  it('maps paged search, storage status, preview and bounded thumbnail endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => {
      const input = String(_input)
      if (input.includes('/preview') || input.includes('/thumbnail')) {
        return Promise.resolve(new Response('binary', {
          status: 200,
          headers: { 'Content-Type': 'image/png', 'Content-Disposition': 'inline; filename="preview.png"' },
        }))
      }
      return ok(input.endsWith('/storage-status')
        ? { mode: 'LOCAL', location: 'configured', available: true }
        : { items: [], page: 2, size: 10, total: 0, totalPages: 0 })
    })
    vi.stubGlobal('fetch', fetchMock)

    await fileApi.list('10', { page: 2, size: 10, keyword: ' quarterly report ', mediaType: 'image/*' })
    await fileApi.storageStatus('10')
    const preview = await fileApi.preview('10', '20')
    const thumbnail = await fileApi.thumbnail('10', '20', 80, 60)

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/files?page=2&size=10&keyword=quarterly+report&mediaType=image%2F*',
      '/api/v1/systems/10/files/storage-status',
      '/api/v1/systems/10/files/20/preview',
      '/api/v1/systems/10/files/20/thumbnail?maxWidth=80&maxHeight=60',
    ])
    expect(preview.filename).toBe('preview.png')
    expect(thumbnail.mediaType).toBe('image/png')
    expect(fileApi.thumbnailUrl('10', '20', 56, 48))
      .toBe('/api/v1/systems/10/files/20/thumbnail?maxWidth=56&maxHeight=48')
  })

  it('maps raw multipart initialization, part, completion and abort without FormData', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => {
      const input = String(_input)
      if (input.includes('/parts/2')) return ok({ uploadId: 'upload-1', partNumber: 2, sha256: 'part-sha' })
      if (input.endsWith(':complete')) return ok({ id: '20', references: [] })
      if ((_init?.method ?? '') === 'DELETE') return ok({ uploadId: 'upload-1', status: 'ABORTED' })
      return ok({ uploadId: 'upload-1', partSizeBytes: 5242880, partCount: 3, status: 'OPEN' }, 201)
    })
    vi.stubGlobal('fetch', fetchMock)
    const chunk = new Blob(['chunk'], { type: 'application/octet-stream' })

    await fileApi.initMultipart('10', {
      originalName: 'archive.bin', mediaType: 'application/octet-stream', sizeBytes: 15, sha256: 'file-sha',
    })
    await fileApi.uploadMultipartPart('10', 'upload-1', 2, chunk, 'part-sha')
    await fileApi.completeMultipart('10', 'upload-1', 'file-sha')
    await fileApi.abortMultipart('10', 'upload-1')

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/files/multipart-uploads',
      '/api/v1/systems/10/files/multipart-uploads/upload-1/parts/2',
      '/api/v1/systems/10/files/multipart-uploads/upload-1:complete',
      '/api/v1/systems/10/files/multipart-uploads/upload-1',
    ])
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    const part = fetchMock.mock.calls[1]?.[1] as RequestInit
    const complete = fetchMock.mock.calls[2]?.[1] as RequestInit
    expect(JSON.parse(String(init.body))).toEqual({
      originalName: 'archive.bin', mediaType: 'application/octet-stream', sizeBytes: 15, sha256: 'file-sha',
    })
    expect(part.method).toBe('PUT')
    expect(part.body).toBe(chunk)
    expect((part.headers as Headers).get('Content-Type')).toBe('application/octet-stream')
    expect((part.headers as Headers).get('X-Part-SHA256')).toBe('part-sha')
    expect(JSON.parse(String(complete.body))).toEqual({ sha256: 'file-sha' })
    expect((fetchMock.mock.calls[3]?.[1] as RequestInit).method).toBe('DELETE')
  })

  it('maps record-scoped list, upload, download and detach endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => {
      const input = String(_input)
      if (input.endsWith('/content')) {
        return Promise.resolve(new Response('hello', {
          status: 200,
          headers: { 'Content-Type': 'text/plain', 'Content-Disposition': 'attachment; filename="hello.txt"' },
        }))
      }
      return ok({ items: [], page: 1, size: 20, total: 0 })
    })
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['hello'], 'hello.txt', { type: 'text/plain' })

    await fileApi.recordFiles('10', 'work_order', '30')
    await fileApi.uploadRecordFile('10', 'work_order', '30', file)
    await fileApi.downloadRecordFile('10', 'work_order', '30', '40')
    await fileApi.downloadRecordFileBundle('10', 'work_order', '30')
    await fileApi.detachRecordFile('10', 'work_order', '30', '40')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/runtime/modules/work_order/records/30/files?page=1&size=20',
      '/api/v1/systems/10/runtime/modules/work_order/records/30/files',
      '/api/v1/systems/10/runtime/modules/work_order/records/30/files/40/content',
      '/api/v1/systems/10/runtime/modules/work_order/records/30/files:bundle',
      '/api/v1/systems/10/runtime/modules/work_order/records/30/files/40',
    ])
    expect(fetchMock.mock.calls.map((call) => (call[1] as RequestInit | undefined)?.method)).toEqual([
      undefined, 'POST', undefined, undefined, 'DELETE',
    ])
  })
})
