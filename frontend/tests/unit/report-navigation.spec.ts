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

function applySession(permissions: string[], shells: Array<'SYSTEM_RUNTIME' | 'SYSTEM_ADMIN'>) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: { type: 'SYSTEM', account: { id: '1', username: 'member', displayName: 'Member' }, systemId: '10', tenantId: '20', memberId: '30', permissionVersion: '1', permissions, shells },
    systems: [], tenants: [],
  })
}

const stubs = {
  RouterLink: { props: ['to'], template: '<a :data-to="typeof to === \'string\' ? to : to.path"><slot /></a>' }, RouterView: true,
  'a-badge': { template: '<span><slot /></span>' }, 'a-dropdown': { template: '<div><slot /></div>' },
}

describe('report admin and runtime navigation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(todoApi.counts).mockResolvedValue({ openCount: 0, taskCount: 0, approvalCount: 0, todayCount: 0, overdueCount: 0 })
  })

  it('registers an ordinary runtime route and top-level report entry', async () => {
    applySession([], ['SYSTEM_RUNTIME'])
    const routeRecord = router.getRoutes().find(item => item.name === 'system-reports')
    expect(routeRecord?.path).toBe('/systems/:systemId/reports/:reportCode?')
    expect(routeRecord?.meta.requiredPermissions).toBeUndefined()
    const wrapper = mount(SystemLayout, { global: { stubs } })
    await flushPromises()
    expect(wrapper.get('a[data-to="/systems/10/reports"]').text()).toContain('报表')
  })

  it('guards report editing with both configuration permissions', () => {
    const routeRecord = router.getRoutes().find(item => item.name === 'system-admin-reports')
    expect(routeRecord?.path).toBe('/systems/:systemId/admin/reports')
    expect(routeRecord?.meta.requiredPermissions).toEqual(['system.admin.access', 'module.config.manage'])
    applySession(['system.admin.access', 'module.config.manage'], ['SYSTEM_ADMIN'])
    expect(mount(SystemAdminLayout, { global: { stubs } }).findAll('a[data-to="/systems/10/admin/reports"]')).toHaveLength(1)
    applySession(['system.admin.access'], ['SYSTEM_ADMIN'])
    expect(mount(SystemAdminLayout, { global: { stubs } }).find('a[data-to="/systems/10/admin/reports"]').exists()).toBe(false)
  })
})
