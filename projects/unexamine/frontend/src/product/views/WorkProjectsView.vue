<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Empty, message } from 'ant-design-vue'
import dayjs, { type Dayjs } from 'dayjs'
import { ApiError, api } from '../api'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { groupProjectTasks, nextTaskAction } from '../work-management'
import type { PeopleDirectoryOption, SystemPeopleDirectory, WorkChecklistItem, WorkConfigurationField, WorkPerson, WorkProject, WorkTask, WorkTaskGroup } from '../types'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import PersonSelect from '../components/PersonSelect.vue'
import WorkConfiguredFields from '../components/WorkConfiguredFields.vue'
import BusinessRecordPicker, { type BusinessRecordValue } from '../components/BusinessRecordPicker.vue'
import WorkTaskDetailPanel from '../components/WorkTaskDetailPanel.vue'
import { productDateTime } from '../presentation'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const projects = ref<WorkProject[]>([])
const directory = ref<SystemPeopleDirectory>({ departments: [], people: [], permissionVersion: 0 })
const taskFields = ref<WorkConfigurationField[]>([])
const selected = ref<WorkProject>()
const taskDetail = ref<WorkTask>()
const loading = ref(false)
const busy = ref('')
const createProjectOpen = ref(false)
const createGroupOpen = ref(false)
const createTaskOpen = ref(false)
const taskDrawerOpen = ref(false)
const viewMode = ref<'list' | 'board' | 'calendar'>('list')
const calendarDate = ref<Dayjs>(dayjs())
const editingProject = ref(false)
const editingGroupId = ref<number>()
const editingTaskId = ref<number>()
const projectForm = reactive({
  code: '', name: '', description: '', customerName: '', internalDepartmentName: '', stage: '', labels: [] as string[],
  startDate: '', dueDate: '', status: 'PLANNED', progressPercent: 0,
  members: [] as Array<{ tenantMemberId?: number; projectRole: string }>, business: {} as BusinessRecordValue,
})
const groupForm = reactive({ name: '', sortOrder: 10, ownerId: undefined as number | undefined, dueTime: '', progressPercent: 0, status: 'ACTIVE', version: 0 })
const taskForm = reactive({
  parentTaskId: undefined as number | undefined, title: '', description: '', taskGroupId: undefined as number | undefined,
  priority: 'NORMAL' as WorkTask['priority'], ownerId: undefined as number | undefined,
  collaboratorIds: [] as number[], followerIds: [] as number[], acceptanceIds: [] as number[],
  estimatedHours: undefined as number | undefined, labels: [] as string[], checklist: [] as WorkChecklistItem[],
  startAt: '', dueAt: '', configuredValues: {} as Record<string, unknown>, business: {} as BusinessRecordValue,
})
const directoryPeople = computed<PeopleDirectoryOption[]>(() => props.context === 'system'
  ? directory.value.people
  : current.value ? [{
    tenantMemberId: current.value.accountId, systemMemberId: current.value.accountId,
    accountId: current.value.accountId, displayName: current.value.displayName,
    roleNames: [], tenantAdmin: false,
  }] : [])

const groupedTasks = computed(() => selected.value ? groupProjectTasks(selected.value.tasks, selected.value.taskGroups) : [])
const boardColumns = computed(() => {
  const tasks = selected.value?.tasks || []
  return [
    { key: 'TODO', name: '待开始', tasks: tasks.filter(item => ['BACKLOG', 'TODO'].includes(item.status)) },
    { key: 'IN_PROGRESS', name: '进行中', tasks: tasks.filter(item => ['IN_PROGRESS', 'BLOCKED'].includes(item.status)) },
    { key: 'WAITING_CONFIRMATION', name: '待验收', tasks: tasks.filter(item => item.status === 'WAITING_CONFIRMATION') },
    { key: 'COMPLETED', name: '已完成', tasks: tasks.filter(item => ['COMPLETED', 'ARCHIVED'].includes(item.status)) },
    { key: 'CANCELLED', name: '已取消', tasks: tasks.filter(item => item.status === 'CANCELLED') },
  ].filter(column => column.tasks.length || ['TODO', 'IN_PROGRESS', 'WAITING_CONFIRMATION'].includes(column.key))
})
const summary = computed(() => {
  const tasks = selected.value?.tasks || []
  const completed = tasks.filter(task => ['COMPLETED', 'ARCHIVED'].includes(task.status)).length
  const inProgress = tasks.filter(task => ['IN_PROGRESS', 'BLOCKED'].includes(task.status)).length
  const progress = tasks.length ? Math.round(tasks.reduce((sum, task) => sum + Number(task.progressPercent), 0) / tasks.length) : 0
  return { total: tasks.length, completed, inProgress, progress }
})

