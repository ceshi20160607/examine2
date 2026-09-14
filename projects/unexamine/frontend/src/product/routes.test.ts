import { afterEach, describe, expect, it } from 'vitest'
import { defaultEntryPath, productRoutes, unavailableRoute } from './routes'
import { platformContext, platformTokens, systemContext, systemTokens } from './session'
import type { CurrentContext, SessionTokens } from './session'

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
  platformTokens.value = null
  platformContext.value = null
  systemTokens.value = null
  systemContext.value = null
})

describe('product route guards', () => {
  it('redirects unauthenticated direct platform access to login', () => {
    expect(runGuard('platform-dashboard')).toEqual({ path: '/login' })
  })

  it('allows only permission-backed Flow entry', () => {
    platformTokens.value = tokens
    platformContext.value = context([{ resourceType: 'MODULE', resourceCode: 'customer', actionCode: 'LIST' }])
    expect(runGuard('platform-flows')).toEqual({ path: '/platform/dashboard', query: { notice: 'access-denied' } })
    platformContext.value = context([{ resourceType: 'FLOW', resourceCode: 'approval', actionCode: 'VIEW' }])
    expect(runGuard('platform-flows')).toBe(true)
  })

  it('keeps platform todos permission-backed and isolated from a system token', () => {
    platformTokens.value = tokens
    platformContext.value = context([{ resourceType: 'MODULE', resourceCode: 'customer', actionCode: 'LIST' }])
    expect(runGuard('platform-todos')).toEqual({ path: '/platform/dashboard', query: { notice: 'access-denied' } })
    platformContext.value = context([{ resourceType: 'TODO', resourceCode: 'PLATFORM', actionCode: 'VIEW' }])
    expect(runGuard('platform-todos')).toBe(true)
  })

  it('uses an application permission for the real platform application page', () => {
    platformTokens.value = tokens
    platformContext.value = context([{ resourceType: 'MODULE', resourceCode: 'customer', actionCode: 'LIST' }])
    expect(runGuard('platform-applications')).toEqual({ path: '/platform/dashboard', query: { notice: 'access-denied' } })
    platformContext.value = context([{ resourceType: 'APPLICATION', resourceCode: 'PLATFORM', actionCode: 'VIEW' }])
    expect(runGuard('platform-applications')).toBe(true)
    expect(productRoutes.find(item => item.name === 'platform-applications')?.component).toBeTruthy()
  })

  it('rejects a stale or different system context', () => {
    systemTokens.value = tokens
    systemContext.value = context([{ resourceType: '*', resourceCode: '*', actionCode: '*' }], 7)
    expect(runGuard('system-home', { systemId: '8' })).toEqual({ path: '/platform/dashboard', query: { notice: 'system-context-changed' } })
    expect(runGuard('system-home', { systemId: '7' })).toBe(true)
  })

  it('keeps the system backend on a separate permission-guarded route', () => {
    systemTokens.value = tokens
    systemContext.value = context([{ resourceType: 'MODULE', resourceCode: 'customer', actionCode: 'LIST' }], 7)
    expect(runGuard('system-admin', { systemId: '7' })).toEqual({ path: '/systems/7', query: { notice: 'access-denied' } })

    systemContext.value = context([{ resourceType: 'CONFIG', resourceCode: 'MODULE', actionCode: 'MANAGE' }], 7)
    expect(runGuard('system-admin', { systemId: '7' })).toBe(true)
    expect(runGuard('system-admin', { systemId: '8' })).toEqual({ path: '/platform/dashboard', query: { notice: 'system-context-changed' } })

    systemContext.value = context([{ resourceType: 'APPLICATION', resourceCode: 'SYSTEM', actionCode: 'VIEW' }], 7)
    expect(runGuard('system-admin', { systemId: '7' })).toBe(true)
  })

  it('opens the platform home from root when authenticated and keeps invalid paths in the right workspace', () => {
    expect(defaultEntryPath()).toBe('/login')
    platformTokens.value = tokens
    platformContext.value = context([{ resourceType: '*', resourceCode: '*', actionCode: '*' }])
    expect(defaultEntryPath()).toBe('/platform/dashboard')
    expect(unavailableRoute({ path: '/missing', params: {} })).toEqual({ path: '/platform/dashboard', query: { notice: 'route-unavailable' } })

    systemTokens.value = tokens
    systemContext.value = context([{ resourceType: '*', resourceCode: '*', actionCode: '*' }], 7)
    expect(unavailableRoute({ path: '/systems/7/missing', params: {} })).toEqual({ path: '/systems/7', query: { notice: 'route-unavailable' } })
  })

  it('does not expose runtime tasks or unrelated administration through a different permission', () => {
    platformTokens.value = tokens
    platformContext.value = context([{ resourceType: 'WORK', resourceCode: 'PLATFORM', actionCode: 'CONFIGURE' }])
    expect(runGuard('platform-tasks')).toEqual({ path: '/platform/dashboard', query: { notice: 'access-denied' } })
    expect(runGuard('platform-work-configurations')).toBe(true)
    expect(runGuard('platform-flow-configurations')).toEqual({ path: '/platform/dashboard', query: { notice: 'access-denied' } })
  })
})
