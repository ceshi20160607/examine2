<template>
  <view style="padding: 16px">
    <uni-card title="待办">
      <view style="display:flex; gap: 8px;">
        <uni-button type="primary" @click="goInbox">打开待办箱</uni-button>
      </view>
      <view style="margin-top: 12px; display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button @click="goTemps">流程模板</uni-button>
        <uni-button @click="goTempManage">模板管理</uni-button>
        <uni-button @click="goStart">发起流程</uni-button>
        <uni-button @click="goInstances">实例列表</uni-button>
        <uni-button @click="goMyInstances">我的实例</uni-button>
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { ensureSystemContext } from '@/utils/guard'

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

onMounted(() => {
  ensureSystemContext()
})
</script>

