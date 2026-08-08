import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { platformTodoApi } from '@/services/platformTodo'
import { useSessionStore } from '@/stores/session'
import type { PlatformTodo } from '@/types/platformTodo'
import PlatformTodoView from '@/views/platform/PlatformTodoView.vue'

vi.mock('@/services/platformTodo', () => ({ platformTodoApi: {
  list: vi.fn(), counts: vi.fn(), detail: vi.fn(), action: vi.fn(),
} }))

const todo: PlatformTodo = {
  id: '91', context: 'PLATFORM', source: 'PLATFORM_TASK', category: 'TASK',
  title: '核对平台授权', description: '确认当前账户的平台授权范围', priority: 'HIGH',
  state: 'OPEN', sourceStatus: 'OPEN', dueAt: '2099-08-06T02:00:00Z',
  routeHint: '/platform/workbench?task=91', availableActions: ['COMPLETE', 'CANCEL'],
  createdAt: '2026-08-06T08:00:00Z', updatedAt: '2026-08-06T08:00:00Z', version: 0,
}

const closed: PlatformTodo = {
  ...todo, state: 'CLOSED', sourceStatus: 'COMPLETED', availableActions: ['REOPEN'], version: 1,
}

const stubs = {
  'a-alert': { props: ['message'], template: '<div class="alert-stub">{{ message }}</div>' },
  'a-button': {
    inheritAttrs: false, props: ['disabled', 'loading'], emits: ['click'],
    template: '<button v-bind="$attrs" :disabled="disabled" :data-loading="loading" @click="$emit(\'click\')"><slot /></button>',
  },
  'a-tag': { template: '<span><slot /></span>' },
  'a-spin': { props: ['spinning'], template: '<div><slot /></div>' },
  'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
  'a-drawer': { props: ['open'], emits: ['update:open'], template: '<aside v-if="open" class="drawer-stub"><slot /></aside>' },
  'a-tabs': { template: '<div><slot /></div>' },
  'a-tab-pane': { props: ['tab'], template: '<section><h3>{{ tab }}</h3><slot /></section>' },
}

function applySession(permissions: string[]) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: {
      type: 'PLATFORM', account: { id: '1', username: 'member', displayName: 'Member' },
      permissionVersion: '1', permissions, shells: ['PLATFORM_RUNTIME'],
    },
    systems: [],
  })
}

function render() {
  return mount(PlatformTodoView, { global: { stubs } })
}

describe('PlatformTodoView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(platformTodoApi.list).mockResolvedValue({ items: [todo], page: 1, size: 20, total: 1 })
    vi.mocked(platformTodoApi.counts).mockResolvedValue({ open: 1, closed: 2, total: 3, today: 1, reminders: 1, approvals: 0, failures: 0 })
    vi.mocked(platformTodoApi.detail).mockResolvedValue(todo)
    vi.mocked(platformTodoApi.action).mockResolvedValue({ todo: closed, replayed: false })
  })

  it('loads counts and rows, opens a tabbed detail drawer and executes a versioned action', async () => {
    applySession(['platform.task.read', 'platform.task.manage'])
    const wrapper = render()
    await flushPromises()

    expect(platformTodoApi.list).toHaveBeenCalledWith({ state: 'OPEN', type: 'ALL', page: 1, size: 20 })
    expect(platformTodoApi.counts).toHaveBeenCalled()
    expect(wrapper.text()).toContain('核对平台授权')
    expect(wrapper.text()).toContain('待处理1')

    await wrapper.get('.platform-todo-detail').trigger('click')
    await flushPromises()
    expect(platformTodoApi.detail).toHaveBeenCalledWith('91')
    expect(wrapper.get('.drawer-stub').text()).toContain('基本信息')
    expect(wrapper.get('.drawer-stub').text()).toContain('定位信息')

    await wrapper.get('.platform-todo-action-complete').trigger('click')
    await flushPromises()
    expect(platformTodoApi.action).toHaveBeenCalledWith('91', 'COMPLETE', 0)
    expect(wrapper.get('.drawer-stub').text()).toContain('重新打开')
    expect(platformTodoApi.list).toHaveBeenCalledTimes(2)
  })

  it('does not expose lifecycle actions to a read-only account', async () => {
    applySession(['platform.task.read'])
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.platform-todo-detail').trigger('click')
    await flushPromises()

    expect(wrapper.find('.platform-todo-action-complete').exists()).toBe(false)
    expect(wrapper.find('.platform-todo-action-cancel').exists()).toBe(false)
  })
})
