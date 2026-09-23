import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defaultEntryPath, productRoutes, unavailableRoute } from './routes'
import { platformContext, platformTokens, systemContext, systemTokens } from './session'
import type { CurrentContext, SessionTokens } from './session'
import { api } from './api'
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), api: vi.fn() }))
beforeEach(() => {
  // 本文件在Node环境执行，提供会话存储夹具，校验授权刷新而非依赖浏览器全局。
  vi.stubGlobal('sessionStorage', { setItem: vi.fn(), removeItem: vi.fn(), getItem: vi.fn() })
  vi.mocked(api).mockReset().mockImplementation(async () => platformContext.value)
})

const tokens: SessionTokens = {
  accessToken: 'access', refreshToken: 'refresh',
  accessExpiresAt: '2099-01-01T00:00:00', refreshExpiresAt: '2099-01-02T00:00:00',
}
const context = (permissions: CurrentContext['permissions'], systemId: number | null = null): CurrentContext => ({
  accountId: 1, platformId: 1, systemId, tenantId: systemId, memberId: systemId,
  tenantMemberId: systemId, username: 'tester', displayName: '测试用户', mfaLevel: 'NONE',
  roleIds: [], permissions,
})

function runGuard(name: string, params: Record<string, string> = {}) {
  const route = productRoutes.find(item => item.name === name)
  expect(route?.beforeEnter).toBeTypeOf('function')
  return (route?.beforeEnter as Function)({ params }, {}, () => undefined)
}

afterEach(() => {
  vi.unstubAllGlobals()
  platformTokens.value = null
  platformContext.value = null
  systemTokens.value = null
  systemContext.value = null
})

