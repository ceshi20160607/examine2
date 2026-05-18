<template>
  <AdminLayout>
    <h2>模型 · app {{ appId }}</h2>
    <div class="toolbar">
      <button type="button" @click="add">新建模型</button>
      <button type="button" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="m in rows" :key="m.id">
          <td>{{ m.id }}</td>
          <td>{{ m.modelCode }}</td>
          <td>{{ m.modelName }}</td>
          <td>
            <router-link :to="`/apps/${appId}/models/${m.id}/fields`">字段</router-link>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无模型</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { listModelsByApp, upsertModel } from '../api/meta'

const route = useRoute()
const appId = computed(() => Number(route.params.appId))
const rows = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const r = await listModelsByApp(appId.value)
    rows.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function add() {
  const modelCode = prompt('modelCode')
  const modelName = prompt('modelName')
  if (!modelCode || !modelName) return
  await upsertModel({ appId: appId.value, modelCode, modelName })
  load()
}

onMounted(load)
</script>

<style scoped src="./admin-shared.css"></style>
