<template>
  <AdminLayout>
    <h2>导出模板 · app {{ appId }}</h2>
    <div class="toolbar">
      <input v-model="modelIdText" placeholder="modelId" />
      <button type="button" @click="loadTpls">加载模板</button>
      <button type="button" @click="addTpl">新建模板</button>
      <router-link class="btn secondary" to="/export-jobs">导出任务</router-link>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="tpls.length" class="table">
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>类型</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="t in tpls" :key="t.id">
          <td>{{ t.id }}</td>
          <td>{{ t.tplCode }}</td>
          <td>{{ t.tplName }}</td>
          <td>{{ t.fileType }}</td>
          <td>
            <button type="button" class="link" @click="loadFields(t)">字段</button>
            ·
            <button type="button" class="link" :disabled="exportingId === t.id" @click="startExport(t)">
              {{ exportingId === t.id ? '提交中…' : '导出' }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <template v-if="activeTpl">
      <h3>模板字段 · #{{ activeTpl.id }}</h3>
      <table v-if="fields.length" class="table">
        <thead><tr><th>ID</th><th>fieldId</th><th>标题</th><th>排序</th></tr></thead>
        <tbody>
          <tr v-for="f in fields" :key="f.id">
            <td>{{ f.id }}</td>
            <td>{{ f.fieldId }}</td>
            <td>{{ f.colTitle }}</td>
            <td>{{ f.sortNo }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">暂无字段</p>
    </template>
  </AdminLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { createExportJob, listExportTplFields, listExportTplsByModel, upsertExportTpl } from '../api/module'

const route = useRoute()
const router = useRouter()
const appId = computed(() => String(route.params.appId || ''))
const modelIdText = ref(String(route.query.modelId || ''))
const tpls = ref([])
const fields = ref([])
const activeTpl = ref(null)
const error = ref('')
const exportingId = ref(0)

async function loadTpls() {
  const modelId = modelIdText.value.trim()
  if (!modelId) {
    error.value = '请输入 modelId'
    return
  }
  error.value = ''
  try {
    const r = await listExportTplsByModel(modelId)
    tpls.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addTpl() {
  const modelId = modelIdText.value.trim()
  const tplCode = prompt('tplCode')
  const tplName = prompt('tplName')
  if (!modelId || !tplCode || !tplName) return
  error.value = ''
  try {
    await upsertExportTpl({ appId: appId.value, modelId, tplCode, tplName, fileType: 'csv' })
    loadTpls()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function loadFields(t) {
  activeTpl.value = t
  error.value = ''
  try {
    const r = await listExportTplFields(t.id)
    fields.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function startExport(t) {
  const modelId = modelIdText.value.trim()
  if (!appId.value || !modelId) {
    error.value = '需要 appId 与 modelId'
    return
  }
  exportingId.value = t.id
  error.value = ''
  try {
    const r = await createExportJob(t.id, {
      appId: appId.value,
      modelId,
      page: 1,
      limit: 500
    })
    const jobId = r.data?.jobId
    if (jobId) {
      router.push(`/export-jobs?highlight=${jobId}`)
    } else {
      router.push('/export-jobs')
    }
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    exportingId.value = 0
  }
}
</script>

<style scoped>
input { padding: 0.35rem 0.5rem; border: 1px solid #d1d5db; border-radius: 6px; width: 120px; }
h3 { margin-top: 1.25rem; }
.link { background: none; border: none; color: #1677ff; cursor: pointer; padding: 0; }
.btn.secondary { text-decoration: none; padding: 0.35rem 0.65rem; border-radius: 6px; border: 1px solid #d1d5db; color: #333; }
</style>
<style src="./admin-shared.css"></style>
