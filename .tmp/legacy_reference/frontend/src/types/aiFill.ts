export type AiFillResultSchema = 'STRING' | 'DECIMAL' | 'INTEGER' | 'DATE' | 'DATETIME' | 'BOOLEAN'
export type AiFillProposalState = 'CLARIFICATION_REQUIRED' | 'PENDING' | 'EXECUTING' | 'SUCCEEDED' | 'FAILED' | 'REJECTED' | 'EXPIRED'

export interface AiFillSourcePreview {
  fieldCode: string
  fieldName: string
  displayValue: string | null
  masked: boolean
  sourceVersion?: number | string
}

export interface AiFillMaterializationResult {
  materializationId: string
  recordId: string
  recordVersion: number
  fieldCode: string
  displayValue: string
  confidence: number
}

export interface AiFillProposal {
  id: string
  state: AiFillProposalState
  moduleCode: string
  recordId: string
  fieldCode: string
  fieldName: string
  resultSchema: AiFillResultSchema
  sources: AiFillSourcePreview[]
  beforeDisplayValue: string | null
  afterDisplayValue: string | null
  confidence: number
  clarification: string | null
  overwrite: boolean
  expiresAt: string
  version: number
  result: AiFillMaterializationResult | null
  errorCode: string | null
}

export interface CreateAiFillProposalInput {
  expectedRecordVersion: number
}
