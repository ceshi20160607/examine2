import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { eventApi } from '@/services/event'
import type { DeliveryLog, DeliveryLogDetail } from '@/types/event'
import EventDeliveryLogManager from '@/views/system/admin/EventDeliveryLogManager.vue'

vi.mock('@/services/event', () => ({
  eventApi: { deliveryLogs: vi.fn(), deliveryLog: vi.fn() },
}))

const log: DeliveryLog = {
  deliveryId: '801', templateCode: 'MODULE_EXPORT_SUCCEEDED', channel: 'WEBHOOK', status: 'FAILED',
  attemptCount: 2, durationMs: 34, traceId: 'trace-801', recipientMasked: 'https://hooks.example.test/***',
  targetType: 'MODULE_EXPORT_TASK', targetId: '401', createdAt: '2026-08-06T08:00:00Z',
  completedAt: '2026-08-06T08:01:00Z', retryable: false,
}
const detail: DeliveryLogDetail = {
  ...log,
  templateVersionId: '22',
  targetPath: '/systems/10/workbench?task=401',
  failureCode: 'WEBHOOK_HTTP_400',
  failureMessage: 'Remote endpoint rejected the request',
  dedupeFingerprint: 'sha256:safe-fingerprint',
  attempts: [{
    attemptNo: 1, status: 'FAILED', durationMs: 34, traceId: 'attempt-trace',
    failureCode: 'WEBHOOK_HTTP_400', failureMessage: 'Remote endpoint rejected the request',
    startedAt: '2026-08-06T08:00:00Z', completedAt: '2026-08-06T08:00:01Z',
  }],
}

const stubs = {
  'a-alert': { props: ['message', 'description'], template: '<div class="alert-stub">{{ message }} {{ description }}<slot /><slot name="action" /></div>' },
  'a-button': { props: ['disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
  'a-input': { inheritAttrs: false, props: ['value'], emits: ['update:value'], template: '<input v-bind="$attrs" :value="value" @input="$emit(\'update:value\', $event.target.value)" />' },
  'a-table': {
    props: ['columns', 'dataSource'],
    template: '<div class="table-stub"><header>{{ columns.map(column => column.title).join(\' | \') }}</header><div v-for="record in dataSource" :key="record.deliveryId || record.attemptNo" class="table-row"><div v-for="column in columns" :key="column.key || column.dataIndex"><slot name="bodyCell" :column="column" :record="record" /></div></div></div>',
  },
  'a-tag': { template: '<span><slot /></span>' },
  'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
  'a-pagination': { emits: ['change'], template: '<button class="page-two" @click="$emit(\'change\', 2)">2</button>' },
  'a-drawer': { props: ['open'], template: '<div v-if="open" class="drawer-stub"><slot /></div>' },
  'a-tabs': { template: '<div><slot /></div>' },
  'a-tab-pane': { props: ['tab'], template: '<section><h4>{{ tab }}</h4><slot /></section>' },
  'a-spin': true,
}

function render() {
  return mount(EventDeliveryLogManager, { props: { systemId: '10' }, global: { stubs } })
}

describe('EventDeliveryLogManager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(eventApi.deliveryLogs).mockResolvedValue({ items: [log], total: 1, page: 0, size: 20 })
    vi.mocked(eventApi.deliveryLog).mockResolvedValue(detail)
  })

  it('renders the standard table headers and exact server filters', async () => {
    const wrapper = render()
    await flushPromises()

    expect(wrapper.text()).toContain('投递记录 | 模板 | 渠道 | 状态 | 尝试 | 耗时 | 脱敏接收方 | 创建时间 | 操作')
    expect(eventApi.deliveryLogs).toHaveBeenCalledWith('10', {
      page: 0, size: 20, channel: 'ALL', status: 'ALL', templateCode: '',
    })

    await wrapper.get('.delivery-log-channel').setValue('WEBHOOK')
    await wrapper.get('.delivery-log-status').setValue('FAILED')
    await wrapper.get('.delivery-log-template').setValue(' MODULE_EXPORT_SUCCEEDED ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(vi.mocked(eventApi.deliveryLogs).mock.calls.at(-1)).toEqual(['10', {
      page: 0,
      size: 20,
      channel: 'WEBHOOK',
      status: 'FAILED',
      templateCode: 'MODULE_EXPORT_SUCCEEDED',
    }])
  })

  it('opens overview, attempts and tracking tabs from the row action without rendering unsafe extras', async () => {
    vi.mocked(eventApi.deliveryLog).mockResolvedValueOnce({
      ...detail,
      recipientEmail: 'raw-recipient@example.test',
      endpoint: 'https://hooks.example.test/raw?secret=query-secret',
      smtpPassword: 'raw-smtp-password',
      webhookSecret: 'raw-webhook-secret',
      responseBody: 'raw-external-response',
    } as DeliveryLogDetail)
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.delivery-log-detail').trigger('click')
    await flushPromises()

    expect(eventApi.deliveryLog).toHaveBeenCalledWith('10', '801')
    const drawer = wrapper.get('.drawer-stub').text()
    expect(drawer).toContain('概览')
    expect(drawer).toContain('尝试记录')
    expect(drawer).toContain('追踪信息')
    expect(drawer).toContain('WEBHOOK_HTTP_400')
    expect(drawer).toContain('sha256:safe-fingerprint')
    expect(drawer).not.toContain('raw-recipient')
    expect(drawer).not.toContain('query-secret')
    expect(drawer).not.toContain('raw-smtp-password')
    expect(drawer).not.toContain('raw-webhook-secret')
    expect(drawer).not.toContain('raw-external-response')
    expect(drawer).not.toContain('workbench?task=401')
  })

  it('changes server pages instead of slicing the visible rows locally', async () => {
    vi.mocked(eventApi.deliveryLogs).mockImplementation(async (_system, query) => ({
      items: [log], total: 41, page: query?.page ?? 0, size: 20,
    }))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.page-two').trigger('click')
    await flushPromises()

    expect(vi.mocked(eventApi.deliveryLogs).mock.calls.at(-1)).toEqual(['10', {
      page: 1, size: 20, channel: 'ALL', status: 'ALL', templateCode: '',
    }])
  })
})
