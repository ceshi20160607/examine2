import { beforeEach, describe, expect, it, vi } from 'vitest'

import { memberDirectoryApi } from '@/services/memberDirectory'

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

describe('member directory API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('maps tenant member search to the ordinary runtime endpoint', async () => {
    const page = {
      items: [{ memberId: '101', memberCode: 'M-101', displayName: '张三' }],
      page: 2,
      size: 10,
      total: 1,
    }
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok(page))
    vi.stubGlobal('fetch', fetchMock)

    await expect(memberDirectoryApi.list('10/20', ' 张三 ', 2, 10)).resolves.toEqual(page)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/v1/systems/10%2F20/directory/members?page=2&size=10&keyword=%E5%BC%A0%E4%B8%89',
    )
  })
})
