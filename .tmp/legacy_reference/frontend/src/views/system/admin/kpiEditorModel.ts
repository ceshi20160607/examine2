import type { DataSourceStatisticsCapabilities } from '@/types/statistics'
import type {
  KpiAggregation,
  KpiAttainmentDirection,
  KpiDraft,
  KpiPeriodType,
  KpiSubjectType,
} from '@/types/kpi'

export interface KpiEditorDraft extends KpiDraft {
  name: string
  description: string
}

export interface KpiEditorIssue {
  code: string
  path: string
  message: string
}

export interface KpiCapabilityField {
  code: string
  name: string
  type: string
  numeric: boolean
  temporal: boolean
  readable: boolean
}

export const KPI_SUBJECT_OPTIONS: Array<{ value: KpiSubjectType; label: string }> = [
  { value: 'MEMBER', label: '成员' },
  { value: 'DEPARTMENT', label: '部门' },
  { value: 'ROLE', label: '角色' },
]
export const KPI_PERIOD_OPTIONS: Array<{ value: KpiPeriodType; label: string }> = [
  { value: 'MONTH', label: '月度' },
  { value: 'QUARTER', label: '季度' },
  { value: 'YEAR', label: '年度' },
]
export const KPI_AGGREGATION_OPTIONS: Array<{ value: KpiAggregation; label: string }> = [
  { value: 'COUNT', label: '记录数' },
  { value: 'SUM', label: '求和' },
  { value: 'AVG', label: '平均值' },
  { value: 'MIN', label: '最小值' },
  { value: 'MAX', label: '最大值' },
]
export const KPI_DIRECTION_OPTIONS: Array<{ value: KpiAttainmentDirection; label: string }> = [
  { value: 'AT_LEAST', label: '越高越好（至少达到）' },
  { value: 'AT_MOST', label: '越低越好（不超过）' },
]

export function createKpiEditorDraft(): KpiEditorDraft {
  return {
    name: '',
    description: '',
    dataSourceId: '',
    subjectType: 'MEMBER',
    periodType: 'MONTH',
    aggregation: 'COUNT',
    measureFieldCode: null,
    timeFieldCode: '',
    direction: 'AT_LEAST',
    warningThreshold: '0.8',
  }
}

export function capabilityFields(capabilities?: DataSourceStatisticsCapabilities | null): KpiCapabilityField[] {
  return (capabilities?.fields ?? [])
    .filter(field => field.readable)
    .map(field => ({
      code: field.code,
      name: field.name,
      type: field.type,
      numeric: field.numeric && !['MONEY', 'CURRENCY'].includes(field.type.toUpperCase()),
      temporal: field.temporal,
      readable: field.readable,
    }))
}

export function kpiAggregationChanged(draft: KpiEditorDraft) {
  if (draft.aggregation === 'COUNT') draft.measureFieldCode = null
}

export function validateKpiEditorDraft(
  draft: KpiEditorDraft,
  capabilities?: DataSourceStatisticsCapabilities | null,
): KpiEditorIssue[] {
  const issues: KpiEditorIssue[] = []
  const fields = capabilityFields(capabilities)
  const numericCodes = new Set(fields.filter(field => field.numeric).map(field => field.code))
  const temporalCodes = new Set(fields.filter(field => field.temporal).map(field => field.code))
  if (!draft.name.trim()) issues.push({ code: 'NAME_REQUIRED', path: 'name', message: '请输入 KPI 名称' })
  if (!draft.dataSourceId) issues.push({ code: 'DATA_SOURCE_REQUIRED', path: 'dataSourceId', message: '请选择已发布数据源' })
  if (draft.aggregation !== 'COUNT' && !draft.measureFieldCode) {
    issues.push({ code: 'MEASURE_REQUIRED', path: 'measureFieldCode', message: `${draft.aggregation} 必须选择数值度量字段` })
  } else if (draft.measureFieldCode && !numericCodes.has(draft.measureFieldCode)) {
    issues.push({ code: 'MEASURE_UNAVAILABLE', path: 'measureFieldCode', message: '度量字段不可读、不是数值字段或不支持货币聚合' })
  }
  if (!draft.timeFieldCode) {
    issues.push({ code: 'TIME_FIELD_REQUIRED', path: 'timeFieldCode', message: '请选择时间字段' })
  } else if (!temporalCodes.has(draft.timeFieldCode)) {
    issues.push({ code: 'TIME_FIELD_UNAVAILABLE', path: 'timeFieldCode', message: '时间字段不可读或不是时间类型' })
  }
  const warningThreshold = normalizeCanonicalDecimal(draft.warningThreshold)
  if (!warningThreshold || warningThreshold === '0' || compareCanonicalDecimals(warningThreshold, '1') > 0) {
    issues.push({ code: 'WARNING_THRESHOLD_INVALID', path: 'warningThreshold', message: '预警阈值必须使用大于 0 且不超过 1 的规范十进制字符串' })
  }
  return issues
}

export function normalizeNonNegativeDecimal(value: string) {
  return normalizeCanonicalDecimal(value)
}

function normalizeCanonicalDecimal(value: string) {
  const normalized = value.trim()
  if (!/^(?:0|[1-9]\d*)(?:\.\d+)?$/.test(normalized)) return null
  const [integer = '0', fraction = ''] = normalized.split('.')
  const compactFraction = fraction.replace(/0+$/, '')
  return compactFraction ? `${integer}.${compactFraction}` : integer
}

function compareCanonicalDecimals(left: string, right: string) {
  const [leftInteger = '0', leftFraction = ''] = left.split('.')
  const [rightInteger = '0', rightFraction = ''] = right.split('.')
  if (leftInteger.length !== rightInteger.length) return leftInteger.length - rightInteger.length
  const integerComparison = leftInteger.localeCompare(rightInteger)
  if (integerComparison) return integerComparison
  const width = Math.max(leftFraction.length, rightFraction.length)
  return leftFraction.padEnd(width, '0').localeCompare(rightFraction.padEnd(width, '0'))
}

export function alignedPeriodStart(periodType: KpiPeriodType, date: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date)
  if (!match) return null
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  if (month < 1 || month > 12 || day !== 1) return null
  if (periodType === 'YEAR' && month !== 1) return null
  if (periodType === 'QUARTER' && ![1, 4, 7, 10].includes(month)) return null
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-01`
}

export function defaultPeriodStart(periodType: KpiPeriodType, now = new Date()) {
  const year = now.getFullYear()
  const month = now.getMonth() + 1
  const alignedMonth = periodType === 'YEAR' ? 1 : periodType === 'QUARTER' ? Math.floor((month - 1) / 3) * 3 + 1 : month
  return `${year}-${String(alignedMonth).padStart(2, '0')}-01`
}

export function kpiStatusLabel(status: string) {
  return ({
    ACHIEVED: '已达成',
    AT_RISK: '有风险',
    MISSED: '未达成',
    CALCULATION_FAILED: '计算失败',
  } as Record<string, string>)[status] ?? status
}

export function kpiStatusColor(status: string) {
  return ({ ACHIEVED: 'green', AT_RISK: 'orange', MISSED: 'red', CALCULATION_FAILED: 'red' } as Record<string, string>)[status] ?? 'default'
}
