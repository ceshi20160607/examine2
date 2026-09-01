import type { KpiDefinition } from './types'

export function parseKpiIds(value: string): number[] {
  return [...new Set(value.split(/[,，\s]+/)
    .map(item => Number(item.trim()))
    .filter(item => Number.isInteger(item) && item > 0))]
}

export function defaultKpiPeriod(periodType: KpiDefinition['periodType'], date = new Date()): string {
  const year = date.getFullYear()
  if (periodType === 'YEAR') return String(year)
  if (periodType === 'QUARTER') return `${year}-Q${Math.floor(date.getMonth() / 3) + 1}`
  return `${year}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

export function kpiStatusLabel(status: string): string {
  if (status === 'ACHIEVED') return '已达标'
  if (status === 'UNDER_TARGET') return '未达标'
  if (status === 'STALE') return '数据已过期'
  return '待计算'
}
