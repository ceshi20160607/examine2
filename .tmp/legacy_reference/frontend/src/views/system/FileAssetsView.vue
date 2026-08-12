<script setup lang="ts">
import {
  Copy,
  Download,
  Eye,
  FileImage,
  FileText,
  FileUp,
  HardDrive,
  Link2,
  RefreshCw,
  RotateCcw,
  Search,
  Trash2,
  Unlink,
  X,
} from 'lucide-vue-next'
import { Modal } from 'ant-design-vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { fileApi } from '@/services/file'
import { useSessionStore } from '@/stores/session'
import type {
  FileAsset,
  FileReference,
  FileStorageStatus,
  MultipartUploadSession,
} from '@/types/file'

const DEFAULT_SINGLE_UPLOAD_BYTES = 20 * 1024 * 1024
const DEFAULT_MULTIPART_UPLOAD_BYTES = 100 * 1024 * 1024
const DEFAULT_PART_BYTES = 5 * 1024 * 1024

type DetailTab = 'overview' | 'preview' | 'references'
type UploadStage = 'HASHING' | 'INITIALIZING' | 'UPLOADING' | 'COMPLETING' | 'FAILED' | 'CANCELLING' | 'CANCELLED' | 'COMPLETED'

interface MultipartUiState {
  file: File
  uploadId: string
  sha256: string
  partSizeBytes: number
  partCount: number
  nextPartNumber: number
  uploadedBytes: number
  progress: number
  stage: UploadStage
  error: string
  cancelRequested: boolean
  cancelFailed: boolean
}

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const tenantId = computed(() => session.context?.tenantId ?? '')
const selectedId = computed(() => typeof route.query.file === 'string' ? route.query.file : '')
const canUpload = computed(() => session.hasPermission('file.create'))
const canReference = computed(() => session.hasPermission('file.reference'))
const canManage = computed(() => session.hasPermission('file.manage'))

const assets = ref<FileAsset[]>([])
const asset = ref<FileAsset | null>(null)
const storage = ref<FileStorageStatus | null>(null)
const keyword = ref('')
const appliedKeyword = ref('')
const mediaType = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalPages = ref(0)
const activeTab = ref<DetailTab>('overview')
const referenceType = ref('MODULE_RECORD')
const referenceId = ref('')
const listLoading = ref(false)
const detailLoading = ref(false)
const storageLoading = ref(false)
const mutation = ref('')
const error = ref('')
const notice = ref('')
const storageError = ref('')
const thumbnailFailures = ref(new Set<string>())

const previewUrl = ref('')
const previewLoading = ref(false)
const previewError = ref('')
let previewGeneration = 0

const multipart = ref<MultipartUiState | null>(null)
let partController: AbortController | null = null

const columns = [
  { title: '文件', key: 'name', width: 330 },
  { title: '媒体类型', dataIndex: 'mediaType', key: 'mediaType', width: 170 },
  { title: '大小', dataIndex: 'size', key: 'size', width: 110 },
  { title: '上传成员', dataIndex: 'uploaderMemberId', key: 'uploaderMemberId', width: 120 },
  { title: '上传时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '引用', key: 'references', width: 80, align: 'center' },
  { title: '操作', key: 'actions', width: 230, fixed: 'right' },
]

const mediaOptions = [
  { value: '', label: '全部类型' },
  { value: 'image/*', label: '图片' },
  { value: 'application/pdf', label: 'PDF' },
  { value: 'text/*', label: '文本' },
  { value: 'application/octet-stream', label: '其他文件' },
]

const singleUploadLimit = computed(() => positiveLimit(storage.value?.maxSingleUploadBytes, DEFAULT_SINGLE_UPLOAD_BYTES))
const multipartUploadLimit = computed(() => positiveLimit(storage.value?.maxMultipartUploadBytes, DEFAULT_MULTIPART_UPLOAD_BYTES))
const uploadUnavailable = computed(() => storage.value?.available === false)
const uploadBusy = computed(() => mutation.value === 'upload'
  || Boolean(multipart.value && !['FAILED', 'CANCELLED', 'COMPLETED'].includes(multipart.value.stage)))
const isImagePreview = computed(() => Boolean(asset.value?.mediaType.toLowerCase().startsWith('image/')))
const isPdfPreview = computed(() => asset.value?.mediaType.toLowerCase() === 'application/pdf')
const supportsPreview = computed(() => isImagePreview.value || isPdfPreview.value)
const safeStorageLocation = computed(() => {
  const location = storage.value?.location?.trim() ?? ''
  if (storage.value?.mode === 'LOCAL') return '本地存储（路径已隐藏）'
  return location || 'S3 位置已配置'
})

async function loadList() {
  listLoading.value = true
  error.value = ''
  try {
    const result = await fileApi.list(systemId.value, {
      page: page.value,
      size: pageSize.value,
      keyword: appliedKeyword.value,
      mediaType: mediaType.value,
    })
    assets.value = result.items
    page.value = result.page
    pageSize.value = result.size
    total.value = result.total
    totalPages.value = result.totalPages
  } catch (cause) {
    assets.value = []
    total.value = 0
    totalPages.value = 0
    error.value = message(cause)
  } finally {
    listLoading.value = false
  }
}

async function loadStorage() {
  storageLoading.value = true
  storageError.value = ''
  try {
    storage.value = await fileApi.storageStatus(systemId.value)
  } catch (cause) {
    storage.value = null
    storageError.value = message(cause)
  } finally {
    storageLoading.value = false
  }
}

async function loadSelected() {
  disposePreview()
  previewError.value = ''
  if (!selectedId.value) {
    asset.value = null
    return
  }
  detailLoading.value = true
  error.value = ''
  try {
    asset.value = await fileApi.get(systemId.value, selectedId.value)
    replaceListAsset(asset.value)
  } catch (cause) {
    asset.value = null
    error.value = message(cause)
  } finally {
    detailLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadList(), loadStorage(), loadSelected()])
}

