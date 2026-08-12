import { beforeEach, describe, expect, it, vi } from 'vitest'

import { recordCommentApi } from '@/services/comment'

function ok(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK',
    message: '',
    data,
    requestId: 'request-1',
    traceId: 'trace-1',
    errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

describe('record comment API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=comment-csrf; path=/'
  })

  it('maps list/create/update/delete to one scoped record path', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      ok({ commentId: '40' }))
    vi.stubGlobal('fetch', fetchMock)

    await recordCommentApi.list('10', 'work_order', '30', 2, 10)
    await recordCommentApi.create('10', 'work_order', '30', {
      body: 'hello',
      parentCommentId: '39',
      mentionedMemberIds: ['41', '42'],
    })
    await recordCommentApi.update('10', 'work_order', '30', '40/41', {
      body: 'updated',
      version: 2,
    })
    await recordCommentApi.remove('10', 'work_order', '30', '40/41', 3)

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/runtime/modules/work_order/records/30/comments?page=2&size=10',
      '/api/v1/systems/10/runtime/modules/work_order/records/30/comments',
      '/api/v1/systems/10/runtime/modules/work_order/records/30/comments/40%2F41',
      '/api/v1/systems/10/runtime/modules/work_order/records/30/comments/40%2F41',
    ])
    expect(fetchMock.mock.calls.map((call) => call[1]?.method)).toEqual([
      undefined, 'POST', 'PUT', 'DELETE',
    ])
    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))).toEqual({
      body: 'hello', parentCommentId: '39', mentionedMemberIds: ['41', '42'],
    })
    expect(JSON.parse(String(fetchMock.mock.calls[3]?.[1]?.body))).toEqual({ version: 3 })
  })
})
