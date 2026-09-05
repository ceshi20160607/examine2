<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { DeleteOutlined, DownloadOutlined, EyeOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { ApiError, api } from '../api'
import { authorizedBlob, fileScanLabel, saveBlob, uploadBusinessAttachment } from '../file'
import { platformTokens, systemTokens } from '../session'
import { productDateTime } from '../presentation'
import type { BusinessAttachmentView } from '../types'

const props = withDefaults(defineProps<{
  endpoint: string
  context?: 'platform' | 'system'
  title?: string
  description?: string
  compact?: boolean
  writable?: boolean
}>(), { context: 'system', title: '附件', description: '文件会先安全扫描，再保存到当前业务对象。', compact: false, writable: true })
const emit = defineEmits<{ changed: [] }>()
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken || '' : systemTokens.value?.accessToken || '')
const rows = ref<BusinessAttachmentView[]>([])
const loading = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const retryFile = ref<File>()
const error = ref('')

function describeError(reason: unknown, fallback: string) {
  return reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（追踪号 ${reason.traceId}）` : ''}` : fallback
}

async function load() {
  if (!token.value || !props.endpoint) return
  loading.value = true
  error.value = ''
  try { rows.value = await api<BusinessAttachmentView[]>(props.endpoint, {}, token.value) }
  catch (reason) { error.value = describeError(reason, '附件加载失败') }
  finally { loading.value = false }
}

async function upload(file: File) {
  if (!props.endpoint) return
  retryFile.value = file
  uploading.value = true
  uploadProgress.value = 0
  error.value = ''
  try {
    const result = await uploadBusinessAttachment(file, props.endpoint, token.value, value => { uploadProgress.value = value })
    if (result.scanStatus !== 'CLEAN') message.warning('文件未通过安全扫描，未加入业务附件')
    else message.success('附件已保存到当前业务对象')
    retryFile.value = undefined
    await load()
    emit('changed')
  } catch (reason) { error.value = describeError(reason, '附件上传失败，可直接重试') }
  finally { uploading.value = false }
}

function selectFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) void upload(file)
  input.value = ''
}

async function preview(item: BusinessAttachmentView) {
  if (!item.attachmentId) return
  try {
    const blob = await authorizedBlob(`/api/business-attachments/${item.attachmentId}/preview`, token.value)
    window.open(URL.createObjectURL(blob), '_blank', 'noopener,noreferrer')
  } catch (reason) { message.error(describeError(reason, '附件预览失败')) }
}

async function download(item: BusinessAttachmentView) {
  if (!item.attachmentId) return
  try { saveBlob(await authorizedBlob(`/api/business-attachments/${item.attachmentId}/download`, token.value), item.originalName) }
  catch (reason) { message.error(describeError(reason, '附件下载失败')) }
}

async function remove(item: BusinessAttachmentView) {
  if (!item.attachmentId) return
  try {
    await api(`/api/business-attachments/${item.attachmentId}`, { method: 'DELETE' }, token.value)
    await load()
    emit('changed')
    message.success('附件已移除')
  } catch (reason) { message.error(describeError(reason, '附件移除失败')) }
}

function size(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

watch(() => [props.endpoint, token.value], load, { immediate: true })
</script>

<template>
  <section :class="['business-attachments', { compact }]">
    <header class="business-attachments__header"><span><strong>{{ title }}</strong><small>{{ description }}</small></span><span><a-button size="small" :loading="loading" @click="load"><ReloadOutlined />刷新</a-button><label v-if="writable" class="ant-btn ant-btn-sm ant-btn-primary" :for="`attachment-input-${endpoint.replace(/[^a-z0-9]/gi, '-')}`"><UploadOutlined />选择文件</label><input v-if="writable" :id="`attachment-input-${endpoint.replace(/[^a-z0-9]/gi, '-')}`" class="visually-hidden" type="file" @change="selectFile" /></span></header>
    <a-progress v-if="uploading" :percent="uploadProgress" status="active" size="small" />
    <a-alert v-if="error" type="error" show-icon :message="error"><template v-if="retryFile" #action><a-button size="small" @click="upload(retryFile)">重试</a-button></template></a-alert>
    <div v-if="rows.length" class="business-attachments__list">
      <article v-for="item in rows" :key="item.attachmentId"><span><strong>{{ item.originalName }}</strong><small>{{ size(item.sizeBytes) }} · {{ productDateTime(item.createdAt) }}</small></span><a-tag :color="item.scanStatus === 'CLEAN' ? 'green' : 'red'">{{ fileScanLabel(item.scanStatus) }}</a-tag><div><a-button v-if="item.previewable" type="text" size="small" @click="preview(item)"><EyeOutlined />预览</a-button><a-button v-if="item.downloadable" type="text" size="small" @click="download(item)"><DownloadOutlined />下载</a-button><a-button v-if="writable && item.removable" type="text" danger size="small" @click="remove(item)"><DeleteOutlined />移除</a-button></div></article>
    </div>
    <a-empty v-else-if="!loading" :image="false" description="暂无附件" />
  </section>
</template>
