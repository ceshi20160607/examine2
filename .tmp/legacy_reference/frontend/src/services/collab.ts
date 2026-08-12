import { apiRequest } from './api'
import type {
  AddRecordTeamMemberInput,
  ChangeRecordTeamRoleInput,
  InitializeRecordTeamResult,
  RecordTeam,
  TransferRecordTeamOwnershipInput,
} from '@/types/collab'

function root(systemId: string, moduleCode: string, recordId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}`
    + `/runtime/modules/${encodeURIComponent(moduleCode)}`
    + `/records/${encodeURIComponent(recordId)}/team`
}

export const recordTeamApi = {
  get(systemId: string, moduleCode: string, recordId: string) {
    return apiRequest<RecordTeam>(root(systemId, moduleCode, recordId))
  },
  initialize(systemId: string, moduleCode: string, recordId: string) {
    return apiRequest<InitializeRecordTeamResult>(`${root(systemId, moduleCode, recordId)}:initialize`, {
      method: 'POST',
      idempotencyKey: crypto.randomUUID(),
    })
  },
  addMember(
    systemId: string,
    moduleCode: string,
    recordId: string,
    input: AddRecordTeamMemberInput,
  ) {
    return apiRequest<RecordTeam>(`${root(systemId, moduleCode, recordId)}/members`, {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  changeRole(
    systemId: string,
    moduleCode: string,
    recordId: string,
    memberId: string,
    input: ChangeRecordTeamRoleInput,
  ) {
    return apiRequest<RecordTeam>(
      `${root(systemId, moduleCode, recordId)}/members/${encodeURIComponent(memberId)}/role`,
      {
        method: 'PUT',
        body: input,
      },
    )
  },
  removeMember(
    systemId: string,
    moduleCode: string,
    recordId: string,
    memberId: string,
  ) {
    return apiRequest<RecordTeam>(
      `${root(systemId, moduleCode, recordId)}/members/${encodeURIComponent(memberId)}`,
      { method: 'DELETE' },
    )
  },
  transferOwnership(
    systemId: string,
    moduleCode: string,
    recordId: string,
    input: TransferRecordTeamOwnershipInput,
  ) {
    return apiRequest<RecordTeam>(`${root(systemId, moduleCode, recordId)}/transfer`, {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
}
