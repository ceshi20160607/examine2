import { beforeEach, describe, expect, it, vi } from 'vitest'

import { contextAccessApi } from '@/services/access'
import { platformAdminApi, systemAdminApi } from '@/services/admin'

function ok(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

describe('VS2 typed API services', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('uses the frozen platform family and sends mutation metadata', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ id: 'system-1', version: '2' }))
    vi.stubGlobal('fetch', fetchMock)

    await platformAdminApi.commandSystem('system-1', 'disable', {
      reason: 'maintenance', version: '1', impactConfirmed: true,
    })

    const [path, options] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    const headers = options.headers as Headers
    expect(path).toBe('/api/v1/platform/admin/systems/system-1:disable')
    expect(options.method).toBe('POST')
    expect(headers.get('Idempotency-Key')).toBeTruthy()
    expect(headers.get('X-CSRF-Token')).toBe('csrf-token')
    expect(JSON.parse(String(options.body))).toMatchObject({ version: '1', impactConfirmed: true })
  })

  it('binds system administration reads to the current system path', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ items: [], page: 1, size: 20, total: 0 }))
    vi.stubGlobal('fetch', fetchMock)

    await systemAdminApi.listAccessRequests('system-7', { page: 2, size: 20, status: 'SUBMITTED' })

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/systems/system-7/admin/access-requests?page=2&size=20&status=SUBMITTED')
  })

  it('uses dedicated optimistic organization relationship endpoints and preserves explicit clears', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      ok({ id: 'target-1', version: '8' }))
    vi.stubGlobal('fetch', fetchMock)

    await systemAdminApi.updateDepartmentLeader('system-7', 'department-3', {
      leaderMemberId: null,
      version: '6',
    })
    await systemAdminApi.updateMemberManager('system-7', 'member-9', {
      managerMemberId: null,
      version: '7',
    })

    expect(fetchMock.mock.calls[0]?.[0])
      .toBe('/api/v1/systems/system-7/admin/departments/department-3/leader')
    expect(fetchMock.mock.calls[1]?.[0])
      .toBe('/api/v1/systems/system-7/admin/members/member-9/manager')
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({
      leaderMemberId: null,
      version: '6',
    })
    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))).toEqual({
      managerMemberId: null,
      version: '7',
    })
  })

  it('uses context endpoints for tenant switching and self-service requests', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ id: 'request-8', version: '1' }))
    vi.stubGlobal('fetch', fetchMock)

    await contextAccessApi.submitRequest('system-7', { reason: 'project access' })
    await contextAccessApi.cancelRequest('request-8', '1')

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/context/systems/system-7/access-requests')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/context/access-requests/request-8:cancel')
  })
})
