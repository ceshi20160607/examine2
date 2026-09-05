<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { ApiError, api } from '../api'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { groupProjectTasks, nextTaskAction } from '../work-management'
import type { SystemDirectoryPerson, SystemPeopleDirectory, WorkConfigurationField, WorkPerson, WorkProject, WorkTask } from '../types'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import PersonSelect from '../components/PersonSelect.vue'
import WorkConfiguredFields from '../components/WorkConfiguredFields.vue'
import BusinessAttachmentsPanel from '../components/BusinessAttachmentsPanel.vue'
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
const projectForm = reactive({ code: '', name: '', description: '', startDate: '', dueDate: '' })
const groupForm = reactive({ name: '', sortOrder: 10 })
const taskForm = reactive({
  title: '', description: '', taskGroupId: undefined as number | undefined,
  priority: 'NORMAL' as WorkTask['priority'], ownerId: undefined as number | undefined,
  collaboratorIds: [] as number[], startAt: '', dueAt: '', configuredValues: {} as Record<string, unknown>,
})
const directoryPeople = computed<SystemDirectoryPerson[]>(() => props.context === 'system'
  ? directory.value.people
  : current.value ? [{
    tenantMemberId: current.value.accountId, systemMemberId: current.value.accountId,
    accountId: current.value.accountId, displayName: current.value.displayName,
    roleNames: [], tenantAdmin: false,
  }] : [])

