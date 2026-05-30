<template>
  <Page :title="pageTitle" :subtitle="pageSubtitle">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="!appId || !modelId" @click="goCreate">新建</uni-button>
        <uni-button :disabled="loading" @click="query">刷新</uni-button>
        <uni-easyinput v-model="keyword" placeholder="keyword(可选)" style="flex: 1; min-width: 160px" />
        <uni-button :disabled="loading" @click="reload">搜索</uni-button>
      </ActionBar>
      <view v-if="searchFieldCode" class="u-subtitle">搜索字段：{{ searchFieldCode }}</view>
      <view v-if="filterTpls.length" class="filter-box">
        <picker :range="filterTplNames" :value="activeFilterTplIndex" @change="changeFilterTpl">
          <view class="filter-picker">筛选模板：{{ activeFilterTplName }}</view>
        </picker>
        <view v-if="activeFilterItems.length" class="filter-items">
          <view v-for="item in activeFilterItems" :key="item.key" class="filter-item">
            <view class="u-subtitle">{{ item.label }}{{ item.required ? ' *' : '' }}</view>
            <uni-easyinput
              v-model="filterValues[item.key]"
              :placeholder="filterPlaceholder(item)"
              @confirm="query"
            />
          </view>
          <uni-button size="mini" :disabled="loading" @click="resetFilters">重置筛选</uni-button>
        </view>
      </view>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">结果</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="r in rows"
            :key="r.id"
            :title="titleOf(r.id)"
            :note="noteOf(r.id)"
            clickable
            @click="goDetail(r.id)"
          />
        </uni-list>
        <EmptyState v-else text="暂无数据" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import { queryRecords } from '@/api/records'
import { listFieldsByModel, type ModuleField } from '@/api/meta'
import { listFilterFields, listFilterTpls, type ModuleFilterFieldRow, type ModuleFilterTplRow } from '@/api/module'
import { getPageRuntime } from '@/api/pages'
import { pageQuerySuffix, type PageRuntime, type PageRuntimeColumn } from '@/utils/pageRuntime'
import { hasId, idToString, type IdValue } from '@/utils/id'

type Row = { id: string }
type Summary = { title: string; note: string }

const appId = ref('')
const modelId = ref('')
const pageId = ref('')
const pageRuntime = ref<PageRuntime | null>(null)
const { loading, error, run } = usePageRequest()
const rows = ref<Row[]>([])
const summaries = ref<Record<string, Summary>>({})
const keyword = ref('')
const searchFieldCode = ref<string>('')
const displayFieldCodes = ref<string[]>([])
const displayColumns = ref<PageRuntimeColumn[]>([])
const fields = ref<ModuleField[]>([])
const filterTpls = ref<ModuleFilterTplRow[]>([])
const activeFilterTplIndex = ref(0)
const filterFields = ref<ModuleFilterFieldRow[]>([])
const filterValues = ref<Record<string, string>>({})

const pageTitle = computed(() => {
  if (pageRuntime.value?.pageName) return pageRuntime.value.pageName
  return `Records（modelId=${modelId.value}）`
})
const pageSubtitle = computed(() => {
  if (pageRuntime.value?.pageCode) return `page=${pageRuntime.value.pageCode} · 关键字搜索`
  return '支持关键字搜索与详情查看'
})

onLoad((opts) => {
  appId.value = idToString((opts as any)?.appId)
  modelId.value = idToString((opts as any)?.modelId)
  pageId.value = idToString((opts as any)?.pageId)
})
const filterTplNames = computed(() => filterTpls.value.map((tpl) => tpl.tplName || tpl.tplCode || String(tpl.id)))
const activeFilterTplName = computed(() => filterTplNames.value[activeFilterTplIndex.value] || '-')
const fieldById = computed(() => {
  const map = new Map<string, ModuleField>()
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
    .filter((x): x is { key: string; field: string; label: string; op: string; required: boolean; defaultValue: string } => !!x)
})

function titleOf(id: string) {
  return summaries.value[id]?.title || `#${id}`
}
function noteOf(id: string) {
  return summaries.value[id]?.note || ''
}

