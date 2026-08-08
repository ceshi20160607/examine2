<script setup lang="ts">
import {
  Archive,
  ArchiveRestore,
  CalendarDays,
  CircleCheckBig,
  ClipboardList,
  Columns3,
  ListTodo,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  UserRoundCog,
  UserMinus,
  UserPlus,
} from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import MemberPicker from '@/components/runtime/MemberPicker.vue'
import { ApiRequestError } from '@/services/api'
import { flowApi } from '@/services/flow'
import { workApi } from '@/services/work'
import { useSessionStore } from '@/stores/session'
import type { FlowInstance } from '@/types/flow'
import WorkDailyReports from '@/views/system/WorkDailyReports.vue'
import type {
  WorkProject,
  WorkProjectMember,
  WorkProjectMemberRole,
  WorkProjectStatusFilter,
  WorkTask,
  WorkTaskRoleFilter,
  WorkTaskStatusFilter,
  WorkTaskViewMode,
} from '@/types/work'

const route = useRoute()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const tenantId = computed(() => session.context?.tenantId ?? '')
const tasks = ref<WorkTask[]>([])
const workMode = ref<'TASKS' | 'REPORTS'>(
  session.hasPermission('work.task.access') ? 'TASKS' : 'REPORTS',
)
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keywordDraft = ref('')
const keyword = ref('')
const statusFilter = ref<WorkTaskStatusFilter>('ALL')
const roleFilter = ref<WorkTaskRoleFilter>('PARTICIPATING')
const selectedProjectId = ref('')
const viewMode = ref<WorkTaskViewMode>('LIST')
const loading = ref(false)
const mutation = ref('')
const error = ref('')
const createOpen = ref(false)
const createTitle = ref('')
const createAssignee = ref('')
const createProjectId = ref('')
const createDescription = ref('')
const createDueAt = ref('')
const createReminderAt = ref('')
const assignTask = ref<WorkTask | null>(null)
const assignAssignee = ref('')
const editTask = ref<WorkTask | null>(null)
const editTitle = ref('')
const editProjectId = ref('')
const editDescription = ref('')
const editDueAt = ref('')
const editReminderAt = ref('')
const editOriginalReminderAt = ref('')
const projects = ref<WorkProject[]>([])
const projectTotal = ref(0)
const projectPage = ref(1)
const projectPageSize = 20
const projectKeywordDraft = ref('')
const projectKeyword = ref('')
const projectStatusFilter = ref<WorkProjectStatusFilter>('ALL')
const projectLoading = ref(false)
const projectError = ref('')
const selectedProject = ref<WorkProject | null>(null)
const projectMembers = ref<WorkProjectMember[]>([])
const projectDetailLoading = ref(false)
const projectCreateOpen = ref(false)
const projectEditOpen = ref(false)
const projectTitle = ref('')
const projectDescription = ref('')
const memberCandidateId = ref('')
const memberCandidateRole = ref<WorkProjectMemberRole>('MEMBER')
const delegatedApprovalTasks = ref<FlowInstance[]>([])
const delegatedApprovalLoading = ref(false)
const delegatedApprovalError = ref('')

const canUseTasks = computed(() => session.hasPermission('work.task.access'))
const canUseReports = computed(() => [
  'work.report.access', 'work.report.create', 'work.report.manage',
].some(session.hasPermission))
const canCreate = computed(() => canUseTasks.value && session.hasPermission('work.task.create'))
const canManage = computed(() => canUseTasks.value && session.hasPermission('work.task.manage'))
const canManageProjects = computed(() => canUseTasks.value && session.hasPermission('work.project.manage'))
const canCreateProject = computed(() => canCreate.value || canManageProjects.value)
const canReadApprovals = computed(() => session.hasPermission('flow.instance.read'))
let loadGeneration = 0
let projectLoadGeneration = 0
let projectDetailGeneration = 0

const activeProjects = computed(() => projects.value.filter(project => project.status === 'ACTIVE'))
const selectedMember = computed(() => projectMembers.value.find(
  member => member.memberId === session.context?.memberId,
))
const canManageSelectedProject = computed(() => (
  canManageProjects.value || selectedMember.value?.role === 'OWNER'
))
const ownerCount = computed(() => projectMembers.value.filter(
  member => member.status === 'ACTIVE' && member.role === 'OWNER',
).length)
const kanbanColumns = computed(() => ([
  { status: 'OPEN' as const, label: '待处理', items: tasks.value.filter(task => task.status === 'OPEN') },
  { status: 'COMPLETED' as const, label: '已完成', items: tasks.value.filter(task => task.status === 'COMPLETED') },
]))
const calendarGroups = computed(() => {
  const grouped = new Map<string, WorkTask[]>()
  for (const task of tasks.value) {
    const key = task.dueAt?.slice(0, 10) || 'NO_DUE'
    grouped.set(key, [...(grouped.get(key) ?? []), task])
  }
  return [...grouped.entries()].sort(([left], [right]) => {
    if (left === 'NO_DUE') return 1
    if (right === 'NO_DUE') return -1
    return left.localeCompare(right)
  })
})