function can(action: string) {
  return allowsPermission(current.value?.permissions, 'WORK', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'WORK', '*', action)
}
function canFile(action: string) {
  return allowsPermission(current.value?.permissions, 'FILE', 'OBJECT', action)
    || allowsPermission(current.value?.permissions, 'FILE', '*', action)
}

function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
}

function statusLabel(status: WorkTask['status']) {
  return ({ BACKLOG: '待规划', TODO: '待开始', IN_PROGRESS: '进行中', BLOCKED: '已阻塞', WAITING_CONFIRMATION: '待验收', COMPLETED: '已完成', CANCELLED: '已取消', ARCHIVED: '已归档' })[status]
}

function statusColor(status: WorkTask['status']) {
  return ({ BACKLOG: 'default', TODO: 'blue', IN_PROGRESS: 'processing', BLOCKED: 'red', WAITING_CONFIRMATION: 'gold', COMPLETED: 'green', CANCELLED: 'default', ARCHIVED: 'default' })[status]
}

function priorityLabel(priority: WorkTask['priority']) {
  return ({ LOW: '低', NORMAL: '普通', HIGH: '高', URGENT: '紧急' })[priority]
}

function projectStatusLabel(status: string) {
  return ({ PLANNED: '筹备中', ACTIVE: '进行中', PAUSED: '已暂停', COMPLETED: '已完成', CANCELLED: '已取消' } as Record<string, string>)[status] || '筹备中'
}

function groupStatusLabel(status?: string) {
  return ({ ACTIVE: '进行中', COMPLETED: '已完成', CANCELLED: '已取消' } as Record<string, string>)[status || ''] || '进行中'
}

function projectRoleLabel(role: string) {
  return ({ MANAGER: '管理者', MEMBER: '成员', OBSERVER: '观察者' } as Record<string, string>)[role] || role
}

function ownerLabel(task: WorkTask) {
  return task.owner.displayName
}

function taskGroupName(task: WorkTask) {
  return selected.value?.taskGroups.find(group => group.id === task.taskGroupId)?.name || '未分组'
}

function tasksForDate(value: Dayjs) {
  return (selected.value?.tasks || []).filter(task => task.dueAt?.slice(0, 10) === value.format('YYYY-MM-DD'))
}

function memberIds(people: WorkPerson[]) {
  return people.map(item => item.tenantMemberId).filter((id): id is number => typeof id === 'number')
}

async function loadProjects(preferId?: number) {
  if (!token.value || !can('VIEW')) return
  loading.value = true
  try {
    projects.value = await api<WorkProject[]>('/api/work/projects', {}, token.value)
    const id = preferId ?? selected.value?.id ?? projects.value[0]?.id
    if (id) await selectProject(id)
    else selected.value = undefined
  } catch (error) {
    message.error(describeError(error))
  } finally {
    loading.value = false
  }
}

async function loadDirectory() {
  if (!token.value || props.context !== 'system') return
  try { directory.value = await api<SystemPeopleDirectory>('/api/system-directory', {}, token.value) }
  catch (error) { message.error(describeError(error)) }
}

async function loadTaskFields() {
  if (!token.value) return
  try { taskFields.value = await api<WorkConfigurationField[]>('/api/work/configuration/effective/TASK', {}, token.value) }
  catch (error) { message.error(describeError(error)) }
}

function assignmentPayload(ownerId: number | undefined, collaborators: number[]) {
  return props.context === 'system'
    ? { ownerTenantMemberId: ownerId, collaboratorTenantMemberIds: collaborators }
    : { ownerAccountId: ownerId, collaboratorAccountIds: collaborators }
}

