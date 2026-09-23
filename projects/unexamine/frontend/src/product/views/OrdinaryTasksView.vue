<script setup lang="ts">
import { CalendarOutlined, PlusOutlined, ReloadOutlined, UnorderedListOutlined } from '@ant-design/icons-vue'
import dayjs, { type Dayjs } from 'dayjs'
import { message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, api } from '../api'
import PersonSelect from '../components/PersonSelect.vue'
import BusinessRecordPicker, { type BusinessRecordValue } from '../components/BusinessRecordPicker.vue'
import ProductDetailDrawer from '../components/ProductDetailDrawer.vue'
import ProductFilterBar from '../components/ProductFilterBar.vue'
import ProductFormDrawer from '../components/ProductFormDrawer.vue'
import ProductListWorkspace from '../components/ProductListWorkspace.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductState from '../components/ProductState.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import WorkConfiguredFields from '../components/WorkConfiguredFields.vue'
import WorkTaskDetailPanel from '../components/WorkTaskDetailPanel.vue'
import { allowsPermission } from '../permissions'
import { productDateTime } from '../presentation'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { nextTaskAction } from '../work-management'
import type { PeopleDirectoryOption, SystemPeopleDirectory, WorkChecklistItem, WorkConfigurationField, WorkPerson, WorkTask } from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const route = useRoute()
const router = useRouter()
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken || '' : systemTokens.value?.accessToken || '')
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const tasks = ref<WorkTask[]>([])
const loading = ref(false)
const error = ref('')
const saving = ref(false)
const advancingId = ref<number>()
const formOpen = ref(false)
const detailOpen = ref(false)
const detail = ref<WorkTask>()
const editingId = ref<number>()
const directory = ref<SystemPeopleDirectory>({ departments: [], people: [], permissionVersion: 0 })
const taskFields = ref<WorkConfigurationField[]>([])
const requestedFilter = String(route.query.taskState || '')
const filter = ref<'OPEN' | 'COMPLETED' | 'ALL'>(['OPEN', 'COMPLETED', 'ALL'].includes(requestedFilter) ? requestedFilter as 'OPEN' | 'COMPLETED' | 'ALL' : 'OPEN')
const viewMode = ref<'list' | 'board' | 'calendar'>('list')
const calendarDate = ref<Dayjs>(dayjs())
const form = reactive({
  parentTaskId: undefined as number | undefined,
  title: '', description: '', priority: 'NORMAL' as WorkTask['priority'], startAt: '', dueAt: '',
  ownerId: undefined as number | undefined, collaboratorIds: [] as number[], followerIds: [] as number[],
  acceptanceIds: [] as number[], estimatedHours: undefined as number | undefined, labels: [] as string[],
  checklist: [] as WorkChecklistItem[], configuredValues: {} as Record<string, unknown>,
  business: {} as BusinessRecordValue,
})

const directoryPeople = computed<PeopleDirectoryOption[]>(() => props.context === 'system'
  ? directory.value.people
  : current.value ? [{
    tenantMemberId: current.value.accountId, systemMemberId: current.value.accountId,
    accountId: current.value.accountId, displayName: current.value.displayName, roleNames: [],
  }] : [])
const visibleTasks = computed(() => tasks.value.filter((task) => {
  if (filter.value === 'COMPLETED') return ['COMPLETED', 'ARCHIVED'].includes(task.status)
  if (filter.value === 'OPEN') return !['COMPLETED', 'CANCELLED', 'ARCHIVED'].includes(task.status)
  return true
}))
const boardColumns = computed(() => [
  { key: 'TODO', name: '待开始', tasks: visibleTasks.value.filter(item => ['BACKLOG', 'TODO'].includes(item.status)) },
  { key: 'IN_PROGRESS', name: '进行中', tasks: visibleTasks.value.filter(item => ['IN_PROGRESS', 'BLOCKED'].includes(item.status)) },
  { key: 'WAITING_CONFIRMATION', name: '待验收', tasks: visibleTasks.value.filter(item => item.status === 'WAITING_CONFIRMATION') },
  { key: 'COMPLETED', name: '已完成', tasks: visibleTasks.value.filter(item => ['COMPLETED', 'ARCHIVED'].includes(item.status)) },
  { key: 'CANCELLED', name: '已取消', tasks: visibleTasks.value.filter(item => item.status === 'CANCELLED') },
].filter(column => column.tasks.length || ['TODO', 'IN_PROGRESS', 'WAITING_CONFIRMATION'].includes(column.key)))

