<template>
  <AdminLayout>
    <h2>导出任务</h2>
    <div class="toolbar">
      <select v-model="statusFilter" class="filter-select" @change="load">
        <option value="">全部状态</option>
        <option value="0">待处理</option>
        <option value="1">处理中</option>
        <option value="2">已完成</option>
        <option value="3">失败</option>
      </select>
      <button type="button" @click="load">刷新</button>
      <label class="auto-refresh">
        <input v-model="autoPoll" type="checkbox" />
        自动刷新
      </label>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead>
        <tr>
          <th>ID</th>
          <th>状态</th>
          <th>模板</th>
          <th>模型</th>
          <th>创建时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="j in rows" :key="j.id">
          <td>{{ j.id }}</td>
          <td>
            <span :class="['status', `status--${j.status}`]">{{ statusLabel(j.status) }}</span>
          </td>
          <td>{{ j.tplId }}</td>
          <td>{{ j.modelId || '-' }}</td>
          <td>{{ formatTime(j.createTime) }}</td>
          <td>
            <button
              v-if="j.status === 2"
              type="button"
              class="link"
              :disabled="downloadingId === j.id"
              @click="downloadJob(j.id)"
            >
              {{ downloadingId === j.id ? '下载中…' : '下载' }}
            </button>
            <button
              v-else-if="j.status === 0 || j.status === 1"
              type="button"
              class="link secondary"
              @click="refreshOne(j.id)"
            >
              刷新状态
            </button>
            <span v-else-if="j.status === 3" class="err-msg" :title="j.errorMsg">{{ j.errorMsg || '失败' }}</span>
            <span v-else class="muted">-</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else-if="!loading" class="muted">暂无任务</p>
    <p class="muted">共 {{ total }} 条</p>
  </AdminLayout>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { getExportJobDetail, pageExportJobs } from '../api/module.js'
import { buildApiUrl, getToken } from '../api/http.js'

const rows = ref([])
const total = ref(0)
const error = ref('')
const loading = ref(false)
const statusFilter = ref('')
const autoPoll = ref(true)
const downloadingId = ref(0)
let pollTimer = null

const STATUS_MAP = {
  0: '待处理',
  1: '处理中',
  2: '已完成',
  3: '失败'
}

function statusLabel(s) {
  return STATUS_MAP[s] ?? String(s)
}

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const r = await pageExportJobs({
      page: 1,
      size: 50,
      status: statusFilter.value
    })
    const data = r.data || {}
    rows.value = data.records || data.list || []
    total.value = Number(data.total || rows.value.length)
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    loading.value = false
  }
}

async function refreshOne(jobId) {
  try {
    const r = await getExportJobDetail(jobId)
    const job = r.data?.job
    if (!job) return
    const idx = rows.value.findIndex((x) => x.id === jobId)
    if (idx >= 0) rows.value[idx] = job
    else await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function downloadJob(jobId) {
  downloadingId.value = jobId
  error.value = ''
  try {
    const r = await getExportJobDetail(jobId)
    const path = r.data?.downloadUrl
    if (!path) {
      error.value = '文件尚未生成'
      return
    }
    const res = await fetch(buildApiUrl(path), {
      headers: { Authorization: `Bearer ${getToken()}` }
    })
    if (!res.ok) throw new Error(`下载失败 HTTP ${res.status}`)
    const blob = await res.blob()
    const name = r.data?.file?.origName || r.data?.file?.fileName || `export-${jobId}.csv`
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = name
    a.click()
    URL.revokeObjectURL(a.href)
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    downloadingId.value = 0
  }
}

function setupPoll() {
  clearInterval(pollTimer)
  if (!autoPoll.value) return
  pollTimer = setInterval(() => {
    const pending = rows.value.some((j) => j.status === 0 || j.status === 1)
    if (pending) load()
  }, 3000)
}

watch(autoPoll, setupPoll)

onMounted(() => {
  load()
  setupPoll()
})
onUnmounted(() => clearInterval(pollTimer))
</script>

<style scoped>
.filter-select {
  padding: 0.35rem 0.5rem;
  border-radius: 6px;
  border: 1px solid #d1d5db;
}
.auto-refresh {
  font-size: 0.88rem;
  color: #555;
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
.status {
  display: inline-block;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
  font-size: 0.8rem;
}
.status--0 {
  background: #fef3c7;
  color: #92400e;
}
.status--1 {
  background: #dbeafe;
  color: #1e40af;
}
.status--2 {
  background: #d1fae5;
  color: #065f46;
}
.status--3 {
  background: #fee2e2;
  color: #991b1b;
}
.link {
  background: none;
  border: none;
  color: #1677ff;
  cursor: pointer;
  padding: 0;
}
.link.secondary {
  color: #64748b;
}
.err-msg {
  color: #c00;
  font-size: 0.8rem;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
}
</style>
<style src="./admin-shared.css"></style>
