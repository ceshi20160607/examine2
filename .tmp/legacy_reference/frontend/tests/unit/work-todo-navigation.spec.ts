import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PlatformLayout from '@/layouts/PlatformLayout.vue'
import SystemAdminLayout from '@/layouts/SystemAdminLayout.vue'
import router from '@/router'
import { useSessionStore } from '@/stores/session'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
vi.mock('vue-router', async importOriginal => {
  const original = await importOriginal<typeof import('vue-router')>()
  return { ...original, useRoute: () => route, useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }
})

const stubs = {
  RouterLink: { props: ['to'], template: '<a :data-to="typeof to === \'string\' ? to : to.path"><slot /></a>' },
  RouterView: true,
  'a-dropdown': { template: '<div><slot /></div>' },
}

function platformSession(permissions: string[]) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: {
      type: 'PLATFORM', account: { id: '1', username: 'member', displayName: 'Member' },
      permissionVersion: '1', permissions, shells: ['PLATFORM_RUNTIME'],

      roleIds: [],
      dataScope: null,
      restrictedMode: 'NONE',
    },
    systems: [],

    tenants: [],
    firstSystemId: null,
  })
}

function systemSession(permissions: string[]) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'admin', displayName: 'Admin' },
    context: {
      type: 'SYSTEM', account: { id: '1', username: 'admin', displayName: 'Admin' },
      systemId: '10', tenantId: '20', memberId: '30', permissionVersion: '1', permissions,
      shells: ['SYSTEM_ADMIN'],

      roleIds: ['legacy-role-1'],
      dataScope: { id: 'legacy-scope-1', code: 'legacy_all', kind: 'ALL' },
      restrictedMode: 'NONE',
    },
    systems: [], tenants: [],

    firstSystemId: null,
  })
}

describe('work configuration and platform Todo navigation', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('registers and exposes platform Todo only with read permission', () => {
    const record = router.getRoutes().find(item => item.name === 'platform-todos')
    expect(record?.path).toBe('/platform/todos')
    expect(record?.meta.requiredPermissions).toEqual(['platform.task.read'])

    platformSession(['platform.task.read'])
    expect(mount(PlatformLayout, { global: { stubs } }).get('a[data-to="/platform/todos"]').text()).toContain('待办')
    platformSession([])
    expect(mount(PlatformLayout, { global: { stubs } }).find('a[data-to="/platform/todos"]').exists()).toBe(false)
  })

  it('registers and exposes Work configuration only with manage permission', () => {
    const record = router.getRoutes().find(item => item.name === 'system-admin-work-configuration')
    expect(record?.path).toBe('/systems/:systemId/admin/work-configuration')
    expect(record?.meta.requiredPermissions).toEqual(['work.config.manage'])

    systemSession(['work.config.manage'])
    expect(mount(SystemAdminLayout, { global: { stubs } }).get('a[data-to="/systems/10/admin/work-configuration"]').text()).toContain('工作配置')
    systemSession([])
    expect(mount(SystemAdminLayout, { global: { stubs } }).find('a[data-to="/systems/10/admin/work-configuration"]').exists()).toBe(false)
  })
})
