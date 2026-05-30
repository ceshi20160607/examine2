<template>
  <AdminLayout>
    <h2>{{ title }}</h2>
    <p class="muted">modelId={{ modelId }} · pageId={{ pageId || '-' }}</p>
    <div class="toolbar">
      <input v-model="keyword" placeholder="关键字搜索" @keyup.enter="load" />
      <button type="button" @click="load">查询</button>
      <button v-if="activeFilterItems.length" type="button" @click="resetFilters">重置筛选</button>
      <router-link
        class="btn"
        :to="{ path: '/records/form', query: { appId, modelId, pageId } }"
      >新建</router-link>
    </div>
    <div v-if="filterTpls.length" class="filter-panel">
      <label>
        筛选模板
        <select v-model="activeFilterTplId" @change="loadActiveFilterFields">
          <option v-for="tpl in filterTpls" :key="tpl.id" :value="String(tpl.id)">
            {{ tpl.tplName || tpl.tplCode || tpl.id }}
          </option>
        </select>
      </label>
      <div v-if="activeFilterItems.length" class="filter-grid">
        <label v-for="item in activeFilterItems" :key="item.key">
          {{ item.label }}
          <span v-if="item.required" class="required">*</span>
          <input
            v-model="filterValues[item.key]"
            :placeholder="filterPlaceholder(item)"
            @keyup.enter="load"
          />
        </label>
      </div>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead>
        <tr>
          <th>ID</th>
          <th v-for="col in displayColumns" :key="col.code" :style="columnStyle(col)">{{ col.label }}</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ row.id }}</td>
          <td v-for="col in displayColumns" :key="col.code" :style="columnStyle(col)">{{ row.cells[col.code] ?? '-' }}</td>
          <td>
            <router-link
              :to="{ path: '/records/detail', query: { appId, modelId, pageId, recordId: row.id } }"
            >详情</router-link>
            ·
            <router-link
              :to="{ path: '/records/form', query: { appId, modelId, pageId, recordId: row.id } }"
            >编辑</router-link>
            ·
            <button type="button" class="link danger-text" @click="remove(row)">删除</button>
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
import { listFilterFields, listFilterTpls } from '../api/module.js'
import { getPageRuntime } from '../api/pages'
import { deleteRecord, queryRecords } from '../api/records'
import { confirmDialog } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const appId = ref('')
const modelId = ref('')
const pageId = ref('')
const pageRuntime = ref(null)
const fields = ref([])
const fieldLabels = ref({})
const filterTpls = ref([])
const activeFilterTplId = ref('')
const filterFields = ref([])
const filterValues = ref({})
const rows = ref([])
const total = ref(0)
const keyword = ref('')
const loading = ref(false)
const error = ref('')

const title = computed(() => pageRuntime.value?.pageName || '记录列表')

const displayColumns = computed(() => {
  const runtimeCols = Array.isArray(pageRuntime.value?.columns) ? pageRuntime.value.columns : []
  const visibleRuntimeCols = runtimeCols
    .filter((col) => col?.fieldCode && col.visibleFlag !== 0)
    .sort((a, b) => Number(a.sortNo ?? 0) - Number(b.sortNo ?? 0))
  if (visibleRuntimeCols.length) {
    return visibleRuntimeCols.map((col) => ({
      code: col.fieldCode,
      label: col.label || fieldLabels.value[col.fieldCode] || col.fieldCode,
      width: col.width ?? null,
      fixedType: col.fixedType || null,
      formatJson: col.formatJson || null
    }))
  }
  const codes =
    pageRuntime.value?.columnFieldCodes ||
    pageRuntime.value?.titleFieldCodes ||
    []
  if (!codes.length) return [{ code: '_summary', label: '摘要' }]
  return codes.map((code) => ({
    code,
    label: fieldLabels.value[code] || code,
    width: null,
    fixedType: null,
    formatJson: null
  }))
})

const includeFieldCodes = computed(() => {
  const codes = displayColumns.value.map((c) => c.code).filter((c) => c !== '_summary')
  return codes.slice(0, 30)
})

const fieldById = computed(() => {
  const map = new Map()
  for (const f of fields.value) {
    if (f?.id != null) map.set(String(f.id), f)
  }
  return map
})

const activeFilterItems = computed(() => {
  return filterFields.value
    .map((cfg) => {
      const field = fieldById.value.get(String(cfg.fieldId || ''))
      if (!field?.fieldCode) return null
      return {
        key: String(cfg.id || cfg.fieldId),
        field: field.fieldCode,
        label: field.fieldName || field.fieldCode,
        op: normalizeQueryOp(cfg.opCode),
        required: Number(cfg.requiredFlag || 0) === 1,
        defaultValue: cfg.defaultValue ?? ''
      }
    })
    .filter(Boolean)
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
    fields.value = r.data || []
    for (const f of r.data || []) {
      if (f?.fieldCode) map[f.fieldCode] = f.fieldName || f.fieldCode
    }
    fieldLabels.value = map
  } catch {
    fields.value = []
    fieldLabels.value = {}
  }
}

async function loadFilterTpls() {
  filterTpls.value = []
  activeFilterTplId.value = ''
  filterFields.value = []
  filterValues.value = {}
  if (!modelId.value) return
  try {
    const r = await listFilterTpls(modelId.value)
    filterTpls.value = (r.data || []).filter((tpl) => tpl.status !== 2)
    const runtimeTplId = pageRuntime.value?.filterTplId ? String(pageRuntime.value.filterTplId) : ''
    const preferred = runtimeTplId && filterTpls.value.some((tpl) => String(tpl.id) === runtimeTplId)
      ? runtimeTplId
      : ''
    activeFilterTplId.value = preferred || (filterTpls.value.length ? String(filterTpls.value[0].id) : '')
    await loadActiveFilterFields()
  } catch {
    filterTpls.value = []
    filterFields.value = []
  }
}

