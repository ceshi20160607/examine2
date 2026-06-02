<template>
  <AdminLayout>
    <h2>导出模板 · app {{ appId }}</h2>
    <div class="toolbar">
      <select v-if="models.length" v-model="modelIdText" @change="loadTpls">
        <option value="">选择模型</option>
        <option v-for="m in models" :key="m.id" :value="String(m.id)">
          {{ m.modelName || m.modelCode }} (#{{ m.id }})
        </option>
      </select>
      <input v-else v-model="modelIdText" placeholder="modelId" />
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
          <td class="actions">
            <button type="button" class="link" @click="loadFields(t)">字段</button>
            <button type="button" class="link" @click="editTpl(t)">编辑</button>
            <button type="button" class="link" :disabled="exportingId === t.id" @click="startExport(t)">
              {{ exportingId === t.id ? '提交中…' : '导出' }}
            </button>
            <button type="button" class="link danger-text" @click="removeTpl(t)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>

    <template v-if="activeTpl">
      <h3>模板字段 · #{{ activeTpl.id }}</h3>
      <div class="toolbar">
        <button type="button" @click="addField">添加字段</button>
        <button type="button" class="secondary" @click="loadFields(activeTpl)">刷新字段</button>
      </div>
      <table v-if="fields.length" class="table">
        <thead><tr><th>ID</th><th>字段</th><th>标题</th><th>排序</th><th>格式</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="f in fields" :key="f.id">
            <td>{{ f.id }}</td>
            <td>{{ modelFieldLabel(f.fieldId) }}</td>
            <td>{{ f.colTitle }}</td>
            <td>{{ f.sortNo }}</td>
            <td>{{ formatLabel(f.formatJson) }}</td>
            <td class="actions">
              <button type="button" class="link" @click="editField(f)">编辑</button>
              <button type="button" class="link danger-text" @click="removeField(f)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">暂无字段</p>
    </template>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { listFieldsByModel, listModelsByApp } from '../api/meta.js'
import {
  createExportJob,
  deleteExportTpl,
  deleteExportTplField,
  listExportTplFields,
  listExportTplsByModel,
  upsertExportTpl,
  upsertExportTplField
} from '../api/module'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'
import { createModulePermState, MODULE_PERMS } from '../utils/modulePerms.js'

const route = useRoute()
const router = useRouter()
const appId = computed(() => String(route.params.appId || ''))
const modelIdText = ref(String(route.query.modelId || ''))
const models = ref([])
const modelFields = ref([])
const tpls = ref([])
const fields = ref([])
const activeTpl = ref(null)
const error = ref('')
const exportingId = ref(0)
const { loadModulePerms, hasModulePerm } = createModulePermState()
const canManageExports = computed(() => hasModulePerm(MODULE_PERMS.exports))
const canViewExportJobs = computed(() => hasModulePerm(MODULE_PERMS.exportJobs))

async function loadModels() {
  try {
    const r = await listModelsByApp(appId.value)
    models.value = r.data || []
    if (!modelIdText.value && models.value[0]?.id) {
      modelIdText.value = String(models.value[0].id)
    }
  } catch {
    models.value = []
  }
}

async function loadModelFields() {
  const modelId = modelIdText.value.trim()
  if (!modelId) {
    modelFields.value = []
    return
  }
  try {
    const r = await listFieldsByModel(modelId)
    modelFields.value = r.data || []
  } catch {
    modelFields.value = []
  }
}

function modelFieldLabel(fieldId) {
  const id = String(fieldId ?? '')
  const f = modelFields.value.find((x) => String(x.id) === id)
  return f ? `${f.fieldName || f.fieldCode} (${f.fieldCode})` : `#${id}`
}

function fieldOptionMessage() {
  if (!modelFields.value.length) return '请输入字段 ID'
  return modelFields.value
    .slice(0, 80)
    .map((f) => `${f.id} - ${f.fieldName || f.fieldCode} (${f.fieldCode})`)
    .join('\n')
}

function prettyFormatJson(value) {
  if (!value) return ''
  if (typeof value !== 'string') return JSON.stringify(value, null, 2)
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return String(value)
  }
}

function normalizeExportFormatJson(value) {
  const raw = String(value || '').trim()
  if (!raw) return null
  const parsed = JSON.parse(raw)
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error('格式配置必须是 JSON 对象')
  }
  return JSON.stringify(parsed)
}

function formatLabel(value) {
  if (!value) return '默认'
  try {
    const obj = typeof value === 'string' ? JSON.parse(value) : value
    const parts = []
    if (obj.emptyText) parts.push(`空值=${obj.emptyText}`)
    if (obj.trim) parts.push('去空格')
    if (obj.dateOnly) parts.push('日期')
    if (obj.numberScale !== undefined && obj.numberScale !== null) parts.push(`小数${obj.numberScale}位`)
    if (obj.prefix || obj.suffix) parts.push('前后缀')
    if (obj.mapping || obj.mappings) parts.push('映射')
    return parts.length ? parts.join(' / ') : '自定义'
  } catch {
    return '自定义'
  }
}

