<template>
  <Page title="待办" :subtitle="subtitle">
    <view class="u-card">
      <ActionBar>
        <uni-button type="primary" @click="goInbox">打开待办箱</uni-button>
        <uni-button @click="goStart">发起流程</uni-button>
      </ActionBar>
      <view style="margin-top: 12px">
        <ActionBar>
          <uni-button @click="goTemps">流程模板</uni-button>
          <uni-button @click="goTempManage">模板管理</uni-button>
          <uni-button @click="goInstances">实例列表</uni-button>
          <uni-button @click="goMyInstances">我的实例</uni-button>
        </ActionBar>
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import { inboxPending } from '@/api/flow'

const pendingCount = ref<number | null>(null)

const subtitle = computed(() => {
  if (pendingCount.value === null) return '流程相关入口'
  return `待办 ${pendingCount.value} 条 · 流程相关入口`
})

function goInbox() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/inbox' })
}

function goTemps() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/temps' })
}

function goTempManage() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/temp_list' })
}

function goStart() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/start' })
}

function goInstances() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/instances' })
}

function goMyInstances() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/my_instances' })
}

async function refreshPendingCount() {
  if (!ensureSystemContext()) return
  try {
    const r = await inboxPending(50)
    pendingCount.value = (r.data || []).length
  } catch {
    pendingCount.value = null
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  refreshPendingCount()
})
</script>
