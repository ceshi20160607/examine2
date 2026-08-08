import { beforeEach, describe, expect, it, vi } from 'vitest'

import { workApi } from '@/services/work'
import type { WorkConfigurationSnapshot } from '@/types/work'

function envelope(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

const snapshot: WorkConfigurationSnapshot = {
  fields: {
    PROJECT_TASK: [{
      code: 'priority', name: '优先级', type: 'SELECT', required: true,
      dictionaryCode: 'work_priority', readPermission: null, editPermission: 'work.task.manage',
      cardVisible: true, kanbanRole: 'COLUMN',
    }],
    ORDINARY_TASK: [],
    DAILY_REPORT: [],
  },
}

describe('work configuration API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('maps the immutable draft, check, publish, history, active, rollback and runtime endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => envelope({ items: [] }))
    vi.stubGlobal('fetch', fetchMock)

    await workApi.workConfigurationHistory('10/20')
    await workApi.activeWorkConfiguration('10/20')
    await workApi.createWorkConfigurationDraft('10/20', snapshot)
    await workApi.checkWorkConfiguration('10/20', 'draft/7', 3)
    await workApi.publishWorkConfiguration('10/20', 'draft/7', 3)
    await workApi.rollbackWorkConfiguration('10/20', 2)
    await workApi.workRuntimeConfiguration('10/20', 'ORDINARY_TASK')

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10%2F20/work/configuration/history',
      '/api/v1/systems/10%2F20/work/configuration/active',
      '/api/v1/systems/10%2F20/work/configuration/drafts',
      '/api/v1/systems/10%2F20/work/configuration/drafts/draft%2F7:check',
      '/api/v1/systems/10%2F20/work/configuration/drafts/draft%2F7:publish',
      '/api/v1/systems/10%2F20/work/configuration:rollback',
      '/api/v1/systems/10%2F20/work/configuration/runtime/ORDINARY_TASK',
    ])
    expect(JSON.parse(String((fetchMock.mock.calls[2]![1] as RequestInit).body))).toEqual({ snapshot })
    expect(JSON.parse(String((fetchMock.mock.calls[3]![1] as RequestInit).body))).toEqual({ version: 3 })
    expect(JSON.parse(String((fetchMock.mock.calls[4]![1] as RequestInit).body))).toEqual({ version: 3 })
    expect(JSON.parse(String((fetchMock.mock.calls[5]![1] as RequestInit).body))).toEqual({ targetRevision: 2 })
    for (const index of [2, 3, 4, 5]) {
      const request = fetchMock.mock.calls[index]![1] as RequestInit
      expect(request.method).toBe('POST')
      expect((request.headers as Headers).get('Idempotency-Key')).toBeTruthy()
      expect((request.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    }
  })
})
