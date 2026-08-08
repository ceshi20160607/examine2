export interface RecordComment {
  commentId: string
  recordId: string
  parentCommentId: string | null
  authorMemberId: string
  body: string | null
  deleted: boolean
  version: number
  createdAt: string
  updatedAt: string
  mentionedMemberIds: string[]
  canEdit: boolean
  canDelete: boolean
}

export interface RecordCommentPage {
  items: RecordComment[]
  page: number
  size: number
  total: number
}

export interface CreateRecordCommentInput {
  body: string
  parentCommentId?: string
  mentionedMemberIds?: string[]
}

export interface UpdateRecordCommentInput {
  body: string
  version: number
}
