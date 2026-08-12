<script setup lang="ts">
import { CheckCircle2, Eye, RefreshCw, RotateCcw, XCircle } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { platformTodoApi } from '@/services/platformTodo'
import { useSessionStore } from '@/stores/session'
import type { PlatformTodo, PlatformTodoAction, PlatformTodoCounts, PlatformTodoStateFilter, PlatformTodoTypeFilter } from '@/types/platformTodo'

const session = useSessionStore()
const router = useRouter()
const canManage = computed(() => session.hasPermission('platform.task.manage'))
const state = ref<PlatformTodoStateFilter>('OPEN')
const type = ref<PlatformTodoTypeFilter>('ALL')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const counts = ref<PlatformTodoCounts>({ open: 0, closed: 0, total: 0, today: 0, reminders: 0, approvals: 0, failures: 0 })
const items = ref<PlatformTodo[]>([])
const detail = ref<PlatformTodo | null>(null)
const detailOpen = ref(false)
const loading = ref(false)
const detailLoading = ref(false)
const mutation = ref('')
const error = ref('')
const result = ref('')
let listSequence = 0

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

function errorMessage(cause: unknown) {
  if (cause instanceof ApiRequestError) return cause.code === cause.message ? cause.code : `${cause.code}：${cause.message}`
  return cause instanceof Error ? cause.message : '平台待办请求失败'
}

