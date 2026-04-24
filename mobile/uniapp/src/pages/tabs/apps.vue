<template>
  <Page title="应用（低代码）" subtitle="应用/模型/字段/记录管理入口">
    <view class="u-card">
      <ActionBar>
        <uni-button type="primary" @click="goApps">Apps</uni-button>
        <uni-button @click="goRecordsTip">Records</uni-button>
      </ActionBar>
      <view class="u-subtitle">Records 需要先选择 app/model（从 Apps→Models→Fields 进入）。</view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'

function goApps() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/module/meta/apps' })
}
function goRecordsTip() {
  uni.showToast({ title: '请先进入某个 Model 的 Fields，再点“进入 Records”', icon: 'none' })
}

onMounted(() => {
  ensureSystemContext()
})
</script>

