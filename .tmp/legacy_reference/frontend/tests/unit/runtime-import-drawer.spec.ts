import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import RuntimeImportDrawer from '@/components/runtime/RuntimeImportDrawer.vue'
import { runtimeImportApi } from '@/services/runtimeImport'
import type { RuntimeImportBatch } from '@/types/runtimeImport'

vi.mock('@/services/runtimeImport', () => ({
  runtimeImportApi: {
    template: vi.fn(),
    templateWorkbook: vi.fn(),
    preview: vi.fn(),
    previewWorkbook: vi.fn(),
    history: vi.fn(),
    batch: vi.fn(),
    commit: vi.fn(),
    rollback: vi.fn(),
    errorWorkbook: vi.fn(),
  },
}))

const queued: RuntimeImportBatch = {
  batchId: '300', moduleCode: 'work_order', schemaVersionId: '40', mode: 'NEW',
  status: 'PREVIEW_QUEUED', totalRows: 1, newRows: 0, updateRows: 0, failedRows: 0,
  previewJobId: '500', rows: [{ rowNumber: 1, status: 'PREVIEW_PENDING' }],
}

function withStatus(status: RuntimeImportBatch['status'], overrides: Partial<RuntimeImportBatch> = {}) {
  return { ...queued, status, ...overrides }
}

function render() {
  return mount(RuntimeImportDrawer, {
    props: { open: true, systemId: '10', moduleCode: 'work_order', moduleName: '工单' },
    global: {
      stubs: {
        CheckCircle2: true,
        Download: true,
        FileSpreadsheet: true,
        History: true,
        RefreshCw: true,
        RotateCcw: true,
        Send: true,
        Upload: true,
        XCircle: true,
        'a-alert': { props: ['message', 'description'], template: '<div class="alert">{{ message }} {{ description }}</div>' },
        'a-button': {
          inheritAttrs: false,
          props: ['disabled', 'loading'],
          emits: ['click'],
          template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
        },
        'a-drawer': { props: ['open'], emits: ['close'], template: '<div v-if="open"><slot /><slot name="footer" /></div>' },
        'a-select': { template: '<div><slot /></div>' },
        'a-select-option': true,
        'a-segmented': {
          props: ['value', 'options'],
          emits: ['update:value'],
          template: '<div><button v-for="option in options" :key="option.value" @click="$emit(\'update:value\', option.value)">{{ option.label }}</button></div>',
        },
        'a-spin': { template: '<div><slot /></div>' },
        'a-tag': { template: '<span><slot /></span>' },
        'a-textarea': {
          props: ['value'],
          emits: ['update:value'],
          template: '<textarea class="json-source" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
        },
      },
    },
  })
}

function button(wrapper: ReturnType<typeof render>, label: string) {
  const target = wrapper.findAll('button').find((item) => item.text().includes(label))
  if (!target) throw new Error(`Missing button: ${label}`)
  return target
}

