<script setup lang="ts">
import { History, RefreshCw } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

import { ApiRequestError } from '@/services/api'
import { recordHistoryApi } from '@/services/history'
import { useSessionStore } from '@/stores/session'
import type { RecordHistoryDiff, RecordHistoryItem } from '@/types/history'

const props = defineProps<{
  systemId: string
  moduleCode: string
  recordId: string
}>()

const session = useSessionStore()
const items = ref<RecordHistoryItem[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref('')
let loadGeneration = 0

const canView = computed(() =>
  session.hasPermission('system.runtime.access')
  && session.hasPermission(`module.${props.moduleCode}.view`)
  && session.hasPermission(`module.${props.moduleCode}.history.read`))
const scopeKey = computed(() => [
  props.systemId,
  props.moduleCode,
  props.recordId,
  session.context?.tenantId ?? '',
  session.context?.permissionVersion ?? '',
].join(':'))

const actionLabels: Record<string, string> = {
  RECORD_DRAFT_CREATED: '创建草稿',
  RECORD_DRAFT_AUTOSAVED: '自动保存草稿',
  RECORD_UPDATED: '更新记录',
  RECORD_ACTIVATED: '激活记录',
  RECORD_ARCHIVED: '归档记录',
  RECORD_UNARCHIVED: '取消归档',
  RECORD_TRASHED: '移入回收站',
  RECORD_RESTORED: '从回收站恢复',
  RECORD_DRAFT_DISCARDED: '丢弃草稿',
  RECORD_DRAFT_RECOVERED: '恢复草稿',
  RECORD_RELATION_MUTATED: '更新关联数据',
  RECORD_SUBTABLE_MUTATED: '更新子表',
}

async function loadHistory() {
  const generation = ++loadGeneration
  items.value = []
  total.value = 0
  error.value = ''
  if (!canView.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const result = await recordHistoryApi.list(
      props.systemId,
      props.moduleCode,
      props.recordId,
      page.value,
    )
    if (generation !== loadGeneration) return
    items.value = result.items
    total.value = result.total
  } catch (cause) {
    if (generation !== loadGeneration) return
    error.value = message(cause)
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

function message(cause: unknown) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code
  return cause instanceof Error ? cause.message : '历史请求失败，请稍后重试'
}

function action(value: string) {
  return actionLabels[value] ?? value
}

function field(value: string) {
  if (value === '$title') return '标题'
  if (value === '$status') return '状态'
  return value
}

function value(diff: RecordHistoryDiff, side: 'beforeValue' | 'afterValue') {
  if (diff.masked) return '••••••'
  const content = diff[side]
  if (content === null || content === undefined || content === '') return '未设置'
  if (typeof content === 'string') return content
  try {
    return JSON.stringify(content)
  } catch {
    return String(content)
  }
}

function time(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

function resetScope() {
  page.value = 1
  void loadHistory()
}

watch(scopeKey, resetScope, { immediate: true })
watch(page, loadHistory)
</script>

<template>
  <section class="history-panel" aria-label="记录历史">
    <header class="history-heading">
      <div>
        <h3><History :size="19" /> 变更历史</h3>
        <p>按时间查看当前记录已持久化的变更。</p>
      </div>
      <a-button v-if="canView" :loading="loading" @click="loadHistory">
        <RefreshCw :size="15" />刷新
      </a-button>
    </header>

    <a-alert
      v-if="!canView"
      type="warning"
      show-icon
      message="无权查看记录历史"
      :description="`需要 module.${moduleCode}.history.read 权限。`"
    />
    <template v-else>
      <a-alert v-if="error" type="error" show-icon closable :message="error" @close="error = ''" />
      <a-spin :spinning="loading">
        <a-empty v-if="!items.length && !loading" description="还没有变更历史" />
        <a-timeline v-else>
          <a-timeline-item v-for="item in items" :key="item.historyId">
            <article class="history-item">
              <div class="history-meta">
                <strong>{{ action(item.action) }}</strong>
                <span>版本 {{ item.recordVersion }}</span>
                <span>{{ item.actorMemberId ? `成员 ${item.actorMemberId}` : '系统' }}</span>
                <time>{{ time(item.occurredAt) }}</time>
              </div>
              <div v-if="item.diff.length" class="diff-list">
                <div v-for="diff in item.diff" :key="diff.fieldCode" class="diff-row">
                  <strong>{{ field(diff.fieldCode) }}</strong>
                  <span>{{ value(diff, 'beforeValue') }}</span>
                  <span aria-hidden="true">→</span>
                  <span>{{ value(diff, 'afterValue') }}</span>
                  <a-tag v-if="diff.masked">已脱敏</a-tag>
                </div>
              </div>
              <p v-else>本次操作未产生可见字段差异。</p>
            </article>
          </a-timeline-item>
        </a-timeline>
      </a-spin>

      <a-pagination
        v-if="total > 20"
        v-model:current="page"
        :page-size="20"
        :total="total"
        :show-size-changer="false"
      />
    </template>
  </section>
</template>

<style scoped>
.history-panel {
  display: grid;
  gap: 14px;
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid #e2e8f0;
}

.history-heading,
.history-heading h3,
.history-meta,
.diff-row {
  display: flex;
  align-items: center;
}

.history-heading {
  justify-content: space-between;
  gap: 16px;
}

.history-heading h3 {
  gap: 8px;
  margin: 0;
}

.history-heading p,
.history-item > p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.history-item {
  display: grid;
  gap: 10px;
}

.history-meta {
  flex-wrap: wrap;
  gap: 7px 14px;
  color: #64748b;
  font-size: 12px;
}

.history-meta strong {
  color: #334155;
  font-size: 14px;
}

.diff-list {
  display: grid;
  gap: 6px;
}

.diff-row {
  flex-wrap: wrap;
  gap: 7px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f8fafc;
  font-size: 12px;
}

.diff-row strong {
  min-width: 92px;
}

.diff-row span {
  max-width: 230px;
  overflow-wrap: anywhere;
}
</style>
