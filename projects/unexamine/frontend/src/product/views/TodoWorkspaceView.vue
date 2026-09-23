<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Empty, message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { ApiError, api } from '../api'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { runtimeIdempotency } from '../flow-runtime'
import { todoActionLabel, todoNeedsComment, todoNeedsTarget, todoStatusLabel, todoTypeLabel } from '../todo'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import PersonSelect from '../components/PersonSelect.vue'
import { productDateTime } from '../presentation'
import type { SystemPeopleDirectory, TodoHandleResult, TodoItemView, TodoOpenResult } from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const router = useRouter()
const emit = defineEmits<{
  openFlow: [target: { taskId?: number; instanceId?: number }]
  openWork: [target: { taskId: number }]
}>()
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const rows = ref<TodoItemView[]>([])
const directory = ref<SystemPeopleDirectory>({ departments: [], people: [], permissionVersion: 0 })
const loading = ref(false)
const busy = ref(false)
const status = ref('PENDING')
const type = ref('ALL')
const actionOpen = ref(false)
const selected = ref<TodoItemView>()
const action = ref('APPROVE')
const actionForm = reactive({ comment: '', targetTenantMemberId: undefined as number | undefined })
const lastResult = ref<TodoHandleResult>()
const openingTodoId = ref<number>()
let active = true
let listGeneration = 0
let directoryGeneration = 0
let openGeneration = 0
const identity = computed(() => {
  const context = current.value
  return JSON.stringify([props.context, context?.platformId, context?.accountId, context?.systemId, context?.tenantId,
    context?.memberId, context?.tenantMemberId, context?.contextRevision, context?.roleIds, context?.permissions, context?.dataScopes])
})
const groups = computed(() => [
  { key: 'APPROVAL', label: '需要审批', description: '需要你作出明确决定的流程事项', rows: rows.value.filter(item => item.todoType === 'APPROVAL') },
  { key: 'WORK', label: '需要推进', description: '由你负责推进的任务事项', rows: rows.value.filter(item => item.todoType === 'WORK') },
  { key: 'REMINDER', label: '需要关注', description: '联系、提醒和其他需要关注的事项', rows: rows.value.filter(item => !['APPROVAL', 'WORK'].includes(item.todoType)) },
].filter(group => group.rows.length))

function can(actionCode: string) {
  return allowsPermission(current.value?.permissions, 'TODO', contextCode.value, actionCode)
    || allowsPermission(current.value?.permissions, 'TODO', '*', actionCode)
}

function requestContext() {
  return { token: token.value, identity: identity.value }
}

function requestCurrent(context: ReturnType<typeof requestContext>) {
  return active && context.token === token.value && context.identity === identity.value
}

function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
}

function statusColor(value: string) {
  if (value === 'COMPLETED') return 'green'
  if (value === 'PENDING') return 'blue'
  if (value === 'INVALID') return 'red'
  return 'orange'
}

function priorityLabel(value: string) {
  return ({ URGENT: '紧急', HIGH: '高', NORMAL: '普通', LOW: '低' } as Record<string, string>)[value] || '普通'
}

function priorityColor(value: string) {
  return ({ URGENT: 'red', HIGH: 'orange', NORMAL: 'blue', LOW: 'default' } as Record<string, string>)[value] || 'default'
}

async function load() {
  if (!token.value || !can('VIEW')) return
  const context = requestContext()
  const request = ++listGeneration
  loading.value = true
  try {
    const result = await api<TodoItemView[]>(`/api/todos?status=${encodeURIComponent(status.value)}&type=${encodeURIComponent(type.value)}`, {}, context.token)
    if (requestCurrent(context) && request === listGeneration) rows.value = result
  } catch (error) {
    if (requestCurrent(context) && request === listGeneration) message.error(describeError(error))
  } finally { if (requestCurrent(context) && request === listGeneration) loading.value = false }
}

async function loadDirectory() {
  if (!token.value || props.context !== 'system') return
  const context = requestContext()
  const request = ++directoryGeneration
  try {
    const result = await api<SystemPeopleDirectory>('/api/system-directory', {}, context.token)
    if (requestCurrent(context) && request === directoryGeneration) directory.value = result
  } catch (error) { if (requestCurrent(context) && request === directoryGeneration) message.error(describeError(error)) }
}