async function applyFilters() {
  appliedKeyword.value = keyword.value.trim()
  page.value = 1
  await loadList()
}

async function resetFilters() {
  keyword.value = ''
  appliedKeyword.value = ''
  mediaType.value = ''
  page.value = 1
  await loadList()
}

async function changePage(nextPage: number, nextSize: number) {
  if (nextSize !== pageSize.value) {
    pageSize.value = nextSize
    page.value = 1
  } else {
    page.value = nextPage
  }
  await loadList()
}

async function openAsset(fileId: string) {
  activeTab.value = 'overview'
  await router.replace({ query: { ...route.query, file: fileId } })
}

async function closeDetails() {
  const query = { ...route.query }
  delete query.file
  await router.replace({ query })
}

async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || uploadBusy.value) return
  error.value = ''
  notice.value = ''
  if (uploadUnavailable.value) {
    error.value = '当前文件存储不可用，无法上传。'
    return
  }
  if (file.size > multipartUploadLimit.value) {
    error.value = `单个文件不能超过 ${bytes(multipartUploadLimit.value)}`
    return
  }
  if (file.size <= singleUploadLimit.value) {
    await uploadSmall(file)
    return
  }
  await startMultipart(file)
}

async function uploadSmall(file: File) {
  mutation.value = 'upload'
  try {
    const uploaded = await fileApi.upload(systemId.value, file)
    notice.value = `“${uploaded.originalName}”上传完成。`
    await loadList()
    asset.value = uploaded
    await openAsset(uploaded.id)
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function startMultipart(file: File) {
  mutation.value = 'upload'
  multipart.value = {
    file,
    uploadId: '',
    sha256: '',
    partSizeBytes: positiveLimit(storage.value?.maxPartBytes, DEFAULT_PART_BYTES),
    partCount: Math.ceil(file.size / positiveLimit(storage.value?.maxPartBytes, DEFAULT_PART_BYTES)),
    nextPartNumber: 1,
    uploadedBytes: 0,
    progress: 0,
    stage: 'HASHING',
    error: '',
    cancelRequested: false,
    cancelFailed: false,
  }
  try {
    const digest = await sha256(file)
    const state = multipart.value
    if (!state || state.cancelRequested) return
    state.sha256 = digest
    state.stage = 'INITIALIZING'
    const initialized = await fileApi.initMultipart(systemId.value, {
      originalName: file.name,
      mediaType: file.type || 'application/octet-stream',
      sizeBytes: file.size,
      sha256: digest,
    })
    if (!multipart.value || multipart.value.cancelRequested) {
      await fileApi.abortMultipart(systemId.value, initialized.uploadId)
      return
    }
    applyMultipartSession(multipart.value, initialized)
    await uploadRemainingParts()
  } catch (cause) {
    failMultipart(cause)
  } finally {
    if (!multipart.value || ['FAILED', 'CANCELLED', 'COMPLETED'].includes(multipart.value.stage)) mutation.value = ''
  }
}

function applyMultipartSession(state: MultipartUiState, initialized: MultipartUploadSession) {
  state.uploadId = initialized.uploadId
  state.partSizeBytes = initialized.partSizeBytes
  state.partCount = initialized.partCount
  state.nextPartNumber = 1
  state.stage = 'UPLOADING'
}

async function uploadRemainingParts() {
  const state = multipart.value
  if (!state || !state.uploadId || state.cancelRequested) return
  mutation.value = 'upload'
  state.error = ''
  state.cancelFailed = false
  try {
    state.stage = 'UPLOADING'
    while (state.nextPartNumber <= state.partCount) {
      if (state.cancelRequested) return
      const partNumber = state.nextPartNumber
      const start = (partNumber - 1) * state.partSizeBytes
      const end = Math.min(start + state.partSizeBytes, state.file.size)
      const chunk = state.file.slice(start, end)
      const partSha256 = await sha256(chunk)
      if (state.cancelRequested) return
      partController = new AbortController()
      await fileApi.uploadMultipartPart(
        systemId.value,
        state.uploadId,
        partNumber,
        chunk,
        partSha256,
        partController.signal,
      )
      partController = null
      state.nextPartNumber += 1
      state.uploadedBytes = end
      state.progress = Math.min(99, Math.floor((end / state.file.size) * 100))
    }
    if (state.cancelRequested) return
    state.stage = 'COMPLETING'
    const uploaded = await fileApi.completeMultipart(systemId.value, state.uploadId, state.sha256)
    state.progress = 100
    state.stage = 'COMPLETED'
    mutation.value = ''
    notice.value = `“${uploaded.originalName}”分片上传完成。`
    await loadList()
    asset.value = uploaded
    await openAsset(uploaded.id)
  } catch (cause) {
    if (!state.cancelRequested) failMultipart(cause)
  } finally {
    partController = null
    if (['FAILED', 'CANCELLED', 'COMPLETED'].includes(state.stage)) mutation.value = ''
  }
}

async function retryMultipart() {
  const state = multipart.value
  if (!state || state.stage !== 'FAILED') return
  if (state.cancelFailed) {
    await cancelMultipart()
  } else if (state.uploadId) {
    await uploadRemainingParts()
  } else {
    await startMultipart(state.file)
  }
}

async function cancelMultipart() {
  const state = multipart.value
  if (!state || ['CANCELLED', 'COMPLETED'].includes(state.stage)) return
  state.cancelRequested = true
  state.cancelFailed = false
  state.stage = 'CANCELLING'
  partController?.abort()
  try {
    if (state.uploadId) await fileApi.abortMultipart(systemId.value, state.uploadId)
    state.stage = 'CANCELLED'
    state.error = ''
    mutation.value = ''
  } catch (cause) {
    state.stage = 'FAILED'
    state.cancelFailed = true
    state.error = `取消失败：${message(cause)}`
    mutation.value = ''
  }
}

function dismissMultipart() {
  if (multipart.value && ['CANCELLED', 'COMPLETED'].includes(multipart.value.stage)) multipart.value = null
}

function failMultipart(cause: unknown) {
  if (!multipart.value || multipart.value.cancelRequested) return
  multipart.value.stage = 'FAILED'
  multipart.value.error = message(cause)
  mutation.value = ''
}

async function loadPreview() {
  const current = asset.value
  disposePreview()
  previewError.value = ''
  if (!current || activeTab.value !== 'preview') return
  if (!supportsPreview.value) {
    previewError.value = '该文件类型不支持在线预览，请下载后查看。'
    return
  }
  const generation = ++previewGeneration
  previewLoading.value = true
  try {
    const result = await fileApi.preview(systemId.value, current.id)
    if (generation !== previewGeneration) return
    previewUrl.value = URL.createObjectURL(result.blob)
  } catch (cause) {
    if (generation === previewGeneration) previewError.value = `${message(cause)}，请下载后查看。`
  } finally {
    if (generation === previewGeneration) previewLoading.value = false
  }
}

function disposePreview() {
  previewGeneration += 1
  previewLoading.value = false
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

async function download(target = asset.value) {
  if (!target) return
  mutation.value = `download:${target.id}`
  error.value = ''
  try {
    const result = await fileApi.download(systemId.value, target.id)
    saveBlob(result.blob, result.filename || target.originalName)
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

async function copyId(fileId: string) {
  try {
    await navigator.clipboard.writeText(fileId)
    notice.value = `文件 ID ${fileId} 已复制。`
  } catch {
    error.value = '浏览器无法复制文件 ID，请手动选择复制。'
  }
}

async function addReference() {
  if (!asset.value || !referenceType.value.trim() || !referenceId.value.trim()) return
  mutation.value = 'reference'
  error.value = ''
  try {
    asset.value = await fileApi.addReference(systemId.value, asset.value.id, {
      targetType: referenceType.value.trim(),
      targetId: referenceId.value.trim(),
    })
    referenceId.value = ''
    replaceListAsset(asset.value)
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function removeReference(reference: FileReference) {
  if (!asset.value) return
  mutation.value = `unreference:${reference.targetType}:${reference.targetId}`
  error.value = ''
  try {
    asset.value = await fileApi.removeReference(systemId.value, asset.value.id, {
      targetType: reference.targetType,
      targetId: reference.targetId,
    })
    replaceListAsset(asset.value)
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function confirmDelete(target: FileAsset) {
  Modal.confirm({
    title: `删除“${target.originalName}”？`,
    content: target.references.length
      ? '文件仍有关联，后端会拒绝删除。请先解除全部关联。'
      : '删除后文件内容无法恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => deleteAsset(target),
  })
}

async function deleteAsset(target: FileAsset) {
  mutation.value = `delete:${target.id}`
  error.value = ''
  try {
    await fileApi.delete(systemId.value, target.id)
    notice.value = `“${target.originalName}”已删除。`
    if (selectedId.value === target.id) await closeDetails()
    await loadList()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function canDelete(target: FileAsset) {
  return canManage.value || target.uploaderMemberId === session.context?.memberId
}

function replaceListAsset(updated: FileAsset | null) {
  if (!updated) return
  const index = assets.value.findIndex(item => item.id === updated.id)
  if (index >= 0) assets.value.splice(index, 1, updated)
}

function failThumbnail(fileId: string) {
  thumbnailFailures.value = new Set([...thumbnailFailures.value, fileId])
}

function message(cause: unknown) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code
  if (cause instanceof DOMException && cause.name === 'AbortError') return '上传已取消'
  return cause instanceof Error ? cause.message : '文件请求失败，请稍后重试'
}

function positiveLimit(value: number | undefined, fallback: number) {
  return value && value > 0 ? value : fallback
}

function bytes(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`
  return `${(value / 1024 / 1024).toFixed(1)} MiB`
}

function dateTime(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('zh-CN', { hour12: false })
}

function isImage(media: string) {
  return media.toLowerCase().startsWith('image/')
}

function uploadStageLabel(state: MultipartUiState) {
  const labels: Record<UploadStage, string> = {
    HASHING: '正在计算文件摘要',
    INITIALIZING: '正在创建分片会话',
    UPLOADING: `正在上传第 ${Math.min(state.nextPartNumber, state.partCount)} / ${state.partCount} 片`,
    COMPLETING: '正在校验并登记文件',
    FAILED: state.cancelFailed ? '取消失败' : '上传中断，可从失败分片重试',
    CANCELLING: '正在取消并清理暂存内容',
    CANCELLED: '上传已取消，暂存内容已清理',
    COMPLETED: '分片上传完成',
  }
  return labels[state.stage]
}

async function sha256(blob: Blob) {
  const digest = await crypto.subtle.digest('SHA-256', await blob.arrayBuffer())
  return Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, '0')).join('')
}

watch([systemId, tenantId], () => {
  page.value = 1
  void Promise.all([loadList(), loadStorage()])
}, { immediate: true })
watch(selectedId, () => void loadSelected(), { immediate: true })
watch([() => asset.value?.id, activeTab], () => void loadPreview())

onBeforeUnmount(() => {
  disposePreview()
  partController?.abort()
  if (multipart.value?.uploadId && !['CANCELLED', 'COMPLETED'].includes(multipart.value.stage)) {
    void fileApi.abortMultipart(systemId.value, multipart.value.uploadId)
  }
})
</script>

<template>
  <section class="files-page">
    <header class="files-heading">
      <div>
        <h1>文件中心</h1>
        <p>查找、上传、预览和维护当前系统与租户内的文件。</p>
      </div>
      <div class="files-heading-actions">
        <a-button :loading="listLoading || storageLoading" @click="refreshAll"><RefreshCw :size="15" />刷新</a-button>
        <label v-if="canUpload" class="upload-command">
          <input type="file" aria-label="选择要上传的文件" :disabled="uploadBusy || uploadUnavailable" @change="upload" />
          <a-button type="primary" :loading="mutation === 'upload'" :disabled="uploadBusy || uploadUnavailable"><FileUp :size="16" />上传文件</a-button>
        </label>
      </div>
    </header>

    <section class="storage-status" :class="{ unavailable: storage && !storage.available }">
      <HardDrive :size="18" />
      <template v-if="storage">
        <strong>{{ storage.mode === 'S3' ? 'S3 兼容存储' : '本地存储' }}</strong>
        <a-tag :color="storage.available ? 'green' : 'red'">{{ storage.available ? '可用' : '不可用' }}</a-tag>
        <span>{{ safeStorageLocation }}</span>
        <span>普通上传 ≤ {{ bytes(singleUploadLimit) }}</span>
        <span>分片上传 ≤ {{ bytes(multipartUploadLimit) }}</span>
      </template>
      <span v-else>{{ storageLoading ? '正在读取存储状态…' : '存储状态不可用' }}</span>
    </section>
    <a-alert v-if="storageError" class="files-alert" type="warning" show-icon :message="storageError" />

    <section class="files-toolbar" aria-label="文件筛选">
      <a-input v-model:value="keyword" class="file-keyword" allow-clear placeholder="搜索文件名称" @press-enter="applyFilters">
        <template #prefix><Search :size="15" /></template>
      </a-input>
      <a-select v-model:value="mediaType" class="file-media-filter" :options="mediaOptions" @change="applyFilters" />
      <a-button type="primary" @click="applyFilters">查询</a-button>
      <a-button @click="resetFilters"><RotateCcw :size="15" />重置</a-button>
      <span class="files-total">共 {{ total }} 个文件</span>
    </section>

    <a-alert v-if="error" class="files-alert" type="error" show-icon closable :message="error" @close="error = ''" />
    <a-alert v-if="notice" class="files-alert" type="success" show-icon closable :message="notice" @close="notice = ''" />

    <section v-if="multipart" class="multipart-progress" :class="multipart.stage.toLowerCase()">
      <div class="multipart-progress-heading">
        <div><strong>{{ multipart.file.name }}</strong><span>{{ bytes(multipart.file.size) }} · {{ uploadStageLabel(multipart) }}</span></div>
        <button v-if="['CANCELLED', 'COMPLETED'].includes(multipart.stage)" type="button" title="关闭" aria-label="关闭上传进度" @click="dismissMultipart"><X :size="16" /></button>
      </div>
      <progress :value="multipart.progress" max="100">{{ multipart.progress }}%</progress>
      <div class="multipart-progress-meta"><span>{{ multipart.progress }}%</span><span v-if="multipart.partCount">{{ Math.max(0, multipart.nextPartNumber - 1) }} / {{ multipart.partCount }} 个分片已确认</span></div>
      <a-alert v-if="multipart.error" class="multipart-error" type="error" show-icon :message="multipart.error" />
      <div class="multipart-actions">
        <a-button v-if="multipart.stage === 'FAILED'" type="primary" @click="retryMultipart"><RotateCcw :size="15" />{{ multipart.cancelFailed ? '重试取消' : '从失败处重试' }}</a-button>
        <a-button v-if="!['CANCELLED', 'COMPLETED', 'CANCELLING'].includes(multipart.stage)" danger @click="cancelMultipart">取消上传</a-button>
      </div>
    </section>

    <section class="files-table-region">
      <a-table
        :columns="columns"
        :data-source="assets"
        :loading="listLoading"
        :pagination="false"
        :locale="{ emptyText: '当前筛选条件下没有文件' }"
        :scroll="{ x: 1220 }"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <div class="file-name-cell">
              <span class="file-thumbnail">
                <img
                  v-if="isImage(record.mediaType) && !thumbnailFailures.has(record.id)"
                  :src="fileApi.thumbnailUrl(systemId, record.id, 56, 56)"
                  :alt="record.originalName"
                  @error="failThumbnail(record.id)"
                />
                <FileImage v-else-if="isImage(record.mediaType)" :size="22" />
                <FileText v-else :size="22" />
              </span>
              <span><button type="button" class="file-name-link" @click="openAsset(record.id)">{{ record.originalName }}</button><small>ID {{ record.id }}</small></span>
            </div>
          </template>
          <template v-else-if="column.key === 'mediaType'">{{ record.mediaType }}</template>
          <template v-else-if="column.key === 'uploaderMemberId'">{{ record.uploaderMemberId }}</template>
          <template v-else-if="column.key === 'size'">{{ bytes(record.size) }}</template>
          <template v-else-if="column.key === 'createdAt'">{{ dateTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'references'"><a-tag>{{ record.references.length }}</a-tag></template>
          <template v-else-if="column.key === 'actions'">
            <div class="file-row-actions">
              <a-button type="link" size="small" @click="openAsset(record.id)"><Eye :size="14" />详情</a-button>
              <a-button type="link" size="small" :loading="mutation === `download:${record.id}`" @click="download(record)"><Download :size="14" />下载</a-button>
              <a-button v-if="canDelete(record)" type="link" size="small" danger :loading="mutation === `delete:${record.id}`" @click="confirmDelete(record)"><Trash2 :size="14" />删除</a-button>
            </div>
          </template>
        </template>
      </a-table>
      <footer class="files-pagination">
        <span>第 {{ page }} / {{ Math.max(totalPages, 1) }} 页</span>
        <a-pagination
          :current="page"
          :page-size="pageSize"
          :total="total"
          :show-size-changer="true"
          :page-size-options="['10', '20', '50']"
          show-less-items
          @change="changePage"
          @show-size-change="changePage"
        />
      </footer>
    </section>

    <a-spin :spinning="detailLoading">
      <section v-if="asset" class="file-detail">
        <header class="file-detail-heading">
          <div><h2>{{ asset.originalName }}</h2><p>{{ asset.mediaType }} · {{ bytes(asset.size) }} · ID {{ asset.id }}</p></div>
          <div class="file-detail-actions">
            <a-button @click="copyId(asset.id)"><Copy :size="15" />复制 ID</a-button>
            <a-button :loading="mutation === `download:${asset.id}`" @click="download(asset)"><Download :size="15" />下载</a-button>
            <a-button v-if="canDelete(asset)" danger :loading="mutation === `delete:${asset.id}`" @click="confirmDelete(asset)"><Trash2 :size="15" />删除</a-button>
            <a-button type="text" title="关闭详情" aria-label="关闭文件详情" @click="closeDetails"><X :size="17" /></a-button>
          </div>
        </header>

        <a-tabs v-model:active-key="activeTab" class="file-detail-tabs">
          <a-tab-pane key="overview" tab="概览">
            <dl class="file-overview-grid">
              <div><dt>文件名称</dt><dd>{{ asset.originalName }}</dd></div>
              <div><dt>媒体类型</dt><dd>{{ asset.mediaType }}</dd></div>
              <div><dt>文件大小</dt><dd>{{ bytes(asset.size) }}</dd></div>
              <div><dt>上传成员</dt><dd>{{ asset.uploaderMemberId }}</dd></div>
              <div><dt>上传时间</dt><dd>{{ dateTime(asset.createdAt) }}</dd></div>
              <div><dt>版本</dt><dd>{{ asset.version }}</dd></div>
              <div class="span-two"><dt>SHA-256</dt><dd class="hash-value">{{ asset.sha256 }}</dd></div>
            </dl>
          </a-tab-pane>
          <a-tab-pane key="preview" tab="预览">
            <section class="file-preview">
              <a-spin v-if="previewLoading" class="file-preview-loading" />
              <img v-else-if="previewUrl && isImagePreview" :src="previewUrl" :alt="asset.originalName" />
              <iframe v-else-if="previewUrl && isPdfPreview" :src="previewUrl" :title="`${asset.originalName} PDF 预览`" />
              <div v-else class="file-preview-fallback">
                <FileText :size="38" />
                <strong>{{ previewError || '正在准备预览…' }}</strong>
                <p>原文件不会在页面中执行；可下载到本地使用可信应用查看。</p>
                <a-button type="primary" @click="download(asset)"><Download :size="15" />下载文件</a-button>
              </div>
            </section>
          </a-tab-pane>
          <a-tab-pane key="references" :tab="`引用关系 ${asset.references.length}`">
            <section class="reference-section">
              <div v-if="canReference" class="reference-form">
                <a-input v-model:value="referenceType" placeholder="对象类型" />
                <a-input v-model:value="referenceId" placeholder="对象 ID" @press-enter="addReference" />
                <a-button type="primary" :loading="mutation === 'reference'" @click="addReference"><Link2 :size="15" />添加引用</a-button>
              </div>
              <a-empty v-if="!asset.references.length" description="没有业务引用" />
              <div v-else class="reference-list">
                <div class="reference-list-head"><span>对象类型</span><span>对象 ID</span><span>创建成员</span><span>创建时间</span><span>操作</span></div>
                <div v-for="reference in asset.references" :key="`${reference.targetType}:${reference.targetId}`" class="reference-row">
                  <span>{{ reference.targetType }}</span><span>{{ reference.targetId }}</span><span>{{ reference.createdByMemberId }}</span><span>{{ dateTime(reference.createdAt) }}</span>
                  <a-button v-if="canReference || canManage" type="link" danger :loading="mutation === `unreference:${reference.targetType}:${reference.targetId}`" @click="removeReference(reference)"><Unlink :size="14" />解除</a-button>
                </div>
              </div>
            </section>
          </a-tab-pane>
        </a-tabs>
      </section>
      <a-empty v-else-if="!detailLoading && !selectedId" class="file-detail-empty" description="从列表选择文件查看详情" />
    </a-spin>
  </section>
</template>

<style scoped>
.files-page { display: grid; gap: 14px; min-width: 0; max-width: 1480px; margin: 0 auto; padding: 22px 24px 48px; background: #eef1f4; }
.files-heading, .files-heading-actions, .storage-status, .files-toolbar, .file-name-cell, .file-row-actions, .file-detail-heading, .file-detail-actions, .multipart-progress-heading, .multipart-actions { display: flex; align-items: center; }
.files-heading, .file-detail-heading, .multipart-progress-heading { justify-content: space-between; gap: 18px; }
.files-heading { min-height: 58px; padding-bottom: 14px; border-bottom: 1px solid #ccd4dc; }
.files-heading h1 { margin: 0; font-size: 21px; }
.files-heading p, .file-detail-heading p { margin: 5px 0 0; color: #657181; }
.files-heading-actions, .file-detail-actions, .multipart-actions { gap: 8px; }
.upload-command input { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.storage-status { min-height: 42px; gap: 10px; padding: 8px 12px; border: 1px solid #cbd7d4; border-radius: 5px; background: #f7fbfa; color: #4f5d69; font-size: 12px; flex-wrap: wrap; }
.storage-status > svg { color: #087f73; }
.storage-status strong { color: #27364b; font-size: 13px; }
.storage-status span + span { padding-left: 10px; border-left: 1px solid #d8e0e4; }
.storage-status.unavailable { border-color: #e1c5c5; background: #fff8f8; }
.files-toolbar { gap: 8px; padding: 12px; border: 1px solid #d3dae1; background: #fff; }
.file-keyword { width: min(360px, 38vw); }
.file-media-filter { width: 160px; }
.files-total { margin-left: auto; color: #6d7885; font-size: 12px; }
.files-alert { margin: 0; }
.files-table-region { min-width: 0; overflow: hidden; border: 1px solid #d3dae1; border-radius: 5px; background: #fff; }
.files-table-region :deep(.ant-table-thead > tr > th) { padding: 10px 12px; background: #f3f5f7; color: #4b5766; }
.files-table-region :deep(.ant-table-tbody > tr > td) { padding: 9px 12px; }
.file-name-cell { min-width: 0; gap: 10px; }
.file-thumbnail { display: grid; flex: 0 0 auto; place-items: center; width: 42px; height: 42px; overflow: hidden; border: 1px solid #dce2e7; border-radius: 4px; background: #f5f7f9; color: #64748b; }
.file-thumbnail img { width: 100%; height: 100%; object-fit: cover; }
.file-name-cell > span:last-child { min-width: 0; }
.file-name-link { display: block; max-width: 250px; overflow: hidden; border: 0; background: none; color: #1d4f78; padding: 0; text-align: left; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; font-weight: 600; }
.file-name-cell small { display: block; margin-top: 3px; color: #7a8591; }
.file-row-actions { gap: 0; white-space: nowrap; }
.file-row-actions .ant-btn { padding-inline: 5px; }
.files-pagination { display: flex; align-items: center; justify-content: space-between; min-height: 54px; padding: 8px 12px; border-top: 1px solid #e0e5e9; color: #6d7885; font-size: 12px; }
.multipart-progress { padding: 14px 16px; border: 1px solid #cdd7df; border-radius: 5px; background: #fff; }
.multipart-progress-heading div { min-width: 0; }
.multipart-progress-heading strong, .multipart-progress-heading span { display: block; }
.multipart-progress-heading strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.multipart-progress-heading span { margin-top: 3px; color: #687483; font-size: 12px; }
.multipart-progress-heading button { border: 0; background: none; color: #657181; cursor: pointer; }
.multipart-progress progress { width: 100%; height: 8px; margin-top: 12px; accent-color: #087f73; }
.multipart-progress-meta { display: flex; justify-content: space-between; color: #687483; font-size: 12px; }
.multipart-actions { margin-top: 10px; }
.multipart-error { margin-top: 10px; }
.file-detail { min-width: 0; border: 1px solid #d3dae1; border-radius: 5px; background: #fff; }
.file-detail-heading { min-height: 72px; padding: 12px 16px; border-bottom: 1px solid #d9dfe5; }
.file-detail-heading h2 { margin: 0; font-size: 17px; }
.file-detail-tabs { padding: 0 16px 16px; }
.file-overview-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 0; border: 1px solid #d9dfe5; border-bottom: 0; }
.file-overview-grid > div { display: grid; grid-template-columns: 110px minmax(0, 1fr); min-height: 44px; border-bottom: 1px solid #d9dfe5; }
.file-overview-grid > div:nth-child(odd):not(.span-two) { border-right: 1px solid #d9dfe5; }
.file-overview-grid .span-two { grid-column: 1 / -1; }
.file-overview-grid dt, .file-overview-grid dd { display: flex; align-items: center; margin: 0; padding: 9px 11px; }
.file-overview-grid dt { background: #f5f7f9; color: #647080; }
.file-overview-grid dd { min-width: 0; overflow-wrap: anywhere; }
.hash-value { font-family: Consolas, monospace; font-size: 12px; }
.file-preview { display: grid; min-height: 440px; place-items: center; overflow: hidden; border: 1px solid #d9dfe5; background: #f4f6f8; }
.file-preview > img { display: block; max-width: 100%; max-height: 620px; object-fit: contain; }
.file-preview > iframe { width: 100%; height: 620px; border: 0; background: #fff; }
.file-preview-fallback { display: grid; max-width: 480px; place-items: center; padding: 36px; text-align: center; }
.file-preview-fallback svg { color: #74808d; }
.file-preview-fallback strong { margin-top: 12px; }
.file-preview-fallback p { margin: 7px 0 16px; color: #687483; line-height: 1.6; }
.reference-section { display: grid; gap: 12px; }
.reference-form { display: grid; grid-template-columns: minmax(180px, .8fr) minmax(240px, 1fr) auto; gap: 8px; padding: 12px; border: 1px solid #d9dfe5; background: #f7f8fa; }
.reference-list { overflow-x: auto; border: 1px solid #d9dfe5; }
.reference-list-head, .reference-row { display: grid; grid-template-columns: 1fr 1fr 110px 180px 80px; align-items: center; min-height: 42px; padding: 0 12px; gap: 10px; }
.reference-list-head { background: #f3f5f7; color: #4b5766; font-weight: 600; }
.reference-row { border-top: 1px solid #e2e6ea; }
.reference-row span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-detail-empty { padding: 46px 0; border: 1px solid #d3dae1; background: #fff; }
@media (max-width: 1024px) {
  .files-page { padding: 20px; }
  .files-heading { align-items: flex-start; }
  .files-toolbar { flex-wrap: wrap; }
  .file-keyword { flex: 1 1 260px; width: auto; }
  .files-total { flex: 1 0 100%; margin-left: 0; text-align: right; }
  .file-detail-heading { align-items: flex-start; }
  .file-detail-actions { flex-wrap: wrap; justify-content: flex-end; }
  .reference-list-head, .reference-row { min-width: 760px; }
}
@media (max-width: 640px) {
  .files-page { gap: 12px; padding: 14px; }
  .files-heading, .file-detail-heading, .multipart-progress-heading { align-items: stretch; flex-direction: column; gap: 12px; }
  .files-heading-actions, .file-detail-actions { justify-content: flex-start; flex-wrap: wrap; }
  .files-toolbar { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); }
  .file-keyword, .file-media-filter, .files-total { grid-column: 1 / -1; width: 100%; }
  .files-total { text-align: left; }
  .files-pagination { align-items: flex-start; flex-direction: column; gap: 10px; overflow-x: auto; }
  .files-pagination .ant-pagination { min-width: max-content; }
  .file-detail-tabs { padding-inline: 12px; }
  .file-overview-grid { grid-template-columns: minmax(0,1fr); }
  .file-overview-grid > div:nth-child(odd):not(.span-two) { border-right: 0; }
  .file-overview-grid .span-two { grid-column: auto; }
  .file-overview-grid > div { grid-template-columns: 96px minmax(0,1fr); }
  .file-preview { min-height: 280px; }
  .file-preview > iframe { height: 420px; }
  .file-preview-fallback { padding: 24px 16px; }
  .reference-form { grid-template-columns: minmax(0,1fr); }
}
</style>