async function loadTasks() {
  if (!canUseTasks.value) {
    tasks.value = []
    total.value = 0
    loading.value = false
    return
  }
  const generation = ++loadGeneration
  loading.value = true
  error.value = ''
  try {
    const result = await workApi.list(systemId.value, {
      keyword: keyword.value,
      status: statusFilter.value,
      role: roleFilter.value,
      ...(selectedProjectId.value ? { projectId: selectedProjectId.value } : {}),
      ...taskRouteQuery(),
      page: page.value,
      size: pageSize.value,
    })
    if (generation !== loadGeneration) return
    total.value = result.total
    const maxPage = Math.max(1, Math.ceil(result.total / result.size))
    if (page.value > maxPage) {
      tasks.value = []
      page.value = maxPage
      return
    }
    tasks.value = result.items
  } catch (cause) {
    if (generation !== loadGeneration) return
    error.value = message(cause)
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

async function loadProjects() {
  if (!canUseTasks.value) {
    projects.value = []
    projectTotal.value = 0
    projectLoading.value = false
    return
  }
  const generation = ++projectLoadGeneration
  projectLoading.value = true
  projectError.value = ''
  try {
    const result = await workApi.listProjects(systemId.value, {
      keyword: projectKeyword.value,
      status: projectStatusFilter.value,
      page: projectPage.value,
      size: projectPageSize,
    })
    if (generation !== projectLoadGeneration) return
    projects.value = result.items
    projectTotal.value = result.total
  } catch (cause) {
    if (generation !== projectLoadGeneration) return
    projectError.value = message(cause)
  } finally {
    if (generation === projectLoadGeneration) projectLoading.value = false
  }
}

async function loadProjectContext() {
  const generation = ++projectDetailGeneration
  if (!canUseTasks.value || !selectedProjectId.value) {
    selectedProject.value = null
    projectMembers.value = []
    return
  }
  projectDetailLoading.value = true
  projectError.value = ''
  try {
    const [project, members] = await Promise.all([
      workApi.project(systemId.value, selectedProjectId.value),
      workApi.listProjectMembers(systemId.value, selectedProjectId.value),
    ])
    if (generation !== projectDetailGeneration) return
    selectedProject.value = project
    projectMembers.value = members
  } catch (cause) {
    if (generation !== projectDetailGeneration) return
    selectedProject.value = null
    projectMembers.value = []
    projectError.value = message(cause)
  } finally {
    if (generation === projectDetailGeneration) projectDetailLoading.value = false
  }
}

function applyProjectKeyword() {
  projectKeyword.value = projectKeywordDraft.value.trim()
  projectPage.value = 1
}

function selectProject(projectId: string) {
  selectedProjectId.value = projectId
  page.value = 1
}

function openCreateProject() {
  projectTitle.value = ''
  projectDescription.value = ''
  projectCreateOpen.value = true
}

async function createProject() {
  const title = projectTitle.value.trim()
  const description = projectDescription.value.trim()
  if (!title || title.length > 200 || description.length > 2000) return
  mutation.value = 'project:create'
  projectError.value = ''
  try {
    const created = await workApi.createProject(systemId.value, {
      title,
      description: description || null,
    })
    projectCreateOpen.value = false
    await loadProjects()
    selectProject(created.id)
  } catch (cause) {
    projectError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function openEditProject() {
  if (!selectedProject.value) return
  projectTitle.value = selectedProject.value.title
  projectDescription.value = selectedProject.value.description ?? ''
  projectEditOpen.value = true
}

async function updateProject() {
  const project = selectedProject.value
  const title = projectTitle.value.trim()
  const description = projectDescription.value.trim()
  if (!project || !title || title.length > 200 || description.length > 2000) return
  mutation.value = `project:update:${project.id}`
  projectError.value = ''
  try {
    selectedProject.value = await workApi.updateProject(systemId.value, project.id, {
      title,
      description: description || null,
      version: project.version,
    })
    projectEditOpen.value = false
    await loadProjects()
  } catch (cause) {
    projectError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function transitionProject() {
  const project = selectedProject.value
  if (!project) return
  mutation.value = `project:status:${project.id}`
  projectError.value = ''
  try {
    selectedProject.value = project.status === 'ACTIVE'
      ? await workApi.archiveProject(systemId.value, project.id, { version: project.version })
      : await workApi.reopenProject(systemId.value, project.id, { version: project.version })
    await Promise.all([loadProjects(), loadTasks()])
  } catch (cause) {
    projectError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function addProjectMember() {
  const project = selectedProject.value
  const memberId = memberCandidateId.value.trim()
  if (!project || !memberId) return
  mutation.value = `project:member:add:${memberId}`
  projectError.value = ''
  try {
    await workApi.addProjectMember(systemId.value, project.id, {
      memberId,
      role: memberCandidateRole.value,
    })
    memberCandidateId.value = ''
    memberCandidateRole.value = 'MEMBER'
    await loadProjectContext()
  } catch (cause) {
    projectError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function changeProjectMemberRole(member: WorkProjectMember) {
  const project = selectedProject.value
  if (!project || (member.role === 'OWNER' && ownerCount.value <= 1)) return
  mutation.value = `project:member:role:${member.memberId}`
  projectError.value = ''
  try {
    await workApi.updateProjectMember(systemId.value, project.id, member.memberId, {
      role: member.role === 'OWNER' ? 'MEMBER' : 'OWNER',
      version: member.version,
    })
    await loadProjectContext()
  } catch (cause) {
    projectError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function removeProjectMember(member: WorkProjectMember) {
  const project = selectedProject.value
  if (!project || (member.role === 'OWNER' && ownerCount.value <= 1)) return
  mutation.value = `project:member:remove:${member.memberId}`
  projectError.value = ''
  try {
    await workApi.removeProjectMember(systemId.value, project.id, member.memberId, {
      version: member.version,
    })
    await loadProjectContext()
  } catch (cause) {
    projectError.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function loadDelegatedApprovalTasks() {
  if (!canUseTasks.value || !canReadApprovals.value) {
    delegatedApprovalTasks.value = []
    delegatedApprovalError.value = ''
    return
  }
  delegatedApprovalLoading.value = true
  delegatedApprovalError.value = ''
  try {
    const result = await flowApi.listApprovalTasks(systemId.value, {
      status: 'PENDING',
      page: 1,
      size: 20,
    })
    delegatedApprovalTasks.value = result.items.filter(
      task => task.representedAuthorities?.some(
        authority => Boolean(authority.delegationRuleId),
      ),
    )
  } catch (cause) {
    delegatedApprovalError.value = message(cause)
  } finally {
    delegatedApprovalLoading.value = false
  }
}

async function refreshAll() {
  if (!canUseTasks.value) return
  await Promise.all([
    loadTasks(),
    loadProjects(),
    loadProjectContext(),
    loadDelegatedApprovalTasks(),
  ])
}

function applyKeyword() {
  const next = keywordDraft.value.trim()
  if (keyword.value === next && page.value === 1) {
    void loadTasks()
    return
  }
  keyword.value = next
  page.value = 1
}

function resetPage() {
  page.value = 1
}

function changePage(nextPage: number, nextSize: number) {
  if (nextSize !== pageSize.value) {
    pageSize.value = nextSize
    page.value = 1
    return
  }
  page.value = nextPage
}

function resetPageState() {
  keywordDraft.value = ''
  keyword.value = ''
  statusFilter.value = 'ALL'
  roleFilter.value = 'PARTICIPATING'
  selectedProjectId.value = ''
  viewMode.value = 'LIST'
  workMode.value = canUseTasks.value ? 'TASKS' : 'REPORTS'
  page.value = 1
  pageSize.value = 20
  tasks.value = []
  total.value = 0
  error.value = ''
  projects.value = []
  projectTotal.value = 0
  projectPage.value = 1
  projectKeywordDraft.value = ''
  projectKeyword.value = ''
  projectStatusFilter.value = 'ALL'
  projectError.value = ''
  selectedProject.value = null
  projectMembers.value = []
  delegatedApprovalTasks.value = []
  delegatedApprovalError.value = ''
  applyTaskRouteFilters()
}

function routeQueryValue(name: string) {
  const value = route.query[name]
  return Array.isArray(value) ? value[0] : value
}

function routeInstant(name: string) {
  const value = routeQueryValue(name)?.trim()
  if (!value) return undefined
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return `${value}T00:00:00.000Z`
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? undefined : parsed.toISOString()
}

function taskRouteQuery() {
  const timeFilter = routeQueryValue('time')
  const dueFrom = routeInstant('dueFrom')
    ?? (timeFilter === 'RANGE' ? routeInstant('from') : undefined)
  const dueBefore = routeInstant('dueBefore')
    ?? (timeFilter === 'RANGE' ? routeInstant('to') : undefined)
    ?? (timeFilter === 'OVERDUE' ? routeInstant('to') ?? new Date().toISOString() : undefined)
  const assigneeMemberId = routeQueryValue('assigneeMemberId')?.trim()
  return {
    ...(dueFrom ? { dueFrom } : {}),
    ...(routeInstant('dueTo') ? { dueTo: routeInstant('dueTo') } : {}),
    ...(dueBefore ? { dueBefore } : {}),
    ...(routeInstant('createdFrom') ? { createdFrom: routeInstant('createdFrom') } : {}),
    ...(routeInstant('createdBefore') ? { createdBefore: routeInstant('createdBefore') } : {}),
    ...(routeInstant('updatedFrom') ? { updatedFrom: routeInstant('updatedFrom') } : {}),
    ...(routeInstant('updatedBefore') ? { updatedBefore: routeInstant('updatedBefore') } : {}),
    ...(assigneeMemberId && /^[1-9]\d*$/.test(assigneeMemberId) ? { assigneeMemberId } : {}),
  }
}

function applyTaskRouteFilters() {
  const status = routeQueryValue('status')
  const role = routeQueryValue('role')
  const projectId = routeQueryValue('projectId')?.trim()
  if (status === 'ALL' || status === 'OPEN' || status === 'COMPLETED') {
    statusFilter.value = status
  }
  if (role === 'ALL' || role === 'PARTICIPATING'
    || role === 'CREATED_BY_ME' || role === 'ASSIGNED_TO_ME') {
    roleFilter.value = role
  }
  selectedProjectId.value = projectId && /^[1-9]\d*$/.test(projectId) ? projectId : ''
}

function openCreateTask() {
  createTitle.value = ''
  createAssignee.value = ''
  createProjectId.value = selectedProject.value?.status === 'ACTIVE'
    ? selectedProject.value.id
    : ''
  createDescription.value = ''
  createDueAt.value = ''
  createReminderAt.value = ''
  createOpen.value = true
}

function dueAtFromInput(value: string) {
  if (!value.trim()) return null
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? null : parsed.toISOString()
}

function dueAtInput(value: string | null) {
  if (!value) return ''
  const parsed = new Date(value)
  if (Number.isNaN(parsed.valueOf())) return ''
  const local = new Date(parsed.valueOf() - parsed.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function taskScheduleInput(dueInput: string, reminderInput: string, requireFutureReminder = true) {
  const dueAt = dueAtFromInput(dueInput)
  const reminderAt = dueAtFromInput(reminderInput)
  if (dueInput.trim() && !dueAt) {
    return { error: '截止时间格式无效，请重新选择。' } as const
  }
  if (reminderInput.trim() && !reminderAt) {
    return { error: '提醒时间格式无效，请重新选择。' } as const
  }
  if (requireFutureReminder && reminderAt && new Date(reminderAt).valueOf() <= Date.now()) {
    return { error: '提醒时间必须晚于当前时间。' } as const
  }
  if (reminderAt && dueAt && reminderAt > dueAt) {
    return { error: '提醒时间不得晚于截止时间。' } as const
  }
  return { dueAt, reminderAt, error: '' } as const
}

function projectName(projectId: string | null) {
  if (!projectId) return '独立任务'
  return projects.value.find(project => project.id === projectId)?.title
    ?? `项目 ${projectId}`
}

async function createTask() {
  if (!createTitle.value.trim() || !createAssignee.value.trim()) return
  const description = createDescription.value.trim()
  if (description.length > 2000) return
  error.value = ''
  const schedule = taskScheduleInput(createDueAt.value, createReminderAt.value)
  if (schedule.error) {
    error.value = schedule.error
    return
  }
  mutation.value = 'create'
  try {
    await workApi.create(systemId.value, {
      title: createTitle.value.trim(),
      assigneeMemberId: createAssignee.value.trim(),
      projectId: createProjectId.value || null,
      description: description || null,
      dueAt: schedule.dueAt,
      reminderAt: schedule.reminderAt,
    })
    createOpen.value = false
    createTitle.value = ''
    createAssignee.value = ''
    createProjectId.value = ''
    createDescription.value = ''
    createDueAt.value = ''
    createReminderAt.value = ''
    await loadTasks()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function openEditTask(task: WorkTask) {
  editTask.value = task
  editTitle.value = task.title
  editProjectId.value = task.projectId ?? ''
  editDescription.value = task.description ?? ''
  editDueAt.value = dueAtInput(task.dueAt)
  editReminderAt.value = dueAtInput(task.reminderAt ?? null)
  editOriginalReminderAt.value = editReminderAt.value
}

async function updateTask() {
  const task = editTask.value
  const title = editTitle.value.trim()
  const description = editDescription.value.trim()
  if (!task || !title || title.length > 200 || description.length > 2000) return
  error.value = ''
  const reminderChanged = editReminderAt.value !== editOriginalReminderAt.value
  const schedule = taskScheduleInput(editDueAt.value, editReminderAt.value, reminderChanged)
  if (schedule.error) {
    error.value = schedule.error
    return
  }
  mutation.value = `update:${task.id}`
  try {
    await workApi.update(systemId.value, task.id, {
      title,
      projectId: editProjectId.value || null,
      description: description || null,
      dueAt: schedule.dueAt,
      ...(reminderChanged ? { reminderAt: schedule.reminderAt } : {}),
      version: task.version,
    })
    editTask.value = null
    await loadTasks()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function openAssign(task: WorkTask) {
  assignTask.value = task
  assignAssignee.value = task.assigneeMemberId
}

async function assign() {
  if (!assignTask.value || !assignAssignee.value.trim()) return
  mutation.value = `assign:${assignTask.value.id}`
  error.value = ''
  try {
    await workApi.assign(systemId.value, assignTask.value.id, {
      assigneeMemberId: assignAssignee.value.trim(),
    })
    assignTask.value = null
    await loadTasks()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function transition(task: WorkTask) {
  mutation.value = `status:${task.id}`
  error.value = ''
  try {
    if (task.status === 'OPEN') await workApi.complete(systemId.value, task.id)
    else await workApi.reopen(systemId.value, task.id)
    await loadTasks()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function retryTaskReminder(task: WorkTask) {
  if (!canManage.value || task.reminder?.status !== 'FAILED') return
  mutation.value = `reminder-retry:${task.id}`
  error.value = ''
  try {
    await workApi.retryTaskReminder(systemId.value, task.id, {
      version: task.reminder.version,
    })
    await loadTasks()
  } catch (cause) {
    error.value = reminderMutationMessage(cause)
  } finally {
    mutation.value = ''
  }
}

function canAssign(task: WorkTask) {
  return canManage.value || task.creatorMemberId === session.context?.memberId
}

function canEditMetadata(task: WorkTask) {
  return canAssign(task)
}

function canScheduleReminder(task: WorkTask) {
  return canEditMetadata(task) || task.assigneeMemberId === session.context?.memberId
}

function canEdit(task: WorkTask) {
  return canScheduleReminder(task)
}

function canTransition(task: WorkTask) {
  return canManage.value || (task.status === 'OPEN'
    ? task.assigneeMemberId === session.context?.memberId
    : task.creatorMemberId === session.context?.memberId)
}

function message(cause: unknown) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code
  return cause instanceof Error ? cause.message : '任务请求失败，请稍后重试'
}

function reminderMutationMessage(cause: unknown) {
  if (cause instanceof ApiRequestError) {
    if (cause.status === 409) return `提醒状态已过期，请刷新后重试：${cause.message || cause.code}`
    if (cause.status === 403) return `无权重试该提醒：${cause.message || cause.code}`
  }
  return `提醒重试失败：${message(cause)}`
}

function reminderTime(task: WorkTask) {
  const scheduledAt = task.reminder?.scheduledAt ?? task.reminderAt
  return scheduledAt ? time(scheduledAt) : '未设置'
}

function time(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

applyTaskRouteFilters()
watch([systemId, tenantId], resetPageState, { flush: 'sync' })
watch(() => route.fullPath, () => {
  applyTaskRouteFilters()
  page.value = 1
  if (canUseTasks.value) void loadTasks()
})
watch(
  [systemId, tenantId, canUseTasks, keyword, statusFilter, roleFilter, selectedProjectId, page, pageSize],
  () => void loadTasks(),
  { immediate: true },
)
watch(
  [systemId, tenantId, canUseTasks, projectKeyword, projectStatusFilter, projectPage],
  () => void loadProjects(),
  { immediate: true },
)
watch([systemId, tenantId, canUseTasks, selectedProjectId], () => void loadProjectContext(), {
  immediate: true,
})
watch([systemId, tenantId, canUseTasks, canReadApprovals], () => void loadDelegatedApprovalTasks(), {
  immediate: true,
})
watch(canManage, (allowed) => {
  if (!allowed && roleFilter.value === 'ALL') {
    roleFilter.value = 'PARTICIPATING'
    page.value = 1
  }
})
watch([canUseTasks, canUseReports], ([tasksAllowed, reportsAllowed]) => {
  if (!tasksAllowed && reportsAllowed) workMode.value = 'REPORTS'
  else if (tasksAllowed && !reportsAllowed) workMode.value = 'TASKS'
})
</script>

<template>
  <section class="work-page">
    <header class="work-heading">
      <div>
        <h1><ClipboardList :size="24" />{{ workMode === 'TASKS' ? '工作任务' : '工作日报' }}</h1>
        <p>{{ workMode === 'TASKS'
          ? '查看与处理当前租户内分配给你的任务。'
          : '填写日报并查看团队七日提交状态。' }}</p>
      </div>
      <div class="work-actions">
        <a-button
          v-if="workMode === 'TASKS'"
          :loading="loading || delegatedApprovalLoading"
          @click="refreshAll"
        >
          <RefreshCw :size="16" />刷新
        </a-button>
        <a-button v-if="canCreate" type="primary" class="work-task-create" @click="openCreateTask"><Plus :size="16" />新建任务</a-button>
      </div>
    </header>

    <div class="work-mode-switch" aria-label="Work 功能模式">
      <a-button
        v-if="canUseTasks"
        class="work-mode-tasks"
        :type="workMode === 'TASKS' ? 'primary' : 'default'"
        @click="workMode = 'TASKS'"
      >任务管理</a-button>
      <a-button
        v-if="canUseReports"
        class="work-mode-reports"
        :type="workMode === 'REPORTS' ? 'primary' : 'default'"
        @click="workMode = 'REPORTS'"
      >工作日报</a-button>
    </div>

    <section v-if="workMode === 'TASKS'" class="work-projects">
      <header class="work-projects-heading">
        <div>
          <h2>项目</h2>
          <p>项目归档可恢复，任务事实不会被复制或删除。</p>
        </div>
        <a-button
          v-if="canCreateProject"
          class="work-project-create"
          @click="openCreateProject"
        >
          <Plus :size="15" />新建项目
        </a-button>
      </header>
      <a-alert
        v-if="projectError"
        class="work-project-error"
        type="error"
        show-icon
        closable
        :message="projectError"
        @close="projectError = ''"
      />
      <div class="work-project-filters">
        <a-input
          v-model:value="projectKeywordDraft"
          class="work-project-keyword"
          maxlength="100"
          allow-clear
          placeholder="搜索项目"
          @press-enter="applyProjectKeyword"
        />
        <a-button class="work-project-search" @click="applyProjectKeyword">搜索项目</a-button>
        <select
          v-model="projectStatusFilter"
          class="work-project-status-filter"
          @change="projectPage = 1"
        >
          <option value="ALL">全部项目</option>
          <option value="ACTIVE">进行中</option>
          <option value="ARCHIVED">已归档</option>
        </select>
      </div>
      <a-spin :spinning="projectLoading">
        <a-empty
          v-if="!projects.length && !projectLoading"
          description="当前没有可见项目"
        />
        <div v-else class="work-project-list">
          <article
            v-for="project in projects"
            :key="project.id"
            class="work-project-card"
            :class="{ selected: selectedProjectId === project.id }"
          >
            <div>
              <strong>{{ project.title }}</strong>
              <a-tag :color="project.status === 'ACTIVE' ? 'blue' : 'default'">
                {{ project.status }}
              </a-tag>
              <p>{{ project.description || '无项目说明' }}</p>
              <small>更新于 {{ time(project.updatedAt) }} · v{{ project.version }}</small>
            </div>
            <a-button
              class="work-project-select"
              @click="selectProject(project.id)"
            >
              {{ selectedProjectId === project.id ? '已选择' : '选择项目' }}
            </a-button>
          </article>
        </div>
      </a-spin>
      <a-pagination
        v-if="projectTotal > projectPageSize"
        class="work-project-pagination"
        :current="projectPage"
        :page-size="projectPageSize"
        :total="projectTotal"
        :show-size-changer="false"
        @change="projectPage = $event"
      />
      <a-spin :spinning="projectDetailLoading">
        <section v-if="selectedProject" class="work-project-detail">
          <header>
            <div>
              <h3>{{ selectedProject.title }}</h3>
              <p>{{ selectedProject.description || '无项目说明' }}</p>
            </div>
            <div v-if="canManageSelectedProject" class="work-project-actions">
              <a-button class="work-project-edit" @click="openEditProject">
                <Pencil :size="14" />编辑
              </a-button>
              <a-button
                class="work-project-transition"
                :loading="mutation === `project:status:${selectedProject.id}`"
                @click="transitionProject"
              >
                <Archive v-if="selectedProject.status === 'ACTIVE'" :size="14" />
                <ArchiveRestore v-else :size="14" />
                {{ selectedProject.status === 'ACTIVE' ? '归档' : '重新打开' }}
              </a-button>
            </div>
          </header>
          <div class="work-project-members">
            <h4>项目成员</h4>
            <article
              v-for="member in projectMembers"
              :key="member.memberId"
              class="work-project-member"
            >
              <span>成员 {{ member.memberId }}</span>
              <a-tag :color="member.role === 'OWNER' ? 'purple' : 'default'">
                {{ member.role }}
              </a-tag>
              <div v-if="canManageSelectedProject" class="work-project-member-actions">
                <a-button
                  class="work-project-member-role"
                  :disabled="member.role === 'OWNER' && ownerCount <= 1"
                  @click="changeProjectMemberRole(member)"
                >
                  {{ member.role === 'OWNER' ? '降为成员' : '提升为负责人' }}
                </a-button>
                <a-button
                  class="work-project-member-remove"
                  danger
                  :disabled="member.role === 'OWNER' && ownerCount <= 1"
                  @click="removeProjectMember(member)"
                >
                  <UserMinus :size="14" />移除
                </a-button>
              </div>
            </article>
            <div v-if="canManageSelectedProject" class="work-project-member-add">
              <MemberPicker
                v-model:value="memberCandidateId"
                :system-id="systemId"
                placeholder="选择要加入项目的成员"
              />
              <select v-model="memberCandidateRole" class="work-project-member-role-select">
                <option value="MEMBER">MEMBER</option>
                <option value="OWNER">OWNER</option>
              </select>
              <a-button
                class="work-project-member-add-submit"
                :disabled="!memberCandidateId"
                @click="addProjectMember"
              >
                <UserPlus :size="14" />添加成员
              </a-button>
            </div>
          </div>
        </section>
      </a-spin>
    </section>

    <a-alert v-if="workMode === 'TASKS' && error" class="work-error" type="error" show-icon closable :message="error" @close="error = ''" />

    <div v-if="workMode === 'TASKS'" class="work-filters" aria-label="任务筛选">
      <a-input
        v-model:value="keywordDraft"
        class="work-keyword"
        :maxlength="100"
        allow-clear
        placeholder="按任务标题搜索"
        @press-enter="applyKeyword"
      >
        <template #prefix><Search :size="15" /></template>
      </a-input>
      <a-button @click="applyKeyword">搜索</a-button>
      <a-select
        v-model:value="statusFilter"
        class="work-filter-select"
        aria-label="任务状态"
        @change="resetPage"
      >
        <a-select-option value="ALL">全部状态</a-select-option>
        <a-select-option value="OPEN">待处理</a-select-option>
        <a-select-option value="COMPLETED">已完成</a-select-option>
      </a-select>
      <a-select
        v-model:value="roleFilter"
        class="work-filter-select"
        aria-label="我的任务角色"
        @change="resetPage"
      >
        <a-select-option value="PARTICIPATING">我参与的</a-select-option>
        <a-select-option value="CREATED_BY_ME">我创建的</a-select-option>
        <a-select-option value="ASSIGNED_TO_ME">分配给我的</a-select-option>
        <a-select-option v-if="canManage" value="ALL">租户全部任务</a-select-option>
      </a-select>
      <select
        v-model="selectedProjectId"
        class="work-project-task-filter"
        aria-label="任务项目"
        @change="resetPage"
      >
        <option value="">全部项目与独立任务</option>
        <option v-for="project in projects" :key="project.id" :value="project.id">
          {{ project.title }}（{{ project.status }}）
        </option>
      </select>
      <div class="work-view-switch" aria-label="任务视图">
        <a-button
          class="work-view-list"
          :type="viewMode === 'LIST' ? 'primary' : 'default'"
          @click="viewMode = 'LIST'"
        >
          <ListTodo :size="15" />列表
        </a-button>
        <a-button
          class="work-view-kanban"
          :type="viewMode === 'KANBAN' ? 'primary' : 'default'"
          @click="viewMode = 'KANBAN'"
        >
          <Columns3 :size="15" />看板
        </a-button>
        <a-button
          class="work-view-calendar"
          :type="viewMode === 'CALENDAR' ? 'primary' : 'default'"
          @click="viewMode = 'CALENDAR'"
        >
          <CalendarDays :size="15" />日历
        </a-button>
      </div>
    </div>

    <a-spin v-if="workMode === 'TASKS'" :spinning="loading">
      <a-empty v-if="!tasks.length && !loading" description="当前没有任务" />
      <div v-else-if="viewMode === 'LIST'" class="task-list work-list-view">
        <article
          v-for="task in tasks"
          :key="task.id"
          class="task-card"
          :data-task-id="task.id"
          :data-task-version="task.version"
        >
          <div class="task-main">
            <div class="task-title">
              <a-tag :color="task.status === 'OPEN' ? 'blue' : 'green'">
                {{ task.status === 'OPEN' ? '待处理' : '已完成' }}
              </a-tag>
              <strong>{{ task.title }}</strong>
            </div>
            <div class="task-meta">
              <span>{{ projectName(task.projectId) }}</span>
              <span>负责人 {{ task.assigneeMemberId }}</span>
              <span>创建人 {{ task.creatorMemberId }}</span>
              <span>截止 {{ task.dueAt ? time(task.dueAt) : '未设置' }}</span>
              <span v-if="task.reminder || task.reminderAt" class="work-task-reminder">
                提醒 {{ reminderTime(task) }} · {{ task.reminder?.status ?? 'PENDING' }}
                <template v-if="task.reminder?.status === 'FAILED' && task.reminder.failureCode">
                  · {{ task.reminder.failureCode }}
                </template>
              </span>
              <span>更新于 {{ time(task.updatedAt) }}</span>
            </div>
            <p v-if="task.description" class="task-description">{{ task.description }}</p>
          </div>
          <div class="task-actions">
            <a-button
              v-if="canEdit(task)"
              class="work-task-edit"
              :disabled="Boolean(mutation)"
              @click="openEditTask(task)"
            >
              <Pencil :size="15" />编辑
            </a-button>
            <a-button v-if="canAssign(task)" :disabled="Boolean(mutation)" @click="openAssign(task)">
              <UserRoundCog :size="15" />转派
            </a-button>
            <a-button
              v-if="canManage && task.reminder?.status === 'FAILED'"
              class="work-task-reminder-retry"
              :loading="mutation === `reminder-retry:${task.id}`"
              :disabled="Boolean(mutation) && mutation !== `reminder-retry:${task.id}`"
              @click="retryTaskReminder(task)"
            >
              <RefreshCw :size="15" />重试提醒
            </a-button>
            <a-button
              v-if="canTransition(task)"
              class="work-task-transition"
              :type="task.status === 'OPEN' ? 'primary' : 'default'"
              :loading="mutation === `status:${task.id}`"
              :disabled="Boolean(mutation) && mutation !== `status:${task.id}`"
              @click="transition(task)"
            >
              <CircleCheckBig v-if="task.status === 'OPEN'" :size="15" />
              <RotateCcw v-else :size="15" />
              {{ task.status === 'OPEN' ? '完成' : '重新打开' }}
            </a-button>
          </div>
        </article>
      </div>
      <div v-else-if="viewMode === 'KANBAN'" class="work-kanban-view">
        <section
          v-for="column in kanbanColumns"
          :key="column.status"
          class="work-kanban-column"
          :data-status="column.status"
        >
          <header>
            <h3>{{ column.label }}</h3>
            <span>{{ column.items.length }}</span>
          </header>
          <a-empty v-if="!column.items.length" description="此列没有任务" />
          <article
            v-for="task in column.items"
            :key="task.id"
            class="task-card work-kanban-task"
            :data-task-id="task.id"
            :data-task-version="task.version"
          >
            <div class="task-main">
              <div class="task-title"><strong>{{ task.title }}</strong></div>
              <div class="task-meta">
                <span>{{ projectName(task.projectId) }}</span>
                <span>负责人 {{ task.assigneeMemberId }}</span>
                <span>截止 {{ task.dueAt ? time(task.dueAt) : '未设置' }}</span>
                <span v-if="task.reminder || task.reminderAt" class="work-task-reminder">
                  提醒 {{ reminderTime(task) }} · {{ task.reminder?.status ?? 'PENDING' }}
                  <template v-if="task.reminder?.status === 'FAILED' && task.reminder.failureCode">
                    · {{ task.reminder.failureCode }}
                  </template>
                </span>
              </div>
              <p v-if="task.description" class="task-description">{{ task.description }}</p>
            </div>
            <div class="task-actions">
              <a-button
                v-if="canEdit(task)"
                class="work-task-edit"
                @click="openEditTask(task)"
              >编辑</a-button>
              <a-button
                v-if="canTransition(task)"
                class="work-task-transition"
                @click="transition(task)"
              >{{ task.status === 'OPEN' ? '完成' : '重新打开' }}</a-button>
              <a-button
                v-if="canManage && task.reminder?.status === 'FAILED'"
                class="work-task-reminder-retry"
                @click="retryTaskReminder(task)"
              >重试提醒</a-button>
            </div>
          </article>
        </section>
      </div>
      <div v-else class="work-calendar-view">
        <section
          v-for="([date, dateTasks]) in calendarGroups"
          :key="date"
          class="work-calendar-day"
          :data-date="date"
        >
          <header>
            <h3>{{ date === 'NO_DUE' ? '未设置截止日期' : date }}</h3>
            <span>{{ dateTasks.length }}</span>
          </header>
          <article
            v-for="task in dateTasks"
            :key="task.id"
            class="task-card work-calendar-task"
            :data-task-id="task.id"
            :data-task-version="task.version"
          >
            <div class="task-main">
              <div class="task-title">
                <a-tag :color="task.status === 'OPEN' ? 'blue' : 'green'">
                  {{ task.status }}
                </a-tag>
                <strong>{{ task.title }}</strong>
              </div>
              <div class="task-meta">
                <span>{{ projectName(task.projectId) }}</span>
                <span>负责人 {{ task.assigneeMemberId }}</span>
                <span v-if="task.dueAt">{{ time(task.dueAt) }}</span>
                <span v-if="task.reminder || task.reminderAt" class="work-task-reminder">
                  提醒 {{ reminderTime(task) }} · {{ task.reminder?.status ?? 'PENDING' }}
                  <template v-if="task.reminder?.status === 'FAILED' && task.reminder.failureCode">
                    · {{ task.reminder.failureCode }}
                  </template>
                </span>
              </div>
              <p v-if="task.description" class="task-description">{{ task.description }}</p>
            </div>
            <div class="task-actions">
              <a-button
                v-if="canEdit(task)"
                class="work-task-edit"
                @click="openEditTask(task)"
              >编辑</a-button>
              <a-button
                v-if="canTransition(task)"
                class="work-task-transition"
                @click="transition(task)"
              >{{ task.status === 'OPEN' ? '完成' : '重新打开' }}</a-button>
              <a-button
                v-if="canManage && task.reminder?.status === 'FAILED'"
                class="work-task-reminder-retry"
                @click="retryTaskReminder(task)"
              >重试提醒</a-button>
            </div>
          </article>
        </section>
      </div>
    </a-spin>

    <a-pagination
      v-if="workMode === 'TASKS' && total > 0"
      class="work-pagination"
      :current="page"
      :page-size="pageSize"
      :total="total"
      :show-size-changer="true"
      :page-size-options="['10', '20', '50', '100']"
      show-quick-jumper
      @change="changePage"
      @show-size-change="changePage"
    />

    <section v-if="workMode === 'TASKS' && canReadApprovals" class="delegated-approval-queue">
      <header>
        <h2>代理审批任务</h2>
        <p>这里仅显示通过有效委托获得处理权限的审批任务。</p>
      </header>
      <a-alert
        v-if="delegatedApprovalError"
        class="delegated-approval-error"
        type="error"
        show-icon
        :message="delegatedApprovalError"
      />
      <a-spin :spinning="delegatedApprovalLoading">
        <a-empty
          v-if="!delegatedApprovalTasks.length && !delegatedApprovalLoading"
          description="当前没有代理审批任务"
        />
        <div v-else class="delegated-approval-list">
          <article
            v-for="task in delegatedApprovalTasks"
            :key="task.instanceId"
            class="task-card delegated-approval-card"
          >
            <div class="task-main">
              <div class="task-title">
                <a-tag color="purple">代理审批</a-tag>
                <strong>{{ task.businessKey }}</strong>
              </div>
              <div class="task-meta">
                <span>审批实例 {{ task.instanceId }}</span>
                <span>定义 {{ task.definitionId }} / v{{ task.definitionVersion }}</span>
                <span>
                  可代表
                  {{
                    task.representedAuthorities
                      ?.filter(item => Boolean(item.delegationRuleId))
                      ?.map(item => item.representedMemberId)
                      .join('、')
                  }}
                </span>
              </div>
            </div>
          </article>
        </div>
      </a-spin>
    </section>

    <WorkDailyReports
      v-if="workMode === 'REPORTS'"
      :system-id="systemId"
      :tenant-id="tenantId"
    />

    <a-modal
      v-model:open="projectCreateOpen"
      title="新建项目"
      :confirm-loading="mutation === 'project:create'"
      @ok="createProject"
    >
      <a-form layout="vertical">
        <a-form-item label="项目标题" required>
          <a-input v-model:value="projectTitle" class="work-project-title" maxlength="200" />
        </a-form-item>
        <a-form-item label="项目说明">
          <a-textarea
            v-model:value="projectDescription"
            class="work-project-description"
            :rows="4"
            maxlength="2000"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="projectEditOpen"
      title="编辑项目"
      :confirm-loading="mutation.startsWith('project:update:')"
      @ok="updateProject"
    >
      <a-form layout="vertical">
        <a-form-item label="项目标题" required>
          <a-input v-model:value="projectTitle" class="work-project-edit-title" maxlength="200" />
        </a-form-item>
        <a-form-item label="项目说明">
          <a-textarea
            v-model:value="projectDescription"
            class="work-project-edit-description"
            :rows="4"
            maxlength="2000"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="createOpen" title="新建任务" :confirm-loading="mutation === 'create'" @ok="createTask">
      <a-form layout="vertical">
        <a-form-item label="任务标题" required>
          <a-input v-model:value="createTitle" maxlength="200" placeholder="输入要完成的事项" />
        </a-form-item>
        <a-form-item label="负责人" required>
          <MemberPicker
            v-model:value="createAssignee"
            :system-id="systemId"
            placeholder="搜索并选择负责人"
          />
        </a-form-item>
        <a-form-item label="所属项目">
          <select v-model="createProjectId" class="work-task-create-project">
            <option value="">独立任务</option>
            <option v-for="project in activeProjects" :key="project.id" :value="project.id">
              {{ project.title }}
            </option>
          </select>
        </a-form-item>
        <a-form-item label="任务说明">
          <a-textarea
            v-model:value="createDescription"
            class="work-task-create-description"
            :rows="4"
            maxlength="2000"
          />
        </a-form-item>
        <a-form-item label="截止时间">
          <input v-model="createDueAt" class="work-task-create-due-at" type="datetime-local">
        </a-form-item>
        <a-form-item label="提醒时间（可选，不得晚于截止时间）">
          <input
            v-model="createReminderAt"
            class="work-task-create-reminder-at"
            type="datetime-local"
          >
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      :open="Boolean(editTask)"
      title="编辑任务"
      :confirm-loading="mutation.startsWith('update:')"
      @cancel="editTask = null"
      @ok="updateTask"
    >
      <a-form layout="vertical">
        <a-form-item label="任务标题" required>
          <a-input
            v-model:value="editTitle"
            class="work-task-edit-title"
            maxlength="200"
            :disabled="editTask ? !canEditMetadata(editTask) : true"
          />
        </a-form-item>
        <a-form-item label="所属项目">
          <select
            v-model="editProjectId"
            class="work-task-edit-project"
            :disabled="editTask ? !canEditMetadata(editTask) : true"
          >
            <option value="">独立任务</option>
            <option v-for="project in activeProjects" :key="project.id" :value="project.id">
              {{ project.title }}
            </option>
          </select>
        </a-form-item>
        <a-form-item label="任务说明">
          <a-textarea
            v-model:value="editDescription"
            class="work-task-edit-description"
            :rows="4"
            maxlength="2000"
            :disabled="editTask ? !canEditMetadata(editTask) : true"
          />
        </a-form-item>
        <a-form-item label="截止时间">
          <input
            v-model="editDueAt"
            class="work-task-edit-due-at"
            type="datetime-local"
            :disabled="editTask ? !canEditMetadata(editTask) : true"
          >
        </a-form-item>
        <a-form-item label="提醒时间（可选，清空可取消）">
          <input
            v-model="editReminderAt"
            class="work-task-edit-reminder-at"
            type="datetime-local"
          >
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      :open="Boolean(assignTask)"
      title="转派任务"
      :confirm-loading="mutation.startsWith('assign:')"
      @cancel="assignTask = null"
      @ok="assign"
    >
      <a-form layout="vertical">
        <a-form-item label="负责人" required>
          <MemberPicker
            v-model:value="assignAssignee"
            :system-id="systemId"
            placeholder="搜索并选择新负责人"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </section>
</template>

<style scoped>
.work-page {
  display: grid;
  gap: 20px;
  max-width: 1120px;
  margin: 0 auto;
  padding: 28px;
  min-width: 0;
  background: #f4f6f8;
}

.work-heading,
.task-card,
.task-title,
.task-meta,
.task-actions,
.work-actions,
.work-heading h1 {
  display: flex;
  align-items: center;
}

.work-heading {
  justify-content: space-between;
  gap: 20px;
}

.work-heading h1 {
  gap: 10px;
  margin: 0;
}

.work-heading p {
  margin: 6px 0 0;
  color: #64748b;
}

.work-actions,
.task-actions,
.work-project-actions,
.work-project-member-actions,
.work-view-switch,
.work-mode-switch {
  gap: 10px;
  flex-wrap: wrap;
}

.work-mode-switch {
  display: flex;
  align-items: center;
}

.work-error {
  margin: 0;
}

.work-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.work-projects,
.work-project-detail,
.work-kanban-column,
.work-calendar-day {
  display: grid;
  gap: 12px;
  padding: 18px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}

.work-projects-heading,
.work-project-card,
.work-project-detail > header,
.work-project-member,
.work-project-member-add,
.work-project-actions,
.work-project-member-actions,
.work-view-switch,
.work-kanban-column > header,
.work-calendar-day > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.work-projects-heading h2,
.work-project-detail h3,
.work-project-members h4,
.work-kanban-column h3,
.work-calendar-day h3 {
  margin: 0;
}

.work-project-filters,
.work-project-list,
.work-project-members {
  display: grid;
  gap: 10px;
}

.work-project-filters {
  grid-template-columns: minmax(220px, 1fr) auto 160px;
}

.work-project-card,
.work-project-member {
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
}

.work-project-card.selected {
  border-color: #2563eb;
}

.work-project-task-filter,
.work-project-status-filter,
.work-project-member-role-select,
.work-task-create-project,
.work-task-edit-project,
.work-task-create-due-at,
.work-task-edit-due-at,
.work-task-create-reminder-at,
.work-task-edit-reminder-at {
  min-height: 32px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  padding: 4px 10px;
  background: #fff;
}

.work-kanban-view {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.work-calendar-view {
  display: grid;
  gap: 14px;
}

.work-kanban-task,
.work-calendar-task {
  align-items: flex-start;
}

.task-description {
  margin: 10px 0 0;
  color: #475569;
  white-space: pre-wrap;
}

.work-keyword {
  width: min(360px, 100%);
}

.work-filter-select {
  min-width: 150px;
}

.task-list {
  display: grid;
  gap: 12px;
}

.task-card {
  justify-content: space-between;
  gap: 20px;
  padding: 18px 20px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  min-width: 0;
}

.task-main {
  min-width: 0;
}

.task-title {
  gap: 8px;
}

.task-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-meta {
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 10px;
  color: #64748b;
  font-size: 13px;
}

.work-pagination {
  justify-self: end;
}

@media (max-width: 900px) {
  .work-page { gap: 16px; padding: 20px; }
  .work-heading { align-items: flex-start; }
  .work-actions { justify-content: flex-end; }
  .work-project-filters { grid-template-columns: minmax(180px,1fr) auto; }
  .work-project-status-filter { grid-column: 1 / -1; width: 100%; }
  .task-card { align-items: flex-start; }
  .task-actions { justify-content: flex-end; flex-wrap: wrap; }
}

@media (max-width: 560px) {
  .work-page { gap: 14px; padding: 14px; }
  .work-heading { align-items: stretch; flex-direction: column; }
  .work-actions { justify-content: flex-start; }
  .work-mode-switch { align-items: stretch; }
  .work-filters { align-items: stretch; display: grid; grid-template-columns: minmax(0,1fr); }
  .work-keyword,.work-filter-select { width: 100%; min-width: 0; }
  .work-projects,.work-project-detail,.work-kanban-column,.work-calendar-day { padding: 14px; }
  .work-projects-heading,.work-project-card,.work-project-detail>header,.work-project-member,.work-project-member-add { align-items: stretch; flex-direction: column; }
  .work-project-actions,.work-project-member-actions { justify-content: flex-start; flex-wrap: wrap; }
  .work-project-filters { grid-template-columns: minmax(0,1fr); }
  .work-project-status-filter { grid-column: auto; }
  .work-kanban-view { grid-template-columns: minmax(0,1fr); }
  .task-card { align-items: stretch; flex-direction: column; padding: 14px; }
  .task-title { align-items: flex-start; }
  .task-title strong { white-space: normal; overflow-wrap: anywhere; }
  .task-actions { justify-content: flex-start; }
  .work-pagination { max-width: 100%; justify-self: center; }
}
</style>
