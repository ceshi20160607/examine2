<script setup lang="ts">
import { Download, FileText, History, Printer, RefreshCw } from 'lucide-vue-next'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { ApiRequestError } from '@/services/api'
import { runtimePrintApi } from '@/services/print'
import type { RuntimePrintPreview, RuntimePrintTask, RuntimePrintTemplate } from '@/types/print'

const props = defineProps<{
  open: boolean
  systemId: string
  moduleCode: string
  recordId: string
  recordVersion: number
  recordTitle: string
  initialTaskId?: string
}>()

const emit = defineEmits<{ close: [] }>()
const templates = ref<RuntimePrintTemplate[]>([])
const selectedTemplateCode = ref('')
const preview = ref<RuntimePrintPreview | null>(null)
const task = ref<RuntimePrintTask | null>(null)
const history = ref<RuntimePrintTask[]>([])
const historyTotal = ref(0)
const loading = ref(false)
const command = ref<'preview' | 'create' | 'refresh' | 'download' | ''>('')
const error = ref('')
const previewFrame = ref<HTMLIFrameElement | null>(null)
let pollTimer: ReturnType<typeof setTimeout> | undefined
let generation = 0

const pending = computed(() => task.value ? ['QUEUED', 'RUNNING'].includes(task.value.status) : false)
const selectedTemplate = computed(() => templates.value.find((item) => item.code === selectedTemplateCode.value))

function stopPolling() {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = undefined
}

function schedulePoll(runGeneration = generation) {
  stopPolling()
  if (!props.open || !task.value || !['QUEUED', 'RUNNING'].includes(task.value.status)) return
  pollTimer = setTimeout(() => void refreshTask(true, runGeneration), 500)
}

async function reset() {
  const runGeneration = ++generation
  stopPolling()
  loading.value = true
  error.value = ''
  preview.value = null
  task.value = null
  try {
    const [templateValues, historyValue] = await Promise.all([
      runtimePrintApi.templates(props.systemId, props.moduleCode, props.recordId),
      runtimePrintApi.history(props.systemId, props.moduleCode, props.recordId),
    ])
    if (runGeneration !== generation) return
    templates.value = templateValues
    selectedTemplateCode.value = templateValues[0]?.code ?? ''
    history.value = historyValue.items
    historyTotal.value = historyValue.total
    if (props.initialTaskId) {
      const initial = historyValue.items.find((item) => item.printId === props.initialTaskId)
      if (initial) openHistory(initial)
    }
  } catch (cause) {
    if (runGeneration === generation) error.value = errorMessage(cause, '打印信息加载失败。')
  } finally {
    if (runGeneration === generation) loading.value = false
  }
}

async function loadPreview() {
  if (!selectedTemplateCode.value) return
  command.value = 'preview'
  error.value = ''
  try {
    preview.value = await runtimePrintApi.preview(props.systemId, props.moduleCode, props.recordId,
      selectedTemplateCode.value)
  } catch (cause) {
    error.value = errorMessage(cause, '打印预览生成失败。')
  } finally {
    command.value = ''
  }
}

async function createTask() {
  if (!selectedTemplateCode.value || pending.value) return
  command.value = 'create'
  error.value = ''
  try {
    task.value = await runtimePrintApi.create(props.systemId, props.moduleCode, props.recordId,
      selectedTemplateCode.value, props.recordVersion)
    schedulePoll()
    void loadHistory()
  } catch (cause) {
    error.value = errorMessage(cause, 'PDF 打印任务创建失败。')
  } finally {
    command.value = ''
  }
}

async function refreshTask(silent = false, runGeneration = generation) {
  if (!task.value) return
  if (!silent) command.value = 'refresh'
  try {
    const result = await runtimePrintApi.task(props.systemId, props.moduleCode, props.recordId, task.value.printId)
    if (runGeneration !== generation) return
    task.value = result
    if (result.status === 'FAILED') error.value = result.failureMessage || 'PDF 打印任务失败。'
    if (!['QUEUED', 'RUNNING'].includes(result.status)) void loadHistory(runGeneration)
    schedulePoll(runGeneration)
  } catch (cause) {
    if (runGeneration === generation) {
      error.value = errorMessage(cause, '打印任务状态刷新失败。')
      if (silent) schedulePoll(runGeneration)
    }
  } finally {
    if (!silent) command.value = ''
  }
}

async function loadHistory(runGeneration = generation) {
  try {
    const result = await runtimePrintApi.history(props.systemId, props.moduleCode, props.recordId)
    if (runGeneration !== generation) return
    history.value = result.items
    historyTotal.value = result.total
  } catch (cause) {
    if (runGeneration === generation) error.value = errorMessage(cause, '打印历史加载失败。')
  }
}

function openHistory(item: RuntimePrintTask) {
  task.value = item
  selectedTemplateCode.value = item.templateCode
  schedulePoll()
}

