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
    context: {
      type: 'SYSTEM', account: { id: '1', username: 'member', displayName: 'Member' },
      systemId: '10', tenantId: '20', memberId: '30', permissionVersion: '1', permissions, shells,
    },
    systems: [], tenants: [],
  })
}

const stubs = {
  RouterLink: { props: ['to'], template: '<a :data-to="typeof to === \'string\' ? to : to.path"><slot /></a>' },
  RouterView: true, 'a-badge': { template: '<span><slot /></span>' },
  'a-dropdown': { template: '<div><slot /></div>' },
}

describe('AI Agent navigation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(todoApi.counts).mockResolvedValue({
      openCount: 0, taskCount: 0, approvalCount: 0, todayCount: 0, overdueCount: 0,
    })
  })

  it('registers and permission-guards the runtime Agent route and navigation', async () => {
    const routeRecord = router.getRoutes().find(item => item.name === 'system-agent')
    expect(routeRecord?.path).toBe('/systems/:systemId/agent')
    expect(routeRecord?.meta.requiredPermissions).toEqual(['ai.agent.use'])

    applySession(['ai.agent.use'], ['SYSTEM_RUNTIME'])
    const allowed = mount(SystemLayout, { global: { stubs } })
    await flushPromises()
    expect(allowed.get('a[data-to="/systems/10/agent"]').text()).toContain('Agent')

    applySession([], ['SYSTEM_RUNTIME'])
    const denied = mount(SystemLayout, { global: { stubs } })
    await flushPromises()
    expect(denied.find('a[data-to="/systems/10/agent"]').exists()).toBe(false)
  })

  it('registers and permission-guards the system-admin Agent configuration', () => {
    const routeRecord = router.getRoutes().find(item => item.name === 'system-admin-agent')
    expect(routeRecord?.path).toBe('/systems/:systemId/admin/agent')
    expect(routeRecord?.meta.requiredPermissions).toEqual(['ai.policy.manage'])

    applySession(['ai.policy.manage'], ['SYSTEM_ADMIN'])
    expect(mount(SystemAdminLayout, { global: { stubs } })
      .get('a[data-to="/systems/10/admin/agent"]').text()).toContain('Agent 配置')
    applySession([], ['SYSTEM_ADMIN'])
    expect(mount(SystemAdminLayout, { global: { stubs } })
      .find('a[data-to="/systems/10/admin/agent"]').exists()).toBe(false)
  })
})
