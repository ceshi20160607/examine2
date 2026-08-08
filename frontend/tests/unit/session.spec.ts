import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useSessionStore } from '@/stores/session'

const platformResult = {
  account: { id: '1', username: 'owner', displayName: 'Owner' },
  context: {
    type: 'PLATFORM' as const,
    account: { id: '1', username: 'owner', displayName: 'Owner' },
    permissionVersion: '1',
    permissions: ['platform.runtime.access'],
    shells: ['PLATFORM_RUNTIME' as const],
  },
  systems: [
    {
      id: '10',
      code: 'demo',
      name: 'Demo',
      status: 'INITIALIZING' as const,
      memberStatus: 'ACTIVE' as const,
      defaultTenantId: '11',
    },
  ],
}

function response(data: unknown, status = 200) {
  return Promise.resolve(
    new Response(
      JSON.stringify({
        code: status === 200 ? 'OK' : 'AUTH_REQUIRED',
        message: status === 200 ? '' : '请先登录',
        data,
        requestId: 'request-1',
        traceId: 'trace-1',
        errors: [],
      }),
      { status, headers: { 'Content-Type': 'application/json' } },
    ),
  )
}

describe('session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('boots into an anonymous state on a 401 response', async () => {
    vi.stubGlobal('fetch', vi.fn(() => response(null, 401)))
    const session = useSessionStore()

    await session.bootstrap()

    expect(session.initialized).toBe(true)
    expect(session.authenticated).toBe(false)
  })

  it('stores the real platform context returned by login', async () => {
    vi.stubGlobal('fetch', vi.fn(() => response(platformResult)))
    const session = useSessionStore()

    await session.login({ account: 'owner', password: 'not-stored-here' })

    expect(session.isPlatform).toBe(true)
    expect(session.systems).toHaveLength(1)
    expect(session.context?.account.displayName).toBe('Owner')
  })

  it('preserves request metadata when the API rejects a call', async () => {
    vi.stubGlobal('fetch', vi.fn(() => response(null, 401)))
    const session = useSessionStore()

    await expect(session.login({ account: 'owner', password: 'wrong' })).rejects.toMatchObject({
      code: 'AUTH_REQUIRED',
      requestId: 'request-1',
      status: 401,
    })
  })

  it('changes the password with the exact contract and clears the local session snapshot', async () => {
    const fetchMock = vi.fn(() => response(null))
    vi.stubGlobal('fetch', fetchMock)
    const session = useSessionStore()
    session.applyAuth(platformResult)

    await session.changePassword({ currentPassword: 'current-secret', newPassword: 'next-secret-value' })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [path, init] = fetchMock.mock.calls[0]! as unknown as [string, RequestInit]
    expect(path).toBe('/api/v1/auth/password:change')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
    expect(JSON.parse(String(init?.body))).toEqual({
      currentPassword: 'current-secret',
      newPassword: 'next-secret-value',
    })
    expect(session.context).toBeNull()
    expect(session.systems).toEqual([])
    expect(session.tenants).toEqual([])
    expect(session.initialized).toBe(true)
  })

  it('retains the local session snapshot when password change is rejected', async () => {
    vi.stubGlobal('fetch', vi.fn(() => response(null, 400)))
    const session = useSessionStore()
    session.applyAuth(platformResult)

    await expect(session.changePassword({
      currentPassword: 'wrong-current',
      newPassword: 'next-secret-value',
    })).rejects.toMatchObject({ requestId: 'request-1', status: 400 })

    expect(session.context?.account.id).toBe('1')
    expect(session.systems).toHaveLength(1)
    expect(session.initialized).toBe(true)
  })

  it('loads authorized tenants and replaces the permission snapshot when switching', async () => {
    const systemResult = {
      ...platformResult,
      context: {
        ...platformResult.context,
        type: 'SYSTEM' as const,
        systemId: '10',
        systemName: 'Demo',
        tenantId: '11',
        tenantName: 'Default',
        permissions: ['system.runtime.access'],
        shells: ['SYSTEM_RUNTIME' as const],
      },
    }
    const tenants = [
      { id: '11', code: 'default', name: 'Default', status: 'ACTIVE' as const, isDefault: true },
      { id: '12', code: 'east', name: 'East', status: 'ACTIVE' as const, isDefault: false },
    ]
    const switched = {
      ...systemResult,
      context: { ...systemResult.context, tenantId: '12', tenantName: 'East', permissionVersion: '2' },
      tenants,
    }
    const fetchMock = vi.fn()
      .mockImplementationOnce(() => response(tenants))
      .mockImplementationOnce(() => response(switched))
    vi.stubGlobal('fetch', fetchMock)
    const session = useSessionStore()
    session.applyAuth(systemResult)

    await session.loadTenants()
    await session.switchTenant('12')

    expect(session.tenants).toHaveLength(2)
    expect(session.context?.tenantId).toBe('12')
    expect(session.context?.permissionVersion).toBe('2')
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/context/tenants')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/context/tenants/12:switch')
  })
})
