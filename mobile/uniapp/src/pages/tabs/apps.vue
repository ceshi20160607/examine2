<template>
  <Page title="应用（低代码）" subtitle="应用、运行时菜单、模型与记录入口">
    <view class="u-card">
      <ActionBar>
        <uni-button type="primary" @click="goApps">管理 Apps</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
      <view class="u-subtitle">点击应用可进入运行时菜单，也可以继续管理模型、页面、权限。</view>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">应用列表</view>
      <uni-list v-if="apps.length">
        <uni-list-item
          v-for="a in apps"
          :key="a.id"
          :title="a.appName || a.appCode || ('App#' + a.id)"
          :note="`${a.appCode || ''} · ${a.status === 1 ? '启用' : '停用'}`"
          clickable
          @click="openActions(a)"
        />
      </uni-list>
      <EmptyState v-else text="暂无应用，请先创建 App" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { listApps, type ModuleApp } from '@/api/meta'
import { hasId, idToString } from '@/utils/id'

const apps = ref<ModuleApp[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

function goApps() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/module/meta/apps' })
}

async function load() {
  if (!ensureSystemContext()) return
  loading.value = true
  error.value = null
  try {
    const r = await listApps()
    apps.value = r.data || []
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

function openActions(a: ModuleApp) {
  const appId = idToString(a?.id)
  if (!hasId(appId)) return
  uni.showActionSheet({
    itemList: ['运行时菜单', '模型与字段', '页面设计', '权限 RBAC', '字典', '部门', '模型关系'],
    success: (res) => {
      if (res.tapIndex === 0) {
        uni.navigateTo({ url: `/pages/system/runtime/menus?appId=${encodeURIComponent(appId)}` })
        return
      }
      if (res.tapIndex === 1) {
        uni.navigateTo({ url: `/pages/system/module/meta/models?appId=${encodeURIComponent(appId)}` })
        return
      }
      if (res.tapIndex === 2) {
        uni.navigateTo({ url: `/pages/system/module/pages/index?appId=${encodeURIComponent(appId)}` })
        return
      }
      if (res.tapIndex === 3) {
        uni.navigateTo({ url: `/pages/system/module/rbac/index?appId=${encodeURIComponent(appId)}` })
        return
      }
      if (res.tapIndex === 4) {
        uni.navigateTo({ url: `/pages/system/module/dict/dicts?appId=${encodeURIComponent(appId)}` })
        return
      }
      if (res.tapIndex === 5) {
        uni.navigateTo({ url: `/pages/system/module/dept/depts?appId=${encodeURIComponent(appId)}` })
        return
      }
      if (res.tapIndex === 6) {
        uni.navigateTo({ url: `/pages/system/module/meta/relations?appId=${encodeURIComponent(appId)}` })
      }
    }
  })
}

onMounted(load)
</script>
