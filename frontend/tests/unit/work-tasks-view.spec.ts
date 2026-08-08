import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiRequestError } from '@/services/api'
import { workApi } from '@/services/work'
import { flowApi } from '@/services/flow'
import { useSessionStore } from '@/stores/session'
import type {
  WorkProject,
  WorkProjectMember,
  WorkTask,
  WorkTaskListQuery,
  WorkTaskPage,
} from '@/types/work'
import WorkTasksView from '@/views/system/WorkTasksView.vue'

const route = vi.hoisted(() => ({
  params: { systemId: '10' },
  query: {} as Record<string, string>,
  fullPath: '/systems/10/tasks',
}))

vi.mock('vue-router', () => ({
  useRoute: () => route,
}))

vi.mock('@/services/work', () => ({
  workApi: {
    list: vi.fn(),
    create: vi.fn(),
    assign: vi.fn(),
    complete: vi.fn(),
    reopen: vi.fn(),
    task: vi.fn(),
    update: vi.fn(),
    retryTaskReminder: vi.fn(),
    listProjects: vi.fn(),
    createProject: vi.fn(),
    project: vi.fn(),
    updateProject: vi.fn(),
    archiveProject: vi.fn(),
    reopenProject: vi.fn(),
    listProjectMembers: vi.fn(),
    addProjectMember: vi.fn(),
    updateProjectMember: vi.fn(),
    removeProjectMember: vi.fn(),
  },
}))

vi.mock('@/services/flow', () => ({
  flowApi: {
    listApprovalTasks: vi.fn(),
  },
}))

const openTask: WorkTask = {
  id: '101',
  systemId: '10',
  tenantId: '20',
  creatorMemberId: '100',
  assigneeMemberId: '200',
  title: 'Review release',
  projectId: null,
  description: null,
  dueAt: null,
  status: 'OPEN',
  createdAt: '2026-07-25T08:00:00Z',
  updatedAt: '2026-07-25T09:00:00Z',
  version: 1,
}

const activeProject: WorkProject = {
  id: '501',
  systemId: '10',
  tenantId: '20',
  creatorMemberId: '200',
  title: 'Release project',
  description: 'Ship the next release',
  status: 'ACTIVE',
  createdAt: '2026-08-04T01:00:00Z',
  updatedAt: '2026-08-04T02:00:00Z',
  version: 3,
}

const projectMembers: WorkProjectMember[] = [{
  projectId: '501',
  memberId: '200',
  role: 'OWNER',
  status: 'ACTIVE',
  joinedAt: '2026-08-04T01:00:00Z',
  updatedAt: '2026-08-04T01:00:00Z',
  version: 1,
}, {
  projectId: '501',
  memberId: '300',
  role: 'MEMBER',
  status: 'ACTIVE',
  joinedAt: '2026-08-04T01:01:00Z',
  updatedAt: '2026-08-04T01:01:00Z',
  version: 2,
}]

function page(items: WorkTask[], current = 1, total = items.length): WorkTaskPage {
  return { items, page: current, size: 20, total }
}

function applySession(
  tenantId: string,
  permissions = ['work.task.access'],
) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: {
      type: 'SYSTEM',
      account: { id: '1', username: 'member', displayName: 'Member' },
      systemId: '10',
      tenantId,
      memberId: '200',
      permissionVersion: '1',
      permissions,
      shells: ['SYSTEM_RUNTIME'],
    },
    systems: [],
  })
}

