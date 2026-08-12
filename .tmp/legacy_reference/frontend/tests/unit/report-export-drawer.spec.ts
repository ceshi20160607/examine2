import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import ReportExportDrawer from '@/components/report/ReportExportDrawer.vue'
import { runtimeReportApi } from '@/services/report'
import type { ReportExportTask } from '@/types/report'

vi.mock('@/services/report', () => ({ runtimeReportApi: {
  startExport: vi.fn(), exports: vi.fn(), exportTask: vi.fn(), downloadExport: vi.fn(),
  scheduledRuns: vi.fn(), scheduledRun: vi.fn(), downloadScheduledRun: vi.fn(),
} }))

const queued: ReportExportTask = {
  exportId: '70', reportCode: 'monthly_orders', reportVersionId: '31', reportVersionNumber: 2,
  dataSourceVersionId: '21', fieldCodes: ['recordNo', 'amount'], status: 'QUEUED', totalRows: null,
  processedRows: 0, truncated: false, jobId: '80', createdAt: '2026-08-04T00:00:00Z',
}
const succeeded: ReportExportTask = {
  ...queued, exportId: '71', status: 'SUCCEEDED', totalRows: 5300, processedRows: 5000,
  truncated: true, resultFilename: 'monthly-orders.xlsx', resultSize: 4096,
  startedAt: '2026-08-04T00:00:01Z', finishedAt: '2026-08-04T00:00:05Z',
}
const failed: ReportExportTask = {
  ...queued, exportId: '72', status: 'FAILED', failureCode: 'REPORT_EXPORT_ACCESS_REVOKED',
  failureMessage: '当前成员已无权读取报表', finishedAt: '2026-08-04T00:00:03Z',
}
const scheduled = {
  id: '90', scheduleId: '40', scheduleCode: 'weekly_orders', scheduleName: '每周订单',
  reportCode: 'monthly_orders', scheduledAt: '2026-08-04T01:30:00Z', status: 'SUCCEEDED' as const,
  exportId: '73', filename: 'weekly-orders.xlsx', totalRows: 20, processedRows: 20, truncated: false,
  createdAt: '2026-08-04T01:30:00Z', finishedAt: '2026-08-04T01:30:05Z',
}

function render() {
  return mount(ReportExportDrawer, {
    props: { open: true, systemId: '10', reportCode: 'monthly_orders', reportName: '月度订单' },
    global: { stubs: {
      'a-drawer': { props: ['open'], template: '<div v-if="open"><slot /></div>' },
      'a-button': { props: ['disabled', 'loading'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
      'a-alert': { props: ['message', 'description'], template: '<div class="alert-stub">{{ message }} {{ description }}</div>' },
      'a-spin': { template: '<div><slot /></div>' }, 'a-tag': { template: '<span><slot /></span>' },
      'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
    } },
  })
}

describe('ReportExportDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(runtimeReportApi.exports).mockResolvedValue({ items: [succeeded, failed], page: 1, size: 10, total: 12 })
    vi.mocked(runtimeReportApi.startExport).mockResolvedValue(queued)
    vi.mocked(runtimeReportApi.exportTask).mockImplementation(async (_systemId, _code, id) =>
      id === succeeded.exportId ? succeeded : failed)
    vi.mocked(runtimeReportApi.downloadExport).mockResolvedValue({
      blob: new Blob(['xlsx']), filename: 'monthly-orders.xlsx',
      mediaType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    vi.mocked(runtimeReportApi.scheduledRuns).mockResolvedValue({ items: [scheduled], page: 1, size: 10, total: 11 })
    vi.mocked(runtimeReportApi.scheduledRun).mockResolvedValue(scheduled)
    vi.mocked(runtimeReportApi.downloadScheduledRun).mockResolvedValue({
      blob: new Blob(['xlsx']), filename: 'weekly-orders.xlsx',
      mediaType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:report'), revokeObjectURL: vi.fn() })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  afterEach(() => vi.restoreAllMocks())

  it('reuses the same request key when an uncertain start is retried', async () => {
    vi.mocked(runtimeReportApi.startExport)
      .mockRejectedValueOnce(new Error('NETWORK_UNCERTAIN'))
      .mockResolvedValueOnce(queued)
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.report-export-start').trigger('click')
    await flushPromises()
    const firstKey = vi.mocked(runtimeReportApi.startExport).mock.calls[0]![2]
    expect(wrapper.get('.report-export-error').text()).toContain('NETWORK_UNCERTAIN')
    await wrapper.get('.report-export-start').trigger('click')
    await flushPromises()
    expect(vi.mocked(runtimeReportApi.startExport).mock.calls[1]![2]).toBe(firstKey)
    expect(wrapper.get('.export-current-card').text()).toContain('排队中')
  })

  it('shows completed, truncated and failed states and downloads only a completed file', async () => {
    const wrapper = render()
    await flushPromises()
    const rows = wrapper.findAll('.export-history-card .export-history-table tbody tr')
    expect(rows).toHaveLength(2)

    await rows[0]!.trigger('click')
    await flushPromises()
    expect(wrapper.get('.export-current-card').text()).toContain('已完成')
    expect(wrapper.get('.report-export-truncated').text()).toContain('5,000')
    expect(wrapper.get('.export-current-card').text()).toContain('monthly-orders.xlsx')
    await wrapper.get('.report-export-download').trigger('click')
    await flushPromises()
    expect(runtimeReportApi.downloadExport).toHaveBeenCalledWith('10', 'monthly_orders', '71')
    expect(URL.createObjectURL).toHaveBeenCalled()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:report')

    await wrapper.findAll('.export-history-card .export-history-table tbody tr')[1]!.trigger('click')
    await flushPromises()
    expect(wrapper.get('.report-export-failed').text()).toContain('REPORT_EXPORT_ACCESS_REVOKED')
    expect(wrapper.find('.report-export-download').exists()).toBe(false)
  })

  it('pages durable history independently from the report row table', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.export-history-next').trigger('click')
    await flushPromises()
    expect(runtimeReportApi.exports).toHaveBeenLastCalledWith('10', 'monthly_orders', 2, 10)
  })

  it('separates member-delivered scheduled runs and downloads their authorized result', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('手工导出历史')
    expect(wrapper.text()).toContain('调度送达历史')
    expect(runtimeReportApi.scheduledRuns).toHaveBeenCalledWith('10', 'monthly_orders', 1, 10)

    await wrapper.get('.scheduled-run-table tbody tr').trigger('click')
    await flushPromises()
    expect(runtimeReportApi.scheduledRun).toHaveBeenCalledWith('10', 'monthly_orders', '90')
    expect(wrapper.get('.scheduled-current-card').text()).toContain('调度 · 已送达')
    await wrapper.get('.scheduled-run-download').trigger('click')
    await flushPromises()
    expect(runtimeReportApi.downloadScheduledRun).toHaveBeenCalledWith('10', 'monthly_orders', '90')

    await wrapper.get('.scheduled-history-next').trigger('click')
    await flushPromises()
    expect(runtimeReportApi.scheduledRuns).toHaveBeenLastCalledWith('10', 'monthly_orders', 2, 10)
  })
})
