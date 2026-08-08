import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PlatformAdminLayout from '@/layouts/PlatformAdminLayout.vue'
import SystemAdminLayout from '@/layouts/SystemAdminLayout.vue'
import router from '@/router'
import { useSessionStore } from '@/stores/session'

vi.mock('vue-router', async importOriginal => {
  const original = await importOriginal<typeof import('vue-router')>()
  return {
    ...original,
    useRoute: () => ({ params: { systemId: '41' } }),
    useRouter: () => ({ replace: vi.fn() }),
  }
})

const stubs = {
  RouterLink: { props: ['to'], template: '<a :data-to="typeof to === \'string\' ? to : to.path"><slot /></a>' },
  RouterView: true,
}

function platform(permissions: string[]) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'owner', displayName: 'Owner' },
    context: { type: 'PLATFORM', account: { id: '1', username: 'owner', displayName: 'Owner' }, permissionVersion: '1', permissions, shells: ['PLATFORM_ADMIN'] },
    systems: [],
  })
}

function system(permissions: string[]) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'owner', displayName: 'Owner' },
    context: { type: 'SYSTEM', systemId: '41', systemName: 'S', tenantId: '73', tenantName: 'T', memberId: '2', account: { id: '1', username: 'owner', displayName: 'Owner' }, permissionVersion: '1', permissions, shells: ['SYSTEM_ADMIN'] },
    systems: [],
  })
}

describe('unified audit navigation', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('registers independent platform and system permissions', () => {
    expect(router.getRoutes().find(item => item.name === 'platform-admin-audit-logs')?.meta.requiredPermissions)
      .toEqual(['platform.audit.view'])
    expect(router.getRoutes().find(item => item.name === 'system-admin-audit-logs')?.meta.requiredPermissions)
      .toEqual(['system.audit.view'])
  })

  it('shows each entry only when its scoped audit permission exists', () => {
    platform(['platform.audit.view'])
    expect(mount(PlatformAdminLayout, { global: { stubs } }).find('a[data-to="/platform/admin/audit-logs"]').exists()).toBe(true)
    platform([])
    expect(mount(PlatformAdminLayout, { global: { stubs } }).find('a[data-to="/platform/admin/audit-logs"]').exists()).toBe(false)

    system(['system.audit.view'])
    expect(mount(SystemAdminLayout, { global: { stubs } }).find('a[data-to="/systems/41/admin/audit-logs"]').exists()).toBe(true)
    system([])
    expect(mount(SystemAdminLayout, { global: { stubs } }).find('a[data-to="/systems/41/admin/audit-logs"]').exists()).toBe(false)
  })
})
