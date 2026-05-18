<template>
  <div class="ref-sub">
    <p class="ref-sub__title">{{ label }}</p>
    <p v-if="relationHint" class="relation-hint">{{ relationHint }}</p>
    <p v-if="!rows.length" class="muted">暂无明细</p>
    <table v-else class="table">
      <thead>
        <tr>
          <th>#</th>
          <th v-for="col in columns" :key="col.code">{{ col.label }}</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, idx) in rows" :key="row.id">
          <td>{{ idx + 1 }}</td>
          <td v-for="col in columns" :key="col.code">{{ row.cells[col.code] ?? '-' }}</td>
          <td>
            <router-link :to="`/records/detail?recordId=${row.id}`" target="_blank">查看</router-link>
            <button type="button" class="link danger" @click="removeRow(row.id)">移除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="toolbar">
      <button type="button" :disabled="!canAdd" @click="pickAdd">选择已有</button>
      <button type="button" :disabled="!refModelId" @click="createNew">新建并关联</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listFieldsByModel } from '../../api/meta.js'
import { configFromMeta } from '../../utils/fieldTypes.js'
import { buildRowCellsMap, loadSubRowsByRelation } from '../../utils/refPicker.js'
import { takeEmbedRecordCreated } from '../../utils/embedRecord.js'

