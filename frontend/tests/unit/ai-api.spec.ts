import { beforeEach, describe, expect, it, vi } from 'vitest'

import { aiAdminApi, aiRuntimeApi } from '@/services/ai'

function envelope(data: unknown) {
  return new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('AI admin and runtime API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('uses the fixed provider, policy, check, publish and capability endpoints', async () => {
    const provider = {
      id: 'provider-1', code: 'openai-main', name: 'OpenAI compatible', baseUrl: 'https://ai.example/v1',
      model: 'model-a', secretRef: 'vault://ai/provider/v1', timeoutSeconds: 30, enabled: true, version: 2,
    }
    const rawPolicy = {
      draftVersion: 3, activeVersionId: 'policy-2', providerId: 'provider-1', moduleCodes: ['orders'],
      outputFieldCodes: ['orders.orderNo', 'orders.amount'], maxRows: 20, redactionMode: 'STRICT',
      allowedOperations: ['RECORD_QUERY', 'RECORD_CREATE', 'RECORD_UPDATE', 'AI_FILL', 'CONFIG_FIELD_DRAFT', 'CONFIG_SELECTION_FIELD_DRAFT', 'CONFIG_PAGE_LAYOUT_DRAFT', 'RECORD_CONTEXT_SUMMARY', 'RECORD_COMMENT_QUERY', 'RECORD_HISTORY_QUERY', 'RECORD_FILE_QUERY', 'WORK_TASK_QUERY', 'WORK_DAILY_REPORT_QUERY', 'WORK_PROJECT_METRICS_QUERY', 'RUNTIME_STATISTICS_QUERY', 'RUNTIME_REPORT_QUERY', 'WORK_TASK_DRAFT', 'WORK_DAILY_REPORT_DRAFT', 'FLOW_DEFINITION_DRAFT', 'CONFIG_REPORT_DRAFT', 'CONFIG_PRINT_TEMPLATE_DRAFT'],
      writableFields: { orders: ['amount'] }, confirmationMode: 'REQUIRED',
      fillFields: { orders: ['aiSummary'] },
      confirmationExpiresSeconds: 900, promptVersion: 'v1', enabled: true,
    }
    const responses = [
      { items: [provider] }, provider, provider, rawPolicy,
      { ...rawPolicy, draftVersion: 4, outboundFields: { orders: ['orderNo', 'amount'] } },
      { status: 'PASSED', issues: [] }, { ...rawPolicy, activeVersionId: 'policy-4' },
      { available: true, reason: null, policyVersion: 'policy-4' },
    ]
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope(responses.shift())))
    vi.stubGlobal('fetch', fetchMock)

    expect(await aiAdminApi.providers('10')).toEqual([provider])
    const providerInput = {
      code: 'openai-main', name: 'OpenAI compatible',
      baseUrl: 'https://ai.example/v1', model: 'model-a', secretRef: 'vault://ai/provider/v1',
      timeoutSeconds: 30, enabled: true,
    }
    await aiAdminApi.createProvider('10', providerInput)
    await aiAdminApi.updateProvider('10', 'provider/1', { ...providerInput, expectedVersion: 2 })
    expect((await aiAdminApi.policy('10')).outboundFields).toEqual({ orders: ['orderNo', 'amount'] })
    await aiAdminApi.savePolicy('10', {
      expectedVersion: 3, providerId: 'provider-1', moduleCodes: ['orders'],
      outboundFields: { orders: ['orderNo', 'amount'] }, maxRows: 20,
      allowedOperations: ['RECORD_QUERY', 'RECORD_CREATE', 'RECORD_UPDATE', 'AI_FILL', 'CONFIG_FIELD_DRAFT', 'CONFIG_SELECTION_FIELD_DRAFT', 'CONFIG_PAGE_LAYOUT_DRAFT', 'RECORD_CONTEXT_SUMMARY', 'RECORD_COMMENT_QUERY', 'RECORD_HISTORY_QUERY', 'RECORD_FILE_QUERY', 'WORK_TASK_QUERY', 'WORK_DAILY_REPORT_QUERY', 'WORK_PROJECT_METRICS_QUERY', 'RUNTIME_STATISTICS_QUERY', 'RUNTIME_REPORT_QUERY', 'WORK_TASK_DRAFT', 'WORK_DAILY_REPORT_DRAFT', 'FLOW_DEFINITION_DRAFT', 'CONFIG_REPORT_DRAFT', 'CONFIG_PRINT_TEMPLATE_DRAFT'],
      writableFields: { orders: ['amount'] }, confirmationMode: 'REQUIRED',
      fillFields: { orders: ['aiSummary'] },
      confirmationExpiresSeconds: 900,
      redactionMode: 'STRICT', promptVersion: 'v1', enabled: true,
    })
    await aiAdminApi.checkPolicy('10')
    await aiAdminApi.publishPolicy('10', 4)
    await aiAdminApi.capability('10')

    const root = '/api/v1/systems/10/admin/ai'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      `${root}/providers`, `${root}/providers`, `${root}/providers/provider%2F1`, `${root}/policy`, `${root}/policy`,
      `${root}/policy:check`, `${root}/policy:publish`, `${root}/capability`,
    ])
    const providerRequest = fetchMock.mock.calls[1]![1] as RequestInit
    const updateRequest = fetchMock.mock.calls[2]![1] as RequestInit
    const policyRequest = fetchMock.mock.calls[4]![1] as RequestInit
    const publishRequest = fetchMock.mock.calls[6]![1] as RequestInit
    expect(providerRequest.method).toBe('POST')
    expect((providerRequest.headers as Headers).get('Idempotency-Key')).toBeTruthy()
    expect(updateRequest.method).toBe('PUT')
    expect(JSON.parse(String(updateRequest.body))).toMatchObject({ expectedVersion: 2 })
    expect(JSON.parse(String(policyRequest.body))).toMatchObject({
      expectedVersion: 3, outboundFields: { orders: ['orderNo', 'amount'] },
      allowedOperations: ['RECORD_QUERY', 'RECORD_CREATE', 'RECORD_UPDATE', 'AI_FILL', 'CONFIG_FIELD_DRAFT', 'CONFIG_SELECTION_FIELD_DRAFT', 'CONFIG_PAGE_LAYOUT_DRAFT', 'RECORD_CONTEXT_SUMMARY', 'RECORD_COMMENT_QUERY', 'RECORD_HISTORY_QUERY', 'RECORD_FILE_QUERY', 'WORK_TASK_QUERY', 'WORK_DAILY_REPORT_QUERY', 'WORK_PROJECT_METRICS_QUERY', 'RUNTIME_STATISTICS_QUERY', 'RUNTIME_REPORT_QUERY', 'WORK_TASK_DRAFT', 'WORK_DAILY_REPORT_DRAFT', 'FLOW_DEFINITION_DRAFT', 'CONFIG_REPORT_DRAFT', 'CONFIG_PRINT_TEMPLATE_DRAFT'],
      writableFields: { orders: ['amount'] }, confirmationMode: 'REQUIRED',
      fillFields: { orders: ['aiSummary'] },
      confirmationExpiresSeconds: 900, redactionMode: 'STRICT',
    })
    expect(JSON.parse(String(publishRequest.body))).toEqual({ expectedVersion: 4 })
    expect((publishRequest.headers as Headers).get('Idempotency-Key')).toBeTruthy()
  })

  it('normalizes session pages and uses create, detail and message endpoints', async () => {
    const session = { id: 'session-1', title: '订单', status: 'ACTIVE', createdAt: '2026-08-04T00:00:00Z', updatedAt: '2026-08-04T00:00:00Z' }
    const turn = {
      id: 'turn-1', status: 'SUCCEEDED', answer: '共 1 条', errorCode: null, retryable: false,
      tool: null, requestId: 'request-1', traceId: 'trace-1',
      contextResult: {
        operation: 'RUNTIME_REPORT_QUERY', record: null, reports: [], todos: null, messages: null,
        tasks: [], runtimeReport: {
          reportCode: 'monthly_orders', reportName: '月度订单', reportVersionNumber: 4,
          dataSourceCode: 'published_orders', dataSourceVersionNumber: 7, moduleCode: 'orders',
          page: 1, size: 20, total: 1, returnedRows: 1, hasMore: false,
          route: '/systems/10/reports/monthly_orders',
          fields: [{ fieldCode: 'amount', fieldName: '金额', type: 'DECIMAL' }],
          rows: [{ values: [{ fieldCode: 'amount', displayValue: '12345678901234567890.123400' }] }],
        },
      },
    }
    const responses = [
      { available: true, reason: null, policyVersion: 'policy-4' },
      { rows: [session], page: 2, size: 10, hasMore: false }, session,
      { session, messages: [], turns: [] }, turn,
    ]
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope(responses.shift())))
    vi.stubGlobal('fetch', fetchMock)

    await aiRuntimeApi.capability('10')
    expect(await aiRuntimeApi.sessions('10', 2, 10)).toEqual({ rows: [session], page: 2, size: 10, hasMore: false })
    await aiRuntimeApi.createSession('10', { title: '订单' })
    await aiRuntimeApi.session('10', 'session/1')
    const messageResult = await aiRuntimeApi.submitMessage('10', 'session/1', { content: '读取月度订单报表' })
    expect(messageResult.contextResult?.operation).toBe('RUNTIME_REPORT_QUERY')
    if (messageResult.contextResult?.operation !== 'RUNTIME_REPORT_QUERY') throw new Error('unexpected context result')
    expect(messageResult.contextResult.runtimeReport?.rows[0]?.values[0]?.displayValue)
      .toBe('12345678901234567890.123400')

    const root = '/api/v1/systems/10/ai/sessions'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/ai/capability', `${root}?page=2&size=10`, root,
      `${root}/session%2F1`, `${root}/session%2F1/messages`,
    ])
    expect(JSON.parse(String((fetchMock.mock.calls[2]![1] as RequestInit).body))).toEqual({ title: '订单' })
    expect(JSON.parse(String((fetchMock.mock.calls[4]![1] as RequestInit).body))).toEqual({ content: '读取月度订单报表' })
    expect(((fetchMock.mock.calls[4]![1] as RequestInit).headers as Headers).get('Idempotency-Key')).toBeTruthy()
  })

  it('passes through typed record comment, history and file context results', async () => {
    const session = { id: 'session-activity', title: '记录活动', status: 'ACTIVE', createdAt: '2026-08-05T00:00:00Z', updatedAt: '2026-08-05T00:00:00Z' }
    const common = { record: null, tasks: [], reports: [], todos: null, messages: null, workMetrics: null }
    const route = '/systems/10/workbench?module=orders&mode=view&record=9001'
    const detail = {
      session, messages: [], turns: [{
        id: 'turn-comments', status: 'SUCCEEDED', answer: '共 1 条评论', errorCode: null,
        retryable: false, tool: null, requestId: 'request-comments', traceId: 'trace-comments',
        contextResult: { ...common, operation: 'RECORD_COMMENT_QUERY', recordHistory: null, recordFiles: null,
          recordComments: { moduleCode: 'orders', recordId: '9001', total: 0, route, items: [] } },
      }, {
        id: 'turn-history', status: 'SUCCEEDED', answer: '共 1 条历史', errorCode: null,
        retryable: false, tool: null, requestId: 'request-history', traceId: 'trace-history',
        contextResult: { ...common, operation: 'RECORD_HISTORY_QUERY', recordComments: null, recordFiles: null,
          recordHistory: { moduleCode: 'orders', recordId: '9001', total: 0, route, items: [] } },
      }, {
        id: 'turn-files', status: 'SUCCEEDED', answer: '共 1 个附件', errorCode: null,
        retryable: false, tool: null, requestId: 'request-files', traceId: 'trace-files',
        contextResult: { ...common, operation: 'RECORD_FILE_QUERY', recordComments: null, recordHistory: null,
          recordFiles: { moduleCode: 'orders', recordId: '9001', total: 0, route, items: [] } },
      }],
    }
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(envelope(detail))))

    const result = await aiRuntimeApi.session('10', 'session/activity')
    expect(result.turns.map(turn => turn.contextResult?.operation)).toEqual([
      'RECORD_COMMENT_QUERY', 'RECORD_HISTORY_QUERY', 'RECORD_FILE_QUERY',
    ])
    expect(result.turns[0]?.contextResult?.recordComments?.route).toBe(route)
    expect(result.turns[1]?.contextResult?.recordHistory?.route).toBe(route)
    expect(result.turns[2]?.contextResult?.recordFiles?.route).toBe(route)
  })

  it('loads, confirms and rejects a versioned confirmation through system-level endpoints', async () => {
    const confirmation = {
      id: 'confirmation/1', sessionId: 'session-1', turnId: 'turn-1', state: 'PENDING',
      operation: 'RECORD_UPDATE', moduleCode: 'orders', recordId: 'record-1',
      expectedRecordVersion: 4, beforeTitle: '订单', afterTitle: '更新订单', fields: [], clarifications: [],
      expiresAt: '2026-08-04T01:00:00Z', version: 7, result: null, errorCode: null,
    }
    const responses = [confirmation, { ...confirmation, state: 'SUCCEEDED', version: 8 }, {
      ...confirmation, state: 'REJECTED', version: 8,
    }]
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope(responses.shift())))
    vi.stubGlobal('fetch', fetchMock)

    await aiRuntimeApi.confirmation('10', 'confirmation/1')
    await aiRuntimeApi.confirm('10', 'confirmation/1', 7, 'confirmation-key-1')
    await aiRuntimeApi.reject('10', 'confirmation/1', 7)

    const root = '/api/v1/systems/10/ai/confirmations/confirmation%2F1'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      root, `${root}/confirm`, `${root}/reject`,
    ])
    const confirmRequest = fetchMock.mock.calls[1]![1] as RequestInit
    const rejectRequest = fetchMock.mock.calls[2]![1] as RequestInit
    expect(confirmRequest.method).toBe('POST')
    expect((confirmRequest.headers as Headers).get('Idempotency-Key')).toBe('confirmation-key-1')
    expect(JSON.parse(String(confirmRequest.body))).toEqual({ expectedVersion: 7 })
    expect(rejectRequest.method).toBe('POST')
    expect(JSON.parse(String(rejectRequest.body))).toEqual({ expectedVersion: 7 })
  })

  it('loads, confirms and rejects a revisioned configuration proposal under its session', async () => {
    const proposal = {
      id: 'configuration-proposal/1', sessionId: 'session/1', turnId: 'turn-configuration',
      state: 'PENDING', revision: 3, moduleCode: 'orders',
      preview: {
        configRootId: 'config-root-1', moduleId: 'module-orders', expectedDraftRevision: 12,
        nextDraftRevision: 13, moduleCode: 'orders', fieldCode: 'externalReference', fieldName: '外部引用',
        fieldType: 'TEXT', required: true,
        settings: { maxLength: 120, precision: null, scale: null, minimum: null, maximum: null },
      },
      confidence: 0.97, clarification: null, expiresAt: '2099-08-04T02:00:00Z',
      result: null, errorCode: null, requestId: 'request-config', traceId: 'trace-config',
    }
    const responses = [proposal, { ...proposal, state: 'SUCCEEDED', revision: 4 }, {
      ...proposal, state: 'REJECTED', revision: 4,
    }]
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope(responses.shift())))
    vi.stubGlobal('fetch', fetchMock)

    await aiRuntimeApi.configurationProposal('10', 'session/1', 'configuration-proposal/1')
    await aiRuntimeApi.confirmConfigurationProposal(
      '10', 'session/1', 'configuration-proposal/1', 3, 'configuration-proposal-key-1',
    )
    await aiRuntimeApi.rejectConfigurationProposal('10', 'session/1', 'configuration-proposal/1', 3)

    const root = '/api/v1/systems/10/ai/sessions/session%2F1/configuration-proposals/configuration-proposal%2F1'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([root, `${root}/confirm`, `${root}/reject`])
    const confirmRequest = fetchMock.mock.calls[1]![1] as RequestInit
    const rejectRequest = fetchMock.mock.calls[2]![1] as RequestInit
    expect(confirmRequest.method).toBe('POST')
    expect(JSON.parse(String(confirmRequest.body))).toEqual({ expectedRevision: 3 })
    expect((confirmRequest.headers as Headers).get('Idempotency-Key')).toBe('configuration-proposal-key-1')
    expect(rejectRequest.method).toBe('POST')
    expect(JSON.parse(String(rejectRequest.body))).toEqual({ expectedRevision: 3 })
    expect((rejectRequest.headers as Headers).has('Idempotency-Key')).toBe(false)
    expect(fetchMock.mock.calls.map(call => String(call[0])).join(' ')).not.toContain('publish')
  })

  it('loads, confirms and rejects a typed configuration artifact proposal under its session', async () => {
    const proposal = {
      id: 'artifact-proposal/1', sessionId: 'session/1', turnId: 'turn-artifact', state: 'PENDING', revision: 5,
      operation: 'CONFIG_SELECTION_FIELD_DRAFT', artifactKind: 'SELECTION_FIELD', moduleCode: 'orders',
      preview: {
        operation: 'CONFIG_SELECTION_FIELD_DRAFT', configRootId: 'config-root-1', moduleId: 'module-orders',
        moduleCode: 'orders', expectedDraftRevision: 13, nextDraftRevision: 14, pageLayout: null,
        filterScenario: null, fieldPermissionStage: null,
        selectionField: {
          sortOrder: 5, fieldCode: 'priority', fieldName: '优先级', fieldType: 'RADIO', required: true,
          dictionaryCode: 'order_priority', dictionaryName: '订单优先级', maxSelections: null,
          options: [
            { code: 'HIGH', label: '高', semanticKey: 'priority.high', color: '#E5484D', defaultOption: true, sortOrder: 0 },
            { code: 'LOW', label: '低', semanticKey: 'priority.low', color: '#30A46C', defaultOption: false, sortOrder: 1 },
          ],
        },
      },
      confidence: 0.98, clarification: null, expiresAt: '2099-08-04T03:00:00Z', result: null,
      errorCode: null, requestId: 'request-artifact', traceId: 'trace-artifact',
    }
    const responses = [proposal, { ...proposal, state: 'SUCCEEDED', revision: 6 }, {
      ...proposal, state: 'REJECTED', revision: 6,
    }]
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope(responses.shift())))
    vi.stubGlobal('fetch', fetchMock)

    await aiRuntimeApi.configurationArtifactProposal('10', 'session/1', 'artifact-proposal/1')
    await aiRuntimeApi.confirmConfigurationArtifactProposal(
      '10', 'session/1', 'artifact-proposal/1', 5, 'artifact-proposal-key-1',
    )
    await aiRuntimeApi.rejectConfigurationArtifactProposal('10', 'session/1', 'artifact-proposal/1', 5)

    const root = '/api/v1/systems/10/ai/sessions/session%2F1/configuration-artifact-proposals/artifact-proposal%2F1'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([root, `${root}/confirm`, `${root}/reject`])
    const confirmRequest = fetchMock.mock.calls[1]![1] as RequestInit
    const rejectRequest = fetchMock.mock.calls[2]![1] as RequestInit
    expect(confirmRequest.method).toBe('POST')
    expect(JSON.parse(String(confirmRequest.body))).toEqual({ expectedRevision: 5 })
    expect((confirmRequest.headers as Headers).get('Idempotency-Key')).toBe('artifact-proposal-key-1')
    expect(rejectRequest.method).toBe('POST')
    expect(JSON.parse(String(rejectRequest.body))).toEqual({ expectedRevision: 5 })
    expect((rejectRequest.headers as Headers).has('Idempotency-Key')).toBe(false)
    expect(fetchMock.mock.calls.map(call => String(call[0])).join(' ')).not.toContain('publish')
  })

  it('loads, confirms and rejects a typed work draft proposal under its session', async () => {
    const proposal = {
      id: 'work-proposal/1', sessionId: 'session/1', turnId: 'turn-work', state: 'PENDING', revision: 2,
      operation: 'WORK_TASK_DRAFT',
      preview: {
        operation: 'WORK_TASK_DRAFT', dailyReport: null,
        task: {
          title: '完成订单对账', description: '[description:redacted]', assigneeMemberId: 'member-7',
          assigneeDisplayName: null, projectId: 'project-3', projectDisplayName: null, dueAt: '2026-08-05T10:00:00Z',
        },
      },
      confidence: 0.96, clarification: null, expiresAt: '2099-08-04T04:00:00Z', result: null,
      errorCode: null, requestId: 'request-work', traceId: 'trace-work',
    }
    const responses = [proposal, { ...proposal, state: 'SUCCEEDED', revision: 3 }, {
      ...proposal, state: 'REJECTED', revision: 3,
    }]
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope(responses.shift())))
    vi.stubGlobal('fetch', fetchMock)

    await aiRuntimeApi.workProposal('10', 'session/1', 'work-proposal/1')
    await aiRuntimeApi.confirmWorkProposal('10', 'session/1', 'work-proposal/1', 2, 'work-proposal-key-1')
    await aiRuntimeApi.rejectWorkProposal('10', 'session/1', 'work-proposal/1', 2)

    const root = '/api/v1/systems/10/ai/sessions/session%2F1/work-proposals/work-proposal%2F1'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([root, `${root}/confirm`, `${root}/reject`])
    const confirmRequest = fetchMock.mock.calls[1]![1] as RequestInit
    const rejectRequest = fetchMock.mock.calls[2]![1] as RequestInit
    expect(confirmRequest.method).toBe('POST')
    expect(JSON.parse(String(confirmRequest.body))).toEqual({ expectedRevision: 2 })
    expect((confirmRequest.headers as Headers).get('Idempotency-Key')).toBe('work-proposal-key-1')
    expect(rejectRequest.method).toBe('POST')
    expect(JSON.parse(String(rejectRequest.body))).toEqual({ expectedRevision: 2 })
    expect((rejectRequest.headers as Headers).has('Idempotency-Key')).toBe(false)
    const urls = fetchMock.mock.calls.map(call => String(call[0])).join(' ')
    expect(urls).not.toContain('/complete')
    expect(urls).not.toContain('/submit')
  })

  it('loads, confirms and rejects a typed generated draft proposal under its session', async () => {
    const proposal = {
      id: 'generated-proposal/1', sessionId: 'session/1', turnId: 'turn-generated', state: 'PENDING', revision: 6,
      operation: 'CONFIG_PRINT_TEMPLATE_DRAFT',
      preview: {
        operation: 'CONFIG_PRINT_TEMPLATE_DRAFT', flowDefinition: null, reportDefinition: null,
        printTemplate: {
          moduleCode: 'orders', code: 'order_receipt', name: '订单回执', paperSize: 'A4', orientation: 'PORTRAIT',
          title: '[title:redacted]', fieldCodes: ['recordNo', 'amount'], footer: '[footer:redacted]',
        },
      },
      confidence: 0.93, clarification: null, expiresAt: '2099-08-04T05:00:00Z', result: null,
      errorCode: null, requestId: 'request-generated', traceId: 'trace-generated',
    }
    const responses = [proposal, { ...proposal, state: 'SUCCEEDED', revision: 7 }, {
      ...proposal, state: 'REJECTED', revision: 7,
    }]
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(envelope(responses.shift())))
    vi.stubGlobal('fetch', fetchMock)

    await aiRuntimeApi.generatedDraftProposal('10', 'session/1', 'generated-proposal/1')
    await aiRuntimeApi.confirmGeneratedDraftProposal(
      '10', 'session/1', 'generated-proposal/1', 6, 'generated-proposal-key-1',
    )
    await aiRuntimeApi.rejectGeneratedDraftProposal('10', 'session/1', 'generated-proposal/1', 6)

    const root = '/api/v1/systems/10/ai/sessions/session%2F1/generated-draft-proposals/generated-proposal%2F1'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([root, `${root}/confirm`, `${root}/reject`])
    const confirmRequest = fetchMock.mock.calls[1]![1] as RequestInit
    const rejectRequest = fetchMock.mock.calls[2]![1] as RequestInit
    expect(confirmRequest.method).toBe('POST')
    expect(JSON.parse(String(confirmRequest.body))).toEqual({ expectedRevision: 6 })
    expect((confirmRequest.headers as Headers).get('Idempotency-Key')).toBe('generated-proposal-key-1')
    expect(rejectRequest.method).toBe('POST')
    expect(JSON.parse(String(rejectRequest.body))).toEqual({ expectedRevision: 6 })
    expect((rejectRequest.headers as Headers).has('Idempotency-Key')).toBe(false)
    const urls = fetchMock.mock.calls.map(call => String(call[0])).join(' ')
    expect(urls).not.toContain('publish')
    expect(urls).not.toContain('activate')
    expect(urls).not.toContain('preview')
    expect(urls).not.toContain('print')
  })
})
