<template>
  <AdminLayout>
    <h2>记录详情 #{{ recordId }}</h2>
    <div class="toolbar">
      <router-link
        v-if="canManageRecords"
        class="btn"
        :to="{ path: '/records/form', query: { appId: currentAppId, modelId: currentModelId, pageId, recordId } }"
      >编辑</router-link>
      <button type="button" class="danger" @click="remove">删除</button>
      <router-link
        class="btn secondary"
        :to="{ path: '/records', query: { appId: currentAppId, modelId: currentModelId, pageId } }"
      >返回列表</router-link>
      <button type="button" class="secondary" @click="load">刷新</button>
      <button type="button" class="secondary" @click="showRaw = !showRaw">
        {{ showRaw ? '结构化' : '原始 JSON' }}
      </button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <pre v-if="showRaw && recordText" class="pre">{{ recordText }}</pre>
    <table v-else-if="dataEntries.length" class="table detail-table">
      <thead>
        <tr><th>字段</th><th>值</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="entry in dataEntries" :key="entry.key">
          <td>
            <strong>{{ entry.label }}</strong>
            <span class="muted code"> {{ entry.key }}</span>
          </td>
          <td>{{ entry.text || '-' }}</td>
          <td class="actions">
            <template v-if="entry.refIds.length">
              <router-link
                v-for="id in entry.refIds"
                :key="id"
                class="link"
                :to="{ path: '/records/detail', query: { recordId: id } }"
              >打开 #{{ id }}</router-link>
            </template>
            <template v-if="entry.fileId">
              <button type="button" class="link" @click="viewFile(entry.fileId)">预览</button>
              <button type="button" class="link" @click="downloadFile(entry.fileId, entry.text)">下载</button>
            </template>
            <span v-if="!entry.refIds.length && !entry.fileId" class="muted">-</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else-if="!error" class="muted">暂无字段数据</p>

    <h3>变更历史</h3>
    <table v-if="history.length" class="table">
      <thead><tr><th>ID</th><th>时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="h in history" :key="h.id">
          <td>{{ h.id }}</td>
          <td>{{ h.createdAt || h.createTime }}</td>
          <td>{{ h.actionType || h.opType || h.action || '-' }}</td>
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
import { listFieldsByModel } from '../api/meta.js'
import { listDepartmentPickerOptions, listMemberPickerOptions } from '../api/rbac.js'
import { deleteRecord, getRecord, listRecordHistory } from '../api/records'
import { fetchUploadDownloadBlob, fetchUploadViewBlob } from '../api/upload.js'
import { saveBlob } from '../api/http.js'
import {
  fieldTypeCode,
  isAddressField,
  isBooleanField,
  isDateRangeField,
  isDepartmentField,
  isFileField,
  isPersonField,
  isRatingSelectField,
  isRefField,
  isRefMultiField
} from '../utils/fieldTypes.js'
import { resolveRefDisplay } from '../utils/refPicker.js'
import { confirmDialog } from '../utils/dialog.js'
import { hasId, idToString, uniqueIds } from '../utils/id.js'
import { notify } from '../utils/notify.js'
import { createModulePermState, MODULE_PERMS } from '../utils/modulePerms.js'

const route = useRoute()
const router = useRouter()
const recordId = computed(() => idToString(route.query.recordId))
const pageId = computed(() => idToString(route.query.pageId))
const detail = ref(null)
const fields = ref([])
const fieldMetaByCode = ref({})
const refLabelByKey = ref({})
const memberLabelMap = ref({})
const deptLabelMap = ref({})
const history = ref([])
const error = ref('')
const showRaw = ref(false)
const { loadModulePerms, hasModulePerm } = createModulePermState()

const currentAppId = computed(() => idToString(route.query.appId || detail.value?.record?.appId))
const currentModelId = computed(() => idToString(route.query.modelId || detail.value?.record?.modelId))
const canManageRecords = computed(() => hasModulePerm(MODULE_PERMS.records))
const recordText = computed(() => {
  try {
    return JSON.stringify(detail.value || null, null, 2)
  } catch {
    return String(detail.value ?? '')
  }
})