describe('product route guards', () => {
  it('refreshes stale platform grants and blocks revoked grants while preserving the system workspace', async () => {
    platformTokens.value = tokens
    platformContext.value = context([])
    systemContext.value = context([], 7)
    const granted = context([{ resourceType: 'FLOW', resourceCode: 'PLATFORM', actionCode: 'VIEW_RUNTIME' }])
    vi.mocked(api).mockResolvedValueOnce(granted)
    expect(await runGuard('platform-flows')).toBe(true)
    expect(platformContext.value?.permissions).toEqual(granted.permissions)
    expect(systemContext.value?.systemId).toBe(7)
    vi.mocked(api).mockResolvedValueOnce(context([]))
    expect(await runGuard('platform-flows')).toEqual({ path: '/platform/dashboard', query: { notice: 'access-denied' } })
  })

  it('discards a delayed authorization response after switching accounts', async () => {
    platformTokens.value = tokens
    platformContext.value = context([])
    let resolve!: (value: CurrentContext) => void
    vi.mocked(api).mockReturnValueOnce(new Promise<CurrentContext>(done => { resolve = done }))
    const navigation = runGuard('platform-flows')
    platformContext.value = { ...context([]), accountId: 2 }
    resolve(context([{ resourceType: '*', resourceCode: '*', actionCode: '*' }]))
    expect(await navigation).toBe(false)
    expect(platformContext.value.accountId).toBe(2)
    expect(platformContext.value.permissions).toEqual([])
  })

  it('redirects unauthenticated direct platform access to login', async () => {
    expect(await runGuard('platform-dashboard')).toEqual({ path: '/login' })
  })

  it('allows only permission-backed Flow entry', async () => {
    platformTokens.value = tokens
    platformContext.value = context([{ resourceType: 'MODULE', resourceCode: 'customer', actionCode: 'LIST' }])
    expect(await runGuard('platform-flows')).toEqual({ path: '/platform/dashboard', query: { notice: 'access-denied' } })
    platformContext.value = context([{ resourceType: 'FLOW', resourceCode: 'approval', actionCode: 'VIEW' }])
    expect(await runGuard('platform-flows')).toBe(true)
  })

  it('keeps platform todos permission-backed and isolated from a system token', async () => {
    platformTokens.value = tokens
    platformContext.value = context([{ resourceType: 'MODULE', resourceCode: 'customer', actionCode: 'LIST' }])
    expect(await runGuard('platform-todos')).toEqual({ path: '/platform/dashboard', query: { notice: 'access-denied' } })
    platformContext.value = context([{ resourceType: 'TODO', resourceCode: 'PLATFORM', actionCode: 'VIEW' }])
    expect(await runGuard('platform-todos')).toBe(true)
  })

  it('uses an application permission for the real platform application page', async () => {
    platformTokens.value = tokens
    platformContext.value = context([{ resourceType: 'MODULE', resourceCode: 'customer', actionCode: 'LIST' }])
    expect(await runGuard('platform-applications')).toEqual({ path: '/platform/dashboard', query: { notice: 'access-denied' } })
    platformContext.value = context([{ resourceType: 'APPLICATION', resourceCode: 'PLATFORM', actionCode: 'VIEW' }])
    expect(await runGuard('platform-applications')).toBe(true)
    expect(productRoutes.find(item => item.name === 'platform-applications')?.component).toBeTruthy()
  })

  it('rejects a stale or different system context', async () => {
    systemTokens.value = tokens
    systemContext.value = context([{ resourceType: '*', resourceCode: '*', actionCode: '*' }], 7)
    expect(await runGuard('system-home', { systemId: '8' })).toEqual({ path: '/platform/dashboard', query: { notice: 'system-context-changed' } })
    expect(await runGuard('system-home', { systemId: '7' })).toBe(true)
  })

  it('keeps the system backend on a separate permission-guarded route', async () => {
    systemTokens.value = tokens
    systemContext.value = context([{ resourceType: 'MODULE', resourceCode: 'customer', actionCode: 'LIST' }], 7)
    expect(await runGuard('system-admin', { systemId: '7' })).toEqual({ path: '/systems/7', query: { notice: 'access-denied' } })

    systemContext.value = context([{ resourceType: 'CONFIG', resourceCode: 'MODULE', actionCode: 'MANAGE' }], 7)
    expect(await runGuard('system-admin', { systemId: '7' })).toBe(true)
    expect(await runGuard('system-admin', { systemId: '8' })).toEqual({ path: '/platform/dashboard', query: { notice: 'system-context-changed' } })

    systemContext.value = context([{ resourceType: 'APPLICATION', resourceCode: 'SYSTEM', actionCode: 'VIEW' }], 7)
    expect(await runGuard('system-admin', { systemId: '7' })).toBe(true)
  })

  it('opens the platform home from root when authenticated and keeps invalid paths in the right workspace', async () => {
    expect(defaultEntryPath()).toBe('/login')
    platformTokens.value = tokens
    platformContext.value = context([{ resourceType: '*', resourceCode: '*', actionCode: '*' }])
    expect(defaultEntryPath()).toBe('/platform/dashboard')
    expect(unavailableRoute({ path: '/missing', params: {} })).toEqual({ path: '/platform/dashboard', query: { notice: 'route-unavailable' } })

    systemTokens.value = tokens
    systemContext.value = context([{ resourceType: '*', resourceCode: '*', actionCode: '*' }], 7)
    expect(unavailableRoute({ path: '/systems/7/missing', params: {} })).toEqual({ path: '/systems/7', query: { notice: 'route-unavailable' } })
  })

  it('does not expose runtime tasks or unrelated administration through a different permission', async () => {
    platformTokens.value = tokens
    platformContext.value = context([{ resourceType: 'WORK', resourceCode: 'PLATFORM', actionCode: 'CONFIGURE' }])
    expect(await runGuard('platform-tasks')).toEqual({ path: '/platform/dashboard', query: { notice: 'access-denied' } })
    expect(await runGuard('platform-work-configurations')).toBe(true)
    expect(await runGuard('platform-flow-configurations')).toEqual({ path: '/platform/dashboard', query: { notice: 'access-denied' } })
  })
})
