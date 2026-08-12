import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { dashboardAdminApi, runtimeDashboardApi } from '@/services/dashboard'
import { dataSourceAdminApi, runtimeDataSourceApi } from '@/services/dataSource'
import { kpiAdminApi } from '@/services/kpi'
import type { DataSourceDetail, DataSourceVersion } from '@/types/dataSource'
import type { KpiSummary } from '@/types/kpi'
import type { DashboardCheckResult, DashboardDetail, DashboardVersion, RuntimeDashboard } from '@/types/dashboard'
import DashboardsView from '@/views/system/admin/DashboardsView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('@/services/dashboard', () => ({
  dashboardAdminApi: { list: vi.fn(), create: vi.fn(), detail: vi.fn(), saveDraft: vi.fn(), checkDraft: vi.fn(), publishDraft: vi.fn(), versions: vi.fn() },
  runtimeDashboardApi: { byCode: vi.fn() },
}))
vi.mock('@/services/dataSource', () => ({
  dataSourceAdminApi: { list: vi.fn(), versions: vi.fn() },
  runtimeDataSourceApi: { statisticsCapabilities: vi.fn() },
}))
vi.mock('@/services/kpi', () => ({ kpiAdminApi: { list: vi.fn() } }))

const source: DataSourceDetail = {
  id: '30', systemId: '10', tenantId: '20', code: 'orders', moduleId: '50', moduleCode: 'orders', name: '订单源', description: null,
  draftVersion: 1, activeVersionId: '31', activeVersionNumber: 1, createdAt: '2026-08-01T01:00:00Z', updatedAt: '2026-08-01T01:00:00Z', version: 1,
  draft: { outputFields: [{ fieldCode: 'title' }, { fieldCode: 'amount' }, { fieldCode: 'createdAt' }], fixedFilters: [], defaultSort: null, defaultTimeFieldCode: 'createdAt' },
}
const sourceVersion: DataSourceVersion = {
  id: '31', dataSourceId: '30', versionNumber: 1, code: 'orders', moduleId: '50', moduleCode: 'orders', schemaVersionId: 'schema-1', name: '订单源', description: null,
  snapshot: source.draft, fingerprint: 'source-fingerprint', publishedBy: '60', publishedAt: '2026-08-01T02:00:00Z', active: true,
}
const kpi: KpiSummary = {
  id: '90', systemId: '10', tenantId: '20', code: 'revenue', name: '收入 KPI', description: null,
  draftVersion: 3, activeVersionId: '91', activeVersionNumber: 3, createdAt: '2026-08-01T01:00:00Z',
  updatedAt: '2026-08-01T01:00:00Z', version: 3,
}
const detail: DashboardDetail = {
  id: '70', systemId: '10', tenantId: '20', code: 'system_home', placement: 'SYSTEM_HOME', name: '业务首页', description: null,
  draftVersion: 1, activeVersionId: '71', activeVersionNumber: 1, createdAt: '2026-08-01T03:00:00Z', updatedAt: '2026-08-01T03:00:00Z', version: 1,
  draft: { widgets: [
    { code: 'count', type: 'STAT_COUNT', title: '订单数', dataSourceId: '30', rowLimit: null, grid: { x: 0, y: 0, width: 3, height: 2 } },
    { code: 'list', type: 'DATA_LIST', title: '订单列表', dataSourceId: '30', rowLimit: 10, grid: { x: 3, y: 0, width: 6, height: 5 } },
  ] },
}
const version: DashboardVersion = {
  id: '71', dashboardId: '70', versionNumber: 1, sourceDraftVersion: 1, code: 'system_home', placement: 'SYSTEM_HOME', name: '业务首页', description: null,
  snapshot: detail.draft, fingerprint: 'dashboard-fingerprint-123', widgetCount: 2, publishedBy: '60', publishedAt: '2026-08-01T04:00:00Z', active: true,
  widgets: [
    { id: '73', ordinal: 0, code: 'count', type: 'STAT_COUNT', title: '订单数', dataSourceId: '30', dataSourceCode: 'orders', dataSourceVersionId: '31', dataSourceVersionNumber: 1, moduleCode: 'orders', schemaVersionId: 'schema-1', rowLimit: null, grid: { x: 0, y: 0, width: 3, height: 2 } },
    { id: '74', ordinal: 1, code: 'revenue', type: 'KPI_VALUE', title: '收入目标', kpiId: '90', kpiVersionId: '91', kpiVersionNumber: 3, kpiCode: 'revenue', kpiName: '收入 KPI', kpiSubjectType: 'ROLE', kpiPeriodType: 'MONTH', grid: { x: 3, y: 0, width: 6, height: 5 } },
  ],
}
const runtime: RuntimeDashboard = {
  id: '70', code: 'system_home', name: '业务首页', description: null, placement: 'SYSTEM_HOME', versionId: '71', versionNumber: 1,
  widgets: [{ code: 'count', type: 'STAT_COUNT', title: '订单数', ordinal: 0, grid: { x: 0, y: 0, width: 3, height: 2 }, dataSourceId: '30', dataSourceVersionId: '31', dataSourceVersionNumber: 1, dataSourceCode: 'orders', rowLimit: null, status: 'OK', error: null, total: 6, fields: [], rows: [], kpiTargets: [] }],
}
const checked: DashboardCheckResult = { dashboardId: '70', checkedDraftVersion: 2, valid: true, blockerCount: 0, warningCount: 0, issues: [] }

