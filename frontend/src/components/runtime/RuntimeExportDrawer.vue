<script setup lang="ts">
import { CheckCircle2, Download, FileSpreadsheet, History, RefreshCw, Send, XCircle } from 'lucide-vue-next'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { ApiRequestError } from '@/services/api'
import { runtimeExportApi } from '@/services/runtimeExport'
import type { RuntimeFieldCapability, RuntimeRecordQuery } from '@/types/config'
import type { RuntimeExportStatus, RuntimeExportTask } from '@/types/runtimeExport'

const props = defineProps<{
  open: boolean
  systemId: string
  moduleCode: string
  moduleName: string
  query: RuntimeRecordQuery
  fields: RuntimeFieldCapability[]
  defaultFieldCodes: string[]
  initialTaskId?: string
}>()

const emit = defineEmits<{ close: [] }>()
const excludedTypes = new Set(['RELATION', 'REFERENCE', 'SUBTABLE'])
const task = ref<RuntimeExportTask | null>(null)
const history = ref<RuntimeExportTask[]>([])
const historyTotal = ref(0)
const selectedCodes = ref<string[]>([])
const loading = ref(false)
const command = ref<'create' | 'refresh' | 'history' | 'download' | ''>('')
const error = ref('')
let pollTimer: ReturnType<typeof setTimeout> | undefined
let generation = 0

const eligibleFields = computed(() => props.fields.filter((field) =>
  field.readable && !field.masked && !excludedTypes.has(field.type)))
const pending = computed(() => task.value ? ['QUEUED', 'RUNNING'].includes(task.value.status) : false)
const canCreate = computed(() => selectedCodes.value.length > 0 && !command.value && !pending.value)
const statusColor = computed(() => task.value?.status === 'SUCCEEDED' ? 'green'
  : task.value?.status === 'FAILED' ? 'red' : 'blue')

function label(status: RuntimeExportStatus) {
  return ({ QUEUED: '排队中', RUNNING: '正在导出', SUCCEEDED: '已完成', FAILED: '失败' })[status]
}

function stopPolling() {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = undefined
}

function schedulePoll(runGeneration = generation) {
  stopPolling()
  if (!props.open || !task.value || !['QUEUED', 'RUNNING'].includes(task.value.status)) return
  pollTimer = setTimeout(() => void refreshTask(true, runGeneration), 500)
}

function reset() {
  const allowed = new Set(eligibleFields.value.map((field) => field.fieldCode))
  selectedCodes.value = props.defaultFieldCodes.filter((code) => allowed.has(code))
  if (!selectedCodes.value.length) {
    selectedCodes.value = eligibleFields.value.filter((field) => field.showInList)
      .map((field) => field.fieldCode)
  }
  task.value = null
  error.value = ''
  loading.value = true
  const runGeneration = ++generation
  void loadHistory(runGeneration).finally(() => {
    if (runGeneration === generation) loading.value = false
  })
}

function toggleField(code: string, checked: boolean) {
  selectedCodes.value = checked
    ? [...selectedCodes.value, code]
    : selectedCodes.value.filter((item) => item !== code)
  error.value = ''
}

async function createTask() {
  if (!canCreate.value) return
  command.value = 'create'
  error.value = ''
  try {
    task.value = await runtimeExportApi.create(props.systemId, props.moduleCode, {
      query: {
        ...props.query,
        page: 1,
        size: 200,
        columns: [...selectedCodes.value],
        viewId: null,
      },
      fieldCodes: [...selectedCodes.value],
    })
    schedulePoll()
    void loadHistory()
  } catch (cause) {
    error.value = errorMessage(cause, '导出任务创建失败。')
  } finally {
    command.value = ''
  }
}

async function refreshTask(silent = false, runGeneration = generation) {
  if (!task.value) return
  if (!silent) command.value = 'refresh'
  try {
    const result = await runtimeExportApi.task(props.systemId, props.moduleCode, task.value.exportId)
    if (runGeneration !== generation) return
    task.value = result
    if (result.status === 'FAILED') error.value = result.failureMessage || '导出任务失败。'
    if (!['QUEUED', 'RUNNING'].includes(result.status)) void loadHistory(runGeneration)
    schedulePoll(runGeneration)
  } catch (cause) {
    if (runGeneration === generation) {
      error.value = errorMessage(cause, '导出状态刷新失败。')
      if (silent) schedulePoll(runGeneration)
    }
  } finally {
    if (!silent) command.value = ''
  }
}

async function loadHistory(runGeneration = generation) {
  if (runGeneration !== generation) return
  if (!command.value) command.value = 'history'
  try {
    const result = await runtimeExportApi.history(props.systemId, props.moduleCode, 1, 20)
    if (runGeneration !== generation) return
    history.value = result.items
    historyTotal.value = result.total
    if (!task.value && props.initialTaskId) {
      const initial = result.items.find((item) => item.exportId === props.initialTaskId)
      if (initial) openHistory(initial)
    }
  } catch (cause) {
    if (runGeneration === generation) error.value = errorMessage(cause, '导出历史加载失败。')
  } finally {
    if (command.value === 'history') command.value = ''
  }
}

