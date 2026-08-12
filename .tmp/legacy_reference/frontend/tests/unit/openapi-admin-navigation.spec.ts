import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SystemAdminLayout from '@/layouts/SystemAdminLayout.vue'
import { useSessionStore } from '@/stores/session'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
const router = vi.hoisted(() => ({ replace: vi.fn() }))

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

function applySession(permissions: string[]) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'admin', displayName: 'Admin' },
    context: {
      type: 'SYSTEM',
      account: { id: '1', username: 'admin', displayName: 'Admin' },
      systemId: '10',
      systemName: 'Demo',
      tenantId: '20',
      tenantName: 'Default',
      memberId: '30',
      permissionVersion: '1',
      permissions,
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
        Blocks: true,
        Building2: true,
        ChevronLeft: true,
        KeyRound: true,
        LogOut: true,
        Settings2: true,
        ShieldCheck: true,
        UsersRound: true,
        RouterLink: {
          props: ['to'],
          template: '<a :data-to="to"><slot /></a>',
        },
        RouterView: true,
      },
    },
  })
}

describe('system administration OpenAPI navigation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows the application entry with the manage permission', () => {
    applySession(['openapi.application.manage'])
    const wrapper = render()

    const entry = wrapper.find('a[data-to="/systems/10/admin/openapi-applications"]')
    expect(entry.exists()).toBe(true)
    expect(entry.text()).toContain('开放应用')
  })

  it('hides the application entry without the manage permission', () => {
    applySession([])
    const wrapper = render()

    expect(wrapper.find('a[data-to="/systems/10/admin/openapi-applications"]').exists()).toBe(false)
  })
})