async function openSource(todo: TodoItemView) {
  if (!['FLOW_TASK', 'WORK_TASK', 'RUNTIME_RECORD'].includes(todo.sourceType) || !token.value || openingTodoId.value) return
  const context = requestContext()
  const request = ++openGeneration
  openingTodoId.value = todo.id
  try {
    // 只传待办 ID，由服务端重查当前所有权及 Flow 可见权限后生成跳转地址。
    const result = await api<TodoOpenResult>(`/api/todos/${todo.id}/open`, { method: 'POST' }, context.token)
    if (!requestCurrent(context) || request !== openGeneration) return
    if (result.todoId !== todo.id || !result.route) {
      message.error('待办打开结果与当前事项不匹配，请刷新待办')
      return
    }
    if (todo.sourceType === 'WORK_TASK' && Number(todo.sourceId)) emit('openWork', { taskId: Number(todo.sourceId) })
    else await router.push(result.route)
  } catch (reason) {
    if (!requestCurrent(context) || request !== openGeneration) return
    if (reason instanceof ApiError && reason.httpStatus === 401) message.error('请重新登录后打开此待办')
    else if (reason instanceof ApiError && [403, 404].includes(reason.httpStatus || 0)) {
      message.error('当前已无法打开此待办或来源，可能已处理或权限已变化，请刷新待办')
    } else message.error(describeError(reason))
  } finally { if (requestCurrent(context) && request === openGeneration) openingTodoId.value = undefined }
}

function openAction(todo: TodoItemView, actionCode: string) {
  if (!can('HANDLE') || !todo.availableActions.includes(actionCode)) return
  // 审批快捷入口也进入公共运行页，复用冻结意见、表单修订号、领取和选人规则，避免另造一套审批弹窗。
  if (todo.sourceType === 'FLOW_TASK') { void openSource(todo); return }
  selected.value = todo
  action.value = actionCode
  Object.assign(actionForm, { comment: '', targetTenantMemberId: undefined })
  actionOpen.value = true
}

async function handleTodo() {
  if (!token.value || !selected.value || !can('HANDLE')) return
  if (todoNeedsComment(action.value) && !actionForm.comment.trim()) return message.warning('拒绝或退回必须填写可追溯原因')
  if (todoNeedsTarget(action.value) && !actionForm.targetTenantMemberId) return message.warning('转交必须选择目标成员')
  const context = requestContext()
  const actionCode = action.value
  const todoId = selected.value.id
  busy.value = true
  try {
    const result = await api<TodoHandleResult>(`/api/todos/${todoId}/actions`, {
      method: 'POST', body: JSON.stringify({
        actionCode,
        comment: actionForm.comment || undefined,
        targetTenantMemberId: actionForm.targetTenantMemberId,
        idempotencyKey: runtimeIdempotency(`todo-${actionCode.toLowerCase()}`),
      }),
    }, context.token)
    if (!requestCurrent(context)) return
    lastResult.value = result
    actionOpen.value = false
    await load()
    if (requestCurrent(context)) message.success(`待办已${todoActionLabel(actionCode)}，目标对象状态已读回`)
  } catch (error) {
    if (!requestCurrent(context)) return
    message.error(describeError(error))
    await load()
  } finally { if (requestCurrent(context)) busy.value = false }
}

function openLastResult() {
  if (lastResult.value?.todo.sourceType === 'FLOW_TASK') { void openSource(lastResult.value.todo); return }
  const instanceId = lastResult.value?.targetResult?.instance.id
  if (instanceId) emit('openFlow', { instanceId })
}

watch([status, type], () => void load())
watch([identity, token], () => {
  listGeneration++
  directoryGeneration++
  openGeneration++
  rows.value = []
  directory.value = { departments: [], people: [], permissionVersion: 0 }
  selected.value = undefined
  actionOpen.value = false
  lastResult.value = undefined
  openingTodoId.value = undefined
  loading.value = false
  busy.value = false
  if (token.value) { void load(); void loadDirectory() }
}, { flush: 'sync' })
onMounted(() => Promise.all([load(), loadDirectory()]))
onBeforeUnmount(() => { active = false; listGeneration++; directoryGeneration++; openGeneration++; rows.value = []; lastResult.value = undefined })
</script>

