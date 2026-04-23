<template>
  <view style="padding: 16px">
    <uni-card title="流程模板">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button type="primary" :disabled="loading" @click="reload">刷新</uni-button>
        <uni-button :disabled="loading || page<=1" @click="prev">上一页</uni-button>
        <uni-button :disabled="loading || !hasNext" @click="next">下一页</uni-button>
      </view>
      <view style="margin-top: 8px; color:#666">page={{ page }} size={{ size }} total={{ total }}</view>
    </uni-card>

    <uni-card title="列表" style="margin-top: 12px">
      <uni-list v-if="rows.length">
        <uni-list-item
          v-for="t in rows"
          :key="String(t.id)"
          :title="t.tempName || t.tempCode || ('Temp#' + t.id)"
          :note="`code=${t.tempCode || ''} ver=${t.latestVerNo ?? ''}`"
          clickable
          @click="goStart(t)"
        />
      </uni-list>
      <view v-else style="color:#666">暂无模板</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { httpGet } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

type FlowTemp = {
  id: number | string
  tempCode?: string
  tempName?: string
  latestVerNo?: number
  status?: number
}

const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref<FlowTemp[]>([])

const hasNext = computed(() => page.value * size.value < total.value)

async function load() {
  loading.value = true
  try {
    const r = await httpGet<any>(`/v1/system/flow/temps/page?page=${page.value}&size=${size.value}`)
    const d = r.data || {}
    total.value = Number(d.total || 0)
    rows.value = (d.records || []) as FlowTemp[]
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

function goStart(t: FlowTemp) {
  const code = encodeURIComponent(String(t.tempCode || ''))
  uni.navigateTo({ url: `/pages/system/flow/start?defCode=${code}` })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>