function formatTime(value: string | null) {
  if (!value) return '未设置'
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function priorityColor(priority: PlatformTodo['priority']) {
  return priority === 'URGENT' ? 'red' : priority === 'HIGH' ? 'orange' : priority === 'LOW' ? 'default' : 'blue'
}

function actionLabel(action: PlatformTodoAction) {
  return { COMPLETE: '完成', REOPEN: '重新打开', CANCEL: '取消' }[action]
}

function actionIcon(action: PlatformTodoAction) {
  return action === 'COMPLETE' ? CheckCircle2 : action === 'REOPEN' ? RotateCcw : XCircle
}

async function load() {
  const sequence = ++listSequence
  loading.value = true
  error.value = ''
  try {
    const [todoPage, countResult] = await Promise.all([
      platformTodoApi.list({ state: state.value, type: type.value, page: page.value, size: size.value }),
      platformTodoApi.counts(),
    ])
    if (sequence !== listSequence) return
    items.value = todoPage.items
    page.value = todoPage.page
    size.value = todoPage.size
    total.value = todoPage.total
    counts.value = countResult
  } catch (cause) {
    if (sequence === listSequence) error.value = errorMessage(cause)
  } finally {
    if (sequence === listSequence) loading.value = false
  }
}

async function showDetail(todo: PlatformTodo) {
  detailOpen.value = true
  detailLoading.value = true
  error.value = ''
  try {
    detail.value = await platformTodoApi.detail(todo.id)
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    detailLoading.value = false
  }
}

async function runAction(action: PlatformTodoAction) {
  if (!canManage.value || !detail.value || mutation.value) return
  mutation.value = action
  error.value = ''
  result.value = ''
  try {
    const actionResult = await platformTodoApi.action(detail.value.id, action, detail.value.version)
    detail.value = actionResult.todo
    result.value = actionResult.replayed ? '已返回相同请求的处理结果' : `待办已${actionLabel(action)}`
    await load()
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    mutation.value = ''
  }
}

function changeState(value: PlatformTodoStateFilter) {
  page.value = 1
  state.value = value
}

function changeType(value: PlatformTodoTypeFilter) {
  page.value = 1
  type.value = value
  void load()
}

async function openTodo(todo: PlatformTodo) {
  if (!todo.routeHint.startsWith('/platform/')) {
    error.value = '平台待办目标不安全，已阻止跳转。'
    return
  }
  await router.push(todo.routeHint)
}

watch(page, load)
onMounted(load)
</script>

<template>
  <section class="workspace platform-todo-page" :aria-busy="loading">
    <header class="workspace-heading">
      <div><h1>平台待办</h1><p>集中查看并处理当前账户的平台事项</p></div>
      <a-button class="platform-todo-refresh" :loading="loading" @click="load"><RefreshCw :size="16" />刷新</a-button>
    </header>

    <div class="platform-todo-counts" aria-label="待办统计">
      <button :class="{ active: state === 'OPEN' }" type="button" @click="changeState('OPEN')"><span>待处理</span><strong>{{ counts.open }}</strong></button>
      <button :class="{ active: state === 'CLOSED' }" type="button" @click="changeState('CLOSED')"><span>已关闭</span><strong>{{ counts.closed }}</strong></button>
      <button :class="{ active: state === 'ALL' }" type="button" @click="changeState('ALL')"><span>全部</span><strong>{{ counts.total }}</strong></button>
    </div>

    <a-alert v-if="error" type="error" show-icon closable :message="error" @close="error = ''" />
    <a-alert v-if="result" type="success" show-icon closable :message="result" @close="result = ''" />

    <div class="platform-todo-layout">
      <aside class="platform-todo-types" aria-label="待办类型">
        <button :class="{ active: type === 'ALL' }" @click="changeType('ALL')"><span>全部待办</span><strong>{{ counts.open }}</strong></button>
        <button :class="{ active: type === 'APPROVAL' }" @click="changeType('APPROVAL')"><span>审批待办</span><strong>{{ counts.approvals }}</strong></button>
        <button :class="{ active: type === 'REMINDER' }" @click="changeType('REMINDER')"><span>提醒待办</span><strong>{{ counts.reminders }}</strong></button>
        <button :class="{ active: type === 'TODAY' }" @click="changeType('TODAY')"><span>今日需处理</span><strong>{{ counts.today }}</strong></button>
        <button :class="{ active: type === 'FAILED' }" @click="changeType('FAILED')"><span>任务失败</span><strong>{{ counts.failures }}</strong></button>
      </aside>
      <div class="platform-todo-table-wrap">
      <table class="platform-todo-table">
        <thead><tr><th>事项</th><th>优先级</th><th>状态</th><th>截止时间</th><th>更新时间</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="todo in items" :key="todo.id" class="platform-todo-row" tabindex="0" @click="openTodo(todo)" @keydown.enter="openTodo(todo)">
            <td><strong>{{ todo.title }}</strong><small>{{ todo.source }} · {{ todo.category }} · #{{ todo.id }}</small></td>
            <td><a-tag :color="priorityColor(todo.priority)">{{ todo.priority }}</a-tag></td>
            <td><a-tag :color="todo.state === 'OPEN' ? 'processing' : 'default'">{{ todo.state === 'OPEN' ? '待处理' : '已关闭' }}</a-tag></td>
            <td>{{ formatTime(todo.dueAt) }}</td>
            <td>{{ formatTime(todo.updatedAt) }}</td>
            <td @click.stop @keydown.stop><a-button class="platform-todo-detail" size="small" @click="showDetail(todo)"><Eye :size="15" />详情</a-button></td>
          </tr>
        </tbody>
      </table>
      <div v-if="loading" class="platform-todo-loading"><a-spin />正在加载平台待办…</div>
      <a-empty v-else-if="!items.length" description="当前筛选条件下没有平台待办" />
      </div>
    </div>

    <footer v-if="total > size" class="platform-todo-pagination">
      <a-button :disabled="page <= 1 || loading" @click="page -= 1">上一页</a-button>
      <span>第 {{ page }} / {{ pageCount }} 页，共 {{ total }} 条</span>
      <a-button :disabled="page >= pageCount || loading" @click="page += 1">下一页</a-button>
    </footer>

    <a-drawer v-model:open="detailOpen" title="待办详情" :width="620">
      <a-spin :spinning="detailLoading">
        <article v-if="detail" class="platform-todo-detail-body">
          <header><div><h2>{{ detail.title }}</h2><span>#{{ detail.id }}</span></div><a-tag :color="detail.state === 'OPEN' ? 'processing' : 'default'">{{ detail.state }}</a-tag></header>
          <p>{{ detail.description || '无事项描述' }}</p>
          <a-tabs default-active-key="detail">
            <a-tab-pane key="detail" tab="基本信息">
              <dl><div><dt>来源</dt><dd>{{ detail.source }}</dd></div><div><dt>来源状态</dt><dd>{{ detail.sourceStatus }}</dd></div><div><dt>优先级</dt><dd>{{ detail.priority }}</dd></div><div><dt>截止时间</dt><dd>{{ formatTime(detail.dueAt) }}</dd></div><div><dt>创建时间</dt><dd>{{ formatTime(detail.createdAt) }}</dd></div><div><dt>更新时间</dt><dd>{{ formatTime(detail.updatedAt) }}</dd></div></dl>
            </a-tab-pane>
            <a-tab-pane key="route" tab="定位信息"><code>{{ detail.routeHint }}</code></a-tab-pane>
          </a-tabs>
          <footer v-if="canManage && detail.availableActions.length">
            <a-button
              v-for="action in detail.availableActions"
              :key="action"
              :class="`platform-todo-action-${action.toLowerCase()}`"
              :danger="action === 'CANCEL'"
              :type="action === 'COMPLETE' ? 'primary' : 'default'"
              :loading="mutation === action"
              :disabled="Boolean(mutation)"
              @click="runAction(action)"
            ><component :is="actionIcon(action)" :size="16" />{{ actionLabel(action) }}</a-button>
          </footer>
        </article>
      </a-spin>
    </a-drawer>
  </section>
</template>

<style scoped>
.platform-todo-page{display:grid;gap:16px}.platform-todo-counts{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.platform-todo-counts button,.platform-todo-types button{display:flex;align-items:center;justify-content:space-between;padding:14px 16px;border:1px solid #d9e0e5;border-radius:7px;background:#fff;color:#586572;text-align:left;cursor:pointer}.platform-todo-counts button.active,.platform-todo-types button.active{border-color:#177ddc;box-shadow:inset 3px 0 #177ddc}.platform-todo-counts strong{color:#17212b;font-size:22px}.platform-todo-layout{display:grid;grid-template-columns:190px minmax(0,1fr);gap:12px;align-items:start}.platform-todo-types{display:grid;gap:7px}.platform-todo-types button{padding:10px 12px}.platform-todo-table-wrap{position:relative;overflow:auto;border:1px solid #d9e0e5;background:#fff}.platform-todo-table{width:100%;min-width:900px;border-collapse:collapse}.platform-todo-table th,.platform-todo-table td{padding:12px;border-bottom:1px solid #e7ebee;text-align:left}.platform-todo-table th{background:#f6f8fa;color:#63717d;font-size:12px}.platform-todo-table td:first-child{display:grid;gap:4px}.platform-todo-table small{color:#798590}.platform-todo-row{cursor:pointer}.platform-todo-row:hover,.platform-todo-row:focus-visible{background:#f0f8f7;outline:0}.platform-todo-loading{display:flex;align-items:center;justify-content:center;gap:9px;min-height:140px;color:#6b7685}.platform-todo-pagination,.platform-todo-detail-body>header,.platform-todo-detail-body>footer{display:flex;align-items:center;gap:10px}.platform-todo-pagination{justify-content:flex-end}.platform-todo-pagination span{color:#6b7685}.platform-todo-detail-body{display:grid;gap:16px}.platform-todo-detail-body>header{justify-content:space-between}.platform-todo-detail-body h2,.platform-todo-detail-body p{margin:0}.platform-todo-detail-body>header>div{display:grid;gap:4px}.platform-todo-detail-body>header span{color:#7a858f}.platform-todo-detail-body dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;margin:0}.platform-todo-detail-body dl>div{display:grid;gap:3px}.platform-todo-detail-body dt{color:#6b7685;font-size:12px}.platform-todo-detail-body dd{margin:0}.platform-todo-detail-body>footer{justify-content:flex-end;padding-top:8px}@media(max-width:760px){.platform-todo-layout{grid-template-columns:minmax(0,1fr)}.platform-todo-types{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
