import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { systemAdminApi } from '@/services/admin'
import { dataSourceAdminApi, runtimeDataSourceApi } from '@/services/dataSource'
import { kpiAdminApi } from '@/services/kpi'
import { memberDirectoryApi } from '@/services/memberDirectory'
import type { DataSourceSummary } from '@/types/dataSource'
import type { KpiCalculation, KpiCheckResult, KpiDetail, KpiTarget, KpiVersion } from '@/types/kpi'
import KpisView from '@/views/system/admin/KpisView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
const toast = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('ant-design-vue', () => ({ message: toast }))
vi.mock('@/services/kpi', () => ({
  kpiAdminApi: {
    list: vi.fn(), create: vi.fn(), detail: vi.fn(), saveDraft: vi.fn(), checkDraft: vi.fn(), publishDraft: vi.fn(), versions: vi.fn(),
    targets: vi.fn(), createTarget: vi.fn(), updateTarget: vi.fn(), recalculate: vi.fn(), history: vi.fn(),
  },
}))
vi.mock('@/services/dataSource', () => ({
  dataSourceAdminApi: { list: vi.fn() }, runtimeDataSourceApi: { statisticsCapabilities: vi.fn() },
}))
vi.mock('@/services/admin', () => ({ systemAdminApi: { listDepartments: vi.fn(), listRoles: vi.fn() } }))
vi.mock('@/services/memberDirectory', () => ({ memberDirectoryApi: { list: vi.fn() } }))

const source: DataSourceSummary = {
  id: '30', systemId: '10', tenantId: '20', code: 'orders', moduleId: '50', moduleCode: 'orders', name: '订单源', description: null,
  draftVersion: 2, activeVersionId: '31', activeVersionNumber: 2, createdAt: '', updatedAt: '', version: 2,
}
const detail: KpiDetail = {
  id: '70', systemId: '10', tenantId: '20', code: 'order_amount', name: '订单金额', description: '按月考核',
  draftVersion: 2, activeVersionId: '71', activeVersionNumber: 1, createdAt: '', updatedAt: '', version: 2,
  draft: { dataSourceId: '30', subjectType: 'MEMBER', periodType: 'MONTH', aggregation: 'SUM', measureFieldCode: 'amount', timeFieldCode: 'createdAt', direction: 'AT_LEAST', warningThreshold: '0.8' },
}
const version: KpiVersion = {
  id: '71', kpiId: '70', versionNumber: 1, sourceDraftVersion: 2, code: 'order_amount', name: '订单金额', description: '按月考核',
  subjectType: 'MEMBER', periodType: 'MONTH', aggregation: 'SUM', direction: 'AT_LEAST', warningThreshold: '0.8',
  dataSourceId: '30', dataSourceVersionId: '31', dataSourceVersionNumber: 2, dataSourceCode: 'orders', moduleCode: 'orders', schemaVersionId: 'schema-1',
  measureField: { logicalFieldId: '91', code: 'amount', name: '金额', type: 'DECIMAL', queryType: 'DECIMAL' },
  timeField: { logicalFieldId: '92', code: 'createdAt', name: '创建时间', type: 'DATETIME', queryType: 'DATETIME' },
  fingerprint: 'a'.repeat(64), publishedBy: '60', publishedAt: '2026-08-01T01:00:00Z', active: true,
}
const calculation: KpiCalculation = {
  id: '90', targetId: '80', status: 'AT_RISK', errorCode: null, targetValue: '100.01', actualValue: '80.01', attainment: '0.800019998',
  calculatedAt: '2026-08-03T01:00:00Z', calculatedBy: '60',
  explanation: { statisticsQueryId: 'query-1', matchedCount: '2', aggregation: 'SUM', dataSourceCode: 'orders', dataSourceVersionId: '31', dataSourceVersionNumber: 2, schemaVersionId: 'schema-1', measureField: version.measureField, timeField: version.timeField, authorizationEpoch: '9', subjectMemberIds: [], trend: [] },
}
const target: KpiTarget = {
  id: '80', kpiId: '70', kpiVersionId: '71', kpiVersionNumber: 1, kpiCode: 'order_amount', kpiName: '订单金额', subjectType: 'MEMBER', subjectId: '61', subjectName: '张三',
  periodType: 'MONTH', periodStart: '2026-08-01', periodEndExclusive: '2026-09-01', targetValue: '100.01', version: 1, createdAt: '', updatedAt: '', latestCalculation: calculation,
}
const checked: KpiCheckResult = { kpiId: '70', checkedDraftVersion: 3, valid: true, blockerCount: 0, warningCount: 0, issues: [] }

