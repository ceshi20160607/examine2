import { describe, expect, it } from 'vitest'

import type { DataSourceStatisticsCapabilities } from '@/types/statistics'
import {
  alignedPeriodStart,
  capabilityFields,
  createKpiEditorDraft,
  defaultPeriodStart,
  kpiAggregationChanged,
  normalizeNonNegativeDecimal,
  validateKpiEditorDraft,
} from '@/views/system/admin/kpiEditorModel'

const capabilities: DataSourceStatisticsCapabilities = {
  dataSourceId: '30', dataSourceCode: 'orders', dataSourceVersionId: '31', dataSourceVersionNumber: 2,
  moduleCode: 'orders', schemaVersionId: 'schema-1',
  fields: [
    { code: 'amount', name: '金额', type: 'DECIMAL', readable: true, numeric: true, temporal: false, groupable: true },
    { code: 'price', name: '币种金额', type: 'MONEY', readable: true, numeric: true, temporal: false, groupable: false },
    { code: 'createdAt', name: '创建时间', type: 'DATETIME', readable: true, numeric: false, temporal: true, groupable: true },
    { code: 'secret', name: '隐藏', type: 'NUMBER', readable: false, numeric: true, temporal: false, groupable: true },
  ],
}

describe('KPI editor model', () => {
  it('derives numeric and temporal controls from backend capabilities and excludes MONEY', () => {
    expect(capabilityFields(capabilities)).toEqual([
      expect.objectContaining({ code: 'amount', numeric: true }),
      expect.objectContaining({ code: 'price', numeric: false }),
      expect.objectContaining({ code: 'createdAt', temporal: true }),
    ])
  })

  it('keeps COUNT measure-free and validates required published capabilities', () => {
    const draft = createKpiEditorDraft()
    Object.assign(draft, { name: '订单数', dataSourceId: '30', timeFieldCode: 'createdAt', measureFieldCode: 'amount' })
    kpiAggregationChanged(draft)
    expect(draft.measureFieldCode).toBeNull()
    expect(validateKpiEditorDraft(draft, capabilities)).toEqual([])

    Object.assign(draft, { aggregation: 'SUM', measureFieldCode: 'price' })
    expect(validateKpiEditorDraft(draft, capabilities).map(issue => issue.code)).toContain('MEASURE_UNAVAILABLE')
  })

  it('requires a canonical warning ratio greater than zero and at most one', () => {
    const draft = createKpiEditorDraft()
    Object.assign(draft, { name: '订单数', dataSourceId: '30', timeFieldCode: 'createdAt' })
    for (const warningThreshold of ['0', '1.01', '-0.1', 'abc']) {
      draft.warningThreshold = warningThreshold
      expect(validateKpiEditorDraft(draft, capabilities).map(issue => issue.code)).toContain('WARNING_THRESHOLD_INVALID')
    }
    for (const warningThreshold of ['0.0001', '0.8', '1']) {
      draft.warningThreshold = warningThreshold
      expect(validateKpiEditorDraft(draft, capabilities)).toEqual([])
    }
  })

  it('aligns month, quarter and year boundaries without timezone conversion', () => {
    expect(alignedPeriodStart('MONTH', '2026-08-01')).toBe('2026-08-01')
    expect(alignedPeriodStart('QUARTER', '2026-07-01')).toBe('2026-07-01')
    expect(alignedPeriodStart('QUARTER', '2026-08-01')).toBeNull()
    expect(alignedPeriodStart('YEAR', '2026-01-01')).toBe('2026-01-01')
    expect(alignedPeriodStart('YEAR', '2026-02-01')).toBeNull()
    expect(defaultPeriodStart('QUARTER', new Date(2026, 7, 3))).toBe('2026-07-01')
  })

  it('canonicalizes target decimals with string operations and never uses floating point', () => {
    expect(normalizeNonNegativeDecimal('12345678901234567890.1234500')).toBe('12345678901234567890.12345')
    expect(normalizeNonNegativeDecimal('0.0000000000000000001')).toBe('0.0000000000000000001')
    expect(normalizeNonNegativeDecimal('01')).toBeNull()
    expect(normalizeNonNegativeDecimal('1e3')).toBeNull()
    expect(normalizeNonNegativeDecimal('-1')).toBeNull()
  })
})
