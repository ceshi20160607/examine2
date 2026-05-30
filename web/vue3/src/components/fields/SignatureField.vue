<template>
  <div class="signature-field">
    <img v-if="previewUrl" :src="previewUrl" alt="签名" class="signature-field__img" />
    <p v-else class="muted">未签名</p>
    <div class="signature-field__actions">
      <input ref="fileInput" type="file" accept="image/*" class="hidden" @change="onPick" />
      <button type="button" class="secondary" @click="fileInput?.click()">选择签名图</button>
      <button type="button" class="secondary" @click="clear">清除</button>
    </div>
    <p v-if="modelValue" class="muted">fileId: {{ modelValue }}</p>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { fetchUploadViewBlob, uploadFile } from '../../api/upload.js'
import { notify } from '../../utils/notify.js'

const props = defineProps({
  modelValue: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue'])

const fileInput = ref(null)
const previewUrl = ref('')
let previewObjectUrl = ''
let loadSeq = 0

watch(
  () => props.modelValue,
  async (id) => {
    const seq = ++loadSeq
    if (!id) {
      revokePreviewObjectUrl()
      previewUrl.value = ''
      return
    }
    try {
      const r = await fetchUploadViewBlob(id)
      if (seq !== loadSeq) return
      const url = URL.createObjectURL(r.blob)
      revokePreviewObjectUrl()
      previewObjectUrl = url
      previewUrl.value = url
    } catch (err) {
      if (seq === loadSeq) {
        revokePreviewObjectUrl()
        previewUrl.value = ''
      }
    }
  },
  { immediate: true }
)

function clear() {
  revokePreviewObjectUrl()
  previewUrl.value = ''
  emit('update:modelValue', null)
}

function revokePreviewObjectUrl() {
  if (previewObjectUrl) {
    URL.revokeObjectURL(previewObjectUrl)
    previewObjectUrl = ''
  }
}

async function onPick(e) {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const data = await uploadFile(file)
    const id = data.data?.fileId
    if (!id) throw new Error('无 fileId')
    revokePreviewObjectUrl()
    previewObjectUrl = URL.createObjectURL(file)
    previewUrl.value = previewObjectUrl
    emit('update:modelValue', id)
  } catch (err) {
    notify.error(err?.message || String(err))
  } finally {
    if (fileInput.value) fileInput.value.value = ''
  }
}

onBeforeUnmount(revokePreviewObjectUrl)
</script>

<style scoped>
.signature-field__img {
  max-width: 100%;
  max-height: 160px;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  display: block;
}
.signature-field__actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
.hidden {
  display: none;
}
</style>
