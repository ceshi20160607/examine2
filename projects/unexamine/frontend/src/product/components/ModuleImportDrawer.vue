<script setup lang="ts">
import { DownloadOutlined, PlayCircleOutlined, ReloadOutlined, RollbackOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import { authorizedBlob, fileScanLabel, importRowLabel, parseCsvHeaders, referenceToAccount, saveBlob, uploadControlledFile } from '../file'
import { productDateTime, productStatus } from '../presentation'
import { systemContext, systemTokens } from '../session'
import type { ControlledFileView, ImportBatchView, ImportRowView, ImportTemplateView } from '../types'

const props = defineProps<{ open: boolean; moduleCode: string; moduleName?: string }>()
const emit = defineEmits<{ 'update:open': [value: boolean]; recordsChanged: [] }>()
const drawerOpen = computed({ get: () => props.open, set: value => emit('update:open', value) })
const token = computed(() => systemTokens.value?.accessToken || '')
const loading = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const sourceFile = ref<ControlledFileView>()
const sourceHeaders = ref<string[]>([])
const mapping = ref<Record<string, string>>({})
const conflictPolicy = ref<'ERROR' | 'SKIP' | 'UPDATE'>('ERROR')
const template = ref<ImportTemplateView>()
const current = ref<ImportBatchView>()
const history = ref<ImportBatchView[]>([])
const error = ref('')
let pollTimer: number | undefined

const targetOptions = computed(() => (template.value?.columns || []).map(column => ({
  value: column.code, label: `${column.name}${column.required ? '（必填）' : ''}`,
})))
const mappedTargets = computed(() => Object.values(mapping.value).filter(Boolean))
const canPreview = computed(() => sourceFile.value?.scanStatus === 'CLEAN' && mappedTargets.value.includes('title'))
const terminal = new Set(['COMPLETED', 'COMPLETED_WITH_ERRORS', 'ROLLED_BACK', 'ROLLBACK_PARTIAL'])

async function load() {
  if (!props.moduleCode || !drawerOpen.value) return
  loading.value = true
  error.value = ''
  try {
    const [templateData, historyData] = await Promise.all([
      api<ImportTemplateView>(`/api/runtime/modules/${props.moduleCode}/imports/template`, {}, token.value),
      api<ImportBatchView[]>(`/api/runtime/modules/${props.moduleCode}/imports`, {}, token.value),
    ])
    template.value = templateData
    history.value = historyData
    if (!current.value && historyData.length) current.value = historyData[0]
  } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '导入工作区加载失败' }
  finally { loading.value = false }
}

async function selectSource(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.csv')) { message.error('当前导入入口仅接受 CSV 文件'); input.value = ''; return }
  sourceHeaders.value = parseCsvHeaders(await file.text())
  if (!sourceHeaders.value.length) { message.error('CSV 表头不能为空'); input.value = ''; return }
  const automatic: Record<string, string> = {}
  sourceHeaders.value.forEach((header, index) => {
    if (template.value?.columns.some(column => column.code === header)) automatic[header] = header
    else if (index === 0 && !Object.values(automatic).includes('title')) automatic[header] = 'title'
  })
  mapping.value = automatic
  uploading.value = true
  uploadProgress.value = 0
  try {
    let uploaded = await uploadControlledFile(file, token.value, progress => { uploadProgress.value = progress })
    if (uploaded.scanStatus === 'CLEAN' && systemContext.value?.accountId) {
      uploaded = await referenceToAccount(uploaded.id, systemContext.value.accountId, token.value)
    }
    sourceFile.value = uploaded
    message.success(uploaded.scanStatus === 'CLEAN' ? '导入文件已扫描并保存引用' : '导入文件未通过扫描，不能预演')
  } catch (reason) { message.error(reason instanceof ApiError ? reason.message : '导入文件上传失败') }
  finally { uploading.value = false; input.value = '' }
}

function setMapping(source: string, target?: string) {
  const next = { ...mapping.value }
  if (target) next[source] = target
  else delete next[source]
  mapping.value = next
}

function mappingOptions(source: string) {
  return targetOptions.value.map(option => ({ ...option,
    disabled: mappedTargets.value.includes(option.value) && mapping.value[source] !== option.value,
  }))
}

