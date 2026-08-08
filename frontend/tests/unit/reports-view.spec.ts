import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { dataSourceAdminApi, runtimeDataSourceApi } from '@/services/dataSource'
import { reportAdminApi } from '@/services/report'
import type { DataSourceSummary } from '@/types/dataSource'
import type { ReportCheckResult, ReportDetail, ReportVersion } from '@/types/report'
import ReportsView from '@/views/system/admin/ReportsView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('@/services/dataSource', () => ({
  dataSourceAdminApi: { list: vi.fn() }, runtimeDataSourceApi: { metadata: vi.fn() },
}))
vi.mock('@/services/report', () => ({ reportAdminApi: {
  list: vi.fn(), create: vi.fn(), detail: vi.fn(), saveDraft: vi.fn(), checkDraft: vi.fn(),
  publishDraft: vi.fn(), versions: vi.fn(), version: vi.fn(),
} }))

const source: DataSourceSummary = {
  id: '20', systemId: '10', tenantId: '11', code: 'orders', moduleId: '40', moduleCode: 'orders',
  name: '订单源', description: null, draftVersion: 1, activeVersionId: '21', activeVersionNumber: 2,
  createdAt: '', updatedAt: '', version: 2,
}
const detail: ReportDetail = {
  id: '30', systemId: '10', tenantId: '11', code: 'monthly_orders', name: '月度订单', description: '经营数据',
  draftVersion: 2, activeVersionId: '31', activeVersionNumber: 1, createdAt: '', updatedAt: '', version: 2,
  draft: { dataSourceId: '20', outputFieldCodes: ['recordNo', 'amount'] },
}
const version: ReportVersion = {
  id: '31', reportId: '30', versionNumber: 1, sourceDraftVersion: 2, code: 'monthly_orders', name: '月度订单', description: '经营数据',
  dataSourceId: '20', dataSourceVersionId: '21', dataSourceVersionNumber: 2, dataSourceCode: 'orders', dataSourceName: '订单源',
  moduleId: '40', moduleCode: 'orders', schemaVersionId: 'schema-2', fingerprint: 'fp-1', publishedBy: '9', publishedAt: '2026-08-04T00:00:00Z', active: true,
  fields: [
    { logicalFieldId: '1', code: 'recordNo', name: '单号', type: 'TEXT', queryType: 'TEXT' },
    { logicalFieldId: '2', code: 'amount', name: '金额', type: 'DECIMAL', queryType: 'DECIMAL' },
  ],
}
const checked: ReportCheckResult = {
  reportId: '30', checkedDraftVersion: 3, valid: true, blockerCount: 0, warningCount: 0, issues: [],
  source: { ...version, fields: version.fields.map(field => ({ ...field, readable: true })) },
}

