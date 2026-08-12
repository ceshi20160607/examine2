<script setup lang="ts">
import { Download, FileArchive, Paperclip, RefreshCw, Trash2, Upload } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

import { ApiRequestError } from '@/services/api'
import { fileApi } from '@/services/file'
import { useSessionStore } from '@/stores/session'
import type { RecordFileItem } from '@/types/file'

const props = defineProps<{
  systemId: string
  moduleCode: string
  recordId: string
}>()

const session = useSessionStore()
const items = ref<RecordFileItem[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const mutation = ref('')
const error = ref('')
const input = ref<HTMLInputElement | null>(null)
let loadGeneration = 0

const canRead = computed(() =>
  session.hasPermission('system.runtime.access')
  && session.hasPermission(`module.${props.moduleCode}.view`)
  && session.hasPermission('file.read'))
const canUpload = computed(() =>
  canRead.value
  && session.hasPermission('file.create')
  && session.hasPermission('file.reference'))
const canDetach = computed(() =>
  session.hasPermission('file.manage') || session.hasPermission('file.reference'))
const scopeKey = computed(() => [
  props.systemId,
  props.moduleCode,
  props.recordId,
  session.context?.tenantId ?? '',
  session.context?.permissionVersion ?? '',
].join(':'))

async function loadFiles() {
  const generation = ++loadGeneration
  items.value = []
  total.value = 0
  error.value = ''
  if (!canRead.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const result = await fileApi.recordFiles(
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

function chooseFile() {
  input.value?.click()
}

async function uploadFile(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = ''
  if (!file) return
  if (file.size > 20 * 1024 * 1024) {
    error.value = '单个文件不能超过 20 MiB'
    return
  }
  mutation.value = 'upload'
  error.value = ''
  try {
    await fileApi.uploadRecordFile(props.systemId, props.moduleCode, props.recordId, file)
    page.value = 1
    await loadFiles()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function downloadFile(item: RecordFileItem) {
  mutation.value = `download:${item.fileId}`
  error.value = ''
  try {
    const result = await fileApi.downloadRecordFile(
      props.systemId,
      props.moduleCode,
      props.recordId,
      item.fileId,
    )
    const url = URL.createObjectURL(result.blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = result.filename || item.originalName
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function downloadBundle() {
  mutation.value = 'bundle'
  error.value = ''
  try {
    const result = await fileApi.downloadRecordFileBundle(
      props.systemId,
      props.moduleCode,
      props.recordId,
    )
    const url = URL.createObjectURL(result.blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = result.filename || `record-${props.recordId}-files.zip`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function detachFile(item: RecordFileItem) {
  mutation.value = `detach:${item.fileId}`
  error.value = ''
  try {
    await fileApi.detachRecordFile(props.systemId, props.moduleCode, props.recordId, item.fileId)
    const lastItemOnPage = items.value.length === 1 && page.value > 1
    if (lastItemOnPage) page.value -= 1
    else await loadFiles()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function message(cause: unknown) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code
  return cause instanceof Error ? cause.message : '附件请求失败，请稍后重试'
}

function bytes(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`
  return `${(value / 1024 / 1024).toFixed(1)} MiB`
}

function time(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

function resetScope() {
  page.value = 1
  void loadFiles()
}

watch(scopeKey, resetScope, { immediate: true })
watch(page, loadFiles)
</script>

<template>
  <section class="record-file-panel" aria-label="记录附件">
    <header class="record-file-heading">
      <div>
        <h3><Paperclip :size="19" /> 记录附件</h3>
        <p>上传并管理当前记录已经持久化的附件。</p>
      </div>
      <div v-if="canRead" class="record-file-actions">
        <a-button :loading="loading" @click="loadFiles">
          <RefreshCw :size="15" />刷新
        </a-button>
        <a-button :loading="mutation === 'bundle'" @click="downloadBundle">
          <FileArchive :size="15" />下载附件包
        </a-button>
        <a-button v-if="canUpload" type="primary" :loading="mutation === 'upload'" @click="chooseFile">
          <Upload :size="15" />上传
        </a-button>
        <input ref="input" class="file-input" type="file" @change="uploadFile">
      </div>
    </header>

    <a-alert
      v-if="!canRead"
      type="warning"
      show-icon
      message="无权查看记录附件"
      description="需要记录查看权限和 file.read 权限。"
    />
    <template v-else>
      <a-alert v-if="error" type="error" show-icon closable :message="error" @close="error = ''" />
      <a-spin :spinning="loading">
        <a-empty v-if="!items.length && !loading" description="当前记录还没有附件" />
        <div v-else class="record-file-list">
          <article v-for="item in items" :key="item.fileId" class="record-file-item">
            <div class="record-file-summary">
              <strong>{{ item.originalName }}</strong>
              <span>{{ bytes(item.sizeBytes) }} · {{ item.mediaType || 'application/octet-stream' }}</span>
              <small>成员 {{ item.referencedByMemberId }} 于 {{ time(item.referencedAt) }} 关联</small>
            </div>
            <div class="record-file-item-actions">
              <a-button
                :loading="mutation === `download:${item.fileId}`"
                @click="downloadFile(item)"
              >
                <Download :size="15" />下载
              </a-button>
              <a-button
                v-if="canDetach"
                danger
                :loading="mutation === `detach:${item.fileId}`"
                @click="detachFile(item)"
              >
                <Trash2 :size="15" />解除关联
              </a-button>
            </div>
          </article>
        </div>
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
.record-file-panel {
  display: grid;
  gap: 14px;
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid #e2e8f0;
}

.record-file-heading,
.record-file-heading h3,
.record-file-actions,
.record-file-item,
.record-file-item-actions {
  display: flex;
  align-items: center;
}

.record-file-heading,
.record-file-item {
  justify-content: space-between;
  gap: 16px;
}

.record-file-heading h3 {
  gap: 8px;
  margin: 0;
}

.record-file-heading p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.record-file-actions,
.record-file-item-actions {
  gap: 8px;
}

.file-input {
  display: none;
}

.record-file-list,
.record-file-summary {
  display: grid;
  gap: 8px;
}

.record-file-item {
  padding: 12px 0;
  border-bottom: 1px solid #e5eaed;
}

.record-file-summary {
  min-width: 0;
}

.record-file-summary strong {
  overflow-wrap: anywhere;
}

.record-file-summary span,
.record-file-summary small {
  color: #64748b;
}

.record-file-summary span {
  font-size: 12px;
}
</style>