<template>
  <div class="todo-workspace-page">
    <ProductPageHeader :kicker="context === 'platform' ? '全部工作' : '当前系统'" title="待办" description="集中处理需要你审批、联系或跟进的事项。">
      <template #actions><a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button></template>
    </ProductPageHeader>
    <a-alert v-if="!can('VIEW')" type="warning" show-icon message="当前工作范围没有待办查看权限" description="待办入口不会混入其他系统或平台的数据。" />
    <template v-else>
      <a-alert v-if="lastResult" class="todo-result-alert" type="success" show-icon :message="`处理结果已保存：${lastResult.resultReference}`">
        <template #action><a-button size="small" @click="openLastResult">查看目标结果</a-button></template>
      </a-alert>
      <section class="panel-card todo-filter-bar">
        <a-segmented v-model:value="status" :options="[{ value: 'PENDING', label: '待处理' }, { value: 'COMPLETED', label: '已完成' }, { value: 'ALL', label: '全部' }]" />
        <a-select v-model:value="type" :options="[
          { value: 'ALL', label: '全部来源' }, { value: 'APPROVAL', label: '审批' },
          { value: 'CONTACT', label: '今日联系' }, { value: 'WORK', label: '工作任务' }, { value: 'OTHER', label: '其他' },
        ]" />
        <span>共 {{ rows.length }} 项</span>
      </section>
      <a-spin :spinning="loading">
        <div v-if="rows.length" class="todo-groups">
          <section v-for="group in groups" :key="group.key" class="todo-group">
            <header class="todo-group__header"><span><strong>{{ group.label }}</strong><small>{{ group.description }}</small></span><a-tag>{{ group.rows.length }}</a-tag></header>
            <div class="todo-list">
          <article v-for="todo in group.rows" :key="todo.id" class="panel-card todo-card">
            <div class="todo-card__main">
              <span class="todo-card__type">{{ todoTypeLabel(todo.todoType) }}</span>
              <div><span class="todo-card__title"><strong>{{ todo.title }}</strong><a-tag :color="priorityColor(todo.priority)">{{ priorityLabel(todo.priority) }}</a-tag><a-tag :color="statusColor(todo.status)">{{ todoStatusLabel(todo.status) }}</a-tag></span><p>{{ todo.summary }}</p><small>{{ todo.sourceLabel }} · {{ todo.objectName }} · 发起人 {{ todo.initiatorName }}<template v-if="todo.dueAt"> · 截止 {{ productDateTime(todo.dueAt) }}</template></small></div>
            </div>
            <div class="todo-card__actions">
              <a-button v-if="['FLOW_TASK','WORK_TASK','RUNTIME_RECORD'].includes(todo.sourceType)" :loading="openingTodoId === todo.id" :disabled="Boolean(openingTodoId && openingTodoId !== todo.id)" @click="openSource(todo)">打开来源</a-button>
              <template v-if="can('HANDLE')">
                <a-button v-for="item in todo.availableActions.filter(action => !['DELEGATE','REVOKE_DELEGATION'].includes(action))" :key="item" :type="['APPROVE','RESUBMIT'].includes(item) ? 'primary' : 'default'" @click="openAction(todo, item)">{{ todoActionLabel(item) }}</a-button>
              </template>
            </div>
          </article>
            </div>
          </section>
        </div>
        <a-empty v-else-if="!loading" :image="Empty.PRESENTED_IMAGE_SIMPLE" :description="status === 'PENDING' ? '当前没有未完成待办' : '当前筛选没有待办记录'" />
      </a-spin>
    </template>
  </div>

  <a-modal v-model:open="actionOpen" :title="`处理待办 · ${todoActionLabel(action)}`" :confirm-loading="busy" @ok="handleTodo">
    <a-form layout="vertical">
      <a-alert type="info" show-icon :message="selected?.title" description="提交后会重新读取业务对象与待办状态，避免重复处理。" style="margin-bottom:16px" />
      <a-form-item label="处理意见" :required="todoNeedsComment(action)"><a-textarea v-model:value="actionForm.comment" :rows="4" placeholder="记录处理结论或可追溯原因" /></a-form-item>
      <a-form-item v-if="todoNeedsTarget(action)" label="转交给" required extra="只能选择当前工作空间内的有效成员"><PersonSelect v-model="actionForm.targetTenantMemberId" :people="directory.people" value-key="tenantMemberId" :excluded-values="current?.tenantMemberId ? [current.tenantMemberId] : []" :allow-clear="false" /></a-form-item>
    </a-form>
  </a-modal>
</template>
