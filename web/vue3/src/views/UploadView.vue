<template>
  <AdminLayout>
    <h2>文件上传</h2>
    <div class="toolbar">
      <input type="file" @change="onPick" />
      <button type="button" :disabled="!file || uploading" @click="doUpload">上传</button>
      <input v-model="keyword" placeholder="文件名/类型" @keyup.enter="loadUploads" />
      <button type="button" class="secondary" @click="loadUploads">查询</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div v-if="lastFile" class="result">
      <strong>最近上传：{{ lastFile.originalName || lastFile.fileName || lastFile.id }}</strong>
      <div class="actions">
        <button type="button" class="link" @click="viewFile(lastFile)">预览</button>
        <button type="button" class="link" @click="downloadFile(lastFile)">下载</button>
      </div>
    </div>
    <pre v-if="resultText" class="pre">{{ resultText }}</pre>

    <h3>最近文件</h3>
    <table v-if="rows.length" class="table">
      <thead><tr><th>ID</th><th>文件名</th><th>类型</th><th>大小</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="r in rows" :key="r.id">
          <td>{{ r.id }}</td>
          <td>{{ r.originalName || r.fileName }}</td>
          <td>{{ r.contentType || '-' }}</td>
          <td>{{ formatSize(r.fileSize || r.size) }}</td>
          <td>{{ r.status === 2 ? '已删除' : '正常' }}</td>
          <td class="actions">
            <button type="button" class="link" @click="viewFile(r)">预览</button>
            <button type="button" class="link" @click="downloadFile(r)">下载</button>
            <button type="button" class="link danger-text" @click="remove(r)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else-if="!loading" class="muted">暂无文件</p>
    <p class="muted">共 {{ total }} 个文件</p>
  </AdminLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { deleteUpload, fetchUploadDownloadBlob, fetchUploadViewBlob, pageUploads, uploadFile } from '../api/upload'
import { saveBlob } from '../api/http.js'
import { confirmDialog } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const file = ref(null)
const uploading = ref(false)
const loading = ref(false)
const error = ref('')
const resultText = ref('')
const lastFile = ref(null)
const rows = ref([])
const total = ref(0)
const keyword = ref('')

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
    lastFile.value = r.data?.file || r.data || null
    notify.success('上传成功')
    await loadUploads()
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    uploading.value = false
  }
}

function formatSize(n) {
  const size = Number(n || 0)
  if (!size) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function fileNameOf(row) {
  return row?.originalName || row?.fileName || `file-${row?.id || 'download'}`
}

async function viewFile(row) {
  if (!row?.id) return
  error.value = ''
  const opened = window.open('', '_blank', 'noopener,noreferrer')
  try {
    const r = await fetchUploadViewBlob(row.id)
    const url = URL.createObjectURL(r.blob)
    if (opened) {
      opened.location.href = url
    } else {
      window.open(url, '_blank', 'noopener,noreferrer')
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (e) {
    if (opened) opened.close()
    error.value = e?.message || String(e)
  }
}

async function downloadFile(row) {
  if (!row?.id) return
  error.value = ''
  try {
    const r = await fetchUploadDownloadBlob(row.id)
    saveBlob(r.blob, r.filename || fileNameOf(row))
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function loadUploads() {
  loading.value = true
  error.value = ''
  try {
    const r = await pageUploads({ page: 1, size: 30, keyword: keyword.value })
    const data = r.data || {}
    rows.value = data.records || data.list || []
    total.value = Number(data.total || rows.value.length)
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    loading.value = false
  }
}

async function remove(row) {
  if (!row?.id || !(await confirmDialog(`删除文件 ${row.originalName || row.fileName || row.id}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteUpload(row.id)
    notify.success('文件已删除')
    await loadUploads()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(loadUploads)
</script>

<style scoped>
.toolbar input[type="text"],
.toolbar input:not([type]) {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--color-border-strong);
  border-radius: 7px;
}
.pre { margin-top: 1rem; background: #fff; border: 1px solid #e5e7eb; padding: 0.75rem; border-radius: 6px; font-size: 0.85rem; }
.result {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0.75rem;
  margin: 1rem 0;
}
.actions {
  display: flex;
  gap: 0.55rem;
  flex-wrap: wrap;
  align-items: center;
}
.link {
  background: none;
  border: none;
  color: #1677ff;
  cursor: pointer;
  padding: 0;
}
.danger-text {
  color: var(--color-danger);
}
</style>
