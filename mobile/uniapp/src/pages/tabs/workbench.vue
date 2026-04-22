<template>
  <view style="padding: 16px">
    <uni-card title="工作台">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button type="primary" @click="goSystems">系统</uni-button>
        <uni-button @click="goApps">Apps</uni-button>
        <uni-button @click="goInbox">待办</uni-button>
        <uni-button @click="goUpload">上传</uni-button>
      </view>
      <view style="margin-top: 12px; color:#666">
        提示：先登录→创建/进入系统→再使用 module/records/flow/upload。
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
function goSystems() {
  uni.navigateTo({ url: '/pages/platform/systems' })
}
function goApps() {
  uni.navigateTo({ url: '/pages/system/module/meta/apps' })
}
function goInbox() {
  uni.navigateTo({ url: '/pages/system/flow/inbox' })
}
function goUpload() {
  uni.navigateTo({ url: '/pages/system/upload/index' })
}
</script>