describe('RuntimeImportDrawer', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    vi.mocked(runtimeImportApi.template).mockResolvedValue({
      schemaVersionId: '40', moduleSnapshotId: '41', checksum: 'a'.repeat(64), maxRows: 200,
      modes: ['NEW', 'UPSERT'],
      fields: [
        { fieldCode: 'external_key', fieldName: '外部编号', type: 'URL', required: true, unique: true },
        { fieldCode: 'title', fieldName: '标题', type: 'TEXT', required: true, unique: false },
      ],
      excludedFields: [],
    })
    vi.mocked(runtimeImportApi.history).mockResolvedValue({ items: [], page: 1, size: 20, total: 0 })
  })

  afterEach(() => vi.useRealTimers())

  it('previews, polls, commits and conditionally rolls back one batch', async () => {
    vi.mocked(runtimeImportApi.preview).mockResolvedValue(queued)
    vi.mocked(runtimeImportApi.batch)
      .mockResolvedValueOnce(withStatus('READY', {
        newRows: 1,
        rows: [{ rowNumber: 1, action: 'NEW', status: 'VALID' }],
      }))
      .mockResolvedValueOnce(withStatus('COMMITTED', {
        newRows: 1, commitJobId: '501',
        rows: [{ rowNumber: 1, action: 'NEW', status: 'COMMITTED', targetRecordId: '700' }],
      }))
      .mockResolvedValueOnce(withStatus('ROLLED_BACK', {
        newRows: 1, commitJobId: '501', rollbackJobId: '502',
        rows: [{ rowNumber: 1, action: 'NEW', status: 'ROLLED_BACK', targetRecordId: '700' }],
      }))
    vi.mocked(runtimeImportApi.commit).mockResolvedValue(withStatus('COMMIT_QUEUED', { commitJobId: '501' }))
    vi.mocked(runtimeImportApi.rollback).mockResolvedValue(withStatus('ROLLBACK_QUEUED', {
      commitJobId: '501', rollbackJobId: '502',
    }))

    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('外部编号 · external_key · 唯一')

    await button(wrapper, 'JSON（高级）').trigger('click')
    await wrapper.find('textarea').setValue('[{"external_key":"WO-1","title":"第一条"}]')
    await button(wrapper, '开始预检').trigger('click')
    await flushPromises()
    expect(runtimeImportApi.preview).toHaveBeenCalledWith('10', 'work_order', {
      mode: 'NEW', matchFieldCode: undefined,
      rows: [{ external_key: 'WO-1', title: '第一条' }],
    })

    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(wrapper.text()).toContain('预检通过')
    expect(wrapper.text()).toContain('新增 1')

    await button(wrapper, '提交写入').trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(wrapper.text()).toContain('已提交')
    expect(wrapper.emitted('changed')).toHaveLength(1)

    await button(wrapper, '安全回滚').trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(wrapper.text()).toContain('已回滚')
    expect(wrapper.emitted('changed')).toHaveLength(2)
  })

  it('rejects malformed JSON before creating a durable preview job', async () => {
    const wrapper = render()
    await flushPromises()
    await button(wrapper, 'JSON（高级）').trigger('click')
    await wrapper.find('textarea').setValue('{bad-json')
    await button(wrapper, '开始预检').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请输入有效的 JSON 数组')
    expect(runtimeImportApi.preview).not.toHaveBeenCalled()
  })

  it('downloads the live template, previews Excel, exposes errors and reopens recent batches', async () => {
    const invalid = withStatus('INVALID', {
      failedRows: 1,
      rows: [{ rowNumber: 1, status: 'INVALID', errorCode: 'IMPORT_DUPLICATE', errorMessage: '编号重复' }],
    })
    vi.mocked(runtimeImportApi.history).mockResolvedValue({ items: [invalid], page: 1, size: 20, total: 1 })
    vi.mocked(runtimeImportApi.templateWorkbook).mockResolvedValue({
      blob: new Blob(['template']), filename: 'work_order-import-template.xlsx', mediaType: 'application/octet-stream',
    })
    vi.mocked(runtimeImportApi.previewWorkbook).mockResolvedValue(queued)
    vi.mocked(runtimeImportApi.batch).mockResolvedValue(invalid)
    vi.mocked(runtimeImportApi.errorWorkbook).mockResolvedValue({
      blob: new Blob(['errors']), filename: 'work_order-import-errors.xlsx', mediaType: 'application/octet-stream',
    })
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:test') })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)

    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('最近导入')
    expect(wrapper.text()).toContain('预检未通过')
    await button(wrapper, '下载当前模板').trigger('click')
    await flushPromises()
    expect(runtimeImportApi.templateWorkbook).toHaveBeenCalledWith('10', 'work_order')

    const file = new File(['xlsx'], 'items.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
    await input.trigger('change')
    await button(wrapper, '开始预检').trigger('click')
    await flushPromises()
    expect(runtimeImportApi.previewWorkbook).toHaveBeenCalledWith(
      '10', 'work_order', file, 'NEW', undefined,
    )

    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(wrapper.text()).toContain('编号重复')
    await button(wrapper, '下载错误行').trigger('click')
    await flushPromises()
    expect(runtimeImportApi.errorWorkbook).toHaveBeenCalledWith('10', 'work_order', '300')

    const historyButton = wrapper.findAll('.import-history > button').find((item) => item.text().includes('300'))
    expect(historyButton).toBeTruthy()
    await historyButton!.trigger('click')
    expect(wrapper.text()).toContain('预检未通过')
  })
})
