import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PlatformAdminLayout from '@/layouts/PlatformAdminLayout.vue'
import router from '@/router'
import { useSessionStore } from '@/stores/session'

vi.mock('vue-router', async importOriginal => {
  const original = await importOriginal<typeof import('vue-router')>()
  return { ...original, useRouter: () => ({ replace: vi.fn() }) }
})

function session(permissions: string[]) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'owner', displayName: 'Owner' },
    context: { type: 'PLATFORM', account: { id: '1', username: 'owner', displayName: 'Owner' },
      permissionVersion: '1', permissions, shells: ['PLATFORM_ADMIN'] }, systems: [],
  })
}

const stubs = {
  RouterLink: { props: ['to'], template: '<a :data-to="to"><slot /></a>' }, RouterView: true,
}

describe('platform settings navigation', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('registers and displays the global settings entry only with its permission', () => {
    const route = router.getRoutes().find(item => item.name === 'platform-admin-global-settings')
    expect(route?.path).toBe('/platform/admin/global-settings')
    expect(route?.meta.requiredPermissions).toEqual(['platform.settings.manage'])
    session(['platform.settings.manage'])
    expect(mount(PlatformAdminLayout, { global: { stubs } })
      .find('a[data-to="/platform/admin/global-settings"]').exists()).toBe(true)
    session([])
    expect(mount(PlatformAdminLayout, { global: { stubs } })
      .find('a[data-to="/platform/admin/global-settings"]').exists()).toBe(false)
  })
})