async function preview() {
  if (!sourceFile.value || !canPreview.value) return
  loading.value = true
  error.value = ''
  try {
    current.value = await api<ImportBatchView>(`/api/runtime/modules/${props.moduleCode}/imports/preview`, {
      method: 'POST', body: JSON.stringify({
        sourceFileId: sourceFile.value.id, columnMapping: mapping.value,
        conflictPolicy: conflictPolicy.value, mappingName: `${sourceFile.value.originalName} 导入映射`,
      }),
    }, token.value)
    message.success(`预演完成：${current.value.validRows} 行可执行，${current.value.failedRows} 行错误`)
    await loadHistory()
  } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '导入预演失败' }
  finally { loading.value = false }
}

async function execute() {
  if (!current.value || current.value.status !== 'PREVIEWED') return
  loading.value = true
  try {
    current.value = await api<ImportBatchView>(`/api/runtime/modules/${props.moduleCode}/imports/${current.value.id}/execute`, {
      method: 'POST', body: JSON.stringify({ version: current.value.version }),
    }, token.value)
    message.success('导入批次已进入后台执行队列')
    schedulePoll()
  } catch (reason) { message.error(reason instanceof ApiError ? reason.message : '导入执行失败') }
  finally { loading.value = false }
}

function schedulePoll() {
  if (pollTimer) window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(poll, 800)
}

async function poll() {
  if (!current.value || !drawerOpen.value) return
  try {
    current.value = await api<ImportBatchView>(`/api/runtime/modules/${props.moduleCode}/imports/${current.value.id}`, {}, token.value)
    if (terminal.has(current.value.status)) {
      if (['COMPLETED', 'COMPLETED_WITH_ERRORS'].includes(current.value.status)) emit('recordsChanged')
      await loadHistory()
      return
    }
    schedulePoll()
  } catch { schedulePoll() }
}

async function rollback() {
  if (!current.value) return
  loading.value = true
  try {
    current.value = await api<ImportBatchView>(`/api/runtime/modules/${props.moduleCode}/imports/${current.value.id}/rollback`, {
      method: 'POST', body: JSON.stringify({ reason: '用户从导入结果页撤销本批次' }),
    }, token.value)
    message.success(current.value.status === 'ROLLED_BACK' ? '本批次已安全回滚' : '部分记录已有后续修改，未被覆盖')
    emit('recordsChanged')
    await loadHistory()
  } catch (reason) { message.error(reason instanceof ApiError ? reason.message : '批次回滚失败') }
  finally { loading.value = false }
}

async function loadHistory() {
  history.value = await api<ImportBatchView[]>(`/api/runtime/modules/${props.moduleCode}/imports`, {}, token.value)
}

function openBatch(id: number) {
  current.value = history.value.find(batch => batch.id === id)
  if (current.value && !terminal.has(current.value.status) && current.value.status !== 'PREVIEWED') schedulePoll()
}

function downloadTemplate() {
  if (!template.value) return
  saveBlob(new Blob([`\uFEFF${template.value.csvHeader}\n${template.value.sampleRow}\n`], { type: 'text/csv;charset=utf-8' }),
    `${props.moduleCode}-import-template.csv`)
}

async function downloadErrors() {
  if (!current.value) return
  try {
    saveBlob(await authorizedBlob(`/api/runtime/modules/${props.moduleCode}/imports/${current.value.id}/errors.csv`, token.value),
      `${props.moduleCode}-import-${current.value.id}-errors.csv`)
  } catch (reason) { message.error(reason instanceof ApiError ? reason.message : '错误行下载失败') }
}

function statusColor(status: string) {
  if (['COMPLETED', 'ROLLED_BACK', 'SUCCEEDED', 'READY'].includes(status)) return 'green'
  if (['ERROR', 'FAILED', 'COMPLETED_WITH_ERRORS', 'ROLLBACK_PARTIAL', 'ROLLBACK_CONFLICT'].includes(status)) return 'red'
  return 'blue'
}

function operationLabel(operation: string) {
  return ({ CREATE: '新建', UPDATE: '更新', SKIP: '跳过' } as Record<string, string>)[operation] || '待处理'
}

function rowSummary(row: ImportRowView) {
  const values = Object.values(row.raw).filter(value => value !== null && value !== undefined && String(value).trim()).slice(0, 3)
  return values.length ? values.join(' · ') : '空白行'
}

watch(() => [props.open, props.moduleCode], ([open]) => { if (open) void load() }, { immediate: true })
onBeforeUnmount(() => { if (pollTimer) window.clearTimeout(pollTimer) })
</script>