function openHistory(item: RuntimeExportTask) {
  stopPolling()
  task.value = item
  selectedCodes.value = [...item.fieldCodes]
  error.value = item.status === 'FAILED' ? item.failureMessage || '导出任务失败。' : ''
  schedulePoll()
}

async function downloadResult(item = task.value) {
  if (!item || item.status !== 'SUCCEEDED') return
  command.value = 'download'
  error.value = ''
  try {
    const result = await runtimeExportApi.resultWorkbook(props.systemId, props.moduleCode, item.exportId)
    const url = URL.createObjectURL(result.blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = result.filename || `${props.moduleCode}-export.xlsx`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (cause) {
    error.value = errorMessage(cause, '导出结果下载失败。')
  } finally {
    command.value = ''
  }
}

function errorMessage(cause: unknown, fallback: string) {
  if (cause instanceof ApiRequestError) return cause.message || fallback
  return cause instanceof Error ? cause.message : fallback
}

watch(() => [props.open, props.systemId, props.moduleCode, props.initialTaskId] as const, ([open]) => {
  stopPolling()
  generation++
  if (open) reset()
}, { immediate: true })
onBeforeUnmount(() => { generation++; stopPolling() })
</script>

<template>
  <a-drawer :open="open" :width="620" :title="`${moduleName || moduleCode} · 导出`" @close="emit('close')">
    <a-spin :spinning="loading">
      <div class="runtime-export-drawer">
        <a-alert v-if="error" type="error" show-icon closable :message="error" @close="error = ''" />

        <section class="export-fields">
          <div class="export-heading">
            <div><h3>导出当前查询</h3><p>沿用当前范围、筛选和排序，最多 5,000 条记录。</p></div>
            <a-button class="export-create" type="primary" :disabled="!canCreate" :loading="command === 'create'" @click="createTask">
              <Send :size="16" />创建导出任务
            </a-button>
          </div>
          <div class="export-field-options">
            <label v-for="field in eligibleFields" :key="field.fieldCode">
              <input type="checkbox" :checked="selectedCodes.includes(field.fieldCode)" @change="toggleField(field.fieldCode, ($event.target as HTMLInputElement).checked)">
              <span>{{ field.fieldName }}</span><small>{{ field.fieldCode }}</small>
            </label>
          </div>
        </section>

        <section v-if="task" class="export-task">
          <div class="export-heading">
            <div><h3>任务 {{ task.exportId }}</h3><p>{{ task.processedRows }} / {{ task.totalRows ?? '—' }} 条</p></div>
            <a-tag :color="statusColor">{{ label(task.status) }}</a-tag>
          </div>
          <div class="export-actions">
            <a-button :loading="command === 'refresh'" @click="refreshTask(false)"><RefreshCw :size="15" />刷新</a-button>
            <a-button v-if="task.status === 'SUCCEEDED'" class="export-download" type="primary" :loading="command === 'download'" @click="downloadResult()">
              <Download :size="15" />下载 XLSX
            </a-button>
            <span v-if="task.status === 'SUCCEEDED'"><CheckCircle2 :size="16" />结果已就绪</span>
            <span v-else-if="task.status === 'FAILED'"><XCircle :size="16" />{{ task.failureCode }}</span>
          </div>
        </section>

        <section class="export-history">
          <div class="export-heading">
            <div><h3><History :size="17" />最近导出</h3><p>共 {{ historyTotal }} 个任务</p></div>
            <a-button :loading="command === 'history'" @click="loadHistory()"><RefreshCw :size="15" />刷新</a-button>
          </div>
          <button v-for="item in history" :key="item.exportId" type="button" :class="{ active: task?.exportId === item.exportId }" @click="openHistory(item)">
            <FileSpreadsheet :size="17" /><span>{{ item.exportId }}</span><small>{{ label(item.status) }} · {{ item.processedRows }} 条</small>
          </button>
          <a-empty v-if="!history.length" :image="false" description="暂无导出任务" />
        </section>
      </div>
    </a-spin>
  </a-drawer>
</template>

<style scoped>
.runtime-export-drawer{display:grid;gap:18px}.runtime-export-drawer section{display:grid;gap:12px;padding:16px;border:1px solid #dde5e9;background:#fff}.export-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.export-heading h3,.export-heading p{margin:0}.export-heading h3{display:flex;align-items:center;gap:7px;font-size:15px}.export-heading p{margin-top:4px;color:#6d7982;font-size:12px}.export-field-options{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.export-field-options label{display:grid;grid-template-columns:auto minmax(0,1fr);gap:2px 8px;align-items:center;padding:8px;background:#f6f8f9}.export-field-options input{grid-row:1/3}.export-field-options small{color:#7a8790;overflow-wrap:anywhere}.export-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.export-actions span{display:flex;align-items:center;gap:5px;color:#58706b}.export-history>button{width:100%;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:8px;padding:10px;border:1px solid #e1e7ea;background:#fff;text-align:left;cursor:pointer}.export-history>button.active{border-color:#178b7c;background:#eef8f6}.export-history>button small{color:#6d7982}
</style>
