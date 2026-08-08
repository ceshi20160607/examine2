import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { eventApi } from '@/services/event'
import { useSessionStore } from '@/stores/session'
import PlatformSystemsView from '@/views/platform/admin/PlatformSystemsView.vue'
import MessageInboxView from '@/views/system/MessageInboxView.vue'

import { expectAccessibleName, setTestViewport, UI_HARDENING_VIEWPORTS } from './ui-hardening-testkit'

const route = vi.hoisted(() => ({ params: { systemId: 'system-10' } }))
const push = vi.hoisted(() => vi.fn())
const listSystems = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push }),
}))

vi.mock('@/services/admin', () => ({
  platformAdminApi: { listSystems },
}))

vi.mock('@/services/event', () => ({
  eventApi: {
    list: vi.fn(),
    unreadCount: vi.fn(),
    read: vi.fn(),
    readAll: vi.fn(),
    archive: vi.fn(),
    deliveryPreferences: vi.fn(),
    updateDeliveryPreference: vi.fn(),
  },
}))

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((accept, decline) => {
    resolve = accept
    reject = decline
  })
  return { promise, reject, resolve }
}

const ButtonStub = {
  inheritAttrs: false,
  props: ['htmlType', 'loading', 'disabled'],
  emits: ['click'],
  template: '<button v-bind="$attrs" :type="htmlType || \'button\'" :disabled="disabled" :aria-busy="String(Boolean(loading))" @click="$emit(\'click\')"><slot /></button>',
}
const AlertStub = {
  props: ['message', 'description'],
  template: '<div role="alert">{{ message }} {{ description }}<slot /><slot name="message" /><slot name="description" /><slot name="action" /></div>',
}
const EmptyStub = {
  props: ['description'],
  template: '<div role="status" data-state="empty">{{ description }}</div>',
}
const SpinStub = {
  props: ['spinning'],
  template: '<div role="status" :aria-busy="String(spinning !== false)"><slot /></div>',
}
const TableStub = {
  props: ['dataSource', 'loading', 'columns'],
  template: '<div role="table" :aria-busy="String(Boolean(loading))" :data-rows="dataSource?.length || 0"><div v-if="!loading && !dataSource?.length" role="status" data-state="empty">暂无数据</div></div>',
}
const DrawerStub = {
  props: ['open', 'title'],
  emits: ['update:open'],
  template: '<aside v-if="open" role="dialog" :aria-label="title"><slot /></aside>',
}

const commonStubs = {
  'a-alert': AlertStub,
  'a-badge': { template: '<span><slot /></span>' },
  'a-button': ButtonStub,
  'a-drawer': DrawerStub,
  'a-empty': EmptyStub,
  'a-form': { template: '<form><slot /></form>' },
  'a-form-item': { template: '<label><slot /></label>' },
  'a-input': { template: '<input />' },
  'a-modal': { props: ['open', 'title'], template: '<section v-if="open" role="dialog" :aria-label="title"><slot /></section>' },
  'a-pagination': true,
  'a-segmented': { template: '<div role="group" />' },
  'a-select': { template: '<select><slot /></select>' },
  'a-select-option': { template: '<option><slot /></option>' },
  'a-spin': SpinStub,
  'a-switch': true,
  'a-table': TableStub,
  'a-tag': { template: '<span><slot /></span>' },
  'a-textarea': { template: '<textarea />' },
}

function matchMediaForCurrentViewport(): MediaQueryList {
  return {
    matches: window.innerWidth <= 720,
    media: '(max-width: 720px)',
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }
}

function memberPinia() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useSessionStore().applyAuth({
    account: { id: 'account-1', username: 'member', displayName: 'Member' },
    context: {
      type: 'SYSTEM',
      account: { id: 'account-1', username: 'member', displayName: 'Member' },
      systemId: 'system-10',
      systemName: '订单系统',
      tenantId: 'tenant-20',
      tenantName: '华东租户',
      memberId: 'member-30',
      permissionVersion: '1',
      permissions: ['event.message.access'],
      shells: ['SYSTEM_RUNTIME'],
    },
    systems: [],
  })
  return pinia
}

