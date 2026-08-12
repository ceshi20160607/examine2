import { beforeEach, describe, expect, it, vi } from 'vitest'

import { runtimeApi } from '@/services/config'

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

describe('runtime record flow-state API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('maps the scoped record flow-state endpoint and preserves null data', async () => {
    const fetchMock = vi.fn()
      .mockImplementationOnce(() => ok({
        instanceId: '701',
        status: 'PENDING',
        version: 0,
        updatedAt: '2026-07-27T20:30:00Z',
      }))
      .mockImplementationOnce(() => ok(null))
    vi.stubGlobal('fetch', fetchMock)

    const current = await runtimeApi.flowState('10', 'purchase/order', '9001')
    const unbound = await runtimeApi.flowState('10', 'purchase/order', '9002')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/runtime/modules/purchase%2Forder/records/9001/flow-state',
      '/api/v1/systems/10/runtime/modules/purchase%2Forder/records/9002/flow-state',
    ])
    expect((fetchMock.mock.calls[0]![1] as RequestInit).method).toBeUndefined()
    expect(current).toEqual({
      instanceId: '701',
      status: 'PENDING',
      version: 0,
      updatedAt: '2026-07-27T20:30:00Z',
    })
    expect(unbound).toBeNull()
  })

  it('maps the plural flow-states endpoint and preserves the returned order', async () => {
    const states = [
      {
        instanceId: '701',
        status: 'PENDING',
        version: 0,
        updatedAt: '2026-07-27T20:30:00Z',
      },
      {
        instanceId: '702',
        status: 'APPROVED',
        version: 1,
        updatedAt: '2026-07-27T20:31:00Z',
      },
    ]
    const fetchMock = vi.fn()
      .mockImplementationOnce(() => ok(states))
      .mockImplementationOnce(() => ok([]))
    vi.stubGlobal('fetch', fetchMock)

    const current = await runtimeApi.flowStates('10', 'purchase/order', '9001')
    const unbound = await runtimeApi.flowStates('10', 'purchase/order', '9002')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/runtime/modules/purchase%2Forder/records/9001/flow-states',
      '/api/v1/systems/10/runtime/modules/purchase%2Forder/records/9002/flow-states',
    ])
    expect(current).toEqual(states)
    expect(unbound).toEqual([])
  })
})
