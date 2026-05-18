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
import { ref, watch } from 'vue'
import { buildApiUrl, getToken } from '../../api/http.js'

const props = defineProps({
  modelValue: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue'])

const fileInput = ref(null)
const previewUrl = ref('')

watch(
  () => props.modelValue,
  (id) => {
    if (!id) {
      if (!previewUrl.value.startsWith('blob:')) previewUrl.value = ''
      return
    }
    if (!previewUrl.value.startsWith('blob:')) {
      previewUrl.value = buildApiUrl(`/v1/system/uploads/${id}/view`)
    }
  },
  { immediate: true }
)

function clear() {
  previewUrl.value = ''
  emit('update:modelValue', null)
}

async function onPick(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const url = buildApiUrl('/v1/system/uploads')
  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const form = new FormData()
  form.append('file', file)
  try {
    const res = await fetch(url, { method: 'POST', headers, body: form })
    const data = await res.json()
    if (!data || data.code !== 0) throw new Error(data?.message || '上传失败')
    const id = data.data?.fileId
    if (!id) throw new Error('无 fileId')
    previewUrl.value = URL.createObjectURL(file)
    emit('update:modelValue', id)
  } catch (err) {
    alert(err?.message || String(err))
  } finally {
    if (fileInput.value) fileInput.value.value = ''
  }
}
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
