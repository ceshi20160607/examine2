<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { ApiError, api } from '../api'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { runtimeIdempotency } from '../flow-runtime'
import { todoActionLabel, todoNeedsComment, todoNeedsTarget, todoStatusLabel, todoTypeLabel } from '../todo'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import { productDateTime } from '../presentation'
import type { TodoHandleResult, TodoItemView } from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const emit = defineEmits<{ openFlow: [target: { taskId?: number; instanceId?: number }] }>()
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const rows = ref<TodoItemView[]>([])
const loading = ref(false)
const busy = ref(false)
const status = ref('PENDING')
const type = ref('ALL')
const actionOpen = ref(false)
const selected = ref<TodoItemView>()
const action = ref('APPROVE')
const actionForm = reactive({ comment: '', targetAccountId: undefined as number | undefined })
const lastResult = ref<TodoHandleResult>()

function can(actionCode: string) {
  return allowsPermission(current.value?.permissions, 'TODO', contextCode.value, actionCode)
    || allowsPermission(current.value?.permissions, 'TODO', '*', actionCode)
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
  loading.value = true
  try {
    rows.value = await api<TodoItemView[]>(`/api/todos?status=${encodeURIComponent(status.value)}&type=${encodeURIComponent(type.value)}`, {}, token.value)
  } catch (error) {
    message.error(describeError(error))
  } finally { loading.value = false }
}

function openSource(todo: TodoItemView) {
  if (todo.sourceType === 'FLOW_TASK' && Number(todo.sourceId)) {
    emit('openFlow', { taskId: Number(todo.sourceId) })
  }
}

function openAction(todo: TodoItemView, actionCode: string) {
  selected.value = todo
  action.value = actionCode
  Object.assign(actionForm, { comment: '', targetAccountId: undefined })
  actionOpen.value = true
}

async function handleTodo() {
  if (!token.value || !selected.value || !can('HANDLE')) return
  if (todoNeedsComment(action.value) && !actionForm.comment.trim()) return message.warning('拒绝或退回必须填写可追溯原因')
  if (todoNeedsTarget(action.value) && !actionForm.targetAccountId) return message.warning('转交必须填写目标账号')
  busy.value = true
  try {
    lastResult.value = await api<TodoHandleResult>(`/api/todos/${selected.value.id}/actions`, {
      method: 'POST', body: JSON.stringify({
        actionCode: action.value,
        comment: actionForm.comment || undefined,
        targetAccountId: actionForm.targetAccountId,
        idempotencyKey: runtimeIdempotency(`todo-${action.value.toLowerCase()}`),
      }),
    }, token.value)
    actionOpen.value = false
    await load()
    message.success(`待办已${todoActionLabel(action.value)}，目标对象状态已读回`)
  } catch (error) {
    message.error(describeError(error))
    await load()
  } finally { busy.value = false }
}

function openLastResult() {
  const instanceId = lastResult.value?.targetResult?.instance.id
  if (instanceId) emit('openFlow', { instanceId })
}

watch([status, type], () => void load())
onMounted(() => load())
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
        <div v-if="rows.length" class="todo-list">
          <article v-for="todo in rows" :key="todo.id" class="panel-card todo-card">
            <div class="todo-card__main">
              <span class="todo-card__type">{{ todoTypeLabel(todo.todoType) }}</span>
              <div><span class="todo-card__title"><strong>{{ todo.title }}</strong><a-tag :color="priorityColor(todo.priority)">{{ priorityLabel(todo.priority) }}</a-tag><a-tag :color="statusColor(todo.status)">{{ todoStatusLabel(todo.status) }}</a-tag></span><p>{{ todo.summary }}</p><small>{{ todoTypeLabel(todo.todoType) }}<template v-if="todo.dueAt"> · 截止 {{ productDateTime(todo.dueAt) }}</template></small></div>
            </div>
            <div class="todo-card__actions">
              <a-button v-if="todo.sourceType === 'FLOW_TASK'" @click="openSource(todo)">打开来源</a-button>
              <template v-if="can('HANDLE')">
                <a-button v-for="item in todo.availableActions" :key="item" :type="item === 'APPROVE' ? 'primary' : 'default'" @click="openAction(todo, item)">{{ todoActionLabel(item) }}</a-button>
              </template>
            </div>
          </article>
        </div>
        <a-empty v-else-if="!loading" :image="Empty.PRESENTED_IMAGE_SIMPLE" :description="status === 'PENDING' ? '当前没有未完成待办' : '当前筛选没有待办记录'" />
      </a-spin>
    </template>
  </div>

  <a-modal v-model:open="actionOpen" :title="`处理待办 · ${todoActionLabel(action)}`" :confirm-loading="busy" @ok="handleTodo">
    <a-form layout="vertical">
      <a-alert type="info" show-icon :message="selected?.title" description="提交后会重新读取业务对象与待办状态，避免重复处理。" style="margin-bottom:16px" />
      <a-form-item label="处理意见" :required="todoNeedsComment(action)"><a-textarea v-model:value="actionForm.comment" :rows="4" placeholder="记录处理结论或可追溯原因" /></a-form-item>
      <a-form-item v-if="todoNeedsTarget(action)" label="目标账号编号" required extra="请输入组织成员的账号编号"><a-input-number v-model:value="actionForm.targetAccountId" :min="1" style="width:100%" /></a-form-item>
    </a-form>
  </a-modal>
</template>
