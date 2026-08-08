import { apiRequest } from './api'
import type {
  CreateRecordCommentInput,
  RecordComment,
  RecordCommentPage,
  UpdateRecordCommentInput,
} from '@/types/comment'

function root(systemId: string, moduleCode: string, recordId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}`
    + `/runtime/modules/${encodeURIComponent(moduleCode)}`
    + `/records/${encodeURIComponent(recordId)}/comments`
}

export const recordCommentApi = {
  list(systemId: string, moduleCode: string, recordId: string, page = 1, size = 20) {
    return apiRequest<RecordCommentPage>(
      `${root(systemId, moduleCode, recordId)}?page=${page}&size=${size}`,
    )
  },
  create(
    systemId: string,
    moduleCode: string,
    recordId: string,
    input: CreateRecordCommentInput,
  ) {
    return apiRequest<RecordComment>(root(systemId, moduleCode, recordId), {
      method: 'POST',
      body: input,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  update(
    systemId: string,
    moduleCode: string,
    recordId: string,
    commentId: string,
    input: UpdateRecordCommentInput,
  ) {
    return apiRequest<RecordComment>(
      `${root(systemId, moduleCode, recordId)}/${encodeURIComponent(commentId)}`,
      { method: 'PUT', body: input },
    )
  },
  remove(
    systemId: string,
    moduleCode: string,
    recordId: string,
    commentId: string,
    version: number,
  ) {
    return apiRequest<RecordComment>(
      `${root(systemId, moduleCode, recordId)}/${encodeURIComponent(commentId)}`,
      { method: 'DELETE', body: { version } },
    )
  },
}
