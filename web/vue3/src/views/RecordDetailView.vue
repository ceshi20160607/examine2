<template>
  <AdminLayout>
    <h2>记录详情 #{{ recordId }}</h2>
    <div class="toolbar">
      <router-link
        class="btn"
        :to="{ path: '/records/form', query: { appId, modelId, recordId } }"
      >编辑</router-link>
      <button type="button" class="secondary" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <pre v-if="recordText" class="pre">{{ recordText }}</pre>

    <h3>变更历史</h3>
    <table v-if="history.length" class="table">
      <thead><tr><th>ID</th><th>时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="h in history" :key="h.id">
          <td>{{ h.id }}</td>
          <td>{{ h.createdAt || h.createTime }}</td>
          <td>{{ h.actionType || h.opType }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无历史</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { getRecord, listRecordHistory } from '../api/records'

const route = useRoute()
const appId = computed(() => route.query.appId)
const modelId = computed(() => route.query.modelId)
const recordId = computed(() => Number(route.query.recordId) || 0)
const recordText = ref('')
const history = ref([])
const error = ref('')

async function load() {
  if (!recordId.value) return
  error.value = ''
  try {
    const [rr, hr] = await Promise.all([
      getRecord(recordId.value),
      listRecordHistory(recordId.value)
    ])
    recordText.value = JSON.stringify(rr.data || null, null, 2)
    history.value = hr.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

watch(() => route.query, load, { deep: true })
onMounted(load)
</script>

<style src="./admin-shared.css"></style>
<style scoped>
.pre { background: #fff; border: 1px solid #e5e7eb; padding: 0.75rem; border-radius: 6px; overflow: auto; font-size: 0.85rem; }
h3 { margin-top: 1.25rem; }
</style>
