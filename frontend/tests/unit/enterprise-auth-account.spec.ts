import { afterEach, describe, expect, it, vi } from 'vitest'

import router from '@/router'
import { accountApi, enterpriseAuthApi } from '@/services/enterpriseAuth'

function response(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: 'ok', data, requestId: 'req-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

afterEach(() => vi.unstubAllGlobals())

describe('enterprise authentication and account contracts', () => {
  it('exposes callback, MFA and authenticated account routes', () => {
    const names = router.getRoutes().map(item => item.name)
    expect(names).toContain('sso-callback')
    expect(names).toContain('mfa')
    expect(names).toContain('account-center')
  })

  it('starts SSO with a bounded query and posts the callback', async () => {
    const fetch = vi.fn()
      .mockImplementationOnce(() => response({ authorizationUrl: 'https://idp.example/authorize', state: 'state-1', expiresAt: '2026-08-07T12:00:00Z' }))
      .mockImplementationOnce(() => response({ status: 'AUTHENTICATED', session: { context: {} } }))
    vi.stubGlobal('fetch', fetch)

    await enterpriseAuthApi.start('corp-oidc', 'https://app.example/auth/sso/callback')
    await enterpriseAuthApi.callback('state-1', 'code-1')

    expect(fetch.mock.calls[0]![0]).toBe('/api/v1/auth/sso/start?providerCode=corp-oidc&redirectUri=https%3A%2F%2Fapp.example%2Fauth%2Fsso%2Fcallback')
    expect(fetch.mock.calls[1]![0]).toBe('/api/v1/auth/sso/callback')
    expect(JSON.parse(String(fetch.mock.calls[1]![1]?.body))).toEqual({ state: 'state-1', code: 'code-1' })
  })

  it('keeps directory passwords and MFA evidence only in request bodies', async () => {
    const fetch = vi.fn().mockImplementation(() => response({ status: 'MFA_REQUIRED', challenge: 'challenge-1' }))
    vi.stubGlobal('fetch', fetch)

    await enterpriseAuthApi.directory('corp-ad', 'member', 'directory-secret')
    await enterpriseAuthApi.verifyMfa('challenge-1', undefined, 'RECOVERY-CODE')

    expect(fetch.mock.calls[0]![0]).toBe('/api/v1/auth/sso/directory')
    expect(JSON.parse(String(fetch.mock.calls[0]![1]?.body))).toEqual({ providerCode: 'corp-ad', username: 'member', password: 'directory-secret' })
    expect(JSON.parse(String(fetch.mock.calls[1]![1]?.body))).toEqual({ challenge: 'challenge-1', recoveryCode: 'RECOVERY-CODE' })
  })

  it('binds profile and owned-session management to account endpoints', async () => {
    const fetch = vi.fn()
      .mockImplementationOnce(() => response({ id: '7', username: 'member', displayName: 'Member', version: 2 }))
      .mockImplementationOnce(() => response([]))
      .mockImplementationOnce(() => response(null))
      .mockImplementationOnce(() => response(null))
    vi.stubGlobal('fetch', fetch)

    await accountApi.profile()
    await accountApi.sessions()
    await accountApi.revokeSession('99')
    await accountApi.revokeOthers()

    expect(fetch.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/account/profile', '/api/v1/account/sessions',
      '/api/v1/account/sessions/99:revoke', '/api/v1/account/sessions:revoke-others',
    ])
    expect(fetch.mock.calls.slice(2).every(call => call[1]?.method === 'POST')).toBe(true)
  })
})
