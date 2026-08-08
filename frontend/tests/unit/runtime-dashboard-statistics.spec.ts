import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import RuntimeDashboardCanvas from '@/components/dashboard/RuntimeDashboardCanvas.vue'
import type { RuntimeDashboard, RuntimeDashboardWidget } from '@/types/dashboard'
import type { DataSourceStatisticsResult } from '@/types/statistics'

const baseResult: DataSourceStatisticsResult = {
  queryId: 'query-1', dataSourceId: '30', dataSourceCode: 'orders', dataSourceVersionId: '31', dataSourceVersionNumber: 2,
  moduleCode: 'orders', schemaVersionId: 'schema-1', aggregation: 'COUNT', measureFieldCode: null, value: '8',
  matchedRecordCount: 8, aggregateCount: 1, bucketCount: 0, groupBuckets: [], trendBuckets: [], totalBucketCount: 0, truncated: false,
}

function widget(type: RuntimeDashboardWidget['type'], ordinal: number, statisticsResult: DataSourceStatisticsResult): RuntimeDashboardWidget {
  return {
    code: `${type.toLowerCase()}_${ordinal}`, type, title: type, ordinal,
    grid: { x: ordinal * 2, y: 0, width: 2, height: 3 }, dataSourceId: '30', dataSourceVersionId: '31',
    dataSourceVersionNumber: 2, dataSourceCode: 'orders', rowLimit: null, status: 'OK', error: null, total: null,
    statisticsResult, fields: [], rows: [], kpiTargets: [],
  }
}

describe('runtime dashboard native statistics widgets', () => {
  it('renders metric, bar, pie and line results from each isolated runtime statisticsResult', () => {
    const grouped: DataSourceStatisticsResult = {
      ...baseResult, queryId: 'grouped', aggregateCount: 3, bucketCount: 2, totalBucketCount: 2,
      groupBuckets: [
        { key: 'OPEN', label: '待处理', nullBucket: false, value: '5', recordCount: 5 },
        { key: null, label: null, nullBucket: true, value: '3', recordCount: 3 },
      ],
    }
    const trend: DataSourceStatisticsResult = {
      ...baseResult, queryId: 'trend', aggregateCount: 3, bucketCount: 2, totalBucketCount: 2,
      trendBuckets: [
        { startInclusive: '2026-08-01', endExclusive: '2026-08-02', value: '0', recordCount: 0, empty: true },
        { startInclusive: '2026-08-02', endExclusive: '2026-08-03', value: '8', recordCount: 8, empty: false },
      ],
    }
    const dashboard: RuntimeDashboard = {
      id: '70', code: 'home', name: '首页', description: null, placement: 'SYSTEM_HOME', versionId: '71', versionNumber: 1,
      widgets: [
        widget('STAT_VALUE', 0, baseResult), widget('BAR_CHART', 1, grouped),
        widget('PIE_CHART', 2, grouped), widget('LINE_TREND', 3, trend),
      ],
    }
    const wrapper = mount(RuntimeDashboardCanvas, { props: { dashboard } })
    expect(wrapper.get('[data-widget-code="stat_value_0"]').text()).toContain('8')
    expect(wrapper.get('[data-widget-code="bar_chart_1"]').text()).toContain('待处理')
    expect(wrapper.get('[data-widget-code="pie_chart_2"]').text()).toContain('空值')
    expect(wrapper.get('[data-widget-code="line_trend_3"]').text()).toContain('2026-08-02')
  })
})
