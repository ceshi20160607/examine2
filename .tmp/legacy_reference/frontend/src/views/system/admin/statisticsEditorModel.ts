import type { DataSourceFieldCapability } from '@/types/dataSource'
import type {
  DataSourceStatisticsRequest,
  StatisticsAggregation,
  StatisticsGrain,
  StatisticsVisualization,
  StatisticsFieldCapability,
} from '@/types/statistics'

export const STATISTICS_GROUP_LIMIT_MAX = 20
export const STATISTICS_TREND_BUCKET_MAX = 100

const NUMERIC_FIELD_TYPES = new Set([
  'NUMBER', 'DECIMAL', 'INTEGER', 'INT', 'LONG', 'FLOAT', 'DOUBLE',
  'PERCENT', 'PERCENTAGE', 'RATING', 'PROGRESS',
])
const NON_GROUPABLE_FIELD_TYPES = new Set(['TEXTAREA', 'MONEY', 'CURRENCY'])

export interface StatisticsCapabilities {
  fields: StatisticsControlField[]
  measureFields: StatisticsControlField[]
  groupFields: StatisticsControlField[]
  timeFields: StatisticsControlField[]
}

export interface StatisticsControlField {
  fieldCode: string
  fieldName: string
  type: string
  numeric: boolean
  temporal: boolean
  groupable: boolean
}

export interface StatisticsQueryDraft {
  aggregation: StatisticsAggregation
  measureFieldCode: string
  groupFieldCode: string
  bucketLimit: number
  timeFieldCode: string
  grain: StatisticsGrain
  startInclusive: string
  endExclusive: string
}

export interface StatisticsClientIssue {
  code: string
  path: string
  message: string
}

export function statisticsCapabilities(
  fields: Array<DataSourceFieldCapability | StatisticsFieldCapability>,
  outputFieldCodes?: string[],
): StatisticsCapabilities {
  const output = outputFieldCodes ? new Set(outputFieldCodes) : null
  const publishedFields = fields.flatMap((field): StatisticsControlField[] => {
    if ('code' in field) {
      if (!field.readable || output && !output.has(field.code)) return []
      return [{ fieldCode: field.code, fieldName: field.name, type: field.type, numeric: field.numeric, temporal: field.temporal, groupable: field.groupable }]
    }
    if (!field.available || output && !output.has(field.fieldCode)) return []
    return [{
      fieldCode: field.fieldCode,
      fieldName: field.fieldName,
      type: field.type,
      numeric: NUMERIC_FIELD_TYPES.has(field.type.toUpperCase()),
      temporal: field.temporal,
      groupable: !NON_GROUPABLE_FIELD_TYPES.has(field.type.toUpperCase()),
    }]
  })
  return {
    fields: publishedFields,
    measureFields: publishedFields.filter(field => field.numeric),
    groupFields: publishedFields.filter(field => field.groupable),
    timeFields: publishedFields.filter(field => field.temporal),
  }
}

export function createStatisticsDraft(): StatisticsQueryDraft {
  return {
    aggregation: 'COUNT',
    measureFieldCode: '',
    groupFieldCode: '',
    bucketLimit: 10,
    timeFieldCode: '',
    grain: 'DAY',
    startInclusive: '',
    endExclusive: '',
  }
}

export function statisticsDraftFromRequest(request?: DataSourceStatisticsRequest | null): StatisticsQueryDraft {
  const draft = createStatisticsDraft()
  if (!request) return draft
  draft.aggregation = request.aggregation
  draft.measureFieldCode = request.measureFieldCode ?? ''
  draft.groupFieldCode = request.grouping?.fieldCode ?? ''
  draft.bucketLimit = request.grouping?.bucketLimit ?? 10
  draft.timeFieldCode = request.trend?.fieldCode ?? ''
  draft.grain = request.trend?.grain ?? 'DAY'
  draft.startInclusive = request.trend?.startInclusive ?? ''
  draft.endExclusive = request.trend?.endExclusive ?? ''
  return draft
}

export function applyStatisticsVisualization(
  draft: StatisticsQueryDraft,
  visualization: StatisticsVisualization,
) {
  if (visualization === 'STAT_VALUE') {
    draft.groupFieldCode = ''
    draft.timeFieldCode = ''
  } else if (visualization === 'BAR_CHART' || visualization === 'PIE_CHART') {
    draft.timeFieldCode = ''
  } else {
    draft.groupFieldCode = ''
  }
}

export function aggregationChanged(draft: StatisticsQueryDraft) {
  if (draft.aggregation === 'COUNT') draft.measureFieldCode = ''
}