const orderedFields = computed(() =>
  (fields.value || [])
    .filter((f) => f?.fieldCode)
    .slice()
    .sort((a, b) => Number(a.sortNo ?? 0) - Number(b.sortNo ?? 0))
)

const dataEntries = computed(() => {
  const data = detail.value?.data || {}
  const files = detail.value?.files || {}
  if (!data || typeof data !== 'object') return []
  const seen = new Set()
  const entries = []
  for (const f of orderedFields.value) {
    if (!Object.prototype.hasOwnProperty.call(data, f.fieldCode)) continue
    entries.push(buildEntry(f.fieldCode, data[f.fieldCode], f, files))
    seen.add(f.fieldCode)
  }
  for (const key of Object.keys(data).sort()) {
    if (seen.has(key)) continue
    entries.push(buildEntry(key, data[key], fieldMetaByCode.value[key], files))
  }
  return entries
})

function buildEntry(key, raw, field, files) {
  const fileId = field && isFileField(field) ? resolveFileId(raw) : ''
  const fileMeta = fileId ? files[String(fileId)] : null
  return {
    key,
    label: field?.fieldName || key,
    raw,
    text: displayValue(key, raw, field, fileMeta),
    refIds: field && isRefField(field) ? parseRefIds(raw) : [],
    fileId
  }
}

function displayValue(key, raw, field, fileMeta) {
  if (fileMeta && typeof fileMeta === 'object') {
    return fileMeta.originalName || fileMeta.origName || fileMeta.fileName || `文件 #${resolveFileId(raw)}`
  }
  if (!field) return stringifyValue(raw)
  if (Object.prototype.hasOwnProperty.call(refLabelByKey.value, key)) {
    return refLabelByKey.value[key]
  }
  if (isBooleanField(field)) {
    return raw === true || raw === 'true' || raw === 1 || raw === '1' ? '是' : '否'
  }
  if (isRatingSelectField(field)) {
    const n = Number(raw)
    return Number.isFinite(n) && n > 0 ? `${n} 星` : stringifyValue(raw)
  }
  if (isAddressField(field)) {
    return formatAddress(raw)
  }
  if (isDateRangeField(field)) {
    return formatDateRange(raw)
  }
  return stringifyValue(raw)
}

function parseRefIds(value) {
  if (value == null || value === '') return []
  if (Array.isArray(value)) return uniqueIds(value)
  const text = String(value).trim()
  if (!text) return []
  if (text.startsWith('[')) {
    try {
      const arr = JSON.parse(text)
      return Array.isArray(arr) ? uniqueIds(arr) : []
    } catch {
      return []
    }
  }
  return hasId(text) ? [text] : []
}

function resolveFileId(value) {
  const id = idToString(value)
  return hasId(id) ? id : ''
}

function stringifyValue(value) {
  if (value == null || value === '') return ''
  if (Array.isArray(value)) return value.join(', ')
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch {
      return String(value)
    }
  }
  return String(value)
}

