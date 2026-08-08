<script setup lang="ts">
import { CalendarClock, CheckCircle2, Download, FileSpreadsheet, History, RefreshCw, Send, XCircle } from 'lucide-vue-next'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { ApiRequestError } from '@/services/api'
import { runtimeReportApi } from '@/services/report'
import type {
  ReportExportStatus,
  ReportExportTask,
  ReportScheduledRun,
  ReportScheduleRunStatus,
} from '@/types/report'

const props = defineProps<{
  open: boolean
  systemId: string
  reportCode: string
  reportName: string
}>()
const emit = defineEmits<{ close: [] }>()

const current = ref<ReportExportTask | null>(null)
const history = ref<ReportExportTask[]>([])
const page = ref(1)
const size = 10
const total = ref(0)
const scheduledCurrent = ref<ReportScheduledRun | null>(null)
const scheduledHistory = ref<ReportScheduledRun[]>([])
const scheduledPage = ref(1)
const scheduledTotal = ref(0)
const scheduledLoading = ref(false)
const loading = ref(false)
const command = ref<'start' | 'refresh' | 'history' | 'download' | ''>('')
const errorMessage = ref('')
let generation = 0
let pendingRequestKey = ''

const pending = computed(() => current.value
  ? ['QUEUED', 'RUNNING'].includes(current.value.status) : false)
const canPrevious = computed(() => page.value > 1)
const canNext = computed(() => page.value * size < total.value)
const canScheduledPrevious = computed(() => scheduledPage.value > 1)
const canScheduledNext = computed(() => scheduledPage.value * size < scheduledTotal.value)
const statusColor = computed(() => current.value?.status === 'SUCCEEDED' ? 'green'
  : current.value?.status === 'FAILED' ? 'red' : 'blue')

function statusLabel(status: ReportExportStatus) {
  return ({ QUEUED: '排队中', RUNNING: '生成中', SUCCEEDED: '已完成', FAILED: '失败' })[status]
}

function scheduledStatusLabel(status: ReportScheduleRunStatus) {
  return ({ PENDING: '待执行', RUNNING: '执行中', SUCCEEDED: '已送达', FAILED: '失败' })[status]
}

function requestError(error: unknown, fallback: string) {
  return error instanceof ApiRequestError
    ? error.message || error.code
    : error instanceof Error ? error.message : fallback
}

function formatTime(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.valueOf()) ? value : date.toLocaleString('zh-CN')
}

