import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiRequestError } from '@/services/api'
import { eventApi } from '@/services/event'
import { useSessionStore } from '@/stores/session'
import type { DeliveryPreference, InboxMessage, InboxMessagePage, InboxMessageQuery } from '@/types/event'
import MessageInboxView from '@/views/system/MessageInboxView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
const push = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push }),
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

const unreadMessage: InboxMessage = {
  id: '101',
  systemId: '10',
  tenantId: '20',
  senderMemberId: '100',
  recipientMemberId: '200',
  templateCode: 'NOTICE',
  title: 'Release notice',
  body: 'Review the release',
  target: null,
  status: 'UNREAD',
  createdAt: '2026-07-25T12:00:00Z',
  readAt: null,
  archivedAt: null,
  version: 1,
}

const exportPreference: DeliveryPreference = {
  templateCode: 'MODULE_EXPORT_SUCCEEDED',
  eventType: 'MODULE_EXPORT_SUCCEEDED',
  name: '导出成功通知',
  channel: 'INBOX',
  enabled: true,
  version: 0,
  updatedAt: null,
}
const importPreference: DeliveryPreference = {
  templateCode: 'MODULE_IMPORT_FAILED',
  eventType: 'MODULE_IMPORT_FAILED',
  name: '导入失败通知',
  channel: 'INBOX',
  enabled: true,
  version: 2,
  updatedAt: '2026-08-05T04:00:00Z',
}

function page(
  items: InboxMessage[],
  current = 1,
  total = items.length,
): InboxMessagePage {
  return { items, page: current, size: 20, total }
}

