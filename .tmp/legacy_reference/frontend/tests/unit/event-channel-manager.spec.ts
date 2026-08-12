import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { eventAdministrationApi } from '@/services/event'
import type { EventChannelConfiguration } from '@/types/event'
import EventChannelManager from '@/views/system/admin/EventChannelManager.vue'

vi.mock('@/services/event', () => ({
  eventAdministrationApi: {
    channels: vi.fn(),
    updateChannel: vi.fn(),
    checkChannel: vi.fn(),
  },
}))

const email: EventChannelConfiguration = {
  channel: 'EMAIL', displayName: '电子邮件', enabled: false, available: true, configured: true,
  maskedDestination: '系统成员邮箱（投递时脱敏解析）', secretRefMasked: null, timeoutMs: null, version: 2,
  updatedAt: '2026-08-06T08:00:00Z', lastCheckAt: null, lastCheckStatus: null,
}
const inbox: EventChannelConfiguration = {
  channel: 'INBOX', displayName: '站内信', enabled: true, available: true, configured: true,
  maskedDestination: '站内消息中心', secretRefMasked: null, timeoutMs: null, version: 0,
  updatedAt: null, lastCheckAt: null, lastCheckStatus: null,
}
const webhook: EventChannelConfiguration = {
  channel: 'WEBHOOK', displayName: '签名 Webhook', enabled: true, available: true, configured: true,
  maskedDestination: 'https://hooks.example.test/…', secretRefMasked: 'env://********',
  timeoutMs: 3000, version: 4, updatedAt: '2026-08-06T08:10:00Z',
  lastCheckAt: '2026-08-06T08:11:00Z', lastCheckStatus: 'SENT',
}

const stubs = {
  'a-alert': { props: ['message', 'description'], template: '<div class="alert-stub">{{ message }} {{ description }}<slot /><slot name="action" /></div>' },
  'a-button': { props: ['disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
  'a-table': {
    props: ['columns', 'dataSource'],
    template: '<div class="table-stub"><div v-for="record in dataSource" :key="record.channel" class="table-row"><div v-for="column in columns" :key="column.key || column.dataIndex"><slot name="bodyCell" :column="column" :record="record" /></div></div></div>',
  },
  'a-tag': { template: '<span><slot /></span>' },
  'a-switch': {
    inheritAttrs: false,
    props: ['checked', 'disabled', 'loading'],
    emits: ['change'],
    template: '<button v-bind="$attrs" :disabled="disabled" :aria-checked="String(checked)" @click="$emit(\'change\', !checked)">{{ checked ? \'on\' : \'off\' }}</button>',
  },
  'a-empty': true,
  'a-drawer': { props: ['open'], template: '<div v-if="open" class="drawer-stub"><slot /></div>' },
  'a-tabs': { template: '<div><slot /></div>' },
  'a-tab-pane': { props: ['tab'], template: '<section><h4>{{ tab }}</h4><slot /></section>' },
  'a-modal': { props: ['open'], emits: ['ok'], template: '<div v-if="open" class="modal-stub"><slot /><button class="modal-ok" @click="$emit(\'ok\')">ok</button></div>' },
  'a-form': { template: '<form><slot /></form>' },
  'a-form-item': { template: '<label><slot /></label>' },
  'a-input': { inheritAttrs: false, props: ['value'], emits: ['update:value'], template: '<input v-bind="$attrs" :value="value" @input="$emit(\'update:value\', $event.target.value)" />' },
  'a-input-number': { inheritAttrs: false, props: ['value'], emits: ['update:value'], template: '<input v-bind="$attrs" type="number" :value="value" @input="$emit(\'update:value\', Number($event.target.value))" />' },
}

function render() {
  return mount(EventChannelManager, { props: { systemId: '10' }, global: { stubs } })
}

describe('EventChannelManager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(eventAdministrationApi.channels).mockResolvedValue([inbox, email, webhook])
    vi.mocked(eventAdministrationApi.updateChannel).mockImplementation(async (_system, channel, input) => ({
      ...(channel === 'EMAIL' ? email : webhook),
      enabled: input.enabled,
      version: (channel === 'EMAIL' ? email.version : webhook.version) + 1,
    }))
    vi.mocked(eventAdministrationApi.checkChannel).mockResolvedValue({
      channel: 'WEBHOOK', status: 'SENT', message: 'HTTPS handshake succeeded',
      traceId: 'trace-safe', checkedAt: '2026-08-06T08:12:00Z', durationMs: 21,
    })
  })

  it('renders only the safe projection and masked channel details', async () => {
    vi.mocked(eventAdministrationApi.channels).mockResolvedValueOnce([inbox, {
      ...webhook,
      endpoint: 'https://hooks.example.test/raw?token=must-not-render',
      smtpPassword: 'must-not-render-password',
      responseBody: 'must-not-render-response',
      rawSecret: 'must-not-render-secret',
    } as EventChannelConfiguration])
    const wrapper = render()
    await flushPromises()

    expect(wrapper.text()).toContain('https://hooks.example.test/…')
    expect(wrapper.text()).not.toContain('站内消息中心')
    expect(wrapper.text()).not.toContain('must-not-render')
    await wrapper.get('.channel-detail').trigger('click')
    expect(wrapper.text()).toContain('env://********')
    expect(wrapper.text()).toContain('安全边界')
  })

  it('keeps replacement endpoint and SecretRef blank, then submits only controlled settings', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.findAll('.channel-edit')[1]!.trigger('click')

    expect((wrapper.get('.channel-endpoint').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('.channel-secret-ref').element as HTMLInputElement).value).toBe('')
    await wrapper.get('.channel-endpoint').setValue('https://new.example.test/notify')
    await wrapper.get('.channel-secret-ref').setValue('env://EVENT_WEBHOOK_SECRET_V3')
    await wrapper.get('.channel-timeout').setValue('4500')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(eventAdministrationApi.updateChannel).toHaveBeenCalledWith('10', 'WEBHOOK', {
      enabled: true,
      expectedVersion: 4,
      endpoint: 'https://new.example.test/notify',
      secretRef: 'env://EVENT_WEBHOOK_SECRET_V3',
      timeoutMs: 4500,
    })
  })

  it('updates one controlled switch and executes a bounded connectivity check', async () => {
    const wrapper = render()
    await flushPromises()

    await wrapper.findAll('.channel-enabled-toggle')[0]!.trigger('click')
    await flushPromises()
    expect(eventAdministrationApi.updateChannel).toHaveBeenCalledWith('10', 'EMAIL', {
      enabled: true,
      expectedVersion: 2,
    })

    await wrapper.findAll('.channel-check')[1]!.trigger('click')
    await flushPromises()
    expect(eventAdministrationApi.checkChannel).toHaveBeenCalledWith('10', 'WEBHOOK')
    expect(eventAdministrationApi.channels).toHaveBeenCalledTimes(2)
  })
})
