import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PlatformLayout from '@/layouts/PlatformLayout.vue'
import SystemLayout from '@/layouts/SystemLayout.vue'
import { todoApi } from '@/services/todo'
import { useSessionStore } from '@/stores/session'
import type { AuthResult } from '@/types/session'

const routerPush = vi.hoisted(() => vi.fn())
const routerReplace = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push: routerPush, replace: routerReplace }),
}))
vi.mock('@/services/todo', () => ({
  TODO_COUNTS_CHANGED_EVENT: 'todo-counts-changed',
  todoApi: { counts: vi.fn() },
}))

const stubs = {
  RouterLink: { template: '<a><slot /></a>' },
  RouterView: true,
  Blocks: true,
  Bot: true,
  Check: true,
  ChevronDown: true,
  FileText: true,
  Gauge: true,
  LayoutDashboard: true,
  LogOut: true,
  RotateCcw: true,
  Settings: true,
  Settings2: true,
  ShieldCheck: true,
  UserRound: true,
  AccountSecurityDialog: {
    props: ['open'],
    emits: ['update:open'],
    template: '<div class="account-security-dialog-stub" :data-open="String(open)" />',
  },
  'a-dropdown': { template: '<div><slot /><slot name="overlay" /></div>' },
  'a-menu': { template: '<div><slot /></div>' },
  'a-menu-item': {
    inheritAttrs: false,
    emits: ['click'],
    template: '<button v-bind="$attrs" type="button" @click="$emit(\'click\')"><slot /></button>',
  },
  'a-menu-divider': true,
  'a-badge': { template: '<span><slot /></span>' },
}

function auth(type: 'PLATFORM' | 'SYSTEM'): AuthResult {
  return {
    account: { id: 'account-1', username: 'owner', displayName: 'Owner' },
    context: type === 'PLATFORM'
      ? {
          type: 'PLATFORM', account: { id: 'account-1', username: 'owner', displayName: 'Owner' },
          permissionVersion: '1', permissions: [], shells: ['PLATFORM_RUNTIME'],
        }
      : {
          type: 'SYSTEM', account: { id: 'account-1', username: 'owner', displayName: 'Owner' },
          systemId: '10', systemName: 'Demo', tenantId: 'tenant-1', tenantName: 'Default',
          permissionVersion: '1', permissions: [], shells: ['SYSTEM_RUNTIME'],
        },
    systems: [],
    tenants: type === 'SYSTEM'
      ? [{ id: 'tenant-1', code: 'default', name: 'Default', status: 'ACTIVE', isDefault: true }]
      : undefined,
  }
}

function renderLayout(component: typeof PlatformLayout | typeof SystemLayout, type: 'PLATFORM' | 'SYSTEM') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const session = useSessionStore()
  session.applyAuth(auth(type))
  vi.spyOn(session, 'loadTenants').mockResolvedValue(undefined)
  return mount(component, { global: { plugins: [pinia], stubs } })
}

describe('account security navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(todoApi.counts).mockResolvedValue({ openCount: 0, taskCount: 0, approvalCount: 0, todayCount: 0, overdueCount: 0 })
  })

  it.each([
    ['Platform', PlatformLayout, 'PLATFORM'],
    ['System', SystemLayout, 'SYSTEM'],
  ] as const)('opens the one shared dialog from the %s personal menu', async (_name, component, type) => {
    const wrapper = renderLayout(component, type)
    await flushPromises()

    expect(wrapper.findAll('.account-security-menu-trigger')).toHaveLength(1)
    expect(wrapper.get('.account-security-dialog-stub').attributes('data-open')).toBe('false')
    await wrapper.get('.account-security-menu-trigger').trigger('click')
    expect(wrapper.get('.account-security-dialog-stub').attributes('data-open')).toBe('true')
  })
})