async function query() {
  if (!hasId(modelId.value)) return
  await run(async () => {
    const filters: any[] = []
    const kw = keyword.value.trim()
    if (kw && searchFieldCode.value) {
      filters.push({ field: searchFieldCode.value, op: 'like', value: kw })
    }
    for (const item of activeFilterItems.value) {
      const raw = String(filterValues.value[item.key] ?? item.defaultValue ?? '').trim()
      if (!raw) {
        if (item.required) throw new Error(`请填写筛选条件：${item.label}`)
        continue
      }
      filters.push(buildQueryFilter(item.field, item.op, raw))
      if (filters.length >= 10) break
    }
    const cols = displayColumns.value.length
      ? displayColumns.value
      : displayFieldCodes.value.length > 0
        ? displayFieldCodes.value.map((code) => ({ fieldCode: code, label: code }))
        : pageRuntime.value?.titleFieldCodes?.length
          ? pageRuntime.value.titleFieldCodes.map((code) => ({ fieldCode: code, label: code }))
          : []
    const r = await queryRecords({
      appId: appId.value,
      modelId: modelId.value,
      page: 1,
      limit: 20,
      filters,
      includeFieldCodes: cols.length ? cols.map((c) => c.fieldCode).slice(0, 30) : []
    })
    const list = r.data?.list || []
    rows.value = list
      .map((x: any) => ({ id: idToString(x.id as IdValue) }))
      .filter((x: { id: string }) => hasId(x.id))
    summaries.value = {}
    for (const item of list) {
      const id = idToString(item.id as IdValue)
      if (!hasId(id)) continue
      const data = item.data || {}
      const keys = cols.length
        ? cols
        : Object.keys(data).slice(0, 3).map((code) => ({ fieldCode: code, label: code }))
      const parts = keys.map((c: PageRuntimeColumn) => `${c.label || c.fieldCode}=${formatCellValue(data[c.fieldCode], c)}`)
      summaries.value = {
        ...summaries.value,
        [id]: {
          title: parts[0] ? `${parts[0]} (#${id})` : `#${id}`,
          note: parts.slice(1).join(' | ')
        }
      }
    }
  })
}

async function reload() {
  await query()
}

function normalizeQueryOp(op?: string): string {
  const s = String(op || 'eq').toLowerCase()
  if (s === 'ge') return 'gte'
  if (s === 'le') return 'lte'
  if (['eq', 'ne', 'like', 'in', 'between', 'gt', 'gte', 'lt', 'lte'].includes(s)) return s
  return 'eq'
}

function buildQueryFilter(field: string, op: string, raw: string) {
  if (op === 'in') {
    return { field, op, values: raw.split(',').map((x) => x.trim()).filter(Boolean) }
  }
  if (op === 'between') {
    const values = raw.split(/[,~，至]/).map((x) => x.trim()).filter(Boolean).slice(0, 2)
    return { field, op, values }
  }
  return { field, op, value: raw }
}

function filterPlaceholder(item: { op: string; defaultValue: string }) {
  if (item.op === 'in') return '多个值用英文逗号分隔'
  if (item.op === 'between') return '起始,结束'
  return item.defaultValue ? `默认：${item.defaultValue}` : item.op
}

async function changeFilterTpl(e: any) {
  activeFilterTplIndex.value = Number(e?.detail?.value || 0)
  await loadActiveFilterFields()
}

function resetFilters() {
  applyDefaultFilterValues()
  query()
}

function stringifyValue(v: any): string {
  if (v == null) return ''
  if (Array.isArray(v)) return v.slice(0, 5).join(',')
  if (typeof v === 'object') {
    try {
      return JSON.stringify(v)
    } catch {
      return String(v)
    }
  }
  return String(v)
}

