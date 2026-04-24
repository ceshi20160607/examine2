<template>
  <Page :title="`Instance #${instanceId}`" subtitle="实例详情 / 任务 / 动作 / 轨迹">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="reload">刷新</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">record</view>
      <view style="margin-top: 12px; font-family: monospace; white-space: pre-wrap;">{{ pretty(record) }}</view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">tasks</view>
      <view style="margin-top: 12px">
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
        <EmptyState v-else text="暂无任务" />
      </view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">actions</view>
      <view style="margin-top: 12px; font-family: monospace; white-space: pre-wrap;">{{ pretty(actions) }}</view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">traces</view>
      <view style="margin-top: 12px; font-family: monospace; white-space: pre-wrap;">{{ pretty(traces) }}</view>
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
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { getInstance, listInstanceActions, listInstanceTasks, listInstanceTraces } from '@/api/flow'

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
      getInstance(instanceId.value),
      listInstanceTasks(instanceId.value),
      listInstanceActions(instanceId.value),
      listInstanceTraces(instanceId.value)
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
