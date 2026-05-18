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
import { listFieldsByModel } from '@/api/meta'
import { getPageRuntime } from '@/api/pages'
import { pageQuerySuffix, type PageRuntime } from '@/utils/pageRuntime'

type Row = { id: number }
type Summary = { title: string; note: string }

const appId = ref<number>(0)
const modelId = ref<number>(0)
const pageId = ref<number>(0)
const pageRuntime = ref<PageRuntime | null>(null)
const { loading, error, run } = usePageRequest()
const rows = ref<Row[]>([])
const summaries = ref<Record<number, Summary>>({})
const keyword = ref('')
const searchFieldCode = ref<string>('')
const displayFieldCodes = ref<string[]>([])

const pageTitle = computed(() => {
  if (pageRuntime.value?.pageName) return pageRuntime.value.pageName
  return `Records（modelId=${modelId.value}）`
})
const pageSubtitle = computed(() => {
  if (pageRuntime.value?.pageCode) return `page=${pageRuntime.value.pageCode} · 关键字搜索`
  return '支持关键字搜索与详情查看'
})

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
  pageId.value = Number((opts as any)?.pageId || 0) || 0
})

function titleOf(id: number) {
  return summaries.value[id]?.title || `#${id}`
}
function noteOf(id: number) {
  return summaries.value[id]?.note || ''
}

async function query() {
  if (!modelId.value) return
  await run(async () => {
    const filters: any[] = []
    const kw = keyword.value.trim()
    if (kw && searchFieldCode.value) {
      filters.push({ field: searchFieldCode.value, op: 'like', value: kw })
    }
    const cols =
      displayFieldCodes.value.length > 0
        ? displayFieldCodes.value
        : pageRuntime.value?.titleFieldCodes?.length
          ? pageRuntime.value.titleFieldCodes
          : []
    const r = await queryRecords({
      appId: appId.value,
      modelId: modelId.value,
      page: 1,
      limit: 20,
      filters,
      includeFieldCodes: cols.length ? cols.slice(0, 30) : []
    })
    const list = r.data?.list || []
    rows.value = list.map((x: any) => ({ id: x.id }))
    summaries.value = {}
    for (const item of list) {
      const id = Number(item.id)
      const data = item.data || {}
      const keys = cols.length ? cols : Object.keys(data).slice(0, 3)
      const parts = keys.map((k: string) => `${k}=${stringifyValue(data[k])}`)
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

function goCreate() {
  uni.navigateTo({
    url: `/pages/system/records/form?appId=${appId.value}&modelId=${modelId.value}${pageQuerySuffix(pageId.value)}`
  })
}

function goDetail(recordId: number) {
  uni.navigateTo({
    url: `/pages/system/records/detail?recordId=${recordId}${pageQuerySuffix(pageId.value)}`
  })
}

async function loadPageRuntime() {
  if (!pageId.value) return
  try {
    const r = await getPageRuntime(pageId.value)
    pageRuntime.value = r.data || null
    if (pageRuntime.value?.appId && !appId.value) appId.value = pageRuntime.value.appId
    if (pageRuntime.value?.modelId && !modelId.value) modelId.value = Number(pageRuntime.value.modelId)
    if (pageRuntime.value?.searchFieldCode) {
      searchFieldCode.value = pageRuntime.value.searchFieldCode
    }
    const cols = pageRuntime.value?.columnFieldCodes || pageRuntime.value?.titleFieldCodes
    displayFieldCodes.value = cols?.length ? cols : []
  } catch {
    pageRuntime.value = null
  }
}

async function loadFieldsForSearch() {
  if (!modelId.value) return
  if (searchFieldCode.value) return
  try {
    const r = await listFieldsByModel(modelId.value)
    const list = (r.data || []) as Array<{ fieldCode?: string; fieldType?: string; dictCode?: string; hiddenFlag?: number }>
    const cand = list.find((f) => f && f.hiddenFlag !== 1 && f.fieldCode && !f.dictCode)
    searchFieldCode.value = cand?.fieldCode ? String(cand.fieldCode) : ''
  } catch {
    searchFieldCode.value = ''
  }
}

onMounted(async () => {
  if (!ensureSystemContext()) return
  await loadPageRuntime()
  await loadFieldsForSearch()
  query()
})
</script>

