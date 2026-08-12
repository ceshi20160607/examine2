import { apiRequest } from '@/services/api'
import type { AiFillProposal, CreateAiFillProposalInput } from '@/types/aiFill'

function proposalBase(
  systemId: string,
  moduleCode: string,
  recordId: string,
  fieldCode: string,
) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}`
    + `/modules/${encodeURIComponent(moduleCode)}`
    + `/records/${encodeURIComponent(recordId)}`
    + `/ai-fill/${encodeURIComponent(fieldCode)}/proposals`
}

export const aiFillApi = {
  createProposal(
    systemId: string,
    moduleCode: string,
    recordId: string,
    fieldCode: string,
    input: CreateAiFillProposalInput,
    idempotencyKey: string,
  ) {
    return apiRequest<AiFillProposal>(proposalBase(systemId, moduleCode, recordId, fieldCode), {
      method: 'POST', body: input, idempotencyKey,
    })
  },
  proposal(
    systemId: string,
    moduleCode: string,
    recordId: string,
    fieldCode: string,
    proposalId: string,
  ) {
    return apiRequest<AiFillProposal>(
      `${proposalBase(systemId, moduleCode, recordId, fieldCode)}/${encodeURIComponent(proposalId)}`,
    )
  },
  confirm(
    systemId: string,
    moduleCode: string,
    recordId: string,
    fieldCode: string,
    proposalId: string,
    expectedVersion: number,
    idempotencyKey: string,
  ) {
    return apiRequest<AiFillProposal>(
      `${proposalBase(systemId, moduleCode, recordId, fieldCode)}/${encodeURIComponent(proposalId)}/confirm`,
      { method: 'POST', body: { expectedVersion }, idempotencyKey },
    )
  },
  reject(
    systemId: string,
    moduleCode: string,
    recordId: string,
    fieldCode: string,
    proposalId: string,
    expectedVersion: number,
    idempotencyKey: string,
  ) {
    return apiRequest<AiFillProposal>(
      `${proposalBase(systemId, moduleCode, recordId, fieldCode)}/${encodeURIComponent(proposalId)}/reject`,
      { method: 'POST', body: { expectedVersion }, idempotencyKey },
    )
  },
}
