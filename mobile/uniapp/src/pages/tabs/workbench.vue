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
        当前：{{ statusText }}
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getSessionPayload } from '@/store/context'

function hasToken(): boolean {
  const t = uni.getStorageSync('token')
  return typeof t === 'string' && !!t.trim()
}

const statusText = computed(() => {
  if (!hasToken()) return '未登录（请先登录）'
  const p = getSessionPayload()
  if (!p || !p.systemId) return '未进入系统（请先创建/进入系统）'
  return `已进入系统 systemId=${p.systemId} tenantId=${p.tenantId}`
})

function goSystems() {
  if (!hasToken()) {
    uni.reLaunch({ url: '/pages/auth/login' })
    return
  }
  uni.navigateTo({ url: '/pages/platform/systems' })
}
function goApps() {
  if (!hasToken()) {
    uni.reLaunch({ url: '/pages/auth/login' })
    return
  }
  uni.navigateTo({ url: '/pages/system/module/meta/apps' })
}
function goInbox() {
  if (!hasToken()) {
    uni.reLaunch({ url: '/pages/auth/login' })
    return
  }
  uni.navigateTo({ url: '/pages/system/flow/inbox' })
}
function goUpload() {
  if (!hasToken()) {
    uni.reLaunch({ url: '/pages/auth/login' })
    return
  }
  uni.navigateTo({ url: '/pages/system/upload/index' })
}

onMounted(() => {
  if (!hasToken()) {
    uni.reLaunch({ url: '/pages/auth/login' })
  }
})
</script>