<template>
  <a-drawer v-model:open="drawerOpen" :title="`${moduleName || moduleCode} · 数据导入`" width="880" :mask-closable="false" class="import-drawer">
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <div class="import-steps">
      <section><span>1</span><strong>下载模板并上传</strong><small>文件先走受控上传、安全扫描和账号引用</small></section>
      <section><span>2</span><strong>映射后预演</strong><small>逐行显示新增、更新、跳过和错误，预演零写入</small></section>
      <section><span>3</span><strong>后台执行或回滚</strong><small>每行重新校验权限与版本，避免覆盖后续修改</small></section>
    </div>
    <div class="import-toolbar">
      <a-button @click="downloadTemplate"><DownloadOutlined />下载 CSV 模板</a-button>
      <label class="ant-btn file-upload-button" for="module-import-file"><UploadOutlined />选择 CSV 文件</label>
      <input id="module-import-file" class="visually-hidden" type="file" accept=".csv,text/csv" @change="selectSource" />
      <a-tag v-if="sourceFile" :color="sourceFile.scanStatus === 'CLEAN' ? 'green' : 'red'">{{ sourceFile.originalName }} · {{ fileScanLabel(sourceFile.scanStatus) }}</a-tag>
    </div>
    <a-progress v-if="uploading" :percent="uploadProgress" status="active" />

    <template v-if="sourceHeaders.length">
      <a-divider>字段映射</a-divider>
      <div class="import-mapping-grid"><div v-for="source in sourceHeaders" :key="source"><code>{{ source }}</code><span>→</span><a-select :value="mapping[source]" allow-clear placeholder="忽略此列" :options="mappingOptions(source)" @change="setMapping(source, $event)" /></div></div>
      <div class="import-preview-actions"><a-radio-group v-model:value="conflictPolicy" button-style="solid"><a-radio-button value="ERROR">编号冲突报错</a-radio-button><a-radio-button value="SKIP">跳过已有记录</a-radio-button><a-radio-button value="UPDATE">更新已有记录</a-radio-button></a-radio-group><a-button type="primary" :disabled="!canPreview" :loading="loading" @click="preview">开始预演</a-button></div>
    </template>

    <template v-if="current">
      <a-divider>本次导入 · {{ productDateTime(current.createdAt) }}</a-divider>
      <div class="import-summary"><span><strong>{{ current.totalRows }}</strong>总行数</span><span><strong>{{ current.validRows }}</strong>可执行</span><span><strong>{{ current.successRows }}</strong>成功</span><span><strong>{{ current.failedRows }}</strong>错误</span><a-tag :color="statusColor(current.status)">{{ productStatus(current.status).label }}</a-tag></div>
      <a-table :data-source="current.rows" row-key="id" size="small" :pagination="{ pageSize: 20 }" :scroll="{ x: 900 }">
        <a-table-column title="行" data-index="rowNumber" width="65" /><a-table-column title="计划" width="90"><template #default="{ record }">{{ operationLabel(record.operation) }}</template></a-table-column>
        <a-table-column title="状态" width="120"><template #default="{ record }"><a-tag :color="statusColor(record.status)">{{ importRowLabel(record.status) }}</a-tag></template></a-table-column>
        <a-table-column title="数据摘要"><template #default="{ record }">{{ rowSummary(record) }}</template></a-table-column>
        <a-table-column title="匹配结果" width="100"><template #default="{ record }">{{ record.targetRecordId ? '已匹配' : '新记录' }}</template></a-table-column>
        <a-table-column title="说明" width="220"><template #default="{ record }">{{ record.errorMessage || (record.rollbackStatus ? importRowLabel(record.rollbackStatus) : '校验通过') }}</template></a-table-column>
      </a-table>
      <div class="drawer-footer import-result-actions"><a-select v-if="history.length" placeholder="查看历史导入" style="width:240px" :options="history.map(batch => ({ value: batch.id, label: `${productDateTime(batch.createdAt)} · ${productStatus(batch.status).label}` }))" @change="openBatch" /><a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button><a-button v-if="current.failedRows" @click="downloadErrors"><DownloadOutlined />下载错误行</a-button><a-popconfirm v-if="['COMPLETED', 'COMPLETED_WITH_ERRORS', 'ROLLBACK_PARTIAL'].includes(current.status)" title="只会撤销本次导入且未被后续修改的记录，确认继续？" @confirm="rollback"><a-button danger :loading="loading"><RollbackOutlined />安全回滚</a-button></a-popconfirm><a-button v-if="current.status === 'PREVIEWED' && current.validRows" type="primary" :loading="loading" @click="execute"><PlayCircleOutlined />确认后台执行</a-button></div>
    </template>
    <template #footer><div class="drawer-footer"><span>权限、字段可写范围和数据归属会在每一行执行时重新校验。</span><a-button @click="drawerOpen = false">关闭</a-button></div></template>
  </a-drawer>
</template>
