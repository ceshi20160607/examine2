import { beforeEach, describe, expect, it, vi } from 'vitest'

import { passwordRecoveryApi } from '@/services/passwordRecovery'

function response(data: unknown = null) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

describe('password recovery API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('requests a generic recovery delivery without exposing a token', async () => {
    const fetchMock = vi.fn(() => response())
    vi.stubGlobal('fetch', fetchMock)

    await passwordRecoveryApi.request('owner@example.com')

    const [path, init] = fetchMock.mock.calls[0]! as unknown as [string, RequestInit]
    const headers = init.headers as Headers
    expect(path).toBe('/api/v1/auth/password-recovery/request')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
    expect(JSON.parse(String(init.body))).toEqual({ account: 'owner@example.com' })
    expect(headers.get('Idempotency-Key')).toBeTruthy()
    expect(String(init.body)).not.toContain('token')
  })

  it('submits only the in-memory token and new password to the reset endpoint', async () => {
    const fetchMock = vi.fn(() => response())
    vi.stubGlobal('fetch', fetchMock)

    await passwordRecoveryApi.reset('single-use-token', 'next-secret-value')

    const [path, init] = fetchMock.mock.calls[0]! as unknown as [string, RequestInit]
    const headers = init.headers as Headers
    expect(path).toBe('/api/v1/auth/password-recovery/reset')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
    expect(JSON.parse(String(init.body))).toEqual({
      token: 'single-use-token',
      newPassword: 'next-secret-value',
    })
    expect(headers.get('Idempotency-Key')).toBeTruthy()
  })
})