function applySession(tenantId: string, memberId = '200') {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: {
      type: 'SYSTEM',
      account: { id: '1', username: 'member', displayName: 'Member' },
      systemId: '10',
      tenantId,
      memberId,
      permissionVersion: '1',
      permissions: ['event.message.access'],
      shells: ['SYSTEM_RUNTIME'],

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
  return mount(MessageInboxView, {
    global: {
      stubs: {
        'a-alert': {
          props: ['message', 'description'],
          template: '<div class="alert-stub">{{ message }} {{ description }}<slot /><slot name="action" /></div>',
        },
        'a-badge': { template: '<span />' },
        'a-spin': { template: '<div><slot /></div>' },
        'a-empty': {
          props: ['description'],
          template: '<div>{{ description }}</div>',
        },
        'a-tag': { template: '<span><slot /></span>' },
        'a-button': {
          props: ['disabled'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
        },
        'a-segmented': {
          props: ['value', 'options'],
          emits: ['change'],
          template: '<button class="status-filter" @click="$emit(\'change\', \'ARCHIVED\')">{{ value }}</button>',
        },
        'a-pagination': {
          props: ['current', 'total'],
          emits: ['change'],
          template: '<button class="page-two" @click="$emit(\'change\', 2)">{{ current }}/{{ total }}</button>',
        },
        'a-drawer': {
          props: ['open'],
          emits: ['update:open'],
          template: '<div v-if="open" class="preference-drawer"><button class="preference-close" @click="$emit(\'update:open\', false)">close</button><slot /></div>',
        },
        'a-switch': {
          inheritAttrs: false,
          props: ['checked', 'disabled', 'loading'],
          emits: ['change'],
          template: '<button v-bind="$attrs" :disabled="disabled" :aria-checked="String(checked)" :data-loading="String(loading)" @click="$emit(\'change\', !checked)">{{ checked ? \'on\' : \'off\' }}</button>',
        },
      },
    },
  })
}

describe('MessageInboxView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    push.mockResolvedValue(undefined)
    route.params.systemId = '10'
    applySession('20')
    vi.mocked(eventApi.list).mockResolvedValue(page([unreadMessage]))
    vi.mocked(eventApi.unreadCount).mockResolvedValue({ unreadCount: 1 })
    vi.mocked(eventApi.read).mockResolvedValue({
      ...unreadMessage,
      status: 'READ',
      readAt: '2026-07-25T12:01:00Z',
    })
    vi.mocked(eventApi.readAll).mockResolvedValue({ changedCount: 1 })
    vi.mocked(eventApi.archive).mockResolvedValue({
      ...unreadMessage,
      status: 'ARCHIVED',
      readAt: '2026-07-25T12:01:00Z',
      archivedAt: '2026-07-25T12:01:00Z',
    })
    vi.mocked(eventApi.deliveryPreferences).mockResolvedValue([exportPreference, importPreference])
    vi.mocked(eventApi.updateDeliveryPreference).mockResolvedValue({
      ...exportPreference,
      enabled: false,
      version: 1,
      updatedAt: '2026-08-05T05:00:00Z',
    })
  })

  it('loads the first ALL page and exposes the server result', async () => {
    const wrapper = render()
    await flushPromises()

    expect(eventApi.list).toHaveBeenCalledWith('10', {
      status: 'ALL',
      page: 1,
      size: 20,
    })
    expect(wrapper.text()).toContain('Release notice')
    expect(eventApi.deliveryPreferences).not.toHaveBeenCalled()
  })

  it('lazy-loads only safe template-and-channel preferences when the panel opens', async () => {
    vi.mocked(eventApi.deliveryPreferences).mockResolvedValueOnce([{
      ...exportPreference,
      templateBody: 'must not render',
      recipientMemberId: 'must not render member',
    } as DeliveryPreference])
    const wrapper = render()
    await flushPromises()

    expect(eventApi.deliveryPreferences).not.toHaveBeenCalled()
    await wrapper.get('.delivery-preferences-open').trigger('click')
    await flushPromises()

    expect(eventApi.deliveryPreferences).toHaveBeenCalledWith('10')
    const panel = wrapper.get('.delivery-preference-panel').text()
    expect(panel).toContain('导出成功通知')
    expect(panel).toContain('MODULE_EXPORT_SUCCEEDED')
    expect(panel).toContain('INBOX')
    expect(panel).toContain('按模板和渠道管理今后的投递')
    expect(panel).toContain('其他渠道和已有消息不受影响')
    expect(panel).not.toContain('must not render')
  })

  it('keeps rows with the same template independent by channel', async () => {
    const emailPreference: DeliveryPreference = {
      ...exportPreference,
      channel: 'EMAIL',
      enabled: true,
      version: 5,
    }
    vi.mocked(eventApi.deliveryPreferences).mockResolvedValueOnce([exportPreference, emailPreference])
    vi.mocked(eventApi.updateDeliveryPreference).mockResolvedValueOnce({
      ...emailPreference,
      enabled: false,
      version: 6,
    })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.delivery-preferences-open').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.delivery-preference-row')).toHaveLength(2)
    await wrapper.findAll('.delivery-preference-toggle')[1]!.trigger('click')
    await flushPromises()

    expect(eventApi.updateDeliveryPreference).toHaveBeenCalledWith(
      '10',
      'MODULE_EXPORT_SUCCEEDED',
      'EMAIL',
      { enabled: false, expectedVersion: 5 },
    )
    expect(wrapper.findAll('.delivery-preference-toggle')[0]!.attributes('aria-checked')).toBe('true')
    expect(wrapper.findAll('.delivery-preference-toggle')[1]!.attributes('aria-checked')).toBe('false')
  })

  it('keeps a toggle controlled until the exact row update succeeds', async () => {
    let resolveUpdate!: (value: DeliveryPreference) => void
    vi.mocked(eventApi.updateDeliveryPreference).mockReturnValueOnce(new Promise(resolve => { resolveUpdate = resolve }))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.delivery-preferences-open').trigger('click')
    await flushPromises()

    const toggles = wrapper.findAll('.delivery-preference-toggle')
    await toggles[0]!.trigger('click')
    expect(eventApi.updateDeliveryPreference).toHaveBeenCalledWith('10', 'MODULE_EXPORT_SUCCEEDED', 'INBOX', {
      enabled: false,
      expectedVersion: 0,
    })
    expect(toggles[0]!.attributes('aria-checked')).toBe('true')
    expect(toggles[0]!.attributes('disabled')).toBeDefined()
    expect(toggles[1]!.attributes('disabled')).toBeUndefined()

    resolveUpdate({
      ...exportPreference,
      enabled: false,
      version: 1,
      updatedAt: '2026-08-05T05:00:00Z',
    })
    await flushPromises()
    expect(wrapper.findAll('.delivery-preference-toggle')[0]!.attributes('aria-checked')).toBe('false')
  })

  it('preserves the prior row on save failure and reloads the catalog after a 409', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.delivery-preferences-open').trigger('click')
    await flushPromises()

    vi.mocked(eventApi.updateDeliveryPreference).mockRejectedValueOnce(new Error('preference unavailable'))
    await wrapper.findAll('.delivery-preference-toggle')[0]!.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.delivery-preference-toggle')[0]!.attributes('aria-checked')).toBe('true')
    expect(wrapper.get('.delivery-preference-save-error').text()).toContain('preference unavailable')
    expect(eventApi.deliveryPreferences).toHaveBeenCalledTimes(1)

    vi.mocked(eventApi.deliveryPreferences).mockResolvedValueOnce([{
      ...exportPreference,
      enabled: false,
      version: 3,
      updatedAt: '2026-08-05T06:00:00Z',
    }, importPreference])
    vi.mocked(eventApi.updateDeliveryPreference).mockRejectedValueOnce(new ApiRequestError(409, {
      code: 'EVENT_DELIVERY_PREFERENCE_VERSION_CONFLICT',
      message: 'preference version is stale',
      data: null,
      requestId: 'request-conflict',
      traceId: 'trace-conflict',
      errors: [],
    }))
    await wrapper.findAll('.delivery-preference-toggle')[0]!.trigger('click')
    await flushPromises()

    expect(eventApi.deliveryPreferences).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('.delivery-preference-toggle')[0]!.attributes('aria-checked')).toBe('false')
    expect(wrapper.get('.delivery-preference-save-error').text())
      .toContain('EVENT_DELIVERY_PREFERENCE_VERSION_CONFLICT')
  })

  it('shows loading, empty, load-error and retry states without blocking inbox actions', async () => {
    let resolvePreferences!: (value: DeliveryPreference[]) => void
    vi.mocked(eventApi.deliveryPreferences).mockReturnValueOnce(new Promise(resolve => { resolvePreferences = resolve }))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.delivery-preferences-open').trigger('click')
    await flushPromises()
    expect(wrapper.find('.delivery-preference-loading').exists()).toBe(true)

    await wrapper.get('.message-actions button').trigger('click')
    await flushPromises()
    expect(eventApi.read).toHaveBeenCalledWith('10', '101')

    resolvePreferences([])
    await flushPromises()
    expect(wrapper.get('.delivery-preference-empty').text()).toContain('暂无可配置的投递类型')

    vi.mocked(eventApi.deliveryPreferences).mockRejectedValueOnce(new Error('preference catalog unavailable'))
    await wrapper.get('.delivery-preferences-open').trigger('click')
    await flushPromises()
    expect(wrapper.get('.delivery-preference-load-error').text()).toContain('preference catalog unavailable')

    vi.mocked(eventApi.deliveryPreferences).mockResolvedValueOnce([exportPreference])
    await wrapper.get('.delivery-preference-retry').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.delivery-preference-row')).toHaveLength(1)
  })

  it('closes, clears and fences stale preferences when member context changes', async () => {
    let resolveOldContext!: (value: DeliveryPreference[]) => void
    vi.mocked(eventApi.deliveryPreferences).mockReturnValueOnce(new Promise(resolve => { resolveOldContext = resolve }))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.delivery-preferences-open').trigger('click')
    await flushPromises()

    applySession('21', '201')
    await flushPromises()
    expect(wrapper.find('.preference-drawer').exists()).toBe(false)
    resolveOldContext([{ ...exportPreference, name: '旧上下文偏好' }])
    await flushPromises()
    expect(wrapper.text()).not.toContain('旧上下文偏好')

    await wrapper.get('.delivery-preferences-open').trigger('click')
    await flushPromises()
    expect(eventApi.deliveryPreferences).toHaveBeenCalledTimes(2)
    expect(wrapper.get('.delivery-preference-panel').text()).toContain('导出成功通知')
  })

  it('marks an actionable result message read and opens its system-local task path', async () => {
    const actionable = { ...unreadMessage,
      target: { type: 'MODULE_EXPORT_TASK', id: '42' },
      targetPath: '/systems/10/workbench?module=order&panel=export&task=42' }
    vi.mocked(eventApi.list).mockResolvedValue(page([actionable]))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.message-card').trigger('click')
    await flushPromises()

    expect(eventApi.read).toHaveBeenCalledWith('10', '101')
    expect(push).toHaveBeenCalledWith('/systems/10/workbench?module=order&panel=export&task=42')
  })

  it('resets status and page when the authenticated tenant changes', async () => {
    vi.mocked(eventApi.list).mockResolvedValue(page([unreadMessage], 1, 21))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.status-filter').trigger('click')
    await flushPromises()
    await wrapper.get('.page-two').trigger('click')
    await flushPromises()
    expect(eventApi.list).toHaveBeenCalledWith('10', {
      status: 'ARCHIVED',
      page: 2,
      size: 20,
    })

    applySession('21')
    await flushPromises()
    expect(vi.mocked(eventApi.list).mock.calls.at(-1)).toEqual([
      '10',
      { status: 'ALL', page: 1, size: 20 },
    ])
  })

  it('reloads and clamps after a mutation removes the last item on a page', async () => {
    let readApplied = false
    vi.mocked(eventApi.list).mockImplementation(
      async (_systemId: string, query: InboxMessageQuery = {}) => {
        if (query.page === 2) {
          return readApplied ? page([], 2, 20) : page([unreadMessage], 2, 21)
        }
        return page([unreadMessage], 1, readApplied ? 20 : 21)
      },
    )
    vi.mocked(eventApi.read).mockImplementation(async () => {
      readApplied = true
      return { ...unreadMessage, status: 'READ', readAt: '2026-07-25T12:01:00Z' }
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.page-two').trigger('click')
    await flushPromises()
    await wrapper.get('.message-actions button').trigger('click')
    await flushPromises()

    expect(eventApi.read).toHaveBeenCalledWith('10', '101')
    expect(vi.mocked(eventApi.list).mock.calls.slice(-2)).toEqual([
      ['10', { status: 'ALL', page: 2, size: 20 }],
      ['10', { status: 'ALL', page: 1, size: 20 }],
    ])
  })
})
