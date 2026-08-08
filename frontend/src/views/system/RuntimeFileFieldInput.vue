<script setup lang="ts">
import { Camera, Download, FileImage, Paperclip, Trash2, Upload } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

import { fileApi } from '@/services/file'
import { useSessionStore } from '@/stores/session'
import type { RuntimeFieldCapability } from '@/types/config'
import type { FileAsset } from '@/types/file'

const props = defineProps<{ field: RuntimeFieldCapability; modelValue?: unknown; inputId: string; systemId?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string[]]; blur: [] }>()
const session = useSessionStore()
const assets = ref<Record<string, FileAsset>>({})
const uploading = ref(false)
const error = ref('')
const ids = computed(() => Array.isArray(props.modelValue)
  ? [...new Set(props.modelValue.map((value) => String(value)).filter((value) => /^\d+$/.test(value)))] : [])
const activeSystemId = computed(() => props.systemId || session.context?.systemId || '')
const maximum = computed(() => Math.max(1, Math.min(Number(props.field.schema.maxFiles
  ?? (props.field.type === 'SIGNATURE' ? 1 : props.field.type === 'IMAGE' ? 20 : 10)), 100)))
const imageOnly = computed(() => ['IMAGE', 'SIGNATURE'].includes(props.field.type)
  || props.field.schema.imageOnly === true)
const accept = computed(() => {
  if (imageOnly.value) return 'image/*'
  const extensions = Array.isArray(props.field.schema.allowedExtensions)
    ? props.field.schema.allowedExtensions.map(String).filter(Boolean) : []
  return extensions.map((value) => value.startsWith('.') ? value : `.${value}`).join(',') || undefined
})

watch([ids, activeSystemId], async ([nextIds, systemId]) => {
  if (!systemId) return
  const missing = nextIds.filter((id) => !assets.value[id])
  if (!missing.length) return
  const loaded = await Promise.allSettled(missing.map((id) => fileApi.get(systemId, id)))
  loaded.forEach((result, index) => {
    if (result.status === 'fulfilled') assets.value[missing[index]!] = result.value
  })
}, { immediate: true })

async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length || !activeSystemId.value) return
  if (ids.value.length + files.length > maximum.value) {
    error.value = `最多允许 ${maximum.value} 个文件`
    return
  }
  uploading.value = true
  error.value = ''
  try {
    const next = [...ids.value]
    for (const file of files) {
      if (imageOnly.value && !file.type.startsWith('image/')) throw new Error('该字段只允许图片文件')
      const asset = await fileApi.upload(activeSystemId.value, file)
      assets.value[asset.id] = asset
      next.push(asset.id)
    }
    emit('update:modelValue', next)
    emit('blur')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '文件上传失败'
  } finally {
    uploading.value = false
  }
}

function remove(fileId: string) {
  emit('update:modelValue', ids.value.filter((id) => id !== fileId))
  emit('blur')
}

async function download(fileId: string) {
  if (!activeSystemId.value) return
  const result = await fileApi.download(activeSystemId.value, fileId)
  const url = URL.createObjectURL(result.blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = result.filename || assets.value[fileId]?.originalName || `file-${fileId}`
  anchor.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="runtime-file-field" :data-field-type="field.type">
    <div v-if="ids.length" class="bound-files">
      <article v-for="fileId in ids" :key="fileId">
        <img v-if="imageOnly && activeSystemId" :src="fileApi.thumbnailUrl(activeSystemId, fileId, 72, 72)" alt="">
        <FileImage v-else-if="imageOnly" :size="24" />
        <Paperclip v-else :size="20" />
        <div><strong>{{ assets[fileId]?.originalName || `文件 ${fileId}` }}</strong><small>{{ assets[fileId]?.mediaType || fileId }}</small></div>
        <a-tooltip title="下载"><a-button size="small" :aria-label="`下载${assets[fileId]?.originalName || fileId}`" @click="download(fileId)"><Download :size="14" /></a-button></a-tooltip>
        <a-tooltip title="从字段移除"><a-button size="small" danger :aria-label="`移除${assets[fileId]?.originalName || fileId}`" @click="remove(fileId)"><Trash2 :size="14" /></a-button></a-tooltip>
      </article>
    </div>
    <div v-if="ids.length < maximum" class="upload-actions">
      <label class="upload-button" :for="inputId" :aria-disabled="uploading || !activeSystemId">
        <Upload :size="15" />{{ uploading ? '上传中…' : field.type === 'SIGNATURE' ? '上传签名' : '上传文件' }}
      </label>
      <label v-if="imageOnly" class="upload-button camera-button" :for="`${inputId}-camera`" :aria-disabled="uploading || !activeSystemId">
        <Camera :size="15" />拍照
      </label>
    </div>
    <input :id="inputId" type="file" class="file-input" :accept="accept" :multiple="maximum > 1" :disabled="uploading || !activeSystemId" @change="upload">
    <input v-if="imageOnly" :id="`${inputId}-camera`" type="file" class="file-input" accept="image/*" capture="environment" :disabled="uploading || !activeSystemId" aria-label="拍照上传" @change="upload">
    <small class="field-hint">已绑定 {{ ids.length }}/{{ maximum }}；保存记录后形成字段级引用，删除文件会被安全阻止。</small>
    <span v-if="error" class="field-error" role="alert">{{ error }}</span>
  </div>
</template>

<style scoped>
.runtime-file-field{display:grid;gap:8px}.bound-files{display:grid;gap:6px}.bound-files article{display:grid;grid-template-columns:auto minmax(0,1fr) auto auto;align-items:center;gap:8px;padding:7px;border:1px solid #dde5e8;background:#fff}.bound-files img{width:48px;height:48px;object-fit:cover;border:1px solid #dde5e8}.bound-files article>div{display:grid;min-width:0}.bound-files strong,.bound-files small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.bound-files small,.field-hint{color:#67757e;font-size:12px}.upload-actions{display:flex;flex-wrap:wrap;gap:8px}.upload-button{display:inline-flex;justify-self:start;align-items:center;gap:6px;padding:5px 11px;border:1px solid #c8d3d9;background:#fff;cursor:pointer}.camera-button{border-color:#91caff;color:#0958d9}.upload-button[aria-disabled="true"]{cursor:not-allowed;opacity:.55}.file-input{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}.field-error{color:#b42318;font-size:12px}
</style>
