import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SystemAdminLayout from '@/layouts/SystemAdminLayout.vue'
import router from '@/router'
import { useSessionStore } from '@/stores/session'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
vi.mock('vue-router', async (importOriginal) => {
  const original = await importOriginal<typeof import('vue-router')>()
  return { ...original, useRoute: () => route, useRouter: () => ({ replace: vi.fn() }) }
})

function applySession(permissions: string[]) {
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
    systems: [],

    tenants: [],
    firstSystemId: null,
  })
}

function render() {
  return mount(SystemAdminLayout, {
    global: {
      stubs: {
        RouterLink: { props: ['to'], template: '<a :data-to="to"><slot /></a>' },
        RouterView: true,
      },
    },
  })
}

describe('data source system-admin navigation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('registers exactly one system-admin route guarded by existing module administration authority', () => {
    const dataSourceRoutes = router.getRoutes().filter((item) => item.name === 'system-admin-data-sources')
    expect(dataSourceRoutes).toHaveLength(1)
    expect(dataSourceRoutes[0]?.path).toBe('/systems/:systemId/admin/data-sources')
    expect(dataSourceRoutes[0]?.meta.requiredPermissions).toEqual([
      'system.admin.access', 'module.config.manage',
    ])

    applySession(['system.admin.access', 'module.config.manage'])
    const wrapper = render()
    const entries = wrapper.findAll('a[data-to="/systems/10/admin/data-sources"]')
    expect(entries).toHaveLength(1)
    expect(entries[0]?.text()).toContain('数据源')
  })

  it('does not show the entry without module administration authority', () => {
    applySession(['system.admin.access'])
    expect(render().find('a[data-to="/systems/10/admin/data-sources"]').exists()).toBe(false)
  })
})
