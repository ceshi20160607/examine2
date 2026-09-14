<script setup lang="ts">
import { CheckCircleOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { Empty, message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, api } from '../api'
import ProductDetailDrawer from '../components/ProductDetailDrawer.vue'
import ProductFilterBar from '../components/ProductFilterBar.vue'
import ProductFormDrawer from '../components/ProductFormDrawer.vue'
import ProductListWorkspace from '../components/ProductListWorkspace.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductState from '../components/ProductState.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import PersonSelect from '../components/PersonSelect.vue'
import WorkConfiguredFields from '../components/WorkConfiguredFields.vue'
import { allowsPermission } from '../permissions'
import { productDateTime } from '../presentation'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { nextTaskAction } from '../work-management'
import type { SystemDirectoryPerson, SystemPeopleDirectory, WorkConfigurationField, WorkPerson, WorkTask } from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const route = useRoute()
const router = useRouter()
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const tasks = ref<WorkTask[]>([])
const loading = ref(false)
const error = ref('')
const saving = ref(false)
const advancingId = ref<number>()
const createOpen = ref(false)
const detailOpen = ref(false)
const detail = ref<WorkTask>()
const directory = ref<SystemPeopleDirectory>({ departments: [], people: [], permissionVersion: 0 })
const taskFields = ref<WorkConfigurationField[]>([])
const requestedFilter = String(route.query.taskState || '')
const filter = ref<'OPEN' | 'COMPLETED' | 'ALL'>(['OPEN', 'COMPLETED', 'ALL'].includes(requestedFilter)
  ? requestedFilter as 'OPEN' | 'COMPLETED' | 'ALL' : 'OPEN')
const form = reactive({
  title: '', description: '', priority: 'NORMAL' as WorkTask['priority'], dueAt: '',
  ownerId: undefined as number | undefined, collaboratorIds: [] as number[],
  configuredValues: {} as Record<string, unknown>,
})
const directoryPeople = computed<SystemDirectoryPerson[]>(() => props.context === 'system'
  ? directory.value.people
  : current.value ? [{
    tenantMemberId: current.value.accountId, systemMemberId: current.value.accountId,
    accountId: current.value.accountId, displayName: current.value.displayName,
    roleNames: [], tenantAdmin: false,
  }] : [])

const visibleTasks = computed(() => tasks.value.filter((task) => {
  if (filter.value === 'COMPLETED') return task.status === 'COMPLETED'
  if (filter.value === 'OPEN') return !['COMPLETED', 'CANCELLED'].includes(task.status)
  return true
}))

function can(action: string) {
  return allowsPermission(current.value?.permissions, 'WORK', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'WORK', '*', action)
}

function readable(reason: unknown) {
  if (reason instanceof ApiError) return reason.traceId ? `${reason.message}（追踪号：${reason.traceId}）` : reason.message
  return '任务操作失败，请稍后重试'
}

function statusLabel(status: WorkTask['status']) {
  return ({ BACKLOG: '待规划', TODO: '待开始', IN_PROGRESS: '进行中', BLOCKED: '已阻塞', COMPLETED: '已完成', CANCELLED: '已取消' })[status]
}

function priorityLabel(priority: WorkTask['priority']) {
  return ({ LOW: '低', NORMAL: '普通', HIGH: '高', URGENT: '紧急' })[priority]
}

async function load() {
  if (!token.value || !can('VIEW')) return
  loading.value = true
  error.value = ''
  try {
    tasks.value = await api<WorkTask[]>('/api/work/tasks', {}, token.value)
  } catch (reason) { error.value = readable(reason) }
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

function beginCreate() {
  Object.assign(form, {
    title: '', description: '', priority: 'NORMAL', dueAt: '',
    ownerId: props.context === 'system' ? current.value?.tenantMemberId || undefined : current.value?.accountId,
    collaboratorIds: [], configuredValues: {},
  })
  createOpen.value = true
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

async function createTask() {
  if (!token.value || !form.title.trim() || !form.ownerId) return
  saving.value = true
  try {
    await api<WorkTask>('/api/work/tasks', {
      method: 'POST', body: JSON.stringify({
        title: form.title.trim(), description: form.description.trim() || undefined,
        priority: form.priority, dueAt: form.dueAt || undefined,
        ...assignmentPayload(form.ownerId, form.collaboratorIds), configuredValues: form.configuredValues,
      }),
    }, token.value)
    createOpen.value = false
    message.success('任务已创建')
    await load()
  } catch (reason) { message.error(readable(reason)) }
  finally { saving.value = false }
}

async function openDetail(task: WorkTask) {
  if (!token.value) return
  try {
    detail.value = await api<WorkTask>(`/api/work/tasks/${task.id}`, {}, token.value)
    detailOpen.value = true
    syncWorkRoute(task.id)
  } catch (reason) { message.error(readable(reason)) }
}

function syncWorkRoute(taskId?: number) {
  const query = { ...route.query } as Record<string, string | undefined>
  if (filter.value === 'OPEN') delete query.taskState
  else query.taskState = filter.value
  const openTaskId = taskId || (detailOpen.value ? detail.value?.id : undefined)
  if (openTaskId) query.taskId = String(openTaskId)
  else delete query.taskId
  const keys = new Set([...Object.keys(route.query), ...Object.keys(query)])
  if (![...keys].every(key => String(route.query[key] ?? '') === String(query[key] ?? ''))) {
    void router.replace({ path: route.path, query })
  }
}

function closeDetail() {
  detailOpen.value = false
  detail.value = undefined
  syncWorkRoute()
}

async function advance(task: WorkTask) {
  if (!token.value) return
  const next = nextTaskAction(task.status)
  if (!next) return
  advancingId.value = task.id
  try {
    const updated = await api<WorkTask>(`/api/work/tasks/${task.id}`, {
      method: 'PUT', body: JSON.stringify({
        expectedVersion: task.version, status: next.status,
        ...updateAssignmentPayload(task.owner, task.collaborators),
        priority: task.priority, progressPercent: next.status === 'COMPLETED' ? 100 : Math.max(10, Number(task.progressPercent)),
        startAt: task.startAt, dueAt: task.dueAt,
        businessType: task.businessType, businessId: task.businessId, businessTitle: task.businessTitle,
        configuredValues: task.configuredValues, comment: next.label,
      }),
    }, token.value)
    message.success(next.status === 'COMPLETED' ? '任务已完成' : '任务已开始')
    if (detailOpen.value) detail.value = updated
    await load()
  } catch (reason) { message.error(readable(reason)) }
  finally { advancingId.value = undefined }
}

watch(filter, () => syncWorkRoute())

onMounted(async () => {
  await Promise.all([load(), loadDirectory(), loadTaskFields()])
  const taskId = Number(Array.isArray(route.query.taskId) ? route.query.taskId[0] : route.query.taskId)
  if (can('VIEW') && Number.isInteger(taskId) && taskId > 0) await openDetail({ id: taskId } as WorkTask)
})
</script>

<template>
  <div class="ordinary-tasks-page">
    <ProductPageHeader kicker="任务" title="普通任务" description="管理不属于项目的个人或协作任务。">
      <template #actions><a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button></template>
      <template #primary><a-button v-if="can('CREATE_TASK')" type="primary" @click="beginCreate"><PlusOutlined />新建任务</a-button></template>
    </ProductPageHeader>
    <ProductState v-if="!can('VIEW')" state="forbidden" title="当前工作空间没有任务查看权限" description="请联系管理员调整当前工作范围的任务权限。" />
    <template v-else>
      <ProductListWorkspace :loading="loading" :has-content="Boolean(visibleTasks.length)" :empty="!loading && !visibleTasks.length" :error="error"
        :empty-title="filter === 'OPEN' ? '当前没有待处理任务' : '当前筛选没有任务'"
        empty-description="可以切换任务状态范围，或创建一项新的普通任务。">
        <template #toolbar><ProductFilterBar label="普通任务筛选"><a-segmented v-model:value="filter" :options="[{value:'OPEN',label:'待处理'},{value:'COMPLETED',label:'已完成'},{value:'ALL',label:'全部'}]" /><template #tools><span>共 {{ visibleTasks.length }} 项</span></template></ProductFilterBar></template>
        <template #state-actions="{ state }">
          <a-button v-if="state === 'error'" type="primary" @click="load">重试</a-button>
          <a-button v-else-if="can('CREATE_TASK') && filter === 'OPEN'" type="primary" @click="beginCreate">新建第一个任务</a-button>
        </template>
        <a-spin :spinning="loading">
          <div v-if="visibleTasks.length" class="ordinary-task-list">
            <article v-for="task in visibleTasks" :key="task.id" class="panel-card ordinary-task-card" @click="openDetail(task)">
              <span class="task-status-dot" :data-status="task.status" />
              <div><strong>{{ task.title }}</strong><p>{{ task.description || '没有补充说明' }}</p><small>{{ task.owner.displayName }} · {{ task.dueAt ? `截止 ${productDateTime(task.dueAt)}` : '未设置截止时间' }}</small></div>
              <div class="ordinary-task-card__meta"><a-tag :color="task.priority === 'URGENT' ? 'red' : task.priority === 'HIGH' ? 'orange' : 'default'">{{ priorityLabel(task.priority) }}</a-tag><ProductStatusTag :status="task.status" :label="statusLabel(task.status)" /></div>
              <a-button v-if="can('UPDATE_TASK') && nextTaskAction(task.status)" :type="nextTaskAction(task.status)?.status === 'COMPLETED' ? 'primary' : 'default'" :loading="advancingId === task.id" @click.stop="advance(task)">{{ nextTaskAction(task.status)?.label }}</a-button>
            </article>
          </div>
        </a-spin>
      </ProductListWorkspace>
    </template>
  </div>

  <ProductFormDrawer :open="createOpen" title="新建普通任务" :saving="saving" save-label="创建任务" @close="createOpen = false" @save="createTask">
    <a-form layout="vertical">
      <a-form-item label="任务标题" required><a-input v-model:value="form.title" placeholder="填写可直接执行的任务" /></a-form-item>
      <a-form-item label="任务说明"><a-textarea v-model:value="form.description" :rows="3" /></a-form-item>
      <a-form-item label="负责人" required><PersonSelect v-model="form.ownerId" :people="directoryPeople" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" :allow-clear="false" /></a-form-item>
      <a-form-item label="协作成员"><PersonSelect v-model="form.collaboratorIds" :people="directoryPeople" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" multiple :excluded-values="form.ownerId ? [form.ownerId] : []" /></a-form-item>
      <div class="form-grid"><a-form-item label="优先级"><a-select v-model:value="form.priority" :options="[{value:'LOW',label:'低'},{value:'NORMAL',label:'普通'},{value:'HIGH',label:'高'},{value:'URGENT',label:'紧急'}]" /></a-form-item><a-form-item label="截止时间"><a-date-picker v-model:value="form.dueAt" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></a-form-item></div>
      <WorkConfiguredFields v-model="form.configuredValues" :fields="taskFields" />
    </a-form>
  </ProductFormDrawer>

  <ProductDetailDrawer :open="detailOpen" title="任务详情" @close="closeDetail">
    <template v-if="detail">
      <div class="ordinary-task-detail"><ProductStatusTag :status="detail.status" :label="statusLabel(detail.status)" /><h2>{{ detail.title }}</h2><p>{{ detail.description || '没有补充说明' }}</p></div>
      <a-descriptions bordered :column="1" size="small"><a-descriptions-item label="负责人">{{ detail.owner.displayName }}<small v-if="detail.owner.departmentName"> · {{ detail.owner.departmentName }}</small></a-descriptions-item><a-descriptions-item label="协作成员">{{ detail.collaborators.map(item => item.displayName).join('、') || '无' }}</a-descriptions-item><a-descriptions-item label="优先级">{{ priorityLabel(detail.priority) }}</a-descriptions-item><a-descriptions-item label="进度">{{ detail.progressPercent }}%</a-descriptions-item><a-descriptions-item label="截止时间">{{ detail.dueAt ? productDateTime(detail.dueAt) : '未设置' }}</a-descriptions-item><a-descriptions-item v-for="field in taskFields.filter(item => detail?.configuredValues[item.fieldCode] !== undefined)" :key="field.fieldCode" :label="field.fieldName">{{ detail.configuredValues[field.fieldCode] }}</a-descriptions-item></a-descriptions>
      <a-divider>处理记录</a-divider>
      <a-timeline v-if="detail.history.length"><a-timeline-item v-for="item in detail.history" :key="item.id"><strong>{{ item.actionCode === 'CREATED' ? '创建任务' : item.comment || '更新任务' }}</strong><p>{{ productDateTime(item.changedAt) }}</p></a-timeline-item></a-timeline>
      <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无处理记录" />
    </template>
    <template #primary><a-button v-if="detail && can('UPDATE_TASK') && nextTaskAction(detail.status)" type="primary" :loading="advancingId === detail.id" @click="advance(detail)"><CheckCircleOutlined />{{ nextTaskAction(detail.status)?.label }}</a-button></template>
  </ProductDetailDrawer>
</template>