function updateAssignmentPayload(owner: WorkPerson, collaborators: WorkPerson[]) {
  return props.context === 'system'
    ? { ownerTenantMemberId: owner.tenantMemberId, collaboratorTenantMemberIds: collaborators.map(item => item.tenantMemberId).filter((id): id is number => !!id) }
    : { ownerAccountId: current.value?.accountId, collaboratorAccountIds: [] }
}

async function selectProject(id: number) {
  if (!token.value) return
  try {
    selected.value = await api<WorkProject>(`/api/work/projects/${id}`, {}, token.value)
  } catch (error) {
    message.error(describeError(error))
  }
}

function openNewProject() {
  editingProject.value = false
  Object.assign(projectForm, { code: '', name: '', description: '', customerName: '', internalDepartmentName: '',
    stage: '', labels: [], startDate: '', dueDate: '', status: 'PLANNED', progressPercent: 0, members: [], business: {} })
  createProjectOpen.value = true
}

function openEditProject() {
  if (!selected.value) return
  editingProject.value = true
  Object.assign(projectForm, {
    code: selected.value.code, name: selected.value.name, description: selected.value.description || '',
    customerName: selected.value.customerName || '', internalDepartmentName: selected.value.internalDepartmentName || '',
    stage: selected.value.stage || '', labels: [...selected.value.labels], startDate: selected.value.startDate || '',
    dueDate: selected.value.dueDate || '', status: selected.value.status, progressPercent: Number(selected.value.progressPercent),
    members: selected.value.members.filter(item => item.projectRole !== 'OWNER').map(item => ({ tenantMemberId: item.person.tenantMemberId, projectRole: item.projectRole })),
    business: { businessType: selected.value.businessType, businessId: selected.value.businessId, businessTitle: selected.value.businessTitle },
  })
  createProjectOpen.value = true
}

function addProjectMember() { projectForm.members.push({ projectRole: 'MEMBER' }) }