function render() {
  return mount(KpisView, { global: { stubs: {
    'a-button': { props: ['disabled', 'loading'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
    'a-alert': { props: ['message', 'description'], template: '<div class="alert-stub">{{ message }} {{ description }}</div>' },
    'a-tag': { template: '<span><slot /></span>' }, 'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
    'a-modal': { props: ['open'], emits: ['ok'], template: '<div v-if="open"><slot /><button class="modal-ok" @click="$emit(\'ok\')">ok</button></div>' },
    'a-drawer': { props: ['open'], template: '<div v-if="open"><slot /></div>' }, 'a-spin': { template: '<div><slot /></div>' },
    'a-form': { template: '<form><slot /></form>' }, 'a-form-item': { template: '<label><slot /></label>' },
    'a-input': { props: ['value'], emits: ['update:value'], template: '<input :value="value" @input="$emit(\'update:value\', $event.target.value)">' },
    'a-textarea': { props: ['value'], emits: ['update:value'], template: '<textarea :value="value" @input="$emit(\'update:value\', $event.target.value)" />' },
    'a-select': { template: '<select />' },
  } } })
}

describe('KpisView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(kpiAdminApi.list).mockResolvedValue([detail])
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([source])
    vi.mocked(kpiAdminApi.detail).mockResolvedValue(detail)
    vi.mocked(kpiAdminApi.versions).mockResolvedValue([version])
    vi.mocked(kpiAdminApi.targets).mockResolvedValue([target])
    vi.mocked(runtimeDataSourceApi.statisticsCapabilities).mockResolvedValue({
      dataSourceId: '30', dataSourceCode: 'orders', dataSourceVersionId: '31', dataSourceVersionNumber: 2, moduleCode: 'orders', schemaVersionId: 'schema-1',
      fields: [
        { code: 'amount', name: '金额', type: 'DECIMAL', readable: true, numeric: true, temporal: false, groupable: true },
        { code: 'createdAt', name: '创建时间', type: 'DATETIME', readable: true, numeric: false, temporal: true, groupable: true },
      ],
    })
    vi.mocked(memberDirectoryApi.list).mockResolvedValue({ items: [], page: 1, size: 200, total: 0 })
    vi.mocked(systemAdminApi.listDepartments).mockResolvedValue({ items: [], page: 1, size: 200, total: 0 })
    vi.mocked(systemAdminApi.listRoles).mockResolvedValue({ items: [], page: 1, size: 200, total: 0 })
  })

  it('loads definition, frozen field pins, targets and capability-driven controls', async () => {
    const wrapper = render()
    await flushPromises()
    expect(kpiAdminApi.detail).toHaveBeenCalledWith('10', '70')
    expect(kpiAdminApi.targets).toHaveBeenCalledWith('10', '70')
    expect(runtimeDataSourceApi.statisticsCapabilities).toHaveBeenCalledWith('10', 'orders')
    expect(wrapper.get('.kpi-measure-select').text()).toContain('金额')
    expect(wrapper.get('.version-section').text()).toContain('orders v2')
    expect(wrapper.get('.version-section').text()).toContain('创建时间')
    expect(wrapper.get('.target-section').text()).toContain('100.01')
    expect(wrapper.get('.target-section').text()).toContain('0.800019998')
  })

  it('saves canonical string configuration, then checks and publishes the exact draft', async () => {
    const saved = { ...detail, name: '月度订单金额', draftVersion: 3, version: 3 }
    const published = { ...saved, activeVersionId: '72', activeVersionNumber: 2, version: 4 }
    vi.mocked(kpiAdminApi.saveDraft).mockResolvedValue(saved)
    vi.mocked(kpiAdminApi.checkDraft).mockResolvedValue(checked)
    vi.mocked(kpiAdminApi.publishDraft).mockResolvedValue({ kpi: published, version: { ...version, id: '72', versionNumber: 2 } })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.kpi-name-input').setValue('月度订单金额')
    await wrapper.get('.kpi-save').trigger('click')
    await flushPromises()
    expect(kpiAdminApi.saveDraft).toHaveBeenCalledWith('10', '70', {
      expectedVersion: 2, name: '月度订单金额', description: '按月考核', dataSourceId: '30', subjectType: 'MEMBER', periodType: 'MONTH',
      aggregation: 'SUM', measureFieldCode: 'amount', timeFieldCode: 'createdAt', direction: 'AT_LEAST', warningThreshold: '0.8',
    })
    await wrapper.get('.kpi-check').trigger('click')
    await flushPromises()
    expect(kpiAdminApi.checkDraft).toHaveBeenCalledWith('10', '70')
    await wrapper.get('.kpi-publish').trigger('click')
    await flushPromises()
    expect(kpiAdminApi.publishDraft).toHaveBeenCalledWith('10', '70', 3)
  })

  it('updates only the selected target with the backend calculation and opens immutable history', async () => {
    const next = { ...calculation, id: '91', actualValue: '100.01', attainment: '1', status: 'ACHIEVED' as const }
    vi.mocked(kpiAdminApi.recalculate).mockResolvedValue(next)
    vi.mocked(kpiAdminApi.history).mockResolvedValue([next, calculation])
    const wrapper = render()
    await flushPromises()
    await wrapper.get('button[title="立即计算"]').trigger('click')
    await flushPromises()
    expect(kpiAdminApi.recalculate).toHaveBeenCalledWith('10', '80')
    expect(wrapper.get('.target-section').text()).toContain('100.01')
    expect(wrapper.get('.target-section').text()).toContain('已达成')
    await wrapper.get('button[title="计算历史"]').trigger('click')
    await flushPromises()
    expect(kpiAdminApi.history).toHaveBeenCalledWith('10', '80')
    expect(wrapper.findAll('.history-list > section')).toHaveLength(2)
  })
})
