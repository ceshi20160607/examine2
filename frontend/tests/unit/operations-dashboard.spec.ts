import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { analyticsApi } from '@/services/analytics'
import { useSessionStore } from '@/stores/session'
import type { OperationsAnalyticsSnapshot } from '@/types/analytics'
import OperationsDashboardView from '@/views/system/OperationsDashboardView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
const router = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => router }))
vi.mock('@/services/analytics', () => ({ analyticsApi: { operations: vi.fn() } }))

const activeSnapshot: OperationsAnalyticsSnapshot = {
  generatedAt: '2026-08-01T12:30:00Z',
  range: { from: '2026-07-26', to: '2026-08-02', days: 7 },
  work: {
    available: true,
    unavailableReason: null,
    openCount: 8,
    overdueOpenCount: 3,
    dueInRangeOpenCount: 4,
    completedInRangeCount: 6,
    route: '/systems/10/tasks',
    openRoute: '/systems/10/tasks?status=OPEN&role=ALL',
    overdueOpenRoute: '/systems/10/tasks?status=OPEN&role=ALL&time=OVERDUE&to=2026-08-01',
    dueInRangeOpenRoute: '/systems/10/tasks?status=OPEN&role=ALL&time=RANGE&from=2026-07-26&to=2026-08-02',
    completedInRangeRoute: '/systems/10/tasks?status=COMPLETED&role=ALL',
    daily: [
      { date: '2026-07-26', created: 2, completed: 1 },
      { date: '2026-07-27', created: 0, completed: 3 },
    ],
    topAssignees: [
      { memberId: '200', openCount: 5, route: '/systems/10/tasks?assigneeMemberId=200' },
      { memberId: '300', openCount: 3, route: '/systems/10/tasks?assigneeMemberId=300' },
    ],
  },
  flow: {
    available: true,
    unavailableReason: null,
    pendingCount: 4,
    pendingRoute: '/systems/10/flows?taskStatus=PENDING',
    terminalInRangeCount: 3,
    terminalInRangeRoute: '/systems/10/flows?from=2026-07-26&to=2026-08-02',
    approvedInRangeCount: 2,
    rejectedInRangeCount: 1,
    withdrawnInRangeCount: 0,
    terminatedInRangeCount: 0,
    daily: [
      { date: '2026-07-26', started: 3, terminal: 1 },
      { date: '2026-07-27', started: 1, terminal: 2 },
    ],
    terminalBreakdown: [
      { status: 'APPROVED', count: 2, route: '/systems/10/flows?status=APPROVED&from=2026-07-26&to=2026-08-02' },
      { status: 'REJECTED', count: 1, route: '/systems/10/flows?status=REJECTED&from=2026-07-26&to=2026-08-02' },
      { status: 'WITHDRAWN', count: 0, route: '/systems/10/flows?status=WITHDRAWN&from=2026-07-26&to=2026-08-02' },
      { status: 'TERMINATED', count: 0, route: '/systems/10/flows?status=TERMINATED&from=2026-07-26&to=2026-08-02' },
    ],
    route: '/systems/10/flows',
  },
  todo: {
    available: true,
    unavailableReason: null,
    openCount: 5,
    taskCount: 2,
    approvalCount: 3,
    todayCount: 1,
    overdueCount: 2,
    route: '/systems/10/todos?state=OPEN',
  },
}

function zeroSnapshot(): OperationsAnalyticsSnapshot {
  return {
    ...activeSnapshot,
    work: {
      ...activeSnapshot.work,
      openCount: 0,
      overdueOpenCount: 0,
      dueInRangeOpenCount: 0,
      completedInRangeCount: 0,
      daily: activeSnapshot.work.daily.map(item => ({ ...item, created: 0, completed: 0 })),
      topAssignees: [],
    },
    flow: {
      ...activeSnapshot.flow,
      pendingCount: 0,
      terminalInRangeCount: 0,
      approvedInRangeCount: 0,
      rejectedInRangeCount: 0,
      withdrawnInRangeCount: 0,
      terminatedInRangeCount: 0,
      daily: activeSnapshot.flow.daily.map(item => ({ ...item, started: 0, terminal: 0 })),
      terminalBreakdown: activeSnapshot.flow.terminalBreakdown.map(item => ({ ...item, count: 0 })),
    },
    todo: {
      ...activeSnapshot.todo,
      openCount: 0, taskCount: 0, approvalCount: 0, todayCount: 0, overdueCount: 0,
    },
  }
}

function unavailableSnapshot(): OperationsAnalyticsSnapshot {
  const zero = zeroSnapshot()
  return {
    ...zero,
    work: { ...zero.work, available: false, unavailableReason: 'Work access denied', daily: [] },
    flow: {
      ...zero.flow,
      available: false,
      unavailableReason: 'Flow access denied',
      daily: [],
      terminalBreakdown: [],
    },
    todo: { ...zero.todo, available: false, unavailableReason: 'Todo access denied' },
  }
}

function applySession(tenantId = '20') {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: {
      type: 'SYSTEM',
      account: { id: '1', username: 'member', displayName: 'Member' },
      systemId: '10', tenantId, memberId: '30', permissionVersion: '1',
      permissions: [], shells: ['SYSTEM_RUNTIME'],
    },
    systems: [],
  })
}

