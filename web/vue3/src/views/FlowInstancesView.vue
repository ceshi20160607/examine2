<template>
  <AdminLayout>
    <h2>流程实例</h2>
    <div class="toolbar">
      <select v-model="viewMode" @change="load">
        <option value="instances">实例</option>
        <option value="tasks">任务</option>
      </select>
      <label v-if="viewMode === 'instances'"><input v-model="mineOnly" type="checkbox" /> 仅我的</label>
      <select v-model="statusFilter" @change="load">
        <option value="">全部状态</option>
        <option v-for="o in visibleStatusOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
      <input v-model="keyword" placeholder="关键字" @keyup.enter="load" />
      <button type="button" @click="load">查询</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="viewMode === 'instances' && rows.length" class="table">
      <thead><tr><th>ID</th><th>标题</th><th>状态</th><th>业务</th><th>创建时间</th></tr></thead>
      <tbody>
        <tr v-for="i in rows" :key="i.id">
          <td>
            <router-link :to="`/flow/instances/${i.id}`">{{ i.id }}</router-link>
          </td>
          <td>{{ i.title }}</td>
          <td>{{ statusLabel(i.status) }}</td>
          <td>{{ i.bizType || '-' }} / {{ i.bizId || '-' }}</td>
          <td>{{ i.createdAt || i.createTime }}</td>
        </tr>
      </tbody>
    </table>
    <table v-else-if="viewMode === 'tasks' && taskRows.length" class="table">
      <thead><tr><th>ID</th><th>实例</th><th>节点</th><th>类型</th><th>处理人</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="t in taskRows" :key="t.id">
          <td>{{ t.id }}</td>
          <td>
            <router-link v-if="t.recordId" :to="`/flow/instances/${t.recordId}`">{{ t.recordId }}</router-link>
            <span v-else>-</span>
          </td>
          <td>{{ t.nodeName || t.nodeKey || '-' }}</td>
          <td>{{ t.taskType || '-' }}</td>
          <td>{{ t.assigneePlatId || '-' }}</td>
          <td>{{ taskStatusLabel(t.status) }}</td>
          <td>{{ t.createdAt || t.createTime }}</td>
          <td>
            <router-link v-if="t.recordId" :to="taskLink(t)">处理</router-link>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">{{ viewMode === 'tasks' ? '暂无任务' : '暂无实例' }}</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { pageInstances, pageMyInstances, pageTasks } from '../api/flow'
import { idToString } from '../utils/id.js'

const viewMode = ref('instances')
const mineOnly = ref(false)
const statusFilter = ref('')
const keyword = ref('')
const rows = ref([])
const taskRows = ref([])
const error = ref('')

const STATUS_MAP = {
  1: '运行中',
  2: '已结束',
  3: '已撤回',
  4: '已终止'
}

const TASK_STATUS_MAP = {
  1: '待处理',
  2: '已通过',
  3: '已拒绝',
  4: '已转办',
  5: '已取消/跳过'
}

const statusOptions = [
  { value: '1', label: '运行中/待处理' },
  { value: '2', label: '已结束/已通过' },
  { value: '3', label: '已撤回/已拒绝' },
  { value: '4', label: '已终止/已转办' }
]

const visibleStatusOptions = computed(() => {
  if (viewMode.value === 'instances' && mineOnly.value) {
    return [{ value: '1', label: '仅运行中' }]
  }
  return statusOptions
})

watch([mineOnly, viewMode], () => {
  if (viewMode.value === 'instances' && mineOnly.value && statusFilter.value !== '1') {
    statusFilter.value = ''
  }
})

function statusLabel(status) {
  return STATUS_MAP[Number(status)] || status || '-'
}

function taskStatusLabel(status) {
  return TASK_STATUS_MAP[Number(status)] || status || '-'
}

function taskLink(task) {
  return {
    path: '/flow/task',
    query: {
      instanceId: idToString(task.recordId),
      taskId: idToString(task.id)
    }
  }
}

async function load() {
  error.value = ''
  try {
    if (viewMode.value === 'tasks') {
      const r = await pageTasks(1, 30, {
        keyword: keyword.value,
        status: statusFilter.value
      })
      taskRows.value = r.data?.records || r.data?.list || r.data || []
      return
    }
    const r = mineOnly.value
      ? await pageMyInstances(1, 30, keyword.value, { onlyRunning: statusFilter.value === '1' ? 1 : undefined })
      : await pageInstances(1, 30, keyword.value, { status: statusFilter.value })
    rows.value = r.data?.list || r.data?.records || r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style scoped>
input[type="text"], input:not([type]) { padding: 0.35rem 0.5rem; border: 1px solid #d1d5db; border-radius: 6px; }
</style>
