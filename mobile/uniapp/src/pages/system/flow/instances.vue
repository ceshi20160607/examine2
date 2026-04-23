<template>
  <view style="padding: 16px">
    <uni-card title="流程实例（分页）">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-easyinput v-model="keyword" placeholder="keyword(可选)" />
        <uni-button type="primary" :disabled="loading" @click="reload">刷新</uni-button>
        <uni-button :disabled="loading || page<=1" @click="prev">上一页</uni-button>
        <uni-button :disabled="loading || !hasNext" @click="next">下一页</uni-button>
      </view>
      <view style="margin-top: 8px; color:#666">page={{ page }} size={{ size }} total={{ total }}</view>
    </uni-card>

    <uni-card title="列表" style="margin-top: 12px">
      <uni-list v-if="rows.length">
        <uni-list-item
          v-for="r in rows"
          :key="String(r.id)"
          :title="r.title || ('Instance#' + r.id)"
          :note="`status=${r.status ?? ''} biz=${r.bizType || ''}:${r.bizId || ''}`"
          clickable
          @click="goDetail(r.id)"
        />
      </uni-list>
      <view v-else style="color:#666">暂无实例</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { httpGet } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

type FlowRecord = {
  id: number | string
  title?: string
  status?: number
  bizType?: string
  bizId?: string
}

const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref<FlowRecord[]>([])
const keyword = ref('')

const hasNext = computed(() => page.value * size.value < total.value)

async function load() {
  loading.value = true
  try {
    const kw = keyword.value.trim()
    const q = `/v1/system/flow/instances/page?page=${page.value}&size=${size.value}${kw ? `&keyword=${encodeURIComponent(kw)}` : ''}`
    const r = await httpGet<any>(q)
    const d = r.data || {}
    total.value = Number(d.total || 0)
    rows.value = (d.records || []) as FlowRecord[]
  } finally {
    loading.value = false
  }
}

async function reload() {
  page.value = 1
  await load()
}

async function prev() {
  if (page.value <= 1) return
  page.value -= 1
  await load()
}

async function next() {
  if (!hasNext.value) return
  page.value += 1
  await load()
}

function goDetail(id: any) {
  uni.navigateTo({ url: `/pages/system/flow/instance?id=${encodeURIComponent(String(id))}` })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>
