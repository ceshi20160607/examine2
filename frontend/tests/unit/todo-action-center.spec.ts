import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiRequestError } from '@/services/api'
import { todoApi } from '@/services/todo'
import { useSessionStore } from '@/stores/session'
import type { TodoActionResult, TodoItem, TodoPage } from '@/types/todo'
import TodoActionCenterView from '@/views/system/TodoActionCenterView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
const router = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

vi.mock('@/services/todo', () => ({
  TODO_COUNTS_CHANGED_EVENT: 'examine:todo-counts-changed',
  todoApi: {
    list: vi.fn(),
    counts: vi.fn(),
    detail: vi.fn(),
    refresh: vi.fn(),
    action: vi.fn(),
  },
}))

const approval: TodoItem = {
  id: '101',
  systemId: '10',
  tenantId: '20',
  recipientMemberId: '30',
  sourceType: 'FLOW_APPROVAL',
  sourceId: '201',
  sourceVersion: 8,
  actionScope: 'INSTANCE_DECISION',
  category: 'APPROVAL',
  title: 'Approve purchase request',
  dueAt: '2099-08-20T10:00:00Z',
  priority: 10,
  routeHint: '/systems/10/flows?instanceId=201',
  representedMemberId: '50',
  availableActions: ['APPROVE', 'REJECT'],
  status: 'OPEN',
  closeReason: null,
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T01:00:00Z',
  closedAt: null,
  version: 3,
}

const reminder: TodoItem = {
  ...approval,
  id: '102',
  sourceType: 'EVENT_MESSAGE',
  sourceId: 'message-301',
  sourceVersion: 6,
  actionScope: 'EVENT_MESSAGE_READ',
  category: 'REMINDER',
  title: 'Release task reminder',
  dueAt: null,
  routeHint: '/systems/10/tasks?taskId=501',
  representedMemberId: null,
  availableActions: ['MARK_READ'],
  createdAt: '2026-08-02T03:04:05Z',
  updatedAt: '2026-08-02T03:04:05Z',
  version: 4,
}

const cc: TodoItem = {
  ...reminder,
  id: '103',
  sourceId: 'message-302',
  sourceVersion: 7,
  category: 'CC',
  title: 'Flow instance copied',
  routeHint: '/systems/10/flows?instanceId=601',
  createdAt: '2026-08-03T04:05:06Z',
  updatedAt: '2026-08-03T04:05:06Z',
  version: 5,
}

function todoPage(items: TodoItem[], page = 1, total = items.length): TodoPage {
  return { items, page, size: 20, total }
}

function error(status: number, code: string, message: string) {
  return new ApiRequestError(status, {
    code,
    message,
    data: null,
    requestId: `request-${code}`,
    traceId: `trace-${code}`,
    errors: [],
  })
}

function applySession(tenantId = '20') {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: {
      type: 'SYSTEM',
      account: { id: '1', username: 'member', displayName: 'Member' },
      systemId: '10',
      tenantId,
      memberId: '30',
      permissionVersion: '1',
      permissions: [],
      shells: ['SYSTEM_RUNTIME'],
    },
    systems: [],
  })
}

