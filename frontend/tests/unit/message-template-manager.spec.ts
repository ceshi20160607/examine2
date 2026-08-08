import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import MessageTemplateManager from '@/components/admin/MessageTemplateManager.vue'
import { messageTemplateApi } from '@/services/event'
import type { MessageTemplate } from '@/types/event'

vi.mock('ant-design-vue', () => ({ message: { success: vi.fn() } }))
vi.mock('@/services/event', () => ({
  messageTemplateApi: { list: vi.fn(), update: vi.fn(), publish: vi.fn() },
}))

const template: MessageTemplate = {
  templateCode: 'MODULE_EXPORT_SUCCEEDED', eventType: 'MODULE_EXPORT_SUCCEEDED', name: '导出完成',
  enabled: true, titleTemplate: '导出已完成', bodyTemplate: '{moduleCode} 已导出 {rows} 条记录。',
  channels: ['INBOX'], allowedVariables: ['moduleCode', 'rows'], publishedVersion: 1,
  publishedSourceDraftVersion: 1, publishedEnabled: true, version: 1, updatedAt: '2026-07-29T15:00:00',
}

function render() {
  return mount(MessageTemplateManager, { props: { systemId: '10' }, global: { stubs: {
    BellRing: true, Pencil: true, RefreshCw: true, Rocket: true,
    'a-alert': true, 'a-spin': { template: '<div><slot /></div>' },
    'a-button': { props: ['loading'], emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
    'a-tag': { template: '<span><slot /></span>' }, 'a-form': { emits: ['finish'], template: '<form @submit.prevent="$emit(\'finish\')"><slot /></form>' },
    'a-form-item': { template: '<label><slot /></label>' }, 'a-input': true, 'a-switch': true, 'a-textarea': true,
    'a-checkbox-group': {
      props: ['value', 'options'],
      emits: ['update:value'],
      template: '<button type="button" class="choose-all-channels" @click="$emit(\'update:value\', [\'INBOX\', \'EMAIL\', \'WEBHOOK\'])">channels</button>',
    },
  } } })
}

describe('MessageTemplateManager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(messageTemplateApi.list).mockResolvedValue([template])
    vi.mocked(messageTemplateApi.update).mockResolvedValue({
      ...template,
      channels: ['INBOX', 'EMAIL', 'WEBHOOK'],
      version: 2,
    })
    vi.mocked(messageTemplateApi.publish).mockResolvedValue({ ...template, publishedVersion: 2, version: 2 })
  })

  it('shows publication state, available variables and publishes the selected draft version', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('MODULE_EXPORT_SUCCEEDED')
    expect(wrapper.text()).toContain('运行 V1')

    await wrapper.findAll('button').find((button) => button.text().includes('编辑'))!.trigger('click')
    expect(wrapper.text()).toContain('{moduleCode}')
    await wrapper.findAll('button').find((button) => button.text().includes('发布'))!.trigger('click')
    await flushPromises()

    expect(messageTemplateApi.publish).toHaveBeenCalledWith('10', 'MODULE_EXPORT_SUCCEEDED', 1)
    expect(messageTemplateApi.list).toHaveBeenCalledTimes(2)
  })

  it('saves the controlled non-empty multi-channel set instead of forcing INBOX', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text().includes('编辑'))!.trigger('click')
    await wrapper.get('.choose-all-channels').trigger('click')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(messageTemplateApi.update).toHaveBeenCalledWith('10', 'MODULE_EXPORT_SUCCEEDED', {
      expectedVersion: 1,
      name: '导出完成',
      enabled: true,
      titleTemplate: '导出已完成',
      bodyTemplate: '{moduleCode} 已导出 {rows} 条记录。',
      channels: ['INBOX', 'EMAIL', 'WEBHOOK'],
    })
  })
})
