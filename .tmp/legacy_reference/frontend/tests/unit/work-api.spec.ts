import { beforeEach, describe, expect, it, vi } from 'vitest'

import { workApi } from '@/services/work'

function ok(data: unknown) {
  return Promise.resolve(new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

describe('work API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('uses the tenant-scoped task collection and preserves member IDs as strings', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      id: '9007199254740993',
      assigneeMemberId: '9007199254740995',
    }))
    vi.stubGlobal('fetch', fetchMock)

    await workApi.create('9007199254740991', {
      title: 'Review contract',
      assigneeMemberId: '9007199254740995',
    })

    const [path, options] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/api/v1/systems/9007199254740991/work/tasks')
    expect(options.method).toBe('POST')
    expect(JSON.parse(String(options.body))).toEqual({
      title: 'Review contract',
      assigneeMemberId: '9007199254740995',
    })
    expect((options.headers as Headers).get('X-CSRF-Token')).toBe('csrf-token')
    expect((options.headers as Headers).get('Idempotency-Key')).toBeTruthy()
  })

  it('serializes escaped filter values and server pagination without changing ID types', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      items: [{
        id: '9007199254740993',
        systemId: '9007199254740991',
        tenantId: '9007199254740992',
        creatorMemberId: '9007199254740994',
        assigneeMemberId: '9007199254740995',
        title: '100% _literal_',
        status: 'OPEN',
        createdAt: '2026-07-25T08:00:00Z',
        updatedAt: '2026-07-25T09:00:00Z',
        version: 1,
      }],
      page: 2,
      size: 50,
      total: 51,
    }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await workApi.list('9007199254740991', {
      keyword: ' 100% _literal_ ',
      status: 'OPEN',
      role: 'ASSIGNED_TO_ME',
      page: 2,
      size: 50,
    })

    const [path, options] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe(
      '/api/v1/systems/9007199254740991/work/tasks'
      + '?keyword=100%25+_literal_&status=OPEN&role=ASSIGNED_TO_ME&page=2&size=50',
    )
    expect(options.method).toBeUndefined()
    expect(result.page).toBe(2)
    expect(result.total).toBe(51)
    expect(result.items[0]?.id).toBe('9007199254740993')
  })

  it('uses the frozen default work task page query', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      items: [], page: 1, size: 20, total: 0,
    }))
    vi.stubGlobal('fetch', fetchMock)

    await workApi.list('10')

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/v1/systems/10/work/tasks'
      + '?keyword=&status=ALL&role=PARTICIPATING&page=1&size=20',
    )
  })

  it('maps supported analytics drill filters without changing ID types', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({
      items: [], page: 1, size: 20, total: 0,
    }))
    vi.stubGlobal('fetch', fetchMock)

    await workApi.list('10', {
      status: 'OPEN',
      role: 'ALL',
      dueBefore: '2026-08-02T00:00:00Z',
      createdFrom: '2026-07-26T00:00:00Z',
      createdBefore: '2026-08-02T00:00:00Z',
      updatedFrom: '2026-07-26T00:00:00Z',
      updatedBefore: '2026-08-02T00:00:00Z',
      assigneeMemberId: '9007199254740991',
    })

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/v1/systems/10/work/tasks?keyword=&status=OPEN&role=ALL&page=1&size=20'
      + '&dueBefore=2026-08-02T00%3A00%3A00Z'
      + '&createdFrom=2026-07-26T00%3A00%3A00Z&createdBefore=2026-08-02T00%3A00%3A00Z'
      + '&updatedFrom=2026-07-26T00%3A00%3A00Z&updatedBefore=2026-08-02T00%3A00%3A00Z'
      + '&assigneeMemberId=9007199254740991',
    )
  })

  it('maps assign, complete and reopen to stable action endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ id: '33' }))
    vi.stubGlobal('fetch', fetchMock)

    await workApi.assign('10', '33', { assigneeMemberId: '44' })
    await workApi.complete('10', '33')
    await workApi.reopen('10', '33')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/systems/10/work/tasks/33:assign',
      '/api/v1/systems/10/work/tasks/33:complete',
      '/api/v1/systems/10/work/tasks/33:reopen',
    ])
  })

  it('maps task detail, metadata update and project/due filters exactly', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ id: '33' }))
    vi.stubGlobal('fetch', fetchMock)

    await workApi.list('10', {
      projectId: '501',
      dueFrom: '2026-08-01T00:00:00Z',
      dueTo: '2026-09-01T00:00:00Z',
    })
    await workApi.task('10', '33')
    await workApi.update('10', '33', {
      title: 'Scheduled work',
      projectId: '501',
      description: 'Ship it',
      dueAt: '2026-08-15T00:00:00Z',
      reminderAt: '2026-08-14T00:00:00Z',
      version: 7,
    })
    await workApi.retryTaskReminder('10', '33', { version: 9 })

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/work/tasks?keyword=&status=ALL&role=PARTICIPATING&page=1&size=20'
        + '&projectId=501&dueFrom=2026-08-01T00%3A00%3A00Z&dueTo=2026-09-01T00%3A00%3A00Z',
      '/api/v1/systems/10/work/tasks/33',
      '/api/v1/systems/10/work/tasks/33',
      '/api/v1/systems/10/work/tasks/33/reminder:retry',
    ])
    expect((fetchMock.mock.calls[2]![1] as RequestInit).method).toBe('PUT')
    expect(JSON.parse(String((fetchMock.mock.calls[2]![1] as RequestInit).body))).toEqual({
      title: 'Scheduled work',
      projectId: '501',
      description: 'Ship it',
      dueAt: '2026-08-15T00:00:00Z',
      reminderAt: '2026-08-14T00:00:00Z',
      version: 7,
    })
    expect((fetchMock.mock.calls[3]![1] as RequestInit).method).toBe('POST')
    expect(JSON.parse(String((fetchMock.mock.calls[3]![1] as RequestInit).body))).toEqual({
      version: 9,
    })
    expect((fetchMock.mock.calls[3]![1] as RequestInit).headers).toBeInstanceOf(Headers)
    expect(((fetchMock.mock.calls[3]![1] as RequestInit).headers as Headers)
      .get('Idempotency-Key')).toBeTruthy()
  })

  it('maps the complete project and member lifecycle endpoints', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ id: '501' }))
    vi.stubGlobal('fetch', fetchMock)

    await workApi.listProjects('10', { keyword: ' release ', status: 'ACTIVE', page: 2 })
    await workApi.createProject('10', { title: 'Release', description: null })
    await workApi.project('10', '501')
    await workApi.updateProject('10', '501', {
      title: 'Release train', description: 'Scope', version: 3,
    })
    await workApi.archiveProject('10', '501', { version: 4 })
    await workApi.reopenProject('10', '501', { version: 5 })
    await workApi.listProjectMembers('10', '501')
    await workApi.addProjectMember('10', '501', { memberId: '300', role: 'MEMBER' })
    await workApi.updateProjectMember('10', '501', '300', { role: 'OWNER', version: 2 })
    await workApi.removeProjectMember('10', '501', '300', { version: 3 })

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/work/projects?keyword=release&status=ACTIVE&page=2&size=20',
      '/api/v1/systems/10/work/projects',
      '/api/v1/systems/10/work/projects/501',
      '/api/v1/systems/10/work/projects/501',
      '/api/v1/systems/10/work/projects/501:archive',
      '/api/v1/systems/10/work/projects/501:reopen',
      '/api/v1/systems/10/work/projects/501/members',
      '/api/v1/systems/10/work/projects/501/members',
      '/api/v1/systems/10/work/projects/501/members/300',
      '/api/v1/systems/10/work/projects/501/members/300:remove',
    ])
    expect(fetchMock.mock.calls.map(call => (call[1] as RequestInit).method)).toEqual([
      undefined, 'POST', undefined, 'PUT', 'POST', 'POST', undefined, 'POST', 'PUT', 'POST',
    ])
    expect(JSON.parse(String((fetchMock.mock.calls[7]![1] as RequestInit).body))).toEqual({
      memberId: '300', role: 'MEMBER',
    })
    expect(JSON.parse(String((fetchMock.mock.calls[8]![1] as RequestInit).body))).toEqual({
      role: 'OWNER', version: 2,
    })
    expect(JSON.parse(String((fetchMock.mock.calls[9]![1] as RequestInit).body))).toEqual({
      version: 3,
    })
  })

  it('maps daily-report CRUD, manager query, transitions and seven-day summary', async () => {
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => ok({ id: '701' }))
    vi.stubGlobal('fetch', fetchMock)

    await workApi.listReports('10', {
      scope: 'ALL',
      memberId: '300',
      dateFrom: '2026-07-30',
      dateTo: '2026-08-05',
      status: 'SUBMITTED',
      page: 2,
      size: 50,
    })
    await workApi.createReport('10', {
      workDate: '2026-08-05',
      completedWork: 'Done',
      plannedWork: 'Next',
      blockers: null,
    })
    await workApi.report('10', '701')
    await workApi.updateReport('10', '701', {
      completedWork: 'Done revised',
      plannedWork: 'Next revised',
      blockers: 'Blocked',
      version: 1,
    })
    await workApi.submitReport('10', '701', { version: 2 })
    await workApi.reopenReport('10', '701', { version: 3 })
    await workApi.reportSummary('10', '2026-08-05', '300')

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/systems/10/work/reports?scope=ALL&status=SUBMITTED&page=2&size=50'
        + '&memberId=300&dateFrom=2026-07-30&dateTo=2026-08-05',
      '/api/v1/systems/10/work/reports',
      '/api/v1/systems/10/work/reports/701',
      '/api/v1/systems/10/work/reports/701',
      '/api/v1/systems/10/work/reports/701:submit',
      '/api/v1/systems/10/work/reports/701:reopen',
      '/api/v1/systems/10/work/reports/summary?endDate=2026-08-05&memberId=300',
    ])
    expect(fetchMock.mock.calls.map(call => (call[1] as RequestInit).method)).toEqual([
      undefined, 'POST', undefined, 'PUT', 'POST', 'POST', undefined,
    ])
    expect(JSON.parse(String((fetchMock.mock.calls[1]![1] as RequestInit).body))).toEqual({
      workDate: '2026-08-05',
      completedWork: 'Done',
      plannedWork: 'Next',
      blockers: null,
    })
    expect(JSON.parse(String((fetchMock.mock.calls[3]![1] as RequestInit).body))).toEqual({
      completedWork: 'Done revised',
      plannedWork: 'Next revised',
      blockers: 'Blocked',
      version: 1,
    })
  })
})
