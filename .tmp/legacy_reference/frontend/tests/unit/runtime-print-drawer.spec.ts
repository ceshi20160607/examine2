import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import RuntimePrintDrawer from '@/components/runtime/RuntimePrintDrawer.vue'
import { runtimePrintApi } from '@/services/print'
import type { RuntimePrintTask, RuntimePrintTemplate } from '@/types/print'

vi.mock('@/services/print', () => ({
  runtimePrintApi: {
    templates: vi.fn(), preview: vi.fn(), create: vi.fn(), history: vi.fn(), task: vi.fn(), resultPdf: vi.fn(),
  },
}))

const template: RuntimePrintTemplate = {
  templateId: '70', code: 'contract_print', name: '合同打印', paperSize: 'A4', orientation: 'PORTRAIT',
  templateVersionId: '71', templateVersionNo: 1, schemaVersionId: '40',
}
const queued: RuntimePrintTask = {
  printId: '80', recordId: '30', recordVersion: 4, recordNo: 'CON-30', templateCode: 'contract_print',
  templateName: '合同打印', templateVersionId: '71', templateVersionNo: 1, schemaVersionId: '40',
  status: 'QUEUED', jobId: '90', createdAt: '2026-07-29T14:00:00',
}
const succeeded: RuntimePrintTask = { ...queued, status: 'SUCCEEDED', resultFilename: 'contract.pdf', resultSize: 900 }

function render() {
  return mount(RuntimePrintDrawer, {
    props: { open: true, systemId: '10', moduleCode: 'contract', recordId: '30', recordVersion: 4, recordTitle: '合同 A' },
    global: { stubs: {
      Download: true, FileText: true, History: true, Printer: true, RefreshCw: true,
      'a-alert': { props: ['message'], template: '<div>{{ message }}</div>' },
      'a-button': { props: ['loading', 'disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
      'a-drawer': { props: ['open'], template: '<div v-if="open"><slot /></div>' },
      'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
      'a-select': { template: '<select><slot /></select>' }, 'a-select-option': { template: '<option><slot /></option>' },
      'a-spin': { template: '<div><slot /></div>' }, 'a-tag': { template: '<span><slot /></span>' },
    } },
  })
}

function button(wrapper: ReturnType<typeof render>, text: string) {
  const target = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!target) throw new Error(`Missing button ${text}`)
  return target
}

describe('RuntimePrintDrawer', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    vi.mocked(runtimePrintApi.templates).mockResolvedValue([template])
    vi.mocked(runtimePrintApi.history).mockResolvedValue({ items: [queued], page: 1, size: 20, total: 1 })
    vi.mocked(runtimePrintApi.preview).mockResolvedValue({
      templateCode: 'contract_print', templateVersionId: '71', templateVersionNo: 1,
      recordId: '30', recordVersion: 4, html: '<!doctype html><p>safe preview</p>',
    })
    vi.mocked(runtimePrintApi.create).mockResolvedValue(queued)
    vi.mocked(runtimePrintApi.task).mockResolvedValue(succeeded)
    vi.mocked(runtimePrintApi.resultPdf).mockResolvedValue({
      blob: new Blob(['%PDF']), filename: 'contract.pdf', mediaType: 'application/pdf',
    })
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:print') })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  })

  afterEach(() => vi.useRealTimers())

  it('previews the frozen version, creates and polls PDF, reopens history and downloads', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('合同打印')
    expect(wrapper.text()).toContain('模板版本 71')

    await button(wrapper, '生成预览').trigger('click')
    await flushPromises()
    expect(runtimePrintApi.preview).toHaveBeenCalledWith('10', 'contract', '30', 'contract_print')
    expect(wrapper.find('iframe').attributes('srcdoc')).toContain('safe preview')

    await button(wrapper, '生成 PDF').trigger('click')
    await flushPromises()
    expect(runtimePrintApi.create).toHaveBeenCalledWith('10', 'contract', '30', 'contract_print', 4)

    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(wrapper.text()).toContain('已完成')

    await button(wrapper, '下载 PDF').trigger('click')
    await flushPromises()
    expect(runtimePrintApi.resultPdf).toHaveBeenCalledWith('10', 'contract', '30', '80')

    const history = wrapper.findAll('.print-history > button').find((item) => item.text().includes('合同打印'))
    await history!.trigger('click')
    expect(wrapper.text()).toContain('排队中')
  })
})
