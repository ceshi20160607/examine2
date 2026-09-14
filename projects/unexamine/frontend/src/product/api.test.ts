import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './api'
import { platformContext, platformTokens, systemContext, systemTokens } from './session'

const memoryStorage = () => {
  const values = new Map<string, string>()
  return {
    get length() { return values.size },
    clear: () => values.clear(),
    getItem: (key: string) => values.get(key) ?? null,
    key: (index: number) => [...values.keys()][index] ?? null,
    removeItem: (key: string) => values.delete(key),
    setItem: (key: string, value: string) => values.set(key, value),
  }
}

describe('unified API contract', () => {
  afterEach(() => {
    platformTokens.value = null
    platformContext.value = null
    systemTokens.value = null
    systemContext.value = null
    vi.unstubAllGlobals()
  })

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

  it('rotates an expired platform session once and retries the original request', async () => {
    vi.stubGlobal('sessionStorage', memoryStorage())
    platformTokens.value = {
      accessToken: 'expired-access', refreshToken: 'valid-refresh',
      accessExpiresAt: '2099-01-01T00:00:00Z', refreshExpiresAt: '2099-01-02T00:00:00Z',
    }
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 'AUTHENTICATION_REQUIRED', message: '登录状态无效或已过期', data: null, requestId: 'expired',
      }), { status: 401, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 'OK', message: 'success', data: {
          accessToken: 'rotated-access', refreshToken: 'rotated-refresh',
          accessExpiresAt: '2099-02-01T00:00:00Z', refreshExpiresAt: '2099-02-02T00:00:00Z',
        }, requestId: 'refresh',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 'OK', message: 'success', data: { value: 'restored' }, requestId: 'retried',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetcher)

    await expect(api<{ value: string }>('/api/example', {}, 'expired-access'))
      .resolves.toEqual({ value: 'restored' })
    expect(fetcher).toHaveBeenCalledTimes(3)
    expect(fetcher.mock.calls[1]![0]).toBe('/api/auth/refresh')
    expect((fetcher.mock.calls[2]![1].headers as Headers).get('Authorization')).toBe('Bearer rotated-access')
    expect(platformTokens.value?.accessToken).toBe('rotated-access')
  })
})
