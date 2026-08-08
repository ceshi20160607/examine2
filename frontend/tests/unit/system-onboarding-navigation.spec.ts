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
    account: { id: '1', username: 'owner', displayName: 'Owner' },
    context: {
      type: 'SYSTEM', account: { id: '1', username: 'owner', displayName: 'Owner' },
      systemId: '10', tenantId: '20', memberId: '30', permissionVersion: '1', permissions,
      shells: ['SYSTEM_ADMIN'],
    },
    systems: [],
  })
}

function render() {
  return mount(SystemAdminLayout, { global: { stubs: {
    RouterLink: { props: ['to'], template: '<a :data-to="to"><slot /></a>' },
    RouterView: true,
  } } })
}

describe('system onboarding navigation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('registers one owner-only route and exposes it when all three existing authorities are present', () => {
    const routes = router.getRoutes().filter(candidate => candidate.name === 'system-admin-onboarding')
    expect(routes).toHaveLength(1)
    expect(routes[0]?.path).toBe('/systems/:systemId/admin/onboarding')
    expect(routes[0]?.meta.requiredPermissions).toEqual([
      'system.admin.access', 'module.config.manage', 'system.member.manage',
    ])

    applySession(['system.admin.access', 'module.config.manage', 'system.member.manage'])
    const entry = render().get('a[data-to="/systems/10/admin/onboarding"]')
    expect(entry.text()).toContain('上线引导')
  })

  it('does not expose a partial-authority onboarding link', () => {
    applySession(['system.admin.access', 'module.config.manage'])
    expect(render().find('a[data-to="/systems/10/admin/onboarding"]').exists()).toBe(false)
  })
})
