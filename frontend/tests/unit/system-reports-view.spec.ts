import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { runtimeReportApi } from '@/services/report'
import type { RuntimeReportMetadata, RuntimeReportRows } from '@/types/report'
import SystemReportsView from '@/views/system/SystemReportsView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10', reportCode: undefined as string | undefined } }))
const replace = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ replace }) }))
vi.mock('@/services/report', () => ({ runtimeReportApi: { list: vi.fn(), metadata: vi.fn(), rows: vi.fn() } }))

const metadata: RuntimeReportMetadata = {
  id: '30', code: 'monthly_orders', name: '月度订单', description: '经营数据', versionId: '31', versionNumber: 2,
  dataSourceId: '20', dataSourceCode: 'orders_source', dataSourceName: '订单源', dataSourceVersionId: '21', dataSourceVersionNumber: 3,
  moduleId: '40', moduleCode: 'orders', schemaVersionId: 'schema-2',
  fields: [{ fieldCode: 'recordNo', fieldName: '单号', type: 'TEXT' }, { fieldCode: 'amount', fieldName: '金额', type: 'DECIMAL' }],
}
const page: RuntimeReportRows = {
  page: 1, size: 20, total: 21, queryHash: 'query-hash-1',
  rows: [{ recordId: '40', version: 1, status: 'ACTIVE', values: [
    { fieldCode: 'recordNo', canonicalValue: 'SO-1', displayValue: '订单 SO-1' },
    { fieldCode: 'amount', canonicalValue: '10000000000000000000.123', displayValue: null },
  ] }], items: [],
}

function render() {
  return mount(SystemReportsView, { global: { stubs: {
    'a-button': { props: ['disabled', 'loading'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
    'a-alert': { props: ['message', 'description'], template: '<div class="alert-stub">{{ message }} {{ description }}</div>' },
    'a-spin': { template: '<div><slot /></div>' }, 'a-tag': { template: '<span><slot /></span>' },
    'a-empty': { props: ['description'], template: '<div class="empty-stub">{{ description }}</div>' },
    ReportExportDrawer: { props: ['open', 'reportCode'], template: '<div v-if="open" class="export-drawer-stub">{{ reportCode }}</div>' },
  } } })
}

describe('SystemReportsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.params.reportCode = undefined
    vi.mocked(runtimeReportApi.list).mockResolvedValue([metadata])
    vi.mocked(runtimeReportApi.metadata).mockResolvedValue(metadata)
    vi.mocked(runtimeReportApi.rows).mockResolvedValue(page)
  })

  it('runs the first published report and preserves canonical/display wire values', async () => {
    const wrapper = render()
    await flushPromises()

    expect(runtimeReportApi.list).toHaveBeenCalledWith('10')
    expect(runtimeReportApi.metadata).toHaveBeenCalledWith('10', 'monthly_orders')
    expect(runtimeReportApi.rows).toHaveBeenCalledWith('10', 'monthly_orders', 1, 20)
    expect(wrapper.get('.runtime-report-table').text()).toContain('订单 SO-1')
    expect(wrapper.get('.runtime-report-table').text()).toContain('10000000000000000000.123')
    expect(wrapper.text()).toContain('query-hash-1')
    await wrapper.get('.runtime-report-export').trigger('click')
    expect(wrapper.get('.export-drawer-stub').text()).toContain('monthly_orders')

    await wrapper.get('.report-page-next').trigger('click')
    await flushPromises()
    expect(runtimeReportApi.rows).toHaveBeenLastCalledWith('10', 'monthly_orders', 2, 20)
  })

  it('keeps empty authorized rows, empty catalog and runtime errors distinct', async () => {
    vi.mocked(runtimeReportApi.rows).mockResolvedValueOnce({ ...page, rows: [], total: 0 })
    const emptyRows = render()
    await flushPromises()
    expect(emptyRows.get('.runtime-report-empty').text()).toContain('当前权限和筛选范围内没有数据')

    vi.mocked(runtimeReportApi.list).mockResolvedValueOnce([])
    const emptyCatalog = render()
    await flushPromises()
    expect(emptyCatalog.get('.runtime-report-catalog-empty').text()).toContain('没有可运行的已发布报表')

    vi.mocked(runtimeReportApi.list).mockResolvedValueOnce([metadata])
    vi.mocked(runtimeReportApi.rows).mockRejectedValueOnce(new Error('REPORT_SOURCE_STALE'))
    const failed = render()
    await flushPromises()
    expect(failed.get('.runtime-report-error').text()).toContain('REPORT_SOURCE_STALE')
  })
})