function render() {
  return mount(ReportsView, { global: { stubs: {
    AdminPageHeader: { template: '<header><slot name="actions" /></header>' },
    'a-button': { props: ['disabled', 'loading'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
    'a-alert': { props: ['message'], template: '<div class="alert-stub">{{ message }}</div>' },
    'a-spin': { template: '<div><slot /></div>' },
    'a-tag': { template: '<span><slot /></span>' },
    'a-empty': { props: ['description'], template: '<div class="empty-stub">{{ description }}</div>' },
    'a-modal': { props: ['open'], emits: ['ok'], template: '<div v-if="open"><slot /><button class="modal-ok" @click="$emit(\'ok\')">ok</button></div>' },
    ReportScheduleManager: { props: ['reportId', 'reportActive'], template: '<div class="schedule-manager-stub">{{ reportId }} {{ reportActive }}</div>' },
  } } })
}

describe('ReportsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(reportAdminApi.list).mockResolvedValue([detail])
    vi.mocked(dataSourceAdminApi.list).mockResolvedValue([source])
    vi.mocked(reportAdminApi.detail).mockResolvedValue(detail)
    vi.mocked(reportAdminApi.versions).mockResolvedValue([version])
    vi.mocked(reportAdminApi.version).mockResolvedValue(version)
    vi.mocked(runtimeDataSourceApi.metadata).mockResolvedValue({
      id: '20', code: 'orders', name: '订单源', moduleCode: 'orders', versionId: '21', activeVersionId: '21',
      versionNumber: 2, activeVersionNumber: 2, schemaVersionId: 'schema-2', description: null,
      outputFields: [{ fieldCode: 'recordNo', fieldName: '单号', type: 'TEXT' }, { fieldCode: 'amount', fieldName: '金额', type: 'DECIMAL' }],
      fields: [], defaultSort: null, defaultTimeFieldCode: null,
    })
  })

  it('loads the ordered editor and exact published source pin', async () => {
    const wrapper = render()
    await flushPromises()

    expect(reportAdminApi.detail).toHaveBeenCalledWith('10', '30')
    expect(runtimeDataSourceApi.metadata).toHaveBeenCalledWith('10', 'orders')
    expect(wrapper.findAll('.report-field-row')).toHaveLength(2)
    expect(wrapper.get('.version-section').text()).toContain('订单源 · orders')
    expect(wrapper.get('.version-section').text()).toContain('v2 · 21')
    expect(wrapper.get('.version-section').text()).toContain('schema-2')
  })

  it('saves ordered codes, checks capabilities and publishes the exact optimistic version', async () => {
    const saved = { ...detail, name: '订单经营报表', draftVersion: 3, version: 3 }
    const published = { ...saved, activeVersionId: '32', activeVersionNumber: 2, version: 4 }
    const publishedVersion = { ...version, id: '32', versionNumber: 2 }
    vi.mocked(reportAdminApi.saveDraft).mockResolvedValue(saved)
    vi.mocked(reportAdminApi.checkDraft).mockResolvedValue(checked)
    vi.mocked(reportAdminApi.publishDraft).mockResolvedValue({ report: published, version: publishedVersion })
    vi.mocked(reportAdminApi.versions).mockResolvedValueOnce([version]).mockResolvedValueOnce([publishedVersion, version])
    vi.mocked(reportAdminApi.version).mockResolvedValue(publishedVersion)
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.report-name-input').setValue('订单经营报表')
    await wrapper.get('.report-save').trigger('click')
    await flushPromises()
    expect(reportAdminApi.saveDraft).toHaveBeenCalledWith('10', '30', {
      expectedVersion: 2, name: '订单经营报表', description: '经营数据', dataSourceId: '20',
      outputFieldCodes: ['recordNo', 'amount'],
    })
    await wrapper.get('.report-check').trigger('click')
    await flushPromises()
    expect(reportAdminApi.checkDraft).toHaveBeenCalledWith('10', '30')
    await wrapper.get('.report-publish').trigger('click')
    await flushPromises()
    expect(reportAdminApi.publishDraft).toHaveBeenCalledWith('10', '30', 3)
    expect(reportAdminApi.version).toHaveBeenCalledWith('10', '30', 2)
  })

  it('creates one ordered multi-field draft without a follow-up edit', async () => {
    vi.mocked(reportAdminApi.create).mockResolvedValue(detail)
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.report-create').trigger('click')
    await flushPromises()
    await wrapper.get('.report-create-code').setValue('monthly_orders')
    await wrapper.get('.report-create-name').setValue('月度订单')
    await wrapper.get('.report-create-field-add').trigger('click')
    await wrapper.get('.report-create-field-add').trigger('click')
    expect(wrapper.findAll('.report-create-field-row')).toHaveLength(2)
    await wrapper.findAll('.report-create-field-up')[1]!.trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(reportAdminApi.create).toHaveBeenCalledWith('10', {
      code: 'monthly_orders', name: '月度订单', description: null, dataSourceId: '20',
      outputFieldCodes: ['amount', 'recordNo'],
    })
  })
})
