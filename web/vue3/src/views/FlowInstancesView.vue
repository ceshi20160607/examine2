<template>
  <AdminLayout>
    <h2>流程实例</h2>
    <div class="toolbar">
      <label><input v-model="mineOnly" type="checkbox" /> 仅我的</label>
      <input v-model="keyword" placeholder="关键字" @keyup.enter="load" />
      <button type="button" @click="load">查询</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead><tr><th>ID</th><th>标题</th><th>状态</th><th>创建时间</th></tr></thead>
      <tbody>
        <tr v-for="i in rows" :key="i.id">
          <td>
            <router-link :to="`/flow/instances/${i.id}`">{{ i.id }}</router-link>
          </td>
          <td>{{ i.title }}</td>
          <td>{{ i.status }}</td>
          <td>{{ i.createdAt || i.createTime }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无实例</p>
  </AdminLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { pageInstances, pageMyInstances } from '../api/flow'

const mineOnly = ref(false)
const keyword = ref('')
const rows = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const r = mineOnly.value
      ? await pageMyInstances(1, 30, keyword.value)
      : await pageInstances(1, 30, keyword.value)
    rows.value = r.data?.list || r.data?.records || r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style src="./admin-shared.css"></style>
<style scoped>
input[type="text"], input:not([type]) { padding: 0.35rem 0.5rem; border: 1px solid #d1d5db; border-radius: 6px; }
</style>
