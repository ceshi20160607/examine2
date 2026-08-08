import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SystemAdminLayout from '@/layouts/SystemAdminLayout.vue'
import SystemLayout from '@/layouts/SystemLayout.vue'
import router from '@/router'
import { todoApi } from '@/services/todo'
import { useSessionStore } from '@/stores/session'
import MessageTemplatesView from '@/views/system/admin/MessageTemplatesView.vue'

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

describe('combined Work and message-template navigation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(todoApi.counts).mockResolvedValue({
      openCount: 0, taskCount: 0, approvalCount: 0, todayCount: 0, overdueCount: 0,
    })
  })

  it.each(['work.task.access', 'work.report.access', 'work.report.create', 'work.report.manage'])(
    'makes the combined Work route reachable with %s', async (permission) => {
      const routeRecord = router.getRoutes().find(item => item.name === 'system-work-tasks')
      expect(routeRecord?.meta.requiredPermissions).toBeUndefined()
      expect(routeRecord?.meta.anyPermissions).toEqual([
        'work.task.access', 'work.report.access', 'work.report.create', 'work.report.manage',
      ])
      applySession([permission], ['SYSTEM_RUNTIME'])
      const wrapper = mount(SystemLayout, { global: { stubs } })
      await flushPromises()
      expect(wrapper.get('a[data-to="/systems/10/tasks"]').text()).toContain('任务 / 日报')
    },
  )

  it('exposes a dedicated template page with only the template permission', () => {
    const routeRecord = router.getRoutes().find(item => item.name === 'system-admin-message-templates')
    expect(routeRecord?.path).toBe('/systems/:systemId/admin/message-templates')
    expect(routeRecord?.meta.requiredPermissions).toEqual(['event.template.manage'])

    applySession(['event.template.manage'], ['SYSTEM_ADMIN'])
    expect(mount(SystemAdminLayout, { global: { stubs } })
      .get('a[data-to="/systems/10/admin/message-templates"]').text()).toContain('消息模板')
    applySession([], ['SYSTEM_ADMIN'])
    expect(mount(SystemAdminLayout, { global: { stubs } })
      .find('a[data-to="/systems/10/admin/message-templates"]').exists()).toBe(false)
  })

  it('reuses the existing message-template manager in the dedicated page', () => {
    const wrapper = mount(MessageTemplatesView, { global: { stubs: {
      AdminPageHeader: { props: ['title'], template: '<header>{{ title }}</header>' },
      MessageTemplateManager: { props: ['systemId'], template: '<div class="manager-stub">{{ systemId }}</div>' },
      EventChannelManager: { props: ['systemId'], template: '<div class="channel-manager-stub">{{ systemId }}</div>' },
      EventDeliveryLogManager: { props: ['systemId'], template: '<div class="log-manager-stub">{{ systemId }}</div>' },
      'a-tabs': { template: '<div><slot /></div>' },
      'a-tab-pane': { props: ['tab'], template: '<section><h2>{{ tab }}</h2><slot /></section>' },
    } } })
    expect(wrapper.text()).toContain('消息模板')
    expect(wrapper.text()).toContain('渠道配置')
    expect(wrapper.text()).toContain('投递日志')
    expect(wrapper.get('.manager-stub').text()).toBe('10')
  })
})
