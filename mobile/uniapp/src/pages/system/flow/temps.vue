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
          :note="noteText(t)"
          clickable
          @click="onClick(t)"
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

function isStartable(t: FlowTemp): boolean {
  const st = Number(t.status || 0)
  const ver = Number(t.latestVerNo || 0)
  return st === 1 && ver > 0 && !!String(t.tempCode || '').trim()
}

function noteText(t: FlowTemp): string {
  const code = String(t.tempCode || '')
  const ver = t.latestVerNo ?? ''
  const st = Number(t.status || 0)
  const stText = st === 1 ? '启用' : st === 2 ? '停用' : `status=${st || ''}`
  const can = isStartable(t) ? '可发起' : '不可发起'
  return `code=${code} ver=${ver} ${stText} ${can}`
}

async function load() {
  loading.value = true
  try {
    const r = await httpGet<any>(`/v1/system/flow/temps/page?page=${page.value}&size=${size.value}`)
    const d = r.data || {}
    total.value = Number(d.total || 0)
    const list = (d.records || []) as FlowTemp[]
    list.sort((a, b) => {
      const sa = isStartable(a) ? 0 : 1
      const sb = isStartable(b) ? 0 : 1
      if (sa !== sb) return sa - sb
      return String(a.tempName || a.tempCode || '').localeCompare(String(b.tempName || b.tempCode || ''), 'zh-Hans-CN')
    })
    rows.value = list
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
  const name = encodeURIComponent(String(t.tempName || ''))
  uni.navigateTo({ url: `/pages/system/flow/start?defCode=${code}&tempName=${name}` })
}

function goTempManage() {
  uni.navigateTo({ url: '/pages/system/flow/temp_list' })
}

function goTempEdit(id: FlowTemp['id']) {
  uni.navigateTo({ url: `/pages/system/flow/temp_edit?id=${encodeURIComponent(String(id))}` })
}

function onClick(t: FlowTemp) {
  const ok = isStartable(t)
  const name = t.tempName || t.tempCode || ''
  if (ok) {
    goStart(t)
    return
  }
  uni.showActionSheet({
    itemList: ['去模板管理', `编辑模板：${name || t.id}`],
    success: (res) => {
      if (res.tapIndex === 0) goTempManage()
      if (res.tapIndex === 1) goTempEdit(t.id)
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>
