import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiRequestError } from '@/services/api'
import { platformTaskApi } from '@/services/platformTask'
import { useSessionStore } from '@/stores/session'
import type { PlatformTask, PlatformTaskListQuery, PlatformTaskPage } from '@/types/platformTask'
import PlatformWorkbenchView from '@/views/platform/PlatformWorkbenchView.vue'

const router = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => router }))
vi.mock('@/services/platformTask', () => ({
  platformTaskApi: {
    list: vi.fn(),
    complete: vi.fn(),
    reopen: vi.fn(),
    cancel: vi.fn(),
  },
}))

const openTask: PlatformTask = {
  taskId: 'task-open',
  title: '核对平台授权',
  description: '确认当前账号的平台权限',
  dueAt: '2099-08-06T02:00:00Z',
  priority: 'HIGH',
  status: 'OPEN',
  source: 'AGENT',
  createdAt: '2026-08-05T00:00:00Z',
  updatedAt: '2026-08-05T00:00:00Z',
  completedAt: null,
  cancelledAt: null,
  version: 0,
}
const completedTask: PlatformTask = {
  ...openTask,
  taskId: 'task-completed',
  title: '归档平台检查',
  status: 'COMPLETED',
  completedAt: '2026-08-05T01:00:00Z',
  updatedAt: '2026-08-05T01:00:00Z',
  version: 4,
}
const cancelledTask: PlatformTask = {
  ...openTask,
  taskId: 'task-cancelled',
  title: '取消过期跟进',
  status: 'CANCELLED',
  cancelledAt: '2026-08-05T02:00:00Z',
  updatedAt: '2026-08-05T02:00:00Z',
  version: 2,
}

function page(items: PlatformTask[], current = 1, total = items.length): PlatformTaskPage {
  return { items, page: current, size: 20, total }
}

function applySession(permissions: string[]) {
  useSessionStore().applyAuth({
    account: { id: 'account-1', username: 'member', displayName: 'Member' },
    context: {
      type: 'PLATFORM',
      account: { id: 'account-1', username: 'member', displayName: 'Member' },
      permissionVersion: '1',
      permissions,
      shells: ['PLATFORM_RUNTIME'],
    },
    systems: [],
  })
}

function render() {
  return mount(PlatformWorkbenchView, { global: { stubs: {
    ArrowRight: true,
    Building2: true,
    CheckCircle2: true,
    CircleAlert: true,
    RefreshCw: true,
    RotateCcw: true,
    XCircle: true,
    'a-alert': { props: ['message'], template: '<div class="alert-stub">{{ message }}<slot name="message" /><slot name="description" /></div>' },
    'a-spin': { template: '<span class="spin-stub">loading</span>' },
    'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
    'a-tag': { template: '<span><slot /></span>' },
    'a-button': {
      inheritAttrs: false,
      props: ['disabled', 'loading'],
      emits: ['click'],
      template: '<button v-bind="$attrs" :disabled="disabled" :data-loading="loading" @click="$emit(\'click\')"><slot /></button>',
    },
  } } })
}

function ownerError(code: string) {
  return new ApiRequestError(409, {
    code,
    message: '任务版本或状态已变化',
    data: null,
    requestId: 'request-conflict',
    traceId: 'trace-conflict',
    errors: [],
  })
}