function render() {
  return mount(DashboardsView, { global: { stubs: {
    AdminPageHeader: { template: '<header><slot name="actions" /></header>' },
    RuntimeDashboardCanvas: { props: ['dashboard'], template: '<div class="runtime-canvas-stub">{{ dashboard.name }} {{ dashboard.widgets[0]?.total }}</div>' },
    'a-button': { props: ['disabled', 'loading'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
    'a-alert': { props: ['message'], template: '<div>{{ message }}</div>' }, 'a-tag': { template: '<span><slot /></span>' },
    'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' }, 'a-modal': { props: ['open'], template: '<div v-if="open"><slot /></div>' },
    'a-form': { template: '<form><slot /></form>' }, 'a-form-item': { template: '<label><slot /></label>' }, 'a-input': { template: '<input>' }, 'a-textarea': { template: '<textarea />' },
  } } })
}

describe('DashboardsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(dashboardAdminApi.list).mockResolvedValue([detail])
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([source])
    vi.mocked(kpiAdminApi.list).mockResolvedValue([kpi])
    vi.mocked(dashboardAdminApi.detail).mockResolvedValue(detail)
    vi.mocked(dashboardAdminApi.versions).mockResolvedValue([version])
    vi.mocked(dataSourceAdminApi.versions).mockResolvedValue([sourceVersion])
    vi.mocked(runtimeDataSourceApi.statisticsCapabilities).mockResolvedValue({
      dataSourceId: '30', dataSourceCode: 'orders', dataSourceVersionId: '31', dataSourceVersionNumber: 1,
      moduleCode: 'orders', schemaVersionId: 'schema-1', fields: [
        { code: 'title', name: '标题', type: 'TEXT', readable: true, numeric: false, temporal: false, groupable: true },
        { code: 'amount', name: '金额', type: 'DECIMAL', readable: true, numeric: true, temporal: false, groupable: false },
        { code: 'createdAt', name: '创建时间', type: 'DATETIME', readable: true, numeric: false, temporal: true, groupable: true },
      ],
    })
    vi.mocked(runtimeDashboardApi.byCode).mockResolvedValue(runtime)
  })

  it('loads ordered widget cards, pinned source versions, numeric layout and real published preview', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.findAll('.widget-editor-card')).toHaveLength(2)
    expect(wrapper.findAll('.numeric-widget')).toHaveLength(2)
    expect(wrapper.findAll('.widget-editor-card input:disabled').some(input => (input.element as HTMLInputElement).value.includes('v1'))).toBe(true)
    expect(runtimeDashboardApi.byCode).toHaveBeenCalledWith('10', 'system_home')
    expect(wrapper.get('.runtime-canvas-stub').text()).toContain('业务首页 6')
    expect(wrapper.get('.dashboard-version-history').text()).toContain('dashboard-fi')
    expect(wrapper.get('.dashboard-version-history').text()).toContain('orders v1')
    expect(wrapper.get('.dashboard-version-history').text()).toContain('revenue v3')
  })

  it('tracks dirty/saving/checked/published and saves the exact ordered numeric draft', async () => {
    const saved = { ...detail, name: '业务总览', version: 2, draftVersion: 2 }
    const published = { ...saved, activeVersionId: '72', activeVersionNumber: 2, version: 3 }
    let resolveSave!: (value: DashboardDetail) => void
    vi.mocked(dashboardAdminApi.saveDraft).mockReturnValue(new Promise(resolve => { resolveSave = resolve }))
    vi.mocked(dashboardAdminApi.checkDraft).mockResolvedValue(checked)
    vi.mocked(dashboardAdminApi.publishDraft).mockResolvedValue({ dashboard: published, version: { ...version, id: '72', versionNumber: 2 } })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.dashboard-basics input:not(:disabled)').setValue('业务总览')
    expect(wrapper.find('[data-state="dirty"]').exists()).toBe(true)
    await wrapper.get('.dashboard-save').trigger('click')
    expect(wrapper.find('[data-state="saving"]').exists()).toBe(true)
    resolveSave(saved)
    await flushPromises()
    expect(dashboardAdminApi.saveDraft).toHaveBeenCalledWith('10', '70', expect.objectContaining({
      expectedVersion: 1, name: '业务总览', widgets: [
        expect.objectContaining({ code: 'count', grid: { x: 0, y: 0, width: 3, height: 2 } }),
        expect.objectContaining({ code: 'list', rowLimit: 10, grid: { x: 3, y: 0, width: 6, height: 5 } }),
      ],
    }))
    await wrapper.get('.dashboard-check').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-state="checked"]').exists()).toBe(true)
    await wrapper.get('.dashboard-publish').trigger('click')
    await flushPromises()
    expect(dashboardAdminApi.publishDraft).toHaveBeenCalledWith('10', '70', 2)
    expect(wrapper.find('[data-state="published"]').exists()).toBe(true)

    const continued = { ...published, name: '发布后继续调整', version: 4, draftVersion: 3 }
    vi.mocked(dashboardAdminApi.saveDraft).mockResolvedValueOnce(continued)
    await wrapper.get('.dashboard-basics input:not(:disabled)').setValue('发布后继续调整')
    await wrapper.get('.dashboard-save').trigger('click')
    await flushPromises()
    expect(vi.mocked(dashboardAdminApi.saveDraft).mock.calls.at(-1)).toEqual([
      '10', '70', expect.objectContaining({ expectedVersion: 2, name: '发布后继续调整' }),
    ])
  })

  it('blocks an invalid overlapping draft before issuing a save request', async () => {
    const wrapper = render()
    await flushPromises()
    const xInputs = wrapper.findAll('.grid-controls label:first-of-type input')
    await xInputs[1]!.setValue(0)
    expect(wrapper.get('.client-issues').text()).toContain('WIDGET_GRID_OVERLAP')
    await wrapper.get('.dashboard-save').trigger('click')
    await flushPromises()
    expect(dashboardAdminApi.saveDraft).not.toHaveBeenCalled()
    expect(wrapper.get('.dashboard-error').text()).toContain('重叠')
  })

  it('adds a capability-driven STAT_VALUE and saves its frozen statistics spec', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.add-value-widget').trigger('click')
    const statisticsCard = wrapper.findAll('.widget-editor-card').at(-1)!
    expect(statisticsCard.get('.statistics-widget-controls').text()).toContain('v1 · schema-1')
    await statisticsCard.get('.statistics-widget-controls select').setValue('SUM')
    vi.mocked(dashboardAdminApi.saveDraft).mockImplementation(async (_systemId, _dashboardId, input) => ({
      ...detail, draftVersion: 2, draft: { widgets: input.widgets },
    }))
    await wrapper.get('.dashboard-save').trigger('click')
    await flushPromises()
    expect(dashboardAdminApi.saveDraft).toHaveBeenCalledWith('10', '70', expect.objectContaining({
      widgets: expect.arrayContaining([expect.objectContaining({
        type: 'STAT_VALUE', statistics: { aggregation: 'SUM', measureFieldCode: 'amount', grouping: null, trend: null },
      })]),
    }))
  })

  it('adds a published KPI root and saves no data-source query fields', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.add-kpi-widget').trigger('click')
    const kpiCard = wrapper.findAll('.widget-editor-card').at(-1)!
    expect(kpiCard.text()).toContain('KPI_VALUE')
    expect((kpiCard.get('select').element as HTMLSelectElement).value).toBe('KPI_VALUE')
    expect(kpiCard.get('input:disabled').element.getAttribute('value')).toContain('revenue v3')
    vi.mocked(dashboardAdminApi.saveDraft).mockImplementation(async (_systemId, _dashboardId, input) => ({
      ...detail, draftVersion: 2, draft: { widgets: input.widgets },
    }))
    await wrapper.get('.dashboard-save').trigger('click')
    await flushPromises()
    const savedWidget = vi.mocked(dashboardAdminApi.saveDraft).mock.calls.at(-1)![2].widgets.at(-1)!
    expect(savedWidget).toMatchObject({ type: 'KPI_VALUE', kpiId: '90' })
    expect(savedWidget).not.toHaveProperty('dataSourceId')
    expect(savedWidget).not.toHaveProperty('statistics')
    expect(savedWidget).not.toHaveProperty('rowLimit')
  })
})
