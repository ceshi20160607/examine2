<template>
  <AdminLayout>
    <h2>记录详情 #{{ recordId }}</h2>
    <div class="toolbar">
      <router-link
        class="btn"
        :to="{ path: '/records/form', query: { appId, modelId, pageId, recordId } }"
      >编辑</router-link>
      <button type="button" class="danger" @click="remove">删除</button>
      <router-link
        class="btn secondary"
        :to="{ path: '/records', query: { appId, modelId, pageId } }"
      >返回列表</router-link>
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
import { useRoute, useRouter } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { deleteRecord, getRecord, listRecordHistory } from '../api/records'
import { confirmDialog } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const router = useRouter()
const appId = computed(() => route.query.appId)
const modelId = computed(() => route.query.modelId)
const pageId = computed(() => route.query.pageId)
const recordId = computed(() => String(route.query.recordId || ''))
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

async function remove() {
  if (!recordId.value || !(await confirmDialog(`删除记录 #${recordId.value}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteRecord(recordId.value)
    notify.success('记录已删除')
    router.push({ path: '/records', query: { appId: appId.value, modelId: modelId.value, pageId: pageId.value } })
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

watch(() => route.query, load, { deep: true })
onMounted(load)
</script>

<style scoped>
.pre { background: #fff; border: 1px solid #e5e7eb; padding: 0.75rem; border-radius: 6px; overflow: auto; font-size: 0.85rem; }
h3 { margin-top: 1.25rem; }
</style>
