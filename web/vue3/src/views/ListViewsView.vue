<template>
  <AdminLayout>
    <h2>列表视图 · app {{ appId }}</h2>
    <div class="toolbar">
      <input v-model="modelIdText" placeholder="modelId" />
      <button type="button" @click="loadViews">加载视图</button>
      <button type="button" @click="addView">新建视图</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="views.length" class="table">
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>默认</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="v in views" :key="v.id">
          <td>{{ v.id }}</td>
          <td>{{ v.viewCode }}</td>
          <td>{{ v.viewName }}</td>
          <td>{{ v.defaultFlag }}</td>
          <td><button type="button" @click="editCols(v)">列配置</button></td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">输入 modelId 后加载</p>

    <template v-if="activeView">
      <h3>列 · view #{{ activeView.id }}</h3>
      <div class="toolbar">
        <button type="button" @click="addCol">添加列</button>
        <button type="button" class="secondary" @click="loadCols">刷新列</button>
      </div>
      <table v-if="cols.length" class="table">
        <thead><tr><th>ID</th><th>fieldId</th><th>标题</th><th>排序</th></tr></thead>
        <tbody>
          <tr v-for="c in cols" :key="c.id">
            <td>{{ c.id }}</td>
            <td>{{ c.fieldId }}</td>
            <td>{{ c.colTitle }}</td>
            <td>{{ c.sortNo }}</td>
          </tr>
        </tbody>
      </table>
    </template>
  </AdminLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { listViewCols, listViewsByModel, upsertListView, upsertViewCol } from '../api/module'

const route = useRoute()
const appId = computed(() => Number(route.params.appId))
const modelIdText = ref(String(route.query.modelId || ''))
const views = ref([])
const cols = ref([])
const activeView = ref(null)
const error = ref('')

async function loadViews() {
  const modelId = Number(modelIdText.value)
  if (!modelId) {
    error.value = '请输入 modelId'
    return
  }
  error.value = ''
  try {
    const r = await listViewsByModel(modelId)
    views.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addView() {
  const modelId = Number(modelIdText.value)
  const viewCode = prompt('viewCode')
  const viewName = prompt('viewName')
  if (!modelId || !viewCode || !viewName) return
  error.value = ''
  try {
    await upsertListView({ appId: appId.value, modelId, viewCode, viewName })
    loadViews()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function editCols(v) {
  activeView.value = v
  await loadCols()
}

async function loadCols() {
  if (!activeView.value) return
  error.value = ''
  try {
    const r = await listViewCols(activeView.value.id)
    cols.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addCol() {
  if (!activeView.value) return
  const fieldId = Number(prompt('fieldId'))
  const colTitle = prompt('colTitle')
  if (!fieldId || !colTitle) return
  error.value = ''
  try {
    await upsertViewCol({ viewId: activeView.value.id, fieldId, colTitle })
    loadCols()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}
</script>

<style src="./admin-shared.css"></style>
<style scoped>
input { padding: 0.35rem 0.5rem; border: 1px solid #d1d5db; border-radius: 6px; width: 120px; }
h3 { margin-top: 1.25rem; }
</style>
