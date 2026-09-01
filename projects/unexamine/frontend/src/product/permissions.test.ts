import { describe, expect, it } from 'vitest'
import { allowsPermission, buildPlatformNavigation, hasPlatformManagementAccess, hasResourceAccess, hasRuntimeModuleAccess } from './permissions'

describe('system navigation permissions', () => {
  it('keeps wildcard administrators unrestricted', () => {
    const permissions = [{ resourceType: '*', resourceCode: '*', actionCode: '*' }]
    expect(allowsPermission(permissions, 'CONFIG', 'MODULE', 'MANAGE')).toBe(true)
    expect(allowsPermission(permissions, 'AUDIT', 'EVENT', 'VIEW')).toBe(true)
    expect(hasRuntimeModuleAccess(permissions)).toBe(true)
    expect(hasResourceAccess(permissions, 'FLOW')).toBe(true)
  })

  it('does not expose configuration or audit menus to a runtime reader', () => {
    const permissions = [
      { resourceType: 'MODULE', resourceCode: 'customer', actionCode: 'LIST' },
      { resourceType: 'MODULE', resourceCode: 'customer', actionCode: 'DETAIL' },
    ]
    expect(hasRuntimeModuleAccess(permissions)).toBe(true)
    expect(allowsPermission(permissions, 'CONFIG', 'MODULE', 'MANAGE')).toBe(false)
    expect(allowsPermission(permissions, 'AUDIT', 'EVENT', 'VIEW')).toBe(false)
    expect(hasResourceAccess(permissions, 'FLOW', 'APPLICATION')).toBe(false)
  })

  it('shows a platform capability when any permission belongs to that resource family', () => {
    const permissions = [{ resourceType: 'FLOW', resourceCode: 'approval', actionCode: 'VIEW' }]
    expect(hasResourceAccess(permissions, 'FLOW')).toBe(true)
    expect(hasResourceAccess(permissions, 'APPLICATION')).toBe(false)
  })

  it('treats the canonical platform super-admin grant as all platform capabilities', () => {
    const permissions = [{ resourceType: 'PLATFORM', resourceCode: '*', actionCode: '*' }]
    expect(allowsPermission(permissions, 'APPLICATION', 'PLATFORM', 'MANAGE')).toBe(true)
    expect(hasResourceAccess(permissions, 'FLOW', 'APPLICATION', 'TODO')).toBe(true)
    expect(buildPlatformNavigation(permissions).map(item => item.label)).toContain('应用')
  })

  it('builds the fixed platform shell in product order and hides unauthorized capability entries', () => {
    const wildcard = [{ resourceType: '*', resourceCode: '*', actionCode: '*' }]
    expect(buildPlatformNavigation(wildcard).map(item => item.label)).toEqual([
      '首页', '系统', '流程', '应用', '任务', '智能助手',
    ])
    const ordinary = [{ resourceType: 'MODULE', resourceCode: 'customer', actionCode: 'LIST' }]
    expect(buildPlatformNavigation(ordinary).map(item => item.label)).toEqual([
      '首页', '系统', '任务',
    ])
    const todoReader = [...ordinary, { resourceType: 'TODO', resourceCode: 'PLATFORM', actionCode: 'VIEW' }]
    expect(buildPlatformNavigation(todoReader).map(item => item.label)).not.toContain('待办')
    expect(hasPlatformManagementAccess(wildcard)).toBe(true)
    expect(hasPlatformManagementAccess(ordinary)).toBe(false)
    expect(buildPlatformNavigation(ordinary).every(item => Boolean(item.path))).toBe(true)
  })
})
