<template>
  <view style="padding: 16px">
    <uni-card :title="`List Views（modelId=${modelId}）`">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-easyinput v-model="form.viewCode" placeholder="viewCode" />
        <uni-easyinput v-model="form.viewName" placeholder="viewName" />
        <uni-button type="primary" :disabled="saving" @click="upsert">创建</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </view>
    </uni-card>

    <uni-card title="列表" style="margin-top: 12px">
      <view style="margin-bottom: 12px; display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button :disabled="!modelId" @click="goFilterTpls">筛选模板</uni-button>
      </view>
      <uni-list v-if="rows.length">
        <uni-list-item
          v-for="v in rows"
          :key="v.id"
          :title="v.viewName || v.viewCode || ('View#' + v.id)"
          :note="v.viewCode || ''"
          clickable
          @click="openViewActions(v)"
        />
      </uni-list>
      <view v-else style="color:#666">暂无视图</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

type ViewRow = { id: number; viewCode?: string; viewName?: string; defaultFlag?: number; status?: number }

const appId = ref<number>(0)
const modelId = ref<number>(0)
const loading = ref(false)
const saving = ref(false)
const rows = ref<ViewRow[]>([])

const form = reactive<{ viewCode: string; viewName: string }>({ viewCode: '', viewName: '' })

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
})

async function load() {
  if (!modelId.value) return
  loading.value = true
  try {
    const r = await httpGet<ViewRow[]>(`/v1/system/module/list-views/models/${modelId.value}`)
    rows.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function upsert() {
  if (!appId.value || !modelId.value) return
  if (!form.viewCode.trim() || !form.viewName.trim()) {
    uni.showToast({ title: '请输入 viewCode/viewName', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await httpPost('/v1/system/module/list-views/upsert', {
      id: null,
      appId: appId.value,
      modelId: modelId.value,
      platId: null,
      viewCode: form.viewCode.trim(),
      viewName: form.viewName.trim(),
      defaultFlag: 0,
      status: 1
    })
    form.viewCode = ''
    form.viewName = ''
    await load()
  } finally {
    saving.value = false
  }
}

function goFilterTpls() {
  if (!appId.value || !modelId.value) return
  uni.navigateTo({ url: `/pages/system/module/listviews/filter_tpls?appId=${appId.value}&modelId=${modelId.value}` })
}

function openViewActions(v: ViewRow) {
  if (!v?.id) return
  uni.showActionSheet({
    itemList: ['Cols（列配置）'],
    success: (res) => {
      if (res.tapIndex !== 0) return
      if (!appId.value || !modelId.value) return
      uni.navigateTo({
        url: `/pages/system/module/listviews/cols?viewId=${v.id}&appId=${appId.value}&modelId=${modelId.value}`
      })
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>

