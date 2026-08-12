import type { RuntimeDataSourceRow } from './dataSource'

export type ReportCheckSeverity = 'BLOCKER' | 'WARNING'

export interface ReportDraft {
  dataSourceId: string
  outputFieldCodes: string[]
}

export interface ReportSummary {
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

export interface ReportDetail extends ReportSummary {
  draft: ReportDraft
}

export interface CreateReportInput extends ReportDraft {
  code: string
  name: string
  description: string | null
}

export interface SaveReportDraftInput extends ReportDraft {
  expectedVersion: number
  name: string
  description: string | null
}

export interface ReportFieldPin {
  logicalFieldId: string
  code: string
  name: string
  type: string
  queryType: string
}

export interface ReportFieldCapability extends ReportFieldPin {
  readable: boolean
}

export interface ReportSourcePin {
  dataSourceId: string
  dataSourceVersionId: string
  dataSourceVersionNumber: number
  dataSourceCode: string
  dataSourceName: string
  moduleId: string
  moduleCode: string
  schemaVersionId: string
  fields: ReportFieldPin[]
}

export interface ReportCheckSource extends Omit<ReportSourcePin, 'fields'> {
  fields: ReportFieldCapability[]
}

export interface ReportCheckIssue {
  severity: ReportCheckSeverity
  code: string
  path: string
  message: string
}

export interface ReportCheckResult {
  reportId: string
  checkedDraftVersion: number
  valid: boolean
  blockerCount: number
  warningCount: number
  source: ReportCheckSource | null
  issues: ReportCheckIssue[]
}

export interface ReportVersion extends ReportSourcePin {
  id: string
  reportId: string
  versionNumber: number
  sourceDraftVersion: number
  code: string
  name: string
  description: string | null
  fingerprint: string
  publishedBy: string
  publishedAt: string
  active: boolean
}

export interface ReportPublishResult {
  report: ReportDetail
  version: ReportVersion
}

export interface RuntimeReportField {
  fieldCode: string
  fieldName: string
  type: string
}

export interface RuntimeReportMetadata {
  id: string
  code: string
  name: string
  description: string | null
  versionId: string
  versionNumber: number
  dataSourceId: string
  dataSourceCode: string
  dataSourceName: string
  dataSourceVersionId: string
  dataSourceVersionNumber: number
  moduleId: string
  moduleCode: string
  schemaVersionId: string
  fields: RuntimeReportField[]
}

export interface RuntimeReportValue {
  fieldCode: string
  fieldName?: string
  type?: string
  value?: unknown
  canonicalValue?: unknown
  displayValue?: string | null
}

export interface RuntimeReportRow extends Omit<RuntimeDataSourceRow, 'values'> {
  values: Record<string, unknown> | RuntimeReportValue[]
}

export interface RuntimeReportRows {
  rows: RuntimeReportRow[]
  items: RuntimeReportRow[]
  page: number
  size: number
  total: number
  queryHash: string | null
}

export type ReportExportStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface ReportExportStartInput {
  requestKey: string
}

export interface ReportExportTask {
  exportId: string
  reportCode: string
  reportVersionId: string
  reportVersionNumber: number
  dataSourceVersionId: string
  fieldCodes: string[]
  status: ReportExportStatus
  totalRows?: number | null
  processedRows: number
  truncated: boolean
  jobId: string
  resultFilename?: string | null
  resultSize?: number | null
  failureCode?: string | null
  failureMessage?: string | null
  createdAt: string
  startedAt?: string | null
  finishedAt?: string | null
}

export interface ReportExportTaskPage {
  items: ReportExportTask[]
  page: number
  size: number
  total: number
}

export type ReportScheduleCadenceKind = 'DAILY' | 'WEEKLY'
export type ReportScheduleRunStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface ReportScheduleCadence {
  kind: ReportScheduleCadenceKind
  localTime: string
  daysOfWeek: string[]
}

export interface ReportSchedule {
  id: string
  reportId: string
  code: string
  name: string
  enabled: boolean
  timeZone: string
  cadence: ReportScheduleCadence
  recipientMemberIds: string[]
  ownerMemberId: string
  nextFireAt: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface ReportSchedulePage {
  items: ReportSchedule[]
  page: number
  size: number
  total: number
}

export interface CreateReportScheduleInput {
  code: string
  name: string
  enabled: boolean
  timeZone: string
  cadence: ReportScheduleCadence
  recipientMemberIds: string[]
}

export interface UpdateReportScheduleInput {
  expectedVersion: number
  name: string
  timeZone: string
  cadence: ReportScheduleCadence
  recipientMemberIds: string[]
}

export interface ReportSchedulePreviewInput {
  timeZone: string
  cadence: ReportScheduleCadence
}

export interface ReportSchedulePreview {
  nextFireAt: string
  localDateTime: string
  offset: string
  timeZone: string
}

export interface ReportScheduledRun {
  id: string
  scheduleId: string
  scheduleCode: string
  scheduleName: string
  reportCode: string
  scheduledAt: string
  status: ReportScheduleRunStatus
  exportId?: string | null
  filename?: string | null
  totalRows?: number | null
  processedRows: number
  truncated: boolean
  failureCode?: string | null
  failureMessage?: string | null
  createdAt: string
  finishedAt?: string | null
}

export interface ReportScheduledRunPage {
  items: ReportScheduledRun[]
  page: number
  size: number
  total: number
}
