<script setup lang="ts">
import {
  CheckCircle2,
  ClipboardCheck,
  ExternalLink,
  MailCheck,
  RefreshCw,
  ThumbsDown,
  ThumbsUp,
} from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { TODO_COUNTS_CHANGED_EVENT, todoApi } from '@/services/todo'
import { useSessionStore } from '@/stores/session'
import type {
  TodoActionCode,
  TodoCategoryFilter,
  TodoCounts,
  TodoItem,
  TodoStateFilter,
  TodoTimeFilter,
} from '@/types/todo'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const tenantId = computed(() => session.context?.tenantId ?? '')
const items = ref<TodoItem[]>([])
const emptyCounts = (): TodoCounts => ({
  openCount: 0,
  taskCount: 0,
  approvalCount: 0,
  todayCount: 0,
  overdueCount: 0,
})
const counts = ref<TodoCounts>(emptyCounts())
const category = ref<TodoCategoryFilter>('ALL')
const stateFilter = ref<TodoStateFilter>('OPEN')
const timeFilter = ref<TodoTimeFilter>('ALL')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const detailLoading = ref(false)
const mutation = ref('')
const error = ref('')
const resultMessage = ref('')
const detail = ref<TodoItem | null>(null)
const actionOpen = ref(false)
const selectedAction = ref<TodoActionCode | null>(null)
const actionComment = ref('')
const actionReason = ref('')
const actionValidation = ref('')
let loadGeneration = 0
let detailGeneration = 0

const decisionPolicy = computed(() => ({
  approveRequired: false,
  rejectRequired: true,
  minimumLength: 1,
}))

const actionText = computed(() => {
  if (selectedAction.value === 'APPROVE') return '通过'
  if (selectedAction.value === 'REJECT') return '驳回'
  if (selectedAction.value === 'MARK_READ') return '标记已读'
  return '完成'
})

const actionInputRequired = computed(() => {
  if (selectedAction.value === 'APPROVE') return decisionPolicy.value.approveRequired
  if (selectedAction.value === 'REJECT') return decisionPolicy.value.rejectRequired
  return false
})

async function loadCounts() {
  try {
    const next = await todoApi.counts(systemId.value)
    counts.value = next
    window.dispatchEvent(new CustomEvent(TODO_COUNTS_CHANGED_EVENT, {
      detail: { openCount: next.openCount },
    }))
  } catch (cause) {
    error.value = requestMessage(cause, '待办数量加载失败')
  }
}

