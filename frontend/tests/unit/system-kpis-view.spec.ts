import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { runtimeKpiApi } from '@/services/kpi'
import type { RuntimeKpiTarget } from '@/types/kpi'
import SystemKpisView from '@/views/system/SystemKpisView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('@/services/kpi', () => ({ runtimeKpiApi: { list: vi.fn() } }))

const explanation = {
  statisticsQueryId: 'query-1', matchedCount: '2', aggregation: 'SUM' as const,
  dataSourceCode: 'orders', dataSourceVersionId: '31', dataSourceVersionNumber: 1, schemaVersionId: 'schema-1',
  measureField: { logicalFieldId: '91', code: 'amount', name: '金额', type: 'DECIMAL', queryType: 'DECIMAL' },
  timeField: { logicalFieldId: '92', code: 'createdAt', name: '创建时间', type: 'DATETIME', queryType: 'DATETIME' },
  authorizationEpoch: '7', subjectMemberIds: [],
  trend: [{ startInclusive: '2026-08-01', endExclusive: '2026-09-01', value: '90.01', matchedCount: '2' }],
}

const targets: RuntimeKpiTarget[] = [
  {
    id: '40', kpiId: '20', kpiVersionId: '21', kpiVersionNumber: 1, kpiCode: 'sales', kpiName: '销售额',
    subjectType: 'MEMBER', subjectId: '30', subjectName: '张三', periodType: 'MONTH', periodStart: '2026-08-01', periodEndExclusive: '2026-09-01',
    targetValue: '10000000000000000000.123', version: 1, createdAt: '', updatedAt: '',
    latestCalculation: { id: '50', targetId: '40', status: 'AT_RISK', errorCode: null, targetValue: '10000000000000000000.123', actualValue: '9000000000000000000.01', attainment: '0.899999999999', calculatedAt: '2026-08-03T01:00:00Z', calculatedBy: '60', explanation },
  },
  {
    id: '41', kpiId: '22', kpiVersionId: '23', kpiVersionNumber: 1, kpiCode: 'quality', kpiName: '质量',
    subjectType: 'ROLE', subjectId: '90', subjectName: '质检角色', periodType: 'MONTH', periodStart: '2026-08-01', periodEndExclusive: '2026-09-01',
    targetValue: '0', version: 1, createdAt: '', updatedAt: '',
    latestCalculation: { id: '51', targetId: '41', status: 'CALCULATION_FAILED', errorCode: 'KPI_SOURCE_PERMISSION_DENIED', targetValue: '0', actualValue: null, attainment: null, calculatedAt: '2026-08-03T02:00:00Z', calculatedBy: '60', explanation: { ...explanation, statisticsQueryId: '', matchedCount: '0', trend: [] } },
  },
]

function render() {
  return mount(SystemKpisView, { global: { stubs: {
    'a-button': { props: ['disabled', 'loading'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
    'a-alert': { props: ['message', 'description'], template: '<div class="alert-stub">{{ message }} {{ description }}</div>' },
    'a-tag': { template: '<span class="tag-stub"><slot /></span>' },
    'a-empty': { props: ['description'], template: '<div class="empty-stub">{{ description }}</div>' },
    'a-spin': { template: '<div><slot /></div>' },
  } } })
}

describe('SystemKpisView', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loads applicable targets, preserves decimal strings and isolates one calculation failure', async () => {
    vi.mocked(runtimeKpiApi.list).mockResolvedValue(targets)
    const wrapper = render()
    await flushPromises()

    expect(runtimeKpiApi.list).toHaveBeenCalledWith('10', expect.objectContaining({ periodType: 'MONTH', periodStart: expect.stringMatching(/^\d{4}-\d{2}-01$/) }))
    expect(wrapper.findAll('.runtime-kpi-card')).toHaveLength(2)
    expect(wrapper.text()).toContain('10000000000000000000.123')
    expect(wrapper.text()).toContain('9000000000000000000.01')
    expect(wrapper.text()).toContain('0.899999999999')
    expect(wrapper.text()).toContain('1 个目标计算失败')
    expect(wrapper.text()).toContain('KPI_SOURCE_PERMISSION_DENIED')
  })

  it('keeps unavailable and empty states distinct', async () => {
    vi.mocked(runtimeKpiApi.list).mockRejectedValueOnce(new Error('KPI_RUNTIME_UNAVAILABLE'))
    const unavailable = render()
    await flushPromises()
    expect(unavailable.get('.runtime-kpi-error').text()).toContain('KPI_RUNTIME_UNAVAILABLE')

    vi.mocked(runtimeKpiApi.list).mockResolvedValueOnce([])
    const empty = render()
    await flushPromises()
    expect(empty.get('.runtime-kpi-empty').text()).toContain('当前周期没有适用于你的 KPI 目标')
  })

  it('rejects a misaligned quarter locally before issuing a runtime request', async () => {
    vi.mocked(runtimeKpiApi.list).mockResolvedValue([])
    const wrapper = render()
    await flushPromises()
    vi.clearAllMocks()
    await wrapper.get('.runtime-kpi-period-type').setValue('QUARTER')
    await wrapper.get('.runtime-kpi-period-start').setValue('2026-08-01')
    expect(wrapper.text()).toContain('请选择与周期边界对齐的开始日期')
    await wrapper.findAll('button').at(-1)!.trigger('click')
    expect(runtimeKpiApi.list).not.toHaveBeenCalled()
  })
})

