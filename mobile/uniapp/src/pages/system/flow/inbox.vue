<template>
  <Page title="待办 Inbox" subtitle="我的待办 + 抄送">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="loadPending">刷新待办</uni-button>
        <uni-button :disabled="loading" @click="loadCc">刷新抄送</uni-button>
      </ActionBar>
    </view>

    <view class="u-card u-section">
      <view class="u-title">我的待办</view>
      <view style="margin-top: 12px">
        <uni-list v-if="pending.length">
          <uni-list-item
            v-for="t in pending"
            :key="t.id"
            :title="t.nodeName || ('Task#' + t.id)"
            :note="`instanceId=${t.recordId} taskId=${t.id}`"
            clickable
            @click="goTask(t)"
          />
        </uni-list>
        <EmptyState v-else text="暂无待办" />
      </view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">我的抄送</view>
      <view style="margin-top: 12px">
        <uni-list v-if="cc.length">
          <uni-list-item
            v-for="t in cc"
            :key="t.id"
            :title="t.nodeName || ('CC#' + t.id)"
            :note="`instanceId=${t.recordId} taskId=${t.id}`"
          />
        </uni-list>
        <EmptyState v-else text="暂无抄送" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import { inboxCc, inboxPending, type FlowTask } from '@/api/flow'

const loading = ref(false)
const pending = ref<FlowTask[]>([])
const cc = ref<FlowTask[]>([])

async function loadPending() {
  loading.value = true
  try {
    const r = await inboxPending(50)
    pending.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function loadCc() {
  loading.value = true
  try {
    const r = await inboxCc(50)
    cc.value = r.data || []
  } finally {
    loading.value = false
  }
}

function goTask(t: FlowTask) {
  if (!t?.id || !t?.recordId) return
  uni.navigateTo({ url: `/pages/system/flow/task?instanceId=${t.recordId}&taskId=${t.id}` })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  loadPending()
})
</script>

