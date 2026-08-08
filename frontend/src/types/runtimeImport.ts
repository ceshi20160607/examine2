export type RuntimeImportMode = 'NEW' | 'UPSERT'

export type RuntimeImportBatchStatus =
  | 'PREVIEW_QUEUED'
  | 'PREVIEWING'
  | 'READY'
  | 'INVALID'
  | 'COMMIT_QUEUED'
  | 'COMMITTING'
  | 'COMMITTED'
  | 'ROLLBACK_QUEUED'
  | 'ROLLING_BACK'
  | 'ROLLED_BACK'
  | 'FAILED'

export interface RuntimeImportTemplateField {
  fieldCode: string
  fieldName: string
  type: string
  required: boolean
  unique: boolean
}

export interface RuntimeImportExcludedField {
  fieldCode: string
  fieldName: string
  type: string
  reason: string
}

export interface RuntimeImportTemplate {
  schemaVersionId: string
  moduleSnapshotId: string
  checksum: string
  fields: RuntimeImportTemplateField[]
  excludedFields: RuntimeImportExcludedField[]
  modes: RuntimeImportMode[]
  maxRows: number
}

export interface RuntimeImportPreviewInput {
  mode: RuntimeImportMode
  matchFieldCode?: string
  rows: Record<string, unknown>[]
}

export interface RuntimeImportRow {
  rowNumber: number
  action?: 'NEW' | 'UPDATE'
  status: 'PREVIEW_PENDING' | 'VALID' | 'INVALID' | 'COMMITTED' | 'ROLLED_BACK'
  errorCode?: string
  errorMessage?: string
  targetRecordId?: string
  targetAfterVersion?: number
}

export interface RuntimeImportBatch {
  batchId: string
  moduleCode: string
  schemaVersionId: string
  mode: RuntimeImportMode
  matchFieldCode?: string
  status: RuntimeImportBatchStatus
  totalRows: number
  newRows: number
  updateRows: number
  failedRows: number
  previewJobId: string
  commitJobId?: string
  rollbackJobId?: string
  rows: RuntimeImportRow[]
}

export interface RuntimeImportBatchPage {
  items: RuntimeImportBatch[]
  page: number
  size: number
  total: number
}
