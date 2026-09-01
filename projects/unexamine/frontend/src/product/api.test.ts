import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './api'

describe('unified API contract', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('unwraps a successful business result', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'OK', message: 'success', data: { version: 2 }, requestId: 'request-ok',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    await expect(api<{ version: number }>('/api/example')).resolves.toEqual({ version: 2 })
  })

  it('rejects a non-OK business code even when HTTP is 200', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'CONCURRENT_MODIFICATION', message: '请刷新后重试',
      data: { traceId: 'trace-conflict' }, requestId: 'request-conflict',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    const error = await api('/api/example').catch(cause => cause)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ code: 'CONCURRENT_MODIFICATION', message: '请刷新后重试', traceId: 'trace-conflict' })
  })

  it('keeps the domain error and requestId for an HTTP failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'PERMISSION_DENIED', message: '没有操作权限', data: null, requestId: 'request-denied',
    }), { status: 403, headers: { 'Content-Type': 'application/json' } })))

    const error = await api('/api/example').catch(cause => cause)
    expect(error).toMatchObject({ code: 'PERMISSION_DENIED', message: '没有操作权限', traceId: 'request-denied' })
  })
})
