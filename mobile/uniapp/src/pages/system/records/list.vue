<template>
  <Page :title="`Records（modelId=${modelId}）`" subtitle="支持关键字搜索与详情查看">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="!appId || !modelId" @click="goCreate">新建</uni-button>
        <uni-button :disabled="loading" @click="query">刷新</uni-button>
        <uni-easyinput v-model="keyword" placeholder="keyword(可选)" style="flex: 1; min-width: 160px" />
        <uni-button :disabled="loading" @click="reload">搜索</uni-button>
      </ActionBar>
      <view v-if="searchFieldCode" class="u-subtitle">搜索字段：{{ searchFieldCode }}</view>
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
import { onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import { getRecord, queryRecords } from '@/api/records'
import { httpGet } from '@/api/http'

type Row = { id: number }
type Summary = { title: string; note: string }

const appId = ref<number>(0)
const modelId = ref<number>(0)
const loading = ref(false)
const rows = ref<Row[]>([])
const summaries = ref<Record<number, Summary>>({})
const keyword = ref('')
const searchFieldCode = ref<string>('')

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
})

function titleOf(id: number) {
  return summaries.value[id]?.title || `#${id}`
}
function noteOf(id: number) {
  return summaries.value[id]?.note || ''
}

async function query() {
  if (!modelId.value) return
  loading.value = true
  try {
    const filters: any[] = []
    const kw = keyword.value.trim()
    if (kw && searchFieldCode.value) {
      filters.push({ field: searchFieldCode.value, op: 'like', value: kw })
    }
    // 使用后端 DSL 查询（最小：只按 modelId 过滤，limit 20）
    const r = await queryRecords({
      appId: appId.value,
      modelId: modelId.value,
      page: 1,
      limit: 20,
      filters
    })
    rows.value = (r.data?.list || []).map((x: any) => ({ id: x.id }))
    summaries.value = {}
    // 轻量增强：拉取前 10 条详情，抽取 data 里的前几个字段做摘要
    await hydrateSummaries(rows.value.slice(0, 10).map((x) => x.id))
  } finally {
    loading.value = false
  }
}

async function reload() {
  await query()
}

async function hydrateSummaries(ids: number[]) {
  for (const id of ids) {
    try {
      const r = await getRecord(id)
      const d = r.data || {}
      const data = d.data || {}
      const keys = Object.keys(data).slice(0, 3)
      const parts = keys.map((k) => `${k}=${stringifyValue(data[k])}`)
      summaries.value = {
        ...summaries.value,
        [id]: {
          title: parts[0] ? `${parts[0]} (#${id})` : `#${id}`,
          note: parts.slice(1).join(' | ')
        }
      }
    } catch {
      // ignore
    }
  }
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
  uni.navigateTo({ url: `/pages/system/records/form?appId=${appId.value}&modelId=${modelId.value}` })
}

function goDetail(recordId: number) {
  uni.navigateTo({ url: `/pages/system/records/detail?recordId=${recordId}` })
}

async function loadFieldsForSearch() {
  if (!modelId.value) return
  try {
    const r = await httpGet<any>(`/v1/system/module/meta/models/${modelId.value}/fields`)
    const list = (r.data || []) as Array<{ fieldCode?: string; fieldType?: string; dictCode?: string; hiddenFlag?: number }>
    const cand = list.find((f) => f && f.hiddenFlag !== 1 && f.fieldCode && !f.dictCode)
    searchFieldCode.value = cand?.fieldCode ? String(cand.fieldCode) : ''
  } catch {
    searchFieldCode.value = ''
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  loadFieldsForSearch()
  query()
})
</script>

