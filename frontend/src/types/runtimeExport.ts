import type { RuntimeRecordQuery } from './config'

export type RuntimeExportStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface RuntimeExportCreateInput {
  query: RuntimeRecordQuery
  fieldCodes: string[]
}

export interface RuntimeExportTask {
  exportId: string
  moduleCode: string
  schemaVersionId: string
  queryHash: string
  fieldCodes: string[]
  status: RuntimeExportStatus
  totalRows?: number
  processedRows: number
  jobId: string
  resultFilename?: string
  resultSize?: number
  failureCode?: string
  failureMessage?: string
  createdAt: string
  startedAt?: string
  finishedAt?: string
}

export interface RuntimeExportTaskPage {
  items: RuntimeExportTask[]
  page: number
  size: number
  total: number
}
