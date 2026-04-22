<template>
  <view style="padding: 16px">
    <uni-card title="应用（低代码）">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button type="primary" @click="goApps">Apps</uni-button>
        <uni-button @click="goRecordsTip">Records</uni-button>
      </view>
      <view style="margin-top: 12px; color:#666">
        Records 需要先选择 app/model（从 Apps→Models→Fields 进入）。
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
function goApps() {
  uni.navigateTo({ url: '/pages/system/module/meta/apps' })
}
function goRecordsTip() {
  uni.showToast({ title: '请先进入某个 Model 的 Fields，再点“进入 Records”', icon: 'none' })
}
</script>

