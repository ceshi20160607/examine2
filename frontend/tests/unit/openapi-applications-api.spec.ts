import { beforeEach, describe, expect, it, vi } from 'vitest'

import { openApiApplicationApi } from '@/services/openapi'

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

describe('OpenAPI application administration API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('maps list and detail reads to the frozen system administration family', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      items: [],
      page: 1,
      size: 100,
      total: 0,
    }))
    vi.stubGlobal('fetch', fetchMock)

    await openApiApplicationApi.list('system 7', 2, 50)
    await openApiApplicationApi.detail('system 7', 'application/8')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/system%207/admin/openapi/applications?page=2&size=50',
      '/api/v1/systems/system%207/admin/openapi/applications/application%2F8',
    ])
    for (const call of fetchMock.mock.calls) {
      expect(((call[1] as RequestInit).headers as Headers).get('Idempotency-Key')).toBeNull()
    }
  })

  it('encodes application-scoped call-log filters and paging exactly', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      items: [],
      page: 1,
      size: 20,
      total: 0,
    }))
    vi.stubGlobal('fetch', fetchMock)

    await openApiApplicationApi.callLogs('system 7', 'application/8')
    await openApiApplicationApi.callLogs('system 7', 'application/8', {
      resultCategory: 'SIGNATURE_REJECTED',
      requestMethod: 'PATCH',
      page: 9,
      size: 50,
    })

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/system%207/admin/openapi/applications/application%2F8/call-logs?resultCategory=ALL&requestMethod=ALL&page=1&size=20',
      '/api/v1/systems/system%207/admin/openapi/applications/application%2F8/call-logs?resultCategory=SIGNATURE_REJECTED&requestMethod=PATCH&page=9&size=50',
    ])
    for (const call of fetchMock.mock.calls) {
      const request = call[1] as RequestInit
      expect(request.method).toBeUndefined()
      expect(request.body).toBeUndefined()
      expect((request.headers as Headers).get('Idempotency-Key')).toBeNull()
    }
  })

  it('sends the exact create, policy and SecretRef rotation contracts with caller keys', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      id: 'application-8',
    }))
    vi.stubGlobal('fetch', fetchMock)

    await openApiApplicationApi.create('10', {
      name: 'ERP connector',
      tenantId: '20',
      serviceMemberId: '30',
      secretRef: 'vault://openapi/erp/v1',
      scopes: ['ping'],
      ipAllowlist: ['10.0.0.0/24'],
      rateLimitPerMinute: 120,
    }, 'create-key')
    await openApiApplicationApi.updatePolicy('10', 'application-8', {
      scopes: ['ping', 'flow.start'],
      ipAllowlist: ['10.0.0.8'],
      rateLimitPerMinute: 60,
      version: 4,
    }, 'policy-key')
    await openApiApplicationApi.rotateSecretRef('10', 'application-8', {
      secretRef: 'vault://openapi/erp/v2',
      version: 5,
    }, 'rotate-key')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/admin/openapi/applications',
      '/api/v1/systems/10/admin/openapi/applications/application-8/policy',
      '/api/v1/systems/10/admin/openapi/applications/application-8:rotate-secret-ref',
    ])
    expect(fetchMock.mock.calls.map((call) => (call[1] as RequestInit).method))
      .toEqual(['POST', 'PUT', 'POST'])
    expect(fetchMock.mock.calls.map((call) =>
      ((call[1] as RequestInit).headers as Headers).get('Idempotency-Key')))
      .toEqual(['create-key', 'policy-key', 'rotate-key'])
    expect(JSON.parse(String((fetchMock.mock.calls[0]![1] as RequestInit).body))).toEqual({
      name: 'ERP connector',
      tenantId: '20',
      serviceMemberId: '30',
      secretRef: 'vault://openapi/erp/v1',
      scopes: ['ping'],
      ipAllowlist: ['10.0.0.0/24'],
      rateLimitPerMinute: 120,
    })
    expect(JSON.parse(String((fetchMock.mock.calls[1]![1] as RequestInit).body))).toEqual({
      scopes: ['ping', 'flow.start'],
      ipAllowlist: ['10.0.0.8'],
      rateLimitPerMinute: 60,
      version: 4,
    })
    expect(JSON.parse(String((fetchMock.mock.calls[2]![1] as RequestInit).body))).toEqual({
      secretRef: 'vault://openapi/erp/v2',
      version: 5,
    })
  })

  it('maps enable and disable with optimistic versions, reasons and caller keys', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      id: 'application-8',
    }))
    vi.stubGlobal('fetch', fetchMock)

    await openApiApplicationApi.changeStatus(
      '10',
      'application-8',
      'enable',
      { version: 6, reason: 'provider restored' },
      'enable-key',
    )
    await openApiApplicationApi.changeStatus(
      '10',
      'application-8',
      'disable',
      { version: 7, reason: 'incident response' },
      'disable-key',
    )

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/admin/openapi/applications/application-8:enable',
      '/api/v1/systems/10/admin/openapi/applications/application-8:disable',
    ])
    expect(fetchMock.mock.calls.map((call) =>
      ((call[1] as RequestInit).headers as Headers).get('Idempotency-Key')))
      .toEqual(['enable-key', 'disable-key'])
    expect(fetchMock.mock.calls.map((call) =>
      JSON.parse(String((call[1] as RequestInit).body))))
      .toEqual([
        { version: 6, reason: 'provider restored' },
        { version: 7, reason: 'incident response' },
      ])
  })
})
