import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import MultiModuleJoinEditor from '@/components/admin/MultiModuleJoinEditor.vue'
import { dataSourceAdminApi } from '@/services/dataSource'
import type { DataSourceMultiModuleJoin, DataSourceSummary, DataSourceVersion } from '@/types/dataSource'

vi.mock('@/services/dataSource', () => ({ dataSourceAdminApi: { versions: vi.fn() } }))

const sources: DataSourceSummary[] = [
  { id: '10', systemId: '1', tenantId: '2', code: 'current', moduleId: '100', name: '当前', draftVersion: 1, activeVersionId: '11', activeVersionNumber: 1, createdAt: '', updatedAt: '', version: 1 },
  { id: '20', systemId: '1', tenantId: '2', code: 'orders', moduleId: '100', name: '订单', draftVersion: 1, activeVersionId: '21', activeVersionNumber: 1, createdAt: '', updatedAt: '', version: 1 },
  { id: '30', systemId: '1', tenantId: '2', code: 'customers', moduleId: '200', name: '客户', draftVersion: 1, activeVersionId: '31', activeVersionNumber: 1, createdAt: '', updatedAt: '', version: 1 },
  { id: '40', systemId: '1', tenantId: '2', code: 'nested', moduleId: '300', name: '嵌套', sourceKind: 'MULTI_MODULE_JOIN', draftVersion: 1, activeVersionId: '41', activeVersionNumber: 1, createdAt: '', updatedAt: '', version: 1 },
]

const plan: DataSourceMultiModuleJoin = {
  inputs: [
    { alias: 'orders', dataSourceId: '20', dataSourceVersionId: '21' },
    { alias: 'customers', dataSourceId: '30', dataSourceVersionId: '31' },
  ],
  edges: [{ leftAlias: 'orders', leftFieldCode: 'customerId', rightAlias: 'customers', rightFieldCode: 'id', joinType: 'LEFT', cardinality: 'MANY_TO_ONE' }],
  projections: [{ sourceAlias: 'orders', sourceFieldCode: 'amount', fieldCode: 'orders__amount' }],
  failureMode: 'ALLOW_PARTIAL_LEFT', timeoutSeconds: 5, rowLimit: 50,
}

function version(id: string, dataSourceId: string): DataSourceVersion {
  return { id, dataSourceId, versionNumber: 1, code: 'source', moduleId: '100', moduleCode: 'source', schemaVersionId: 'schema', name: 'source', fingerprint: 'f', snapshot: { outputFields: [], fixedFilters: [] }, publishedAt: '', publishedBy: '1', active: true }
}

describe('MultiModuleJoinEditor', () => {
  it('pins exact versions, enforces anchor candidates and emits immutable left-deep edits', async () => {
    vi.mocked(dataSourceAdminApi.versions).mockImplementation(async (_systemId, sourceId) => [version(sourceId === '20' ? '21' : '31', sourceId)])
    const wrapper = mount(MultiModuleJoinEditor, {
      props: { systemId: '1', currentSourceId: '10', anchorModuleId: '100', sources, modelValue: plan },
    })
    await flushPromises()

    const sourceSelects = wrapper.findAll('.input-grid select').filter(select => select.text().includes('请选择'))
    expect(sourceSelects[0]!.text()).toContain('订单')
    expect(sourceSelects[0]!.text()).not.toContain('客户')
    expect(wrapper.text()).not.toContain('嵌套')
    expect(wrapper.findAll('option').some(option => option.attributes('value') === '10')).toBe(false)

    await wrapper.find('.join-settings input[type="number"]').setValue(8)
    const emitted = wrapper.emitted('update:modelValue')!.at(-1)![0] as DataSourceMultiModuleJoin
    expect(emitted.timeoutSeconds).toBe(8)
    expect(emitted).not.toBe(plan)
    expect(plan.timeoutSeconds).toBe(5)
    expect(emitted.edges).toHaveLength(emitted.inputs.length - 1)
  })
})