async function download(item = task.value) {
  if (!item || item.status !== 'SUCCEEDED') return
  command.value = 'download'
  error.value = ''
  try {
    const result = await runtimePrintApi.resultPdf(props.systemId, props.moduleCode, props.recordId, item.printId)
    const url = URL.createObjectURL(result.blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = result.filename || `${props.moduleCode}-${props.recordId}.pdf`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (cause) {
    error.value = errorMessage(cause, 'PDF 下载失败。')
  } finally {
    command.value = ''
  }
}

function printPreview() {
  previewFrame.value?.contentWindow?.print()
}

function label(status: RuntimePrintTask['status']) {
  return ({ QUEUED: '排队中', RUNNING: '生成中', SUCCEEDED: '已完成', FAILED: '失败' })[status]
}

function errorMessage(cause: unknown, fallback: string) {
  if (cause instanceof ApiRequestError) return cause.message || fallback
  return cause instanceof Error ? cause.message : fallback
}

watch(() => [props.open, props.systemId, props.moduleCode, props.recordId, props.initialTaskId] as const, ([open]) => {
  stopPolling()
  generation++
  if (open) void reset()
}, { immediate: true })
watch(selectedTemplateCode, () => { preview.value = null; error.value = '' })
onBeforeUnmount(() => { generation++; stopPolling() })
</script>

<template>
  <a-drawer :open="open" :width="720" :title="`打印 · ${recordTitle || recordId}`" @close="emit('close')">
    <a-spin :spinning="loading">
      <div class="runtime-print-drawer">
        <a-alert v-if="error" type="error" show-icon closable :message="error" @close="error = ''" />

        <section class="print-controls">
          <div>
            <label for="runtime-print-template">已发布模板</label>
            <a-select id="runtime-print-template" v-model:value="selectedTemplateCode" style="width:100%" placeholder="选择模板">
              <a-select-option v-for="template in templates" :key="template.code" :value="template.code">
                {{ template.name }} · V{{ template.templateVersionNo }} · {{ template.paperSize }}
              </a-select-option>
            </a-select>
          </div>
          <div class="print-actions">
            <a-button :disabled="!selectedTemplateCode" :loading="command === 'preview'" @click="loadPreview"><FileText :size="15" />生成预览</a-button>
            <a-button v-if="preview" @click="printPreview"><Printer :size="15" />浏览器打印</a-button>
            <a-button type="primary" :disabled="!selectedTemplateCode || pending" :loading="command === 'create'" @click="createTask"><Printer :size="15" />生成 PDF</a-button>
          </div>
          <small v-if="selectedTemplate">绑定模块发布版本 {{ selectedTemplate.schemaVersionId }}，模板版本 {{ selectedTemplate.templateVersionId }}</small>
          <a-empty v-if="!templates.length" :image="false" description="管理员尚未发布可用打印模板" />
        </section>

        <section v-if="preview" class="print-preview">
          <header><strong>打印预览</strong><span>记录版本 v{{ preview.recordVersion }} · 模板 V{{ preview.templateVersionNo }}</span></header>
          <iframe ref="previewFrame" title="打印预览" sandbox="allow-modals" :srcdoc="preview.html" />
        </section>

        <section v-if="task" class="print-task">
          <header><div><strong>PDF 任务 {{ task.printId }}</strong><span>{{ task.templateName }} · V{{ task.templateVersionNo }}</span></div><a-tag :color="task.status === 'SUCCEEDED' ? 'green' : task.status === 'FAILED' ? 'red' : 'blue'">{{ label(task.status) }}</a-tag></header>
          <div class="print-actions">
            <a-button :loading="command === 'refresh'" @click="refreshTask(false)"><RefreshCw :size="15" />刷新</a-button>
            <a-button v-if="task.status === 'SUCCEEDED'" type="primary" :loading="command === 'download'" @click="download()"><Download :size="15" />下载 PDF</a-button>
          </div>
        </section>

        <section class="print-history">
          <header><div><strong><History :size="16" />打印记录</strong><span>共 {{ historyTotal }} 条</span></div><a-button size="small" @click="loadHistory()"><RefreshCw :size="14" />刷新</a-button></header>
          <button v-for="item in history" :key="item.printId" type="button" :class="{ active: task?.printId === item.printId }" @click="openHistory(item)">
            <FileText :size="16" /><span>{{ item.templateName }} · V{{ item.templateVersionNo }}</span><small>{{ label(item.status) }} · 记录 v{{ item.recordVersion }}</small>
          </button>
          <a-empty v-if="!history.length" :image="false" description="暂无打印记录" />
        </section>
      </div>
    </a-spin>
  </a-drawer>
</template>

<style scoped>
.runtime-print-drawer{display:grid;gap:16px}.runtime-print-drawer section{display:grid;gap:12px;padding:14px;border:1px solid #dfe6e9}.print-controls>div:first-child{display:grid;gap:6px}.print-controls label{font-weight:650}.print-controls small,.runtime-print-drawer header span{color:#697781;font-size:12px}.print-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.print-actions .ant-btn{display:inline-flex;align-items:center;gap:5px}.runtime-print-drawer header{display:flex;align-items:center;justify-content:space-between;gap:12px}.runtime-print-drawer header>div{display:grid;gap:3px}.print-preview iframe{width:100%;height:500px;border:1px solid #cfd8dc;background:#fff}.print-history header strong{display:flex;align-items:center;gap:6px}.print-history>button{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:8px;padding:10px;border:1px solid #e1e7ea;background:#fff;text-align:left;cursor:pointer}.print-history>button.active{border-color:#168774;background:#eef8f6}.print-history>button small{color:#6d7982}
</style>
