<template>
  <Page title="待办" subtitle="流程相关入口">
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
import { onMounted } from 'vue'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'

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

