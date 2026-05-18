<template>
  <AdminLayout>
    <h2>平台消息 / 待办</h2>
    <div class="toolbar">
      <button type="button" @click="load">刷新</button>
      <label class="check-label">
        <input v-model="ccOnlyUnread" type="checkbox" @change="load" />
        抄送仅未读
      </label>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <h3>消息</h3>
    <table v-if="messages.length" class="table">
      <thead><tr><th>ID</th><th>标题</th><th>时间</th></tr></thead>
      <tbody>
        <tr v-for="m in messages" :key="m.id">
          <td>{{ m.id }}</td>
          <td>{{ m.title || m.content }}</td>
          <td>{{ m.createdAt || m.createTime }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无消息</p>

    <h3>待办</h3>
    <table v-if="todos.length" class="table">
      <thead><tr><th>ID</th><th>标题</th><th>系统</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="t in todos" :key="t.id">
          <td>{{ t.id }}</td>
          <td>{{ t.title || t.nodeName }}</td>
          <td>{{ t.systemId }}</td>
          <td>
            <router-link :to="{ path: '/flow/task', query: { taskId: t.id, instanceId: t.instanceId } }">办理</router-link>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无待办</p>

    <h3>抄送</h3>
    <table v-if="cc.length" class="table">
      <thead><tr><th>ID</th><th>标题</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="c in cc" :key="c.id">
          <td>{{ c.id }}</td>
          <td>{{ c.title || c.nodeName }}</td>
          <td>{{ c.readFlag === 1 ? '已读' : '未读' }}</td>
          <td>
            <button
              v-if="c.readFlag !== 1"
              type="button"
              class="link"
              :disabled="readingId === c.id"
              @click="markCcRead(c.id)"
            >
              {{ readingId === c.id ? '…' : '标记已读' }}
            </button>
            <router-link :to="{ path: '/flow/task', query: { taskId: c.id, instanceId: c.instanceId } }">查看</router-link>
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
import { listPlatformCc, listPlatformMessages, listPlatformTodos, readPlatformCc } from '../api/platform'

const messages = ref([])
const todos = ref([])
const cc = ref([])
const error = ref('')
const ccOnlyUnread = ref(false)
const readingId = ref(0)

async function load() {
  error.value = ''
  try {
    const onlyUnread = ccOnlyUnread.value ? 1 : undefined
    const [mr, tr, cr] = await Promise.all([
      listPlatformMessages(),
      listPlatformTodos(),
      listPlatformCc(50, onlyUnread)
    ])
    messages.value = mr.data || []
    todos.value = tr.data || []
    cc.value = cr.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function markCcRead(taskId) {
  readingId.value = taskId
  error.value = ''
  try {
    await readPlatformCc(taskId)
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    readingId.value = 0
  }
}

onMounted(load)
</script>

<style scoped>
h3 { margin-top: 1.25rem; }
.check-label { font-size: 0.9rem; display: flex; align-items: center; gap: 0.35rem; }
.link { background: none; border: none; color: #1677ff; cursor: pointer; padding: 0; margin-right: 0.5rem; }
</style>
<style src="./admin-shared.css"></style>
