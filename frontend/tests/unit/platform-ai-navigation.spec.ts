import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PlatformAdminLayout from '@/layouts/PlatformAdminLayout.vue'
import PlatformLayout from '@/layouts/PlatformLayout.vue'
import router from '@/router'
import { useSessionStore } from '@/stores/session'

vi.mock('vue-router', async importOriginal => {
  const original = await importOriginal<typeof import('vue-router')>()
  return { ...original, useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }
})

function applySession(permissions: string[], shells: Array<'PLATFORM_RUNTIME' | 'PLATFORM_ADMIN'>) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: {
      type: 'PLATFORM', account: { id: '1', username: 'member', displayName: 'Member' },
      permissionVersion: '1', permissions, shells,
    },
    systems: [],
  })
}

const stubs = {
  RouterLink: { props: ['to'], template: '<a :data-to="typeof to === \'string\' ? to : to.path"><slot /></a>' },
  RouterView: true, 'a-dropdown': { template: '<div><slot /></div>' },
}

describe('platform AI navigation', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('requires platform runtime access and Agent use permission', () => {
    const routeRecord = router.getRoutes().find(item => item.name === 'platform-agent')
    expect(routeRecord?.path).toBe('/platform/agent')
    expect(routeRecord?.meta.requiredPermissions).toEqual(['platform.runtime.access', 'platform.ai.agent.use'])

    applySession(['platform.runtime.access', 'platform.ai.agent.use'], ['PLATFORM_RUNTIME'])
    expect(mount(PlatformLayout, { global: { stubs } }).get('a[data-to="/platform/agent"]').text()).toContain('Agent')
    applySession(['platform.ai.agent.use'], ['PLATFORM_RUNTIME'])
    expect(mount(PlatformLayout, { global: { stubs } }).find('a[data-to="/platform/agent"]').exists()).toBe(false)
  })

  it('registers the independently permissioned platform admin policy page', () => {
    const routeRecord = router.getRoutes().find(item => item.name === 'platform-admin-agent')
    expect(routeRecord?.path).toBe('/platform/admin/agent')
    expect(routeRecord?.meta.requiredPermissions).toEqual(['platform.ai.policy.manage'])

    applySession(['platform.ai.policy.manage'], ['PLATFORM_ADMIN'])
    expect(mount(PlatformAdminLayout, { global: { stubs } }).get('a[data-to="/platform/admin/agent"]').text()).toContain('Agent 配置')
    applySession([], ['PLATFORM_ADMIN'])
    expect(mount(PlatformAdminLayout, { global: { stubs } }).find('a[data-to="/platform/admin/agent"]').exists()).toBe(false)
  })
})
