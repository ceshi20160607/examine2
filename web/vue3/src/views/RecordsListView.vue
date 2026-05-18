<template>
  <AdminLayout>
    <h2>{{ title }}</h2>
    <p class="muted">modelId={{ modelId }} · pageId={{ pageId || '-' }}</p>
    <div class="toolbar">
      <input v-model="keyword" placeholder="关键字搜索" @keyup.enter="load" />
      <button type="button" @click="load">查询</button>
      <router-link
        class="btn"
        :to="{ path: '/records/form', query: { appId, modelId, pageId } }"
      >新建</router-link>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead>
        <tr>
          <th>ID</th>
          <th v-for="col in displayColumns" :key="col.code">{{ col.label }}</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ row.id }}</td>
          <td v-for="col in displayColumns" :key="col.code">{{ row.cells[col.code] ?? '-' }}</td>
          <td>
            <router-link
              :to="{ path: '/records/detail', query: { appId, modelId, recordId: row.id } }"
            >详情</router-link>
            ·
            <router-link
              :to="{ path: '/records/form', query: { appId, modelId, recordId: row.id } }"
            >编辑</router-link>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else-if="!loading" class="muted">暂无数据</p>
    <p class="muted">共 {{ total }} 条</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { listFieldsByModel } from '../api/meta.js'
import { getPageRuntime } from '../api/pages'
import { queryRecords } from '../api/records'

const route = useRoute()
const appId = ref(0)
const modelId = ref(0)
const pageId = ref(0)
const pageRuntime = ref(null)
const fieldLabels = ref({})
const rows = ref([])
const total = ref(0)
const keyword = ref('')
const loading = ref(false)
const error = ref('')

const title = computed(() => pageRuntime.value?.pageName || '记录列表')

const displayColumns = computed(() => {
  const codes =
    pageRuntime.value?.columnFieldCodes ||
    pageRuntime.value?.titleFieldCodes ||
    []
  if (!codes.length) return [{ code: '_summary', label: '摘要' }]
  return codes.map((code) => ({
    code,
    label: fieldLabels.value[code] || code
  }))
})

const includeFieldCodes = computed(() => {
  const codes = displayColumns.value.map((c) => c.code).filter((c) => c !== '_summary')
  return codes.slice(0, 30)
})

async function loadRuntime() {
  if (!pageId.value) return
  try {
    const r = await getPageRuntime(pageId.value)
    pageRuntime.value = r.data
  } catch {
    pageRuntime.value = null
  }
}

async function loadFieldLabels() {
  if (!modelId.value) return
  try {
    const r = await listFieldsByModel(modelId.value)
    const map = {}
    for (const f of r.data || []) {
      if (f?.fieldCode) map[f.fieldCode] = f.fieldName || f.fieldCode
    }
    fieldLabels.value = map
  } catch {
    fieldLabels.value = {}
  }
}

function formatVal(v) {
  if (v == null || v === '') return '-'
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

function rowFromItem(item, cols) {
  const id = item.id
  const data = item.data || {}
  const cells = {}
  if (cols.length === 1 && cols[0].code === '_summary') {
    const keys = Object.keys(data).slice(0, 3)
    cells._summary = keys.length
      ? keys.map((k) => `${k}=${formatVal(data[k])}`).join(' | ')
      : `#${id}`
  } else {
    for (const col of cols) {
      cells[col.code] = formatVal(data[col.code])
    }
  }
  return { id, cells }
}

async function load() {
  if (!modelId.value) return
  loading.value = true
  error.value = ''
  try {
    const filters = []
    const kw = keyword.value.trim()
    const searchField = pageRuntime.value?.searchFieldCode
    if (kw && searchField) {
      filters.push({ field: searchField, op: 'like', value: kw })
    }
    const r = await queryRecords({
      appId: appId.value,
      modelId: modelId.value,
      page: 1,
      limit: 30,
      filters,
      includeFieldCodes: includeFieldCodes.value
    })
    const list = r.data?.list || []
    total.value = Number(r.data?.total || 0)
    const cols = displayColumns.value
    rows.value = list.map((item) => rowFromItem(item, cols))
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    loading.value = false
  }
}

function syncFromRoute() {
  appId.value = Number(route.query.appId) || 0
  modelId.value = Number(route.query.modelId) || 0
  pageId.value = Number(route.query.pageId) || 0
}

watch(() => route.query, async () => {
  syncFromRoute()
  await loadRuntime()
  await loadFieldLabels()
  load()
})

onMounted(async () => {
  syncFromRoute()
  await loadRuntime()
  await loadFieldLabels()
  load()
})
</script>

<style src="./admin-shared.css"></style>
<style scoped>
.toolbar input {
  flex: 1;
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  min-width: 160px;
}
</style>
