<script setup lang="ts">
import { CheckCircle2, Download, FileSpreadsheet, History, RefreshCw, RotateCcw, Send, Upload, XCircle } from 'lucide-vue-next'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { ApiRequestError } from '@/services/api'
import { runtimeImportApi } from '@/services/runtimeImport'
import type {
  RuntimeImportBatch,
  RuntimeImportBatchStatus,
  RuntimeImportMode,
  RuntimeImportTemplate,
} from '@/types/runtimeImport'

const props = defineProps<{
  open: boolean
  systemId: string
  moduleCode: string
  moduleName: string
  initialTaskId?: string
}>()

const emit = defineEmits<{
  close: []
  changed: []
}>()

const template = ref<RuntimeImportTemplate | null>(null)
const batch = ref<RuntimeImportBatch | null>(null)
const mode = ref<RuntimeImportMode>('NEW')
const matchFieldCode = ref('')
const sourceType = ref<'EXCEL' | 'JSON'>('EXCEL')
const source = ref('[\n  {}\n]')
const workbookFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const history = ref<RuntimeImportBatch[]>([])
const historyTotal = ref(0)
const loading = ref(false)
const command = ref<'preview' | 'commit' | 'rollback' | 'refresh' | 'template' | 'errors' | 'history' | ''>('')
const error = ref('')
let pollTimer: ReturnType<typeof setTimeout> | undefined
let generation = 0
let emittedTerminal = ''

const uniqueFields = computed(() => template.value?.fields.filter((field) => field.unique) ?? [])
const polling = computed(() => Boolean(batch.value && isPending(batch.value.status)))
const canPreview = computed(() => Boolean(template.value) && !command.value && !polling.value
  && (sourceType.value === 'JSON' || Boolean(workbookFile.value)))
const canCommit = computed(() => batch.value?.status === 'READY' && !command.value)
const canRollback = computed(() => batch.value?.status === 'COMMITTED' && !command.value)
const statusLabel = computed(() => batch.value ? labelFor(batch.value.status) : '')
const statusColor = computed(() => {
  if (!batch.value) return 'default'
  if (['READY', 'COMMITTED', 'ROLLED_BACK'].includes(batch.value.status)) return 'green'
  if (['INVALID', 'FAILED'].includes(batch.value.status)) return 'red'
  return 'blue'
})

function isPending(status: RuntimeImportBatchStatus) {
  return ['PREVIEW_QUEUED', 'PREVIEWING', 'COMMIT_QUEUED', 'COMMITTING',
    'ROLLBACK_QUEUED', 'ROLLING_BACK'].includes(status)
}

function labelFor(status: RuntimeImportBatchStatus) {
  return ({
    PREVIEW_QUEUED: '预检排队中',
    PREVIEWING: '正在预检',
    READY: '预检通过',
    INVALID: '预检未通过',
    COMMIT_QUEUED: '提交排队中',
    COMMITTING: '正在写入',
    COMMITTED: '已提交',
    ROLLBACK_QUEUED: '回滚排队中',
    ROLLING_BACK: '正在回滚',
    ROLLED_BACK: '已回滚',
    FAILED: '任务失败',
  })[status]
}

function rowStatusLabel(status: string) {
  return ({
    PREVIEW_PENDING: '等待预检',
    VALID: '有效',
    INVALID: '无效',
    COMMITTED: '已写入',
    ROLLED_BACK: '已回滚',
  } as Record<string, string>)[status] ?? status
}

function stopPolling() {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = undefined
}

function schedulePoll(runGeneration: number) {
  stopPolling()
  if (!props.open || !batch.value || !isPending(batch.value.status)) return
  pollTimer = setTimeout(() => void refreshBatch(true, runGeneration), 500)
}