async function loadTpls() {
  if (!canManageExports.value) {
    error.value = '当前账号没有导出模板权限'
    return
  }
  const modelId = modelIdText.value.trim()
  if (!modelId) {
    error.value = '请输入 modelId'
    return
  }
  error.value = ''
  try {
    await loadModelFields()
    const r = await listExportTplsByModel(modelId)
    tpls.value = r.data || []
    activeTpl.value = null
    fields.value = []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addTpl() {
  await saveTpl()
}

async function editTpl(t) {
  await saveTpl(t)
}

async function saveTpl(existing = null) {
  if (!canManageExports.value) {
    error.value = '当前账号没有导出模板权限'
    return
  }
  const modelId = modelIdText.value.trim()
  const tplCode = await promptText('导出模板编码', { defaultValue: existing?.tplCode || '' })
  const tplName = await promptText('导出模板名称', { defaultValue: existing?.tplName || '' })
  const fileType = await promptText('文件类型', { defaultValue: existing?.fileType || 'csv', message: '支持 csv / xlsx' })
  if (!modelId || !tplCode || !tplName) return
  error.value = ''
  try {
    await upsertExportTpl({
      id: existing?.id ?? null,
      appId: appId.value,
      modelId,
      tplCode,
      tplName,
      fileType: (fileType || 'csv').trim().toLowerCase(),
      status: existing?.status ?? 1
    })
    notify.success('导出模板已保存')
    await loadTpls()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function removeTpl(t) {
  if (!canManageExports.value) {
    error.value = '当前账号没有导出模板权限'
    return
  }
  if (!t?.id || !(await confirmDialog(`删除导出模板 ${t.tplCode || t.id}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteExportTpl([t.id])
    notify.success('导出模板已删除')
    await loadTpls()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function loadFields(t) {
  if (!canManageExports.value) {
    error.value = '当前账号没有导出模板权限'
    return
  }
  activeTpl.value = t
  error.value = ''
  try {
    if (!modelFields.value.length) await loadModelFields()
    const r = await listExportTplFields(t.id)
    fields.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function saveField(existing = null) {
  if (!canManageExports.value) {
    error.value = '当前账号没有导出模板权限'
    return
  }
  if (!activeTpl.value) return
  if (!modelFields.value.length) await loadModelFields()
  const defaultField = modelFields.value.find((f) => !fields.value.some((x) => String(x.fieldId) === String(f.id)))
  const fieldId = String((await promptText('字段 ID', {
    defaultValue: existing?.fieldId ? String(existing.fieldId) : (defaultField?.id ? String(defaultField.id) : ''),
    message: fieldOptionMessage()
  })) || '').trim()
  const selected = modelFields.value.find((f) => String(f.id) === fieldId)
  const colTitle = await promptText('导出列标题', { defaultValue: existing?.colTitle || selected?.fieldName || selected?.fieldCode || '' })
  const sortNo = await promptText('排序号', { defaultValue: String(existing?.sortNo ?? fields.value.length + 1) })
  const formatText = await promptText('格式配置 JSON', {
    defaultValue: prettyFormatJson(existing?.formatJson),
    multiline: true,
    message: '可留空。示例：{"trim":true,"emptyText":"-","dateOnly":true,"numberScale":2}'
  })
  if (formatText === null) return
  if (!fieldId || !colTitle) return
  error.value = ''
  try {
    const formatJson = normalizeExportFormatJson(formatText)
    await upsertExportTplField({
      id: existing?.id ?? null,
      tplId: activeTpl.value.id,
      fieldId,
      colTitle,
      sortNo: Number.isNaN(Number(sortNo)) ? fields.value.length + 1 : Number(sortNo),
      formatJson
    })
    notify.success('导出字段已保存')
    await loadFields(activeTpl.value)
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addField() {
  await saveField()
}

async function editField(f) {
  await saveField(f)
}

async function removeField(f) {
  if (!canManageExports.value) {
    error.value = '当前账号没有导出模板权限'
    return
  }
  if (!f?.id || !(await confirmDialog(`删除导出字段 ${f.colTitle || f.id}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteExportTplField([f.id])
    notify.success('导出字段已删除')
    await loadFields(activeTpl.value)
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function startExport(t) {
  if (!canViewExportJobs.value) {
    error.value = '当前账号没有导出任务权限'
    return
  }
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

onMounted(async () => {
  await loadModulePerms()
  await loadModels()
  if (modelIdText.value) await loadTpls()
})
</script>

<style scoped>
input,
select {
  padding: 0.35rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  min-width: 180px;
}
h3 { margin-top: 1.25rem; }
.link { background: none; border: none; color: #1677ff; cursor: pointer; padding: 0; }
.danger-text { color: var(--color-danger); }
.actions {
  display: flex;
  gap: 0.45rem;
  flex-wrap: wrap;
}
.btn.secondary { text-decoration: none; padding: 0.35rem 0.65rem; border-radius: 6px; border: 1px solid #d1d5db; color: #333; }
</style>
