import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SystemLayout from '@/layouts/SystemLayout.vue'
import router from '@/router'
import { todoApi } from '@/services/todo'
import { useSessionStore } from '@/stores/session'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
const routerActions = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))

vi.mock('vue-router', async (importOriginal) => {
  const original = await importOriginal<typeof import('vue-router')>()
  return { ...original, useRoute: () => route, useRouter: () => routerActions }
})

vi.mock('@/services/todo', () => ({
  TODO_COUNTS_CHANGED_EVENT: 'examine:todo-counts-changed',
  todoApi: { counts: vi.fn() },
}))

function applySession() {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: {
      type: 'SYSTEM',
      account: { id: '1', username: 'member', displayName: 'Member' },
      systemId: '10',
      systemName: 'Demo',
      tenantId: '20',
      tenantName: 'Default',
      memberId: '30',
      permissionVersion: '1',
      permissions: [],
      shells: ['SYSTEM_RUNTIME'],
    },
    systems: [],
    tenants: [{ id: '20', code: 'default', name: 'Default', status: 'ACTIVE', isDefault: true }],
  })
}

describe('operations dashboard navigation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    applySession()
    vi.spyOn(useSessionStore(), 'loadTenants').mockResolvedValue(undefined)
    vi.mocked(todoApi.counts).mockResolvedValue({
      openCount: 0, taskCount: 0, approvalCount: 0, todayCount: 0, overdueCount: 0,
    })
  })

  it('registers one permission-safe system route and top-shell entry', async () => {
    const operationsRoute = router.getRoutes().find(candidate => candidate.name === 'system-operations')
    expect(operationsRoute?.path).toBe('/systems/:systemId/operations')
    expect(operationsRoute?.meta.requiredPermissions).toBeUndefined()

    const wrapper = mount(SystemLayout, {
      global: {
        stubs: {
          RouterLink: {
            props: ['to'],
            template: '<a :data-to="to"><slot /></a>',
          },
          RouterView: true,
          'a-badge': { template: '<span><slot /></span>' },
          'a-dropdown': { template: '<div><slot /></div>' },
        },
      },
    })
    await flushPromises()
    const entry = wrapper.get('a[data-to="/systems/10/operations"]')
    expect(entry.text()).toContain('运营')
    expect(wrapper.findAll('a[data-to="/systems/10/operations"]')).toHaveLength(1)
  })
})
