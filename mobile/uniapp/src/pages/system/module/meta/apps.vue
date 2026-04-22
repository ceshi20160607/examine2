<template>
  <view style="padding: 16px">
    <uni-card title="应用 Apps">
      <view style="display:flex; gap: 8px;">
        <uni-easyinput v-model="form.appCode" placeholder="appCode (如 default)" />
        <uni-easyinput v-model="form.appName" placeholder="appName" />
        <uni-button type="primary" :disabled="saving" @click="create">创建</uni-button>
      </view>
    </uni-card>

    <uni-card title="列表" style="margin-top: 12px">
      <uni-list v-if="apps.length">
        <uni-list-item
          v-for="a in apps"
          :key="a.id"
          :title="(a.appName || a.appCode || ('App#' + a.id))"
          :note="a.appCode || ''"
          clickable
          @click="goModels(a)"
        />
      </uni-list>
      <view v-else style="color:#666">暂无应用</view>
      <view style="margin-top: 12px">
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { httpGet, httpPost } from '@/api/http'

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

onMounted(load)
</script>