async function loadTodos() {
  const generation = ++loadGeneration
  loading.value = true
  error.value = ''
  try {
    const result = await todoApi.list(systemId.value, {
      category: category.value,
      state: stateFilter.value,
      time: timeFilter.value,
      page: page.value,
      size: pageSize.value,
    })
    if (generation !== loadGeneration) return
    items.value = result.items
    total.value = result.total
  } catch (cause) {
    if (generation !== loadGeneration) return
    items.value = []
    total.value = 0
    error.value = requestMessage(cause, '待办列表加载失败')
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

async function reloadContext() {
  await Promise.allSettled([loadTodos(), loadCounts()])
}

function changeFilter() {
  page.value = 1
  detail.value = null
  resultMessage.value = ''
  void loadTodos()
}

function changePage(nextPage: number, nextSize: number) {
  if (nextSize !== pageSize.value) {
    pageSize.value = nextSize
    page.value = 1
  } else {
    page.value = nextPage
  }
  detail.value = null
  resultMessage.value = ''
  void loadTodos()
}

async function refreshTodos() {
  mutation.value = 'refresh'
  error.value = ''
  resultMessage.value = ''
  try {
    const result = await todoApi.refresh(systemId.value)
    resultMessage.value = `刷新完成：发现 ${result.discovered}，新增 ${result.created}，更新 ${result.updated}，关闭 ${result.closed}`
    await reloadContext()
  } catch (cause) {
    error.value = requestMessage(cause, '待办刷新失败')
  } finally {
    mutation.value = ''
  }
}

async function openDetail(item: TodoItem) {
  const generation = ++detailGeneration
  detailLoading.value = true
  error.value = ''
  resultMessage.value = ''
  try {
    const result = await todoApi.detail(systemId.value, item.id)
    detail.value = result.todo
    if (result.status !== 'LIVE') {
      const detailError = result.status === 'STALE'
        ? '待办已失效或来源状态已变化，请刷新列表。'
        : '无权查看或处理该待办。'
      await Promise.allSettled([loadTodos(), loadCounts()])
      error.value = detailError
    }
  } catch (cause) {
    if (generation !== detailGeneration) return
    detail.value = null
    const detailError = requestMessage(cause, '待办详情加载失败')
    await Promise.allSettled([loadTodos(), loadCounts()])
    error.value = detailError
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

function beginAction(action: TodoActionCode) {
  selectedAction.value = action
  actionComment.value = ''
  actionReason.value = ''
  actionValidation.value = ''
  actionOpen.value = true
}

async function submitAction() {
  const item = detail.value
  const action = selectedAction.value
  if (!item || !action || mutation.value) return
  const value = action === 'APPROVE' ? actionComment.value.trim() : actionReason.value.trim()
  if (actionInputRequired.value && value.length < decisionPolicy.value.minimumLength) {
    actionValidation.value = `${actionText.value}意见至少需要 ${decisionPolicy.value.minimumLength} 个字符。`
    return
  }
  actionValidation.value = ''
  mutation.value = `action:${item.id}:${action}`
  error.value = ''
  resultMessage.value = ''
  try {
    const input = action === 'MARK_READ'
      ? { version: item.version, action }
      : {
          version: item.version,
          action,
          comment: action === 'APPROVE' ? value || null : null,
          reason: action === 'REJECT' ? value || null : null,
        }
    const result = await todoApi.action(systemId.value, item.id, input)
    detail.value = result.todo
    const nextMessage = actionResultMessage(result.status, result.sourceResult.code, result.sourceResult.message)
    if (result.status === 'SUCCESS') actionOpen.value = false
    await reloadContext()
    if (result.status === 'SUCCESS') {
      resultMessage.value = `${nextMessage}${result.replayed ? '（幂等重放）' : ''}`
    } else {
      error.value = nextMessage
    }
  } catch (cause) {
    const actionError = requestMessage(cause, '待办动作执行失败')
    await Promise.allSettled([loadTodos(), loadCounts()])
    error.value = actionError
  } finally {
    mutation.value = ''
  }
}

async function openSource() {
  if (!detail.value?.routeHint) return
  await router.push(detail.value.routeHint)
}

function requestMessage(cause: unknown, fallback: string) {
  if (cause instanceof ApiRequestError) {
    const detailMessage = cause.message || cause.code
    if (cause.code.includes('STALE') || cause.status === 410 || cause.status === 404) {
      return `待办已失效或来源状态已变化：${detailMessage}`
    }
    if (cause.status === 403 || cause.code.includes('DENIED')) {
      return `无权查看或处理该待办：${detailMessage}`
    }
    if (cause.status === 409) return `待办状态冲突，请刷新后重试：${detailMessage}`
    return `${fallback}：${detailMessage}`
  }
  return cause instanceof Error ? `${fallback}：${cause.message}` : `${fallback}，请稍后重试`
}

function actionResultMessage(status: string, code: string, sourceMessage: string) {
  const detailMessage = sourceMessage || code
  if (status === 'SUCCESS') return `${actionText.value}处理完成（${code}）${sourceMessage ? `：${sourceMessage}` : ''}`
  if (status === 'STALE') return `待办已失效或来源状态已变化：${detailMessage}`
  if (status === 'DENIED') return `无权查看或处理该待办：${detailMessage}`
  if (status === 'CONFLICT') return `待办状态冲突，请刷新后重试：${detailMessage}`
  if (status === 'IN_PROGRESS') return `相同待办动作正在处理中，请稍后刷新：${detailMessage}`
  return `待办动作执行失败：${detailMessage}`
}

function categoryLabel(value: TodoItem['category']) {
  if (value === 'TASK') return '任务'
  if (value === 'APPROVAL') return '审批'
  if (value === 'REMINDER') return '提醒'
  return '抄送'
}

function categoryColor(value: TodoItem['category']) {
  if (value === 'APPROVAL') return 'purple'
  if (value === 'REMINDER') return 'orange'
  if (value === 'CC') return 'cyan'
  return 'blue'
}

function actionLabel(value: TodoActionCode) {
  if (value === 'COMPLETE') return '完成'
  if (value === 'APPROVE') return '通过'
  if (value === 'REJECT') return '驳回'
  return '标记已读'
}

function time(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

function resetContext() {
  category.value = 'ALL'
  stateFilter.value = 'OPEN'
  timeFilter.value = 'ALL'
  page.value = 1
  pageSize.value = 20
  items.value = []
  total.value = 0
  counts.value = emptyCounts()
  detail.value = null
  actionOpen.value = false
  error.value = ''
  resultMessage.value = ''
}

watch([systemId, tenantId], () => {
  resetContext()
  void reloadContext()
}, { immediate: true, flush: 'sync' })
</script>

<template>
  <section class="todo-page">
    <header class="todo-heading">
      <div>
        <h1><ClipboardCheck :size="24" /> 待办动作中心 <a-badge :count="counts.openCount" /></h1>
        <p>集中处理当前租户分配给你的任务、审批、提醒与抄送，不复制来源业务事实。</p>
      </div>
      <a-button
        class="todo-refresh"
        :loading="mutation === 'refresh'"
        :disabled="Boolean(mutation) && mutation !== 'refresh'"
        @click="refreshTodos"
      ><RefreshCw :size="16" />刷新来源</a-button>
    </header>

    <a-alert
      v-if="error"
      class="todo-error"
      type="error"
      show-icon
      closable
      :message="error"
      @close="error = ''"
    />
    <a-alert
      v-if="resultMessage"
      class="todo-result"
      type="success"
      show-icon
      closable
      :message="resultMessage"
      @close="resultMessage = ''"
    />

    <div class="todo-filters" aria-label="待办筛选">
      <label>
        分类
        <select v-model="category" class="todo-category-filter" @change="changeFilter">
          <option value="ALL">全部</option>
          <option value="APPROVAL">审批</option>
          <option value="TASK">任务</option>
          <option class="todo-filter-reminder" data-todo-category-filter="REMINDER" value="REMINDER">提醒</option>
          <option class="todo-filter-cc" data-todo-category-filter="CC" value="CC">抄送</option>
        </select>
      </label>
      <label>
        状态
        <select v-model="stateFilter" class="todo-status-filter" @change="changeFilter">
          <option value="OPEN">待处理</option>
          <option value="CLOSED">已关闭</option>
          <option value="ALL">全部</option>
        </select>
      </label>
      <label>
        时间
        <select v-model="timeFilter" class="todo-time-filter" @change="changeFilter">
          <option value="ALL">全部</option>
          <option value="TODAY">今天到期</option>
          <option value="OVERDUE">已逾期</option>
        </select>
      </label>
      <span>共 {{ total }} 条</span>
    </div>

    <div class="todo-content">
      <a-spin :spinning="loading">
        <a-empty v-if="!items.length && !loading" description="当前筛选条件下没有待办" />
        <div v-else class="todo-list">
          <button
            v-for="item in items"
            :key="item.id"
            class="todo-card"
            :class="{
              selected: detail?.id === item.id,
              'todo-card-reminder': item.category === 'REMINDER',
              'todo-card-cc': item.category === 'CC',
            }"
            :data-todo-category="item.category"
            :data-todo-id="item.id"
            :data-todo-version="item.version"
            type="button"
            @click="openDetail(item)"
          >
            <span class="todo-card-heading">
              <a-tag :color="categoryColor(item.category)">
                {{ categoryLabel(item.category) }}
              </a-tag>
              <strong>{{ item.title }}</strong>
            </span>
            <span class="todo-card-meta">
              <span>{{ item.status }}</span>
              <span>创建 {{ time(item.createdAt) }}</span>
              <span>来源 {{ item.sourceType }} / {{ item.sourceId }}</span>
              <span>到期 {{ item.dueAt ? time(item.dueAt) : '未设置' }}</span>
            </span>
          </button>
        </div>
      </a-spin>

      <aside class="todo-detail" aria-label="待办详情">
        <a-spin :spinning="detailLoading">
          <a-empty v-if="!detail && !detailLoading" description="选择一条待办查看详情" />
          <template v-else-if="detail">
            <header>
              <div>
                <a-tag>{{ categoryLabel(detail.category) }}</a-tag>
                <h2>{{ detail.title }}</h2>
              </div>
              <a-button
                v-if="detail.routeHint"
                class="todo-open-source"
                @click="openSource"
              ><ExternalLink :size="15" />打开来源</a-button>
            </header>
            <dl>
              <div><dt>状态</dt><dd>{{ detail.status }}</dd></div>
              <div><dt>创建时间</dt><dd>{{ time(detail.createdAt) }}</dd></div>
              <div><dt>来源</dt><dd>{{ detail.sourceType }} / {{ detail.sourceId }}</dd></div>
              <div><dt>来源版本</dt><dd>{{ detail.sourceVersion }}</dd></div>
              <div><dt>动作范围</dt><dd>{{ detail.actionScope }}</dd></div>
              <div v-if="detail.representedMemberId">
                <dt>代表成员</dt><dd>{{ detail.representedMemberId }}</dd>
              </div>
              <div><dt>到期时间</dt><dd>{{ detail.dueAt ? time(detail.dueAt) : '未设置' }}</dd></div>
              <div v-if="detail.closeReason"><dt>关闭原因</dt><dd>{{ detail.closeReason }}</dd></div>
            </dl>
            <div v-if="detail.status === 'OPEN'" class="todo-detail-actions">
              <a-button
                v-for="action in detail.availableActions"
                :key="action"
                :class="[
                  `todo-action-${action}`,
                  { 'todo-action-mark-read': action === 'MARK_READ' },
                ]"
                :danger="action === 'REJECT'"
                :type="action === 'REJECT' ? 'default' : 'primary'"
                :disabled="Boolean(mutation)"
                @click="beginAction(action)"
              >
                <CheckCircle2 v-if="action === 'COMPLETE'" :size="15" />
                <ThumbsUp v-else-if="action === 'APPROVE'" :size="15" />
                <ThumbsDown v-else-if="action === 'REJECT'" :size="15" />
                <MailCheck v-else :size="15" />
                {{ actionLabel(action) }}
              </a-button>
            </div>
          </template>
        </a-spin>
      </aside>
    </div>

    <a-pagination
      v-if="total > 0"
      class="todo-pagination"
      :current="page"
      :page-size="pageSize"
      :total="total"
      :show-size-changer="true"
      :page-size-options="['10', '20', '50']"
      @change="changePage"
      @show-size-change="changePage"
    />

    <a-modal
      v-model:open="actionOpen"
      :title="`${actionText}待办`"
      :confirm-loading="mutation.startsWith('action:')"
      @ok="submitAction"
    >
      <p v-if="selectedAction === 'COMPLETE'">确认完成这项任务。提交后会由 Work 模块执行并重新校验来源状态。</p>
      <p v-else-if="selectedAction === 'MARK_READ'">确认将这条消息标记为已读。提交后会由 Event 模块执行并重新校验来源状态。</p>
      <a-form v-else layout="vertical">
        <a-form-item
          v-if="selectedAction === 'APPROVE'"
          :label="`通过意见${actionInputRequired ? '（必填）' : '（选填）'}`"
        >
          <a-textarea
            v-model:value="actionComment"
            class="todo-action-comment"
            :rows="4"
            maxlength="2000"
          />
        </a-form-item>
        <a-form-item v-else label="驳回原因（必填）">
          <a-textarea
            v-model:value="actionReason"
            class="todo-action-reason"
            :rows="4"
            maxlength="2000"
          />
        </a-form-item>
        <a-alert
          v-if="actionValidation"
          class="todo-action-validation"
          type="error"
          show-icon
          :message="actionValidation"
        />
      </a-form>
    </a-modal>
  </section>
</template>

<style scoped>
.todo-page{display:grid;gap:18px;max-width:1280px;margin:0 auto;padding:28px;min-width:0;background:#f4f6f8}.todo-heading,.todo-heading h1,.todo-filters,.todo-card-heading,.todo-card-meta,.todo-detail>div>div>header,.todo-detail-actions{display:flex;align-items:center}.todo-heading{justify-content:space-between;gap:20px}.todo-heading h1{gap:10px;margin:0}.todo-heading p{margin:6px 0 0;color:#64748b}.todo-filters{gap:14px;padding:14px;border:1px solid #dce3e8;border-radius:8px;background:#fff}.todo-filters label{display:flex;align-items:center;gap:7px}.todo-filters>span{margin-left:auto;color:#64748b}.todo-filters select{min-width:126px;height:34px;padding:4px 9px;border:1px solid #d9d9d9;border-radius:6px;background:#fff}.todo-content{display:grid;grid-template-columns:minmax(0,1fr) minmax(340px,.72fr);gap:18px;align-items:start;min-width:0}.todo-content>*{min-width:0}.todo-list{display:grid;gap:10px}.todo-card{display:grid;gap:10px;width:100%;padding:17px 18px;border:1px solid #dce3e8;border-radius:10px;background:#fff;text-align:left;cursor:pointer}.todo-card:focus-visible{outline:3px solid rgb(15 118 110 / 24%);outline-offset:2px}.todo-card.selected{border-color:#0f766e;background:#f4fbf9}.todo-card-heading,.todo-card-meta{gap:9px}.todo-card-heading{min-width:0}.todo-card-heading strong{min-width:0;overflow-wrap:anywhere}.todo-card-meta{flex-wrap:wrap;color:#64748b;font-size:13px}.todo-detail{min-height:280px;padding:18px;border:1px solid #dce3e8;border-radius:10px;background:#fff}.todo-detail>div>div>header{justify-content:space-between;gap:16px}.todo-detail h2{margin:8px 0 0;font-size:19px;overflow-wrap:anywhere}.todo-detail dl{margin:14px 0}.todo-detail dl>div{display:grid;grid-template-columns:92px minmax(0,1fr);gap:10px;padding:9px 0;border-bottom:1px solid #edf0f2}.todo-detail dt{color:#64748b}.todo-detail dd{margin:0;overflow-wrap:anywhere}.todo-detail-actions{flex-wrap:wrap;gap:9px;padding-top:4px}.todo-pagination{justify-self:end}
@media(max-width:1100px){.todo-page{padding:22px}.todo-content{grid-template-columns:minmax(0,1fr)}.todo-detail{min-height:220px}.todo-heading{align-items:flex-start}.todo-filters{flex-wrap:wrap}}
@media(max-width:640px){.todo-page{gap:14px;padding:14px}.todo-heading{align-items:stretch;flex-direction:column}.todo-refresh{align-self:flex-start}.todo-filters{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.todo-filters label{align-items:stretch;flex-direction:column;gap:5px}.todo-filters select{width:100%;min-width:0}.todo-filters>span{margin:0;align-self:end;text-align:right}.todo-card{padding:14px}.todo-card-heading{align-items:flex-start}.todo-card-meta{display:grid;gap:5px}.todo-detail{padding:14px}.todo-detail>div>div>header{align-items:flex-start;flex-direction:column}.todo-detail dl>div{grid-template-columns:82px minmax(0,1fr)}.todo-pagination{max-width:100%;justify-self:center}.todo-detail-actions .ant-btn{min-height:34px}}
</style>
