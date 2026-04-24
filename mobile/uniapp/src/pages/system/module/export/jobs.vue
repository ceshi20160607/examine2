<template>
  <Page title="导出任务 Export Jobs" subtitle="异步导出任务列表（分页）">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item label="tplId(可选)">
          <uni-easyinput v-model="filter.tplId" placeholder="tplId(可选)" />
        </uni-forms-item>
        <uni-forms-item label="modelId(可选)">
          <uni-easyinput v-model="filter.modelId" placeholder="modelId(可选)" />
        </uni-forms-item>
        <uni-forms-item label="status(可选 0/1/2/3)">
          <uni-easyinput v-model="filter.status" placeholder="status(可选 0/1/2/3)" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="reload">刷新</uni-button>
        <uni-button :disabled="loading || page<=1" @click="prev">上一页</uni-button>
        <uni-button :disabled="loading || !hasNext" @click="next">下一页</uni-button>
      </ActionBar>
      <view class="u-subtitle">page={{ page }} size={{ size }} total={{ total }}</view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="j in rows"
            :key="String(j.id)"
            :title="`job#${j.id} ${statusText(j.status)}`"
            :note="`${j.errorMsg ? ('err=' + j.errorMsg + ' | ') : ''}tplId=${j.tplId || ''} modelId=${j.modelId || ''} fileId=${j.resultFileId || ''}`"
            clickable
            @click="goDetail(j.id)"
          />
        </uni-list>
        <EmptyState v-else text="暂无任务" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'

type JobRow = {
  id: number | string
  status?: number
  tplId?: number | string
  modelId?: number | string
  resultFileId?: number | string
  errorMsg?: string
}

const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref<JobRow[]>([])
const filter = reactive<{ tplId: string; modelId: string; status: string }>({ tplId: '', modelId: '', status: '' })

const hasNext = computed(() => page.value * size.value < total.value)

function statusText(st: any): string {
  if (st === 0) return 'pending'
  if (st === 1) return 'running'
  if (st === 2) return 'success'
  if (st === 3) return 'failed'
  return String(st ?? '')
}

onLoad((opts) => {
  const mid = String((opts as any)?.modelId || '').trim()
  if (mid) filter.modelId = mid
  const tid = String((opts as any)?.tplId || '').trim()
  if (tid) filter.tplId = tid
})

function buildQuery() {
  const params: string[] = []
  const tplId = filter.tplId.trim()
  const modelId = filter.modelId.trim()
  const status = filter.status.trim()
  if (tplId) params.push(`tplId=${encodeURIComponent(tplId)}`)
  if (modelId) params.push(`modelId=${encodeURIComponent(modelId)}`)
  if (status) params.push(`status=${encodeURIComponent(status)}`)
  return params.length ? '&' + params.join('&') : ''
}

async function load() {
  loading.value = true
  try {
    const r = await httpGet<any>(`/v1/system/module/export-jobs/page?page=${page.value}&size=${size.value}${buildQuery()}`)
    const d = r.data || {}
    total.value = Number(d.total || 0)
    rows.value = (d.records || []) as JobRow[]
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

function goDetail(jobId: any) {
  uni.navigateTo({ url: `/pages/system/module/export/job_detail?jobId=${encodeURIComponent(String(jobId))}` })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>