describe('PlatformWorkbenchView personal tasks', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    router.push.mockResolvedValue(undefined)
    vi.spyOn(useSessionStore(), 'loadSystems').mockResolvedValue(undefined)
    vi.mocked(platformTaskApi.list).mockResolvedValue(page([openTask, completedTask, cancelledTask]))
    vi.mocked(platformTaskApi.complete).mockResolvedValue(completedTask)
    vi.mocked(platformTaskApi.reopen).mockResolvedValue(openTask)
    vi.mocked(platformTaskApi.cancel).mockResolvedValue(cancelledTask)
  })

  it('hides the whole task section without read permission', async () => {
    applySession([])
    const wrapper = render()
    await flushPromises()

    expect(wrapper.find('.platform-personal-tasks').exists()).toBe(false)
    expect(platformTaskApi.list).not.toHaveBeenCalled()
  })

  it('renders safe task fields but no actions or native create control for a read-only account', async () => {
    applySession(['platform.task.read'])
    const wrapper = render()
    await flushPromises()

    expect(platformTaskApi.list).toHaveBeenCalledWith({ status: 'ALL', page: 1, size: 20 })
    expect(wrapper.get('.platform-personal-tasks').text()).toContain('核对平台授权')
    expect(wrapper.get('.platform-personal-tasks').text()).toContain('确认当前账号的平台权限')
    expect(wrapper.get('.platform-personal-tasks').text()).toContain('AGENT')
    expect(wrapper.get('.platform-personal-tasks').text()).toContain('HIGH')
    expect(wrapper.get('.platform-personal-tasks').text()).toContain('OPEN')
    expect(wrapper.find('.platform-task-actions').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('创建任务')
  })

  it('shows loading, empty and owner read errors explicitly', async () => {
    applySession(['platform.task.read'])
    let resolveList!: (result: PlatformTaskPage) => void
    vi.mocked(platformTaskApi.list).mockReturnValueOnce(new Promise(resolve => { resolveList = resolve }))
    const wrapper = render()
    await flushPromises()
    expect(wrapper.find('.platform-task-loading').exists()).toBe(true)

    resolveList(page([]))
    await flushPromises()
    expect(wrapper.get('.platform-task-empty').text()).toContain('没有个人任务')

    vi.mocked(platformTaskApi.list).mockRejectedValueOnce(ownerError('PLATFORM_TASK_READ_FAILED'))
    await wrapper.get('.platform-task-refresh').trigger('click')
    await flushPromises()
    expect(wrapper.get('.platform-task-error').text()).toContain('PLATFORM_TASK_READ_FAILED')
  })

  it('filters and pages with the current stable query', async () => {
    applySession(['platform.task.read'])
    vi.mocked(platformTaskApi.list).mockImplementation(async (query: PlatformTaskListQuery = {}) =>
      page([openTask], query.page ?? 1, 41))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.platform-task-next').trigger('click')
    await flushPromises()
    expect(vi.mocked(platformTaskApi.list).mock.calls.at(-1)?.[0]).toEqual({ status: 'ALL', page: 2, size: 20 })

    await wrapper.get('.platform-task-status-filter').setValue('COMPLETED')
    await flushPromises()
    expect(vi.mocked(platformTaskApi.list).mock.calls.at(-1)?.[0]).toEqual({ status: 'COMPLETED', page: 1, size: 20 })
  })

  it('offers only state-valid actions and refreshes the same page after success', async () => {
    applySession(['platform.task.read', 'platform.task.manage'])
    vi.mocked(platformTaskApi.list)
      .mockResolvedValueOnce(page([openTask, completedTask, cancelledTask]))
      .mockResolvedValueOnce(page([{ ...openTask, status: 'COMPLETED', version: 4, completedAt: '2026-08-05T03:00:00Z' }, completedTask, cancelledTask]))
    const wrapper = render()
    await flushPromises()

    expect(wrapper.findAll('.platform-task-complete')).toHaveLength(1)
    expect(wrapper.findAll('.platform-task-cancel')).toHaveLength(1)
    expect(wrapper.findAll('.platform-task-reopen')).toHaveLength(2)
    await wrapper.get('.platform-task-complete').trigger('click')
    await flushPromises()

    expect(platformTaskApi.complete).toHaveBeenCalledWith('task-open', { version: 0 })
    expect(platformTaskApi.list).toHaveBeenCalledTimes(2)
    expect(vi.mocked(platformTaskApi.list).mock.calls[1]?.[0]).toEqual({ status: 'ALL', page: 1, size: 20 })
    expect(wrapper.findAll('.platform-task-complete')).toHaveLength(0)
    expect(wrapper.findAll('.platform-task-reopen')).toHaveLength(3)
  })

  it('keeps the task unchanged when a lifecycle request fails', async () => {
    applySession(['platform.task.read', 'platform.task.manage'])
    let rejectComplete!: (reason: unknown) => void
    vi.mocked(platformTaskApi.complete).mockReturnValueOnce(new Promise((_resolve, reject) => { rejectComplete = reject }))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.platform-task-complete').trigger('click')
    expect(wrapper.get('.platform-task-card').text()).toContain('OPEN')
    expect(platformTaskApi.list).toHaveBeenCalledTimes(1)
    rejectComplete(ownerError('PLATFORM_TASK_VERSION_CONFLICT'))
    await flushPromises()

    expect(wrapper.get('.platform-task-error').text()).toContain('PLATFORM_TASK_VERSION_CONFLICT')
    expect(wrapper.get('.platform-task-card').text()).toContain('OPEN')
    expect(platformTaskApi.list).toHaveBeenCalledTimes(1)
  })
})