function formatCellValue(v: any, col: PageRuntimeColumn): string {
  let text = stringifyValue(v) || '-'
  if (!col?.formatJson || text === '-') return text
  try {
    const cfg = JSON.parse(col.formatJson)
    if (!cfg || Array.isArray(cfg) || typeof cfg !== 'object') return text
    if ((v == null || v === '') && cfg.emptyText != null) return String(cfg.emptyText)
    if (cfg.trim) text = text.trim()
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

function goCreate() {
  uni.navigateTo({
    url: `/pages/system/records/form?appId=${encodeURIComponent(appId.value)}&modelId=${encodeURIComponent(modelId.value)}${pageQuerySuffix(pageId.value)}`
  })
}

function goDetail(recordId: string) {
  uni.navigateTo({
    url: `/pages/system/records/detail?recordId=${encodeURIComponent(recordId)}${pageQuerySuffix(pageId.value)}`
  })
}

async function loadPageRuntime() {
  if (!hasId(pageId.value)) return
  try {
    const r = await getPageRuntime(pageId.value)
    pageRuntime.value = r.data || null
    if (pageRuntime.value?.appId && !appId.value) appId.value = idToString(pageRuntime.value.appId)
    if (pageRuntime.value?.modelId && !modelId.value) modelId.value = idToString(pageRuntime.value.modelId)
    if (pageRuntime.value?.searchFieldCode) {
      searchFieldCode.value = pageRuntime.value.searchFieldCode
    }
    const runtimeCols = (pageRuntime.value?.columns || [])
      .filter((c) => c?.fieldCode && c.visibleFlag !== 0)
      .sort((a, b) => Number(a.sortNo ?? 0) - Number(b.sortNo ?? 0))
    displayColumns.value = runtimeCols
    const cols = runtimeCols.length
      ? runtimeCols.map((c) => c.fieldCode)
      : pageRuntime.value?.columnFieldCodes || pageRuntime.value?.titleFieldCodes
    displayFieldCodes.value = cols?.length ? cols : []
  } catch {
    pageRuntime.value = null
  }
}

async function loadFieldsForSearch() {
  if (!hasId(modelId.value)) return
  try {
    const r = await listFieldsByModel(modelId.value)
    const list = r.data || []
    fields.value = list
    if (searchFieldCode.value) return
    const cand = list.find((f) => f && f.hiddenFlag !== 1 && f.fieldCode && !f.dictCode)
    searchFieldCode.value = cand?.fieldCode ? String(cand.fieldCode) : ''
  } catch {
    fields.value = []
    searchFieldCode.value = ''
  }
}

async function loadFilterTpls() {
  filterTpls.value = []
  activeFilterTplIndex.value = 0
  filterFields.value = []
  filterValues.value = {}
  if (!hasId(modelId.value)) return
  try {
    const r = await listFilterTpls(modelId.value)
    filterTpls.value = (r.data || []).filter((tpl) => tpl.status !== 2)
    const runtimeTplId = pageRuntime.value?.filterTplId ? idToString(pageRuntime.value.filterTplId) : ''
    const runtimeIndex = runtimeTplId
      ? filterTpls.value.findIndex((tpl) => idToString(tpl.id) === runtimeTplId)
      : -1
    activeFilterTplIndex.value = runtimeIndex >= 0 ? runtimeIndex : 0
    await loadActiveFilterFields()
  } catch {
    filterTpls.value = []
    filterFields.value = []
  }
}

async function loadActiveFilterFields() {
  filterFields.value = []
  filterValues.value = {}
  const tpl = filterTpls.value[activeFilterTplIndex.value]
  if (!tpl?.id) return
  try {
    const r = await listFilterFields(tpl.id)
    filterFields.value = r.data || []
    applyDefaultFilterValues()
  } catch {
    filterFields.value = []
  }
}

function applyDefaultFilterValues() {
  const values: Record<string, string> = {}
  for (const item of filterFields.value) {
    const key = String(item.id || item.fieldId)
    if (item.defaultValue != null && item.defaultValue !== '') values[key] = String(item.defaultValue)
  }
  filterValues.value = values
}

onMounted(async () => {
  if (!ensureSystemContext()) return
  await loadPageRuntime()
  await loadFieldsForSearch()
  await loadFilterTpls()
  query()
})
</script>

<style scoped>
.filter-box {
  margin-top: 12px;
}
.filter-picker {
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}
.filter-items {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}
.filter-item {
  display: grid;
  gap: 4px;
}
</style>