async function loadTemplate() {
  const runGeneration = ++generation
  stopPolling()
  template.value = null
  batch.value = null
  emittedTerminal = ''
  error.value = ''
  loading.value = true
  try {
    const result = await runtimeImportApi.template(props.systemId, props.moduleCode)
    if (runGeneration !== generation) return
    template.value = result
    sourceType.value = 'EXCEL'
    workbookFile.value = null
    mode.value = result.modes.includes('NEW') ? 'NEW' : result.modes[0] ?? 'NEW'
    matchFieldCode.value = result.fields.find((field) => field.unique)?.fieldCode ?? ''
    source.value = JSON.stringify([Object.fromEntries(result.fields.map((field) => [
      field.fieldCode,
      field.required ? sampleValue(field.type) : null,
    ]))], null, 2)
    void loadHistory(runGeneration)
  } catch (cause) {
    if (runGeneration === generation) error.value = errorMessage(cause, '导入模板加载失败，请稍后重试。')
  } finally {
    if (runGeneration === generation) loading.value = false
  }
}

async function loadHistory(runGeneration = generation) {
  if (runGeneration !== generation) return
  command.value = command.value || 'history'
  try {
    const result = await runtimeImportApi.history(props.systemId, props.moduleCode, 1, 20)
    if (runGeneration !== generation) return
    history.value = result.items
    historyTotal.value = result.total
    if (!batch.value && props.initialTaskId) {
      const initial = result.items.find((item) => item.batchId === props.initialTaskId)
      if (initial) openHistory(initial)
    }
  } catch (cause) {
    if (runGeneration === generation) error.value = errorMessage(cause, '导入历史加载失败。')
  } finally {
    if (command.value === 'history') command.value = ''
  }
}

function saveDownload(result: { blob: Blob; filename: string }, fallback: string) {
  const url = URL.createObjectURL(result.blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = result.filename || fallback
  anchor.click()
  URL.revokeObjectURL(url)
}

async function downloadTemplate() {
  command.value = 'template'
  error.value = ''
  try {
    saveDownload(await runtimeImportApi.templateWorkbook(props.systemId, props.moduleCode),
      `${props.moduleCode}-import-template.xlsx`)
  } catch (cause) {
    error.value = errorMessage(cause, 'Excel 导入模板下载失败。')
  } finally {
    command.value = ''
  }
}

function chooseWorkbook() {
  fileInput.value?.click()
}

function selectWorkbook(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0] ?? null
  target.value = ''
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    error.value = '只支持 .xlsx Excel 工作簿。'
    return
  }
  if (file.size > 1024 * 1024) {
    error.value = 'Excel 文件不能超过 1 MiB。'
    return
  }
  workbookFile.value = file
  error.value = ''
}

function sampleValue(type: string): unknown {
  if (['NUMBER', 'MONEY', 'RATING'].includes(type)) return 0
  if (type === 'CHECKBOX') return false
  if (type === 'MULTI_SELECT') return []
  if (type === 'DATE') return '2026-07-29'
  if (type === 'DATETIME') return '2026-07-29T12:00:00+08:00'
  return '请填写'
}

function parseRows() {
  let value: unknown
  try {
    value = JSON.parse(source.value)
  } catch {
    throw new Error('请输入有效的 JSON 数组。')
  }
  if (!Array.isArray(value) || !value.length) throw new Error('导入数据必须是至少包含一行的 JSON 数组。')
  if (value.length > (template.value?.maxRows ?? 200)) {
    throw new Error(`当前批次最多导入 ${template.value?.maxRows ?? 200} 行。`)
  }
  if (value.some((row) => !row || Array.isArray(row) || typeof row !== 'object')) {
    throw new Error('数组中的每一行都必须是 JSON 对象。')
  }
  return value as Record<string, unknown>[]
}

async function preview() {
  if (!template.value) return
  error.value = ''
  if (mode.value === 'UPSERT' && !matchFieldCode.value) {
    error.value = '新增或更新模式必须选择一个唯一匹配字段。'
    return
  }
  command.value = 'preview'
  try {
    if (sourceType.value === 'EXCEL') {
      if (!workbookFile.value) throw new Error('请先选择一个 .xlsx 文件。')
      batch.value = await runtimeImportApi.previewWorkbook(
        props.systemId,
        props.moduleCode,
        workbookFile.value,
        mode.value,
        mode.value === 'UPSERT' ? matchFieldCode.value : undefined,
      )
    } else {
      const rows = parseRows()
      batch.value = await runtimeImportApi.preview(props.systemId, props.moduleCode, {
        mode: mode.value,
        matchFieldCode: mode.value === 'UPSERT' ? matchFieldCode.value : undefined,
        rows,
      })
    }
    schedulePoll(generation)
    void loadHistory()
  } catch (cause) {
    error.value = errorMessage(cause, '导入预检创建失败。')
  } finally {
    command.value = ''
  }
}