function render() {
  return mount(OperationsDashboardView, {
    global: {
      stubs: {
        OperationsSeriesChart: {
          props: ['categories', 'series'],
          template: '<div class="chart-stub">{{ categories.join(\',\') }}|{{ series.map(item => `${item.name}:${item.data.join(\',\')}`).join(\';\') }}</div>',
        },
        'a-alert': {
          props: ['message'],
          template: '<div>{{ message }}<slot /></div>',
        },
        'a-spin': {
          props: ['spinning'],
          template: '<div class="spin-stub" :data-spinning="spinning"><slot /></div>',
        },
        'a-empty': {
          props: ['description'],
          template: '<div>{{ description }}</div>',
        },
        'a-button': {
          emits: ['click'],
          template: '<button @click="$emit(\'click\')"><slot /></button>',
        },
      },
    },
  })
}

describe('OperationsDashboardView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-01T12:00:00Z'))
    setActivePinia(createPinia())
    vi.clearAllMocks()
    applySession()
    vi.mocked(analyticsApi.operations).mockResolvedValue(activeSnapshot)
  })

  afterEach(() => vi.useRealTimers())

  it('loads the latest seven UTC days and renders KPI, charts, breakdowns and drills', async () => {
    const wrapper = render()
    await flushPromises()

    expect(analyticsApi.operations).toHaveBeenCalledWith('10', '2026-07-26', '2026-08-02')
    expect(wrapper.get('.work-open').text()).toContain('8')
    expect(wrapper.get('.flow-terminal').text()).toContain('3')
    expect(wrapper.findAll('.chart-stub')).toHaveLength(2)
    expect(wrapper.findAll('.chart-stub')[0]!.text()).toContain('创建:2,0')
    expect(wrapper.findAll('.chart-stub')[1]!.text()).toContain('进入终态:1,2')
    expect(wrapper.findAll('.work-assignees .breakdown-row')).toHaveLength(2)
    expect(wrapper.findAll('.flow-terminal-breakdown .breakdown-row')).toHaveLength(4)

    await wrapper.get('.work-overdue').trigger('click')
    await wrapper.get('[data-status="REJECTED"]').trigger('click')
    await wrapper.get('.todo-open').trigger('click')
    expect(router.push.mock.calls.map(call => call[0])).toEqual([
      '/systems/10/tasks?status=OPEN&role=ALL&time=OVERDUE&to=2026-08-01',
      '/systems/10/flows?status=REJECTED&from=2026-07-26&to=2026-08-02',
      '/systems/10/todos?state=OPEN',
    ])
  })

  it('switches to the 30-day preset without changing system or tenant context', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.operations-range-30').trigger('click')
    await flushPromises()

    expect(vi.mocked(analyticsApi.operations).mock.calls.at(-1)).toEqual([
      '10', '2026-07-03', '2026-08-02',
    ])
  })

  it('renders unavailable separately from zero activity and exposes request errors', async () => {
    vi.mocked(analyticsApi.operations).mockResolvedValueOnce(unavailableSnapshot())
    const unavailable = render()
    await flushPromises()
    expect(unavailable.get('.operations-unavailable').text()).toContain('数据不可用而不是零')
    expect(unavailable.find('.work-empty').exists()).toBe(false)

    vi.mocked(analyticsApi.operations).mockResolvedValueOnce(zeroSnapshot())
    applySession('21')
    await flushPromises()
    expect(unavailable.get('.work-empty').text()).toContain('没有可见 Work 活动')
    expect(unavailable.get('.flow-empty').text()).toContain('没有可见 Flow 活动')
    expect(unavailable.get('.todo-analytics-empty').text()).toContain('没有开放待办')

    vi.mocked(analyticsApi.operations).mockRejectedValueOnce(new Error('analytics unavailable'))
    applySession('22')
    await flushPromises()
    expect(unavailable.get('.operations-error').text()).toContain('analytics unavailable')
  })

  it('discards stale tenant responses and keeps loading explicit', async () => {
    let resolveFirst!: (value: OperationsAnalyticsSnapshot) => void
    let resolveSecond!: (value: OperationsAnalyticsSnapshot) => void
    vi.mocked(analyticsApi.operations)
      .mockReturnValueOnce(new Promise(resolve => { resolveFirst = resolve }))
      .mockReturnValueOnce(new Promise(resolve => { resolveSecond = resolve }))
    const wrapper = render()
    expect(wrapper.get('.spin-stub').attributes('data-spinning')).toBe('true')

    applySession('21')
    const newest = {
      ...activeSnapshot,
      work: { ...activeSnapshot.work, openCount: 22 },
    }
    resolveSecond(newest)
    await flushPromises()
    resolveFirst({
      ...activeSnapshot,
      work: { ...activeSnapshot.work, openCount: 11 },
    })
    await flushPromises()

    expect(wrapper.get('.work-open').text()).toContain('22')
    expect(wrapper.get('.work-open').text()).not.toContain('11')
    expect(wrapper.get('.spin-stub').attributes('data-spinning')).toBe('false')
  })
})
