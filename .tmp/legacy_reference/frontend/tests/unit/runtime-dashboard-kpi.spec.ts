import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import RuntimeDashboardCanvas from '@/components/dashboard/RuntimeDashboardCanvas.vue'
import type { RuntimeDashboard, RuntimeDashboardWidget } from '@/types/dashboard'
import type { KpiTarget } from '@/types/kpi'

function target(id: string, subjectName: string): KpiTarget {
  return {
    id, kpiId: '90', kpiVersionId: '91', kpiVersionNumber: 2, kpiCode: 'revenue', kpiName: '收入 KPI',
    subjectType: 'ROLE', subjectId: `role-${id}`, subjectName, periodType: 'MONTH', periodStart: '2026-08-01',
    periodEndExclusive: '2026-09-01', targetValue: '99999999999999999999.00001', version: 1,
    createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z',
    latestCalculation: {
      id: `calculation-${id}`, targetId: id, status: 'AT_RISK', errorCode: null,
      targetValue: '99999999999999999999.00001', actualValue: '81234567890123456789.12345',
      attainment: '0.81234567890123456789', calculatedAt: '2026-08-03T02:00:00Z', calculatedBy: '30',
      explanation: {
        statisticsQueryId: `query-${id}`, matchedCount: '12345678901234567890', aggregation: 'SUM',
        dataSourceCode: 'orders', dataSourceVersionId: '31', dataSourceVersionNumber: 4, schemaVersionId: 'schema-1',
        measureField: { logicalFieldId: 'f1', code: 'amount', name: '金额', type: 'DECIMAL', queryType: 'NUMBER' },
        timeField: { logicalFieldId: 'f2', code: 'createdAt', name: '创建时间', type: 'DATETIME', queryType: 'DATETIME' },
        authorizationEpoch: '900719925474099312345', subjectMemberIds: ['30'], trend: [],
      },
    },
  }
}

function kpiWidget(code: string, ordinal: number, kpiTargets: KpiTarget[], status: 'OK' | 'ERROR' = 'OK'): RuntimeDashboardWidget {
  return {
    code, type: 'KPI_VALUE', title: `KPI ${code}`, ordinal, grid: { x: 0, y: ordinal * 5, width: 6, height: 5 },
    kpiId: '90', kpiVersionId: '91', kpiVersionNumber: 2, kpiCode: 'revenue', kpiName: '收入 KPI',
    kpiSubjectType: 'ROLE', kpiPeriodType: 'MONTH', status,
    error: status === 'ERROR' ? { code: 'KPI_RUNTIME_FAILED', message: '本组件暂不可用' } : null,
    total: null, fields: [], rows: [], kpiTargets,
  }
}

describe('runtime dashboard KPI widget', () => {
  it('renders zero, one and every applicable target while preserving backend decimal strings', () => {
    const dashboard: RuntimeDashboard = {
      id: '70', code: 'home', name: '首页', description: null, placement: 'SYSTEM_HOME', versionId: '71', versionNumber: 1,
      widgets: [
        kpiWidget('empty', 0, []),
        kpiWidget('one', 1, [target('100', '销售一组')]),
        kpiWidget('many', 2, [target('101', '销售二组'), target('102', '区域经理')]),
      ],
    }
    const wrapper = mount(RuntimeDashboardCanvas, { props: { dashboard } })

    expect(wrapper.get('[data-widget-code="empty"]').text()).toContain('当前周期没有适用于你的 KPI 目标')
    expect(wrapper.findAll('[data-widget-code="one"] .runtime-kpi-card')).toHaveLength(1)
    expect(wrapper.findAll('[data-widget-code="many"] .runtime-kpi-card')).toHaveLength(2)
    expect(wrapper.get('[data-widget-code="many"] header small').text()).toContain('revenue · v2')
    expect(wrapper.get('[data-widget-code="one"]').text()).toContain('99999999999999999999.00001')
    expect(wrapper.get('[data-widget-code="one"]').text()).toContain('81234567890123456789.12345')
    expect(wrapper.get('[data-widget-code="one"]').text()).toContain('0.81234567890123456789')
    expect(wrapper.find('[data-widget-code="one"] .kpi-calculation-explanation').exists()).toBe(true)
  })

  it('isolates a failed KPI widget without erasing a successful sibling', () => {
    const count: RuntimeDashboardWidget = {
      code: 'count', type: 'STAT_COUNT', title: '订单数', ordinal: 1, grid: { x: 6, y: 0, width: 3, height: 2 },
      dataSourceId: '30', dataSourceVersionId: '31', dataSourceVersionNumber: 1, dataSourceCode: 'orders',
      rowLimit: null, status: 'OK', error: null, total: 7, fields: [], rows: [], kpiTargets: [],
    }
    const dashboard: RuntimeDashboard = {
      id: '70', code: 'home', name: '首页', description: null, placement: 'SYSTEM_HOME', versionId: '71', versionNumber: 1,
      widgets: [kpiWidget('failed-kpi', 0, [], 'ERROR'), count],
    }
    const wrapper = mount(RuntimeDashboardCanvas, { props: { dashboard } })

    expect(wrapper.get('[data-widget-code="failed-kpi"]').text()).toContain('KPI_RUNTIME_FAILED')
    expect(wrapper.get('[data-widget-code="count"]').text()).toContain('7')
    expect(wrapper.attributes('data-partial-error')).toBe('true')
  })
})
