<script setup lang="ts">
import { DownloadOutlined, ExportOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import { authorizedBlob, exportScopeLabel, exportStatusLabel, saveBlob } from '../file'
import { productDateTime } from '../presentation'
import { systemTokens } from '../session'
import type { ExportBatchView, ExportEstimateView, RuntimeListFilter } from '../types'

/** 业务页面只执行当前模块导出；导出配置在系统后台维护。 */
const props = defineProps<{
  open: boolean; moduleId?: number; moduleCode: string; moduleName?: string
  lifecycleState: 'ACTIVE' | 'ARCHIVED' | 'DELETED'
  tenantScope: 'ALL' | 'OWN' | 'SHARED'
  search: string; filters: RuntimeListFilter[]
  sortField: string; sortDirection: 'ASC' | 'DESC'
  selectedRecordIds: number[]; visibleFieldCodes: string[]
}>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()
const drawerOpen = computed({ get: () => props.open, set: value => emit('update:open', value) })
const token = computed(() => systemTokens.value?.accessToken || '')
const loading = ref(false)
const submitting = ref(false)
const selectedFields = ref<string[]>([])
const format = ref<'CSV' | 'XLSX'>('XLSX')
const idempotencyKey = ref(crypto.randomUUID())
const estimate = ref<ExportEstimateView>()
const estimateDirty = ref(false)
const current = ref<ExportBatchView>()
const history = ref<ExportBatchView[]>([])
const error = ref('')
let pollTimer: number | undefined
const scope = computed(() => props.selectedRecordIds.length ? 'SELECTED' : 'CURRENT_FILTER')
const terminal = new Set(['COMPLETED', 'FAILED'])
const progress = computed(() => {
  if (!current.value) return 0
  if (terminal.has(current.value.status)) return 100
  if (!current.value.totalRows) return current.value.status === 'RUNNING' ? 60 : 5
  return Math.max(current.value.status === 'RUNNING' ? 10 : 3,
    Math.min(99, Math.round(current.value.exportedRows * 100 / current.value.totalRows)))
})
const base = computed(() => '/api/runtime/modules/' + props.moduleCode + '/exports')
function payload() {
  return {
    lifecycleState: props.lifecycleState, tenantScope: props.tenantScope,
    search: props.search.trim(), filters: props.filters,
    sortField: props.sortField, sortDirection: props.sortDirection,
    selectedRecordIds: props.selectedRecordIds, format: format.value,
    idempotencyKey: idempotencyKey.value, selectedFields: selectedFields.value,
  }
}
function readable(reason: unknown, fallback: string) {
  return reason instanceof ApiError ? reason.message : fallback
}
async function load() {
  if (!drawerOpen.value || !props.moduleCode) return
  loading.value = true; error.value = ''; current.value = undefined
  try {
    idempotencyKey.value = crypto.randomUUID()
    history.value = await api<ExportBatchView[]>(base.value, {}, token.value)
    let published: string[] = []
    let available: Set<string> | undefined
    try {
      const config = await api<{ availableFields: Array<{ code: string }>; published?: { selectedFields: string[] } }>(
        base.value + '/config', {}, token.value)
      available = new Set(config.availableFields.map(field => field.code))
      published = (config.published?.selectedFields || []).filter(code => available?.has(code))
    } catch (reason) {
      if (!(reason instanceof ApiError) || reason.httpStatus !== 403) throw reason
    }
    const visible = props.visibleFieldCodes.filter(code => available?.has(code) ?? true)
    selectedFields.value = [...new Set(published.length ? published
      : visible.length ? visible : ['title'])]
    await refreshEstimate()
    if (history.value.length) current.value = history.value[0]
  } catch (reason) { error.value = readable(reason, '导出工作区加载失败') }
  finally { loading.value = false }
}
async function refreshEstimate() {
  if (!selectedFields.value.length) return
  loading.value = true; error.value = ''
  try {
    estimate.value = await api<ExportEstimateView>(base.value + '/estimate', {
      method: 'POST', body: JSON.stringify(payload()),
    }, token.value)
    estimateDirty.value = false
  } catch (reason) { error.value = readable(reason, '导出预估失败') }
  finally { loading.value = false }
}
async function submit() {
  if (!estimate.value?.withinQuota || !selectedFields.value.length || estimateDirty.value) return
  submitting.value = true; error.value = ''
  try {
    current.value = await api<ExportBatchView>(base.value, {
      method: 'POST', body: JSON.stringify(payload()),
    }, token.value)
    message.success('导出任务已进入后台队列')
    history.value = await api<ExportBatchView[]>(base.value, {}, token.value)
    schedulePoll()
  } catch (reason) { error.value = readable(reason, '导出任务提交失败') }
  finally { submitting.value = false }
}
function schedulePoll() {
  if (pollTimer) window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(poll, 700)
}
async function poll() {
  if (!current.value || !drawerOpen.value) return
  try {
    current.value = await api<ExportBatchView>(base.value + '/' + current.value.id, {}, token.value)
    if (terminal.has(current.value.status)) {
      history.value = await api<ExportBatchView[]>(base.value, {}, token.value)
      return
    }
  } catch { /* 下一次轮询重新读取服务端状态。 */ }
  schedulePoll()
}
function openBatch(id: number) {
  current.value = history.value.find(batch => batch.id === id)
  if (current.value && !terminal.has(current.value.status)) schedulePoll()
}
async function download(batch = current.value) {
  if (!batch?.resultFileId || batch.status !== 'COMPLETED') return
  try {
    const blob = await authorizedBlob(base.value + '/' + batch.id + '/download', token.value)
    saveBlob(blob, props.moduleCode + '-export-' + batch.id + (batch.format === 'XLSX' ? '.xlsx' : '.csv'))
  } catch (reason) { message.error(readable(reason, '导出文件下载失败')) }
}
function statusColor(status: string) {
  return status === 'COMPLETED' ? 'green' : status === 'FAILED' ? 'red' : 'blue'
}
watch(() => [props.open, props.moduleCode], ([open]) => { if (open) void load() }, { immediate: true })
onBeforeUnmount(() => { if (pollTimer) window.clearTimeout(pollTimer) })
</script>

<template>
  <a-drawer v-model:open="drawerOpen" :title="(moduleName || moduleCode) + ' · 数据导出'" width="640" :mask-closable="false">
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-alert type="info" show-icon :message="exportScopeLabel(scope) + ' · ' + (search || '无关键词') + ' · ' + filters.length + ' 项高级筛选'"
      description="导出结果同时受当前筛选、列表与导出数据权限约束；勾选记录时再取勾选集合。" />
    <div class="export-actions"><strong>文件格式</strong><a-radio-group v-model:value="format" button-style="solid" @change="estimateDirty = true"><a-radio-button value="XLSX">Excel</a-radio-button><a-radio-button value="CSV">CSV</a-radio-button></a-radio-group></div>
    <strong>导出字段</strong>
    <div v-if="estimate" class="export-fields"><a-checkbox-group v-model:value="selectedFields"
      :options="estimate.availableFields.map(field => ({ value: field.code, label: field.name + (field.masked ? ' · ' + (field.maskStrategy || '脱敏') : '') }))"
      @change="estimateDirty = true" /></div>
    <div class="export-actions">
      <a-button :loading="loading" :disabled="!selectedFields.length" @click="refreshEstimate"><ReloadOutlined />重新预估</a-button>
      <span v-if="estimate"><strong>{{ estimate.estimatedRows }}</strong> / {{ estimate.maximumRows }} 行配额</span>
      <a-tag v-if="estimate?.maskedFields.length" color="gold">{{ estimate.maskedFields.length }} 个字段脱敏</a-tag>
      <a-button type="primary" :disabled="estimateDirty || !estimate?.withinQuota || !selectedFields.length" :loading="submitting" @click="submit"><ExportOutlined />创建导出任务</a-button>
    </div>
    <a-alert v-if="estimate && !estimate.withinQuota" type="error" show-icon message="当前结果超过导出配额" description="请收紧筛选条件，或勾选少量记录后重新预估。" />
    <template v-if="current">
      <a-divider>本次导出 · {{ productDateTime(current.createdAt) }}</a-divider>
      <div class="export-current"><div><a-tag :color="statusColor(current.status)">{{ exportStatusLabel(current.status) }}</a-tag><span>{{ current.exportedRows }} / {{ current.totalRows }} 行</span><small v-if="current.errorMessage">{{ current.errorMessage }}</small></div>
        <a-progress :percent="progress" :status="current.status === 'FAILED' ? 'exception' : current.status === 'COMPLETED' ? 'success' : 'active'" /></div>
      <div class="export-actions"><a-button :loading="loading" @click="poll"><ReloadOutlined />刷新状态</a-button><a-button v-if="current.status === 'COMPLETED'" type="primary" @click="download()"><DownloadOutlined />下载 {{ current.format === 'XLSX' ? 'Excel' : 'CSV' }}</a-button></div>
    </template>
    <a-divider v-if="history.length">最近导出</a-divider>
    <div v-if="history.length" class="export-history"><button v-for="batch in history" :key="batch.id" type="button" :class="{ active: current?.id === batch.id }" @click="openBatch(batch.id)">
      <span><strong>{{ productDateTime(batch.createdAt) }}</strong><a-tag :color="statusColor(batch.status)">{{ exportStatusLabel(batch.status) }}</a-tag></span>
      <small>{{ batch.totalRows }} 行 · {{ batch.selectedFields.length }} 列 · {{ batch.format === 'XLSX' ? 'Excel' : 'CSV' }}</small>
      <a-button v-if="batch.status === 'COMPLETED'" type="link" size="small" @click.stop="download(batch)"><DownloadOutlined />下载</a-button>
    </button></div>
    <template #footer><div class="export-actions"><span>文件只供任务提交人下载。</span><a-button @click="drawerOpen = false">关闭</a-button></div></template>
  </a-drawer>
</template>

<style scoped>
.export-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin: 14px 0; }
.export-fields { margin: 10px 0; }
.export-current { display: grid; gap: 8px; }
.export-current > div { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.export-current small { color: #b91c1c; }
.export-history { display: grid; gap: 6px; }
.export-history button { display: flex; align-items: center; justify-content: space-between; gap: 8px; border: 1px solid #e2e8f0; background: white; padding: 8px; text-align: left; border-radius: 6px; cursor: pointer; }
.export-history button.active { border-color: #1677ff; }
.export-history button span { display: flex; align-items: center; gap: 8px; }
</style>
