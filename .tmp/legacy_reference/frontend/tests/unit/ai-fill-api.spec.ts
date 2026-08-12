import { beforeEach, describe, expect, it, vi } from 'vitest'

import { aiFillApi } from '@/services/aiFill'

function envelope(data: unknown) {
  return new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('AI_FILL record proposal API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('uses record/field-scoped create, detail, confirm and reject contracts', async () => {
    const proposal = {
      id: 'proposal/1', state: 'PENDING', moduleCode: 'work/order', recordId: 'record/1',
      fieldCode: 'ai/summary', fieldName: 'AI 摘要', resultSchema: 'STRING', sources: [],
      beforeDisplayValue: null, afterDisplayValue: '摘要', confidence: 0.92,
      clarification: null, overwrite: false, expiresAt: '2099-01-01T00:00:00Z',
      version: 3, result: null, errorCode: null,
    }
    const responses = [proposal, proposal, { ...proposal, state: 'SUCCEEDED' }, { ...proposal, state: 'REJECTED' }]
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope(responses.shift())))
    vi.stubGlobal('fetch', fetchMock)

    await aiFillApi.createProposal('10', 'work/order', 'record/1', 'ai/summary',
      { expectedRecordVersion: 7 }, 'generate-key')
    await aiFillApi.proposal('10', 'work/order', 'record/1', 'ai/summary', 'proposal/1')
    await aiFillApi.confirm('10', 'work/order', 'record/1', 'ai/summary', 'proposal/1', 3, 'confirm-key')
    await aiFillApi.reject('10', 'work/order', 'record/1', 'ai/summary', 'proposal/1', 3, 'reject-key')

    const base = '/api/v1/systems/10/modules/work%2Forder/records/record%2F1/ai-fill/ai%2Fsummary/proposals'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      base, `${base}/proposal%2F1`, `${base}/proposal%2F1/confirm`, `${base}/proposal%2F1/reject`,
    ])
    const create = fetchMock.mock.calls[0]![1] as RequestInit
    const confirm = fetchMock.mock.calls[2]![1] as RequestInit
    const reject = fetchMock.mock.calls[3]![1] as RequestInit
    expect(JSON.parse(String(create.body))).toEqual({ expectedRecordVersion: 7 })
    expect((create.headers as Headers).get('Idempotency-Key')).toBe('generate-key')
    expect(JSON.parse(String(confirm.body))).toEqual({ expectedVersion: 3 })
    expect((confirm.headers as Headers).get('Idempotency-Key')).toBe('confirm-key')
    expect(JSON.parse(String(reject.body))).toEqual({ expectedVersion: 3 })
    expect((reject.headers as Headers).get('Idempotency-Key')).toBe('reject-key')
  })
})

