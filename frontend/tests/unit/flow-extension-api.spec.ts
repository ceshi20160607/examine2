import { beforeEach, describe, expect, it, vi } from 'vitest'

import { flowApi } from '@/services/flow'
import type { FlowExtensionGraph } from '@/types/flow'

function ok(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

describe('flow extension API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('maps catalog, draft and publication impact endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok([]))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.extensionNodeCatalog('10')
    await flowApi.extensionDraft('10', '20')
    await flowApi.extensionPublishImpact('10', '20')

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/flow/node-catalog',
      '/api/v1/systems/10/flow/definitions/20/extension-draft',
      '/api/v1/systems/10/flow/definitions/20/publish-impact',
    ])
  })

  it('saves the extension graph against the definition draft revision', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({}))
    vi.stubGlobal('fetch', fetchMock)
    const graph: FlowExtensionGraph = {
      nodes: [{
        code: 'start', name: '开始', type: 'START', applicationCode: 'purchase',
        moduleCode: null, config: {}, fieldPolicies: [], next: ['end'],
      }, {
        code: 'end', name: '结束', type: 'END', applicationCode: 'purchase',
        moduleCode: null, config: {}, fieldPolicies: [], next: [],
      }],
      dependencies: [],
    }

    await flowApi.saveExtensionDraft('10', '20/blue', 7, graph)

    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe('/api/v1/systems/10/flow/definitions/20%2Fblue/extension-draft')
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(String(init?.body))).toEqual({ expectedRevision: 7, graph })
  })

  it('writes versioned forms and executes/resumes nodes with caller-owned idempotency keys', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({}))
    vi.stubGlobal('fetch', fetchMock)

    await flowApi.nodeForm('10', '30', 'review/form')
    await flowApi.writeNodeForm('10', '30', 'review/form', {
      expectedSnapshotVersion: 2,
      expectedRecordVersion: 9,
      values: { title: 'approved' },
    }, 'form-key-1')
    await flowApi.nodeFormHistory('10', '30', 'review/form')
    await flowApi.executeExtensionNode('10', '30', 'wait', null, { event: 'start' }, 'execute-key-1')
    await flowApi.resumeExtensionNode('10', '30', 'wait', 1, { event: 'ready' }, 'resume-key-1')
    await flowApi.extensionNodeHistory('10', '30', 'wait')

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/flow/instances/30/nodes/review%2Fform/form',
      '/api/v1/systems/10/flow/instances/30/nodes/review%2Fform/form',
      '/api/v1/systems/10/flow/instances/30/nodes/review%2Fform/form/history',
      '/api/v1/systems/10/flow/instances/30/nodes/wait:execute',
      '/api/v1/systems/10/flow/instances/30/nodes/wait:resume',
      '/api/v1/systems/10/flow/instances/30/nodes/wait/execution-history',
    ])
    const headers = fetchMock.mock.calls.slice(1, 5).map(call => call[1]?.headers as Headers)
    expect(headers.map(value => value.get('Idempotency-Key'))).toEqual([
      'form-key-1', null, 'execute-key-1', 'resume-key-1',
    ])
    expect(JSON.parse(String(fetchMock.mock.calls[1]![1]?.body))).toEqual({
      expectedSnapshotVersion: 2,
      expectedRecordVersion: 9,
      values: { title: 'approved' },
    })
    expect(JSON.parse(String(fetchMock.mock.calls[4]![1]?.body))).toEqual({
      expectedVersion: 1,
      input: { event: 'ready' },
    })
  })
})