export function validateStatisticsDraft(
  draft: StatisticsQueryDraft,
  capabilities: StatisticsCapabilities,
  visualization?: StatisticsVisualization,
): StatisticsClientIssue[] {
  const issues: StatisticsClientIssue[] = []
  const measureCodes = new Set(capabilities.measureFields.map(field => field.fieldCode))
  const groupCodes = new Set(capabilities.groupFields.map(field => field.fieldCode))
  const timeCodes = new Set(capabilities.timeFields.map(field => field.fieldCode))

  if (draft.aggregation === 'COUNT' && draft.measureFieldCode) {
    issues.push({ code: 'COUNT_MEASURE_FORBIDDEN', path: 'measureFieldCode', message: 'COUNT 不使用度量字段' })
  }
  if (draft.aggregation !== 'COUNT' && !draft.measureFieldCode) {
    issues.push({ code: 'MEASURE_REQUIRED', path: 'measureFieldCode', message: `${draft.aggregation} 必须选择数值度量字段` })
  } else if (draft.measureFieldCode && !measureCodes.has(draft.measureFieldCode)) {
    issues.push({ code: 'MEASURE_NOT_CAPABLE', path: 'measureFieldCode', message: '度量字段必须是已发布、可读取的数值输出字段' })
  }

  if (draft.groupFieldCode && draft.timeFieldCode) {
    issues.push({ code: 'GROUP_TREND_CONFLICT', path: 'grouping', message: '分组统计与时间趋势不能同时启用' })
  }
  if (draft.groupFieldCode) {
    if (!groupCodes.has(draft.groupFieldCode)) {
      issues.push({ code: 'GROUP_NOT_CAPABLE', path: 'grouping.fieldCode', message: '分组字段必须来自已发布的可读取输出字段' })
    }
    if (!Number.isInteger(draft.bucketLimit) || draft.bucketLimit < 1 || draft.bucketLimit > STATISTICS_GROUP_LIMIT_MAX) {
      issues.push({ code: 'GROUP_LIMIT_INVALID', path: 'grouping.bucketLimit', message: '分组数量必须为 1 到 20' })
    }
  }
  if (visualization === 'BAR_CHART' || visualization === 'PIE_CHART') {
    if (!draft.groupFieldCode) issues.push({ code: 'GROUP_REQUIRED', path: 'grouping', message: `${visualization} 必须选择分组字段` })
  }

  if (draft.timeFieldCode) {
    if (!timeCodes.has(draft.timeFieldCode)) {
      issues.push({ code: 'TIME_NOT_CAPABLE', path: 'trend.fieldCode', message: '趋势字段必须是已发布、可读取的时间输出字段' })
    }
    const rangeIssue = validateTrendRange(draft.startInclusive, draft.endExclusive, draft.grain)
    if (rangeIssue) issues.push(rangeIssue)
  }
  if (visualization === 'LINE_TREND' && !draft.timeFieldCode) {
    issues.push({ code: 'TIME_REQUIRED', path: 'trend', message: 'LINE_TREND 必须选择时间字段和范围' })
  }
  if (visualization === 'STAT_VALUE' && (draft.groupFieldCode || draft.timeFieldCode)) {
    issues.push({ code: 'STAT_VALUE_DIMENSION_FORBIDDEN', path: 'visualization', message: 'STAT_VALUE 只能展示单个聚合值' })
  }
  return issues
}

export function buildStatisticsRequest(draft: StatisticsQueryDraft): DataSourceStatisticsRequest {
  return {
    aggregation: draft.aggregation,
    measureFieldCode: draft.aggregation === 'COUNT' ? null : draft.measureFieldCode,
    grouping: draft.groupFieldCode
      ? { fieldCode: draft.groupFieldCode, bucketLimit: draft.bucketLimit }
      : null,
    trend: draft.timeFieldCode
      ? {
          fieldCode: draft.timeFieldCode,
          grain: draft.grain,
          startInclusive: draft.startInclusive,
          endExclusive: draft.endExclusive,
        }
      : null,
  }
}

export function isCanonicalDecimal(value: string): boolean {
  return /^(?:0|-?(?:[1-9]\d*|0\.\d*[1-9]|[1-9]\d*\.\d*[1-9]))$/.test(value)
}

function validateTrendRange(
  startInclusive: string,
  endExclusive: string,
  grain: StatisticsGrain,
): StatisticsClientIssue | null {
  if (!isIsoDate(startInclusive) || !isIsoDate(endExclusive)) {
    return { code: 'TREND_RANGE_REQUIRED', path: 'trend', message: '趋势开始和结束日期均为必填项' }
  }
  const start = parseIsoDate(startInclusive)
  const end = parseIsoDate(endExclusive)
  if (end <= start) return { code: 'TREND_RANGE_INVALID', path: 'trend', message: '趋势结束日期必须晚于开始日期' }
  if (!isAligned(start, grain) || !isAligned(end, grain)) {
    return { code: 'TREND_RANGE_NOT_ALIGNED', path: 'trend', message: `趋势范围必须按 ${grain} 边界对齐` }
  }
  const buckets = bucketCount(start, end, grain)
  if (buckets < 1 || buckets > STATISTICS_TREND_BUCKET_MAX) {
    return { code: 'TREND_RANGE_TOO_LARGE', path: 'trend', message: '趋势范围最多包含 100 个时间桶' }
  }
  return null
}

function isIsoDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const date = parseIsoDate(value)
  return Number.isFinite(date.valueOf()) && date.toISOString().slice(0, 10) === value
}

function parseIsoDate(value: string) {
  return new Date(`${value}T00:00:00.000Z`)
}

function isAligned(value: Date, grain: StatisticsGrain) {
  if (grain === 'DAY') return true
  if (grain === 'WEEK') return value.getUTCDay() === 1
  return value.getUTCDate() === 1
}

function bucketCount(start: Date, end: Date, grain: StatisticsGrain) {
  if (grain === 'DAY') return Math.round((end.valueOf() - start.valueOf()) / 86_400_000)
  if (grain === 'WEEK') return Math.round((end.valueOf() - start.valueOf()) / (7 * 86_400_000))
  return (end.getUTCFullYear() - start.getUTCFullYear()) * 12 + end.getUTCMonth() - start.getUTCMonth()
}
