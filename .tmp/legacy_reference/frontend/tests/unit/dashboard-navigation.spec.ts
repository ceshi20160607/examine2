import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SystemAdminLayout from '@/layouts/SystemAdminLayout.vue'
import SystemLayout from '@/layouts/SystemLayout.vue'
import router from '@/router'
import { todoApi } from '@/services/todo'
import { useSessionStore } from '@/stores/session'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
vi.mock('vue-router', async importOriginal => {
  const original = await importOriginal<typeof import('vue-router')>()
  return { ...original, useRoute: () => route, useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }
})
vi.mock('@/services/todo', () => ({ TODO_COUNTS_CHANGED_EVENT: 'todo-change', todoApi: { counts: vi.fn() } }))

function applySession(
  permissions: string[],
  shells: Array<'SYSTEM_RUNTIME' | 'SYSTEM_ADMIN'> = ['SYSTEM_RUNTIME', 'SYSTEM_ADMIN'],
) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: { type: 'SYSTEM', account: { id: '1', username: 'member', displayName: 'Member' }, systemId: '10', tenantId: '20', memberId: '30', permissionVersion: '1', permissions, shells , roleIds: ['legacy-role-1'], dataScope: { id: 'legacy-scope-1', code: 'legacy_all', kind: 'ALL' }, restrictedMode: 'NONE'},
    systems: [], tenants: [],

    firstSystemId: null,
  })
}

const stubs = {
  RouterLink: { props: ['to'], template: '<a :data-to="to"><slot /></a>' }, RouterView: true,
  'a-badge': { template: '<span><slot /></span>' }, 'a-dropdown': { template: '<div><slot /></div>' },
}

describe('dashboard admin and runtime navigation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(todoApi.counts).mockResolvedValue({ openCount: 0, taskCount: 0, approvalCount: 0, todayCount: 0, overdueCount: 0 })
  })

  it('registers one public runtime system route and top navigation entry', async () => {
    applySession([])
    const routes = router.getRoutes().filter(item => item.name === 'system-dashboard')
    expect(routes).toHaveLength(1)
    expect(routes[0]?.path).toBe('/systems/:systemId/dashboard')
    const wrapper = mount(SystemLayout, { global: { stubs } })
    await flushPromises()
    expect(wrapper.findAll('a[data-to="/systems/10/dashboard"]')).toHaveLength(1)
    expect(wrapper.get('a[data-to="/systems/10/dashboard"]').text()).toContain('仪表盘')
  })

  it('guards the unique editor route and entry with both administration permissions', () => {
    const routes = router.getRoutes().filter(item => item.name === 'system-admin-dashboards')
    expect(routes).toHaveLength(1)
    expect(routes[0]?.path).toBe('/systems/:systemId/admin/dashboards')
    expect(routes[0]?.meta.requiredPermissions).toEqual(['system.admin.access', 'module.config.manage'])
    applySession(['system.admin.access', 'module.config.manage'])
    expect(mount(SystemAdminLayout, { global: { stubs } }).findAll('a[data-to="/systems/10/admin/dashboards"]')).toHaveLength(1)
    applySession(['system.admin.access'])
    expect(mount(SystemAdminLayout, { global: { stubs } }).find('a[data-to="/systems/10/admin/dashboards"]').exists()).toBe(false)
  })
})
