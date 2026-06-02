<template>
  <view class="ref-sub-table">
    <view class="u-subtitle">{{ label }}</view>
    <view v-if="relationHint" class="ref-sub-table__hint">{{ relationHint }}</view>
    <view v-if="rows.length === 0" class="ref-sub-table__empty">暂无明细，点击下方添加</view>
    <scroll-view v-else scroll-x class="ref-sub-table__scroll">
      <view class="ref-sub-table__table">
        <view class="ref-sub-table__row ref-sub-table__row--head">
          <text class="ref-sub-table__cell ref-sub-table__cell--idx">#</text>
          <text v-for="col in columns" :key="col.code" class="ref-sub-table__cell">{{ col.label }}</text>
          <text class="ref-sub-table__cell ref-sub-table__cell--act">操作</text>
        </view>
        <view v-for="(row, idx) in rows" :key="row.id" class="ref-sub-table__row">
          <text class="ref-sub-table__cell ref-sub-table__cell--idx">{{ idx + 1 }}</text>
          <text v-for="col in columns" :key="col.code" class="ref-sub-table__cell">{{ row.cells[col.code] ?? '-' }}</text>
          <view class="ref-sub-table__cell ref-sub-table__cell--act">
            <text class="ref-sub-table__link" @click="openRecord(row.id)">查看</text>
            <text class="ref-sub-table__link ref-sub-table__link--danger" @click="removeRow(row.id)">移除</text>
          </view>
        </view>
      </view>
    </scroll-view>
    <ActionBar>
      <uni-button size="mini" type="primary" :disabled="!canAdd" @click="pickAdd">选择已有</uni-button>
      <uni-button size="mini" :disabled="!refModelId" @click="createNew">新建并关联</uni-button>
    </ActionBar>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ActionBar from '@/ui/ActionBar.vue'
import { listFieldsByModel, type ModuleField } from '@/api/meta'
import { attachRelation, detachRelation } from '@/api/records'
import { configFromMeta } from '@/utils/fieldTypes'
import { buildRowCellsMap, loadSubRowsByRelation } from '@/utils/refPicker'
import { hasId, idToString, uniqueIds, type IdValue } from '@/utils/id'

type Option = { value: string | number; text: string }
type Col = { code: string; label: string }
type Row = { id: string; cells: Record<string, string> }

const props = defineProps<{
  field: ModuleField
  appId: IdValue
  parentRecordId?: IdValue
  parentModelId?: IdValue
  options: Option[]
  modelValue: unknown
}>()

const emit = defineEmits<{ (e: 'update:modelValue', v: string[]): void }>()

const label = computed(() => props.field.relationModuleLabel || props.field.fieldName || '子表/明细')
const refModelId = computed(() => idToString(props.field.refModelId))
const columns = ref<Col[]>([])
const rowMap = ref<Record<string, Row>>({})
const relationHint = ref('')
const relationFkField = ref('')
const relationId = ref('')
const relationRelType = ref('')
const loadingRelation = ref(false)

const useRelationQuery = computed(
  () =>
    hasId(props.parentRecordId) &&
    hasId(props.parentModelId) &&
    hasId(refModelId.value)
)

const selectedIds = computed(() => {
  const raw = props.modelValue
  if (raw == null || raw === '') return [] as string[]
  if (Array.isArray(raw)) {
    return uniqueIds(raw as IdValue[])
  }
  if (typeof raw === 'string') {
    const t = raw.trim()
    if (!t) return []
    if (t.startsWith('[')) {
      try {
        const arr = JSON.parse(t)
        return Array.isArray(arr) ? uniqueIds(arr as IdValue[]) : []
      } catch {
        return []
      }
    }
    return uniqueIds(t.split(',').map((x) => x.trim()))
  }
  const id = idToString(raw as IdValue)
  return hasId(id) ? [id] : []
})

const rows = computed(() => selectedIds.value.map((id) => rowMap.value[id]).filter(Boolean) as Row[])

const canAdd = computed(() => {
  const used = new Set(selectedIds.value)
  return props.options.some((o) => {
    const id = idToString(o.value)
    return hasId(id) && !used.has(id)
  })
})

function emitIds(ids: string[]) {
  emit('update:modelValue', uniqueIds(ids))
}

function isNnRelation() {
  const t = String(relationRelType.value || '').toLowerCase()
  return t === 'n-n' || t === 'nn'
}

async function ensureRelationLoaded() {
  if (!useRelationQuery.value || relationId.value) return
  await loadFromRelation()
}

async function removeRow(id: string) {
  await ensureRelationLoaded()
  if (isNnRelation() && relationId.value) {
    const pid = idToString(props.parentRecordId)
    if (!hasId(pid)) {
      uni.showToast({ title: '父记录保存后才能移除关联', icon: 'none' })
      return
    }
    try {
      await detachRelation({ relationId: relationId.value, parentRecordId: pid, childRecordId: id })
      await loadFromRelation()
      uni.showToast({ title: '关联已移除', icon: 'success' })
    } catch (e: any) {
      uni.showToast({ title: e?.message || '移除关联失败', icon: 'none' })
    }
    return
  }
  emitIds(selectedIds.value.filter((x) => x !== id))
}

function openRecord(id: string) {
  if (!hasId(id)) return
  uni.navigateTo({ url: `/pages/system/records/detail?recordId=${encodeURIComponent(id)}` })
}