async function saveProject() {
  if (!token.value || !projectForm.name.trim()) return
  busy.value = 'project'
  try {
    const path = editingProject.value && selected.value ? `/api/work/projects/${selected.value.id}` : '/api/work/projects'
    const project = await api<WorkProject>(path, {
      method: editingProject.value ? 'PUT' : 'POST', body: JSON.stringify({
        code: projectForm.code.trim() || undefined, name: projectForm.name.trim(), description: projectForm.description,
        customerName: projectForm.customerName, internalDepartmentName: projectForm.internalDepartmentName,
        stage: projectForm.stage, labels: projectForm.labels, startDate: projectForm.startDate || undefined,
        dueDate: projectForm.dueDate || undefined, status: projectForm.status, progressPercent: projectForm.progressPercent,
        members: projectForm.members.filter(item => item.tenantMemberId), ...projectForm.business,
        ...(editingProject.value && selected.value ? { expectedVersion: selected.value.version } : {}),
      }),
    }, token.value)
    createProjectOpen.value = false
    message.success(editingProject.value ? '项目已更新' : '项目已创建，你已成为项目负责人')
    await loadProjects(project.id)
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

function openGroup(group?: WorkTaskGroup) {
  editingGroupId.value = group?.id
  Object.assign(groupForm, group ? {
    name: group.name, sortOrder: group.sortOrder, ownerId: group.owner?.tenantMemberId,
    dueTime: group.dueTime || '', progressPercent: Number(group.progressPercent || 0), status: group.status, version: group.version,
  } : {
    name: '', sortOrder: Math.max(0, ...(selected.value?.taskGroups.map(item => item.sortOrder) || [0])) + 10,
    ownerId: undefined, dueTime: '', progressPercent: 0, status: 'ACTIVE', version: 0,
  })
  createGroupOpen.value = true
}

async function saveGroup() {
  if (!token.value || !selected.value || !groupForm.name.trim()) return
  busy.value = 'group'
  try {
    const path = editingGroupId.value
      ? `/api/work/projects/${selected.value.id}/groups/${editingGroupId.value}`
      : `/api/work/projects/${selected.value.id}/groups`
    selected.value = await api<WorkProject>(path, {
      method: editingGroupId.value ? 'PUT' : 'POST', body: JSON.stringify({
        name: groupForm.name, sortOrder: groupForm.sortOrder, dueTime: groupForm.dueTime || undefined,
        progressPercent: groupForm.progressPercent, status: groupForm.status,
        ...(props.context === 'system' ? { ownerTenantMemberId: groupForm.ownerId } : { ownerAccountId: groupForm.ownerId }),
        ...(editingGroupId.value ? { expectedVersion: groupForm.version } : {}),
      }),
    }, token.value)
    createGroupOpen.value = false
    message.success(editingGroupId.value ? '任务组已更新' : '任务组已加入项目')
    await refreshProjectList()
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

function openTask(groupId?: number, parentTaskId?: number) {
  editingTaskId.value = undefined
  Object.assign(taskForm, {
    parentTaskId, title: '', description: '', taskGroupId: groupId ?? selected.value?.taskGroups[0]?.id,
    priority: 'NORMAL', ownerId: props.context === 'system' ? current.value?.tenantMemberId || undefined : current.value?.accountId,
    collaboratorIds: [], followerIds: [], acceptanceIds: [], estimatedHours: undefined, labels: [], checklist: [],
    startAt: '', dueAt: '', configuredValues: {}, business: {},
  })
  createTaskOpen.value = true
}

function editTask(task: WorkTask) {
  editingTaskId.value = task.id
  Object.assign(taskForm, {
    parentTaskId: task.parentTaskId, title: task.title, description: task.description || '', taskGroupId: task.taskGroupId,
    priority: task.priority, ownerId: props.context === 'system' ? task.owner.tenantMemberId : current.value?.accountId,
    collaboratorIds: props.context === 'system' ? memberIds(task.collaborators) : [], followerIds: memberIds(task.followers),
    acceptanceIds: memberIds(task.acceptors), estimatedHours: task.estimatedHours, labels: [...task.labels],
    checklist: task.checklist.map(item => ({ ...item })), startAt: task.startAt || '', dueAt: task.dueAt || '',
    configuredValues: { ...task.configuredValues }, business: { businessType: task.businessType, businessId: task.businessId, businessTitle: task.businessTitle },
  })
  createTaskOpen.value = true
}

function addChecklistItem() { taskForm.checklist.push({ id: crypto.randomUUID(), text: '', completed: false }) }

async function saveTask() {
  if (!token.value || !selected.value || !taskForm.title.trim() || !taskForm.ownerId) return
  busy.value = 'task'
  try {
    const currentTask = editingTaskId.value ? taskDetail.value : undefined
    const path = editingTaskId.value ? `/api/work/tasks/${editingTaskId.value}` : `/api/work/projects/${selected.value.id}/tasks`
    const task = await api<WorkTask>(path, {
      method: editingTaskId.value ? 'PUT' : 'POST', body: JSON.stringify({
        taskGroupId: taskForm.taskGroupId, parentTaskId: taskForm.parentTaskId,
        title: taskForm.title, description: taskForm.description,
        priority: taskForm.priority, ...assignmentPayload(taskForm.ownerId, taskForm.collaboratorIds),
        followerTenantMemberIds: taskForm.followerIds, acceptanceTenantMemberIds: taskForm.acceptanceIds,
        estimatedHours: taskForm.estimatedHours, labels: taskForm.labels,
        checklist: taskForm.checklist.filter(item => item.text.trim()).map(item => ({ ...item, text: item.text.trim() })),
        startAt: taskForm.startAt || undefined, dueAt: taskForm.dueAt || undefined,
        configuredValues: taskForm.configuredValues, ...taskForm.business,
        ...(currentTask ? { expectedVersion: currentTask.version, status: currentTask.status,
          progressPercent: currentTask.progressPercent, actualHours: currentTask.actualHours, comment: '编辑任务信息' } : {}),
      }),
    }, token.value)
    createTaskOpen.value = false
    message.success(editingTaskId.value ? '任务已更新' : `任务“${task.title}”已创建，状态为待开始`)
    await selectProject(selected.value.id)
    await refreshProjectList()
    await openTaskDetail(task.id)
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

async function refreshProjectList() {
  if (!token.value) return
  projects.value = await api<WorkProject[]>('/api/work/projects', {}, token.value)
}

async function openTaskDetail(taskId: number) {
  if (!token.value) return
  try {
    taskDetail.value = await api<WorkTask>(`/api/work/tasks/${taskId}`, {}, token.value)
    taskDrawerOpen.value = true
  } catch (error) {
    message.error(describeError(error))
  }
}

async function advanceTask(task: WorkTask) {
  if (!token.value) return
  const action = nextTaskAction(task.status)
  if (!action) return
  busy.value = `advance:${task.id}`
  try {
    const updated = await api<WorkTask>(`/api/work/tasks/${task.id}`, {
      method: 'PUT', body: JSON.stringify({
        expectedVersion: task.version, status: action.status, title: task.title, description: task.description,
        ...updateAssignmentPayload(task.owner, task.collaborators),
        followerTenantMemberIds: memberIds(task.followers), acceptanceTenantMemberIds: memberIds(task.acceptors),
        priority: task.priority, progressPercent: Math.max(10, Number(task.progressPercent)),
        estimatedHours: task.estimatedHours, actualHours: task.actualHours, labels: task.labels, checklist: task.checklist,
        startAt: task.startAt, dueAt: task.dueAt,
        businessType: task.businessType, businessId: task.businessId, businessTitle: task.businessTitle,
        configuredValues: task.configuredValues, comment: action.label,
      }),
    }, token.value)
    message.success('任务已开始')
    if (selected.value) await selectProject(selected.value.id)
    await refreshProjectList()
    if (taskDrawerOpen.value) taskDetail.value = updated
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

async function handleTaskUpdated(task: WorkTask) {
  taskDetail.value = task
  if (selected.value) await selectProject(selected.value.id)
  await refreshProjectList()
}

async function handleTaskDeleted() {
  taskDrawerOpen.value = false
  taskDetail.value = undefined
  if (selected.value) await selectProject(selected.value.id)
  await refreshProjectList()
}

onMounted(() => Promise.all([loadProjects(), loadDirectory(), loadTaskFields()]))
</script>

<template>
  <div class="work-projects-page">
    <ProductPageHeader kicker="任务" title="项目任务" description="按项目和阶段组织协作任务，随时查看进度。">
      <template #actions><a-button v-if="can('CREATE_PROJECT')" type="primary" @click="openNewProject">新建项目</a-button></template>
    </ProductPageHeader>
    <a-alert v-if="!can('VIEW')" type="warning" show-icon message="当前工作范围没有项目任务查看权限" />
    <div v-else class="work-project-layout">
      <section class="panel-card project-list-panel">
        <div class="panel-title"><strong>我的项目</strong><span>{{ projects.length }} 个</span></div>
        <a-spin :spinning="loading">
          <button v-for="project in projects" :key="project.id" :class="['project-list-item', { active: selected?.id === project.id }]" @click="selectProject(project.id)">
            <span><strong>{{ project.name }}</strong><small>{{ project.description || '暂无说明' }}</small></span>
            <ProductStatusTag :status="project.status" :label="projectStatusLabel(project.status)" />
          </button>
          <a-empty v-if="!loading && !projects.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无可见项目" />
        </a-spin>
      </section>

      <main v-if="selected" class="project-main">
        <section class="panel-card project-overview">
          <div class="project-overview__heading">
            <span><p class="eyebrow">项目概览</p><h2>{{ selected.name }}</h2><small>{{ selected.description || '暂无说明' }} · 负责人：{{ selected.owner.displayName }}</small></span>
            <span><a-button v-if="can('MANAGE_PROJECT')" @click="openEditProject">编辑项目</a-button><a-button v-if="can('MANAGE_PROJECT')" @click="openGroup()">新建任务组</a-button><a-button v-if="can('CREATE_TASK')" type="primary" @click="openTask()">新建任务</a-button></span>
          </div>
          <div class="project-overview__facts">
            <span><small>客户</small><strong>{{ selected.customerName || '未设置' }}</strong></span>
            <span><small>内部部门</small><strong>{{ selected.internalDepartmentName || '未设置' }}</strong></span>
            <span><small>项目阶段</small><strong>{{ selected.stage || '未设置' }}</strong></span>
            <span><small>计划周期</small><strong>{{ selected.startDate || '未设置' }} 至 {{ selected.dueDate || '未设置' }}</strong></span>
            <span v-if="selected.businessTitle"><small>关联业务</small><strong>{{ selected.businessTitle }}</strong></span>
          </div>
          <div v-if="selected.labels.length || selected.members.length" class="project-overview__relations">
            <span v-if="selected.labels.length"><small>标签</small><span><a-tag v-for="label in selected.labels" :key="label">{{ label }}</a-tag></span></span>
            <span v-if="selected.members.length"><small>项目成员</small><span><a-tag v-for="member in selected.members" :key="member.id">{{ member.person.displayName }} · {{ projectRoleLabel(member.projectRole) }}</a-tag></span></span>
          </div>
          <div class="project-metrics">
            <article><small>全部任务</small><strong>{{ summary.total }}</strong></article>
            <article><small>进行中 / 阻塞</small><strong>{{ summary.inProgress }}</strong></article>
            <article><small>已完成</small><strong>{{ summary.completed }}</strong></article>
            <article><small>任务平均进度</small><strong>{{ summary.progress }}%</strong></article>
          </div>
          <a-progress :percent="summary.progress" :status="summary.progress === 100 ? 'success' : 'active'" />
        </section>

        <section class="panel-card project-task-viewbar">
          <span><strong>项目任务</strong><small>列表、看板和日历读取当前项目的同一批任务。</small></span>
          <a-segmented v-model:value="viewMode" :options="[{value:'list',label:'列表'},{value:'board',label:'看板'},{value:'calendar',label:'日历'}]" />
        </section>

        <section v-for="group in viewMode === 'list' ? groupedTasks : []" :key="group.id" class="panel-card task-group-card">
          <div class="panel-title"><span><strong>{{ group.name }}</strong><small>{{ group.tasks.length }} 项任务</small></span><span><a-button v-if="can('MANAGE_PROJECT') && group.details" type="link" @click="openGroup(group.details)">设置任务组</a-button><a-button v-if="can('CREATE_TASK') && group.details" type="link" @click="openTask(group.details.id)">在此组新建</a-button></span></div>
          <div v-if="group.details" class="task-group-summary">
            <span><small>负责人</small><strong>{{ group.details.owner?.displayName || '未设置' }}</strong></span>
            <span><small>截止时间</small><strong>{{ group.details.dueTime ? productDateTime(group.details.dueTime) : '未设置' }}</strong></span>
            <span><small>状态</small><strong>{{ groupStatusLabel(group.details.status) }}</strong></span>
            <span><small>完成度</small><a-progress :percent="Number(group.details.progressPercent || 0)" size="small" /></span>
          </div>
          <div v-if="group.tasks.length" class="task-list">
            <article v-for="task in group.tasks" :key="task.id" class="task-row" @click="openTaskDetail(task.id)">
              <span class="task-status-dot" :data-status="task.status"></span>
              <span><strong>{{ task.title }}</strong><small>{{ ownerLabel(task) }} · {{ task.dueAt ? `截止 ${productDateTime(task.dueAt)}` : '未设置截止时间' }}</small></span>
              <span><a-tag :color="priorityLabel(task.priority) === '紧急' ? 'red' : 'default'">{{ priorityLabel(task.priority) }}</a-tag><a-tag :color="task.overdue ? 'red' : statusColor(task.status)">{{ task.statusName }}</a-tag></span>
              <a-progress :percent="Number(task.progressPercent)" size="small" :show-info="false" />
              <a-button v-if="can('UPDATE_TASK') && nextTaskAction(task.status)" size="small" :loading="busy === `advance:${task.id}`" @click.stop="advanceTask(task)">{{ nextTaskAction(task.status)?.label }}</a-button>
            </article>
          </div>
          <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="此任务组尚无任务" />
        </section>
        <section v-if="viewMode === 'list' && !groupedTasks.length" class="panel-card project-empty"><a-empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="先创建任务组，再新建首个项目任务" /></section>

        <div v-if="viewMode === 'board'" class="project-task-board">
          <section v-for="column in boardColumns" :key="column.key" class="project-task-board__column">
            <div class="panel-title"><strong>{{ column.name }}</strong><span>{{ column.tasks.length }}</span></div>
            <button v-for="task in column.tasks" :key="task.id" class="project-task-board__card" @click="openTaskDetail(task.id)">
              <strong>{{ task.title }}</strong><small>{{ taskGroupName(task) }} · {{ ownerLabel(task) }}</small>
              <a-progress :percent="Number(task.progressPercent)" size="small" />
            </button>
            <a-empty v-if="!column.tasks.length" :image="false" description="暂无任务" />
          </section>
        </div>

        <section v-if="viewMode === 'calendar'" class="panel-card project-task-calendar-wrap">
          <a-calendar v-model:value="calendarDate" class="project-task-calendar">
            <template #dateCellRender="{ current: date }">
              <button v-for="task in tasksForDate(date)" :key="task.id" class="project-task-calendar__item" @click="openTaskDetail(task.id)">{{ task.title }}</button>
            </template>
          </a-calendar>
        </section>
      </main>
    </div>
  </div>

  <a-modal v-model:open="createProjectOpen" :title="editingProject ? '编辑项目' : '新建项目'" width="760px" :confirm-loading="busy === 'project'" @ok="saveProject">
    <a-form layout="vertical">
      <div class="form-grid"><a-form-item label="项目名称" required><a-input v-model:value="projectForm.name" placeholder="例如：客户平台交付" /></a-form-item><a-form-item label="项目编码" extra="创建后不可修改"><a-input v-model:value="projectForm.code" :disabled="editingProject" placeholder="可留空自动生成" /></a-form-item></div>
      <a-form-item label="项目说明"><a-textarea v-model:value="projectForm.description" /></a-form-item>
      <div class="form-grid"><a-form-item label="客户"><a-input v-model:value="projectForm.customerName" /></a-form-item><a-form-item label="内部部门"><a-input v-model:value="projectForm.internalDepartmentName" /></a-form-item></div>
      <div class="form-grid"><a-form-item label="项目阶段"><a-input v-model:value="projectForm.stage" placeholder="例如：设计、开发、上线" /></a-form-item><a-form-item label="标签"><a-select v-model:value="projectForm.labels" mode="tags" :token-separators="[',','，']" /></a-form-item></div>
      <div class="form-grid"><a-form-item label="开始日期"><a-date-picker v-model:value="projectForm.startDate" value-format="YYYY-MM-DD" style="width:100%" /></a-form-item><a-form-item label="截止日期"><a-date-picker v-model:value="projectForm.dueDate" value-format="YYYY-MM-DD" style="width:100%" /></a-form-item></div>
      <div v-if="editingProject" class="form-grid"><a-form-item label="状态"><a-select v-model:value="projectForm.status" :options="[{value:'PLANNED',label:'筹备中'},{value:'ACTIVE',label:'进行中'},{value:'PAUSED',label:'已暂停'},{value:'COMPLETED',label:'已完成'},{value:'CANCELLED',label:'已取消'}]" /></a-form-item><a-form-item label="项目进度"><a-input-number v-model:value="projectForm.progressPercent" :min="0" :max="100" addon-after="%" style="width:100%" /></a-form-item></div>
      <a-form-item label="关联业务"><BusinessRecordPicker v-model="projectForm.business" :token="token || ''" /></a-form-item>
      <a-form-item v-if="context === 'system'" label="项目成员"><div class="project-member-editor"><div v-for="(member,index) in projectForm.members" :key="index" class="project-member-editor__row"><PersonSelect v-model="member.tenantMemberId" :people="directoryPeople" value-key="tenantMemberId" :allow-clear="false" /><a-select v-model:value="member.projectRole" :options="[{value:'MANAGER',label:'管理者'},{value:'MEMBER',label:'成员'},{value:'OBSERVER',label:'观察者'}]" /><a-button type="text" danger @click="projectForm.members.splice(index,1)">移除</a-button></div><a-button type="dashed" block @click="addProjectMember">添加项目成员</a-button></div></a-form-item>
    </a-form>
  </a-modal>
  <a-modal v-model:open="createGroupOpen" :title="editingGroupId ? '设置任务组' : '新建任务组'" :confirm-loading="busy === 'group'" @ok="saveGroup">
    <a-form layout="vertical"><a-form-item label="任务组名称" required><a-input v-model:value="groupForm.name" placeholder="例如：第一阶段" /></a-form-item><div class="form-grid"><a-form-item label="负责人"><PersonSelect v-model="groupForm.ownerId" :people="directoryPeople" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" /></a-form-item><a-form-item label="截止时间"><a-date-picker v-model:value="groupForm.dueTime" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></a-form-item></div><div class="form-grid"><a-form-item label="排序"><a-input-number v-model:value="groupForm.sortOrder" :min="0" style="width:100%" /></a-form-item><a-form-item label="完成度"><a-input-number v-model:value="groupForm.progressPercent" :min="0" :max="100" addon-after="%" style="width:100%" /></a-form-item></div><a-form-item v-if="editingGroupId" label="状态"><a-select v-model:value="groupForm.status" :options="[{value:'ACTIVE',label:'进行中'},{value:'COMPLETED',label:'已完成'},{value:'CANCELLED',label:'已取消'}]" /></a-form-item></a-form>
  </a-modal>
  <a-modal v-model:open="createTaskOpen" :title="editingTaskId ? '编辑项目任务' : taskForm.parentTaskId ? '新建子任务' : '新建项目任务'" width="760px" :confirm-loading="busy === 'task'" @ok="saveTask">
    <a-form layout="vertical"><a-form-item label="任务标题" required><a-input v-model:value="taskForm.title" placeholder="填写一个可直接执行和验收的任务" /></a-form-item><a-form-item label="说明"><a-textarea v-model:value="taskForm.description" /></a-form-item><div class="form-grid"><a-form-item label="任务组"><a-select v-model:value="taskForm.taskGroupId" allow-clear :options="selected?.taskGroups.map(group => ({ value: group.id, label: group.name }))" /></a-form-item><a-form-item label="优先级"><a-select v-model:value="taskForm.priority" :options="[{value:'LOW',label:'低'},{value:'NORMAL',label:'普通'},{value:'HIGH',label:'高'},{value:'URGENT',label:'紧急'}]" /></a-form-item></div><div class="form-grid"><a-form-item label="负责人" required><PersonSelect v-model="taskForm.ownerId" :people="directoryPeople" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" :allow-clear="false" /></a-form-item><a-form-item label="协作人"><PersonSelect v-model="taskForm.collaboratorIds" :people="directoryPeople" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" multiple :excluded-values="taskForm.ownerId ? [taskForm.ownerId] : []" /></a-form-item></div><div class="form-grid"><a-form-item label="关注人"><PersonSelect v-model="taskForm.followerIds" :people="directoryPeople" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" multiple /></a-form-item><a-form-item label="验收人"><PersonSelect v-model="taskForm.acceptanceIds" :people="directoryPeople" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" multiple /></a-form-item></div><div class="form-grid"><a-form-item label="预计工时"><a-input-number v-model:value="taskForm.estimatedHours" :min="0" :precision="2" addon-after="小时" style="width:100%" /></a-form-item><a-form-item label="标签"><a-select v-model:value="taskForm.labels" mode="tags" :token-separators="[',','，']" /></a-form-item></div><a-form-item label="关联业务"><BusinessRecordPicker v-model="taskForm.business" :token="token || ''" /></a-form-item><a-form-item label="检查清单"><div class="task-checklist-editor"><div v-for="(item,index) in taskForm.checklist" :key="item.id" class="task-checklist-editor__row"><a-checkbox v-model:checked="item.completed" /><a-input v-model:value="item.text" /><a-button type="text" danger @click="taskForm.checklist.splice(index,1)">移除</a-button></div><a-button type="dashed" block @click="addChecklistItem">添加清单事项</a-button></div></a-form-item><WorkConfiguredFields v-model="taskForm.configuredValues" :fields="taskFields" /><div class="form-grid"><a-form-item label="开始时间"><a-date-picker v-model:value="taskForm.startAt" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></a-form-item><a-form-item label="截止时间"><a-date-picker v-model:value="taskForm.dueAt" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></a-form-item></div></a-form>
  </a-modal>

  <a-drawer v-model:open="taskDrawerOpen" title="任务详情" width="760">
    <WorkTaskDetailPanel v-if="taskDetail" :task="taskDetail" :token="token || ''" :context="context" :people="directoryPeople" :can-file-view="canFile('VIEW')" :can-file-write="canFile('UPLOAD') && canFile('REFERENCE')" @updated="handleTaskUpdated" @deleted="handleTaskDeleted" @edit="editTask" @add-subtask="task => openTask(task.taskGroupId, task.id)" @open-subtask="openTaskDetail" />
  </a-drawer>
</template>
