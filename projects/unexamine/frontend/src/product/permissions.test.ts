import { describe, expect, it } from 'vitest'
import { allowsPermission, hasRuntimeModuleAccess } from './permissions'

describe('system navigation permissions', () => {
  it('keeps wildcard administrators unrestricted', () => {
    const permissions = [{ resourceType: '*', resourceCode: '*', actionCode: '*' }]
    expect(allowsPermission(permissions, 'CONFIG', 'MODULE', 'MANAGE')).toBe(true)
    expect(allowsPermission(permissions, 'AUDIT', 'EVENT', 'VIEW')).toBe(true)
    expect(hasRuntimeModuleAccess(permissions)).toBe(true)
  })

  it('does not expose configuration or audit menus to a runtime reader', () => {
    const permissions = [
      { resourceType: 'MODULE', resourceCode: 'customer', actionCode: 'LIST' },
      { resourceType: 'MODULE', resourceCode: 'customer', actionCode: 'DETAIL' },
    ]
    expect(hasRuntimeModuleAccess(permissions)).toBe(true)
    expect(allowsPermission(permissions, 'CONFIG', 'MODULE', 'MANAGE')).toBe(false)
    expect(allowsPermission(permissions, 'AUDIT', 'EVENT', 'VIEW')).toBe(false)
  })
})
