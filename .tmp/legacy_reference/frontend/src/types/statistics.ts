export type StatisticsAggregation = 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX'
export type StatisticsGrain = 'DAY' | 'WEEK' | 'MONTH'
export type StatisticsVisualization = 'STAT_VALUE' | 'BAR_CHART' | 'PIE_CHART' | 'LINE_TREND'

export interface StatisticsGrouping {
  fieldCode: string
  bucketLimit: number
}

export interface StatisticsTrend {
  fieldCode: string
  grain: StatisticsGrain
  startInclusive: string
  endExclusive: string
}

export interface DataSourceStatisticsRequest {
  aggregation: StatisticsAggregation
  measureFieldCode?: string | null
  grouping?: StatisticsGrouping | null
  trend?: StatisticsTrend | null
}

export interface StatisticsFieldCapability {
  code: string
  name: string
  type: string
  readable: boolean
  numeric: boolean
  temporal: boolean
  groupable: boolean
}

export interface DataSourceStatisticsCapabilities {
  dataSourceId: string
  dataSourceCode: string
  dataSourceVersionId: string
  dataSourceVersionNumber: number
  moduleCode: string
  schemaVersionId: string
  fields: StatisticsFieldCapability[]
}

export interface StatisticsGroupBucket {
  key: string | null
  label: string | null
  nullBucket: boolean
  value: string | null
  recordCount: number
}

export interface StatisticsTrendBucket {
  startInclusive: string
  endExclusive: string
  value: string | null
  recordCount: number
  empty: boolean
}

export interface DataSourceStatisticsResult {
  queryId: string
  dataSourceId: string
  dataSourceCode: string
  dataSourceVersionId: string
  dataSourceVersionNumber: number
  moduleCode: string
  schemaVersionId: string
  aggregation: StatisticsAggregation
  measureFieldCode: string | null
  value: string | null
  matchedRecordCount: number
  aggregateCount: number
  bucketCount: number
  groupBuckets: StatisticsGroupBucket[]
  trendBuckets: StatisticsTrendBucket[]
  totalBucketCount: number
  truncated: boolean
}
