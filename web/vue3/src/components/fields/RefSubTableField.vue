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
            <router-link :to="recordDetailTo(row.id)" target="_blank">查看</router-link>
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
import { attachRelation, detachRelation } from '../../api/records.js'
import { configFromMeta } from '../../utils/fieldTypes.js'
import { buildRowCellsMap, loadSubRowsByRelation } from '../../utils/refPicker.js'
import { takeEmbedRecordCreated } from '../../utils/embedRecord.js'
import { hasId, idToString, sameId, uniqueIds } from '../../utils/id.js'
import { promptText } from '../../utils/dialog.js'
import { notify } from '../../utils/notify.js'

const props = defineProps({
  field: { type: Object, required: true },
  appId: { type: [String, Number], required: true },
  parentRecordId: { type: [String, Number], default: '0' },
  parentModelId: { type: [String, Number], default: '0' },
  modelValue: { default: null },
  options: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue'])

const router = useRouter()
const label = computed(() => props.field.relationModuleLabel || props.field.fieldName || '子表/明细')
const refModelId = computed(() => idToString(props.field.refModelId))
const columns = ref([])
const rowMap = ref({})
const relationHint = ref('')
const relationFkField = ref('')
const relationId = ref('')
const relationRelType = ref('')
const loadingRelation = ref(false)

function parseIds(raw) {
  if (raw == null || raw === '') return []
  if (Array.isArray(raw)) return uniqueIds(raw)
  if (typeof raw === 'string') {
    const t = raw.trim()
    if (!t) return []
    if (t.startsWith('[')) {
      try {
        const arr = JSON.parse(t)
        return Array.isArray(arr) ? uniqueIds(arr) : []
      } catch {
        return uniqueIds(t.split(','))
      }
    }
    return uniqueIds(t.split(','))
  }
  const id = idToString(raw)
  return hasId(id) ? [id] : []
}

const selectedIds = computed(() => parseIds(props.modelValue))
const rows = computed(() => selectedIds.value.map((id) => rowMap.value[id]).filter(Boolean))

const canAdd = computed(() => {
  const used = new Set(selectedIds.value)
  return props.options.some((o) => {
    const id = idToString(o.value)
    return hasId(id) && !used.has(id)
  })
})

const useRelationQuery = computed(
  () => hasId(props.parentRecordId) && hasId(props.parentModelId) && hasId(refModelId.value)
)

function emitIds(ids) {
  emit('update:modelValue', ids)
}

function isNnRelation() {
  const t = String(relationRelType.value || '').toLowerCase()
  return t === 'n-n' || t === 'nn'
}

async function ensureRelationLoaded() {
  if (!useRelationQuery.value || relationId.value) return
  await loadFromRelation()
}

async function removeRow(id) {
  await ensureRelationLoaded()
  if (isNnRelation() && relationId.value) {
    const pid = idToString(props.parentRecordId)
    if (!hasId(pid)) {
      notify.warn('父记录保存后才能移除 n-n 关联')
      return
    }
    try {
      await detachRelation({ relationId: relationId.value, parentRecordId: pid, childRecordId: id })
      await loadFromRelation()
      notify.success('关联已移除')
    } catch (e) {
      notify.warn(e?.message || '移除关联失败')
    }
    return
  }
  emitIds(selectedIds.value.filter((x) => x !== id))
}

function recordDetailTo(id) {
  return { path: '/records/detail', query: { recordId: idToString(id) } }
}

async function pickAdd() {
  const used = new Set(selectedIds.value)
  const candidates = props.options.filter((o) => {
    const id = idToString(o.value)
    return hasId(id) && !used.has(id)
  })
  if (!candidates.length) {
    notify.warn('没有可添加的记录')
    return
  }
  const names = candidates.slice(0, 20).map((o) => o.text)
  const pickedIndex = await promptText('选择记录序号', {
    message: names.map((n, i) => `${i + 1}. ${n}`).join('\n')
  })
  const idx = Number(pickedIndex) - 1
  const picked = candidates[idx]
  if (!picked) return
  const id = idToString(picked.value)
  if (!hasId(id)) return
  await ensureRelationLoaded()
  if (isNnRelation() && relationId.value) {
    const pid = idToString(props.parentRecordId)
    if (!hasId(pid)) {
      notify.warn('父记录保存后才能添加 n-n 关联')
      return
    }
    try {
      await attachRelation({ relationId: relationId.value, parentRecordId: pid, childRecordId: id })
      await loadFromRelation()
      notify.success('关联已添加')
    } catch (e) {
      notify.warn(e?.message || '添加关联失败')
    }
    return
  }
  emitIds([...selectedIds.value, id])
}

function createNew() {
  if (!refModelId.value || !props.appId) {
    notify.warn('未配置关联模型')
    return
  }
  const q = { appId: props.appId, modelId: refModelId.value, embed: '1' }
  const pid = idToString(props.parentRecordId)
  if (hasId(pid) && relationFkField.value && !isNnRelation()) {
    q.linkParentId = pid
    q.linkFkField = relationFkField.value
  }
  router.push({ path: '/records/form', query: q })
}

async function onEmbedReturn() {
  const id = takeEmbedRecordCreated()
  if (id && !selectedIds.value.includes(id)) {
    await ensureRelationLoaded()
    if (isNnRelation() && relationId.value) {
      const pid = idToString(props.parentRecordId)
      if (hasId(pid)) {
        try {
          await attachRelation({ relationId: relationId.value, parentRecordId: pid, childRecordId: id })
          await loadFromRelation()
          return
        } catch (e) {
          notify.warn(e?.message || '新建记录已保存，但关联写入失败')
        }
      }
    }
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
    relationHint.value = hasId(props.parentRecordId) ? '' : '保存父记录后可按关系加载子表'
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
    relationId.value = r.relationId || ''
    relationRelType.value = String(r.relType || '').toLowerCase()
    if (r.relationId) {
      rowMap.value = r.rowMap
      const ids = r.ids || []
      const cur = selectedIds.value.join(',')
      const next = ids.join(',')
      if (cur !== next) emitIds(ids)
      relationHint.value = r.relType === 'n-n'
        ? `已按 n-n 关系 #${r.relationId} 加载 ${ids.length} 条关联记录`
        : `已按关系 #${r.relationId} 加载 ${ids.length} 条子记录（外键子表）`
      return true
    }
  } catch (e) {
    relationHint.value = e?.message || '按关系加载失败'
    relationId.value = ''
    relationRelType.value = ''
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
        const opt = props.options.find((o) => sameId(o.value, id))
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
