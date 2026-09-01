import { describe, expect, it } from 'vitest'
import { defaultKpiPeriod, kpiStatusLabel, parseKpiIds } from './kpi'

describe('Cycle 50 KPI closed-loop rules', () => {
  it('normalizes responsible and reminder account ids', () => {
    expect(parseKpiIds('10, 20，10 bad 0')).toEqual([10, 20])
  })

  it('builds month, quarter and year period keys deterministically', () => {
    const date = new Date(2026, 7, 27)
    expect(defaultKpiPeriod('MONTH', date)).toBe('2026-08')
    expect(defaultKpiPeriod('QUARTER', date)).toBe('2026-Q3')
    expect(defaultKpiPeriod('YEAR', date)).toBe('2026')
  })

  it('does not present a stale calculation as achieved', () => {
    expect(kpiStatusLabel('STALE')).toBe('数据已过期')
    expect(kpiStatusLabel('ACHIEVED')).toBe('已达标')
  })
})
