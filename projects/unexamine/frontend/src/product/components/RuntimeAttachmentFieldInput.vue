<script setup lang="ts">
import { DeleteOutlined, EyeOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { api } from '../api'
import { authorizedBlob, uploadControlledFile } from '../file'
import type { ControlledFileView, FileStoragePolicyView, RuntimeField } from '../types'

const props = defineProps<{
  field: Pick<RuntimeField, 'fieldType' | 'configJson' | 'required'>
  modelValue?: number | number[] | null
  token: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: number | number[] | undefined] }>()

const uploading = ref(false)
const progress = ref(0)
const error = ref('')
const files = ref<ControlledFileView[]>([])
const policy = ref<FileStoragePolicyView>()
const thumbnails = new Map<number, string>()
const thumbnailVersion = ref(0)

const configuration = computed(() => {
  try { return JSON.parse(props.field.configJson || '{}') as { maxFiles?: number; maxCount?: number; maxSizeMb?: number; allowedTypes?: string[] } }
  catch { return {} }
})
const multiple = computed(() => props.field.fieldType === 'FILE_GROUP')
const maximum = computed(() => multiple.value ? Math.max(1, Math.min(50, configuration.value.maxFiles || configuration.value.maxCount || 10)) : 1)
const maximumSizeMb = computed(() => Math.max(1, Math.min(1024, configuration.value.maxSizeMb || 50)))
const ids = computed(() => {
  if (multiple.value) return Array.isArray(props.modelValue) ? props.modelValue.filter(value => Number.isInteger(value) && value > 0) : []
  return typeof props.modelValue === 'number' && props.modelValue > 0 ? [props.modelValue] : []
})
const accept = computed(() => props.field.fieldType === 'IMAGE' ? 'image/*' : configuration.value.allowedTypes?.join(',') || undefined)

function revokeThumbnails() {
  thumbnails.forEach(url => URL.revokeObjectURL(url))
  thumbnails.clear()
  thumbnailVersion.value += 1
}

async function load() {
  if (!policy.value && props.token) policy.value = await api<FileStoragePolicyView>('/api/files/policy', {}, props.token).catch(() => undefined)
  const requested = [...ids.value]
  revokeThumbnails()
  if (!requested.length || !props.token) { files.value = []; return }
  const loaded = await Promise.all(requested.map(id => api<ControlledFileView>(`/api/files/${id}`, {}, props.token)))
  if (requested.join(',') !== ids.value.join(',')) return
  files.value = loaded
  await Promise.all(loaded.filter(file => file.contentType.startsWith('image/') && file.previewStatus === 'AVAILABLE')
    .map(async file => {
      try { thumbnails.set(file.id, URL.createObjectURL(await authorizedBlob(`/api/files/${file.id}/preview`, props.token))) }
      catch { /* 缩略图失败不阻止表单继续保存，用户仍可下载原文件。 */ }
    }))
  thumbnailVersion.value += 1
}

async function choose(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = [...(input.files || [])]
  input.value = ''
  if (!selected.length || uploading.value) return
  if (ids.value.length + selected.length > maximum.value) {
    error.value = `该字段最多选择 ${maximum.value} 个文件`
    return
  }
  const tooLarge = selected.find(file => file.size > maximumSizeMb.value * 1024 * 1024)
  if (tooLarge) { error.value = `${tooLarge.name} 超过 ${maximumSizeMb.value} MB 限制`; return }
  uploading.value = true
  error.value = ''
  const added: number[] = []
  try {
    for (const file of selected) {
      const uploaded = await uploadControlledFile(file, props.token, value => { progress.value = value })
      if (uploaded.scanStatus !== 'CLEAN') throw new Error(`${file.name} 未通过安全扫描`)
      added.push(uploaded.id)
    }
    const next = [...ids.value, ...added]
    emit('update:modelValue', multiple.value ? next : next[next.length - 1])
    message.success(added.length > 1 ? `${added.length} 个文件已上传，保存表单后绑定记录` : '文件已上传，保存表单后绑定记录')
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '文件上传失败，请重试'
  } finally {
    uploading.value = false
    progress.value = 0
  }
}

async function remove(file: ControlledFileView) {
  const next = ids.value.filter(id => id !== file.id)
  emit('update:modelValue', multiple.value ? next : undefined)
  if (!file.references.length) await api(`/api/files/${file.id}`, { method: 'DELETE' }, props.token).catch(() => undefined)
}

async function preview(file: ControlledFileView) {
  try {
    const blob = await authorizedBlob(`/api/files/${file.id}/preview`, props.token)
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank', 'noopener,noreferrer')
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch { message.warning('在线预览失败，可在记录附件中下载查看') }
}

function thumbnail(fileId: number) {
  void thumbnailVersion.value
  return thumbnails.get(fileId)
}

watch(() => [props.token, ids.value.join(',')], load, { immediate: true })
onBeforeUnmount(revokeThumbnails)
</script>

<template>
  <div class="runtime-attachment-input">
    <div v-if="files.length" class="runtime-attachment-input__files">
      <article v-for="file in files" :key="file.id">
        <img v-if="thumbnail(file.id)" :src="thumbnail(file.id)" :alt="`${file.originalName} 缩略图`" />
        <span><strong>{{ file.originalName }}</strong><small>{{ (file.sizeBytes / 1024 / 1024).toFixed(2) }} MB · {{ file.scanStatus === 'CLEAN' ? '安全扫描通过' : file.scanStatus }}</small></span>
        <a-button v-if="file.previewStatus === 'AVAILABLE'" type="text" size="small" @click="preview(file)"><EyeOutlined />预览</a-button>
        <a-button type="text" danger size="small" @click="remove(file)"><DeleteOutlined />移除</a-button>
      </article>
    </div>
    <label v-if="ids.length < maximum" class="ant-btn ant-btn-default" :class="{ disabled: uploading }">
      <UploadOutlined />{{ uploading ? '正在上传' : (multiple ? '选择文件' : '选择文件') }}
      <input class="visually-hidden" type="file" :accept="accept" :multiple="multiple" :disabled="uploading" @change="choose" />
    </label>
    <a-progress v-if="uploading" :percent="progress" size="small" status="active" />
    <small class="runtime-attachment-input__hint">受控{{ multiple ? `多文件（最多 ${maximum} 个）` : '单文件' }}上传 · 单个不超过 {{ maximumSizeMb }} MB · {{ policy?.backendName || '托管存储' }}<template v-if="policy"> · 剩余 {{ (policy.availableBytes / 1024 / 1024 / 1024).toFixed(2) }} GB</template></small>
    <a-alert v-if="error" type="error" show-icon :message="error" />
  </div>
</template>

<style scoped>
.runtime-attachment-input { display:grid; gap:8px; }
.runtime-attachment-input__files { display:grid; gap:8px; }
.runtime-attachment-input__files article { display:flex; align-items:center; gap:8px; min-width:0; padding:8px; border:1px solid var(--app-border,#e8e8e8); border-radius:8px; }
.runtime-attachment-input__files img { width:48px; height:48px; object-fit:cover; border-radius:6px; }
.runtime-attachment-input__files span { display:grid; flex:1; min-width:0; }
.runtime-attachment-input__files strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.runtime-attachment-input__files small,.runtime-attachment-input__hint { color:var(--app-text-secondary,#666); }
</style>