async function loadActiveFilterFields() {
  filterFields.value = []
  filterValues.value = {}
  if (!activeFilterTplId.value) return
  try {
    const r = await listFilterFields(activeFilterTplId.value)
    filterFields.value = r.data || []
    const values = {}
    for (const item of filterFields.value) {
      const key = String(item.id || item.fieldId)
      if (item.defaultValue != null && item.defaultValue !== '') values[key] = String(item.defaultValue)
    }
    filterValues.value = values
  } catch {
    filterFields.value = []
  }
}

function formatVal(v) {
  if (v == null || v === '') return '-'
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

function formatCellValue(v, col) {
  let text = formatVal(v)
  if (!col?.formatJson || text === '-') return text
  try {
    const cfg = JSON.parse(col.formatJson)
    if (!cfg || Array.isArray(cfg) || typeof cfg !== 'object') return text
    if ((v == null || v === '') && cfg.emptyText != null) return String(cfg.emptyText)
    if (cfg.trim && typeof text === 'string') text = text.trim()
    const mappings = cfg.mappings || cfg.mapping
    if (mappings && Object.prototype.hasOwnProperty.call(mappings, text)) {
      text = String(mappings[text])
    }
    if (cfg.dateOnly && text.length >= 10 && text[4] === '-' && text[7] === '-') {
      text = text.slice(0, 10)
    }
    if (cfg.numberScale !== undefined && cfg.numberScale !== null) {
      const n = Number(text)
      if (!Number.isNaN(n)) text = n.toFixed(Number(cfg.numberScale))
    }
    return `${cfg.prefix || ''}${text}${cfg.suffix || ''}`
  } catch {
    return text
  }
}

function columnStyle(col) {
  const style = {}
  if (col.width) {
    style.width = `${col.width}px`
    style.minWidth = `${col.width}px`
  }
  if (col.fixedType === 'left') {
    style.position = 'sticky'
    style.left = '0'
    style.zIndex = 1
    style.background = '#fff'
  }
  if (col.fixedType === 'right') {
    style.position = 'sticky'
    style.right = '0'
    style.zIndex = 1
    style.background = '#fff'
  }
  return style
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
      cells[col.code] = formatCellValue(data[col.code], col)
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
    for (const item of activeFilterItems.value) {
      const raw = (filterValues.value[item.key] ?? item.defaultValue ?? '').trim()
      if (!raw) {
        if (item.required) {
          throw new Error(`请填写筛选条件：${item.label}`)
        }
        continue
      }
      filters.push(buildQueryFilter(item.field, item.op, raw))
      if (filters.length >= 10) break
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

function normalizeQueryOp(op) {
  const s = String(op || 'eq').toLowerCase()
  if (s === 'ge') return 'gte'
  if (s === 'le') return 'lte'
  if (['eq', 'ne', 'like', 'in', 'between', 'gt', 'gte', 'lt', 'lte'].includes(s)) return s
  return 'eq'
}

function buildQueryFilter(field, op, raw) {
  if (op === 'in') {
    return {
      field,
      op,
      values: raw.split(',').map((x) => x.trim()).filter(Boolean)
    }
  }
  if (op === 'between') {
    const values = raw.split(/[,~，至]/).map((x) => x.trim()).filter(Boolean).slice(0, 2)
    return { field, op, values }
  }
  return { field, op, value: raw }
}

function filterPlaceholder(item) {
  if (item.op === 'in') return '多个值用英文逗号分隔'
  if (item.op === 'between') return '起始,结束'
  return item.defaultValue ? `默认：${item.defaultValue}` : item.op
}

function resetFilters() {
  const values = {}
  for (const item of filterFields.value) {
    const key = String(item.id || item.fieldId)
    if (item.defaultValue != null && item.defaultValue !== '') values[key] = String(item.defaultValue)
  }
  filterValues.value = values
  load()
}

async function remove(row) {
  if (!row?.id || !(await confirmDialog(`删除记录 #${row.id}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteRecord(row.id)
    notify.success('记录已删除')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

function syncFromRoute() {
  appId.value = String(route.query.appId || '')
  modelId.value = String(route.query.modelId || '')
  pageId.value = String(route.query.pageId || '')
}

watch(() => route.query, async () => {
  syncFromRoute()
  await loadRuntime()
  await loadFieldLabels()
  await loadFilterTpls()
  load()
})

onMounted(async () => {
  syncFromRoute()
  await loadRuntime()
  await loadFieldLabels()
  await loadFilterTpls()
  load()
})
</script>

<style scoped>
.toolbar input {
  flex: 1;
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  min-width: 160px;
}
.filter-panel {
  margin: 0.75rem 0 1rem;
  display: grid;
  gap: 0.75rem;
}
.filter-panel select,
.filter-panel input {
  margin-top: 0.25rem;
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
}
.filter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.75rem;
}
.filter-grid label,
.filter-panel > label {
  display: flex;
  flex-direction: column;
  font-size: 0.9rem;
  color: #374151;
}
.required {
  color: var(--color-danger);
}
.link {
  background: none;
  border: none;
  color: #1677ff;
  cursor: pointer;
  padding: 0;
}
.danger-text {
  color: var(--color-danger);
}
</style>