const props = defineProps({
  field: { type: Object, required: true },
  appId: { type: Number, required: true },
  parentRecordId: { type: Number, default: 0 },
  parentModelId: { type: Number, default: 0 },
  modelValue: { default: null },
  options: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue'])

const router = useRouter()
const label = computed(() => props.field.relationModuleLabel || props.field.fieldName || '子表/明细')
const refModelId = computed(() => Number(props.field.refModelId) || 0)
const columns = ref([])
const rowMap = ref({})
const relationHint = ref('')
const relationFkField = ref('')
const loadingRelation = ref(false)

function parseIds(raw) {
  if (raw == null || raw === '') return []
  if (Array.isArray(raw)) return raw.map(Number).filter((n) => n > 0)
  if (typeof raw === 'string') {
    const t = raw.trim()
    if (!t) return []
    if (t.startsWith('[')) {
      try {
        const arr = JSON.parse(t)
        return Array.isArray(arr) ? arr.map(Number).filter((n) => n > 0) : []
      } catch {
        return t.split(',').map((x) => Number(x.trim())).filter((n) => n > 0)
      }
    }
    return t.split(',').map((x) => Number(x.trim())).filter((n) => n > 0)
  }
  const n = Number(raw)
  return n > 0 ? [n] : []
}

const selectedIds = computed(() => parseIds(props.modelValue))
const rows = computed(() => selectedIds.value.map((id) => rowMap.value[id]).filter(Boolean))

const canAdd = computed(() => {
  const used = new Set(selectedIds.value)
  return props.options.some((o) => {
    const id = Number(o.value)
    return id > 0 && !used.has(id)
  })
})

const useRelationQuery = computed(
  () => Number(props.parentRecordId) > 0 && Number(props.parentModelId) > 0 && refModelId.value > 0
)

function emitIds(ids) {
  emit('update:modelValue', ids)
}

function removeRow(id) {
  emitIds(selectedIds.value.filter((x) => x !== id))
}

function pickAdd() {
  const used = new Set(selectedIds.value)
  const candidates = props.options.filter((o) => {
    const id = Number(o.value)
    return id > 0 && !used.has(id)
  })
  if (!candidates.length) {
    alert('没有可添加的记录')
    return
  }
  const names = candidates.slice(0, 20).map((o) => o.text)
  const idx = Number(prompt(`选择序号:\n${names.map((n, i) => `${i + 1}. ${n}`).join('\n')}`)) - 1
  const picked = candidates[idx]
  if (!picked) return
  const id = Number(picked.value)
  if (id) emitIds([...selectedIds.value, id])
}

function createNew() {
  if (!refModelId.value || !props.appId) {
    alert('未配置关联模型')
    return
  }
  const q = { appId: props.appId, modelId: refModelId.value, embed: '1' }
  const pid = Number(props.parentRecordId)
  if (pid > 0 && relationFkField.value) {
    q.linkParentId = String(pid)
    q.linkFkField = relationFkField.value
  }
  router.push({ path: '/records/form', query: q })
}

function onEmbedReturn() {
  const id = takeEmbedRecordCreated()
  if (id && !selectedIds.value.includes(id)) {
    emitIds([...selectedIds.value, id])
    if (useRelationQuery.value) {
      loadFromRelation()
    }
  }
}

async function loadColumns() {
  const cfg = configFromMeta(props.field)
  const listFields = cfg.listFields || []
  if (!refModelId.value) {
    columns.value = [{ code: '_title', label: '标题' }]
    return
  }
  try {
    const r = await listFieldsByModel(refModelId.value)
    const fields = r.data || []
    const byCode = new Map(fields.filter((f) => f.fieldCode).map((f) => [String(f.fieldCode), f]))
    if (listFields.length) {
      columns.value = listFields.map((code) => {
        const f = byCode.get(code)
        return { code, label: f?.fieldName || code }
      })
    } else {
      const show = props.field.refDisplayField
      columns.value = show
        ? [{ code: show, label: byCode.get(show)?.fieldName || show }]
        : [{ code: '_title', label: '标题' }]
    }
  } catch {
    columns.value = [{ code: '_title', label: '标题' }]
  }
}

async function loadFromRelation() {
  if (!useRelationQuery.value || !columns.value.length) {
    relationHint.value = Number(props.parentRecordId) > 0 ? '' : '保存父记录后可按关系加载子表'
    return false
  }
  loadingRelation.value = true
  relationHint.value = ''
  try {
    const r = await loadSubRowsByRelation({
      field: props.field,
      appId: props.appId,
      parentModelId: props.parentModelId,
      parentRecordId: props.parentRecordId,
      columns: columns.value,
      options: props.options
    })
    relationFkField.value = r.fkField || ''
    if (r.relationId && r.relType !== 'n-n') {
      rowMap.value = r.rowMap
      const ids = r.ids || []
      const cur = selectedIds.value.join(',')
      const next = ids.join(',')
      if (cur !== next) emitIds(ids)
      relationHint.value = `已按关系 #${r.relationId} 加载 ${ids.length} 条子记录（外键子表）`
      return true
    }
    if (r.relType === 'n-n') {
      relationHint.value = 'n-n 关系请使用父字段存储的关联 ID 列表'
    }
  } catch (e) {
    relationHint.value = e?.message || '按关系加载失败'
  } finally {
    loadingRelation.value = false
  }
  return false
}

async function refreshRows(ids) {
  if (!ids.length) {
    rowMap.value = {}
    return
  }
  if (!refModelId.value || !props.appId) {
    rowMap.value = Object.fromEntries(
      ids.map((id) => {
        const opt = props.options.find((o) => Number(o.value) === id)
        return [id, { id, cells: { _title: opt?.text || `#${id}` } }]
      })
    )
    return
  }
  try {
    rowMap.value = await buildRowCellsMap(
      props.appId,
      refModelId.value,
      ids,
      columns.value,
      props.field.refDisplayField,
      props.options
    )
  } catch {
    rowMap.value = Object.fromEntries(
      ids.map((id) => [id, { id, cells: { _title: `#${id}` } }])
    )
  }
}

async function syncDisplay() {
  if (loadingRelation.value) return
  if (useRelationQuery.value) {
    const ok = await loadFromRelation()
    if (ok) return
  }
  await refreshRows(selectedIds.value)
}

watch(() => [props.field, refModelId.value], loadColumns, { immediate: true })
watch(
  () => [
    props.parentRecordId,
    props.parentModelId,
    columns.value.map((c) => c.code).join(','),
    props.options.length,
    selectedIds.value.join(',')
  ],
  syncDisplay,
  { immediate: true }
)

onMounted(() => {
  window.addEventListener('focus', onEmbedReturn)
  onEmbedReturn()
})
onUnmounted(() => window.removeEventListener('focus', onEmbedReturn))
</script>

<style scoped>
.ref-sub__title {
  font-weight: 500;
  margin: 0 0 0.5rem;
}
.relation-hint {
  font-size: 0.82rem;
  color: #0369a1;
  margin: 0 0 0.5rem;
}
.table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 0.75rem;
  font-size: 0.88rem;
}
.table th,
.table td {
  border: 1px solid #e5e7eb;
  padding: 0.4rem 0.5rem;
}
.toolbar {
  display: flex;
  gap: 0.5rem;
}
.toolbar button {
  padding: 0.35rem 0.75rem;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: #fff;
  cursor: pointer;
}
.toolbar button:first-child {
  background: #1677ff;
  color: #fff;
  border-color: #1677ff;
}
.link {
  background: none;
  border: none;
  color: #1677ff;
  cursor: pointer;
  margin-right: 0.5rem;
}
.link.danger {
  color: #c00;
}
.muted {
  color: #888;
  font-size: 0.85rem;
}
</style>
