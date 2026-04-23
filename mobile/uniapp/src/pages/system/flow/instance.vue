<template>
  <view style="padding: 16px">
    <uni-card :title="`Instance #${instanceId}`">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button type="primary" :disabled="loading" @click="reload">刷新</uni-button>
      </view>
      <view v-if="error" style="margin-top: 12px; color:#d00">{{ error }}</view>
    </uni-card>

    <uni-card title="record" style="margin-top: 12px">
      <view style="font-family: monospace; white-space: pre-wrap;">{{ pretty(record) }}</view>
    </uni-card>

    <uni-card title="tasks" style="margin-top: 12px">
      <uni-list v-if="tasks.length">
        <uni-list-item
          v-for="t in tasks"
          :key="String(t.id)"
          :title="t.nodeName || ('Task#' + t.id)"
          :note="`status=${t.status ?? ''} id=${t.id}`"
          clickable
          @click="goTask(t)"
        />
      </uni-list>
      <view v-else style="color:#666">暂无任务</view>
    </uni-card>

    <uni-card title="actions" style="margin-top: 12px">
      <view style="font-family: monospace; white-space: pre-wrap;">{{ pretty(actions) }}</view>
    </uni-card>

    <uni-card title="traces" style="margin-top: 12px">
      <view style="font-family: monospace; white-space: pre-wrap;">{{ pretty(traces) }}</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

const instanceId = ref<number>(0)
const loading = ref(false)
const error = ref<string | null>(null)

const record = ref<any>(null)
const tasks = ref<any[]>([])
const actions = ref<any[]>([])
const traces = ref<any[]>([])

onLoad((opts) => {
  instanceId.value = Number((opts as any)?.id || 0) || 0
})

function pretty(v: any) {
  try {
    return JSON.stringify(v, null, 2)
  } catch {
    return String(v ?? '')
  }
}

function goTask(t: any) {
  if (!instanceId.value || !t?.id) return
  uni.navigateTo({ url: `/pages/system/flow/task?instanceId=${instanceId.value}&taskId=${t.id}` })
}

async function reload() {
  if (!instanceId.value) return
  loading.value = true
  error.value = null
  try {
    const [r1, r2, r3, r4] = await Promise.all([
      httpGet<any>(`/v1/system/flow/instances/${instanceId.value}`),
      httpGet<any[]>(`/v1/system/flow/instances/${instanceId.value}/tasks`),
      httpGet<any[]>(`/v1/system/flow/instances/${instanceId.value}/actions`),
      httpGet<any[]>(`/v1/system/flow/instances/${instanceId.value}/traces`)
    ])
    record.value = r1.data
    tasks.value = r2.data || []
    actions.value = r3.data || []
    traces.value = r4.data || []
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  if (!instanceId.value) {
    uni.showToast({ title: '缺少 id', icon: 'none' })
    return
  }
  reload()
})
</script>