function createNew() {
  if (!hasId(refModelId.value) || !hasId(props.appId)) {
    uni.showToast({ title: '未配置关联模型', icon: 'none' })
    return
  }
  let url = `/pages/system/records/form?appId=${encodeURIComponent(idToString(props.appId))}&modelId=${encodeURIComponent(refModelId.value)}&embed=1`
  const pid = idToString(props.parentRecordId)
  if (hasId(pid) && relationFkField.value && !isNnRelation()) {
    url += `&linkParentId=${encodeURIComponent(pid)}&linkFkField=${encodeURIComponent(relationFkField.value)}`
  }
  uni.navigateTo({
    url,
    events: {
      recordCreated: async (payload: { recordId?: IdValue }) => {
        const id = idToString(payload?.recordId)
        if (!hasId(id)) return
        await ensureRelationLoaded()
        if (isNnRelation() && relationId.value) {
          const parentId = idToString(props.parentRecordId)
          if (hasId(parentId)) {
            try {
              await attachRelation({ relationId: relationId.value, parentRecordId: parentId, childRecordId: id })
              await loadFromRelation()
              return
            } catch (e: any) {
              uni.showToast({ title: e?.message || '新建记录已保存，但关联写入失败', icon: 'none' })
            }
          }
        }
        if (!selectedIds.value.includes(id)) {
          emitIds([...selectedIds.value, id])
        }
        if (useRelationQuery.value) syncDisplay()
      }
    }
  })
}

function pickAdd() {
  const used = new Set(selectedIds.value)
  const candidates = props.options.filter((o) => {
    const id = idToString(o.value)
    return hasId(id) && !used.has(id)
  })
  if (!candidates.length) {
    uni.showToast({ title: '没有可添加的记录', icon: 'none' })
    return
  }
  uni.showActionSheet({
    itemList: candidates.slice(0, 12).map((o) => o.text),
    success: async (res) => {
      const picked = candidates[res.tapIndex]
      if (!picked) return
      const id = idToString(picked.value)
      if (!hasId(id)) return
      await ensureRelationLoaded()
      if (isNnRelation() && relationId.value) {
        const pid = idToString(props.parentRecordId)
        if (!hasId(pid)) {
          uni.showToast({ title: '父记录保存后才能添加关联', icon: 'none' })
          return
        }
        try {
          await attachRelation({ relationId: relationId.value, parentRecordId: pid, childRecordId: id })
          await loadFromRelation()
          uni.showToast({ title: '关联已添加', icon: 'success' })
        } catch (e: any) {
          uni.showToast({ title: e?.message || '添加关联失败', icon: 'none' })
        }
        return
      }
      emitIds([...selectedIds.value, id])
    }
  })
}

async function loadColumns() {
  const cfg = configFromMeta(props.field)
  const listFields = (cfg.listFields as string[]) || []
  if (!hasId(refModelId.value)) {
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

async function refreshRows(ids: string[]) {
  if (!ids.length) {
    rowMap.value = {}
    return
  }
  if (!hasId(refModelId.value) || !hasId(props.appId)) {
    rowMap.value = Object.fromEntries(
      ids.map((id) => {
        const opt = props.options.find((o) => idToString(o.value) === id)
        return [id, { id, cells: { _title: opt?.text || `#${id}` } }]
      })
    ) as Record<string, Row>
    return
  }
  try {
    rowMap.value = await buildRowCellsMap(
      props.appId,
      refModelId.value,
      ids,
      columns.value,
      props.field.refDisplayField ?? undefined,
      props.options
    )
  } catch {
    rowMap.value = Object.fromEntries(
      ids.map((id) => [id, { id, cells: { _title: `#${id}` } }])
    ) as Record<string, Row>
  }
}

watch(
  () => [props.field, refModelId.value],
  () => {
    loadColumns()
  },
  { immediate: true }
)

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
        : `已按关系 #${r.relationId} 加载 ${ids.length} 条`
      return true
    }
  } catch (e: any) {
    relationHint.value = e?.message || '按关系加载失败'
    relationId.value = ''
    relationRelType.value = ''
  } finally {
    loadingRelation.value = false
  }
  return false
}

async function syncDisplay() {
  if (loadingRelation.value) return
  if (useRelationQuery.value) {
    const ok = await loadFromRelation()
    if (ok) return
  }
  await refreshRows(selectedIds.value)
}

watch(
  () => [
    props.parentRecordId,
    props.parentModelId,
    selectedIds.value.join(','),
    props.options.length,
    columns.value.map((c) => c.code).join(',')
  ],
  syncDisplay,
  { immediate: true }
)
</script>

<style scoped>
.ref-sub-table__hint {
  color: #0369a1;
  font-size: 12px;
  margin-bottom: 6px;
}
.ref-sub-table__empty {
  color: var(--u-text-muted, #888);
  font-size: 13px;
  padding: 8px 0;
}
.ref-sub-table__scroll {
  width: 100%;
}
.ref-sub-table__table {
  min-width: 100%;
}
.ref-sub-table__row {
  display: flex;
  flex-direction: row;
  align-items: center;
  border-bottom: 1px solid #eee;
  padding: 8px 0;
}
.ref-sub-table__row--head {
  font-weight: 600;
  color: #333;
}
.ref-sub-table__cell {
  flex: 1;
  min-width: 72px;
  font-size: 13px;
  padding: 0 6px;
  word-break: break-all;
}
.ref-sub-table__cell--idx {
  flex: 0 0 28px;
  min-width: 28px;
}
.ref-sub-table__cell--act {
  flex: 0 0 88px;
  min-width: 88px;
  display: flex;
  gap: 8px;
}
.ref-sub-table__link {
  color: #007aff;
  font-size: 12px;
}
.ref-sub-table__link--danger {
  color: #d00;
}
</style>
