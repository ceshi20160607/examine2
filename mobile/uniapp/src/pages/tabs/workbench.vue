<template>
  <Page title="工作台" :subtitle="`当前：${statusText}`">
    <view class="u-card">
      <ActionBar>
        <uni-button type="primary" @click="goSystems">系统</uni-button>
        <uni-button @click="goOpenApps">OpenAPI</uni-button>
        <uni-button @click="goApps">元数据</uni-button>
        <uni-button @click="goInbox">待办</uni-button>
        <uni-button @click="goFlowStart">发起流程</uni-button>
        <uni-button @click="goFlowInstances">流程实例</uni-button>
        <uni-button @click="goFlowMyInstances">我的实例</uni-button>
        <uni-button @click="goUpload">上传</uni-button>
      </ActionBar>
      <ActionBar>
        <uni-button @click="goDict">字典</uni-button>
        <uni-button @click="goExportJobs">导出任务</uni-button>
        <uni-button @click="goRbac">权限</uni-button>
        <uni-button @click="goFlowTemps">流程模板</uni-button>
      </ActionBar>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { getSessionPayload } from '@/store/context'
import { ensureLogin, ensureSystemContext, hasToken } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'

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
function goOpenApps() {
  if (!ensureLogin()) return
  uni.navigateTo({ url: '/pages/platform/open_apps' })
}
function goApps() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/module/meta/apps' })
}
function goInbox() {
  if (!ensureLogin()) return
  uni.switchTab({ url: '/pages/tabs/inbox' })
}
function goFlowStart() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/start' })
}
function goFlowInstances() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/instances' })
}
function goFlowMyInstances() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/my_instances' })
}
function goUpload() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/upload/index' })
}
function goDict() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/module/dict/dicts' })
}
function goExportJobs() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/module/export/jobs' })
}
function goRbac() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/module/rbac/index' })
}
function goFlowTemps() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/temp_list' })
}

onMounted(() => {
  if (!ensureLogin()) return
  const p = getSessionPayload()
  if (!p || !p.systemId) {
    uni.reLaunch({ url: '/pages/platform/systems' })
  }
})
</script>