function parseObjectValue(value) {
  if (!value) return null
  if (typeof value === 'object' && !Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(String(value))
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch {
    return null
  }
}

function formatAddress(value) {
  const obj = parseObjectValue(value)
  if (!obj) return stringifyValue(value)
  const parts = [obj.region, obj.detail].filter(Boolean)
  if (obj.lat != null && obj.lng != null) parts.push(`(${obj.lat}, ${obj.lng})`)
  return parts.join(' ') || stringifyValue(value)
}

function formatDateRange(value) {
  const obj = parseObjectValue(value)
  if (!obj) return stringifyValue(value)
  return [obj.start, obj.end].filter(Boolean).join(' ~ ')
}

async function load() {
  if (!recordId.value) return
  error.value = ''
  try {
    const [recordResp, historyResp] = await Promise.all([
      getRecord(recordId.value),
      listRecordHistory(recordId.value)
    ])
    detail.value = recordResp.data || null
    history.value = historyResp.data || []
    await loadFieldMeta()
    await enrichLabels()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function loadFieldMeta() {
  fields.value = []
  fieldMetaByCode.value = {}
  const modelId = currentModelId.value
  if (!hasId(modelId)) return
  try {
    const r = await listFieldsByModel(modelId)
    fields.value = r.data || []
    const map = {}
    for (const field of fields.value) {
      if (field?.fieldCode) map[field.fieldCode] = field
    }
    fieldMetaByCode.value = map
  } catch {
    fields.value = []
    fieldMetaByCode.value = {}
  }
}

async function enrichLabels() {
  refLabelByKey.value = {}
  memberLabelMap.value = {}
  deptLabelMap.value = {}
  const appId = currentAppId.value
  if (hasId(appId)) {
    try {
      const [members, departments] = await Promise.all([
        listMemberPickerOptions(appId),
        listDepartmentPickerOptions(appId)
      ])
      for (const item of members.data || []) memberLabelMap.value[idToString(item.value)] = item.text
      for (const item of departments.data || []) deptLabelMap.value[idToString(item.value)] = item.text
    } catch {
      // Picker labels are optional; keep raw ids when they cannot be loaded.
    }
  }
  const labels = {}
  const data = detail.value?.data || {}
  for (const key of Object.keys(data)) {
    const field = fieldMetaByCode.value[key]
    if (!field) continue
    const raw = data[key]
    if (isRefField(field)) {
      const ids = parseRefIds(raw)
      if (isRefMultiField(field)) {
        labels[key] = (await Promise.all(ids.map((id) => resolveRefDisplay(id, field.refDisplayField)))).join('、')
      } else {
        labels[key] = ids[0] ? await resolveRefDisplay(ids[0], field.refDisplayField) : ''
      }
      continue
    }
    if (isPersonField(field)) {
      labels[key] = parseRefIds(raw).map((id) => memberLabelMap.value[id] || `#${id}`).join('、')
      continue
    }
    if (isDepartmentField(field)) {
      labels[key] = parseRefIds(raw).map((id) => deptLabelMap.value[id] || `#${id}`).join('、')
      continue
    }
    if (fieldTypeCode(field) === 'DATE_RANGE') {
      labels[key] = formatDateRange(raw)
    }
  }
  refLabelByKey.value = labels
}

async function viewFile(fileId) {
  error.value = ''
  try {
    const result = await fetchUploadViewBlob(fileId)
    const url = URL.createObjectURL(result.blob)
    window.open(url, '_blank', 'noopener,noreferrer')
    setTimeout(() => URL.revokeObjectURL(url), 30000)
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function downloadFile(fileId, fallbackName) {
  error.value = ''
  try {
    const result = await fetchUploadDownloadBlob(fileId)
    saveBlob(result.blob, result.filename || fallbackName || `file-${fileId}`)
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove() {
  if (!canManageRecords.value) {
    error.value = '当前账号没有记录维护权限'
    return
  }
  if (!recordId.value || !(await confirmDialog(`删除记录 #${recordId.value}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteRecord(recordId.value)
    notify.success('记录已删除')
    router.push({ path: '/records', query: { appId: currentAppId.value, modelId: currentModelId.value, pageId: pageId.value } })
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

watch(() => route.query, load, { deep: true })
onMounted(async () => {
  await loadModulePerms()
  await load()
})
</script>

<style scoped>
.pre { background: #fff; border: 1px solid #e5e7eb; padding: 0.75rem; border-radius: 6px; overflow: auto; font-size: 0.85rem; }
h3 { margin-top: 1.25rem; }
.detail-table td:first-child { width: 220px; }
.code { font-size: 0.78rem; }
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}
.link {
  background: none;
  border: none;
  color: #1677ff;
  cursor: pointer;
  padding: 0;
}
</style>