const groupedTasks = computed(() => selected.value ? groupProjectTasks(selected.value.tasks, selected.value.taskGroups) : [])
const summary = computed(() => {
  const tasks = selected.value?.tasks || []
  const completed = tasks.filter(task => task.status === 'COMPLETED').length
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
  return ({ BACKLOG: '待规划', TODO: '待开始', IN_PROGRESS: '进行中', BLOCKED: '已阻塞', COMPLETED: '已完成', CANCELLED: '已取消' })[status]
}

function statusColor(status: WorkTask['status']) {
  return ({ BACKLOG: 'default', TODO: 'blue', IN_PROGRESS: 'processing', BLOCKED: 'red', COMPLETED: 'green', CANCELLED: 'default' })[status]
}

function priorityLabel(priority: WorkTask['priority']) {
  return ({ LOW: '低', NORMAL: '普通', HIGH: '高', URGENT: '紧急' })[priority]
}

function projectStatusLabel(status: string) {
  return ({ PLANNED: '筹备中', ACTIVE: '进行中', PAUSED: '已暂停', COMPLETED: '已完成', CANCELLED: '已取消' } as Record<string, string>)[status] || '筹备中'
}

function ownerLabel(task: WorkTask) {
  return task.owner.displayName
}

function historyLabel(code: string) {
  return ({ CREATED: '创建任务', UPDATED: '更新任务', STATUS_CHANGED: '更新状态', ATTACHMENT_ADDED: '添加附件', ATTACHMENT_REMOVED: '移除附件' } as Record<string, string>)[code] || '更新任务'
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

async function createProject() {
  if (!token.value || !projectForm.name.trim()) return
  busy.value = 'project'
  try {
    const project = await api<WorkProject>('/api/work/projects', {
      method: 'POST', body: JSON.stringify({
        code: projectForm.code.trim() || undefined, name: projectForm.name, description: projectForm.description || undefined,
        startDate: projectForm.startDate || undefined, dueDate: projectForm.dueDate || undefined, members: [],
      }),
    }, token.value)
    createProjectOpen.value = false
    Object.assign(projectForm, { code: '', name: '', description: '', startDate: '', dueDate: '' })
    message.success('项目已创建，你已成为项目负责人')
    await loadProjects(project.id)
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

function openGroup() {
  groupForm.name = ''
  groupForm.sortOrder = Math.max(0, ...(selected.value?.taskGroups.map(group => group.sortOrder) || [0])) + 10
  createGroupOpen.value = true
}

async function createGroup() {
  if (!token.value || !selected.value || !groupForm.name.trim()) return
  busy.value = 'group'
  try {
    selected.value = await api<WorkProject>(`/api/work/projects/${selected.value.id}/groups`, {
      method: 'POST', body: JSON.stringify(groupForm),
    }, token.value)
    createGroupOpen.value = false
    message.success('任务组已加入项目')
    await refreshProjectList()
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

function openTask(groupId?: number) {
  Object.assign(taskForm, {
    title: '', description: '', taskGroupId: groupId ?? selected.value?.taskGroups[0]?.id,
    priority: 'NORMAL', ownerId: props.context === 'system' ? current.value?.tenantMemberId || undefined : current.value?.accountId,
    collaboratorIds: [], startAt: '', dueAt: '', configuredValues: {},
  })
  createTaskOpen.value = true
}

async function createTask() {
  if (!token.value || !selected.value || !taskForm.title.trim() || !taskForm.ownerId) return
  busy.value = 'task'
  try {
    const task = await api<WorkTask>(`/api/work/projects/${selected.value.id}/tasks`, {
      method: 'POST', body: JSON.stringify({
        taskGroupId: taskForm.taskGroupId, title: taskForm.title, description: taskForm.description || undefined,
        priority: taskForm.priority, ...assignmentPayload(taskForm.ownerId, taskForm.collaboratorIds),
        startAt: taskForm.startAt || undefined, dueAt: taskForm.dueAt || undefined,
        configuredValues: taskForm.configuredValues,
      }),
    }, token.value)
    createTaskOpen.value = false
    message.success(`任务“${task.title}”已创建，状态为待开始`)
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
        expectedVersion: task.version, status: action.status,
        ...updateAssignmentPayload(task.owner, task.collaborators),
        priority: task.priority, progressPercent: action.status === 'COMPLETED' ? 100 : Math.max(10, Number(task.progressPercent)),
        startAt: task.startAt, dueAt: task.dueAt,
        businessType: task.businessType, businessId: task.businessId, businessTitle: task.businessTitle,
        configuredValues: task.configuredValues, comment: action.label,
      }),
    }, token.value)
    message.success(`任务已${action.label === '完成任务' ? '完成' : '开始'}`)
    if (selected.value) await selectProject(selected.value.id)
    await refreshProjectList()
    if (taskDrawerOpen.value) taskDetail.value = updated
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

onMounted(() => Promise.all([loadProjects(), loadDirectory(), loadTaskFields()]))
</script>

<template>
  <div class="work-projects-page">
    <ProductPageHeader kicker="任务" title="项目任务" description="按项目和阶段组织协作任务，随时查看进度。">
      <template #actions><a-button v-if="can('CREATE_PROJECT')" type="primary" @click="createProjectOpen = true">新建项目</a-button></template>
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
            <span><a-button v-if="can('MANAGE_PROJECT')" @click="openGroup">新建任务组</a-button><a-button v-if="can('CREATE_TASK')" type="primary" @click="openTask()">新建任务</a-button></span>
          </div>
          <div class="project-metrics">
            <article><small>全部任务</small><strong>{{ summary.total }}</strong></article>
            <article><small>进行中 / 阻塞</small><strong>{{ summary.inProgress }}</strong></article>
            <article><small>已完成</small><strong>{{ summary.completed }}</strong></article>
            <article><small>任务平均进度</small><strong>{{ summary.progress }}%</strong></article>
          </div>
          <a-progress :percent="summary.progress" :status="summary.progress === 100 ? 'success' : 'active'" />
        </section>

        <section v-for="group in groupedTasks" :key="group.id" class="panel-card task-group-card">
          <div class="panel-title"><span><strong>{{ group.name }}</strong><small>{{ group.tasks.length }} 项任务</small></span><a-button v-if="can('CREATE_TASK') && group.id !== 'ungrouped'" type="link" @click="openTask(Number(group.id))">在此组新建</a-button></div>
          <div v-if="group.tasks.length" class="task-list">
            <article v-for="task in group.tasks" :key="task.id" class="task-row" @click="openTaskDetail(task.id)">
              <span class="task-status-dot" :data-status="task.status"></span>
              <span><strong>{{ task.title }}</strong><small>{{ ownerLabel(task) }} · {{ task.dueAt ? `截止 ${productDateTime(task.dueAt)}` : '未设置截止时间' }}</small></span>
              <span><a-tag :color="priorityLabel(task.priority) === '紧急' ? 'red' : 'default'">{{ priorityLabel(task.priority) }}</a-tag><a-tag :color="statusColor(task.status)">{{ statusLabel(task.status) }}</a-tag></span>
              <a-progress :percent="Number(task.progressPercent)" size="small" :show-info="false" />
              <a-button v-if="can('UPDATE_TASK') && nextTaskAction(task.status)" size="small" :loading="busy === `advance:${task.id}`" @click.stop="advanceTask(task)">{{ nextTaskAction(task.status)?.label }}</a-button>
            </article>
          </div>
          <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="此任务组尚无任务" />
        </section>
        <section v-if="!groupedTasks.length" class="panel-card project-empty"><a-empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="先创建任务组，再新建首个项目任务" /></section>
      </main>
    </div>
  </div>

  <a-modal v-model:open="createProjectOpen" title="新建项目" :confirm-loading="busy === 'project'" @ok="createProject">
    <a-form layout="vertical"><div class="form-grid"><a-form-item label="项目名称" required><a-input v-model:value="projectForm.name" placeholder="例如：客户平台交付" /></a-form-item><a-form-item label="项目编码" extra="可不填，由系统自动生成"><a-input v-model:value="projectForm.code" placeholder="自动生成" /></a-form-item></div><a-form-item label="项目说明"><a-textarea v-model:value="projectForm.description" /></a-form-item><div class="form-grid"><a-form-item label="开始日期"><a-date-picker v-model:value="projectForm.startDate" value-format="YYYY-MM-DD" style="width:100%" /></a-form-item><a-form-item label="截止日期"><a-date-picker v-model:value="projectForm.dueDate" value-format="YYYY-MM-DD" style="width:100%" /></a-form-item></div></a-form>
  </a-modal>
  <a-modal v-model:open="createGroupOpen" title="新建任务组" :confirm-loading="busy === 'group'" @ok="createGroup">
    <a-form layout="vertical"><a-form-item label="任务组名称" required><a-input v-model:value="groupForm.name" placeholder="例如：第一阶段" /></a-form-item><a-form-item label="排序"><a-input-number v-model:value="groupForm.sortOrder" :min="0" style="width:100%" /></a-form-item></a-form>
  </a-modal>
  <a-modal v-model:open="createTaskOpen" title="新建项目任务" width="680px" :confirm-loading="busy === 'task'" @ok="createTask">
    <a-form layout="vertical"><a-form-item label="任务标题" required><a-input v-model:value="taskForm.title" placeholder="填写一个可直接执行和验收的任务" /></a-form-item><a-form-item label="说明"><a-textarea v-model:value="taskForm.description" /></a-form-item><div class="form-grid"><a-form-item label="任务组"><a-select v-model:value="taskForm.taskGroupId" allow-clear :options="selected?.taskGroups.map(group => ({ value: group.id, label: group.name }))" /></a-form-item><a-form-item label="优先级"><a-select v-model:value="taskForm.priority" :options="[{value:'LOW',label:'低'},{value:'NORMAL',label:'普通'},{value:'HIGH',label:'高'},{value:'URGENT',label:'紧急'}]" /></a-form-item></div><div class="form-grid"><a-form-item label="负责人" required><PersonSelect v-model="taskForm.ownerId" :people="directoryPeople" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" :allow-clear="false" /></a-form-item><a-form-item label="协作成员"><PersonSelect v-model="taskForm.collaboratorIds" :people="directoryPeople" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" multiple :excluded-values="taskForm.ownerId ? [taskForm.ownerId] : []" /></a-form-item></div><WorkConfiguredFields v-model="taskForm.configuredValues" :fields="taskFields" /><div class="form-grid"><a-form-item label="开始时间"><a-date-picker v-model:value="taskForm.startAt" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></a-form-item><a-form-item label="截止时间"><a-date-picker v-model:value="taskForm.dueAt" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></a-form-item></div></a-form>
  </a-modal>

  <a-drawer v-model:open="taskDrawerOpen" title="任务详情与版本时间线" width="620">
    <template v-if="taskDetail">
      <div class="task-detail-heading"><span><p class="eyebrow">项目任务</p><h2>{{ taskDetail.title }}</h2></span><a-tag :color="statusColor(taskDetail.status)">{{ statusLabel(taskDetail.status) }}</a-tag></div>
      <a-descriptions bordered :column="2" size="small"><a-descriptions-item label="负责人">{{ ownerLabel(taskDetail) }}</a-descriptions-item><a-descriptions-item label="协作成员">{{ taskDetail.collaborators.map(item => item.displayName).join('、') || '无' }}</a-descriptions-item><a-descriptions-item label="优先级">{{ priorityLabel(taskDetail.priority) }}</a-descriptions-item><a-descriptions-item label="进度">{{ taskDetail.progressPercent }}%</a-descriptions-item><a-descriptions-item label="截止时间">{{ taskDetail.dueAt ? productDateTime(taskDetail.dueAt) : '未设置' }}</a-descriptions-item><a-descriptions-item v-for="field in taskFields.filter(item => taskDetail?.configuredValues[item.fieldCode] !== undefined)" :key="field.fieldCode" :label="field.fieldName">{{ taskDetail.configuredValues[field.fieldCode] }}</a-descriptions-item></a-descriptions>
      <BusinessAttachmentsPanel v-if="canFile('VIEW')" :endpoint="`/api/business-attachments/work-tasks/${taskDetail.id}`" :context="context" :writable="canFile('UPLOAD') && canFile('REFERENCE')" title="任务附件" description="说明文档、交付物和处理结果保存在当前任务内。" compact />
      <a-button v-if="can('UPDATE_TASK') && nextTaskAction(taskDetail.status)" type="primary" style="margin-top:16px" :loading="busy === `advance:${taskDetail.id}`" @click="advanceTask(taskDetail)">{{ nextTaskAction(taskDetail.status)?.label }}</a-button>
      <section class="task-linked-logs">
        <div class="panel-title"><strong>关联工作日志</strong><span>{{ taskDetail.linkedLogs.length }} 条</span></div>
        <article v-for="log in taskDetail.linkedLogs" :key="log.id" class="task-linked-log">
          <span><strong>{{ log.title }}</strong><small>{{ log.authorName }} · {{ log.workDate }}<template v-if="log.durationMinutes"> · {{ log.durationMinutes }} 分钟</template></small></span>
          <p>{{ log.content }}</p>
          <a-tag :color="log.status === 'SUBMITTED' ? 'green' : log.status === 'WITHDRAWN' ? 'orange' : 'blue'">{{ log.status === 'SUBMITTED' ? '已提交' : log.status === 'WITHDRAWN' ? '已撤回' : '草稿' }}</a-tag>
        </article>
        <a-empty v-if="!taskDetail.linkedLogs.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="尚无工作日志关联此任务" />
      </section>
      <a-timeline class="task-history-timeline">
        <a-timeline-item v-for="history in taskDetail.history" :key="history.id" :color="history.actionCode === 'CREATED' ? 'blue' : 'green'">
          <strong>{{ historyLabel(history.actionCode) }}</strong><p>{{ history.comment || '任务状态已保存' }}</p><small>{{ productDateTime(history.changedAt) }}</small>
        </a-timeline-item>
      </a-timeline>
    </template>
  </a-drawer>
</template>
