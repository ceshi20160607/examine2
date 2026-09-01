import { describe, expect, it } from 'vitest'
import { aiWriteCanConfirm, normalizedAiWriteFields } from './ai-write'
import type { AiWriteCandidate } from './types'

const candidate = {
  pendingWriteId: 47, conversationId: 1, executionId: 1, agentId: 1, agentVersionId: 1,
  moduleCode: 'customer', outcome: 'READY', status: 'PENDING_CONFIRMATION', message: '待确认',
  retryable: false, confirmationRequired: true, businessWritten: false, sourceType: 'TEXT',
  inputText: '客户名称：星河', proposedTitle: '星河', proposedStatus: 'ACTIVE', fields: [],
  unknownSegments: [], version: 0, createdAt: '', updatedAt: '',
} satisfies AiWriteCandidate

describe('AI confirmed write boundary', () => {
  it('requires an explicit check and a title before confirmation', () => {
    expect(aiWriteCanConfirm(candidate, false, '星河')).toBe(false)
    expect(aiWriteCanConfirm(candidate, true, '')).toBe(false)
    expect(aiWriteCanConfirm(candidate, true, '星河')).toBe(true)
  })

  it('never reconfirms a completed or refused candidate', () => {
    expect(aiWriteCanConfirm({ ...candidate, status: 'CONFIRMED', businessWritten: true }, true, '星河')).toBe(false)
    expect(aiWriteCanConfirm({ ...candidate, status: 'REFUSED' }, true, '星河')).toBe(false)
  })

  it('removes empty optional values but preserves false and zero', () => {
    expect(normalizedAiWriteFields({ empty: '', missing: null, off: false, count: 0, name: '星河' }))
      .toEqual({ off: false, count: 0, name: '星河' })
  })
})
