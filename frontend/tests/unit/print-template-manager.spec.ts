import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PrintTemplateManager from '@/components/admin/PrintTemplateManager.vue'
import { printTemplateApi } from '@/services/print'
import type { ConfigField } from '@/types/config'
import type { PrintTemplate } from '@/types/print'

vi.mock('ant-design-vue', () => ({ message: { success: vi.fn() } }))
vi.mock('@/services/print', () => ({
  printTemplateApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), publish: vi.fn() },
}))

const template: PrintTemplate = {
  templateId: '70', moduleId: '20', moduleCode: 'contract', code: 'contract_print', name: '合同打印',
  status: 'ENABLED', paperSize: 'A4', orientation: 'PORTRAIT',
  definition: { title: '{title}', fieldCodes: ['name'], footer: '内部打印' },
  publishedVersionId: '71', publishedVersionNo: 1, publishedSchemaVersionId: '40',
  version: 2, updatedAt: '2026-07-29T14:00:00',
}
const fields: ConfigField[] = [
  { id: '1', moduleId: '20', code: 'name', name: '名称', type: 'TEXT', sortOrder: 1, required: true,
    hidden: false, readonly: false, searchable: true, filterable: true, showInList: true, showInDetail: true,
    indexMode: 'FILTER', status: 'ENABLED', readPermissionMode: 'INHERIT', writePermissionMode: 'INHERIT', properties: {}, version: '1', updatedRevision: '1' },
  { id: '2', moduleId: '20', code: 'secret', name: '密钥', type: 'SECRET', sortOrder: 2, required: false,
    hidden: false, readonly: false, searchable: false, filterable: false, showInList: false, showInDetail: true,
    indexMode: 'NONE', status: 'ENABLED', readPermissionMode: 'INHERIT', writePermissionMode: 'INHERIT', properties: {}, version: '1', updatedRevision: '1' },
  { id: '3', moduleId: '20', code: 'lines', name: '合同明细', type: 'SUBTABLE', sortOrder: 3, required: false,
    hidden: false, readonly: false, searchable: false, filterable: false, showInList: false, showInDetail: true,
    indexMode: 'NONE', status: 'ENABLED', readPermissionMode: 'INHERIT', writePermissionMode: 'INHERIT', properties: {}, version: '1', updatedRevision: '1' },
]

function render() {
  return mount(PrintTemplateManager, {
    props: { systemId: '10', moduleId: '20', fields },
    global: { stubs: {
      FileText: true, Pencil: true, Plus: true, RefreshCw: true, Send: true, Trash2: true,
      'a-alert': { props: ['message'], template: '<div>{{ message }}</div>' },
      'a-button': { props: ['loading', 'disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
      'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
      'a-form': { template: '<form><slot /></form>' }, 'a-form-item': { template: '<label><slot /></label>' },
      'a-input': true, 'a-textarea': true, 'a-select': { template: '<select><slot /></select>' },
      'a-input-number': true,
      'a-select-option': { template: '<option><slot /></option>' },
      'a-modal': { props: ['open'], template: '<div v-if="open"><slot /></div>' },
      'a-spin': { template: '<div><slot /></div>' }, 'a-tag': { template: '<span><slot /></span>' },
    } },
  })
}

describe('PrintTemplateManager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(printTemplateApi.list).mockResolvedValue([template])
    vi.mocked(printTemplateApi.publish).mockResolvedValue({ ...template, publishedVersionNo: 2, version: 3 })
  })

  it('lists immutable publication state, excludes protected fields and publishes the current version', async () => {
    const wrapper = render()
    await flushPromises()

    expect(wrapper.text()).toContain('合同打印')
    expect(wrapper.text()).toContain('已发布 V1')
    const publish = wrapper.findAll('button').find((button) => button.text().includes('发布'))
    await publish!.trigger('click')
    await flushPromises()

    expect(printTemplateApi.publish).toHaveBeenCalledWith('10', '20', '70', 2)
    expect(printTemplateApi.list).toHaveBeenCalledTimes(2)

    const create = wrapper.findAll('button').find((button) => button.text().includes('新建模板'))
    await create!.trigger('click')
    expect(wrapper.text()).toContain('名称 · TEXT')
    expect(wrapper.text()).toContain('合同明细 · SUBTABLE')
    expect(wrapper.text()).not.toContain('密钥 · secret')
  })
})
