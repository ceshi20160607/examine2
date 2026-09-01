<script setup lang="ts">
import { CheckCircleOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { Empty, message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, api } from '../api'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import { allowsPermission } from '../permissions'
import { productDateTime } from '../presentation'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { nextTaskAction } from '../work-management'
import type { WorkTask } from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const tasks = ref<WorkTask[]>([])
const loading = ref(false)
const saving = ref(false)
const advancingId = ref<number>()
const createOpen = ref(false)
const detailOpen = ref(false)
const detail = ref<WorkTask>()
const filter = ref<'OPEN' | 'COMPLETED' | 'ALL'>('OPEN')
const form = reactive({ title: '', description: '', priority: 'NORMAL' as WorkTask['priority'], dueAt: '' })

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
  try {
    tasks.value = await api<WorkTask[]>('/api/work/tasks', {}, token.value)
  } catch (reason) { message.error(readable(reason)) }
  finally { loading.value = false }
}

function beginCreate() {
  Object.assign(form, { title: '', description: '', priority: 'NORMAL', dueAt: '' })
  createOpen.value = true
}

async function createTask() {
  if (!token.value || !form.title.trim() || !current.value?.accountId) return
  saving.value = true
  try {
    await api<WorkTask>('/api/work/tasks', {
      method: 'POST', body: JSON.stringify({
        title: form.title.trim(), description: form.description.trim() || undefined,
        priority: form.priority, ownerAccountId: current.value.accountId,
        dueAt: form.dueAt || undefined, collaboratorAccountIds: [], customValues: {},
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
  } catch (reason) { message.error(readable(reason)) }
}

async function advance(task: WorkTask) {
  if (!token.value) return
  const next = nextTaskAction(task.status)
  if (!next) return
  advancingId.value = task.id
  try {
    const updated = await api<WorkTask>(`/api/work/tasks/${task.id}`, {
      method: 'PUT', body: JSON.stringify({
        expectedVersion: task.version, status: next.status, ownerAccountId: task.ownerAccountId,
        priority: task.priority, progressPercent: next.status === 'COMPLETED' ? 100 : Math.max(10, Number(task.progressPercent)),
        startAt: task.startAt, dueAt: task.dueAt, collaboratorAccountIds: task.collaboratorAccountIds,
        customValues: task.customValues, comment: next.label,
      }),
    }, token.value)
    message.success(next.status === 'COMPLETED' ? '任务已完成' : '任务已开始')
    if (detailOpen.value) detail.value = updated
    await load()
  } catch (reason) { message.error(readable(reason)) }
  finally { advancingId.value = undefined }
}

onMounted(load)
</script>

<template>
  <div class="ordinary-tasks-page">
    <ProductPageHeader kicker="任务" title="普通任务" description="管理不属于项目的个人或协作任务。">
      <template #actions>
        <a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button>
        <a-button v-if="can('CREATE_TASK')" type="primary" @click="beginCreate"><PlusOutlined />新建任务</a-button>
      </template>
    </ProductPageHeader>
    <a-alert v-if="!can('VIEW')" type="warning" show-icon message="当前工作空间没有任务查看权限" />
    <template v-else>
      <section class="panel-card ordinary-task-toolbar">
        <a-segmented v-model:value="filter" :options="[{value:'OPEN',label:'待处理'},{value:'COMPLETED',label:'已完成'},{value:'ALL',label:'全部'}]" />
        <span>共 {{ visibleTasks.length }} 项</span>
      </section>
      <a-spin :spinning="loading">
        <div v-if="visibleTasks.length" class="ordinary-task-list">
          <article v-for="task in visibleTasks" :key="task.id" class="panel-card ordinary-task-card" @click="openDetail(task)">
            <span class="task-status-dot" :data-status="task.status" />
            <div><strong>{{ task.title }}</strong><p>{{ task.description || '没有补充说明' }}</p><small>{{ task.dueAt ? `截止 ${productDateTime(task.dueAt)}` : '未设置截止时间' }}</small></div>
            <div class="ordinary-task-card__meta"><a-tag :color="task.priority === 'URGENT' ? 'red' : task.priority === 'HIGH' ? 'orange' : 'default'">{{ priorityLabel(task.priority) }}</a-tag><ProductStatusTag :status="task.status" :label="statusLabel(task.status)" /></div>
            <a-button v-if="can('UPDATE_TASK') && nextTaskAction(task.status)" :type="nextTaskAction(task.status)?.status === 'COMPLETED' ? 'primary' : 'default'" :loading="advancingId === task.id" @click.stop="advance(task)">{{ nextTaskAction(task.status)?.label }}</a-button>
          </article>
        </div>
        <a-empty v-else-if="!loading" :image="Empty.PRESENTED_IMAGE_SIMPLE" :description="filter === 'OPEN' ? '当前没有待处理任务' : '当前筛选没有任务'">
          <a-button v-if="can('CREATE_TASK') && filter === 'OPEN'" type="primary" @click="beginCreate">新建第一个任务</a-button>
        </a-empty>
      </a-spin>
    </template>
  </div>

  <a-modal v-model:open="createOpen" title="新建普通任务" :confirm-loading="saving" ok-text="创建任务" @ok="createTask">
    <a-form layout="vertical">
      <a-form-item label="任务标题" required><a-input v-model:value="form.title" placeholder="填写可直接执行的任务" /></a-form-item>
      <a-form-item label="任务说明"><a-textarea v-model:value="form.description" :rows="3" /></a-form-item>
      <div class="form-grid"><a-form-item label="优先级"><a-select v-model:value="form.priority" :options="[{value:'LOW',label:'低'},{value:'NORMAL',label:'普通'},{value:'HIGH',label:'高'},{value:'URGENT',label:'紧急'}]" /></a-form-item><a-form-item label="截止时间"><a-date-picker v-model:value="form.dueAt" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></a-form-item></div>
    </a-form>
  </a-modal>

  <a-drawer v-model:open="detailOpen" title="任务详情" width="520">
    <template v-if="detail">
      <div class="ordinary-task-detail"><ProductStatusTag :status="detail.status" :label="statusLabel(detail.status)" /><h2>{{ detail.title }}</h2><p>{{ detail.description || '没有补充说明' }}</p></div>
      <a-descriptions bordered :column="1" size="small"><a-descriptions-item label="优先级">{{ priorityLabel(detail.priority) }}</a-descriptions-item><a-descriptions-item label="进度">{{ detail.progressPercent }}%</a-descriptions-item><a-descriptions-item label="截止时间">{{ detail.dueAt ? productDateTime(detail.dueAt) : '未设置' }}</a-descriptions-item></a-descriptions>
      <a-divider>处理记录</a-divider>
      <a-timeline v-if="detail.history.length"><a-timeline-item v-for="item in detail.history" :key="item.id"><strong>{{ item.actionCode === 'CREATED' ? '创建任务' : item.comment || '更新任务' }}</strong><p>{{ productDateTime(item.changedAt) }}</p></a-timeline-item></a-timeline>
      <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无处理记录" />
      <a-button v-if="can('UPDATE_TASK') && nextTaskAction(detail.status)" type="primary" block :loading="advancingId === detail.id" @click="advance(detail)"><CheckCircleOutlined />{{ nextTaskAction(detail.status)?.label }}</a-button>
    </template>
  </a-drawer>
</template>
