import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { platformTaskApi } from '@/services/platformTask'
import { platformWorkApi } from '@/services/platformWork'
import { useSessionStore } from '@/stores/session'
import PlatformWorkView from '@/views/platform/PlatformWorkView.vue'

vi.mock('@/services/platformWork', () => ({ platformWorkApi: {
  overview: vi.fn(), projects: vi.fn(), tasks: vi.fn(), reports: vi.fn(),
  createProject: vi.fn(), createTask: vi.fn(), saveReport: vi.fn(), submitReport: vi.fn(),
} }))
vi.mock('@/services/platformTask', () => ({ platformTaskApi: { complete: vi.fn(), reopen: vi.fn() } }))

const project = { id: '11', code: 'RELEASE_2026', name: '平台发布', status: 'ACTIVE' as const, startDate: '2026-08-01', dueDate: '2026-08-31', taskCount: 2, completedTaskCount: 1, updatedAt: '2026-08-07T01:00:00Z', version: 0 }
const task = { taskId: '21', kind: 'PROJECT' as const, projectId: '11', projectName: '平台发布', title: '验证发布门禁', description: '执行回归', dueAt: '2099-08-08T01:00:00Z', priority: 'HIGH' as const, status: 'OPEN' as const, labels: ['发布'], createdAt: '2026-08-07T01:00:00Z', updatedAt: '2026-08-07T01:00:00Z', version: 0 }
const report = { id: '31', reportDate: '2026-08-07', status: 'DRAFT' as const, completed: '完成接口', plan: '执行回归', risks: null, projectId: '11', projectName: '平台发布', updatedAt: '2026-08-07T02:00:00Z', version: 0 }

const stubs = {
  'a-button': { inheritAttrs: false, props: ['loading', 'disabled'], emits: ['click'], template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
  'a-alert': { props: ['message'], template: '<div class="alert-stub">{{ message }}</div>' },
  'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
  'a-tag': { template: '<span><slot /></span>' },
  'a-modal': { props: ['open', 'title'], emits: ['update:open', 'ok'], template: '<section v-if="open" class="modal-stub"><h3>{{ title }}</h3><slot /><button class="modal-ok" @click="$emit(\'ok\')">确定</button></section>' },
}

function session() {
  useSessionStore().applyAuth({ account: { id: '7', username: 'member', displayName: 'Member' }, context: { type: 'PLATFORM', account: { id: '7', username: 'member', displayName: 'Member' }, permissionVersion: '1', permissions: ['platform.work.read', 'platform.work.manage', 'platform.task.manage'], shells: ['PLATFORM_RUNTIME'] }, systems: [] })
}

describe('PlatformWorkView', () => {
  beforeEach(() => {
    setActivePinia(createPinia()); session(); vi.clearAllMocks()
    vi.mocked(platformWorkApi.overview).mockResolvedValue({ activeProjects: 1, openTasks: 1, overdueTasks: 0, todayTasks: 1, todayReportSubmitted: false })
    vi.mocked(platformWorkApi.projects).mockResolvedValue([project])
    vi.mocked(platformWorkApi.tasks).mockResolvedValue({ items: [task], page: 1, size: 100, total: 1 })
    vi.mocked(platformWorkApi.reports).mockResolvedValue([report])
    vi.mocked(platformTaskApi.complete).mockResolvedValue({ ...task, source: 'WORK', status: 'COMPLETED', completedAt: '2026-08-07T03:00:00Z', cancelledAt: null })
    vi.mocked(platformWorkApi.createTask).mockResolvedValue(task)
    vi.mocked(platformWorkApi.submitReport).mockResolvedValue({ ...report, status: 'SUBMITTED', version: 1 })
  })

  it('loads the account-scoped project/task/report aggregate and switches one task set between views', async () => {
    const wrapper = mount(PlatformWorkView, { global: { stubs } })
    await flushPromises()
    expect(platformWorkApi.tasks).toHaveBeenCalledWith({ page: 1, size: 100 })
    expect(wrapper.text()).toContain('平台发布')
    expect(wrapper.text()).toContain('验证发布门禁')
    expect(wrapper.text()).toContain('今日任务1')

    expect(wrapper.findAll('.work-tools > .view-mode')).toHaveLength(3)
    expect(wrapper.get('.platform-work-task-create').classes()).not.toContain('view-mode')

    await wrapper.get('.work-tools button:nth-of-type(2)').trigger('click')
    expect(wrapper.get('.task-board').text()).toContain('验证发布门禁')
    await wrapper.get('.work-tools button:nth-of-type(3)').trigger('click')
    expect(wrapper.get('.task-calendar').text()).toContain('验证发布门禁')
  })

  it('reuses the native platform task lifecycle and reloads after read-back', async () => {
    const wrapper = mount(PlatformWorkView, { global: { stubs } })
    await flushPromises()
    await wrapper.findAll('.work-tools button')[0]!.trigger('click')
    await wrapper.find('table tbody tr button').trigger('click')
    await flushPromises()
    expect(platformTaskApi.complete).toHaveBeenCalledWith('21', { version: 0 })
    expect(platformWorkApi.overview).toHaveBeenCalledTimes(2)
  })

  it('submits a report draft with its real optimistic version', async () => {
    const wrapper = mount(PlatformWorkView, { global: { stubs } })
    await flushPromises()
    await wrapper.findAll('.work-sections button')[2]!.trigger('click')
    await wrapper.find('table tbody tr button').trigger('click')
    await flushPromises()
    expect(platformWorkApi.submitReport).toHaveBeenCalledWith('31', 0)
  })
})
