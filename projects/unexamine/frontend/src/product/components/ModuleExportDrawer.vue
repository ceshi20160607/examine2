<script setup lang="ts">
import { DownloadOutlined, ExportOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import { authorizedBlob, exportScopeLabel, exportStatusLabel, saveBlob } from '../file'
import { systemTokens } from '../session'
import type { ExportBatchView, ExportEstimateView, RuntimeListFilter } from '../types'

const props = defineProps<{
  open: boolean
  moduleCode: string
  moduleName?: string
  lifecycleState: 'ACTIVE' | 'ARCHIVED' | 'DELETED'
  tenantScope: 'ALL' | 'OWN' | 'SHARED'
  search: string
  filters: RuntimeListFilter[]
  sortField: string
  sortDirection: 'ASC' | 'DESC'
  selectedRecordIds: number[]
  visibleFieldCodes: string[]
}>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()
const drawerOpen = computed({ get: () => props.open, set: value => emit('update:open', value) })
const token = computed(() => systemTokens.value?.accessToken || '')
const loading = ref(false)
const submitting = ref(false)
const selectedFields = ref<string[]>([])
const estimate = ref<ExportEstimateView>()
const estimateDirty = ref(false)
const current = ref<ExportBatchView>()
const history = ref<ExportBatchView[]>([])
const error = ref('')
let pollTimer: number | undefined

const scope = computed(() => props.selectedRecordIds.length ? 'SELECTED' : 'CURRENT_FILTER')
const progress = computed(() => {
  if (!current.value) return 0
  if (current.value.status === 'COMPLETED') return 100
  if (current.value.status === 'FAILED') return 100
  if (!current.value.totalRows) return current.value.status === 'RUNNING' ? 60 : 5
  return Math.max(current.value.status === 'RUNNING' ? 10 : 3,
    Math.min(99, Math.round(current.value.exportedRows * 100 / current.value.totalRows)))
})
const terminal = new Set(['COMPLETED', 'FAILED'])

function payload() {
  return {
    lifecycleState: props.lifecycleState,
    tenantScope: props.tenantScope,
    search: props.search.trim(),
    filters: props.filters,
    sortField: props.sortField,
    sortDirection: props.sortDirection,
    selectedRecordIds: props.selectedRecordIds,
    selectedFields: selectedFields.value,
  }
}

async function load() {
  if (!drawerOpen.value || !props.moduleCode) return
  loading.value = true
  error.value = ''
  try {
    history.value = await api<ExportBatchView[]>(`/api/runtime/modules/${props.moduleCode}/exports`, {}, token.value)
    selectedFields.value = [...new Set(props.visibleFieldCodes.length ? props.visibleFieldCodes : ['title'])]
    await refreshEstimate()
    if (history.value.length) current.value = history.value[0]
  } catch (reason) { error.value = readable(reason, '导出工作区加载失败') }
  finally { loading.value = false }
}

async function refreshEstimate() {
  if (!selectedFields.value.length) return
  loading.value = true
  error.value = ''
  try {
    estimate.value = await api<ExportEstimateView>(`/api/runtime/modules/${props.moduleCode}/exports/estimate`, {
      method: 'POST', body: JSON.stringify(payload()),
    }, token.value)
    estimateDirty.value = false
  } catch (reason) { error.value = readable(reason, '导出预估失败') }
  finally { loading.value = false }
}

async function submit() {
  if (!estimate.value?.withinQuota || !selectedFields.value.length) return
  submitting.value = true
  error.value = ''
  try {
    current.value = await api<ExportBatchView>(`/api/runtime/modules/${props.moduleCode}/exports`, {
      method: 'POST', body: JSON.stringify(payload()),
    }, token.value)
    message.success(`导出批次 #${current.value.id} 已进入后台队列`)
    await loadHistory()
    schedulePoll()
  } catch (reason) { error.value = readable(reason, '导出任务提交失败') }
  finally { submitting.value = false }
}

async function loadHistory() {
  history.value = await api<ExportBatchView[]>(`/api/runtime/modules/${props.moduleCode}/exports`, {}, token.value)
}

function schedulePoll() {
  if (pollTimer) window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(poll, 700)
}

async function poll() {
  if (!current.value || !drawerOpen.value) return
  try {
    current.value = await api<ExportBatchView>(
      `/api/runtime/modules/${props.moduleCode}/exports/${current.value.id}`, {}, token.value)
    if (terminal.has(current.value.status)) { await loadHistory(); return }
  } catch { /* 保留当前进度，下一轮从权威批次状态恢复。 */ }
  schedulePoll()
}

function openBatch(id: number) {
  current.value = history.value.find(batch => batch.id === id)
  if (current.value && !terminal.has(current.value.status)) schedulePoll()
}

async function download(batch = current.value) {
  if (!batch?.resultFileId || batch.status !== 'COMPLETED') return
  try {
    const blob = await authorizedBlob(`/api/runtime/modules/${props.moduleCode}/exports/${batch.id}/download`, token.value)
    saveBlob(blob, `${props.moduleCode}-export-${batch.id}.csv`)
  } catch (reason) { message.error(readable(reason, '导出文件下载失败')) }
}

function statusColor(status: string) {
  if (status === 'COMPLETED') return 'green'
  if (status === 'FAILED') return 'red'
  return 'blue'
}

function readable(reason: unknown, fallback: string) {
  return reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（追踪号 ${reason.traceId}）` : ''}` : fallback
}

watch(() => [props.open, props.moduleCode], ([open]) => { if (open) void load() }, { immediate: true })
onBeforeUnmount(() => { if (pollTimer) window.clearTimeout(pollTimer) })
</script>

<template>
  <a-drawer v-model:open="drawerOpen" :title="`${moduleName || moduleCode} · 权限导出`" width="820" :mask-closable="false" class="export-drawer">
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <div class="export-boundary">
      <section><span>1</span><div><strong>冻结当前范围</strong><small>{{ exportScopeLabel(scope) }}，筛选、排序和数据范围一起快照</small></div></section>
      <section><span>2</span><div><strong>收紧文件字段</strong><small>PAGE 与 FILE 渠道取交集，敏感值保持脱敏</small></div></section>
      <section><span>3</span><div><strong>后台生成受控文件</strong><small>执行前重验权限，完成后下载全程留痕</small></div></section>
    </div>

    <a-divider>导出列与数据范围</a-divider>
    <a-alert type="info" show-icon :message="`${exportScopeLabel(scope)} · ${search || '无关键词'} · ${filters.length} 项高级筛选`" description="若已勾选记录，只导出同时满足当前筛选、数据范围和勾选集合的记录。" />
    <div v-if="estimate" class="export-fields">
      <a-checkbox-group v-model:value="selectedFields" :options="estimate.availableFields.map(field => ({ value: field.code, label: `${field.name}${field.masked ? ` · ${field.maskStrategy || '脱敏'}` : ''}` }))" @change="estimateDirty = true" />
    </div>
    <div class="export-estimate-actions">
      <a-button :loading="loading" :disabled="!selectedFields.length" @click="refreshEstimate"><ReloadOutlined />重新预估</a-button>
      <span v-if="estimate"><strong>{{ estimate.estimatedRows }}</strong> / {{ estimate.maximumRows }} 行配额</span>
      <a-tag v-if="estimate?.maskedFields.length" color="gold">{{ estimate.maskedFields.length }} 个字段保持脱敏</a-tag>
      <a-button type="primary" :danger="estimate && !estimate.withinQuota" :disabled="estimateDirty || !estimate?.withinQuota || !selectedFields.length" :loading="submitting" @click="submit"><ExportOutlined />创建后台导出</a-button>
    </div>
    <a-alert v-if="estimate && !estimate.withinQuota" type="error" show-icon message="当前结果超过导出配额" description="请收紧筛选条件或改为勾选少量记录后重新预估。" />

    <template v-if="current">
      <a-divider>批次 #{{ current.id }}</a-divider>
      <div class="export-current">
        <div><a-tag :color="statusColor(current.status)">{{ exportStatusLabel(current.status) }}</a-tag><span>{{ current.exportedRows }} / {{ current.totalRows }} 行</span><small v-if="current.errorMessage">{{ current.errorMessage }}</small></div>
        <a-progress :percent="progress" :status="current.status === 'FAILED' ? 'exception' : current.status === 'COMPLETED' ? 'success' : 'active'" />
      </div>
      <div class="drawer-footer export-actions"><a-button :loading="loading" @click="poll"><ReloadOutlined />刷新状态</a-button><a-button v-if="current.status === 'COMPLETED'" type="primary" @click="download()"><DownloadOutlined />下载 CSV</a-button></div>
    </template>

    <a-divider v-if="history.length">最近导出</a-divider>
    <div v-if="history.length" class="export-history">
      <button v-for="batch in history" :key="batch.id" type="button" :class="{ active: current?.id === batch.id }" @click="openBatch(batch.id)">
        <span><strong>#{{ batch.id }}</strong><a-tag :color="statusColor(batch.status)">{{ exportStatusLabel(batch.status) }}</a-tag></span>
        <small>{{ batch.totalRows }} 行 · {{ batch.selectedFields.length }} 列 · {{ batch.createdAt }}</small>
        <a-button v-if="batch.status === 'COMPLETED'" type="link" size="small" @click.stop="download(batch)"><DownloadOutlined />下载</a-button>
      </button>
    </div>
    <template #footer><div class="drawer-footer"><span>文件只属于任务提交人；权限失效或超配额时不会生成可下载文件。</span><a-button @click="drawerOpen = false">关闭</a-button></div></template>
  </a-drawer>
</template>
