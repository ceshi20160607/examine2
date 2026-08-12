import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useSessionStore } from '@/stores/session'
import AdminIndexView from '@/views/admin/AdminIndexView.vue'

const routerReplace = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ replace: routerReplace }),
}))

function render(zone: 'platform' | 'system', permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const session = useSessionStore()
  session.$patch({
    context: {
      type: 'PLATFORM',
      account: { id: 'account-1', username: 'owner', displayName: 'Owner' },
      permissionVersion: '1',
      permissions,
      shells: ['PLATFORM_ADMIN'],
    },
    initialized: true,
  })
  const wrapper = mount(AdminIndexView, {
    props: { zone },
    global: { plugins: [pinia], stubs: { ShieldX: true } },
  })
  return { session, wrapper }
}

describe('AdminIndexView precise fallback', () => {
  beforeEach(() => vi.clearAllMocks())

  it.each([
    ['platform.system.manage', '/platform/admin/systems'],
    ['platform.organization.manage', '/platform/admin/organization'],
    ['platform.role.manage', '/platform/admin/roles'],
    ['platform.ai.policy.manage', '/platform/admin/agent'],
  ])('maps the isolated platform permission %s', async (permission, path) => {
    render('platform', [permission])
    await flushPromises()
    expect(routerReplace).toHaveBeenCalledWith(path)
  })

  it.each([
    ['system.settings.manage', '/systems/10/admin/settings'],
    ['system.tenant.manage', '/systems/10/admin/tenants'],
    ['system.organization.manage', '/systems/10/admin/organization'],
    ['system.member.manage', '/systems/10/admin/organization'],
    ['system.role.manage', '/systems/10/admin/roles'],
    ['system.access.review', '/systems/10/admin/access-requests'],
    ['event.template.manage', '/systems/10/admin/message-templates'],
    ['ai.policy.manage', '/systems/10/admin/agent'],
    ['openapi.application.manage', '/systems/10/admin/openapi-applications'],
  ])('maps the isolated system permission %s', async (permission, path) => {
    render('system', [permission])
    await flushPromises()
    expect(routerReplace).toHaveBeenCalledWith(path)
  })

  it('requires both system admin access and module config manage for configuration', async () => {
    const both = render('system', ['system.admin.access', 'module.config.manage'])
    await flushPromises()
    expect(routerReplace).toHaveBeenCalledWith('/systems/10/admin/configuration')
    both.wrapper.unmount()

    routerReplace.mockClear()
    const accessOnly = render('system', ['system.admin.access'])
    await flushPromises()
    expect(routerReplace).not.toHaveBeenCalled()
    expect(accessOnly.wrapper.find('.admin-empty-state').exists()).toBe(true)
    accessOnly.wrapper.unmount()

    const configOnly = render('system', ['module.config.manage'])
    await flushPromises()
    expect(routerReplace).not.toHaveBeenCalled()
    expect(configOnly.wrapper.find('.admin-empty-state').exists()).toBe(true)
  })

  it('sends a fully authorized owner to the onboarding result before individual settings pages', async () => {
    render('system', ['system.admin.access', 'module.config.manage', 'system.member.manage', 'system.settings.manage'])
    await flushPromises()
    expect(routerReplace).toHaveBeenCalledWith('/systems/10/admin/onboarding')
  })

  it('preserves existing priority and stays safely empty with no valid candidate', async () => {
    render('system', ['ai.policy.manage', 'system.role.manage', 'event.template.manage'])
    await flushPromises()
    expect(routerReplace).toHaveBeenCalledTimes(1)
    expect(routerReplace).toHaveBeenCalledWith('/systems/10/admin/roles')

    routerReplace.mockClear()
    const { wrapper } = render('platform', [])
    await flushPromises()
    expect(routerReplace).not.toHaveBeenCalled()
    expect(wrapper.find('.admin-empty-state').exists()).toBe(true)
  })

  it('reacts once when a permission snapshot gains a candidate without redirect loops', async () => {
    const { session } = render('system', [])
    await flushPromises()
    expect(routerReplace).not.toHaveBeenCalled()

    session.context = { ...session.context!, permissionVersion: '2', permissions: ['ai.policy.manage'] }
    await nextTick()
    await flushPromises()
    expect(routerReplace).toHaveBeenCalledTimes(1)
    expect(routerReplace).toHaveBeenCalledWith('/systems/10/admin/agent')

    await nextTick()
    expect(routerReplace).toHaveBeenCalledTimes(1)
  })
})
