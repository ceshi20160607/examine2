<template>
  <view style="padding: 16px">
    <uni-card :title="`Records（modelId=${modelId}）`">
      <view style="display:flex; gap: 8px;">
        <uni-button type="primary" :disabled="!appId || !modelId" @click="goCreate">新建</uni-button>
        <uni-button :disabled="loading" @click="query">刷新</uni-button>
      </view>
    </uni-card>

    <uni-card title="结果" style="margin-top: 12px">
      <uni-list v-if="rows.length">
        <uni-list-item
          v-for="r in rows"
          :key="r.id"
          :title="`#${r.id}`"
          clickable
          @click="goDetail(r.id)"
        />
      </uni-list>
      <view v-else style="color:#666">暂无数据</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

type Row = { id: number }

const appId = ref<number>(0)
const modelId = ref<number>(0)
const loading = ref(false)
const rows = ref<Row[]>([])

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
})

async function query() {
  if (!modelId.value) return
  loading.value = true
  try {
    // 使用后端 DSL 查询（最小：只按 modelId 过滤，limit 20）
    const r = await httpPost<any>('/v1/system/records/query', {
      modelId: modelId.value,
      limit: 20
    })
    rows.value = (r.data?.list || []).map((x: any) => ({ id: x.id }))
  } finally {
    loading.value = false
  }
}

function goCreate() {
  uni.navigateTo({ url: `/pages/system/records/form?appId=${appId.value}&modelId=${modelId.value}` })
}

function goDetail(recordId: number) {
  uni.navigateTo({ url: `/pages/system/records/detail?recordId=${recordId}` })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  query()
})
</script>

