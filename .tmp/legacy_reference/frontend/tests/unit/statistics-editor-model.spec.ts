import { describe, expect, it } from 'vitest'

import type { DataSourceFieldCapability } from '@/types/dataSource'
import {
  aggregationChanged,
  buildStatisticsRequest,
  createStatisticsDraft,
  isCanonicalDecimal,
  statisticsCapabilities,
  validateStatisticsDraft,
} from '@/views/system/admin/statisticsEditorModel'

const fields: DataSourceFieldCapability[] = [
  { fieldCode: 'title', fieldName: '标题', type: 'TEXT', operators: [], sortable: true, temporal: false, available: true },
  { fieldCode: 'amount', fieldName: '金额', type: 'DECIMAL', operators: [], sortable: true, temporal: false, available: true },
  { fieldCode: 'createdAt', fieldName: '创建时间', type: 'DATETIME', operators: [], sortable: true, temporal: true, available: true },
  { fieldCode: 'secret', fieldName: '密钥', type: 'NUMBER', operators: [], sortable: false, temporal: false, available: false },
]

const unsafeStatisticsFields: DataSourceFieldCapability[] = [
  { fieldCode: 'price', fieldName: 'Price', type: 'MONEY', operators: [], sortable: true, temporal: false, available: true },
  { fieldCode: 'notes', fieldName: 'Notes', type: 'TEXTAREA', operators: [], sortable: false, temporal: false, available: true },
]

describe('statistics editor model', () => {
  it('derives measure/group/time controls only from published readable output fields', () => {
    const capabilities = statisticsCapabilities(fields, ['title', 'amount', 'createdAt', 'secret'])
    expect(capabilities.fields.map(field => field.fieldCode)).toEqual(['title', 'amount', 'createdAt'])
    expect(capabilities.measureFields.map(field => field.fieldCode)).toEqual(['amount'])
    expect(capabilities.groupFields.map(field => field.fieldCode)).toEqual(['title', 'amount', 'createdAt'])
    expect(capabilities.timeFields.map(field => field.fieldCode)).toEqual(['createdAt'])
  })

  it('honors explicit backend MONEY capability flags instead of inferring from the display type', () => {
    const capabilities = statisticsCapabilities([
      { code: 'amount', name: '金额', type: 'MONEY', readable: true, numeric: false, temporal: false, groupable: false },
      { code: 'score', name: '分数', type: 'NUMBER', readable: true, numeric: true, temporal: false, groupable: true },
    ])

    expect(capabilities.fields.map(field => field.fieldCode)).toEqual(['amount', 'score'])
    expect(capabilities.measureFields.map(field => field.fieldCode)).toEqual(['score'])
    expect(capabilities.groupFields.map(field => field.fieldCode)).toEqual(['score'])
  })

  it('keeps COUNT measure-free and builds the frozen grouping wire DTO', () => {
    const draft = createStatisticsDraft()
    draft.measureFieldCode = 'amount'
    aggregationChanged(draft)
    draft.groupFieldCode = 'title'
    draft.bucketLimit = 20
    expect(draft.measureFieldCode).toBe('')
    expect(buildStatisticsRequest(draft)).toEqual({
      aggregation: 'COUNT', measureFieldCode: null,
      grouping: { fieldCode: 'title', bucketLimit: 20 }, trend: null,
    })
  })

  it('does not offer currency amounts or unbounded textarea values as unsafe statistics dimensions', () => {
    const capabilities = statisticsCapabilities(unsafeStatisticsFields)
    expect(capabilities.measureFields).toEqual([])
    expect(capabilities.groupFields).toEqual([])
  })

  it('validates numeric measures, group bounds, mutual exclusion and aligned bounded ranges', () => {
    const capabilities = statisticsCapabilities(fields, ['title', 'amount', 'createdAt'])
    const draft = createStatisticsDraft()
    Object.assign(draft, {
      aggregation: 'SUM', measureFieldCode: 'title', groupFieldCode: 'title', bucketLimit: 21,
      timeFieldCode: 'createdAt', grain: 'WEEK', startInclusive: '2026-08-02', endExclusive: '2028-08-07',
    })
    expect(validateStatisticsDraft(draft, capabilities, 'LINE_TREND').map(issue => issue.code)).toEqual(expect.arrayContaining([
      'MEASURE_NOT_CAPABLE', 'GROUP_TREND_CONFLICT', 'GROUP_LIMIT_INVALID', 'TREND_RANGE_NOT_ALIGNED',
    ]))
  })

  it('accepts all grains and caps trend ranges at 100 aligned buckets', () => {
    const capabilities = statisticsCapabilities(fields, ['amount', 'createdAt'])
    const draft = createStatisticsDraft()
    Object.assign(draft, {
      aggregation: 'AVG', measureFieldCode: 'amount', timeFieldCode: 'createdAt',
      grain: 'MONTH', startInclusive: '2026-01-01', endExclusive: '2026-07-01',
    })
    expect(validateStatisticsDraft(draft, capabilities, 'LINE_TREND')).toEqual([])
    draft.grain = 'DAY'
    draft.endExclusive = '2026-05-01'
    expect(validateStatisticsDraft(draft, capabilities, 'LINE_TREND').map(issue => issue.code)).toContain('TREND_RANGE_TOO_LARGE')
  })

  it('recognizes canonical plain decimal strings without coercing precision', () => {
    expect(['0', '8', '-8', '0.125', '-0.125', '-12345678901234567890.01'].every(isCanonicalDecimal)).toBe(true)
    expect(['-0', '+1', '01', '1.0', '1e3', '.5', '1.'].some(isCanonicalDecimal)).toBe(false)
  })
})
