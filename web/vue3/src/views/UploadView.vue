<template>
  <AdminLayout>
    <h2>文件上传</h2>
    <div class="toolbar">
      <input type="file" @change="onPick" />
      <button type="button" :disabled="!file || uploading" @click="doUpload">上传</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <pre v-if="resultText" class="pre">{{ resultText }}</pre>
  </AdminLayout>
</template>

<script setup>
import { ref } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { uploadFile } from '../api/upload'

const file = ref(null)
const uploading = ref(false)
const error = ref('')
const resultText = ref('')

function onPick(e) {
  file.value = e.target.files?.[0] || null
}

async function doUpload() {
  if (!file.value) return
  uploading.value = true
  error.value = ''
  resultText.value = ''
  try {
    const r = await uploadFile(file.value)
    resultText.value = JSON.stringify(r.data || r, null, 2)
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    uploading.value = false
  }
}
</script>

<style src="./admin-shared.css"></style>
<style scoped>
.pre { margin-top: 1rem; background: #fff; border: 1px solid #e5e7eb; padding: 0.75rem; border-radius: 6px; font-size: 0.85rem; }
</style>