async function refreshBatch(silent = false, runGeneration = generation) {
  if (!batch.value) return
  if (!silent) command.value = 'refresh'
  if (!silent) error.value = ''
  try {
    const result = await runtimeImportApi.batch(props.systemId, props.moduleCode, batch.value.batchId)
    if (runGeneration !== generation) return
    batch.value = result
    if (result.status === 'FAILED') {
      error.value = '导入任务执行失败；批次未发生部分写入，请修正数据或状态后重新预检。'
    }
    if (['COMMITTED', 'ROLLED_BACK'].includes(result.status) && emittedTerminal !== result.status) {
      emittedTerminal = result.status
      emit('changed')
    }
    if (!isPending(result.status)) void loadHistory(runGeneration)
    schedulePoll(runGeneration)
  } catch (cause) {
    if (runGeneration === generation) {
      error.value = errorMessage(cause, '导入批次状态刷新失败。')
      if (silent) schedulePoll(runGeneration)
    }
  } finally {
    if (!silent) command.value = ''
  }
}

function openHistory(item: RuntimeImportBatch) {
  stopPolling()
  batch.value = item
  emittedTerminal = ['COMMITTED', 'ROLLED_BACK'].includes(item.status) ? item.status : ''
  error.value = ''
  schedulePoll(generation)
}

async function downloadErrors() {
  if (!batch.value?.failedRows) return
  command.value = 'errors'
  error.value = ''
  try {
    saveDownload(await runtimeImportApi.errorWorkbook(
      props.systemId, props.moduleCode, batch.value.batchId,
    ), `${props.moduleCode}-import-${batch.value.batchId}-errors.xlsx`)
  } catch (cause) {
    error.value = errorMessage(cause, '错误行工作簿下载失败。')
  } finally {
    command.value = ''
  }
}

async function commit() {
  if (!batch.value || !canCommit.value) return
  command.value = 'commit'
  error.value = ''
  try {
    batch.value = await runtimeImportApi.commit(props.systemId, props.moduleCode, batch.value.batchId)
    schedulePoll(generation)
  } catch (cause) {
    error.value = errorMessage(cause, '提交导入批次失败。')
  } finally {
    command.value = ''
  }
}

async function rollback() {
  if (!batch.value || !canRollback.value) return
  command.value = 'rollback'
  error.value = ''
  try {
    batch.value = await runtimeImportApi.rollback(props.systemId, props.moduleCode, batch.value.batchId)
    schedulePoll(generation)
  } catch (cause) {
    error.value = errorMessage(cause, '回滚请求失败；数据未被部分修改。')
  } finally {
    command.value = ''
  }
}

function errorMessage(cause: unknown, fallback: string) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code || fallback
  return cause instanceof Error ? cause.message : fallback
}

function close() {
  stopPolling()
  emit('close')
}

watch(() => [props.open, props.systemId, props.moduleCode, props.initialTaskId] as const, ([open], previous) => {
  if (!open) {
    stopPolling()
    return
  }
  if (!previous?.[0] || previous[1] !== props.systemId || previous[2] !== props.moduleCode) {
    void loadTemplate()
  }
}, { immediate: true })

onBeforeUnmount(() => {
  generation += 1
  stopPolling()
})
</script>

