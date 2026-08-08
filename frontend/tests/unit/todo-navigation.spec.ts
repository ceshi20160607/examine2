import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SystemLayout from '@/layouts/SystemLayout.vue'
import router from '@/router'
import { TODO_COUNTS_CHANGED_EVENT, todoApi } from '@/services/todo'
import { useSessionStore } from '@/stores/session'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
const routerActions = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))

vi.mock('vue-router', async (importOriginal) => {
  const original = await importOriginal<typeof import('vue-router')>()
  return {
    ...original,
    useRoute: () => route,
    useRouter: () => routerActions,
  }
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

describe('todo system navigation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    applySession()
    vi.spyOn(useSessionStore(), 'loadTenants').mockResolvedValue(undefined)
    vi.mocked(todoApi.counts).mockResolvedValue({
      openCount: 4,
      taskCount: 2,
      approvalCount: 2,
      todayCount: 1,
      overdueCount: 1,
    })
  })

  it('registers an authenticated system route without duplicating module permissions', () => {
    const todoRoute = router.getRoutes().find(candidate => candidate.name === 'system-todos')

    expect(todoRoute?.path).toBe('/systems/:systemId/todos')
    expect(todoRoute?.meta.requiredPermissions).toBeUndefined()
    expect(todoRoute?.meta.anyPermissions).toBeUndefined()
  })

  it('shows the top-shell entry and keeps its open badge current', async () => {
    const wrapper = mount(SystemLayout, {
      global: {
        stubs: {
          RouterLink: {
            props: ['to'],
            template: '<a :data-to="to"><slot /></a>',
          },
          RouterView: true,
          'a-badge': {
            props: ['count'],
            template: '<span class="badge"><slot /> {{ count }}</span>',
          },
          'a-dropdown': { template: '<div><slot /></div>' },
        },
      },
    })
    await flushPromises()

    const entry = wrapper.get('a[data-to="/systems/10/todos"]')
    expect(entry.text()).toContain('待办')
    expect(entry.text()).toContain('4')
    window.dispatchEvent(new CustomEvent(TODO_COUNTS_CHANGED_EVENT, {
      detail: { openCount: 2 },
    }))
    await wrapper.vm.$nextTick()
    expect(entry.text()).toContain('2')
  })
})