describe('UI hardening: loading, empty, error and recovery states', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setTestViewport(UI_HARDENING_VIEWPORTS[2]!)
    vi.stubGlobal('matchMedia', vi.fn(matchMediaForCurrentViewport))
  })

  it('keeps the desktop administration table recoverable from a failed initial request', async () => {
    const first = deferred<{ items: never[], page: number, size: number, total: number }>()
    listSystems.mockReturnValueOnce(first.promise)
    const wrapper = mount(PlatformSystemsView, { global: { stubs: commonStubs } })

    await flushPromises()
    expect(wrapper.get('[role="table"]').attributes('aria-busy')).toBe('true')

    first.reject(new Error('administration unavailable'))
    await flushPromises()
    expectAccessibleName(wrapper.get('[role="alert"]').element, /无法连接管理服务/u)

    listSystems.mockResolvedValueOnce({
      items: [{
        id: 'system-1', code: 'orders', name: '订单系统', description: '业务订单',
        status: 'ACTIVE', tenantMode: 'SINGLE', ownerAccountId: 'account-1', version: '1',
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const refresh = wrapper.findAll('button').find(button => /刷新/u.test(button.text()))
    expect(refresh).toBeDefined()
    expectAccessibleName(refresh!.element, /刷新/u)
    await refresh!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('[role="table"]').attributes('data-rows')).toBe('1')
    expect(wrapper.get('[role="table"]').attributes('aria-busy')).toBe('false')
  })

  it.each(UI_HARDENING_VIEWPORTS)('renders an explicit admin empty state at $name width', async (viewport) => {
    setTestViewport(viewport)
    listSystems.mockResolvedValueOnce({ items: [], page: 1, size: 20, total: 0 })
    const wrapper = mount(PlatformSystemsView, { global: { stubs: commonStubs } })
    await flushPromises()

    const empty = wrapper.get('[data-state="empty"]')
    expectAccessibleName(empty.element, /暂无/u)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('announces a member inbox failure and reaches a non-busy empty state after retry', async () => {
    const first = deferred<{ items: never[], page: number, size: number, total: number }>()
    vi.mocked(eventApi.list).mockReturnValueOnce(first.promise)
    vi.mocked(eventApi.unreadCount).mockResolvedValue({ unreadCount: 0 })
    const pinia = memberPinia()
    const wrapper = mount(MessageInboxView, {
      global: { plugins: [pinia], stubs: commonStubs },
    })
    await flushPromises()

    const busyState = wrapper.findAll('[role="status"]')
      .find(state => state.attributes('aria-busy') === 'true')
    expect(busyState).toBeDefined()

    first.reject(new Error('inbox unavailable'))
    await flushPromises()
    expectAccessibleName(wrapper.get('[role="alert"]').element, 'inbox unavailable')

    vi.mocked(eventApi.list).mockResolvedValueOnce({ items: [], page: 1, size: 20, total: 0 })
    vi.mocked(eventApi.unreadCount).mockResolvedValueOnce({ unreadCount: 0 })
    const refresh = wrapper.findAll('button').find(button => /刷新/u.test(button.text()))
    expect(refresh).toBeDefined()
    await refresh!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expectAccessibleName(wrapper.get('[data-state="empty"]').element, /当前没有消息/u)
    const settledState = wrapper.findAll('[role="status"]')
      .find(state => state.attributes('aria-busy') === 'false')
    expect(settledState).toBeDefined()
  })

  it('gives the member preference drawer a programmatic name before exposing its states', async () => {
    vi.mocked(eventApi.list).mockResolvedValue({ items: [], page: 1, size: 20, total: 0 })
    vi.mocked(eventApi.unreadCount).mockResolvedValue({ unreadCount: 0 })
    vi.mocked(eventApi.deliveryPreferences).mockResolvedValue([])
    const pinia = memberPinia()
    const wrapper = mount(MessageInboxView, {
      global: { plugins: [pinia], stubs: commonStubs },
    })
    await flushPromises()

    await wrapper.get('.delivery-preferences-open').trigger('click')
    await flushPromises()
    const drawer = wrapper.get('[role="dialog"]')
    expectAccessibleName(drawer.element, '投递偏好')
    expectAccessibleName(drawer.get('[data-state="empty"]').element, /暂无可配置/u)
  })
})
