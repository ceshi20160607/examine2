<template>
  <view class="ref-sub-table">
    <view class="u-subtitle">{{ label }}</view>
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
      <uni-button size="mini" type="primary" :disabled="!canAdd" @click="pickAdd">添加行</uni-button>
    </ActionBar>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ActionBar from '@/ui/ActionBar.vue'
import { getRecord } from '@/api/records'
import { listFieldsByModel, type ModuleField } from '@/api/meta'
import { configFromMeta } from '@/utils/fieldTypes'

type Option = { value: number | string; text: string }
type Col = { code: string; label: string }
type Row = { id: number; cells: Record<string, string> }

const props = defineProps<{
  field: ModuleField
  appId: number
  options: Option[]
  modelValue: unknown
}>()

const emit = defineEmits<{ (e: 'update:modelValue', v: number[]): void }>()

const label = computed(() => props.field.relationModuleLabel || props.field.fieldName || '子表/明细')
const refModelId = computed(() => Number(props.field.refModelId) || 0)
const columns = ref<Col[]>([])
const rowMap = ref<Record<number, Row>>({})

const selectedIds = computed(() => {
  const raw = props.modelValue
  if (raw == null || raw === '') return [] as number[]
  if (Array.isArray(raw)) {
    return raw.map((x) => Number(x)).filter((n) => n > 0)
  }
  if (typeof raw === 'string') {
    const t = raw.trim()
    if (!t) return []
    if (t.startsWith('[')) {
      try {
        const arr = JSON.parse(t)
        return Array.isArray(arr) ? arr.map((x) => Number(x)).filter((n) => n > 0) : []
      } catch {
        return []
      }
    }
    return t.split(',').map((x) => Number(x.trim())).filter((n) => n > 0)
  }
  const n = Number(raw)
  return n > 0 ? [n] : []
})

const rows = computed(() => selectedIds.value.map((id) => rowMap.value[id]).filter(Boolean) as Row[])

const canAdd = computed(() => {
  const used = new Set(selectedIds.value)
  return props.options.some((o) => {
    const id = Number(o.value)
    return id > 0 && !used.has(id)
  })
})

function emitIds(ids: number[]) {
  emit('update:modelValue', ids)
}

function removeRow(id: number) {
  emitIds(selectedIds.value.filter((x) => x !== id))
}

function openRecord(id: number) {
  if (!id) return
  uni.navigateTo({ url: `/pages/system/records/detail?recordId=${id}` })
}

function pickAdd() {
  const used = new Set(selectedIds.value)
  const candidates = props.options.filter((o) => {
    const id = Number(o.value)
    return id > 0 && !used.has(id)
  })
  if (!candidates.length) {
    uni.showToast({ title: '没有可添加的记录', icon: 'none' })
    return
  }
  uni.showActionSheet({
    itemList: candidates.slice(0, 12).map((o) => o.text),
    success: (res) => {
      const picked = candidates[res.tapIndex]
      if (!picked) return
      const id = Number(picked.value)
      if (!id) return
      emitIds([...selectedIds.value, id])
    }
  })
}

async function loadColumns() {
  const cfg = configFromMeta(props.field)
  const listFields = (cfg.listFields as string[]) || []
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

async function refreshRows(ids: number[]) {
  const next: Record<number, Row> = {}
  for (const id of ids) {
    const cached = rowMap.value[id]
    if (cached) {
      next[id] = cached
      continue
    }
    const opt = props.options.find((o) => Number(o.value) === id)
    let cells: Record<string, string> = { _title: opt?.text || `#${id}` }
    try {
      const d = await getRecord(id)
      const data = d.data?.data || {}
      const cols = columns.value
      cells = {}
      for (const col of cols) {
        if (col.code === '_title') {
          cells._title = opt?.text || String(data[props.field.refDisplayField || ''] ?? `#${id}`)
        } else {
          const v = data[col.code]
          cells[col.code] = v == null ? '-' : typeof v === 'object' ? JSON.stringify(v) : String(v)
        }
      }
    } catch {
      cells = { _title: opt?.text || `#${id}` }
    }
    next[id] = { id, cells }
  }
  rowMap.value = next
}

watch(
  () => [props.field, refModelId.value],
  () => {
    loadColumns()
  },
  { immediate: true }
)

watch(
  () => [selectedIds.value.join(','), props.options.length, columns.value.map((c) => c.code).join(',')],
  () => {
    refreshRows(selectedIds.value)
  },
  { immediate: true }
)
</script>

<style scoped>
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
