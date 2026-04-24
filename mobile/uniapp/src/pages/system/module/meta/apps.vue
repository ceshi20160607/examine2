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
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'

type ModuleApp = { id: number; appCode?: string; appName?: string; status?: number }

const apps = ref<ModuleApp[]>([])
const loading = ref(false)
const saving = ref(false)
const form = reactive({ appCode: '', appName: '' })

async function load() {
  loading.value = true
  try {
    const r = await httpGet<ModuleApp[]>('/v1/system/module/meta/apps')
    apps.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!form.appCode.trim() || !form.appName.trim()) {
    uni.showToast({ title: '请输入 appCode/appName', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await httpPost('/v1/system/module/meta/apps/upsert', {
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
    itemList: ['Models', 'Dicts', 'RBAC'],
    success: (res) => {
      if (res.tapIndex === 0) {
        goModels(a)
        return
      }
      if (res.tapIndex === 1) {
        uni.navigateTo({ url: `/pages/system/module/dict/dicts?appId=${a.id}` })
        return
      }
      if (res.tapIndex === 2) {
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