function render() {
  return mount(WorkTasksView, {
    global: {
      stubs: {
        WorkDailyReports: { template: '<div class="daily-report-mode-stub">日报模式</div>' },
        MemberPicker: {
          props: ['value'],
          emits: ['update:value'],
          template: '<input class="member-picker" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
        },
        'a-alert': {
          props: ['message'],
          template: '<div>{{ message }}<slot /></div>',
        },
        'a-spin': { template: '<div><slot /></div>' },
        'a-empty': {
          props: ['description'],
          template: '<div>{{ description }}</div>',
        },
        'a-tag': { template: '<span><slot /></span>' },
        'a-button': {
          props: ['disabled'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
        },
        'a-input': {
          props: ['value'],
          emits: ['update:value', 'pressEnter'],
          template: '<input :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
        },
        'a-textarea': {
          props: ['value'],
          emits: ['update:value'],
          template: '<textarea :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
        },
        'a-select': {
          inheritAttrs: false,
          props: ['value'],
          emits: ['update:value', 'change'],
          template: `
            <button
              v-bind="$attrs"
              @click="
                $emit('update:value', $attrs['aria-label'] === '任务状态' ? 'COMPLETED' : 'CREATED_BY_ME');
                $emit('change', $attrs['aria-label'] === '任务状态' ? 'COMPLETED' : 'CREATED_BY_ME')
              "
            >{{ value }}</button>
          `,
        },
        'a-select-option': { template: '<span><slot /></span>' },
        'a-pagination': {
          props: ['current', 'total'],
          emits: ['change', 'showSizeChange'],
          template: '<button class="page-two" @click="$emit(\'change\', 2, 20)">{{ current }}/{{ total }}</button>',
        },
        'a-modal': {
          props: ['open'],
          emits: ['ok', 'cancel'],
          template: '<div v-if="open"><slot /><button class="modal-ok" @click="$emit(\'ok\')">ok</button></div>',
        },
        'a-form': { template: '<div><slot /></div>' },
        'a-form-item': { template: '<div><slot /></div>' },
      },
    },
  })
}

describe('WorkTasksView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    route.params.systemId = '10'
    route.query = {}
    route.fullPath = '/systems/10/tasks'
    applySession('20')
    vi.mocked(workApi.list).mockResolvedValue(page([openTask]))
    vi.mocked(workApi.listProjects).mockResolvedValue({
      items: [], page: 1, size: 20, total: 0,
    })
    vi.mocked(workApi.complete).mockResolvedValue({
      ...openTask,
      status: 'COMPLETED',
      version: 2,
    })
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue({
      items: [],
      page: 1,
      size: 20,
      total: 0,
    })
  })

  it('loads the frozen default participating page', async () => {
    const wrapper = render()
    await flushPromises()

    expect(workApi.list).toHaveBeenCalledWith('10', {
      keyword: '',
      status: 'ALL',
      role: 'PARTICIPATING',
      page: 1,
      size: 20,
    })
    expect(wrapper.text()).toContain('Review release')
  })

  it('defaults report-only members to reports without task, project or approval requests', async () => {
    applySession('20', ['work.report.access'])
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('.daily-report-mode-stub').text()).toContain('日报模式')
    expect(wrapper.find('.work-mode-tasks').exists()).toBe(false)
    expect(workApi.list).not.toHaveBeenCalled()
    expect(workApi.listProjects).not.toHaveBeenCalled()
    expect(flowApi.listApprovalTasks).not.toHaveBeenCalled()
  })

  it('initializes supported Work drill filters from the operations route', async () => {
    applySession('20', ['work.task.access', 'work.task.manage'])
    vi.mocked(workApi.project).mockResolvedValue(activeProject)
    vi.mocked(workApi.listProjectMembers).mockResolvedValue(projectMembers)
    route.query = {
      projectId: '501',
      status: 'OPEN',
      role: 'ALL',
      time: 'RANGE',
      from: '2026-07-26',
      to: '2026-08-02',
      assigneeMemberId: '300',
    }
    route.fullPath = '/systems/10/tasks?projectId=501&status=OPEN&role=ALL&time=RANGE'
    render()
    await flushPromises()

    expect(vi.mocked(workApi.list).mock.calls.at(-1)).toEqual(['10', {
      keyword: '',
      status: 'OPEN',
      role: 'ALL',
      projectId: '501',
      dueFrom: '2026-07-26T00:00:00.000Z',
      dueBefore: '2026-08-02T00:00:00.000Z',
      assigneeMemberId: '300',
      page: 1,
      size: 20,
    }])
    expect(workApi.project).toHaveBeenCalledWith('10', '501')
    expect(workApi.listProjectMembers).toHaveBeenCalledWith('10', '501')
  })

  it('switches task and daily-report modes without discarding task facts', async () => {
    applySession('20', ['work.task.access', 'work.report.access'])
    const wrapper = render()
    await flushPromises()
    const calls = vi.mocked(workApi.list).mock.calls.length

    await wrapper.get('.work-mode-reports').trigger('click')
    expect(wrapper.get('.daily-report-mode-stub').text()).toContain('日报模式')
    await wrapper.get('.work-mode-tasks').trigger('click')
    expect(wrapper.text()).toContain('Review release')
    expect(workApi.list).toHaveBeenCalledTimes(calls)
  })

  it('resets filters and page when the authenticated tenant changes', async () => {
    vi.mocked(workApi.list).mockResolvedValue(page([openTask], 1, 21))
    const wrapper = render()
    await flushPromises()

    const filters = wrapper.findAll('.work-filter-select')
    await filters[0]?.trigger('click')
    await filters[1]?.trigger('click')
    await flushPromises()
    await wrapper.get('.page-two').trigger('click')
    await flushPromises()
    expect(vi.mocked(workApi.list).mock.calls.at(-1)).toEqual([
      '10',
      {
        keyword: '',
        status: 'COMPLETED',
        role: 'CREATED_BY_ME',
        page: 2,
        size: 20,
      },
    ])

    applySession('21')
    await flushPromises()
    expect(vi.mocked(workApi.list).mock.calls.at(-1)).toEqual([
      '10',
      {
        keyword: '',
        status: 'ALL',
        role: 'PARTICIPATING',
        page: 1,
        size: 20,
      },
    ])
  })

  it('reloads and clamps after a mutation removes the last item on a page', async () => {
    let completed = false
    vi.mocked(workApi.list).mockImplementation(
      async (_systemId: string, query: WorkTaskListQuery = {}) => {
        if (query.page === 2) {
          return completed ? page([], 2, 20) : page([openTask], 2, 21)
        }
        return page([openTask], 1, 20)
      },
    )
    vi.mocked(workApi.complete).mockImplementation(async () => {
      completed = true
      return { ...openTask, status: 'COMPLETED', version: 2 }
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.page-two').trigger('click')
    await flushPromises()
    await wrapper.get('.work-task-transition').trigger('click')
    await flushPromises()

    expect(workApi.complete).toHaveBeenCalledWith('10', '101')
    expect(vi.mocked(workApi.list).mock.calls.slice(-2)).toEqual([
      ['10', {
        keyword: '',
        status: 'ALL',
        role: 'PARTICIPATING',
        page: 2,
        size: 20,
      }],
      ['10', {
        keyword: '',
        status: 'ALL',
        role: 'PARTICIPATING',
        page: 1,
        size: 20,
      }],
    ])
  })

  it('creates, selects, revises, archives and reopens projects with owner member controls', async () => {
    applySession('20', [
      'work.task.access',
      'work.task.create',
      'work.project.manage',
    ])
    vi.mocked(workApi.listProjects).mockResolvedValue({
      items: [activeProject], page: 1, size: 20, total: 1,
    })
    vi.mocked(workApi.project).mockResolvedValue(activeProject)
    vi.mocked(workApi.listProjectMembers).mockResolvedValue(projectMembers)
    vi.mocked(workApi.createProject).mockResolvedValue({
      ...activeProject,
      id: '502',
      title: 'Created project',
      description: 'Created from Work',
      version: 1,
    })
    vi.mocked(workApi.updateProject).mockResolvedValue({
      ...activeProject,
      title: 'Release train',
      description: 'Updated project scope',
      version: 4,
    })
    vi.mocked(workApi.archiveProject).mockResolvedValue({
      ...activeProject,
      status: 'ARCHIVED',
      version: 4,
    })
    vi.mocked(workApi.reopenProject).mockResolvedValue({
      ...activeProject,
      version: 5,
    })
    vi.mocked(workApi.addProjectMember).mockResolvedValue(projectMembers[1]!)
    vi.mocked(workApi.updateProjectMember).mockResolvedValue({
      ...projectMembers[1]!, role: 'OWNER', version: 3,
    })
    vi.mocked(workApi.removeProjectMember).mockResolvedValue({
      ...projectMembers[1]!, status: 'REMOVED', version: 3,
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.work-project-create').trigger('click')
    await wrapper.get('.work-project-title').setValue('Created project')
    await wrapper.get('.work-project-description').setValue('Created from Work')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(workApi.createProject).toHaveBeenCalledWith('10', {
      title: 'Created project',
      description: 'Created from Work',
    })

    await wrapper.get('.work-project-select').trigger('click')
    await flushPromises()
    expect(workApi.project).toHaveBeenCalledWith('10', '501')
    expect(workApi.listProjectMembers).toHaveBeenCalledWith('10', '501')
    expect(wrapper.findAll('.work-project-member')).toHaveLength(2)

    await wrapper.get('.work-project-edit').trigger('click')
    await wrapper.get('.work-project-edit-title').setValue('Release train')
    await wrapper.get('.work-project-edit-description').setValue('Updated project scope')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(workApi.updateProject).toHaveBeenCalledWith('10', '501', {
      title: 'Release train',
      description: 'Updated project scope',
      version: 3,
    })

    await wrapper.get('.work-project-transition').trigger('click')
    await flushPromises()
    expect(workApi.archiveProject).toHaveBeenCalledWith('10', '501', { version: 4 })
    await wrapper.get('.work-project-transition').trigger('click')
    await flushPromises()
    expect(workApi.reopenProject).toHaveBeenCalledWith('10', '501', { version: 4 })

    await wrapper.get('.work-project-member-add .member-picker').setValue('400')
    await wrapper.get('.work-project-member-role-select').setValue('OWNER')
    await wrapper.get('.work-project-member-add-submit').trigger('click')
    await flushPromises()
    expect(workApi.addProjectMember).toHaveBeenCalledWith('10', '501', {
      memberId: '400',
      role: 'OWNER',
    })

    const member300 = wrapper.findAll('.work-project-member')
      .find(member => member.text().includes('成员 300'))!
    await member300.get('.work-project-member-role').trigger('click')
    await flushPromises()
    expect(workApi.updateProjectMember).toHaveBeenCalledWith('10', '501', '300', {
      role: 'OWNER',
      version: 2,
    })
    await member300.get('.work-project-member-remove').trigger('click')
    await flushPromises()
    expect(workApi.removeProjectMember).toHaveBeenCalledWith('10', '501', '300', {
      version: 2,
    })
    const lastOwner = wrapper.findAll('.work-project-member')
      .find(member => member.text().includes('成员 200'))!
    expect(lastOwner.get('.work-project-member-remove').attributes('disabled')).toBeDefined()
  })

  it('edits shared task metadata and switches list, Kanban and calendar without refetching', async () => {
    applySession('20', ['work.task.access', 'work.task.create', 'work.task.manage'])
    const dueTask: WorkTask = {
      ...openTask,
      projectId: '501',
      description: 'Prepare release notes',
      dueAt: '2026-08-15T02:30:00Z',
      version: 7,
    }
    const completedTask: WorkTask = {
      ...openTask,
      id: '102',
      title: 'Publish release',
      status: 'COMPLETED',
      projectId: '501',
      description: null,
      dueAt: null,
      version: 4,
    }
    vi.mocked(workApi.list).mockResolvedValue(page([dueTask, completedTask]))
    vi.mocked(workApi.listProjects).mockResolvedValue({
      items: [activeProject], page: 1, size: 20, total: 1,
    })
    vi.mocked(workApi.project).mockResolvedValue(activeProject)
    vi.mocked(workApi.listProjectMembers).mockResolvedValue(projectMembers)
    vi.mocked(workApi.update).mockResolvedValue({
      ...dueTask,
      projectId: null,
      description: 'Updated notes',
      dueAt: '2026-08-16T03:45:00Z',
      version: 8,
    })
    vi.mocked(workApi.create).mockResolvedValue({
      ...openTask,
      id: '103',
      projectId: '501',
      description: 'New scheduled task',
      dueAt: '2026-08-20T01:00:00Z',
    })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.work-project-select').trigger('click')
    await flushPromises()
    expect(vi.mocked(workApi.list).mock.calls.at(-1)?.[1]).toEqual({
      keyword: '',
      status: 'ALL',
      role: 'PARTICIPATING',
      projectId: '501',
      page: 1,
      size: 20,
    })
    const requestsBeforeSwitch = vi.mocked(workApi.list).mock.calls.length
    expect(wrapper.findAll('.work-list-view .task-card').map(card => card.attributes('data-task-id')))
      .toEqual(['101', '102'])

    await wrapper.get('.work-view-kanban').trigger('click')
    expect(wrapper.findAll('.work-kanban-task').map(card => card.attributes('data-task-id')).sort())
      .toEqual(['101', '102'])
    expect(wrapper.findAll('.work-kanban-column')).toHaveLength(2)
    await wrapper.get('.work-view-calendar').trigger('click')
    expect(wrapper.findAll('.work-calendar-task').map(card => card.attributes('data-task-id')).sort())
      .toEqual(['101', '102'])
    expect(wrapper.find('[data-date="2026-08-15"]').exists()).toBe(true)
    expect(wrapper.find('[data-date="NO_DUE"]').exists()).toBe(true)
    expect(workApi.list).toHaveBeenCalledTimes(requestsBeforeSwitch)

    await wrapper.get('.work-view-list').trigger('click')
    await wrapper.findAll('.work-task-edit')[0]!.trigger('click')
    await wrapper.get('.work-task-edit-project').setValue('')
    await wrapper.get('.work-task-edit-description').setValue('Updated notes')
    await wrapper.get('.work-task-edit-due-at').setValue('2026-08-16T11:45')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(workApi.update).toHaveBeenCalledWith('10', '101', {
      title: 'Review release',
      projectId: null,
      description: 'Updated notes',
      dueAt: new Date('2026-08-16T11:45').toISOString(),
      version: 7,
    })

    await wrapper.get('.work-task-create').trigger('click')
    await wrapper.get('.work-task-create-project').setValue('501')
    await wrapper.get('.work-task-create-description').setValue('New scheduled task')
    await wrapper.get('.work-task-create-due-at').setValue('2026-08-20T09:00')
    const visibleModal = wrapper.findAll('.modal-ok').at(-1)!
    await wrapper.findAll('.member-picker').at(-1)!.setValue('300')
    await wrapper.findAll('input').find(input => input.attributes('maxlength') === '200')!
      .setValue('Schedule launch')
    await visibleModal.trigger('click')
    await flushPromises()
    expect(workApi.create).toHaveBeenCalledWith('10', {
      title: 'Schedule launch',
      assigneeMemberId: '300',
      projectId: '501',
      description: 'New scheduled task',
      dueAt: new Date('2026-08-20T09:00').toISOString(),
      reminderAt: null,
    })
  })

  it('validates reminder scheduling and supports explicit cancellation from task editing', async () => {
    applySession('20', ['work.task.access', 'work.task.create', 'work.task.manage'])
    const scheduledTask: WorkTask = {
      ...openTask,
      dueAt: '2099-08-20T09:00:00Z',
      reminderAt: '2099-08-19T09:00:00Z',
      reminder: {
        generation: 1,
        scheduledAt: '2099-08-19T09:00:00Z',
        status: 'PENDING',
        attemptCount: 0,
        sentAt: null,
        failureCode: null,
        version: 6,
      },
    }
    vi.mocked(workApi.list).mockResolvedValue(page([scheduledTask]))
    vi.mocked(workApi.update).mockResolvedValue({
      ...scheduledTask, reminderAt: null, reminder: null, version: 2,
    })
    vi.mocked(workApi.create).mockResolvedValue({ ...openTask, id: '104' })
    const wrapper = render()
    await flushPromises()

    await wrapper.get('.work-task-edit').trigger('click')
    await wrapper.get('.work-task-edit-reminder-at').setValue('2099-08-21T09:00')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(workApi.update).not.toHaveBeenCalled()
    expect(wrapper.get('.work-error').text()).toContain('提醒时间不得晚于截止时间')

    await wrapper.get('.work-task-edit-reminder-at').setValue('')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    expect(workApi.update).toHaveBeenCalledWith('10', '101', {
      title: 'Review release',
      projectId: null,
      description: null,
      dueAt: '2099-08-20T09:00:00.000Z',
      reminderAt: null,
      version: 1,
    })

    await wrapper.get('.work-task-create').trigger('click')
    await wrapper.findAll('.member-picker').at(-1)!.setValue('300')
    await wrapper.findAll('input').find(input => input.attributes('maxlength') === '200')!
      .setValue('Reminder validation')
    await wrapper.get('.work-task-create-due-at').setValue('2099-09-10T10:00')
    await wrapper.get('.work-task-create-reminder-at').setValue('2099-09-11T10:00')
    await wrapper.findAll('.modal-ok').at(-1)!.trigger('click')
    await flushPromises()
    expect(workApi.create).not.toHaveBeenCalled()
    expect(wrapper.get('.work-error').text()).toContain('提醒时间不得晚于截止时间')

    await wrapper.get('.work-task-create-reminder-at').setValue('2099-09-09T10:00')
    await wrapper.findAll('.modal-ok').at(-1)!.trigger('click')
    await flushPromises()
    expect(workApi.create).toHaveBeenCalledWith('10', {
      title: 'Reminder validation',
      assigneeMemberId: '300',
      projectId: null,
      description: null,
      dueAt: new Date('2099-09-10T10:00').toISOString(),
      reminderAt: new Date('2099-09-09T10:00').toISOString(),
    })
  })

  it('shows sanitized reminder states and gives managers explicit retry outcomes', async () => {
    applySession('20', ['work.task.access', 'work.task.manage'])
    const reminderTask = (
      id: string,
      status: 'PENDING' | 'SENT' | 'CANCELLED' | 'FAILED',
    ): WorkTask => ({
      ...openTask,
      id,
      title: `Reminder ${status}`,
      reminderAt: status === 'CANCELLED' ? null : '2099-08-19T09:00:00Z',
      reminder: {
        generation: 2,
        scheduledAt: '2099-08-19T09:00:00Z',
        status,
        attemptCount: status === 'FAILED' ? 3 : 0,
        sentAt: status === 'SENT' ? '2099-08-19T09:00:03Z' : null,
        failureCode: status === 'FAILED' ? 'DELIVERY_TIMEOUT' : null,
        version: status === 'FAILED' ? 9 : 2,
      },
    })
    const failedTask = reminderTask('204', 'FAILED')
    vi.mocked(workApi.list).mockResolvedValue(page([
      reminderTask('201', 'PENDING'),
      reminderTask('202', 'SENT'),
      reminderTask('203', 'CANCELLED'),
      failedTask,
    ]))
    vi.mocked(workApi.retryTaskReminder).mockResolvedValue({
      ...failedTask,
      reminder: { ...failedTask.reminder!, status: 'PENDING', version: 10 },
    })
    const wrapper = render()
    await flushPromises()

    expect(wrapper.text()).toContain('PENDING')
    expect(wrapper.text()).toContain('SENT')
    expect(wrapper.text()).toContain('CANCELLED')
    expect(wrapper.text()).toContain('FAILED')
    expect(wrapper.text()).toContain('DELIVERY_TIMEOUT')
    await wrapper.get('.work-task-reminder-retry').trigger('click')
    await flushPromises()
    expect(workApi.retryTaskReminder).toHaveBeenCalledWith('10', '204', { version: 9 })

    vi.mocked(workApi.retryTaskReminder).mockRejectedValueOnce(new ApiRequestError(409, {
      code: 'STALE_VERSION',
      message: 'version changed',
      data: null,
      requestId: 'request-stale',
      traceId: 'trace-stale',
      errors: [],
    }))
    await wrapper.get('.work-task-reminder-retry').trigger('click')
    await flushPromises()
    expect(wrapper.get('.work-error').text()).toContain('提醒状态已过期')

    vi.mocked(workApi.retryTaskReminder).mockRejectedValueOnce(new ApiRequestError(403, {
      code: 'ACCESS_DENIED',
      message: 'denied',
      data: null,
      requestId: 'request-denied',
      traceId: 'trace-denied',
      errors: [],
    }))
    await wrapper.get('.work-task-reminder-retry').trigger('click')
    await flushPromises()
    expect(wrapper.get('.work-error').text()).toContain('无权重试该提醒')
  })

  it('shows delegated approval queue state from represented authorities', async () => {
    applySession('20', ['work.task.access', 'flow.instance.read'])
    vi.mocked(flowApi.listApprovalTasks).mockResolvedValue({
      items: [{
        instanceId: '501',
        definitionId: '601',
        definitionVersion: 2,
        businessKey: 'purchase:701',
        requesterId: '100',
        approverId: '300',
        approverIds: ['300'],
        currentStepIndex: 0,
        claimState: 'CLAIMED',
        recordBinding: null,
        status: 'PENDING',
        startedAt: '2026-07-30T00:00:00Z',
        completedAt: null,
        representedAuthorities: [{
          representedMemberId: '300',
          delegationRuleId: '801',
        }],
      }],
      page: 1,
      size: 20,
      total: 1,
    })
    const wrapper = render()
    await flushPromises()

    expect(flowApi.listApprovalTasks).toHaveBeenCalledWith('10', {
      status: 'PENDING',
      page: 1,
      size: 20,
    })
    expect(wrapper.get('.delegated-approval-card').text()).toContain('purchase:701')
    expect(wrapper.get('.delegated-approval-card').text()).toContain('300')
  })
})
