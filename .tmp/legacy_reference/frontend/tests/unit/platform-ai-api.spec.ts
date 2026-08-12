import { beforeEach, describe, expect, it, vi } from 'vitest'

import { platformAiAdminApi, platformAiRuntimeApi } from '@/services/platformAi'

function envelope(data: unknown) {
  return new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('platform AI admin and runtime API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('uses only platform-scoped provider, policy and capability endpoints', async () => {
    const provider = {
      id: 'provider-1', code: 'platform-main', name: 'Platform model', baseUrl: 'https://ai.example/v1',
      model: 'model-a', secretRef: 'vault://ai/platform/v1', timeoutSeconds: 30, enabled: true, version: 2,
    }
    const policy = {
      draftVersion: 3, status: 'DRAFT', providerId: 'provider-1', providerVersion: 2,
      allowedOperations: ['AUTHORIZED_SYSTEMS_QUERY', 'PLATFORM_OPERATIONS_QUERY', 'PLATFORM_TASK_DRAFT', 'SYSTEM_SWITCH_GUIDANCE'], maxSystems: 50,
      dailyRequestQuota: 1000, dailyTokenQuota: 1000000, maxConcurrency: 4, strictRedaction: true,
      dataResidency: 'PLATFORM_METADATA_ONLY', promptVersion: 'platform-v1', enabled: true, activeVersionId: null,
    }
    const responses = [
      { items: [provider] }, provider, { ...provider, version: 3 }, policy, { ...policy, draftVersion: 4 },
      { status: 'PASSED', issues: [] }, { ...policy, draftVersion: 4, status: 'PUBLISHED', activeVersionId: 'policy-4' },
      { available: true, reason: null, policyVersion: 'policy-4' },
    ]
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope(responses.shift())))
    vi.stubGlobal('fetch', fetchMock)

    expect(await platformAiAdminApi.providers()).toEqual([provider])
    const providerInput = {
      code: 'platform-main', name: 'Platform model', baseUrl: 'https://ai.example/v1', model: 'model-a',
      secretRef: 'vault://ai/platform/v1', timeoutSeconds: 30, enabled: true,
    }
    await platformAiAdminApi.createProvider(providerInput)
    await platformAiAdminApi.updateProvider('provider/1', { ...providerInput, expectedVersion: 2 })
    await platformAiAdminApi.policy()
    await platformAiAdminApi.savePolicy({
      expectedVersion: 3, providerId: 'provider-1',
      allowedOperations: ['AUTHORIZED_SYSTEMS_QUERY', 'PLATFORM_OPERATIONS_QUERY', 'PLATFORM_TASK_DRAFT', 'SYSTEM_SWITCH_GUIDANCE'], maxSystems: 50,
      dailyRequestQuota: 1000, dailyTokenQuota: 1000000, maxConcurrency: 4, strictRedaction: true,
      dataResidency: 'PLATFORM_METADATA_ONLY', promptVersion: 'platform-v1', enabled: true,
    })
    await platformAiAdminApi.checkPolicy()
    await platformAiAdminApi.publishPolicy(4)
    await platformAiAdminApi.capability()

    const root = '/api/v1/platform/admin/ai'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      `${root}/providers`, `${root}/providers`, `${root}/providers/provider%2F1`, `${root}/policy`,
      `${root}/policy`, `${root}/policy:check`, `${root}/policy:publish`, `${root}/capability`,
    ])
    expect(JSON.parse(String((fetchMock.mock.calls[4]![1] as RequestInit).body))).toEqual({
      expectedVersion: 3, providerId: 'provider-1',
      allowedOperations: ['AUTHORIZED_SYSTEMS_QUERY', 'PLATFORM_OPERATIONS_QUERY', 'PLATFORM_TASK_DRAFT', 'SYSTEM_SWITCH_GUIDANCE'], maxSystems: 50,
      dailyRequestQuota: 1000, dailyTokenQuota: 1000000, maxConcurrency: 4, strictRedaction: true,
      dataResidency: 'PLATFORM_METADATA_ONLY', promptVersion: 'platform-v1', enabled: true,
    })
    const publishRequest = fetchMock.mock.calls[6]![1] as RequestInit
    expect(JSON.parse(String(publishRequest.body))).toEqual({ expectedVersion: 4 })
    expect((publishRequest.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(fetchMock.mock.calls.every(call => !String(call[0]).includes('/systems/'))).toBe(true)
  })

  it('uses platform session endpoints and never exposes system confirmation endpoints', async () => {
    const session = { id: 'session-1', title: '系统授权', status: 'ACTIVE', createdAt: '2026-08-04T00:00:00Z', updatedAt: '2026-08-04T00:00:00Z' }
    const turn = {
      id: 'turn-1', status: 'SUCCEEDED', operation: 'PLATFORM_OPERATIONS_QUERY', answer: 'AI 配额只读回读。',
      errorCode: null, retryable: false, systems: [], guidance: null, proposal: null,
      operations: {
        queryKind: 'AI_QUOTA', confidence: 0.99, clarification: null, personalTasks: [], serviceHealth: null,
        quota: {
          policyVersion: 'policy-4', periodStart: '2026-08-04T00:00:00Z', periodEnd: '2026-08-05T00:00:00Z',
          requestLimit: 1000, requestCount: 125, remainingRequests: 875,
          tokenLimit: 1000000, usedTokens: 120000, reservedTokens: 5000, remainingTokens: 875000,
          concurrencyLimit: 4, runningCount: 1, remainingConcurrency: 3,
        },
        agentActivity: [],
      },
      requestId: 'request-1', traceId: 'trace-1',
    }
    const storedTurn = {
      id: 'turn-stored', status: 'SUCCEEDED', operation: 'AUTHORIZED_SYSTEMS_QUERY',
      responseSummary: '返回 1 个授权系统。', errorCode: null, retryable: false, returnedSystems: 1,
      evidence: { type: 'AUTHORIZED_SYSTEMS', systems: [{
        systemId: 'system-1', systemCode: 'CRM', systemName: '客户系统', status: 'ACTIVE',
        membershipState: 'ACTIVE', accessState: 'AUTHORIZED', switchTarget: '/api/v1/context/systems/system-1:switch',
      }], guidance: null },
      proposal: null, operations: null,
      createdAt: '2026-08-04T00:00:00Z', finishedAt: '2026-08-04T00:00:01Z',
    }
    const responses = [
      { available: true, reason: null, policyVersion: 'policy-4' },
      { rows: [session], page: 2, size: 10, hasMore: false }, session,
      { session, messages: [], turns: [storedTurn] }, turn,
    ]
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope(responses.shift())))
    vi.stubGlobal('fetch', fetchMock)

    await platformAiRuntimeApi.capability()
    expect(await platformAiRuntimeApi.sessions(2, 10)).toEqual({ rows: [session], page: 2, size: 10, hasMore: false })
    await platformAiRuntimeApi.createSession({ title: '系统授权' })
    expect(await platformAiRuntimeApi.session('session/1')).toMatchObject({
      turns: [{
        id: 'turn-stored', responseSummary: '返回 1 个授权系统。', returnedSystems: 1,
        evidence: {
          type: 'AUTHORIZED_SYSTEMS',
          systems: [{ systemCode: 'CRM', switchTarget: '/api/v1/context/systems/system-1:switch' }],
          guidance: null,
        },
      }],
    })
    const messageResult = await platformAiRuntimeApi.submitMessage('session/1', { content: '我的 AI 配额还剩多少？' })
    expect(messageResult.operations?.queryKind).toBe('AI_QUOTA')
    expect(messageResult.operations?.quota?.remainingRequests).toBe(875)

    const root = '/api/v1/platform/ai/sessions'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/platform/ai/capability', `${root}?page=2&size=10`, root,
      `${root}/session%2F1`, `${root}/session%2F1/messages`,
    ])
    expect(fetchMock.mock.calls.map(call => String(call[0])).join(' ')).not.toContain('confirmations')
    expect(((fetchMock.mock.calls[4]![1] as RequestInit).headers as Headers).get('Idempotency-Key')).toBeTruthy()
  })

  it('reads, confirms and rejects a revisioned platform task proposal under its session', async () => {
    const proposal = {
      id: 'proposal/1', state: 'PENDING', revision: 3,
      preview: {
        title: '跟进平台授权', description: '确认账号权限', dueAt: '2026-08-05T02:00:00Z',
        priority: 'HIGH', selfAssigned: true,
      },
      confidence: 0.94, clarification: null, expiresAt: '2026-08-04T02:00:00Z',
      result: null, errorCode: null, requestId: 'request-proposal', traceId: 'trace-proposal',
    }
    const responses = [proposal, { ...proposal, state: 'SUCCEEDED', revision: 4 }, {
      ...proposal, state: 'REJECTED', revision: 4,
    }]
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope(responses.shift())))
    vi.stubGlobal('fetch', fetchMock)

    await platformAiRuntimeApi.taskProposal('session/1', 'proposal/1')
    await platformAiRuntimeApi.confirmTaskProposal(
      'session/1', 'proposal/1', { expectedRevision: 3 }, 'platform-task-key-1',
    )
    await platformAiRuntimeApi.rejectTaskProposal('session/1', 'proposal/1', { expectedRevision: 3 })

    const root = '/api/v1/platform/ai/sessions/session%2F1/proposals/proposal%2F1'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      root, `${root}/confirm`, `${root}/reject`,
    ])
    const confirmRequest = fetchMock.mock.calls[1]![1] as RequestInit
    const rejectRequest = fetchMock.mock.calls[2]![1] as RequestInit
    expect(confirmRequest.method).toBe('POST')
    expect(JSON.parse(String(confirmRequest.body))).toEqual({ expectedRevision: 3 })
    expect((confirmRequest.headers as Headers).get('Idempotency-Key')).toBe('platform-task-key-1')
    expect(rejectRequest.method).toBe('POST')
    expect(JSON.parse(String(rejectRequest.body))).toEqual({ expectedRevision: 3 })
    expect((rejectRequest.headers as Headers).has('Idempotency-Key')).toBe(false)
  })
})
