<template>
  <AdminLayout>
    <h2>流程待办箱</h2>
    <div class="toolbar">
      <button type="button" @click="load">刷新</button>
      <label class="check-label">
        <input v-model="ccOnlyUnread" type="checkbox" @change="load" />
        抄送仅未读
      </label>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <h3>待办</h3>
    <table v-if="pending.length" class="table">
      <thead><tr><th>任务</th><th>实例</th><th>标题</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="t in pending" :key="t.taskId || t.id">
          <td>{{ t.taskId || t.id }}</td>
          <td>{{ taskInstanceId(t) }}</td>
          <td>{{ t.title || t.instanceTitle }}</td>
          <td>
            <router-link :to="taskLink(t)">处理</router-link>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无待办</p>

    <h3>抄送</h3>
    <table v-if="cc.length" class="table">
      <thead><tr><th>任务</th><th>实例</th><th>标题</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="t in cc" :key="t.taskId || t.id">
          <td>{{ t.taskId || t.id }}</td>
          <td>{{ taskInstanceId(t) }}</td>
          <td>{{ t.title || t.instanceTitle }}</td>
          <td>{{ t.readFlag === 1 ? '已读' : '未读' }}</td>
          <td>
            <button
              v-if="t.readFlag !== 1"
              type="button"
              class="link"
              :disabled="sameId(readingId, t.taskId || t.id)"
              @click="markCcRead(t)"
            >
              {{ sameId(readingId, t.taskId || t.id) ? '标记中...' : '标记已读' }}
            </button>
            <router-link :to="instanceLink(t)">实例详情</router-link>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无抄送</p>
  </AdminLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { inboxCc, inboxPending, readInboxCc } from '../api/flow'
import { idToString, sameId } from '../utils/id.js'

const pending = ref([])
const cc = ref([])
const error = ref('')
const ccOnlyUnread = ref(false)
const readingId = ref('')

function taskInstanceId(t) {
  return idToString(t?.instanceId || t?.recordId)
}

function taskLink(t) {
  return {
    path: '/flow/task',
    query: { instanceId: taskInstanceId(t), taskId: idToString(t.taskId || t.id) }
  }
}

function instanceLink(t) {
  return `/flow/instances/${encodeURIComponent(taskInstanceId(t))}`
}

async function markCcRead(t) {
  const taskId = idToString(t?.taskId || t?.id)
  if (!taskId) return
  readingId.value = taskId
  error.value = ''
  try {
    await readInboxCc(taskId)
    t.readFlag = 1
    if (ccOnlyUnread.value) {
      await load()
    }
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    readingId.value = ''
  }
}

async function load() {
  error.value = ''
  try {
    const onlyUnread = ccOnlyUnread.value ? 1 : undefined
    const [pr, cr] = await Promise.all([inboxPending(), inboxCc(50, onlyUnread)])
    pending.value = pr.data || []
    cc.value = cr.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style scoped>
h3 { margin-top: 1.25rem; }
.check-label { font-size: 0.9rem; display: flex; align-items: center; gap: 0.35rem; }
.link { background: none; border: none; color: #1677ff; cursor: pointer; padding: 0; margin-right: 0.5rem; }
</style>
