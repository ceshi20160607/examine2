import { describe, expect, it } from 'vitest'

import { canAccess, hasPermission, hasShell } from '@/router/access'
import type { SessionContext } from '@/types/session'

const context: SessionContext = {
  type: 'PLATFORM',
  account: { id: 'account-1', username: 'ordinary-name', displayName: '普通管理员' },
  permissionVersion: '9',
  permissions: ['platform.admin.access', 'platform.organization.manage'],
  shells: ['PLATFORM_RUNTIME', 'PLATFORM_ADMIN'],
}

describe('route access policy', () => {
  it('uses server shells and permissions without inspecting account identity', () => {
    expect(hasShell(context, 'PLATFORM_ADMIN')).toBe(true)
    expect(hasPermission(context, 'platform.organization.manage')).toBe(true)
    expect(canAccess(context, {
      shell: 'PLATFORM_ADMIN',
      permissions: ['platform.organization.manage'],
    })).toBe(true)
  })

  it('denies a page when its action permission is absent', () => {
    expect(canAccess(context, {
      shell: 'PLATFORM_ADMIN',
      permissions: ['platform.system.manage'],
    })).toBe(false)
  })

  it('supports system organization pages with either organization or member permission', () => {
    const systemContext: SessionContext = {
      ...context,
      type: 'SYSTEM',
      systemId: 'system-1',
      permissions: ['system.admin.access', 'system.member.manage'],
      shells: ['SYSTEM_RUNTIME', 'SYSTEM_ADMIN'],
    }
    expect(canAccess(systemContext, {
      shell: 'SYSTEM_ADMIN',
      anyPermissions: ['system.organization.manage', 'system.member.manage'],
    })).toBe(true)
  })
})
