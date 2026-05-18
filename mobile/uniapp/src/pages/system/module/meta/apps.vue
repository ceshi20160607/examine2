<template>
  <Page title="应用 Apps" subtitle="创建应用后可继续创建模型、字段与权限">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item label="创建应用">
          <view style="display:flex; gap: 8px; flex-wrap: wrap; align-items: center;">
            <uni-easyinput v-model="form.appCode" placeholder="appCode（如 default）" style="flex:1; min-width: 160px" />
            <uni-easyinput v-model="form.appName" placeholder="appName" style="flex:1; min-width: 160px" />
          </view>
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="create">创建</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="apps.length">
          <uni-list-item
            v-for="a in apps"
            :key="a.id"
            :title="(a.appName || a.appCode || ('App#' + a.id))"
            :note="a.appCode || ''"
            clickable
            @click="openActions(a)"
          />
        </uni-list>
        <EmptyState v-else text="暂无应用" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import { listApps, type ModuleApp, upsertApp } from '@/api/meta'

const apps = ref<ModuleApp[]>([])
const saving = ref(false)
const { loading, error, run, capture, clearError } = usePageRequest()
const form = reactive({ appCode: '', appName: '' })

async function load() {
  await run(async () => {
    const r = await listApps()
    apps.value = r.data || []
  })
}

async function create() {
  if (!form.appCode.trim() || !form.appName.trim()) {
    uni.showToast({ title: '请输入 appCode/appName', icon: 'none' })
    return
  }
  saving.value = true
  clearError()
  try {
    await upsertApp({
      appCode: form.appCode.trim(),
      appName: form.appName.trim(),
      iconUrl: null,
      publishedFlag: 0,
      remark: null,
      status: 1
    })
    form.appCode = ''
    form.appName = ''
    await load()
  } catch (e: unknown) {
    capture(e)
  } finally {
    saving.value = false
  }
}

function goModels(a: ModuleApp) {
  uni.navigateTo({ url: `/pages/system/module/meta/models?appId=${a.id}` })
}

function openActions(a: ModuleApp) {
  if (!a?.id) return
  uni.showActionSheet({
    itemList: ['运行时菜单', 'Models', 'Pages', 'Relations', 'Dicts', 'Depts', 'RBAC'],
    success: (res) => {
      if (res.tapIndex === 0) {
        uni.navigateTo({ url: `/pages/system/runtime/menus?appId=${a.id}` })
        return
      }
      if (res.tapIndex === 1) {
        goModels(a)
        return
      }
      if (res.tapIndex === 2) {
        uni.navigateTo({ url: `/pages/system/module/pages/index?appId=${a.id}` })
        return
      }
      if (res.tapIndex === 3) {
        uni.navigateTo({ url: `/pages/system/module/meta/relations?appId=${a.id}` })
        return
      }
      if (res.tapIndex === 4) {
        uni.navigateTo({ url: `/pages/system/module/dict/dicts?appId=${a.id}` })
        return
      }
      if (res.tapIndex === 5) {
        uni.navigateTo({ url: `/pages/system/module/dept/depts?appId=${a.id}` })
        return
      }
      if (res.tapIndex === 6) {
        uni.navigateTo({ url: `/pages/system/module/rbac/index?appId=${a.id}` })
      }
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>

