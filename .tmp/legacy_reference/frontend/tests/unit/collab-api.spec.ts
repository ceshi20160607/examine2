import { beforeEach, describe, expect, it, vi } from 'vitest'

import { recordTeamApi } from '@/services/collab'

const team = {
  systemId: '10',
  tenantId: '20',
  recordId: '30',
  version: 1,
  ownerMemberId: '100',
  members: [{ memberId: '100', role: 'OWNER' }],
}
const initialization = { created: true, team }

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

describe('record team API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=collab-csrf; path=/'
  })

  it('reads a record team from the scoped runtime record path', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok(team))
    vi.stubGlobal('fetch', fetchMock)

    await expect(recordTeamApi.get('10', 'work_order', '30')).resolves.toEqual(team)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/v1/systems/10/runtime/modules/work_order/records/30/team',
    )
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ credentials: 'include' })
  })

  it('initializes and adds a member with mutation metadata', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, _init?: RequestInit) =>
      ok(String(input).endsWith(':initialize') ? initialization : team))
    vi.stubGlobal('fetch', fetchMock)

    await expect(recordTeamApi.initialize('10', 'work_order', '30')).resolves.toEqual(initialization)
    await recordTeamApi.addMember('10', 'work_order', '30', {
      memberId: '101',
      role: 'COLLABORATOR',
    })

    const [initializePath, initializeOptions] = fetchMock.mock.calls[0] as unknown as [
      string,
      RequestInit,
    ]
    const [addPath, addOptions] = fetchMock.mock.calls[1] as unknown as [
      string,
      RequestInit,
    ]
    expect(initializePath).toBe(
      '/api/v1/systems/10/runtime/modules/work_order/records/30/team:initialize',
    )
    expect(initializeOptions.method).toBe('POST')
    expect((initializeOptions.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(addPath).toBe(
      '/api/v1/systems/10/runtime/modules/work_order/records/30/team/members',
    )
    expect(addOptions.method).toBe('POST')
    expect((addOptions.headers as Headers).get('X-CSRF-Token')).toBe('collab-csrf')
    expect((addOptions.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(addOptions.body))).toEqual({
      memberId: '101',
      role: 'COLLABORATOR',
    })
  })

  it('uses member-scoped role/removal paths and the transfer command', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok(team))
    vi.stubGlobal('fetch', fetchMock)

    await recordTeamApi.changeRole('10', 'work_order', '30', 'member/101', {
      role: 'VIEWER',
    })
    await recordTeamApi.removeMember('10', 'work_order', '30', 'member/101')
    await recordTeamApi.transferOwnership('10', 'work_order', '30', {
      targetMemberId: '102',
    })

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/v1/systems/10/runtime/modules/work_order/records/30/team/members/member%2F101/role',
    )
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'PUT' })
    expect(fetchMock.mock.calls[1]?.[0]).toBe(
      '/api/v1/systems/10/runtime/modules/work_order/records/30/team/members/member%2F101',
    )
    expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({ method: 'DELETE' })
    expect(fetchMock.mock.calls[2]?.[0]).toBe(
      '/api/v1/systems/10/runtime/modules/work_order/records/30/team/transfer',
    )
    expect(JSON.parse(String(fetchMock.mock.calls[2]?.[1]?.body))).toEqual({
      targetMemberId: '102',
    })
  })
})
