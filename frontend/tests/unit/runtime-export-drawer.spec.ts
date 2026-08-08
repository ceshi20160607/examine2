import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import RuntimeExportDrawer from '@/components/runtime/RuntimeExportDrawer.vue'
import { runtimeExportApi } from '@/services/runtimeExport'
import type { RuntimeFieldCapability, RuntimeRecordQuery } from '@/types/config'
import type { RuntimeExportTask } from '@/types/runtimeExport'

vi.mock('@/services/runtimeExport', () => ({
  runtimeExportApi: {
    create: vi.fn(), history: vi.fn(), task: vi.fn(), resultWorkbook: vi.fn(),
  },
}))

const query: RuntimeRecordQuery = {
  schemaVersionId: '40', page: 3, size: 50, recordScope: 'active', q: 'pump',
  filter: null, sort: [], columns: ['name'], viewId: '80',
}
const fields: RuntimeFieldCapability[] = [
  {
    fieldCode: 'name', fieldName: '名称', logicalFieldId: '41', type: 'TEXT', mode: 'WRITABLE',
    readable: true, writable: true, sensitiveReadable: false, sensitiveQueryable: false, masked: false,
    operators: ['EQ'], sortable: true, showInList: true, showInDetail: true, options: [], schema: {},
  },
  {
    fieldCode: 'secret', fieldName: '秘密', logicalFieldId: '42', type: 'SECRET', mode: 'MASKED',
    readable: true, writable: false, sensitiveReadable: false, sensitiveQueryable: false, masked: true,
    operators: [], sortable: false, showInList: true, showInDetail: true, options: [], schema: {},
  },
]
const queued: RuntimeExportTask = {
  exportId: '700', moduleCode: 'work_order', schemaVersionId: '40', queryHash: 'hash',
  fieldCodes: ['name'], status: 'QUEUED', processedRows: 0, jobId: '800', createdAt: '2026-07-29T12:00:00',
}
const succeeded: RuntimeExportTask = {
  ...queued, status: 'SUCCEEDED', totalRows: 2, processedRows: 2,
  resultFilename: 'work_order-export-700.xlsx', resultSize: 2048,
}

function render() {
  return mount(RuntimeExportDrawer, {
    props: {
      open: true, systemId: '10', moduleCode: 'work_order', moduleName: '工单',
      query, fields, defaultFieldCodes: ['name', 'secret'],
    },
    global: {
      stubs: {
        CheckCircle2: true, Download: true, FileSpreadsheet: true, History: true,
        RefreshCw: true, Send: true, XCircle: true,
        'a-alert': { props: ['message'], template: '<div class="alert">{{ message }}</div>' },
        'a-button': {
          inheritAttrs: false, props: ['disabled', 'loading'], emits: ['click'],
          template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
        },
        'a-drawer': { props: ['open'], emits: ['close'], template: '<div v-if="open"><slot /></div>' },
        'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
        'a-spin': { template: '<div><slot /></div>' },
        'a-tag': { template: '<span><slot /></span>' },
      },
    },
  })
}

function button(wrapper: ReturnType<typeof render>, text: string) {
  const target = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!target) throw new Error(`Missing button ${text}`)
  return target
}

describe('RuntimeExportDrawer', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    vi.mocked(runtimeExportApi.history).mockResolvedValue({ items: [queued], page: 1, size: 20, total: 1 })
    vi.mocked(runtimeExportApi.create).mockResolvedValue(queued)
    vi.mocked(runtimeExportApi.task).mockResolvedValue(succeeded)
    vi.mocked(runtimeExportApi.resultWorkbook).mockResolvedValue({
      blob: new Blob(['xlsx']), filename: 'work_order-export-700.xlsx', mediaType: 'application/octet-stream',
    })
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:export') })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  })

  afterEach(() => vi.useRealTimers())

  it('submits the current query, polls, reopens history and downloads a successful workbook', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('最近导出')
    expect(wrapper.text()).toContain('名称')
    expect(wrapper.text()).not.toContain('秘密')

    await button(wrapper, '创建导出任务').trigger('click')
    await flushPromises()
    expect(runtimeExportApi.create).toHaveBeenCalledWith('10', 'work_order', {
      query: { ...query, page: 1, size: 200, columns: ['name'], viewId: null },
      fieldCodes: ['name'],
    })

    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('2 / 2 条')

    await button(wrapper, '下载 XLSX').trigger('click')
    await flushPromises()
    expect(runtimeExportApi.resultWorkbook).toHaveBeenCalledWith('10', 'work_order', '700')

    const recent = wrapper.findAll('.export-history > button').find((item) => item.text().includes('700'))
    expect(recent).toBeTruthy()
    await recent!.trigger('click')
    expect(wrapper.text()).toContain('排队中')
  })
})