function render() {
  return mount(TodoActionCenterView, {
    global: {
      stubs: {
        'a-alert': {
          props: ['message'],
          template: '<div>{{ message }}<slot /></div>',
        },
        'a-spin': { template: '<div><slot /></div>' },
        'a-empty': {
          props: ['description'],
          template: '<div class="empty">{{ description }}</div>',
        },
        'a-badge': {
          props: ['count'],
          template: '<span>{{ count }}</span>',
        },
        'a-tag': { template: '<span><slot /></span>' },
        'a-button': {
          props: ['disabled'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
        },
        'a-pagination': {
          emits: ['change', 'showSizeChange'],
          template: '<button class="page-two" @click="$emit(\'change\', 2, 20)">page 2</button>',
        },
        'a-modal': {
          props: ['open'],
          emits: ['ok'],
          template: '<div v-if="open" class="action-modal"><slot /><button class="modal-ok" @click="$emit(\'ok\')">ok</button></div>',
        },
        'a-form': { template: '<div><slot /></div>' },
        'a-form-item': { template: '<div><slot /></div>' },
        'a-textarea': {
          props: ['value'],
          emits: ['update:value'],
          template: '<textarea :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
        },
      },
    },
  })
}

describe('TodoActionCenterView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    applySession()
    vi.mocked(todoApi.list).mockResolvedValue(todoPage([approval], 1, 41))
    vi.mocked(todoApi.counts).mockResolvedValue({
      openCount: 4,
      taskCount: 2,
      approvalCount: 2,
      todayCount: 1,
      overdueCount: 1,
    })
    vi.mocked(todoApi.detail).mockResolvedValue({ status: 'LIVE', todo: approval })
    vi.mocked(todoApi.refresh).mockResolvedValue({
      discovered: 7, created: 1, updated: 2, closed: 3,
    })
  })

  it('loads default filters and changes category, time and stable page explicitly', async () => {
    const wrapper = render()
    await flushPromises()

    expect(todoApi.list).toHaveBeenCalledWith('10', {
      category: 'ALL', state: 'OPEN', time: 'ALL', page: 1, size: 20,
    })
    await wrapper.get('.todo-category-filter').setValue('APPROVAL')
    await flushPromises()
    await wrapper.get('.todo-time-filter').setValue('OVERDUE')
    await flushPromises()
    await wrapper.get('.page-two').trigger('click')
    await flushPromises()
    expect(vi.mocked(todoApi.list).mock.calls.at(-1)).toEqual(['10', {
      category: 'APPROVAL', state: 'OPEN', time: 'OVERDUE', page: 2, size: 20,
    }])
  })

  it('filters and renders reminder/CC cards using only safe Todo projections', async () => {
    const reminderWithUnprojectedBody = {
      ...reminder,
      body: 'RAW_EVENT_BODY_MUST_NOT_RENDER',
      templateVariables: { secret: 'TEMPLATE_SECRET_MUST_NOT_RENDER' },
    }
    vi.mocked(todoApi.list).mockResolvedValue(todoPage([reminderWithUnprojectedBody, cc]))
    vi.mocked(todoApi.detail).mockResolvedValue({ status: 'LIVE', todo: reminder })
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('.todo-filter-reminder').attributes('value')).toBe('REMINDER')
    expect(wrapper.get('.todo-filter-cc').attributes('value')).toBe('CC')
    expect(wrapper.get('.todo-card-reminder').text()).toContain('Release task reminder')
    expect(wrapper.get('.todo-card-reminder').text()).toContain('提醒')
    expect(wrapper.get('.todo-card-reminder').text()).toContain('创建')
    expect(wrapper.get('.todo-card-cc').text()).toContain('Flow instance copied')
    expect(wrapper.get('.todo-card-cc').text()).toContain('抄送')
    expect(wrapper.text()).not.toContain('RAW_EVENT_BODY_MUST_NOT_RENDER')
    expect(wrapper.text()).not.toContain('TEMPLATE_SECRET_MUST_NOT_RENDER')

    await wrapper.get('.todo-category-filter').setValue('REMINDER')
    await flushPromises()
    expect(vi.mocked(todoApi.list).mock.calls.at(-1)?.[1]).toMatchObject({ category: 'REMINDER' })
    await wrapper.get('.todo-category-filter').setValue('CC')
    await flushPromises()
    expect(vi.mocked(todoApi.list).mock.calls.at(-1)?.[1]).toMatchObject({ category: 'CC' })

    await wrapper.get('.todo-card-reminder').trigger('click')
    await flushPromises()
    expect(wrapper.get('.todo-detail').text()).toContain('创建时间')
    await wrapper.get('.todo-open-source').trigger('click')
    expect(router.push).toHaveBeenCalledWith('/systems/10/tasks?taskId=501')
  })

  it('opens sanitized detail and submits represented approval with its comment', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.todo-card').trigger('click')
    await flushPromises()

    expect(todoApi.detail).toHaveBeenCalledWith('10', '101')
    expect(wrapper.get('.todo-detail').text()).toContain('FLOW_APPROVAL / 201')
    expect(wrapper.get('.todo-detail').text()).toContain('代表成员')
    await wrapper.get('.todo-open-source').trigger('click')
    expect(router.push).toHaveBeenCalledWith('/systems/10/flows?instanceId=201')

    await wrapper.get('.todo-action-APPROVE').trigger('click')
    vi.mocked(todoApi.action).mockResolvedValue({
      status: 'SUCCESS',
      replayed: false,
      todo: { ...approval, status: 'CLOSED' },
      sourceResult: { code: 'APPROVED', message: 'approved', sourceVersion: 9 },
    })
    await wrapper.get('.todo-action-comment').setValue('Looks good')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(todoApi.action).toHaveBeenCalledWith('10', '101', {
      action: 'APPROVE',
      comment: 'Looks good',
      reason: null,
      version: 3,
    })
    expect(wrapper.get('.todo-result').text()).toContain('APPROVED')
  })

  it('refreshes sources and preserves the current filter and page context', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.todo-status-filter').setValue('CLOSED')
    await flushPromises()
    await wrapper.get('.page-two').trigger('click')
    await flushPromises()
    await wrapper.get('.todo-refresh').trigger('click')
    await flushPromises()

    expect(todoApi.refresh).toHaveBeenCalledWith('10')
    expect(vi.mocked(todoApi.list).mock.calls.at(-1)).toEqual(['10', {
      category: 'ALL', state: 'CLOSED', time: 'ALL', page: 2, size: 20,
    }])
    expect(wrapper.get('.todo-result').text()).toContain('发现 7，新增 1，更新 2，关闭 3')
  })

  it('delegates task completion with the current Todo CAS version', async () => {
    const task: TodoItem = {
      ...approval,
      id: '102',
      sourceType: 'WORK_TASK',
      sourceId: '302',
      actionScope: 'TASK_COMPLETE',
      category: 'TASK',
      title: 'Complete release task',
      representedMemberId: null,
      availableActions: ['COMPLETE'],
      version: 5,
    }
    vi.mocked(todoApi.list).mockResolvedValue(todoPage([task]))
    vi.mocked(todoApi.detail).mockResolvedValue({ status: 'LIVE', todo: task })
    vi.mocked(todoApi.action).mockResolvedValue({
      status: 'SUCCESS',
      replayed: false,
      todo: { ...task, status: 'CLOSED' },
      sourceResult: { code: 'COMPLETED', message: 'task completed', sourceVersion: 9 },
    })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.todo-card').trigger('click')
    await flushPromises()
    await wrapper.get('.todo-action-COMPLETE').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(todoApi.action).toHaveBeenCalledWith('10', '102', {
      version: 5,
      action: 'COMPLETE',
      comment: null,
      reason: null,
    })
    expect(wrapper.get('.todo-result').text()).toContain('COMPLETED')
  })

  it('marks an Event Todo read once, protects loading, and refreshes current context', async () => {
    vi.mocked(todoApi.list).mockResolvedValue(todoPage([reminder]))
    vi.mocked(todoApi.detail).mockResolvedValue({ status: 'LIVE', todo: reminder })
    let resolveAction!: (result: TodoActionResult) => void
    vi.mocked(todoApi.action).mockReturnValue(new Promise<TodoActionResult>((resolve) => {
      resolveAction = resolve
    }))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.todo-card-reminder').trigger('click')
    await flushPromises()
    await wrapper.get('.todo-action-mark-read').trigger('click')

    await wrapper.get('.modal-ok').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    expect(todoApi.action).toHaveBeenCalledTimes(1)
    expect(todoApi.action).toHaveBeenCalledWith('10', '102', {
      version: 4,
      action: 'MARK_READ',
    })
    expect(wrapper.get('.todo-action-mark-read').attributes('disabled')).toBeDefined()

    resolveAction({
      status: 'SUCCESS',
      replayed: false,
      todo: { ...reminder, status: 'CLOSED', closeReason: 'SOURCE_COMPLETED', version: 5 },
      sourceResult: { code: 'MARKED_READ', message: 'message marked read', sourceVersion: 7 },
    })
    await flushPromises()
    expect(wrapper.find('.action-modal').exists()).toBe(false)
    expect(wrapper.get('.todo-detail').text()).toContain('CLOSED')
    expect(wrapper.get('.todo-result').text()).toContain('MARKED_READ')
    expect(todoApi.list).toHaveBeenCalledTimes(2)
    expect(todoApi.counts).toHaveBeenCalledTimes(2)
  })

  it('keeps mark-read stale results bounded and available for retry', async () => {
    vi.mocked(todoApi.list).mockResolvedValue(todoPage([cc]))
    vi.mocked(todoApi.detail).mockResolvedValue({ status: 'LIVE', todo: cc })
    vi.mocked(todoApi.action).mockResolvedValue({
      status: 'STALE',
      replayed: false,
      todo: cc,
      sourceResult: { code: 'EVENT_MESSAGE_STALE', message: 'message changed', sourceVersion: 8 },
    })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.todo-card-cc').trigger('click')
    await flushPromises()
    await wrapper.get('.todo-action-mark-read').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(wrapper.get('.todo-error').text()).toContain('待办已失效或来源状态已变化')
    expect(wrapper.get('.todo-error').text()).toContain('message changed')
    expect(wrapper.find('.action-modal').exists()).toBe(true)
    expect(todoApi.list).toHaveBeenCalledTimes(2)
  })

  it('keeps non-live detail results explicit without losing list context', async () => {
    vi.mocked(todoApi.detail).mockResolvedValueOnce({
      status: 'STALE', todo: { ...approval, status: 'CLOSED', closeReason: 'SOURCE_STALE' },
    })
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.todo-card').trigger('click')
    await flushPromises()
    expect(wrapper.get('.todo-error').text()).toContain('待办已失效或来源状态已变化')

    vi.mocked(todoApi.detail).mockResolvedValueOnce({ status: 'DENIED', todo: approval })
    await wrapper.get('.todo-card').trigger('click')
    await flushPromises()
    expect(wrapper.get('.todo-error').text()).toContain('无权查看或处理该待办')
    expect(vi.mocked(todoApi.list).mock.calls.at(-1)?.[1]).toMatchObject({
      category: 'ALL', state: 'OPEN', time: 'ALL', page: 1,
    })
  })

  it('keeps stale, denied, conflict and general action failures explicit', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.todo-card').trigger('click')
    await flushPromises()
    await wrapper.get('.todo-action-REJECT').trigger('click')
    await wrapper.get('.modal-ok').trigger('click')
    expect(todoApi.action).not.toHaveBeenCalled()
    expect(wrapper.get('.todo-action-validation').text()).toContain('至少需要 1 个字符')
    await wrapper.get('.todo-action-reason').setValue('Not enough evidence')

    const outcomes = [
      ['STALE', '待办已失效或来源状态已变化'],
      ['DENIED', '无权查看或处理该待办'],
      ['CONFLICT', '待办状态冲突'],
      ['IN_PROGRESS', '相同待办动作正在处理中'],
      ['FAILED', '待办动作执行失败'],
    ] as const
    for (const [status, expected] of outcomes) {
      vi.mocked(todoApi.action).mockResolvedValueOnce({
        status,
        replayed: false,
        todo: approval,
        sourceResult: { code: `SOURCE_${status}`, message: 'source result', sourceVersion: 8 },
      })
      await wrapper.get('.modal-ok').trigger('click')
      await flushPromises()
      expect(wrapper.get('.todo-error').text()).toContain(expected)
    }
    vi.mocked(todoApi.action).mockRejectedValueOnce(error(
      409, 'TODO_VERSION_CONFLICT', 'version changed',
    ))
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(wrapper.get('.todo-error').text()).toContain('待办状态冲突')
    vi.mocked(todoApi.action).mockRejectedValueOnce(new Error('gateway unavailable'))
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(wrapper.get('.todo-error').text()).toContain('待办动作执行失败：gateway unavailable')
    expect(vi.mocked(todoApi.list).mock.calls.at(-1)?.[1]).toMatchObject({
      category: 'ALL', state: 'OPEN', time: 'ALL', page: 1,
    })
  })

  it('shows an explicit empty state and resets tenant-scoped page context', async () => {
    vi.mocked(todoApi.list).mockResolvedValue(todoPage([]))
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('当前筛选条件下没有待办')

    applySession('21')
    await flushPromises()
    expect(vi.mocked(todoApi.list).mock.calls.at(-1)).toEqual(['10', {
      category: 'ALL', state: 'OPEN', time: 'ALL', page: 1, size: 20,
    }])
  })
})
