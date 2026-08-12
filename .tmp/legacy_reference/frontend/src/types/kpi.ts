export type KpiSubjectType = 'MEMBER' | 'DEPARTMENT' | 'ROLE'
export type KpiPeriodType = 'MONTH' | 'QUARTER' | 'YEAR'
export type KpiAggregation = 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX'
export type KpiAttainmentDirection = 'AT_LEAST' | 'AT_MOST'
export type KpiResultStatus = 'ACHIEVED' | 'AT_RISK' | 'MISSED' | 'CALCULATION_FAILED'
export type KpiCheckSeverity = 'BLOCKER' | 'WARNING'

export interface KpiDraft {
  dataSourceId: string
  subjectType: KpiSubjectType
  periodType: KpiPeriodType
  aggregation: KpiAggregation
  measureFieldCode: string | null
  timeFieldCode: string
  direction: KpiAttainmentDirection
  warningThreshold: string
}

export interface KpiSummary {
  id: string
  systemId: string
  tenantId: string
  code: string
  name: string
  description: string | null
  draftVersion: number
  activeVersionId: string | null
  activeVersionNumber: number | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface KpiDetail extends KpiSummary {
  draft: KpiDraft
}

export interface CreateKpiInput extends KpiDraft {
  code: string
  name: string
  description: string | null
}

export interface SaveKpiDraftInput extends KpiDraft {
  expectedVersion: number
  name: string
  description: string | null
}

export interface KpiCheckIssue {
  severity: KpiCheckSeverity
  code: string
  path: string
  message: string
}

export interface KpiCheckResult {
  kpiId: string
  checkedDraftVersion: number
  valid: boolean
  blockerCount: number
  warningCount: number
  issues: KpiCheckIssue[]
}

export interface KpiFieldPin {
  logicalFieldId: string
  code: string
  name: string
  type: string
  queryType: string
}

export interface KpiVersion {
  id: string
  kpiId: string
  versionNumber: number
  sourceDraftVersion: number
  code: string
  name: string
  description: string | null
  subjectType: KpiSubjectType
  periodType: KpiPeriodType
  aggregation: KpiAggregation
  direction: KpiAttainmentDirection
  warningThreshold: string
  dataSourceId: string
  dataSourceVersionId: string
  dataSourceVersionNumber: number
  dataSourceCode: string
  moduleCode: string
  schemaVersionId: string
  measureField: KpiFieldPin | null
  timeField: KpiFieldPin
  fingerprint: string
  publishedBy: string
  publishedAt: string
  active: boolean
}

export interface KpiPublishResult {
  kpi: KpiDetail
  version: KpiVersion
}

export interface KpiTrendBucket {
  startInclusive: string
  endExclusive: string
  value: string
  matchedCount: string
}

export interface KpiCalculationExplanation {
  statisticsQueryId: string
  matchedCount: string
  aggregation: KpiAggregation
  dataSourceCode: string
  dataSourceVersionId: string
  dataSourceVersionNumber: number
  schemaVersionId: string
  measureField: KpiFieldPin | null
  timeField: KpiFieldPin | null
  authorizationEpoch: string
  subjectMemberIds: string[]
  trend: KpiTrendBucket[]
}

export interface KpiCalculation {
  id: string
  targetId: string
  status: KpiResultStatus
  errorCode: string | null
  targetValue: string
  actualValue: string | null
  attainment: string | null
  calculatedAt: string
  calculatedBy: string
  explanation: KpiCalculationExplanation
}

export interface KpiTarget {
  id: string
  kpiId: string
  kpiVersionId: string
  kpiVersionNumber: number
  kpiCode: string
  kpiName: string
  subjectType: KpiSubjectType
  subjectId: string
  subjectName: string
  periodType: KpiPeriodType
  periodStart: string
  periodEndExclusive: string
  targetValue: string
  version: number
  createdAt: string
  updatedAt: string
  latestCalculation: KpiCalculation | null
}

export interface CreateKpiTargetInput {
  subjectId: string
  periodStart: string
  targetValue: string
}

export interface UpdateKpiTargetInput {
  targetValue: string
  expectedVersion: number
}

export interface RuntimeKpiTarget extends KpiTarget {}
