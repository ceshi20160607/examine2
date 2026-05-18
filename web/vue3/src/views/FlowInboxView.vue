<template>
  <AdminLayout>
    <h2>流程待办箱</h2>
    <div class="toolbar">
      <button type="button" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <h3>待办</h3>
    <table v-if="pending.length" class="table">
      <thead><tr><th>任务</th><th>实例</th><th>标题</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="t in pending" :key="t.taskId || t.id">
          <td>{{ t.taskId || t.id }}</td>
          <td>{{ t.instanceId }}</td>
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
      <thead><tr><th>任务</th><th>实例</th><th>标题</th></tr></thead>
      <tbody>
        <tr v-for="t in cc" :key="t.taskId || t.id">
          <td>{{ t.taskId || t.id }}</td>
          <td>{{ t.instanceId }}</td>
          <td>{{ t.title || t.instanceTitle }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无抄送</p>
  </AdminLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { inboxCc, inboxPending } from '../api/flow'

const pending = ref([])
const cc = ref([])
const error = ref('')

function taskLink(t) {
  return {
    path: '/flow/task',
    query: { instanceId: t.instanceId, taskId: t.taskId || t.id }
  }
}

async function load() {
  error.value = ''
  try {
    const [pr, cr] = await Promise.all([inboxPending(), inboxCc()])
    pending.value = pr.data || []
    cc.value = cr.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style src="./admin-shared.css"></style>
<style scoped>
h3 { margin-top: 1.25rem; }
</style>
