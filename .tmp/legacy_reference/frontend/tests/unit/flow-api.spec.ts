import { beforeEach, describe, expect, it, vi } from 'vitest'

import { flowApi } from '@/services/flow'

function ok(data: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status, headers: { 'Content-Type': 'application/json' } }))
}

describe('flow API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('loads flow-authorized single-value record MEMBER fields by module', async () => {
    const catalog = {
      items: [{
        sourceId: '501',
        moduleCode: 'purchase_order',
        fieldCode: 'owner',
        fieldName: '负责人',
      }],
    }
    const fetchMock = vi.fn(() => ok(catalog))
    vi.stubGlobal('fetch', fetchMock)

    await expect(flowApi.listRecordMemberFields('10', 'purchase order'))
      .resolves.toEqual(catalog)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/systems/10/flow/approver-sources/record-member-fields?moduleCode=purchase+order',
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('passes explicit ordered approval stages without reshaping their sources', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      ok({ definitionId: '20' }))
    vi.stubGlobal('fetch', fetchMock)
    const approvalStages = [
      {
        code: 'review',
        name: 'Review',
        approverIds: ['30'],
        approvalMode: 'SEQUENTIAL' as const,
        approverSource: { kind: 'FIXED' as const, sourceId: null },
      },
      {
        code: 'confirm',
        name: 'Confirm',
        approverIds: [],
        approvalMode: 'ANY' as const,
        approverSource: { kind: 'PREVIOUS_HANDLER' as const },
      },
    ]

    await flowApi.createDefinition('10', {
      name: 'Two stages',
      approverIds: ['30'],
      approvalStages,
    })

    expect(JSON.parse(String((fetchMock.mock.calls[0]![1] as RequestInit).body)))
      .toEqual({
        name: 'Two stages',
        approverIds: ['30'],
        approvalStages,
      })
  })

  it('passes branch-local approval stages through every gateway shape', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      ok({ definitionId: '20' }))
    vi.stubGlobal('fetch', fetchMock)
    const approvalStages = [{
      code: 'review',
      name: 'Review',
      approverIds: [],
      approvalMode: 'SEQUENTIAL' as const,
      approverSource: { kind: 'REQUESTER' as const },
    }]
    const branch = {
      code: 'branch_1',
      name: 'Branch 1',
      approverIds: [],
      approvalMode: 'SEQUENTIAL' as const,
      approverSource: { kind: 'REQUESTER' as const },
      approvalStages,
    }

    await flowApi.createDefinition('10', {
      name: 'Branch stages',
      approverIds: ['30'],
      parallelGateway: { branches: [branch, { ...branch, code: 'branch_2', name: 'Branch 2' }] },
    })

    expect(JSON.parse(String((fetchMock.mock.calls[0]![1] as RequestInit).body)))
      .toEqual(expect.objectContaining({
        parallelGateway: {
          branches: [
            expect.objectContaining({ approvalStages }),
            expect.objectContaining({ approvalStages }),
          ],
        },
      }))
  })

  it('maps the definition draft and publication lifecycle', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ definitionId: '20' }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.listDefinitions('10', 2, 25)
    await flowApi.createDefinition('10', { name: 'Contract approval', approverId: '30' })
    await flowApi.reviseDefinition('10', '20', {
      name: 'Contract approval v2',
      approverIds: ['31', '32'],
    })
    await flowApi.publishDefinition('10', '20')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/flow/definitions?page=2&size=25',
      '/api/v1/systems/10/flow/definitions',
      '/api/v1/systems/10/flow/definitions/20/draft',
      '/api/v1/systems/10/flow/definitions/20:publish',
    ])
    expect(fetchMock.mock.calls.map((call) => (call[1] as RequestInit).method)).toEqual([
      undefined, 'POST', 'PUT', 'POST',
    ])
    expect(JSON.parse((fetchMock.mock.calls[1]?.[1] as RequestInit).body as string)).toEqual({
      name: 'Contract approval',
      approverId: '30',
    })
    expect(JSON.parse((fetchMock.mock.calls[2]?.[1] as RequestInit).body as string)).toEqual({
      name: 'Contract approval v2',
      approverIds: ['31', '32'],
    })
  })

  it('lists the current tenant published definitions available to starters', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      items: [],
      page: 2,
      size: 50,
      total: 0,
    }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.listStartableDefinitions('10', 2, 50)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/systems/10/flow/startable-definitions?page=2&size=50',
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('maps immutable version history and draft restore endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      definitionId: '20',
    }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.listDefinitionVersions('10', '20', 2, 5)
    await flowApi.restoreDefinitionVersion('10', '20', 1)

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/flow/definitions/20/versions?page=2&size=5',
      '/api/v1/systems/10/flow/definitions/20/versions/1:restore',
    ])
    expect((fetchMock.mock.calls[0]![1] as RequestInit).method).toBeUndefined()
    const restore = fetchMock.mock.calls[1]![1] as RequestInit
    expect(restore.method).toBe('POST')
    expect((restore.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    expect((restore.headers as Headers).get('Idempotency-Key')).toBeTruthy()
  })

  it('maps versioned decision comment template CRUD and lifecycle endpoints', async () => {
    const template = {
      templateId: '801',
      name: 'Approved',
      version: 1,
      body: 'Reviewed and approved',
      status: 'ACTIVE',
      createdAt: '2026-07-31T01:00:00Z',
      updatedAt: '2026-07-31T01:00:00Z',
    }
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok(template))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.listDecisionCommentTemplates('10', 'ACTIVE', 2, 20)
    await flowApi.createDecisionCommentTemplate('10', {
      name: template.name,
      body: template.body,
    })
    await flowApi.updateDecisionCommentTemplate('10', '801', {
      name: 'Approved v2',
      body: 'Reviewed twice and approved',
    })
    await flowApi.activateDecisionCommentTemplate('10', '801')
    await flowApi.deactivateDecisionCommentTemplate('10', '801')

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/flow/decision-comment-templates?status=ACTIVE&page=2&size=20',
      '/api/v1/systems/10/flow/decision-comment-templates',
      '/api/v1/systems/10/flow/decision-comment-templates/801',
      '/api/v1/systems/10/flow/decision-comment-templates/801:activate',
      '/api/v1/systems/10/flow/decision-comment-templates/801:deactivate',
    ])
    expect(fetchMock.mock.calls.map(call => (call[1] as RequestInit).method)).toEqual([
      undefined,
      'POST',
      'PUT',
      'POST',
      'POST',
    ])
    expect(JSON.parse(String((fetchMock.mock.calls[1]![1] as RequestInit).body))).toEqual({
      name: 'Approved',
      body: 'Reviewed and approved',
    })
    expect(JSON.parse(String((fetchMock.mock.calls[2]![1] as RequestInit).body))).toEqual({
      name: 'Approved v2',
      body: 'Reviewed twice and approved',
    })
  })

  it('passes typed and file evidence fields and omits free text when a template is selected', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      ok({ instanceId: '101' }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.approve('10', '101', '', undefined, {
      attachmentFileIds: [901, 902],
      typedSignature: 'Approver',
      commentTemplateId: 801,
    })
    await flowApi.rejectBranch('10', '101', 'finance', '', '200', {
      signatureFileId: 903,
      commentTemplateId: 802,
    })

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/flow/instances/101:approve',
      '/api/v1/systems/10/flow/instances/101/branches/finance:reject',
    ])
    expect(JSON.parse(String((fetchMock.mock.calls[0]![1] as RequestInit).body))).toEqual({
      attachmentFileIds: [901, 902],
      typedSignature: 'Approver',
      commentTemplateId: 801,
    })
    expect(JSON.parse(String((fetchMock.mock.calls[1]![1] as RequestInit).body))).toEqual({
      representedMemberId: '200',
      signatureFileId: 903,
      commentTemplateId: 802,
    })
  })

  it('maps the external-task lease lifecycle and administrator retry endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      ok({ executionId: '9901' }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.listExternalTasks('10', 'contract.archive', 2, 25)
    await flowApi.claimExternalTask('10', '9901')
    await flowApi.heartbeatExternalTask('10', '9901', 'lease-token')
    await flowApi.completeExternalTask('10', '9901', {
      leaseToken: 'lease-token',
      result: { archived: true },
    })
    await flowApi.failExternalTask('10', '9901', {
      leaseToken: 'lease-token',
      code: 'ARCHIVE_FAILED',
      message: 'archive service rejected the request',
    })
    await flowApi.retryCompletionExecution('10', '101', '9901')
    await flowApi.retryCompensationExecution('10', '101', '9951')

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/flow/external-tasks?topic=contract.archive&page=2&size=25',
      '/api/v1/systems/10/flow/external-tasks/9901:claim',
      '/api/v1/systems/10/flow/external-tasks/9901:heartbeat',
      '/api/v1/systems/10/flow/external-tasks/9901:complete',
      '/api/v1/systems/10/flow/external-tasks/9901:fail',
      '/api/v1/systems/10/flow/instances/101/completion-executions/9901:retry',
      '/api/v1/systems/10/flow/instances/101/compensation-executions/9951:retry',
    ])
    expect(fetchMock.mock.calls.map(call => (call[1] as RequestInit).method)).toEqual([
      undefined,
      'POST',
      'POST',
      'POST',
      'POST',
      'POST',
      'POST',
    ])
    expect((fetchMock.mock.calls[1]![1] as RequestInit).body).toBeUndefined()
    expect((fetchMock.mock.calls[5]![1] as RequestInit).body).toBeUndefined()
    expect((fetchMock.mock.calls[6]![1] as RequestInit).body).toBeUndefined()
    expect(JSON.parse(String((fetchMock.mock.calls[2]![1] as RequestInit).body))).toEqual({
      leaseToken: 'lease-token',
    })
    expect(JSON.parse(String((fetchMock.mock.calls[3]![1] as RequestInit).body))).toEqual({
      leaseToken: 'lease-token',
      result: { archived: true },
    })
    expect(JSON.parse(String((fetchMock.mock.calls[4]![1] as RequestInit).body))).toEqual({
      leaseToken: 'lease-token',
      code: 'ARCHIVE_FAILED',
      message: 'archive service rejected the request',
    })
    for (const call of fetchMock.mock.calls.slice(1)) {
      const request = call[1] as RequestInit
      expect((request.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    }
  })

  it('maps draft check and side-effect-free simulation endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      definitionId: '20',
    }))
    vi.stubGlobal('fetch', fetchMock)
    const simulation = {
      requesterId: '30',
      businessKey: 'PO-2026-0099',
      trigger: {
        moduleCode: 'purchase_order',
        event: 'RECORD_ACTIVATED' as const,
        values: { amount: 150 },
      },
    }

    await flowApi.checkDefinitionDraft('10', '20')
    await flowApi.simulateDefinitionDraft('10', '20', simulation)

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/flow/definitions/20/draft:check',
      '/api/v1/systems/10/flow/definitions/20/draft:simulate',
    ])
    for (const call of fetchMock.mock.calls) {
      const request = call[1] as RequestInit
      expect(request.method).toBe('POST')
      expect((request.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
      expect((request.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    }
    expect(JSON.parse(String((fetchMock.mock.calls[1]![1] as RequestInit).body)))
      .toEqual(simulation)
  })

  it('maps activation status mapping and a non-activation trigger event without translation', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ definitionId: '20' }))
    vi.stubGlobal('fetch', fetchMock)

    const triggerBinding = {
      moduleCode: 'purchase_order',
      event: 'RECORD_ACTIVATED' as const,
      priority: 200,
      exclusive: false,
      conditions: [
        { fieldCode: 'amount', operator: 'GTE' as const, value: 100 },
        { fieldCode: 'urgent', operator: 'EQ' as const, value: true },
        { fieldCode: 'remark', operator: 'NOT_EMPTY' as const },
      ],
    }
    const recordStatusMapping = {
      fieldCode: 'approval_status',
      approvedValue: '101',
      rejectedValue: '102',
      withdrawnValue: '103',
      terminatedValue: '104',
    }
    const revisedTriggerBinding = {
      ...triggerBinding,
      event: 'RECORD_STATUS_CHANGED' as const,
      priority: -1000,
    }
    await flowApi.createDefinition('10', {
      name: 'Purchase approval',
      approverIds: ['30', '31'],
      triggerBinding,
      recordStatusMapping,
    })
    await flowApi.reviseDefinition('10', '20', {
      name: 'Purchase approval v2',
      approverIds: ['31', '32'],
      triggerBinding: revisedTriggerBinding,
    })

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/flow/definitions',
      '/api/v1/systems/10/flow/definitions/20/draft',
    ])
    const create = fetchMock.mock.calls[0]![1] as RequestInit
    const revise = fetchMock.mock.calls[1]![1] as RequestInit
    for (const request of [create, revise]) {
      expect((request.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
      expect((request.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    }
    expect(JSON.parse(String(create.body))).toEqual({
      name: 'Purchase approval',
      approverIds: ['30', '31'],
      triggerBinding,
      recordStatusMapping,
    })
    expect(JSON.parse(String(revise.body))).toEqual({
      name: 'Purchase approval v2',
      approverIds: ['31', '32'],
      triggerBinding: revisedTriggerBinding,
    })
  })

  it('passes periodic configuration and reads its durable runtime state', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      definitionId: '20',
      status: 'ACTIVE',
    }))
    vi.stubGlobal('fetch', fetchMock)
    const triggerBinding = {
      moduleCode: null,
      event: 'PERIODIC' as const,
      priority: 0,
      exclusive: true,
      conditions: [],
      startAt: '2026-07-30T01:00:00.000Z',
      intervalMinutes: 60,
    }

    await flowApi.createDefinition('10', {
      name: 'Hourly approval',
      approverIds: ['30'],
      triggerBinding,
    })
    await flowApi.periodicSchedule('10', '20')

    expect(JSON.parse(String((fetchMock.mock.calls[0]![1] as RequestInit).body))).toEqual({
      name: 'Hourly approval',
      approverIds: ['30'],
      triggerBinding,
    })
    expect(fetchMock.mock.calls[1]![0]).toBe(
      '/api/v1/systems/10/flow/definitions/20/periodic-schedule',
    )
    expect((fetchMock.mock.calls[1]![1] as RequestInit).method).toBeUndefined()
  })

  it('maps start, decision, requester withdrawal, operator termination and history endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ instanceId: '40' }, 201))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.startInstance('10', '20', { businessKey: 'record:50' }, 'start-key-1')
    await flowApi.listInstances('10', 3, 10)
    await flowApi.listApprovalTasks('10')
    await flowApi.listApprovalTasks('10', { status: 'COMPLETED', page: 2, size: 25 })
    await flowApi.instance('10', '40')
    await flowApi.approve('10', '40', 'approved')
    await flowApi.reject('10', '41', 'needs changes')
    await flowApi.withdraw('10', '42', { reason: 'request changed' })
    await flowApi.terminate('10', '43', { reason: 'duplicate request' })
    await flowApi.history('10', '40')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/flow/definitions/20/instances',
      '/api/v1/systems/10/flow/instances?page=3&size=10',
      '/api/v1/systems/10/flow/tasks?status=PENDING&page=1&size=20',
      '/api/v1/systems/10/flow/tasks?status=COMPLETED&page=2&size=25',
      '/api/v1/systems/10/flow/instances/40',
      '/api/v1/systems/10/flow/instances/40:approve',
      '/api/v1/systems/10/flow/instances/41:reject',
      '/api/v1/systems/10/flow/instances/42:withdraw',
      '/api/v1/systems/10/flow/instances/43:terminate',
      '/api/v1/systems/10/flow/instances/40/history',
    ])
    const start = fetchMock.mock.calls[0]![1] as RequestInit
    expect((start.headers as Headers).get('Idempotency-Key')).toBe('start-key-1')

    const withdrawal = fetchMock.mock.calls[7]![1] as RequestInit
    const headers = withdrawal.headers as Headers
    expect(withdrawal.method).toBe('POST')
    expect(headers.get('X-CSRF-Token')).toBe('csrf-token')
    expect(headers.get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(withdrawal.body))).toEqual({ reason: 'request changed' })

    const termination = fetchMock.mock.calls[8]![1] as RequestInit
    const terminationHeaders = termination.headers as Headers
    expect(termination.method).toBe('POST')
    expect(terminationHeaders.get('X-CSRF-Token')).toBe('csrf-token')
    expect(terminationHeaders.get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(termination.body))).toEqual({ reason: 'duplicate request' })
  })

  it('maps supported instance-status and UTC-date drill filters', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      items: [], page: 2, size: 50, total: 0,
    }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.listInstances('10', {
      status: 'REJECTED',
      from: '2026-07-26',
      to: '2026-08-02',
      page: 2,
      size: 50,
    })

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/v1/systems/10/flow/instances?page=2&size=50'
      + '&status=REJECTED&from=2026-07-26&to=2026-08-02',
    )
  })

  it('targets explicit parallel branch decision endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      instanceId: '40',
      status: 'PENDING',
    }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.approveBranch('10', '40', 'finance', 'accepted')
    await flowApi.rejectBranch('10', '40', 'owner', 'risk rejected')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/flow/instances/40/branches/finance:approve',
      '/api/v1/systems/10/flow/instances/40/branches/owner:reject',
    ])
    expect(JSON.parse(String((fetchMock.mock.calls[0]![1] as RequestInit).body)))
      .toEqual({ comment: 'accepted' })
    expect(JSON.parse(String((fetchMock.mock.calls[1]![1] as RequestInit).body)))
      .toEqual({ reason: 'risk rejected' })
  })

  it('maps outgoing delegation lifecycle and represented decisions', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      delegationRuleId: '70',
    }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.listDelegations('10', '30', 2, 25)
    await flowApi.createDelegation('10', {
      delegateMemberId: '31',
      startsAt: '2026-08-01T00:00:00Z',
      endsAt: '2026-08-02T00:00:00Z',
      definitionId: '20',
    })
    await flowApi.revokeDelegation('10', '70/71')
    await flowApi.approve('10', '40', 'accepted', '30')
    await flowApi.rejectBranch('10', '41', 'finance', 'risk', '30')

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/flow/delegations?delegatorMemberId=30&page=2&size=25',
      '/api/v1/systems/10/flow/delegations',
      '/api/v1/systems/10/flow/delegations/70%2F71/revoke',
      '/api/v1/systems/10/flow/instances/40:approve',
      '/api/v1/systems/10/flow/instances/41/branches/finance:reject',
    ])
    expect(JSON.parse(String((fetchMock.mock.calls[1]![1] as RequestInit).body))).toEqual({
      delegateMemberId: '31',
      startsAt: '2026-08-01T00:00:00Z',
      endsAt: '2026-08-02T00:00:00Z',
      definitionId: '20',
    })
    expect(JSON.parse(String((fetchMock.mock.calls[3]![1] as RequestInit).body))).toEqual({
      comment: 'accepted',
      representedMemberId: '30',
    })
    expect(JSON.parse(String((fetchMock.mock.calls[4]![1] as RequestInit).body))).toEqual({
      reason: 'risk',
      representedMemberId: '30',
    })
    for (const index of [1, 2, 3, 4]) {
      const request = fetchMock.mock.calls[index]![1] as RequestInit
      expect(request.method).toBe('POST')
      expect((request.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    }
  })

  it('maps optional record binding on start and keeps unbound starts unchanged', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ instanceId: '40' }, 201))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.startInstance('10', '20', {
      definitionVersion: 2,
      businessKey: 'PO-2026-0008',
      recordBinding: {
        moduleCode: 'purchase_order',
        recordId: '9001',
      },
    }, 'start-key-bound')
    await flowApi.startInstance('10', '20', { businessKey: 'standalone:1' }, 'start-key-unbound')

    const bound = fetchMock.mock.calls[0]![1] as RequestInit
    const unbound = fetchMock.mock.calls[1]![1] as RequestInit
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/flow/definitions/20/instances',
      '/api/v1/systems/10/flow/definitions/20/instances',
    ])
    expect(bound.method).toBe('POST')
    expect((bound.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    expect((bound.headers as Headers).get('Idempotency-Key')).toBe('start-key-bound')
    expect(JSON.parse(String(bound.body))).toEqual({
      definitionVersion: 2,
      businessKey: 'PO-2026-0008',
      recordBinding: {
        moduleCode: 'purchase_order',
        recordId: '9001',
      },
    })
    expect(JSON.parse(String(unbound.body))).toEqual({ businessKey: 'standalone:1' })
    expect((unbound.headers as Headers).get('Idempotency-Key')).toBe('start-key-unbound')
  })

  it('uses a fresh idempotency key for each withdrawal attempt', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ instanceId: '40' }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.withdraw('10', '40', { reason: 'first reason' })
    await flowApi.withdraw('10', '40', { reason: 'second reason' })

    const firstHeaders = (fetchMock.mock.calls[0]![1] as RequestInit).headers as Headers
    const secondHeaders = (fetchMock.mock.calls[1]![1] as RequestInit).headers as Headers
    expect(firstHeaders.get('Idempotency-Key')).toBeTruthy()
    expect(secondHeaders.get('Idempotency-Key')).toBeTruthy()
    expect(secondHeaders.get('Idempotency-Key')).not.toBe(firstHeaders.get('Idempotency-Key'))
  })

  it('uses a fresh idempotency key for each termination attempt', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ instanceId: '40' }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.terminate('10', '40', { reason: 'first reason' })
    await flowApi.terminate('10', '40', { reason: 'second reason' })

    const firstHeaders = (fetchMock.mock.calls[0]![1] as RequestInit).headers as Headers
    const secondHeaders = (fetchMock.mock.calls[1]![1] as RequestInit).headers as Headers
    expect(firstHeaders.get('Idempotency-Key')).toBeTruthy()
    expect(secondHeaders.get('Idempotency-Key')).toBeTruthy()
    expect(secondHeaders.get('Idempotency-Key')).not.toBe(firstHeaders.get('Idempotency-Key'))
  })

  it('maps urge and comment list and append endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ items: [] }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.listUrges('10', '40', 2, 50)
    await flowApi.urge('10', '40', { message: 'Please review' })
    await flowApi.listComments('10', '40', 3, 25)
    await flowApi.createComment('10', '40', { body: 'Documents checked' })

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/flow/instances/40/urges?page=2&size=50',
      '/api/v1/systems/10/flow/instances/40:urge',
      '/api/v1/systems/10/flow/instances/40/comments?page=3&size=25',
      '/api/v1/systems/10/flow/instances/40/comments',
    ])

    const urge = fetchMock.mock.calls[1]![1] as RequestInit
    const comment = fetchMock.mock.calls[3]![1] as RequestInit
    expect(urge.method).toBe('POST')
    expect((urge.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    expect((urge.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(urge.body))).toEqual({ message: 'Please review' })
    expect(comment.method).toBe('POST')
    expect((comment.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    expect((comment.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(comment.body))).toEqual({ body: 'Documents checked' })
  })

  it('uses fresh idempotency keys for every urge and comment attempt', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({}))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.urge('10', '40', { message: 'first' })
    await flowApi.urge('10', '40', { message: 'second' })
    await flowApi.createComment('10', '40', { body: 'first' })
    await flowApi.createComment('10', '40', { body: 'second' })

    const keys = fetchMock.mock.calls.map((call) =>
      ((call[1] as RequestInit).headers as Headers).get('Idempotency-Key'))
    expect(keys.every(Boolean)).toBe(true)
    expect(new Set(keys).size).toBe(4)
  })

  it('maps transfer and add-sign assignment endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ instanceId: '40' }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.transfer('10', '40', {
      targetMemberId: '300',
      reason: 'Route to owner',
    })
    await flowApi.addSign('10', '40', {
      targetMemberId: '301',
      position: 'BEFORE',
      reason: 'Security review first',
    })

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/flow/instances/40:transfer',
      '/api/v1/systems/10/flow/instances/40:add-sign',
    ])
    const transfer = fetchMock.mock.calls[0]![1] as RequestInit
    const addSign = fetchMock.mock.calls[1]![1] as RequestInit
    expect(transfer.method).toBe('POST')
    expect((transfer.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    expect((transfer.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(transfer.body))).toEqual({
      targetMemberId: '300',
      reason: 'Route to owner',
    })
    expect(addSign.method).toBe('POST')
    expect((addSign.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    expect((addSign.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(addSign.body))).toEqual({
      targetMemberId: '301',
      position: 'BEFORE',
      reason: 'Security review first',
    })
  })

  it('uses fresh idempotency keys for every transfer and add-sign attempt', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ instanceId: '40' }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.transfer('10', '40', { targetMemberId: '300', reason: 'first' })
    await flowApi.transfer('10', '40', { targetMemberId: '301', reason: 'second' })
    await flowApi.addSign('10', '40', {
      targetMemberId: '302',
      position: 'BEFORE',
      reason: 'third',
    })
    await flowApi.addSign('10', '40', {
      targetMemberId: '303',
      position: 'AFTER',
      reason: 'fourth',
    })

    const keys = fetchMock.mock.calls.map((call) =>
      ((call[1] as RequestInit).headers as Headers).get('Idempotency-Key'))
    expect(keys.every(Boolean)).toBe(true)
    expect(new Set(keys).size).toBe(4)
  })

  it('maps claimable tasks and return, cancel-claim and claim mutations', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ instanceId: '40' }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.listClaimableTasks('10', 2, 25)
    await flowApi.returnInstance('10', '40', { reason: 'Correct previous data' })
    await flowApi.cancelClaim('10', '40', { reason: 'Release to pool' })
    await flowApi.claim('10', '40', { comment: 'I will review' })

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/flow/claimable-tasks?page=2&size=25',
      '/api/v1/systems/10/flow/instances/40:return',
      '/api/v1/systems/10/flow/instances/40:cancel-claim',
      '/api/v1/systems/10/flow/instances/40:claim',
    ])
    expect((fetchMock.mock.calls[0]![1] as RequestInit).method).toBeUndefined()
    const returnRequest = fetchMock.mock.calls[1]![1] as RequestInit
    const cancelRequest = fetchMock.mock.calls[2]![1] as RequestInit
    const claimRequest = fetchMock.mock.calls[3]![1] as RequestInit
    for (const request of [returnRequest, cancelRequest, claimRequest]) {
      expect(request.method).toBe('POST')
      expect((request.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
      expect((request.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    }
    expect(JSON.parse(String(returnRequest.body))).toEqual({ reason: 'Correct previous data' })
    expect(JSON.parse(String(cancelRequest.body))).toEqual({ reason: 'Release to pool' })
    expect(JSON.parse(String(claimRequest.body))).toEqual({ comment: 'I will review' })
  })

  it('uses fresh idempotency keys for every return, cancel-claim and claim attempt', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ instanceId: '40' }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.returnInstance('10', '40', { reason: 'first' })
    await flowApi.returnInstance('10', '40', { reason: 'second' })
    await flowApi.cancelClaim('10', '40', { reason: 'third' })
    await flowApi.cancelClaim('10', '40', { reason: 'fourth' })
    await flowApi.claim('10', '40', { comment: 'fifth' })
    await flowApi.claim('10', '40', { comment: 'sixth' })

    const keys = fetchMock.mock.calls.map((call) =>
      ((call[1] as RequestInit).headers as Headers).get('Idempotency-Key'))
    expect(keys.every(Boolean)).toBe(true)
    expect(new Set(keys).size).toBe(6)
  })

  it('maps reduce-sign and copy mutation and timeline endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ instanceId: '40' }))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.reduceSign('10', '40', {
      targetStepIndex: 2,
      reason: 'Finance review is no longer required',
    })
    await flowApi.listCopies('10', '40', 2, 25)
    await flowApi.copy('10', '40', {
      targetMemberId: '300',
      message: 'Please follow this approval outcome',
    })

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/flow/instances/40:reduce-sign',
      '/api/v1/systems/10/flow/instances/40/copies?page=2&size=25',
      '/api/v1/systems/10/flow/instances/40/copies',
    ])
    const reduceSign = fetchMock.mock.calls[0]![1] as RequestInit
    const copies = fetchMock.mock.calls[1]![1] as RequestInit
    const copy = fetchMock.mock.calls[2]![1] as RequestInit
    expect(reduceSign.method).toBe('POST')
    expect((reduceSign.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    expect((reduceSign.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(reduceSign.body))).toEqual({
      targetStepIndex: 2,
      reason: 'Finance review is no longer required',
    })
    expect(copies.method).toBeUndefined()
    expect(copy.method).toBe('POST')
    expect((copy.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    expect((copy.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(JSON.parse(String(copy.body))).toEqual({
      targetMemberId: '300',
      message: 'Please follow this approval outcome',
    })
  })

  it('uses fresh idempotency keys for every reduce-sign and copy attempt', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({}))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.reduceSign('10', '40', { targetStepIndex: 2, reason: 'first' })
    await flowApi.reduceSign('10', '40', { targetStepIndex: 3, reason: 'second' })
    await flowApi.copy('10', '40', { targetMemberId: '300' })
    await flowApi.copy('10', '40', { targetMemberId: '301', message: 'fourth' })

    const keys = fetchMock.mock.calls.map((call) =>
      ((call[1] as RequestInit).headers as Headers).get('Idempotency-Key'))
    expect(keys.every(Boolean)).toBe(true)
    expect(new Set(keys).size).toBe(4)
  })
})