function can(action: string) {
  return allowsPermission(current.value?.permissions, 'WORK', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'WORK', '*', action)
}
function canFile(action: string) {
  return allowsPermission(current.value?.permissions, 'FILE', '*', action)
}
function readable(reason: unknown) {
  if (reason instanceof ApiError) return reason.traceId ? `${reason.message}（追踪号：${reason.traceId}）` : reason.message
  return '任务操作失败，请稍后重试'
}
function statusLabel(task: WorkTask) { return task.statusName || task.status }
function priorityLabel(priority: WorkTask['priority']) { return ({ LOW: '低', NORMAL: '普通', HIGH: '高', URGENT: '紧急' })[priority] }
function memberIds(people: WorkPerson[]) { return people.map(item => item.tenantMemberId).filter((id): id is number => typeof id === 'number') }
function tasksForDate(value: Dayjs) { return visibleTasks.value.filter(task => task.dueAt?.slice(0, 10) === value.format('YYYY-MM-DD')) }

async function load() {
  if (!token.value || !can('VIEW')) return
  loading.value = true
  error.value = ''
  try { tasks.value = await api<WorkTask[]>('/api/work/tasks', {}, token.value) }
  catch (reason) { error.value = readable(reason) }
  finally { loading.value = false }
}
async function loadDirectory() {
  if (!token.value || props.context !== 'system') return
  try { directory.value = await api<SystemPeopleDirectory>('/api/system-directory', {}, token.value) }
  catch (reason) { message.error(readable(reason)) }
}
async function loadTaskFields() {
  if (!token.value) return
  try { taskFields.value = await api<WorkConfigurationField[]>('/api/work/configuration/effective/TASK', {}, token.value) }
  catch (reason) { message.error(readable(reason)) }
}

function resetForm(parentTaskId?: number) {
  Object.assign(form, {
    parentTaskId, title: '', description: '', priority: 'NORMAL', startAt: '', dueAt: '',
    ownerId: props.context === 'system' ? current.value?.tenantMemberId || undefined : current.value?.accountId,
    collaboratorIds: [], followerIds: [], acceptanceIds: [], estimatedHours: undefined,
    labels: [], checklist: [], configuredValues: {}, business: {},
  })
}
function beginCreate(parentTaskId?: number) { editingId.value = undefined; resetForm(parentTaskId); formOpen.value = true }
function beginEdit(task: WorkTask) {
  editingId.value = task.id
  Object.assign(form, {
    parentTaskId: task.parentTaskId, title: task.title, description: task.description || '', priority: task.priority,
    startAt: task.startAt || '', dueAt: task.dueAt || '',
    ownerId: props.context === 'system' ? task.owner.tenantMemberId : current.value?.accountId,
    collaboratorIds: props.context === 'system' ? memberIds(task.collaborators) : [],
    followerIds: memberIds(task.followers), acceptanceIds: memberIds(task.acceptors),
    estimatedHours: task.estimatedHours, labels: [...task.labels], checklist: task.checklist.map(item => ({ ...item })),
    configuredValues: { ...task.configuredValues }, business: { businessType: task.businessType, businessId: task.businessId, businessTitle: task.businessTitle },
  })
  formOpen.value = true
}
function addChecklistItem() { form.checklist.push({ id: crypto.randomUUID(), text: '', completed: false }) }
function assignmentPayload() {
  return props.context === 'system'
    ? { ownerTenantMemberId: form.ownerId, collaboratorTenantMemberIds: form.collaboratorIds,
        followerTenantMemberIds: form.followerIds, acceptanceTenantMemberIds: form.acceptanceIds }
    : { ownerAccountId: form.ownerId, collaboratorAccountIds: form.collaboratorIds,
        followerTenantMemberIds: form.followerIds, acceptanceTenantMemberIds: form.acceptanceIds }
}

