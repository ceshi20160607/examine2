import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiDownload, apiRequestForm } from '@/services/api'

describe('binary API transport', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('sends FormData without overriding the multipart boundary', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(new Response(JSON.stringify({
      code: 'OK', message: '', data: { id: '1' }, requestId: 'r', traceId: 't', errors: [],
    }), { status: 201, headers: { 'Content-Type': 'application/json' } })))
    vi.stubGlobal('fetch', fetchMock)
    const form = new FormData()
    form.append('file', new Blob(['hello'], { type: 'text/plain' }), 'hello.txt')

    await apiRequestForm('/upload', form, { method: 'POST', idempotencyKey: 'key-1' })

    const options = fetchMock.mock.calls[0]?.[1] as RequestInit
    const headers = options.headers as Headers
    expect(options.body).toBe(form)
    expect(headers.has('Content-Type')).toBe(false)
    expect(headers.get('X-CSRF-Token')).toBe('csrf-token')
    expect(headers.get('Idempotency-Key')).toBe('key-1')
  })

  it('returns binary metadata and keeps JSON error envelopes', async () => {
    const success = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(new Response('hello', {
      status: 200,
      headers: {
        'Content-Type': 'text/plain',
        'Content-Disposition': "attachment; filename*=UTF-8''hello%20world.txt",
      },
    })))
    vi.stubGlobal('fetch', success)

    const downloaded = await apiDownload('/download')
    expect(downloaded.filename).toBe('hello world.txt')
    expect(downloaded.mediaType).toBe('text/plain')
    expect(await downloaded.blob.text()).toBe('hello')

    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(JSON.stringify({
      code: 'FILE_NOT_FOUND', message: 'missing', data: null, requestId: 'r', traceId: 't', errors: [],
    }), { status: 404, headers: { 'Content-Type': 'application/json' } }))))
    await expect(apiDownload('/missing')).rejects.toMatchObject({
      code: 'FILE_NOT_FOUND',
      status: 404,
    })
  })
})
