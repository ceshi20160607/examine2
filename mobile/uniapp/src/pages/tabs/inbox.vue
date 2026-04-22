<template>
  <view style="padding: 16px">
    <uni-card title="待办">
      <view style="display:flex; gap: 8px;">
        <uni-button type="primary" @click="goInbox">打开待办箱</uni-button>
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
function goInbox() {
  uni.navigateTo({ url: '/pages/system/flow/inbox' })
}
</script>