<template>
  <a-drawer
    :open="open"
    :width="760"
    :title="`批量导入 · ${moduleName}`"
    :mask-closable="!command"
    @close="close"
  >
    <a-spin :spinning="loading">
      <div class="runtime-import">
        <a-alert
          type="info"
          show-icon
          message="先预检，再提交"
          :description="`Excel 和 JSON 都进入同一预检链路，每批最多 ${template?.maxRows ?? 200} 行；预检不会写入业务数据。`"
        />

        <div v-if="template" class="import-schema">
          <div>
            <strong>可导入字段</strong>
            <span>字段代码是 Excel 表头或 JSON 属性名</span>
          </div>
          <div class="import-field-tags">
            <a-tag v-for="field in template.fields" :key="field.fieldCode" :color="field.required ? 'blue' : 'default'">
              {{ field.fieldName }} · {{ field.fieldCode }}<template v-if="field.unique"> · 唯一</template>
            </a-tag>
          </div>
        </div>

        <div v-if="template" class="import-source-switch">
          <a-segmented v-model:value="sourceType" :options="[{ label: 'Excel', value: 'EXCEL' }, { label: 'JSON（高级）', value: 'JSON' }]" />
        </div>

        <div v-if="template" class="import-options">
          <label>
            <span>导入模式</span>
            <a-select v-model:value="mode" :disabled="polling || Boolean(command)">
              <a-select-option value="NEW">仅新增</a-select-option>
              <a-select-option value="UPSERT" :disabled="!uniqueFields.length">新增或更新</a-select-option>
            </a-select>
          </label>
          <label v-if="mode === 'UPSERT'">
            <span>唯一匹配字段</span>
            <a-select v-model:value="matchFieldCode" :disabled="polling || Boolean(command)">
              <a-select-option v-for="field in uniqueFields" :key="field.fieldCode" :value="field.fieldCode">
                {{ field.fieldName }} · {{ field.fieldCode }}
              </a-select-option>
            </a-select>
          </label>
        </div>

        <div v-if="template && sourceType === 'EXCEL'" class="import-workbook">
          <input ref="fileInput" class="sr-only" type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" @change="selectWorkbook">
          <div class="import-workbook-actions">
            <a-button :loading="command === 'template'" :disabled="Boolean(command) || polling" @click="downloadTemplate">
              <Download :size="16" />下载当前模板
            </a-button>
            <a-button :disabled="Boolean(command) || polling" @click="chooseWorkbook">
              <FileSpreadsheet :size="16" />选择 Excel 文件
            </a-button>
          </div>
          <span>{{ workbookFile ? `${workbookFile.name} · ${Math.max(1, Math.ceil(workbookFile.size / 1024))} KiB` : '尚未选择文件' }}</span>
        </div>

        <label v-if="template && sourceType === 'JSON'" class="import-source">
          <span>JSON 行数据</span>
          <a-textarea
            v-model:value="source"
            :rows="12"
            spellcheck="false"
            :disabled="polling || Boolean(command)"
          />
        </label>

        <a-alert v-if="error" type="error" show-icon closable :message="error" @close="error = ''" />

        <section v-if="batch" class="import-result">
          <header>
            <div>
              <strong>批次 {{ batch.batchId }}</strong>
              <span>共 {{ batch.totalRows }} 行</span>
            </div>
            <div class="import-status-actions">
              <a-tag :color="statusColor">{{ statusLabel }}</a-tag>
              <a-button aria-label="刷新导入批次" :loading="command === 'refresh'" :disabled="polling" @click="refreshBatch()">
                <RefreshCw :size="15" />
              </a-button>
            </div>
          </header>
          <div class="import-counts">
            <span><CheckCircle2 :size="16" />新增 {{ batch.newRows }}</span>
            <span><Send :size="16" />更新 {{ batch.updateRows }}</span>
            <span :class="{ failed: batch.failedRows }"><XCircle :size="16" />失败 {{ batch.failedRows }}</span>
          </div>
          <a-button v-if="batch.failedRows" class="download-errors" :loading="command === 'errors'" @click="downloadErrors">
            <Download :size="16" />下载错误行
          </a-button>
          <div v-if="batch.rows.length" class="import-row-table-wrap">
            <table class="import-row-table">
              <thead><tr><th>行</th><th>计划</th><th>状态</th><th>结果</th></tr></thead>
              <tbody>
                <tr v-for="row in batch.rows" :key="row.rowNumber">
                  <td>{{ row.rowNumber }}</td>
                  <td>{{ row.action ?? '—' }}</td>
                  <td>{{ rowStatusLabel(row.status) }}</td>
                  <td class="import-row-result">{{ row.errorMessage ?? row.targetRecordId ?? '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section v-if="history.length" class="import-history">
          <header>
            <div><History :size="17" /><strong>最近导入</strong><span>共 {{ historyTotal }} 个批次</span></div>
            <a-button aria-label="刷新导入历史" :loading="command === 'history'" @click="loadHistory()"><RefreshCw :size="15" /></a-button>
          </header>
          <button v-for="item in history.slice(0, 6)" :key="item.batchId" type="button" :class="{ active: batch?.batchId === item.batchId }" @click="openHistory(item)">
            <span>{{ item.batchId }}</span><span>{{ labelFor(item.status) }}</span><small>{{ item.totalRows }} 行 · 新增 {{ item.newRows }} · 更新 {{ item.updateRows }} · 失败 {{ item.failedRows }}</small>
          </button>
        </section>
      </div>
    </a-spin>

    <template #footer>
      <div class="import-footer">
        <a-button @click="close">关闭</a-button>
        <a-button v-if="canRollback" danger :loading="command === 'rollback'" @click="rollback">
          <RotateCcw :size="16" />安全回滚
        </a-button>
        <a-button v-if="canCommit" type="primary" :loading="command === 'commit'" @click="commit">
          <Send :size="16" />提交写入
        </a-button>
        <a-button v-else type="primary" :disabled="!canPreview" :loading="command === 'preview'" @click="preview">
          <Upload :size="16" />开始预检
        </a-button>
      </div>
    </template>
  </a-drawer>
</template>

<style scoped>
.runtime-import{display:grid;gap:18px}.import-schema{display:grid;gap:10px;padding:12px;border:1px solid #dfe6e9;background:#f8fafb}.import-schema>div:first-child{display:flex;align-items:center;justify-content:space-between;gap:12px}.import-schema span{color:#687680;font-size:12px}.import-field-tags{display:flex;flex-wrap:wrap;gap:6px}.import-source-switch{display:flex;justify-content:flex-start}.import-options{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.import-options label,.import-source{display:grid;gap:7px}.import-options label>span,.import-source>span{font-weight:650;color:#34434d}.import-options .ant-select{width:100%}.import-workbook{display:grid;gap:9px;padding:14px;border:1px dashed #b8c8cf;background:#fafcfc}.import-workbook-actions{display:flex;flex-wrap:wrap;gap:8px}.import-workbook-actions .ant-btn,.download-errors{display:inline-flex;align-items:center;gap:6px}.import-workbook>span{color:#5f6e78;font-size:12px;overflow-wrap:anywhere}.import-source :deep(textarea){font:13px/1.55 Consolas,"SFMono-Regular",monospace}.import-result{display:grid;gap:12px;border-top:1px solid #dfe6e9;padding-top:16px}.import-result header{display:flex;align-items:center;justify-content:space-between;gap:12px}.import-result header>div:first-child{display:grid;gap:3px}.import-result header span{color:#687680;font-size:12px}.import-status-actions,.import-counts,.import-footer{display:flex;align-items:center;gap:8px}.import-status-actions .ant-btn,.import-footer .ant-btn{display:inline-flex;align-items:center;gap:6px}.import-counts{flex-wrap:wrap}.import-counts>span{display:inline-flex;align-items:center;gap:5px;padding:5px 9px;background:#f0f7f5;color:#176b5c}.import-counts>span.failed{background:#fff1f0;color:#b42318}.import-row-table-wrap{max-height:280px;overflow:auto;border:1px solid #e1e7ea}.import-row-table{width:100%;border-collapse:collapse}.import-row-table th,.import-row-table td{padding:8px 10px;border-bottom:1px solid #e6ebed;text-align:left;vertical-align:top}.import-row-table th{position:sticky;top:0;background:#f6f8f9;color:#64727c;font-size:12px}.import-row-result{max-width:360px;overflow-wrap:anywhere}.import-history{display:grid;border-top:1px solid #dfe6e9;padding-top:14px}.import-history header{display:flex;align-items:center;justify-content:space-between;margin-bottom:7px}.import-history header>div{display:flex;align-items:center;gap:7px}.import-history header span{color:#687680;font-size:12px}.import-history>button{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:3px 12px;padding:9px;border:0;border-bottom:1px solid #e6ebed;background:#fff;text-align:left;color:#34434d;cursor:pointer}.import-history>button.active{background:#e9f4f2;color:#087f73}.import-history>button small{grid-column:1/-1;color:#71808a}.import-footer{justify-content:flex-end}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
</style>
