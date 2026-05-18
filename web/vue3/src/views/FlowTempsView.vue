<template>
  <AdminLayout>
    <h2>流程模板</h2>
    <div class="toolbar">
      <button type="button" @click="load">刷新</button>
      <button type="button" @click="add">新建模板</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>状态</th></tr></thead>
      <tbody>
        <tr v-for="t in rows" :key="t.id">
          <td>{{ t.id }}</td>
          <td>{{ t.tempCode }}</td>
          <td>{{ t.tempName }}</td>
          <td>
            {{ t.status }}
            · <router-link :to="`/flow/temps/${t.id}`">版本</router-link>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无模板</p>
  </AdminLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { pageTemps, upsertTemp } from '../api/flow'

const rows = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const r = await pageTemps(1, 50)
    rows.value = r.data?.list || r.data?.records || r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function add() {
  const tempCode = prompt('tempCode')
  const tempName = prompt('tempName')
  if (!tempCode || !tempName) return
  error.value = ''
  try {
    await upsertTemp({ tempCode, tempName, status: 1 })
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style src="./admin-shared.css"></style>