async function saveTask() {
  if (!token.value || !form.title.trim() || !form.ownerId) return
  const currentTask = editingId.value ? detail.value : undefined
  saving.value = true
  try {
    const common = {
      parentTaskId: form.parentTaskId, title: form.title.trim(), description: form.description,
      priority: form.priority, startAt: form.startAt || undefined, dueAt: form.dueAt || undefined,
      ...assignmentPayload(), estimatedHours: form.estimatedHours, labels: form.labels,
      checklist: form.checklist.filter(item => item.text.trim()).map(item => ({ ...item, text: item.text.trim() })),
      configuredValues: form.configuredValues, ...form.business,
    }
    const updated = editingId.value && currentTask
      ? await api<WorkTask>(`/api/work/tasks/${editingId.value}`, { method: 'PUT', body: JSON.stringify({
          ...common, expectedVersion: currentTask.version, status: currentTask.status,
          progressPercent: currentTask.progressPercent, actualHours: currentTask.actualHours,
          businessType: currentTask.businessType, businessId: currentTask.businessId,
          businessTitle: currentTask.businessTitle, comment: '编辑任务信息',
        }) }, token.value)
      : await api<WorkTask>('/api/work/tasks', { method: 'POST', body: JSON.stringify(common) }, token.value)
    formOpen.value = false
    message.success(editingId.value ? '任务已更新' : '任务已创建')
    await load()
    await openDetailById(updated.id)
  } catch (reason) { message.error(readable(reason)) }
  finally { saving.value = false }
}

async function openDetail(task: WorkTask) { await openDetailById(task.id) }
async function openDetailById(taskId: number) {
  if (!token.value) return
  try {
    detail.value = await api<WorkTask>(`/api/work/tasks/${taskId}`, {}, token.value)
    detailOpen.value = true
    syncWorkRoute(taskId)
  } catch (reason) { message.error(readable(reason)) }
}
function syncWorkRoute(taskId?: number) {
  const query = { ...route.query } as Record<string, string | undefined>
  if (filter.value === 'OPEN') delete query.taskState
  else query.taskState = filter.value
  const openTaskId = taskId || (detailOpen.value ? detail.value?.id : undefined)
  if (openTaskId) query.taskId = String(openTaskId)
  else delete query.taskId
  void router.replace({ path: route.path, query })
}
function closeDetail() { detailOpen.value = false; detail.value = undefined; syncWorkRoute() }

async function advance(task: WorkTask) {
  if (!token.value) return
  const next = nextTaskAction(task.status)
  if (!next) return
  advancingId.value = task.id
  try {
    const updated = await api<WorkTask>(`/api/work/tasks/${task.id}`, {
      method: 'PUT', body: JSON.stringify({
        expectedVersion: task.version, status: next.status, title: task.title, description: task.description,
        ...(props.context === 'system' ? { ownerTenantMemberId: task.owner.tenantMemberId, collaboratorTenantMemberIds: memberIds(task.collaborators) } : { ownerAccountId: current.value?.accountId, collaboratorAccountIds: [] }),
        followerTenantMemberIds: memberIds(task.followers), acceptanceTenantMemberIds: memberIds(task.acceptors),
        priority: task.priority, progressPercent: Math.max(10, Number(task.progressPercent)), estimatedHours: task.estimatedHours,
        actualHours: task.actualHours, labels: task.labels, checklist: task.checklist,
        startAt: task.startAt, dueAt: task.dueAt, businessType: task.businessType, businessId: task.businessId,
        businessTitle: task.businessTitle, configuredValues: task.configuredValues, comment: next.label,
      }),
    }, token.value)
    message.success('任务已开始')
    detail.value = updated
    await load()
  } catch (reason) { message.error(readable(reason)) }
  finally { advancingId.value = undefined }
}
async function handleUpdated(task: WorkTask) { detail.value = task; await load() }
async function handleDeleted() { closeDetail(); await load() }

watch(filter, () => syncWorkRoute())
onMounted(async () => {
  await Promise.all([load(), loadDirectory(), loadTaskFields()])
  const taskId = Number(Array.isArray(route.query.taskId) ? route.query.taskId[0] : route.query.taskId)
  if (can('VIEW') && Number.isInteger(taskId) && taskId > 0) await openDetailById(taskId)
})
</script>

