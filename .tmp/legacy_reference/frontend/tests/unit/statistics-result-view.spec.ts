import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import StatisticsResultView from '@/components/statistics/StatisticsResultView.vue'
import type { DataSourceStatisticsResult } from '@/types/statistics'

const result: DataSourceStatisticsResult = {
  queryId: 'query-1', dataSourceId: '30', dataSourceCode: 'orders', dataSourceVersionId: '31',
  dataSourceVersionNumber: 2, moduleCode: 'orders', schemaVersionId: 'schema-1', aggregation: 'SUM',
  measureFieldCode: 'amount', value: '12345678901234567890.01', matchedRecordCount: 8, aggregateCount: 3, bucketCount: 2,
  groupBuckets: [
    { key: 'PAID', label: '已支付', nullBucket: false, value: '120.5', recordCount: 3 },
    { key: null, label: null, nullBucket: true, value: '2.5', recordCount: 1 },
  ],
  trendBuckets: [
    { startInclusive: '2026-08-01', endExclusive: '2026-08-02', value: '0', recordCount: 0, empty: true },
    { startInclusive: '2026-08-02', endExclusive: '2026-08-03', value: '8.25', recordCount: 2, empty: false },
  ],
  totalBucketCount: 5, truncated: true,
}

describe('StatisticsResultView', () => {
  it('keeps exact canonical decimals in STAT_VALUE and exposes stable query/version metadata', () => {
    const wrapper = mount(StatisticsResultView, { props: { visualization: 'STAT_VALUE', result } })
    expect(wrapper.get('[data-state="ready"]').text()).toContain('12345678901234567890.01')
    expect(wrapper.text()).toContain('orders · v2')
    expect(wrapper.text()).toContain('query-1')
  })

  it('renders bar and pie group buckets including explicit null and overflow states', () => {
    const bar = mount(StatisticsResultView, { props: { visualization: 'BAR_CHART', result } })
    expect(bar.findAll('.bar-row')).toHaveLength(2)
    expect(bar.text()).toContain('空值')
    expect(bar.text()).toContain('共 5 组')
    const pie = mount(StatisticsResultView, { props: { visualization: 'PIE_CHART', result } })
    expect(pie.findAll('.pie-chart li')).toHaveLength(2)
    expect(pie.text()).toContain('其余 3 组未展示')
  })

  it('renders DAY/WEEK/MONTH trend buckets and preserves zero versus null empty semantics', () => {
    const wrapper = mount(StatisticsResultView, { props: { visualization: 'LINE_TREND', result } })
    expect(wrapper.findAll('.line-trend li')).toHaveLength(2)
    expect(wrapper.get('.line-trend li.empty').text()).toContain('0')
    expect(wrapper.text()).toContain('8.25')
  })

  it('keeps loading, empty and error states distinct', async () => {
    const wrapper = mount(StatisticsResultView, { props: { visualization: 'STAT_VALUE', loading: true } })
    expect(wrapper.attributes('data-state')).toBe('loading')
    await wrapper.setProps({ loading: false })
    expect(wrapper.attributes('data-state')).toBe('empty')
    await wrapper.setProps({ error: 'SOURCE_FORBIDDEN' })
    expect(wrapper.attributes('data-state')).toBe('error')
    expect(wrapper.text()).toContain('SOURCE_FORBIDDEN')
  })
})