function formatSize(value?: number | null) {
  if (value === undefined || value === null) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

async function reset() {
  const runGeneration = ++generation
  page.value = 1
  current.value = null
  scheduledCurrent.value = null
  history.value = []
  scheduledHistory.value = []
  total.value = 0
  scheduledTotal.value = 0
  scheduledPage.value = 1
  pendingRequestKey = ''
  errorMessage.value = ''
  loading.value = true
  try {
    await Promise.all([
      loadHistory(1, runGeneration),
      loadScheduledHistory(1, runGeneration),
    ])
  } finally {
    if (runGeneration === generation) loading.value = false
  }
}

async function startExport() {
  if (!props.reportCode || command.value || pending.value) return
  if (!pendingRequestKey) pendingRequestKey = crypto.randomUUID()
  command.value = 'start'
  errorMessage.value = ''
  try {
    const task = await runtimeReportApi.startExport(
      props.systemId, props.reportCode, pendingRequestKey,
    )
    pendingRequestKey = ''
    current.value = task
    await loadHistory(1)
  } catch (error) {
    errorMessage.value = requestError(error, '导出任务创建失败，请重试')
  } finally {
    command.value = ''
  }
}

async function loadHistory(nextPage = page.value, runGeneration = generation) {
  if (!props.reportCode || runGeneration !== generation) return
  if (!command.value) command.value = 'history'
  errorMessage.value = ''
  try {
    const result = await runtimeReportApi.exports(
      props.systemId, props.reportCode, nextPage, size,
    )
    if (runGeneration !== generation) return
    history.value = result.items ?? []
    page.value = result.page || nextPage
    total.value = result.total ?? 0
  } catch (error) {
    if (runGeneration === generation) {
      errorMessage.value = requestError(error, '导出历史加载失败')
    }
  } finally {
    if (command.value === 'history') command.value = ''
  }
}

async function inspect(task: ReportExportTask) {
  command.value = 'refresh'
  errorMessage.value = ''
  try {
    current.value = await runtimeReportApi.exportTask(
      props.systemId, props.reportCode, task.exportId,
    )
  } catch (error) {
    errorMessage.value = requestError(error, '导出任务状态加载失败')
  } finally {
    command.value = ''
  }
}

async function loadScheduledHistory(nextPage = scheduledPage.value, runGeneration = generation) {
  if (!props.reportCode || runGeneration !== generation) return
  scheduledLoading.value = true
  try {
    const result = await runtimeReportApi.scheduledRuns(
      props.systemId, props.reportCode, nextPage, size,
    )
    if (runGeneration !== generation) return
    scheduledHistory.value = result.items ?? []
    scheduledPage.value = result.page || nextPage
    scheduledTotal.value = result.total ?? 0
  } catch (error) {
    if (runGeneration === generation) {
      errorMessage.value = requestError(error, '调度送达历史加载失败')
    }
  } finally {
    if (runGeneration === generation) scheduledLoading.value = false
  }
}

async function inspectScheduled(run: ReportScheduledRun) {
  scheduledLoading.value = true
  errorMessage.value = ''
  try {
    scheduledCurrent.value = await runtimeReportApi.scheduledRun(
      props.systemId, props.reportCode, run.id,
    )
  } catch (error) {
    errorMessage.value = requestError(error, '调度运行详情加载失败')
  } finally {
    scheduledLoading.value = false
  }
}

async function refreshCurrent() {
  if (!current.value) {
    await loadHistory()
    return
  }
  await inspect(current.value)
  await loadHistory(page.value)
}

async function download(task = current.value) {
  if (!task || task.status !== 'SUCCEEDED') return
  command.value = 'download'
  errorMessage.value = ''
  try {
    const result = await runtimeReportApi.downloadExport(
      props.systemId, props.reportCode, task.exportId,
    )
    saveDownload(result.blob, result.filename || task.resultFilename || `${props.reportCode}-report.xlsx`)
  } catch (error) {
    errorMessage.value = requestError(error, '导出文件下载失败')
  } finally {
    command.value = ''
  }
}

async function downloadScheduled(run = scheduledCurrent.value) {
  if (!run || run.status !== 'SUCCEEDED') return
  command.value = 'download'
  errorMessage.value = ''
  try {
    const result = await runtimeReportApi.downloadScheduledRun(
      props.systemId, props.reportCode, run.id,
    )
    saveDownload(result.blob, result.filename || run.filename || `${props.reportCode}-scheduled.xlsx`)
  } catch (error) {
    errorMessage.value = requestError(error, '调度报表下载失败')
  } finally {
    command.value = ''
  }
}

function saveDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

watch(() => [props.open, props.systemId, props.reportCode] as const, ([open]) => {
  generation += 1
  if (open) void reset()
}, { immediate: true })
onBeforeUnmount(() => { generation += 1 })
</script>

<template>
  <a-drawer :open="open" :width="760" :title="`${reportName || reportCode} · XLSX 导出`" @close="emit('close')">
    <a-spin :spinning="loading">
      <div class="report-export-drawer">
        <a-alert v-if="errorMessage" class="report-export-error" type="error" show-icon :message="errorMessage" />

        <section class="export-command-card">
          <div><h3><FileSpreadsheet :size="18" />手工导出</h3><p>按当前已发布报表和实时权限生成，最多 5,000 行；不允许客户端提交字段、SQL 或数据范围。</p></div>
          <a-button class="report-export-start" type="primary" :disabled="Boolean(command) || pending" :loading="command === 'start'" @click="startExport"><Send :size="16" />开始导出</a-button>
        </section>

        <section v-if="current" class="export-current-card">
          <header><div><h3>手工任务 {{ current.exportId }}</h3><p>报表 v{{ current.reportVersionNumber }} · 数据源版本 {{ current.dataSourceVersionId }}</p></div><a-tag :color="statusColor">手工 · {{ statusLabel(current.status) }}</a-tag></header>
          <dl><div><dt>处理行数</dt><dd>{{ current.processedRows }} / {{ current.totalRows ?? '—' }}</dd></div><div><dt>字段数</dt><dd>{{ current.fieldCodes.length }}</dd></div><div><dt>创建时间</dt><dd>{{ formatTime(current.createdAt) }}</dd></div><div><dt>完成时间</dt><dd>{{ formatTime(current.finishedAt) }}</dd></div><div><dt>结果文件</dt><dd>{{ current.resultFilename || '—' }}</dd></div><div><dt>文件大小</dt><dd>{{ formatSize(current.resultSize) }}</dd></div></dl>
          <a-alert v-if="current.truncated" class="report-export-truncated" type="warning" show-icon message="结果已截断为最多 5,000 行" />
          <a-alert v-if="current.status === 'FAILED'" class="report-export-failed" type="error" show-icon :message="current.failureCode || 'REPORT_EXPORT_FAILED'" :description="current.failureMessage || '导出失败，未生成可下载文件。'" />
          <div class="current-actions"><a-button :loading="command === 'refresh'" @click="refreshCurrent"><RefreshCw :size="15" />刷新状态</a-button><a-button v-if="current.status === 'SUCCEEDED'" class="report-export-download" type="primary" :loading="command === 'download'" @click="download()"><Download :size="15" />下载 XLSX</a-button><span v-if="current.status === 'SUCCEEDED'"><CheckCircle2 :size="16" />文件已就绪</span><span v-else-if="current.status === 'FAILED'"><XCircle :size="16" />无可下载文件</span></div>
        </section>

        <section class="export-history-card">
          <header><div><h3><History :size="17" />手工导出历史</h3><p>共 {{ total }} 个任务</p></div><a-button :loading="command === 'history'" @click="loadHistory(page)"><RefreshCw :size="15" />刷新</a-button></header>
          <div v-if="history.length" class="export-history-table-wrap"><table class="export-history-table"><thead><tr><th>来源</th><th>状态</th><th>版本</th><th>行数</th><th>结果</th><th>时间</th></tr></thead><tbody><tr v-for="task in history" :key="task.exportId" :class="{ active: current?.exportId === task.exportId }" @click="inspect(task)"><td><a-tag>手工</a-tag></td><td>{{ statusLabel(task.status) }}</td><td>v{{ task.reportVersionNumber }}</td><td>{{ task.processedRows }}{{ task.truncated ? '（已截断）' : '' }}</td><td>{{ task.status === 'FAILED' ? task.failureCode : task.resultFilename || '—' }}</td><td>{{ formatTime(task.createdAt) }}</td></tr></tbody></table></div>
          <a-empty v-else class="report-export-empty" :image="false" description="暂无导出任务" />
          <footer><span>第 {{ page }} 页</span><div><a-button class="export-history-previous" :disabled="!canPrevious" @click="loadHistory(page - 1)">上一页</a-button><a-button class="export-history-next" :disabled="!canNext" @click="loadHistory(page + 1)">下一页</a-button></div></footer>
        </section>

        <section v-if="scheduledCurrent" class="scheduled-current-card">
          <header><div><h3><CalendarClock :size="17" />{{ scheduledCurrent.scheduleName }}</h3><p>{{ scheduledCurrent.scheduleCode }} · 计划 {{ formatTime(scheduledCurrent.scheduledAt) }}</p></div><a-tag :color="scheduledCurrent.status === 'SUCCEEDED' ? 'green' : scheduledCurrent.status === 'FAILED' ? 'red' : 'blue'">调度 · {{ scheduledStatusLabel(scheduledCurrent.status) }}</a-tag></header>
          <dl><div><dt>处理行数</dt><dd>{{ scheduledCurrent.processedRows }} / {{ scheduledCurrent.totalRows ?? '—' }}</dd></div><div><dt>结果文件</dt><dd>{{ scheduledCurrent.filename || '—' }}</dd></div><div><dt>完成时间</dt><dd>{{ formatTime(scheduledCurrent.finishedAt) }}</dd></div></dl>
          <a-alert v-if="scheduledCurrent.truncated" class="scheduled-run-truncated" type="warning" show-icon message="调度结果已截断为最多 5,000 行" />
          <a-alert v-if="scheduledCurrent.status === 'FAILED'" class="scheduled-run-failed" type="error" show-icon :message="scheduledCurrent.failureCode || 'REPORT_SCHEDULE_RUN_FAILED'" :description="scheduledCurrent.failureMessage || '本次调度未生成可下载文件。'" />
          <a-button v-if="scheduledCurrent.status === 'SUCCEEDED'" class="scheduled-run-download" type="primary" :loading="command === 'download'" @click="downloadScheduled()"><Download :size="15" />下载调度 XLSX</a-button>
        </section>

        <section class="scheduled-history-card">
          <header><div><h3><CalendarClock :size="17" />调度送达历史</h3><p>仅展示送达当前成员的运行，共 {{ scheduledTotal }} 条。</p></div><a-button :loading="scheduledLoading" @click="loadScheduledHistory(scheduledPage)"><RefreshCw :size="15" />刷新</a-button></header>
          <div v-if="scheduledHistory.length" class="export-history-table-wrap"><table class="export-history-table scheduled-run-table"><thead><tr><th>来源</th><th>调度</th><th>状态</th><th>计划时间</th><th>结果</th></tr></thead><tbody><tr v-for="run in scheduledHistory" :key="run.id" :class="{ active: scheduledCurrent?.id === run.id }" @click="inspectScheduled(run)"><td><a-tag color="purple">调度</a-tag></td><td>{{ run.scheduleName }}<small>{{ run.scheduleCode }}</small></td><td>{{ scheduledStatusLabel(run.status) }}</td><td>{{ formatTime(run.scheduledAt) }}</td><td>{{ run.status === 'FAILED' ? run.failureCode : run.filename || `${run.processedRows} 行` }}</td></tr></tbody></table></div>
          <a-empty v-else class="scheduled-run-empty" :image="false" description="暂无送达给你的调度报表" />
          <footer><span>第 {{ scheduledPage }} 页</span><div><a-button class="scheduled-history-previous" :disabled="!canScheduledPrevious" @click="loadScheduledHistory(scheduledPage - 1)">上一页</a-button><a-button class="scheduled-history-next" :disabled="!canScheduledNext" @click="loadScheduledHistory(scheduledPage + 1)">下一页</a-button></div></footer>
        </section>
      </div>
    </a-spin>
  </a-drawer>
</template>

<style scoped>
.report-export-drawer{display:grid;gap:16px}.report-export-drawer>section{padding:16px;border:1px solid #dde5e8;border-radius:8px;background:#fff}.export-command-card,.export-current-card>header,.export-history-card>header,.scheduled-current-card>header,.scheduled-history-card>header,.current-actions,.export-history-card footer,.scheduled-history-card footer,.export-history-card footer>div,.scheduled-history-card footer>div{display:flex;align-items:center}.export-command-card,.export-current-card>header,.export-history-card>header,.scheduled-current-card>header,.scheduled-history-card>header,.export-history-card footer,.scheduled-history-card footer{justify-content:space-between;gap:14px}.report-export-drawer h3,.report-export-drawer p{margin:0}.report-export-drawer h3{display:flex;align-items:center;gap:7px}.report-export-drawer p{margin-top:5px;color:#687880;font-size:12px}.export-current-card,.scheduled-current-card{display:grid;gap:13px}.export-current-card dl,.scheduled-current-card dl{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.export-current-card dl div,.scheduled-current-card dl div{display:grid;gap:3px}.export-current-card dt,.scheduled-current-card dt{color:#728087;font-size:12px}.export-current-card dd,.scheduled-current-card dd{margin:0;overflow-wrap:anywhere}.current-actions{gap:9px;flex-wrap:wrap}.current-actions span{display:flex;align-items:center;gap:5px;color:#536b64}.export-history-card,.scheduled-history-card{display:grid;gap:12px}.export-history-table-wrap{overflow:auto;border:1px solid #e2e8ea}.export-history-table{width:100%;border-collapse:collapse;white-space:nowrap}.export-history-table th,.export-history-table td{padding:9px 11px;border-bottom:1px solid #e8edef;text-align:left}.export-history-table th{background:#f5f8f8}.export-history-table tbody tr{cursor:pointer}.export-history-table tbody tr.active{background:#eef8f6}.scheduled-run-table td:nth-child(2){display:grid;gap:2px}.scheduled-run-table small{color:#708087}.report-export-empty,.scheduled-run-empty{padding:24px}.export-history-card footer>div,.scheduled-history-card footer>div{gap:8px}
</style>