<template>
  <div class="ordinary-tasks-page">
    <ProductPageHeader kicker="任务" title="普通任务" description="管理不属于项目的个人或协作任务，列表、看板和日历使用同一份数据。">
      <template #actions><a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button></template>
      <template #primary><a-button v-if="can('CREATE_TASK')" type="primary" @click="beginCreate()"><PlusOutlined />新建任务</a-button></template>
    </ProductPageHeader>
    <ProductState v-if="!can('VIEW')" state="forbidden" title="当前工作空间没有任务查看权限" description="请联系管理员调整当前工作范围的任务权限。" />
    <template v-else>
      <ProductListWorkspace :loading="loading" :has-content="Boolean(visibleTasks.length)" :empty="!loading && !visibleTasks.length" :error="error" :empty-title="filter === 'OPEN' ? '当前没有待处理任务' : '当前筛选没有任务'" empty-description="可以切换任务状态范围，或创建一项新的普通任务。">
        <template #toolbar><ProductFilterBar label="普通任务筛选"><a-segmented v-model:value="filter" :options="[{value:'OPEN',label:'待处理'},{value:'COMPLETED',label:'已完成'},{value:'ALL',label:'全部'}]" /><template #tools><a-segmented v-model:value="viewMode" :options="[{value:'list',label:'列表'},{value:'board',label:'看板'},{value:'calendar',label:'日历'}]" /><span>共 {{ visibleTasks.length }} 项</span></template></ProductFilterBar></template>
        <template #state-actions="{ state }"><a-button v-if="state === 'error'" type="primary" @click="load">重试</a-button><a-button v-else-if="can('CREATE_TASK') && filter === 'OPEN'" type="primary" @click="beginCreate()">新建第一个任务</a-button></template>
        <a-spin :spinning="loading">
          <div v-if="visibleTasks.length && viewMode === 'list'" class="ordinary-task-list">
            <article v-for="task in visibleTasks" :key="task.id" class="panel-card ordinary-task-card" @click="openDetail(task)">
              <span class="task-status-dot" :data-status="task.overdue ? 'OVERDUE' : task.status" />
              <div><strong>{{ task.title }}</strong><p>{{ task.description || '没有补充说明' }}</p><small>{{ task.owner.displayName }} · {{ task.dueAt ? `截止 ${productDateTime(task.dueAt)}` : '未设置截止时间' }}</small></div>
              <div class="ordinary-task-card__meta"><a-tag :color="task.priority === 'URGENT' ? 'red' : task.priority === 'HIGH' ? 'orange' : 'default'">{{ priorityLabel(task.priority) }}</a-tag><ProductStatusTag :status="task.overdue ? 'OVERDUE' : task.status" :label="statusLabel(task)" /></div>
              <a-button v-if="can('UPDATE_TASK') && nextTaskAction(task.status)" :loading="advancingId === task.id" @click.stop="advance(task)">{{ nextTaskAction(task.status)?.label }}</a-button>
            </article>
          </div>
          <div v-else-if="visibleTasks.length && viewMode === 'board'" class="ordinary-task-board">
            <section v-for="column in boardColumns" :key="column.key" class="ordinary-task-board__column"><div class="panel-title"><strong>{{ column.name }}</strong><span>{{ column.tasks.length }}</span></div><button v-for="task in column.tasks" :key="task.id" class="ordinary-task-board__card" @click="openDetail(task)"><strong>{{ task.title }}</strong><small>{{ task.owner.displayName }}</small><a-progress :percent="Number(task.progressPercent)" size="small" /></button><a-empty v-if="!column.tasks.length" :image="false" description="暂无任务" /></section>
          </div>
          <a-calendar v-else-if="visibleTasks.length" v-model:value="calendarDate" class="ordinary-task-calendar"><template #dateCellRender="{ current: date }"><button v-for="task in tasksForDate(date)" :key="task.id" class="ordinary-task-calendar__item" @click="openDetail(task)">{{ task.title }}</button></template></a-calendar>
        </a-spin>
      </ProductListWorkspace>
    </template>
  </div>

  <ProductFormDrawer :open="formOpen" :title="editingId ? '编辑任务' : form.parentTaskId ? '新建子任务' : '新建普通任务'" :saving="saving" :save-label="editingId ? '保存修改' : '创建任务'" @close="formOpen = false" @save="saveTask">
    <a-form layout="vertical">
      <a-form-item label="任务标题" required><a-input v-model:value="form.title" placeholder="填写可直接执行和验收的任务" /></a-form-item>
      <a-form-item label="任务说明"><a-textarea v-model:value="form.description" :rows="3" /></a-form-item>
      <div class="form-grid"><a-form-item label="负责人" required><PersonSelect v-model="form.ownerId" :people="directoryPeople" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" :allow-clear="false" /></a-form-item><a-form-item label="协作人"><PersonSelect v-model="form.collaboratorIds" :people="directoryPeople" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" multiple :excluded-values="form.ownerId ? [form.ownerId] : []" /></a-form-item></div>
      <div class="form-grid"><a-form-item label="关注人"><PersonSelect v-model="form.followerIds" :people="directoryPeople" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" multiple :excluded-values="form.ownerId ? [form.ownerId] : []" /></a-form-item><a-form-item label="验收人"><PersonSelect v-model="form.acceptanceIds" :people="directoryPeople" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" multiple :excluded-values="form.ownerId ? [form.ownerId] : []" /></a-form-item></div>
      <div class="form-grid"><a-form-item label="优先级"><a-select v-model:value="form.priority" :options="[{value:'LOW',label:'低'},{value:'NORMAL',label:'普通'},{value:'HIGH',label:'高'},{value:'URGENT',label:'紧急'}]" /></a-form-item><a-form-item label="预计工时"><a-input-number v-model:value="form.estimatedHours" :min="0" :precision="2" addon-after="小时" style="width:100%" /></a-form-item></div>
      <div class="form-grid"><a-form-item label="开始时间"><a-date-picker v-model:value="form.startAt" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></a-form-item><a-form-item label="截止时间"><a-date-picker v-model:value="form.dueAt" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></a-form-item></div>
      <a-form-item label="标签"><a-select v-model:value="form.labels" mode="tags" :token-separators="[',','，']" placeholder="输入标签后回车" /></a-form-item>
      <a-form-item label="关联业务"><BusinessRecordPicker v-model="form.business" :token="token" /></a-form-item>
      <a-form-item label="检查清单"><div class="task-checklist-editor"><div v-for="(item,index) in form.checklist" :key="item.id" class="task-checklist-editor__row"><a-checkbox v-model:checked="item.completed" /><a-input v-model:value="item.text" placeholder="清单事项" /><a-button type="text" danger @click="form.checklist.splice(index,1)">移除</a-button></div><a-button type="dashed" block @click="addChecklistItem">添加清单事项</a-button></div></a-form-item>
      <WorkConfiguredFields v-model="form.configuredValues" :fields="taskFields" />
    </a-form>
  </ProductFormDrawer>

  <ProductDetailDrawer :open="detailOpen" title="任务详情" width="760" @close="closeDetail">
    <WorkTaskDetailPanel v-if="detail" :task="detail" :token="token" :context="context" :people="directoryPeople" :can-file-view="canFile('VIEW')" :can-file-write="canFile('UPLOAD') && canFile('REFERENCE')" @updated="handleUpdated" @deleted="handleDeleted" @edit="beginEdit" @add-subtask="task => beginCreate(task.id)" @open-subtask="openDetailById" />
  </ProductDetailDrawer>
</template>

<style scoped>
.ordinary-task-board{display:grid;grid-template-columns:repeat(3,minmax(230px,1fr));gap:12px;align-items:start;overflow:auto}.ordinary-task-board__column{display:grid;gap:10px;padding:12px;background:var(--surface-muted);border-radius:10px}.ordinary-task-board__card{display:grid;gap:6px;padding:12px;border:1px solid var(--border-color);border-radius:8px;background:var(--surface);text-align:left}.ordinary-task-board__card small{color:var(--text-secondary)}.ordinary-task-calendar__item{display:block;width:100%;margin:2px 0;padding:2px 5px;overflow:hidden;border:0;border-radius:4px;background:var(--color-primary-bg);color:var(--color-primary);text-align:left;text-overflow:ellipsis;white-space:nowrap}.task-checklist-editor{display:grid;gap:8px}.task-checklist-editor__row{display:grid;grid-template-columns:auto 1fr auto;gap:8px;align-items:center}@media(max-width:900px){.ordinary-task-board{grid-template-columns:repeat(5,260px)}}
</style>
