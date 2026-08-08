export type PrintTemplateStatus = 'ENABLED' | 'DISABLED' | 'ARCHIVED'
export type PrintPaperSize = 'A4' | 'A5'
export type PrintOrientation = 'PORTRAIT' | 'LANDSCAPE'
export type RuntimePrintStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface PrintTemplateDefinition {
  header?: string
  title: string
  fieldCodes: string[]
  footer: string
  positionedElements?: PrintPositionedElement[]
  codeBlocks?: PrintCodeBlock[]
}

export interface PrintPositionedElement {
  kind: 'SIGNATURE' | 'SEAL' | 'PREPRINT'
  label: string
  fieldCode?: string
  xMm: number
  yMm: number
  widthMm: number
  heightMm: number
}

export interface PrintCodeBlock { kind: 'QR' | 'BARCODE'; label: string; fieldCode: string }

export interface PrintTemplate {
  templateId: string
  moduleId: string
  moduleCode: string
  code: string
  name: string
  status: PrintTemplateStatus
  paperSize: PrintPaperSize
  orientation: PrintOrientation
  definition: PrintTemplateDefinition
  publishedVersionId?: string
  publishedVersionNo?: number
  publishedSchemaVersionId?: string
  version: number
  updatedAt: string
}

export interface RuntimePrintTemplate {
  templateId: string
  code: string
  name: string
  paperSize: PrintPaperSize
  orientation: PrintOrientation
  templateVersionId: string
  templateVersionNo: number
  schemaVersionId: string
}

export interface RuntimePrintPreview {
  templateCode: string
  templateVersionId: string
  templateVersionNo: number
  recordId: string
  recordVersion: number
  html: string
}

export interface RuntimePrintTask {
  printId: string
  recordId: string
  recordVersion: number
  recordNo: string
  templateCode: string
  templateName: string
  templateVersionId: string
  templateVersionNo: number
  schemaVersionId: string
  status: RuntimePrintStatus
  jobId: string
  resultFilename?: string
  resultSize?: number
  failureCode?: string
  failureMessage?: string
  createdAt: string
  startedAt?: string
  finishedAt?: string
}

export interface RuntimePrintTaskPage {
  items: RuntimePrintTask[]
  page: number
  size: number
  total: number
}
