<template>
  <view style="padding: 16px">
    <uni-card title="工作台">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button type="primary" @click="goSystems">系统</uni-button>
        <uni-button @click="goApps">Apps</uni-button>
        <uni-button @click="goInbox">待办</uni-button>
        <uni-button @click="goFlowStart">发起流程</uni-button>
        <uni-button @click="goFlowInstances">流程实例</uni-button>
        <uni-button @click="goUpload">上传</uni-button>
      </view>
      <view style="margin-top: 12px; color:#666">
        当前：{{ statusText }}
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { getSessionPayload } from '@/store/context'
import { ensureLogin, ensureSystemContext, hasToken } from '@/utils/guard'

const statusText = computed(() => {
  if (!hasToken()) return '未登录（请先登录）'
  const p = getSessionPayload()
  if (!p || !p.systemId) return '未进入系统（请先创建/进入系统）'
  return `已进入系统 systemId=${p.systemId} tenantId=${p.tenantId}`
})

function goSystems() {
  if (!ensureLogin()) return
  uni.navigateTo({ url: '/pages/platform/systems' })
}
function goApps() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/module/meta/apps' })
}
function goInbox() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/inbox' })
}
function goFlowStart() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/start' })
}
function goFlowInstances() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/instances' })
}
function goUpload() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/upload/index' })
}

onMounted(() => {
  if (!ensureLogin()) return
  const p = getSessionPayload()
  if (!p || !p.systemId) {
    uni.reLaunch({ url: '/pages/platform/systems' })
  }
})
</script>

