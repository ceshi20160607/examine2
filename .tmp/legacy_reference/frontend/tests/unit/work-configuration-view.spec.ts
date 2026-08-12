import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { configApi } from '@/services/config'
import { workApi } from '@/services/work'
import type { WorkConfigurationVersion } from '@/types/work'
import WorkConfigurationView from '@/views/system/admin/WorkConfigurationView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
vi.mock('vue-router', async importOriginal => {
  const original = await importOriginal<typeof import('vue-router')>()
  return { ...original, useRoute: () => route }
})
vi.mock('@/services/config', () => ({ configApi: { dictionaries: vi.fn() } }))
vi.mock('@/services/work', () => ({ workApi: {
  workConfigurationHistory: vi.fn(),
  createWorkConfigurationDraft: vi.fn(),
  checkWorkConfiguration: vi.fn(),
  publishWorkConfiguration: vi.fn(),
  rollbackWorkConfiguration: vi.fn(),
} }))

const draft: WorkConfigurationVersion = {
  id: '701', revision: 3, status: 'DRAFT', rollbackFromRevision: null,
  createdBy: '30', createdAt: '2026-08-06T08:00:00Z', publishedBy: null, publishedAt: null, version: 1,
  snapshot: { fields: {
    PROJECT_TASK: [{
      code: 'priority', name: '优先级', type: 'SELECT', required: true,
      dictionaryCode: 'work_priority', readPermission: null, editPermission: 'work.task.manage',
      cardVisible: true, kanbanRole: 'COLUMN',
    }],
    ORDINARY_TASK: [], DAILY_REPORT: [],
  } },
}

const published: WorkConfigurationVersion = {
  ...draft, id: '702', revision: 4, status: 'PUBLISHED', version: 2,
  publishedBy: '30', publishedAt: '2026-08-06T09:00:00Z',
}

const stubs = {
  AdminPageHeader: { props: ['title', 'description'], template: '<header><h1>{{ title }}</h1><p>{{ description }}</p><slot name="actions" /></header>' },
  'a-alert': { props: ['message'], template: '<div class="alert-stub">{{ message }}</div>' },
  'a-button': {
    inheritAttrs: false, props: ['disabled', 'loading'], emits: ['click'],
    template: '<button v-bind="$attrs" :disabled="disabled" :data-loading="loading" @click="$emit(\'click\')"><slot /></button>',
  },
  'a-tabs': { template: '<div><slot /></div>' },
  'a-tab-pane': { props: ['tab'], template: '<section><h2>{{ tab }}</h2><slot /></section>' },
  'a-segmented': true,
  'a-input': { props: ['value'], emits: ['update:value'], template: '<input :value="value" @input="$emit(\'update:value\', $event.target.value)" />' },
  'a-checkbox': { props: ['checked'], emits: ['update:checked'], template: '<label><input type="checkbox" :checked="checked" @change="$emit(\'update:checked\', $event.target.checked)" /><slot /></label>' },
  'a-select': { props: ['value'], emits: ['update:value', 'change'], template: '<select :value="value" @change="$emit(\'update:value\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>' },
  'a-select-option': { props: ['value'], template: '<option :value="value"><slot /></option>' },
  'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
  'a-tag': { template: '<span><slot /></span>' },
  'a-table': { template: '<div class="history-table-stub" />' },
  'a-table-column': true,
}

function render() {
  return mount(WorkConfigurationView, { global: { stubs } })
}

describe('WorkConfigurationView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(workApi.workConfigurationHistory).mockResolvedValue({ items: [draft] })
    vi.mocked(configApi.dictionaries).mockResolvedValue([{
      id: '81', code: 'work_priority', name: '工作优先级', type: 'LIST', status: 'ENABLED',
      version: '1', updatedRevision: '1',
    }])
    vi.mocked(workApi.createWorkConfigurationDraft).mockResolvedValue({ ...draft, id: '703', revision: 5 })
    vi.mocked(workApi.checkWorkConfiguration).mockResolvedValue({
      revision: 5, ready: true, checkedBy: 30, checkedAt: '2026-08-06T09:30:00Z',
    })
    vi.mocked(workApi.publishWorkConfiguration).mockResolvedValue(published)
    vi.mocked(workApi.rollbackWorkConfiguration).mockResolvedValue(published)
  })

  it('loads history and dictionaries, then completes draft, check and publish in order', async () => {
    const wrapper = render()
    await flushPromises()

    expect(workApi.workConfigurationHistory).toHaveBeenCalledWith('10')
    expect(configApi.dictionaries).toHaveBeenCalledWith('10')
    expect(wrapper.text()).toContain('优先级')
    expect(wrapper.text()).toContain('work_priority')

    await wrapper.get('.work-config-save').trigger('click')
    await flushPromises()
    expect(workApi.createWorkConfigurationDraft).toHaveBeenCalledWith('10', draft.snapshot)

    await wrapper.get('.work-config-check').trigger('click')
    await flushPromises()
    expect(workApi.checkWorkConfiguration).toHaveBeenCalledWith('10', '703', 1)
    expect(wrapper.text()).toContain('发布检查通过')

    await wrapper.get('.work-config-publish').trigger('click')
    await flushPromises()
    expect(workApi.publishWorkConfiguration).toHaveBeenCalledWith('10', '703', 1)
    expect(workApi.workConfigurationHistory).toHaveBeenCalledTimes(2)
  })

  it('rejects an invalid field code locally without creating a draft', async () => {
    const wrapper = render()
    await flushPromises()
    const codeInputs = wrapper.findAll('input').filter(input => input.element.getAttribute('type') !== 'checkbox')
    await codeInputs[1]!.setValue('Invalid Code')
    await wrapper.get('.work-config-save').trigger('click')
    await flushPromises()

    expect(workApi.createWorkConfigurationDraft).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('存在无效字段编码或名称')
  })
})
